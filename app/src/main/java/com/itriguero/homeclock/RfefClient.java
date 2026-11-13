package com.itriguero.homeclock;

import android.content.Context;
import android.text.Html;

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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cliente de la RFEF para As Celtas (Celta Femenino).
 *
 * Usa la web oficial, sin registro ni API key. Consulta la competición
 * jornada a jornada y guarda en disco un calendario compacto para no repetir
 * peticiones. La RFEF da programación y resultado final, pero no minuto a
 * minuto.
 */
public final class RfefClient {

    private static final String RESULTS_URL =
            "https://rfef.es/es/resultados?competition=33836146&group=33836147";
    private static final String AJAX_URL = "https://rfef.es/novanet-content";
    private static final String GROUP_ID = "33836147";
    private static final String COMPETITION = "2ª Federación Femenina";
    private static final String TRACKED_NAME = "As Celtas";
    private static final long TRACKED_ID = -21677726L;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) HomeClock/1.0";

    private static final String CACHE_FILE = "rfef_cache.json";
    private static final long JOURNEY_MAP_TTL_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long IDLE_TTL_MS = 6L * 60 * 60 * 1000;
    private static final long LIVE_TTL_MS = 5L * 60 * 1000;
    private static final long MATCH_DURATION_MS = 3L * 60 * 60 * 1000;
    private static final long RESULT_SETTLE_MS = 2L * 60 * 60 * 1000;
    private static final long REFRESH_BEFORE_MS = 30L * 60 * 1000;
    private static final int JOURNEY_DATE_MARGIN_DAYS = 3;

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DMY_DASH = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter YMD = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final Pattern JOURNEY_SELECT = Pattern.compile(
            "<select[^>]*id=\"edit-journey\"[^>]*>(.*?)</select>", Pattern.DOTALL);
    private static final Pattern JOURNEY_OPTION = Pattern.compile(
            "<option value=\"(\\d+)\"[^>]*>\\s*\\d+\\s*\\((\\d{2}-\\d{2}-\\d{4})\\)\\s*</option>");
    private static final Pattern ROW_SPLIT = Pattern.compile(
            "<div class=\"row mx-auto align-items-stretch");
    private static final Pattern HOME = Pattern.compile(
            "<div class=\"text-center fw-bold\">\\s*(.*?)\\s*</div>", Pattern.DOTALL);
    private static final Pattern AWAY = Pattern.compile(
            "<div class=\"fw-bold text-center text-md-right\">\\s*(.*?)\\s*</div>",
            Pattern.DOTALL);
    private static final Pattern DATE_TIME = Pattern.compile(
            "(\\d{2}/\\d{2}/\\d{4})(?:\\s*-\\s*(\\d{2}:\\d{2}))?");
    private static final Pattern SCORE = Pattern.compile(
            "<div class=\"text-lg\">\\s*(.*?)\\s*</div>", Pattern.DOTALL);
    private static final Pattern SCORE_NUMBERS = Pattern.compile("(\\d+)\\s*-\\s*(\\d+)");
    private static final Pattern ACTA = Pattern.compile("CodActa=(\\d+)");

    private static final Object LOCK = new Object();

    private RfefClient() {
    }

    /**
     * Resultado de una consulta a la RFEF. {@code fromCache} solo es true
     * cuando alguna parte de los datos se sirvió de la caché local porque la
     * petición de red falló. El uso normal de la caché por TTL no se marca:
     * no es un fallback, es el comportamiento esperado.
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
            boolean fromCache = ensureJourneys(context, cache);

            JSONArray journeys = cache.optJSONArray("journeys");
            JSONObject data = cache.optJSONObject("data");
            if (data == null) {
                data = new JSONObject();
                cache.put("data", data);
            }
            if (journeys == null) journeys = new JSONArray();

            long now = System.currentTimeMillis();
            List<Match> result = new ArrayList<>();

            for (int i = 0; i < journeys.length(); i++) {
                JSONArray pair = journeys.optJSONArray(i);
                if (pair == null || pair.length() < 2) continue;

                int journey = pair.optInt(0);
                LocalDate weekend;
                try {
                    weekend = LocalDate.parse(pair.optString(1), YMD);
                } catch (Exception e) {
                    continue;
                }
                long distance = Math.abs(ChronoUnit.DAYS.between(weekend, date));
                if (distance > JOURNEY_DATE_MARGIN_DAYS) continue;

                String key = String.valueOf(journey);
                JSONObject entry = data.optJSONObject(key);
                if (entry == null || needsRefresh(entry, date, now)) {
                    JSONObject fresh = fetchJourney(journey);
                    if (fresh != null) {
                        fresh.put("fetched", now);
                        data.put(key, fresh);
                        saveCache(context, cache);
                        entry = fresh;
                    } else if (entry != null) {
                        // La jornada no se pudo refrescar: se usa la copia local.
                        fromCache = true;
                    }
                }
                if (entry != null) {
                    addMatches(result, entry.optJSONArray("items"), journey, date, now);
                }
            }

            return new FetchResult(result, fromCache);
        }
    }

    /**
     * @return true si el calendario se sirvió de la caché porque la red falló.
     */
    private static boolean ensureJourneys(Context context, JSONObject cache) throws Exception {
        long updated = cache.optLong("journeysUpdated", 0);
        JSONArray existing = cache.optJSONArray("journeys");
        boolean valid = existing != null && existing.length() > 0;

        if (valid && System.currentTimeMillis() - updated < JOURNEY_MAP_TTL_MS) {
            return false;
        }

        JSONArray parsed = null;
        try {
            parsed = parseJourneys(httpGet(RESULTS_URL));
        } catch (Exception ignored) {
            // Si la RFEF no responde, se usa el calendario que ya tuviéramos.
        }

        if (parsed != null && parsed.length() > 0) {
            cache.put("journeys", parsed);
            cache.put("journeysUpdated", System.currentTimeMillis());
            saveCache(context, cache);
            return false;
        }
        if (!valid) {
            throw new RuntimeException("RFEF: no se pudo obtener el calendario");
        }
        return true;
    }

    private static boolean needsRefresh(JSONObject entry, LocalDate date, long now) {
        long fetched = entry.optLong("fetched", 0);
        if (fetched <= 0) return true;

        long age = now - fetched;
        JSONArray items = entry.optJSONArray("items");
        long kickoff = 0;
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null || !date.toString().equals(item.optString("date"))) continue;
                long start = item.optLong("start", 0);
                if (start > 0 && (kickoff == 0 || start < kickoff)) kickoff = start;
            }
        }

        if (kickoff > 0
                && now >= kickoff - REFRESH_BEFORE_MS
                && now <= kickoff + MATCH_DURATION_MS) {
            return age > LIVE_TTL_MS;
        }
        return age > IDLE_TTL_MS;
    }

    private static JSONObject fetchJourney(int journey) {
        try {
            String parameters = "{\"group\":\"" + GROUP_ID + "\",\"journey\":\""
                    + journey + "\",\"type\":\"results\"}";
            String body = "parameters=" + URLEncoder.encode(parameters, "UTF-8");
            String html = httpPost(AJAX_URL, body);
            if (html.contains("Error al obtener los datos")) return null;

            JSONObject entry = new JSONObject();
            entry.put("items", parseMatches(html));
            return entry;
        } catch (Exception e) {
            return null;
        }
    }

    static JSONArray parseJourneys(String html) throws Exception {
        Matcher select = JOURNEY_SELECT.matcher(html);
        if (!select.find()) return null;

        JSONArray result = new JSONArray();
        Matcher option = JOURNEY_OPTION.matcher(select.group(1));
        while (option.find()) {
            LocalDate weekend = LocalDate.parse(option.group(2), DMY_DASH);
            JSONArray pair = new JSONArray();
            pair.put(Integer.parseInt(option.group(1)));
            pair.put(weekend.format(YMD));
            result.put(pair);
        }
        return result;
    }

    private static JSONArray parseMatches(String html) throws Exception {
        JSONArray items = new JSONArray();
        String[] chunks = ROW_SPLIT.split(html);

        for (int i = 1; i < chunks.length; i++) {
            String chunk = chunks[i];
            String lowerChunk = chunk.toLowerCase(Locale.ROOT);
            String specialStatus = "";
            if (lowerChunk.contains("aplazado") || lowerChunk.contains("suspend")) {
                specialStatus = "POSTPONED";
            } else if (lowerChunk.contains("cancelado")) {
                specialStatus = "CANCELLED";
            }

            Matcher homeMatcher = HOME.matcher(chunk);
            Matcher awayMatcher = AWAY.matcher(chunk);
            Matcher dateMatcher = DATE_TIME.matcher(chunk);
            if (!homeMatcher.find() || !awayMatcher.find() || !dateMatcher.find()) continue;

            String home = clean(homeMatcher.group(1));
            String away = clean(awayMatcher.group(1));
            if (!isAsCeltas(home) && !isAsCeltas(away)) continue;

            String time = dateMatcher.group(2);
            LocalDate matchDate;
            LocalTime matchTime;
            try {
                matchDate = LocalDate.parse(dateMatcher.group(1), DMY);
                matchTime = time == null ? LocalTime.MIDNIGHT : LocalTime.parse(time);
            } catch (Exception e) {
                continue;
            }

            int homeScore = -1;
            int awayScore = -1;
            Matcher scoreMatcher = SCORE.matcher(chunk);
            if (scoreMatcher.find()) {
                Matcher numbers = SCORE_NUMBERS.matcher(scoreMatcher.group(1));
                if (numbers.find()) {
                    homeScore = Integer.parseInt(numbers.group(1));
                    awayScore = Integer.parseInt(numbers.group(2));
                }
            }

            JSONObject item = new JSONObject();
            item.put("date", matchDate.format(YMD));
            item.put("time", time == null ? "" : time);
            item.put("start", matchDate.atTime(matchTime).atZone(MADRID)
                    .toInstant().toEpochMilli());
            item.put("home", home);
            item.put("away", away);
            item.put("hs", homeScore);
            item.put("as", awayScore);
            if (!specialStatus.isEmpty()) item.put("status", specialStatus);

            Matcher actaMatcher = ACTA.matcher(chunk);
            if (actaMatcher.find()) item.put("acta", actaMatcher.group(1));

            items.put(item);
        }
        return items;
    }

    private static void addMatches(List<Match> out, JSONArray items, int journey,
                                   LocalDate date, long now) {
        if (items == null) return;

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null || !date.toString().equals(item.optString("date"))) continue;

            long start = item.optLong("start", 0);
            if (start <= 0) continue;

            String home = item.optString("home", "");
            String away = item.optString("away", "");
            boolean homeTracked = isAsCeltas(home);
            int homeScore = item.optInt("hs", -1);
            int awayScore = item.optInt("as", -1);

            Match match = new Match();
            String acta = item.optString("acta", "");
            if (!acta.isEmpty()) {
                try {
                    match.id = -Long.parseLong(acta);
                } catch (NumberFormatException e) {
                    match.id = -(journey * 1000L + i + 1);
                }
            } else {
                match.id = -(journey * 1000L + i + 1);
            }

            match.sourceId = RfefSource.ID;
            match.sport = "Fútbol";
            match.competition = COMPETITION;
            match.startMillis = start;
            match.timeTbd = item.optString("time", "").isEmpty();
            match.statusText = "";
            match.gameTimeDisplay = "";
            match.gameTime = -1;
            match.homeId = homeTracked ? TRACKED_ID : -(100000L + i);
            match.awayId = homeTracked ? -(100000L + i) : TRACKED_ID;
            match.homeName = home;
            match.awayName = away;
            match.homeScore = homeScore;
            match.awayScore = awayScore;
            match.scoreKnown = homeScore >= 0 && awayScore >= 0;
            match.trackedIsHome = homeTracked;
            match.trackedName = TRACKED_NAME;
            match.opponentName = homeTracked ? away : home;

            String specialStatus = item.optString("status", "");
            if ("POSTPONED".equals(specialStatus) || "CANCELLED".equals(specialStatus)) {
                match.status = "POSTPONED".equals(specialStatus)
                        ? MatchStatus.POSTPONED : MatchStatus.CANCELLED;
                match.scoreKnown = false;
                match.homeScore = -1;
                match.awayScore = -1;
                out.add(match);
                continue;
            }

            boolean pastDay = date.isBefore(LocalDate.now(MADRID));
            match.status = MatchTimeStatus.resolve(
                    pastDay, match.timeTbd, match.scoreKnown,
                    start, now, RESULT_SETTLE_MS, MATCH_DURATION_MS);

            out.add(match);
        }
    }

    private static boolean isAsCeltas(String name) {
        return name != null
                && name.toLowerCase(Locale.ROOT).contains("celta");
    }

    private static String clean(String raw) {
        String text = Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString();
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept-Language", "es-ES,es;q=0.9");
        try {
            int code = conn.getResponseCode();
            if (code != 200) throw new RuntimeException("HTTP " + code);
            return read(conn);
        } finally {
            conn.disconnect();
        }
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
}
