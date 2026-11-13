package com.itriguero.homeclock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cliente del API público (no oficial) de 365Scores.
 *
 * No requiere registro ni API key. Devuelve los partidos de los equipos
 * seguidos para una fecha, con estado, marcador y minuto/periodo en directo.
 */
public final class MatchesClient {

    private static final String BASE = "https://webws.365scores.com/web/games/";
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    // Real Madrid, Rayo, Liverpool, España masculino y Real Madrid Baloncesto.
    private static final String TRACKED_IDS_CSV = "131,174,108,5050,204";
    private static final Set<Long> TRACKED_IDS = new HashSet<>(
            Arrays.asList(131L, 174L, 108L, 5050L, 204L));

    private MatchesClient() {
    }

    public static List<Match> fetch(LocalDate date, String timezoneId) throws Exception {
        String d = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        List<Match> all = fetchGames(d, timezoneId, date);
        sortMatches(all);
        return all;
    }

    public static void sortMatches(List<Match> list) {
        Map<Long, Match> byId = new LinkedHashMap<>();
        for (Match m : list) byId.put(m.id, m);

        list.clear();
        list.addAll(byId.values());
        Collections.sort(list, (a, b) -> {
            int ra = rank(a);
            int rb = rank(b);
            if (ra != rb) return ra - rb;
            if (a.isFinished()) return Long.compare(b.startMillis, a.startMillis);
            return Long.compare(a.startMillis, b.startMillis);
        });
    }

    private static int rank(Match m) {
        if (m.isInPlay()) return 0;
        if (m.isScheduled()) return 1;
        if (m.isPostponed()) return 2;
        if (m.isFinished()) return 3;
        if (m.isCancelled()) return 4;
        return 5;
    }

    private static long midnight(LocalDate date, ZoneId zone) {
        return date == null ? 0L : date.atStartOfDay(zone).toInstant().toEpochMilli();
    }

    private static ZoneId zoneOrMadrid(String timezoneId) {
        if (timezoneId == null || timezoneId.trim().isEmpty()) return MADRID;
        try {
            return ZoneId.of(timezoneId);
        } catch (Exception e) {
            return MADRID;
        }
    }

    private static String logoUrl(long competitorId) {
        return "https://imagecache.365scores.com/image/upload/f_png,w_200,h_200,"
                + "c_limit,q_auto:eco/v1/Competitors/" + competitorId;
    }

    private static String sportName(int sportId) {
        if (sportId == 1) return "Fútbol";
        if (sportId == 2) return "Baloncesto";
        return "Otros";
    }

    /**
     * Construye la URL sin android.net.Uri para que el cliente pueda probarse
     * en la JVM (el contrato diario de fuentes) además de en el dispositivo.
     */
    private static String buildUrl(String date, String timezoneId) {
        try {
            return BASE + "?appTypeId=5&langId=14"
                    + "&timezoneName=" + URLEncoder.encode(timezoneId, "UTF-8")
                    + "&userCountryId=225"
                    + "&competitors=" + URLEncoder.encode(TRACKED_IDS_CSV, "UTF-8")
                    + "&startDate=" + URLEncoder.encode(date, "UTF-8")
                    + "&endDate=" + URLEncoder.encode(date, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 no disponible", e);
        }
    }

    private static List<Match> fetchGames(String date, String timezoneId,
                                          LocalDate localDate) throws Exception {
        String url = buildUrl(date, timezoneId);

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "HomeClock/1.0");

        try {
            int code = conn.getResponseCode();
            if (code != 200) throw new RuntimeException("HTTP " + code);

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }

            JSONObject root = new JSONObject(sb.toString());
            return parseGames(root.optJSONArray("games"), TRACKED_IDS,
                    localDate, zoneOrMadrid(timezoneId));
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Parsea una respuesta de 365Scores. Se expone al paquete para poder
     * probarla con fixtures sin depender de la red ni de Android.
     */
    static List<Match> parseGames(JSONArray games, Set<Long> trackedIds) throws Exception {
        return parseGames(games, trackedIds, LocalDate.now(MADRID), MADRID);
    }

    static List<Match> parseGames(JSONArray games, Set<Long> trackedIds,
                                  LocalDate date) throws Exception {
        return parseGames(games, trackedIds, date, MADRID);
    }

    static List<Match> parseGames(JSONArray games, Set<Long> trackedIds,
                                  LocalDate date, ZoneId zone) throws Exception {
        List<Match> result = new ArrayList<>();
        if (games == null) return result;
        ZoneId matchZone = zone == null ? MADRID : zone;

        for (int i = 0; i < games.length(); i++) {
            JSONObject g = games.getJSONObject(i);
            JSONObject h = g.optJSONObject("homeCompetitor");
            JSONObject a = g.optJSONObject("awayCompetitor");
            if (h == null || a == null) continue;

            long homeId = h.optLong("id");
            long awayId = a.optLong("id");
            boolean homeTracked = trackedIds.contains(homeId);
            boolean awayTracked = trackedIds.contains(awayId);
            if (!homeTracked && !awayTracked) continue;

            int rawGroup = g.optInt("statusGroup", 0);
            String statusText = g.optString("statusText", "").trim();

            Match m = new Match();
            m.id = g.optLong("id");
            m.sourceId = Scores365Source.ID;
            m.sport = sportName(g.optInt("sportId", 0));
            m.competition = g.optString("competitionDisplayName", "Competición").trim();

            // Si la fuente no da hora (o la marca explícitamente como TBD), se
            // usa la medianoche del día consultado: no se inventa una hora.
            String startText = g.isNull("startTime")
                    ? "" : g.optString("startTime", "").trim();
            boolean explicitTbd = g.optBoolean("timeTbd", false)
                    || g.optBoolean("startTimeTbd", false);
            if (startText.isEmpty() || explicitTbd) {
                m.timeTbd = true;
                m.startMillis = midnight(date, matchZone);
            } else {
                try {
                    m.startMillis = OffsetDateTime.parse(startText)
                            .toInstant().toEpochMilli();
                    m.timeTbd = false;
                } catch (Exception e) {
                    continue;
                }
            }

            m.status = MatchStatusMapper.from365(rawGroup, statusText);
            m.statusText = statusText;
            m.gameTimeDisplay = g.optString("gameTimeDisplay", "").trim();
            m.gameTime = g.optDouble("gameTime", -1);

            m.homeId = homeId;
            m.awayId = awayId;
            m.homeName = h.optString("name", "").trim();
            m.awayName = a.optString("name", "").trim();
            m.homeLogoUrl = logoUrl(homeId);
            m.awayLogoUrl = logoUrl(awayId);

            double homeScore = h.isNull("score") ? -1 : h.optDouble("score", -1);
            double awayScore = a.isNull("score") ? -1 : a.optDouble("score", -1);
            m.scoreKnown = !h.isNull("score") && !a.isNull("score")
                    && homeScore >= 0 && awayScore >= 0;
            if (m.isPostponed() || m.isCancelled()) {
                m.scoreKnown = false;
                homeScore = -1;
                awayScore = -1;
            }
            m.homeScore = homeScore;
            m.awayScore = awayScore;

            m.trackedIsHome = homeTracked;
            m.trackedName = homeTracked ? m.homeName : m.awayName;
            m.opponentName = homeTracked ? m.awayName : m.homeName;

            result.add(m);
        }
        return result;
    }
}
