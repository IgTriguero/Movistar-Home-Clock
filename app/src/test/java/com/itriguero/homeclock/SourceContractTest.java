package com.itriguero.homeclock;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * Contrato diario contra las fuentes reales.
 *
 * No se ejecuta en el CI normal: cada test empieza con una assumption sobre
 * HOMECLOCK_CONTRACT. El workflow contract.yml la activa a diario para
 * detectar cambios de API o de HTML antes de que rompan la app.
 *
 * Cubierto aquí:
 * - 365Scores, por el camino real de Scores365Source/MatchesClient.
 * - Open-Meteo geocoding, por CitySearchClient.search.
 * - RFEF, solo parseJourneys (parser estático, sin Android).
 * - Celta, solo parseMatch (parser estático, sin Android).
 *
 * Fuera del contrato:
 * - RfefClient.parseMatches es privado y usa android.text.Html; no se puede
 *   invocar desde una unit test JVM sin cambiar producción.
 * - RfefClient.fetch/CeltaClient.fetch necesitan Context (filesDir) y no
 *   aportan nada al parser que ya se valida aquí.
 */
public class SourceContractTest {

    private static final int TIMEOUT_MS = 15_000;
    private static final String RFEF_RESULTS_URL =
            "https://rfef.es/es/resultados?competition=33836146&group=33836147";
    private static final String CELTA_AJAX_URL =
            "https://rccelta.es/wp/wp-admin/admin-ajax.php";
    // Misma UA que RfefClient/CeltaClient: el contrato reproduce la petición real.
    private static final String SOURCE_UA =
            "Mozilla/5.0 (Linux; Android 15) HomeClock/1.0";

    /** Los tests solo corren cuando el workflow diario activa el contrato. */
    private static void requireContract() {
        String flag = System.getenv("HOMECLOCK_CONTRACT");
        if (flag == null) flag = System.getProperty("homeclock.contract");
        assumeTrue("1".equals(flag));
    }

    @Test
    public void scores365ServesCoherentMatchesNearToday() throws Exception {
        requireContract();

        LocalDate today = LocalDate.now();
        List<LocalDate> dates = Arrays.asList(
                today.minusDays(3), today.minusDays(2), today.minusDays(1),
                today,
                today.plusDays(1), today.plusDays(2), today.plusDays(3));

        int daysWithMatches = 0;
        int knownStatuses = 0;
        List<String> emptyDays = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (LocalDate date : dates) {
            SourceResult<List<Match>> result = new Scores365Source().fetch(date);

            // Un reintento: el API público devuelve 429/5xx puntuales.
            if (isFailure(result)) {
                Thread.sleep(2_000L);
                result = new Scores365Source().fetch(date);
            }

            assertNotNull("365Scores no devolvió resultado para " + date, result);
            if (!result.isOk()) {
                errors.add(date + " -> " + result.state
                        + (result.error == null ? "" : " (" + result.error + ")"));
                continue;
            }
            if (result.state == SourceState.EMPTY) {
                emptyDays.add(date.toString());
                continue;
            }

            assertNotNull("365Scores devolvió OK sin lista para " + date, result.data);
            assertFalse("365Scores devolvió OK con lista vacía para " + date,
                    result.data.isEmpty());

            daysWithMatches++;
            for (Match match : result.data) {
                assertCoherent365Match(date, match);
                if (match.status != MatchStatus.UNKNOWN) knownStatuses++;
            }
        }

        if (!errors.isEmpty()) {
            fail("365Scores falló en " + errors.size() + " fecha(s): " + errors);
        }
        assertTrue("365Scores no devolvió partidos en ninguna fecha de " + dates
                        + ". ¿API caída o parser roto? Fechas vacías: " + emptyDays,
                daysWithMatches > 0);
        assertTrue("365Scores devolvió partidos pero ningún estado reconocible; "
                + "revisa MatchStatusMapper y statusGroup/statusText",
                knownStatuses > 0);
    }

    private static boolean isFailure(SourceResult<?> result) {
        return result != null && result.state != SourceState.OK
                && result.state != SourceState.EMPTY;
    }

