package com.itriguero.homeclock;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MatchesClientParserTest {

    @Test
    public void parsesLiveScheduledPostponedAndCancelledFromFixture() throws Exception {
        JSONArray games = fixture().getJSONArray("games");
        Set<Long> tracked = new HashSet<>(Arrays.asList(5050L, 204L, 131L, 174L, 108L));

        List<Match> matches = MatchesClient.parseGames(games, tracked);

        assertEquals(4, matches.size());

        Match live = find(matches, 1001);
        assertNotNull(live);
        assertEquals(MatchStatus.LIVE, live.status);
        assertEquals("365scores", live.sourceId);
        assertEquals("Fútbol", live.sport);
        assertEquals("21'", live.gameTimeDisplay);
        assertTrue(live.scoreKnown);
        assertEquals(0.0, live.homeScore, 0.001);
        assertEquals(0.0, live.awayScore, 0.001);

        Match scheduled = find(matches, 1002);
        assertNotNull(scheduled);
        assertEquals(MatchStatus.SCHEDULED, scheduled.status);
        assertEquals("Baloncesto", scheduled.sport);
        assertFalse(scheduled.scoreKnown);

        Match postponed = find(matches, 1003);
        assertNotNull(postponed);
        assertEquals(MatchStatus.POSTPONED, postponed.status);
        assertFalse(postponed.scoreKnown);
        assertEquals(-1.0, postponed.homeScore, 0.001);

        Match cancelled = find(matches, 1004);
        assertNotNull(cancelled);
        assertEquals(MatchStatus.CANCELLED, cancelled.status);
        assertFalse(cancelled.scoreKnown);
    }

    @Test
    public void gameWithoutStartTimeUsesMidnightAndMarksTimeTbd() throws Exception {
        LocalDate date = LocalDate.of(2026, 11, 12);
        ZoneId madrid = ZoneId.of("Europe/Madrid");
        JSONArray games = new JSONArray("[{"
                + "\"id\":2001,\"sportId\":1,"
                + "\"competitionDisplayName\":\"LaLiga\","
                + "\"statusGroup\":2,\"statusText\":\"Prog.\","
                + "\"homeCompetitor\":{\"id\":131,\"name\":\"Real Madrid\",\"score\":-1},"
                + "\"awayCompetitor\":{\"id\":174,\"name\":\"Rayo Vallecano\",\"score\":-1}"
                + "}]");

        List<Match> matches = MatchesClient.parseGames(games, tracked(), date, madrid);

        assertEquals(1, matches.size());
        Match match = matches.get(0);
        assertTrue(match.timeTbd);
        assertEquals(date.atStartOfDay(madrid).toInstant().toEpochMilli(),
                match.startMillis);
        assertEquals(MatchStatus.SCHEDULED, match.status);
    }

    @Test
    public void gameWithStartTimeKeepsExactKickoffAndClearsTimeTbd() throws Exception {
        LocalDate date = LocalDate.of(2026, 11, 12);
        String startTime = "2026-11-12T20:45:00+01:00";
        JSONArray games = new JSONArray("[{"
                + "\"id\":2002,\"sportId\":1,"
                + "\"competitionDisplayName\":\"LaLiga\","
                + "\"startTime\":\"" + startTime + "\","
                + "\"statusGroup\":2,\"statusText\":\"Prog.\","
                + "\"homeCompetitor\":{\"id\":131,\"name\":\"Real Madrid\",\"score\":-1},"
                + "\"awayCompetitor\":{\"id\":174,\"name\":\"Rayo Vallecano\",\"score\":-1}"
                + "}]");

        List<Match> matches = MatchesClient.parseGames(
                games, tracked(), date, ZoneId.of("Europe/Madrid"));

        assertEquals(1, matches.size());
        Match match = matches.get(0);
        assertFalse(match.timeTbd);
        assertEquals(OffsetDateTime.parse(startTime).toInstant().toEpochMilli(),
                match.startMillis);
    }

    private static Set<Long> tracked() {
        return new HashSet<>(Arrays.asList(5050L, 204L, 131L, 174L, 108L));
    }

    private static Match find(List<Match> matches, long id) {
        for (Match match : matches) {
            if (match.id == id) return match;
        }
        return null;
    }

    private static JSONObject fixture() throws Exception {
        try (InputStream input = MatchesClientParserTest.class
                .getResourceAsStream("/fixtures/scores365_games.json")) {
            assertNotNull(input);
            return new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
