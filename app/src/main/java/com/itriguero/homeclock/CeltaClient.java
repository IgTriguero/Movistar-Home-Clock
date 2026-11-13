package com.itriguero.homeclock;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Cliente de la web oficial del Celta para As Celtas.
 *
 * La web del club carga el calendario con JavaScript y lo pide a su propio
 * WordPress (admin-ajax). Se usa ese mismo endpoint, sin registro ni key,
 * para obtener los partidos de Copa de la Reina y su resultado.
 */
public final class CeltaClient {

    private static final String AJAX_URL = "https://rccelta.es/wp/wp-admin/admin-ajax.php";
    private static final String TEAM_ID = "138738";
    private static final String COMPETITION = "copa_de_la_reina";
    private static final String COMPETITION_LABEL = "Copa de la Reina";
    private static final String TRACKED_NAME = "As Celtas";
    private static final long TRACKED_ID = -138738L;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) HomeClock/1.0";

    private static final String CACHE_FILE = "celta_cache.json";
    private static final String SEASON_CACHE_FILE = "celta_season_cache.json";
    private static final long IDLE_TTL_MS = 6L * 60 * 60 * 1000;
    private static final long SEASON_TTL_MS = 24L * 60 * 60 * 1000;
    private static final long LIVE_TTL_MS = 5L * 60 * 1000;
    private static final long MATCH_DURATION_MS = 3L * 60 * 60 * 1000;
    private static final long RESULT_SETTLE_MS = 2L * 60 * 60 * 1000;
    private static final long REFRESH_BEFORE_MS = 30L * 60 * 1000;

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final DateTimeFormatter YMD = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final Object LOCK = new Object();

    private CeltaClient() {
    }

    /**
     * Resultado de una consulta al Celta. {@code fromCache} solo es true
     * cuando el calendario se sirvió de la caché local porque la petición de
     * red falló. El uso normal de la caché por TTL no se marca.
     */
    public static final class FetchResult {
        public final List<Match> matches;
        public final boolean fromCache;

        FetchResult(List<Match> matches, boolean fromCache) {
            this.matches = matches == null ? new ArrayList<>() : matches;
            this.fromCache = fromCache;
        }
    }

    public static FetchResult fetch(Context context, LocalDate date) throws Exception {
        synchronized (LOCK) {
            JSONObject cache = loadCache(context);
            JSONArray cachedItems = cache.optJSONArray("items");
            boolean cachedHasItems = cachedItems != null && cachedItems.length() > 0;
            boolean fromCache = false;

            if (needsRefresh(cache, date, System.currentTimeMillis())) {
                String season = SeasonResolver.forDate(date);
                JSONObject fresh = fetchSeason(season);
                if (fresh != null) {
                    fresh.put("fetched", System.currentTimeMillis());
                    saveCache(context, fresh);
                    cache = fresh;
                } else if (!cachedHasItems) {
                    // Sin red y sin calendario previo: la fuente debe reportar error,
                    // no una lista vacía que parezca "sin partidos".
                    throw new RuntimeException("Celta: sin datos");
                } else {
                    // Sin red pero con calendario previo: se sirve la copia local.
                    fromCache = true;
                }
            }

            List<Match> result = new ArrayList<>();
            addMatches(result, cache.optJSONArray("items"), date);
            return new FetchResult(result, fromCache);
        }
    }

    private static boolean needsRefresh(JSONObject cache, LocalDate date, long now) {
        if (!SeasonResolver.forDate(date).equals(cache.optString("season", ""))) return true;

        long fetched = cache.optLong("fetched", 0);
        if (fetched <= 0) return true;

        long age = now - fetched;
        JSONArray items = cache.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null || !date.toString().equals(item.optString("date"))) continue;
                long start = item.optLong("start", 0);
                if (start > 0
                        && now >= start - REFRESH_BEFORE_MS
                        && now <= start + MATCH_DURATION_MS) {
                    return age > LIVE_TTL_MS;
                }
            }
        }
        return age > IDLE_TTL_MS;
    }

    private static JSONObject fetchSeason(String season) {
        try {
            String body = "action=" + URLEncoder.encode(
                    "get_competition_season_matches", "UTF-8")
                    + "&team_id=" + TEAM_ID
                    + "&year=" + season
                    + "&competition=" + URLEncoder.encode(COMPETITION, "UTF-8");

            JSONObject root = new JSONObject(httpPost(AJAX_URL, body));
            JSONObject calendar = root.optJSONObject("match_calendar");
            JSONArray items = new JSONArray();

            if (calendar != null) {
                Iterator<String> keys = calendar.keys();
                while (keys.hasNext()) {
                    JSONObject month = calendar.optJSONObject(keys.next());
                    if (month == null) continue;
                    JSONArray matches = month.optJSONArray("matches");
                    if (matches == null) continue;
                    for (int i = 0; i < matches.length(); i++) {
                        JSONObject item = parseMatch(matches.optJSONObject(i));
                        if (item != null) items.put(item);
                    }
                }
            }

            JSONObject result = new JSONObject();
            result.put("items", items);
            result.put("season", season);
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    static JSONObject parseMatch(JSONObject match) throws Exception {
        if (match == null) return null;

        JSONObject local = match.optJSONObject("local_team");
        JSONObject visitor = match.optJSONObject("visitor_team");
        if (local == null || visitor == null) return null;

        boolean localTracked = isAsCeltas(local.optString("name", ""));
        boolean visitorTracked = isAsCeltas(visitor.optString("name", ""));
        if (!localTracked && !visitorTracked) return null;

        JSONObject dateObject = match.optJSONObject("date");
        String dateText = dateObject == null ? "" : dateObject.optString("date", "");
        if (dateText.length() < 10) return null;

        LocalDate matchDate;
        LocalTime matchTime;
        String hour = match.optString("hour", "");
        boolean timeTbd = hour == null || hour.trim().isEmpty();
        try {
            matchDate = LocalDate.parse(dateText.substring(0, 10), YMD);
            matchTime = timeTbd ? LocalTime.MIDNIGHT : LocalTime.parse(hour);
        } catch (Exception e) {
            return null;
        }

        JSONObject item = new JSONObject();
        item.put("date", matchDate.format(YMD));
        item.put("time", timeTbd ? "" : hour);
        item.put("start", matchDate.atTime(matchTime).atZone(MADRID)
                .toInstant().toEpochMilli());
        item.put("home", cleanName(local.optString("name", "")));
        item.put("away", cleanName(visitor.optString("name", "")));
        item.put("homeLogo", cleanShield(local.optString("shield", "")));
        item.put("awayLogo", cleanShield(visitor.optString("shield", "")));
        item.put("homeTracked", localTracked);
        item.put("hs", parseGoals(local.optString("goals", "")));
        item.put("as", parseGoals(visitor.optString("goals", "")));
        item.put("status", match.optInt("status", -1));
        item.put("live", match.optString("live_minute", ""));
        item.put("id", match.optString("id", ""));
        return item;
    }

    private static void addMatches(List<Match> out, JSONArray items, LocalDate date) {
        if (items == null) return;
        long now = System.currentTimeMillis();
        boolean pastDay = date.isBefore(LocalDate.now(MADRID));

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null || !date.toString().equals(item.optString("date"))) continue;

            long start = item.optLong("start", 0);
            if (start <= 0) continue;

            boolean homeTracked = item.optBoolean("homeTracked", false);
            int homeScore = item.optInt("hs", -1);
            int awayScore = item.optInt("as", -1);

            Match match = new Match();
            String id = item.optString("id", "");
            try {
                match.id = -Long.parseLong(id);
            } catch (NumberFormatException e) {
                match.id = -(900000L + i);
            }

            match.sourceId = CeltaSource.ID;
            match.sport = "Fútbol";
            match.competition = COMPETITION_LABEL;
            match.startMillis = start;
            match.timeTbd = item.optString("time", "").isEmpty();
            match.gameTimeDisplay = item.optString("live", "");
            match.gameTime = -1;
            match.homeName = item.optString("home", "");
            match.awayName = item.optString("away", "");
            match.homeLogoUrl = optUrl(item, "homeLogo");
            match.awayLogoUrl = optUrl(item, "awayLogo");
            match.homeScore = homeScore;
            match.awayScore = awayScore;
            match.scoreKnown = homeScore >= 0 && awayScore >= 0;
            match.trackedIsHome = homeTracked;
            match.trackedName = TRACKED_NAME;
            match.opponentName = homeTracked ? match.awayName : match.homeName;
            match.homeId = homeTracked ? TRACKED_ID : -(900000L + i);
            match.awayId = homeTracked ? -(900000L + i) : TRACKED_ID;

            int status = item.optInt("status", -1);
            if (status == 1) {
                match.status = MatchStatus.FINISHED;
            } else if (status == 0 || (status >= 3 && status <= 5)) {
                match.status = MatchStatus.LIVE;
            } else if (status == 2) {
                match.status = MatchStatus.POSTPONED;
                match.scoreKnown = false;
                match.homeScore = -1;
                match.awayScore = -1;
            } else {
                match.status = MatchTimeStatus.resolve(
                        pastDay, match.timeTbd, match.scoreKnown,
                        start, now, RESULT_SETTLE_MS, MATCH_DURATION_MS);
            }

            out.add(match);
        }
    }

    /**
     * Completa los escudos de los partidos de liga de As Celtas usando el
     * calendario completo del Celta (los escudos de la RFEF no están disponibles).
     */
    public static void fillLeagueLogos(Context context, List<Match> list) {
        if (list == null || list.isEmpty()) return;
        Match logoMatch = firstLeagueLogoMatch(list);
        if (logoMatch == null) return;

        LocalDate logoDate = Instant.ofEpochMilli(logoMatch.startMillis)
                .atZone(MADRID).toLocalDate();
        String season = SeasonResolver.forDate(logoDate);
        JSONObject cache = loadSeasonCache(context);
        long fetched = cache.optLong("fetched", 0);
        if (!season.equals(cache.optString("season", ""))
                || fetched <= 0
                || System.currentTimeMillis() - fetched > SEASON_TTL_MS) {
            JSONObject fresh = fetchFullSeason(season);
            if (fresh != null) {
                try {
                    fresh.put("fetched", System.currentTimeMillis());
                } catch (Exception ignored) {
                    return;
                }
                saveSeasonCache(context, fresh);
                cache = fresh;
            }
        }

        JSONArray items = cache.optJSONArray("items");
        if (items == null) return;

        for (Match match : list) {
            if (match.homeLogoUrl != null && match.awayLogoUrl != null) continue;
            if (match.competition == null
                    || !match.competition.toLowerCase(Locale.ROOT).contains("federaci")) {
                continue;
            }

            String date = Instant.ofEpochMilli(match.startMillis)
                    .atZone(MADRID).toLocalDate().format(YMD);

            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null || !date.equals(item.optString("date"))) continue;

                boolean celtaHome = isAsCeltas(item.optString("home"));
                boolean celtaAway = isAsCeltas(item.optString("away"));
                if (!celtaHome && !celtaAway) continue;

                boolean sameOrientation = celtaHome == match.trackedIsHome;
                match.homeLogoUrl = sameOrientation
                        ? optUrl(item, "homeLogo") : optUrl(item, "awayLogo");
                match.awayLogoUrl = sameOrientation
                        ? optUrl(item, "awayLogo") : optUrl(item, "homeLogo");
                break;
            }
        }
    }

    private static Match firstLeagueLogoMatch(List<Match> list) {
        for (Match match : list) {
            if (match.homeLogoUrl != null && match.awayLogoUrl != null) continue;
            if (match.competition != null
                    && match.competition.toLowerCase(Locale.ROOT).contains("federaci")) {
                return match;
            }
        }
        return null;
    }

    private static JSONObject fetchFullSeason(String season) {
        try {
            String body = "action=" + URLEncoder.encode("get_full_season_matches", "UTF-8")
                    + "&team_id=" + TEAM_ID
                    + "&year=" + season;
            JSONObject root = new JSONObject(httpPost(AJAX_URL, body));
            JSONObject calendar = root.optJSONObject("match_calendar");
            JSONArray items = new JSONArray();

            if (calendar != null) {
                Iterator<String> keys = calendar.keys();
                while (keys.hasNext()) {
                    JSONObject month = calendar.optJSONObject(keys.next());
                    if (month == null) continue;
                    JSONArray matches = month.optJSONArray("matches");
                    if (matches == null) continue;

                    for (int i = 0; i < matches.length(); i++) {
                        JSONObject match = matches.optJSONObject(i);
                        if (match == null) continue;
                        JSONObject local = match.optJSONObject("local_team");
                        JSONObject visitor = match.optJSONObject("visitor_team");
                        if (local == null || visitor == null) continue;
                        if (!isAsCeltas(local.optString("name", ""))
                                && !isAsCeltas(visitor.optString("name", ""))) {
                            continue;
                        }

                        JSONObject dateObject = match.optJSONObject("date");
                        String dateText = dateObject == null
                                ? "" : dateObject.optString("date", "");
                        if (dateText.length() < 10) continue;

                        JSONObject item = new JSONObject();
                        item.put("date", dateText.substring(0, 10));
                        item.put("home", cleanName(local.optString("name", "")));
                        item.put("away", cleanName(visitor.optString("name", "")));
                        item.put("homeLogo", cleanShield(local.optString("shield", "")));
                        item.put("awayLogo", cleanShield(visitor.optString("shield", "")));
                        items.put(item);
                    }
                }
            }

            JSONObject result = new JSONObject();
            result.put("items", items);
            result.put("season", season);
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private static String optUrl(JSONObject object, String key) {
        String value = object.optString(key, "");
        return value.isEmpty() ? null : value;
    }

    private static String cleanShield(String shield) {
        if (shield == null || shield.isEmpty()) return "";
        return shield.replace("size=60x", "size=120x");
    }

    private static boolean isAsCeltas(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).contains("celta");
    }

    private static int parseGoals(String goals) {
        if (goals == null) return -1;
        try {
            return Integer.parseInt(goals.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String cleanName(String name) {
        if (name == null) return "";
        String cleaned = name.replaceAll("\\s*\\(W\\)\\s*$", "")
                .replaceAll("\\s+W\\s*$", "")
                .trim();
        return cleaned.replaceAll("\\s+", " ");
    }

    private static String httpPost(String url, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept-Language", "es-ES,es;q=0.9");
        conn.setRequestProperty("Content-Type",
                "application/x-www-form-urlencoded; charset=UTF-8");
        conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        try {
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code != 200) throw new RuntimeException("HTTP " + code);
            return read(conn);
        } finally {
            conn.disconnect();
        }
    }

    private static String read(HttpURLConnection conn) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static JSONObject loadCache(Context context) {
        File file = new File(context.getFilesDir(), CACHE_FILE);
        if (!file.exists()) return new JSONObject();
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int read = in.read(buffer);
            if (read <= 0) return new JSONObject();
            return new JSONObject(new String(buffer, 0, read, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static void saveCache(Context context, JSONObject cache) {
        String json = cache.toString();
        File file = new File(context.getFilesDir(), CACHE_FILE);
        File tmp = new File(context.getFilesDir(), CACHE_FILE + ".tmp");

        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return;
        }

        if (!tmp.renameTo(file)) {
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
                // Sin caché se vuelve a consultar la próxima vez.
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private static JSONObject loadSeasonCache(Context context) {
        File file = new File(context.getFilesDir(), SEASON_CACHE_FILE);
        if (!file.exists()) return new JSONObject();
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int read = in.read(buffer);
            if (read <= 0) return new JSONObject();
            return new JSONObject(new String(buffer, 0, read, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static void saveSeasonCache(Context context, JSONObject cache) {
        String json = cache.toString();
        File file = new File(context.getFilesDir(), SEASON_CACHE_FILE);
        File tmp = new File(context.getFilesDir(), SEASON_CACHE_FILE + ".tmp");

        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return;
        }

        if (!tmp.renameTo(file)) {
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
                // Sin caché se vuelve a consultar la próxima vez.
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }
}