    private static void assertCoherent365Match(LocalDate date, Match match) {
        assertNotNull("Partido sin estado para " + date, match.status);
        assertEquals("sourceId inesperado en el partido " + match.id,
                Scores365Source.ID, match.sourceId);

        assertNotNull("homeName nulo en el partido " + match.id, match.homeName);
        assertNotNull("awayName nulo en el partido " + match.id, match.awayName);
        assertFalse("homeName vacío en el partido " + match.id,
                match.homeName.trim().isEmpty());
        assertFalse("awayName vacío en el partido " + match.id,
                match.awayName.trim().isEmpty());

        assertNotNull("trackedName nulo en el partido " + match.id, match.trackedName);
        assertTrue("trackedName no coincide con ningún equipo en el partido " + match.id,
                match.trackedName.equals(match.homeName)
                        || match.trackedName.equals(match.awayName));

        assertTrue("startMillis inválido en el partido " + match.id,
                match.startMillis > 0);
        long dayStart = date.atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli();
        long sixHours = 6L * 60 * 60 * 1000;
        long thirtyHours = 30L * 60 * 60 * 1000;
        assertTrue("startMillis fuera de la fecha " + date + " para el partido "
                        + match.id,
                match.startMillis >= dayStart - sixHours
                        && match.startMillis <= dayStart + thirtyHours);

        // Un partido en juego o terminado no puede empezar en el futuro lejano.
        if (match.isInPlay() || match.isFinished()) {
            assertTrue("Partido " + match.status + " con inicio futuro: " + match.id,
                    match.startMillis <= System.currentTimeMillis() + sixHours);
        }
    }

    @Test
    public void openMeteoGeocodingFindsTresCantosWithValidCoordinates() throws Exception {
        requireContract();

        List<CitySearchClient.Result> results = CitySearchClient.search("Tres Cantos");

        assertNotNull("Open-Meteo no devolvió lista para Tres Cantos", results);
        assertFalse("Open-Meteo no devolvió resultados para Tres Cantos",
                results.isEmpty());

        CitySearchClient.Result tresCantos = null;
        for (CitySearchClient.Result result : results) {
            assertTrue("Latitud inválida para " + result.name + ": " + result.lat,
                    result.lat >= -90.0 && result.lat <= 90.0);
            assertTrue("Longitud inválida para " + result.name + ": " + result.lon,
                    result.lon >= -180.0 && result.lon <= 180.0);
            if (tresCantos == null
                    && result.name.toLowerCase(Locale.ROOT).contains("tres cantos")) {
                tresCantos = result;
            }
        }

        assertNotNull("Open-Meteo no devolvió ningún resultado llamado Tres Cantos: "
                + describe(results), tresCantos);
    }

