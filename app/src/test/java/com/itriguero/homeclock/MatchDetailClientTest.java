package com.itriguero.homeclock;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MatchDetailClientTest {

    private static final long GAME_ID = 4750793L;

    @Test
    public void buildsExactGameUrl() {
        assertEquals("https://webws.365scores.com/web/game/"
                        + "?appTypeId=5&langId=14&timezoneName=Europe/Madrid"
                        + "&userCountryId=2&gameId=4750793",
                MatchDetailClient.buildGameUrl(GAME_ID));
    }

    @Test
    public void buildsExactStatsUrl() {
        assertEquals("https://webws.365scores.com/web/game/stats/"
                        + "?appTypeId=5&langId=14&timezoneName=Europe/Madrid"
                        + "&userCountryId=2&games=4750793",
                MatchDetailClient.buildStatsUrl(GAME_ID));
    }

    @Test
    public void gateRejectsNullWrongSourceAndInvalidIdWithoutNetwork() {
        SourceResult<MatchDetail> nullMatch = MatchDetailClient.fetch(null, true);
        assertEquals(SourceState.EMPTY, nullMatch.state);

        Match wrongSource = match(GAME_ID, "rfef", MatchStatus.FINISHED);
        SourceResult<MatchDetail> otherSource = MatchDetailClient.fetch(wrongSource, true);
        assertEquals(SourceState.EMPTY, otherSource.state);

        Match invalidId = match(0L, "365scores", MatchStatus.FINISHED);
        SourceResult<MatchDetail> noId = MatchDetailClient.fetch(invalidId, true);
        assertEquals(SourceState.EMPTY, noId.state);

        assertFalse(MatchDetailClient.canFetch(wrongSource));
        assertFalse(MatchDetailClient.canFetch(invalidId));
        assertTrue(MatchDetailClient.canFetch(match(GAME_ID, "365scores",
                MatchStatus.FINISHED)));
    }

    @Test
    public void mapsHttpFailures() {
        SourceResult<MatchDetail> notFound = MatchDetailClient.httpFailure(404, System.nanoTime());
        assertEquals(SourceState.EMPTY, notFound.state);
        assertNull(notFound.data);

        SourceResult<MatchDetail> tooMany = MatchDetailClient.httpFailure(429, System.nanoTime());
        assertEquals(SourceState.HTTP_ERROR, tooMany.state);
        assertEquals("HTTP 429", tooMany.error);

        SourceResult<MatchDetail> serverError = MatchDetailClient.httpFailure(503, System.nanoTime());
        assertEquals(SourceState.HTTP_ERROR, serverError.state);
        assertEquals("HTTP 503", serverError.error);
    }

    @Test
    public void classifiesNetworkExceptions() {
        SourceResult<MatchDetail> timeout = MatchDetailClient.exceptionResult(
                new SocketTimeoutException("timeout"), System.nanoTime());
        assertEquals(SourceState.TIMEOUT, timeout.state);

        SourceResult<MatchDetail> offline = MatchDetailClient.exceptionResult(
                new UnknownHostException("sin dns"), System.nanoTime());
        assertEquals(SourceState.OFFLINE, offline.state);

        SourceResult<MatchDetail> io = MatchDetailClient.exceptionResult(
                new IOException("conexión cortada"), System.nanoTime());
        assertEquals(SourceState.OFFLINE, io.state);

        SourceResult<MatchDetail> parse = MatchDetailClient.exceptionResult(
                new JSONException("json roto"), System.nanoTime());
        assertEquals(SourceState.PARSE_ERROR, parse.state);

        SourceResult<MatchDetail> unknown = MatchDetailClient.exceptionResult(
                new IllegalStateException("raro"), System.nanoTime());
        assertEquals(SourceState.ERROR, unknown.state);
    }

    @Test
    public void buildsDetailFromFixturesWithStatsAndTv() throws Exception {
        Match match = match(GAME_ID, "365scores", MatchStatus.FINISHED);

        SourceResult<MatchDetail> result = MatchDetailClient.buildResult(
                match, fixture("scores365_game_football.json"),
                fixture("scores365_stats_football.json"), true, 123_456L, 42L);

        assertEquals(SourceState.OK, result.state);
        assertFalse(result.fromCache);
        assertEquals(123_456L, result.fetchedAt);
        assertEquals(42L, result.durationMs);
        assertNotNull(result.data);
        assertEquals(GAME_ID, result.data.gameId);
        assertEquals(123_456L, result.data.fetchedAt);
        assertFalse(result.data.fromCache);
        assertTrue(result.data.hasStats);
        assertEquals(6, result.data.statCategories.size());
        assertEquals(2, result.data.tvNetworks.size());
        assertEquals("Movistar Plus+", result.data.tvNetworks.get(0));
    }

    @Test
    public void statsFailureKeepsGameDetailWithShortError() throws Exception {
        Match match = match(GAME_ID, "365scores", MatchStatus.FINISHED);

        SourceResult<MatchDetail> result = MatchDetailClient.buildResult(
                match, fixture("scores365_game_football.json"), null,
                true, 999L, 10L);

        assertEquals(SourceState.OK, result.state);
        assertNotNull(result.data);
        assertFalse(result.data.hasStats);
        assertEquals("Sin estadísticas", result.data.statsError);
        assertFalse(result.data.events.isEmpty());
        assertNotNull(result.data.homeLineup);
        assertFalse(result.data.homeLineup.starters.isEmpty());
    }

    @Test
    public void doesNotRequestStatsForScheduledGameWithoutHasStats() throws Exception {
        Match match = match(4900001L, "365scores", MatchStatus.SCHEDULED);

        SourceResult<MatchDetail> result = MatchDetailClient.buildResult(
                match, fixture("scores365_game_scheduled.json"),
                fixture("scores365_stats_football.json"), true, 999L, 10L);

        assertEquals(SourceState.OK, result.state);
        assertNotNull(result.data);
        assertFalse(result.data.hasStats);
        assertEquals("", result.data.statsError);
        assertEquals(1, result.data.tvNetworks.size());
        assertEquals("La 1", result.data.tvNetworks.get(0));
    }

    @Test
    public void missingGameReturnsEmpty() throws Exception {
        SourceResult<MatchDetail> result = MatchDetailClient.buildResult(
                match(GAME_ID, "365scores", MatchStatus.FINISHED),
                new JSONObject().put("sports", "nada"), null,
                true, 1L, 2L);

        assertEquals(SourceState.EMPTY, result.state);
        assertNull(result.data);
    }

    @Test
    public void scheduledHasStatsFlagForcesStatsRequest() throws Exception {
        JSONObject root = fixture("scores365_game_scheduled.json");
        root.getJSONObject("game").put("hasStats", true);
        Match match = match(4900001L, "365scores", MatchStatus.SCHEDULED);

        assertTrue(MatchDetailClient.shouldLoadStats(match, root));
        assertFalse(MatchDetailClient.shouldLoadStats(
                match(4900001L, "365scores", MatchStatus.CANCELLED),
                fixture("scores365_game_scheduled.json")));
    }

    private static Match match(long id, String sourceId, MatchStatus status) {
        Match match = new Match();
        match.id = id;
        match.sourceId = sourceId;
        match.sport = "Fútbol";
        match.competition = "España, LaLiga";
        match.startMillis = 1_760_000_000_000L;
        match.status = status;
        match.statusText = status == MatchStatus.FINISHED ? "Finalizado" : "Prog.";
        return match;
    }

    private static JSONObject fixture(String name) throws Exception {
        try (InputStream input = MatchDetailClientTest.class
                .getResourceAsStream("/fixtures/" + name)) {
            assertNotNull("Falta el fixture " + name, input);
            return new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