    private static String describe(List<CitySearchClient.Result> results) {
        StringBuilder sb = new StringBuilder();
        for (CitySearchClient.Result result : results) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(result.name).append(" (").append(result.lat)
                    .append(", ").append(result.lon).append(')');
        }
        return sb.toString();
    }

    @Test
    public void rfefJourneyCalendarStillMatchesParser() throws Exception {
        requireContract();

        String html = httpGet(RFEF_RESULTS_URL);
        assertNotNull("RFEF devolvió una respuesta nula", html);
        assertFalse("RFEF devolvió una página vacía", html.trim().isEmpty());

        JSONArray journeys = RfefClient.parseJourneys(html);
        assertNotNull("El HTML de RFEF ya no contiene <select id=\"edit-journey\">; "
                + "revisa RfefClient.JOURNEY_SELECT", journeys);
        assertTrue("RFEF no expone ninguna jornada en el HTML", journeys.length() > 0);

        LocalDate min = LocalDate.of(2020, 1, 1);
        LocalDate max = LocalDate.now().plusYears(2);
        for (int i = 0; i < journeys.length(); i++) {
            JSONArray pair = journeys.getJSONArray(i);
            assertTrue("Jornada con formato inesperado en la posición " + i,
                    pair.length() >= 2);

            int journey = pair.getInt(0);
            assertTrue("Número de jornada inválido: " + journey, journey > 0);

            LocalDate weekend = LocalDate.parse(pair.getString(1));
            assertFalse("Fecha de jornada fuera de rango: " + weekend,
                    weekend.isBefore(min) || weekend.isAfter(max));
        }
    }

    @Test
    public void celtaMatchParserHandlesLiveCalendar() throws Exception {
        requireContract();

        LocalDate today = LocalDate.now();
        List<String> seasons = Arrays.asList(
                SeasonResolver.forDate(today),
                SeasonResolver.forDate(today.minusYears(1)));

        List<JSONObject> celtaMatches = new ArrayList<>();
        boolean sawCalendar = false;
        String lastResponse = "";

        for (String season : seasons) {
            JSONObject root = new JSONObject(postForm(
                    CELTA_AJAX_URL, celtaCompetitionBody(season)));
            JSONObject calendar = root.optJSONObject("match_calendar");
            if (calendar != null) {
                sawCalendar = true;
                collectCeltaMatches(calendar, celtaMatches);
            } else {
                lastResponse = root.toString();
            }
        }

        assertTrue("Celta ya no devuelve match_calendar en la competición. Respuesta: "
                + truncate(lastResponse), sawCalendar);

        if (celtaMatches.isEmpty()) {
            // Sin partidos de Copa en las temporadas consultadas, se prueba el
            // calendario completo (liga + copa) antes de rendirse.
            JSONObject root = new JSONObject(postForm(CELTA_AJAX_URL,
                    "action=" + encode("get_full_season_matches")
                            + "&team_id=138738"
                            + "&year=" + encode(SeasonResolver.forDate(today))));
            JSONObject calendar = root.optJSONObject("match_calendar");
            if (calendar != null) {
                collectCeltaMatches(calendar, celtaMatches);
            }
        }

        assumeTrue("Celta no publica partidos de As Celtas en las temporadas "
                        + seasons + "; no hay datos reales con los que validar parseMatch",
                !celtaMatches.isEmpty());

        for (JSONObject raw : celtaMatches) {
            JSONObject parsed = CeltaClient.parseMatch(raw);
            assertNotNull("CeltaClient.parseMatch devolvió null para un partido real "
                    + "de As Celtas: " + truncate(raw.toString()), parsed);

            assertTrue("Fecha inválida en Celta: " + parsed.optString("date"),
                    parsed.optString("date").matches("\\d{4}-\\d{2}-\\d{2}"));
            assertTrue("start inválido en Celta: " + parsed.optLong("start"),
                    parsed.optLong("start") > 0);
            assertFalse("Equipo local vacío en Celta",
                    parsed.optString("home").trim().isEmpty());
            assertFalse("Equipo visitante vacío en Celta",
                    parsed.optString("away").trim().isEmpty());
            assertTrue("El partido de Celta no incluye a As Celtas",
                    containsCelta(parsed.optString("home"))
                            || containsCelta(parsed.optString("away")));
        }
    }

    private static String celtaCompetitionBody(String season) throws Exception {
        return "action=" + encode("get_competition_season_matches")
                + "&team_id=138738"
                + "&year=" + encode(season)
                + "&competition=" + encode("copa_de_la_reina");
    }

    private static void collectCeltaMatches(JSONObject calendar, List<JSONObject> out) {
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

                if (containsCelta(local.optString("name", ""))
                        || containsCelta(visitor.optString("name", ""))) {
                    out.add(match);
                }
            }
        }
    }

    private static boolean containsCelta(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).contains("celta");
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", SOURCE_UA);
        conn.setRequestProperty("Accept-Language", "es-ES,es;q=0.9");
        try {
            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code + " en " + url);
            return read(conn);
        } finally {
            conn.disconnect();
        }
    }

    private static String postForm(String url, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("User-Agent", SOURCE_UA);
        conn.setRequestProperty("Accept-Language", "es-ES,es;q=0.9");
        conn.setRequestProperty("Content-Type",
                "application/x-www-form-urlencoded; charset=UTF-8");
        conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        try {
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code + " en " + url);
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

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static String truncate(String text) {
        if (text == null) return "";
        return text.length() <= 300 ? text : text.substring(0, 300) + "…";
    }
}
