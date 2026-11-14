package com.itriguero.homeclock;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MatchDetailRefreshPolicyTest {

    private static final long NOW = 1_760_000_000_000L;

    @Test
    public void finishedMatchNeverPollsAgain() {
        Match match = match(MatchStatus.FINISHED, "Fútbol", NOW + 60_000L);
        assertEquals(Long.MAX_VALUE,
                MatchDetailRefreshPolicy.nextDelay(match, detail(MatchStatus.FINISHED), NOW));
        assertFalse(MatchDetailRefreshPolicy.shouldPoll(match,
                detail(MatchStatus.FINISHED), NOW));
    }

    @Test
    public void liveFootballPollsEvery20Seconds() {
        assertEquals(MatchDetailRefreshPolicy.LIVE_FOOTBALL,
                MatchDetailRefreshPolicy.nextDelay(
                        match(MatchStatus.LIVE, "Fútbol", NOW), null, NOW));
        assertEquals(MatchDetailRefreshPolicy.LIVE_FOOTBALL,
                MatchDetailRefreshPolicy.nextDelay(
                        match(MatchStatus.LIVE, "Fútbol Sala", NOW), null, NOW));
    }

    @Test
    public void liveBasketballPollsEvery15Seconds() {
        assertEquals(MatchDetailRefreshPolicy.LIVE_BASKETBALL,
                MatchDetailRefreshPolicy.nextDelay(
                        match(MatchStatus.LIVE, "Baloncesto", NOW), null, NOW));
    }

    @Test
    public void liveUnknownSportUsesApproximateDelay() {
        assertEquals(MatchDetailRefreshPolicy.LIVE_APPROX,
                MatchDetailRefreshPolicy.nextDelay(
                        match(MatchStatus.LIVE, "Tenis", NOW), null, NOW));
        assertEquals(MatchDetailRefreshPolicy.LIVE_APPROX,
                MatchDetailRefreshPolicy.nextDelay(
                        match(MatchStatus.LIVE_APPROX, "Fútbol", NOW), null, NOW));
    }

    @Test
    public void liveSportFallsBackToDetail() {
        Match match = match(MatchStatus.LIVE, "", NOW);
        MatchDetail detail = detail(MatchStatus.LIVE);
        detail = new MatchDetail(detail.gameId, detail.sourceId, detail.competition,
                "Baloncesto", detail.startMillis, detail.status, detail.statusText,
                detail.gameTimeDisplay, detail.scoreKnown, detail.homeScore,
                detail.awayScore, detail.homeName, detail.awayName, detail.homeId,
                detail.awayId, detail.venue, detail.officials, detail.events,
                detail.homeLineup, detail.awayLineup, detail.statCategories,
                detail.hasStats, detail.statsError, detail.tvNetworks,
                detail.fetchedAt, detail.fromCache);

        assertEquals(MatchDetailRefreshPolicy.LIVE_BASKETBALL,
                MatchDetailRefreshPolicy.nextDelay(match, detail, NOW));
    }

    @Test
    public void scheduledNearKickoffPollsEveryMinute() {
        Match inTenMinutes = match(MatchStatus.SCHEDULED, "Fútbol", NOW + 10L * 60L * 1000L);
        Match inNineMinutes = match(MatchStatus.SCHEDULED, "Fútbol", NOW + 9L * 60L * 1000L);
        Match justStarted = match(MatchStatus.SCHEDULED, "Fútbol", NOW - 1_000L);

        assertEquals(MatchDetailRefreshPolicy.SCHEDULED_NEAR,
                MatchDetailRefreshPolicy.nextDelay(inTenMinutes, null, NOW));
        assertEquals(MatchDetailRefreshPolicy.SCHEDULED_NEAR,
                MatchDetailRefreshPolicy.nextDelay(inNineMinutes, null, NOW));
        assertEquals(MatchDetailRefreshPolicy.SCHEDULED_NEAR,
                MatchDetailRefreshPolicy.nextDelay(justStarted, null, NOW));
        assertTrue(MatchDetailRefreshPolicy.shouldPoll(inNineMinutes, null, NOW));
    }

    @Test
    public void scheduledFarAwayDoesNotPoll() {
        Match far = match(MatchStatus.SCHEDULED, "Fútbol", NOW + 11L * 60L * 1000L);
        Match tomorrow = match(MatchStatus.SCHEDULED, "Fútbol", NOW + 24L * 60L * 60L * 1000L);
        Match noTime = match(MatchStatus.SCHEDULED, "Fútbol", 0L);

        assertEquals(Long.MAX_VALUE,
                MatchDetailRefreshPolicy.nextDelay(far, null, NOW));
        assertEquals(Long.MAX_VALUE,
                MatchDetailRefreshPolicy.nextDelay(tomorrow, null, NOW));
        assertEquals(Long.MAX_VALUE,
                MatchDetailRefreshPolicy.nextDelay(noTime, null, NOW));
        assertFalse(MatchDetailRefreshPolicy.shouldPoll(far, null, NOW));
    }

    @Test
    public void unknownOrMissingMatchUsesErrorDelay() {
        assertEquals(MatchDetailRefreshPolicy.ERROR,
                MatchDetailRefreshPolicy.nextDelay(
                        match(MatchStatus.UNKNOWN, "Fútbol", NOW), null, NOW));
        assertEquals(MatchDetailRefreshPolicy.ERROR,
                MatchDetailRefreshPolicy.nextDelay(null, null, NOW));
        assertEquals(Long.MAX_VALUE,
                MatchDetailRefreshPolicy.nextDelay(null,
                        detail(MatchStatus.FINISHED), NOW));
        assertTrue(MatchDetailRefreshPolicy.shouldPoll(null, null, NOW));
    }

    @Test
    public void postponedAndCancelledDoNotPoll() {
        assertEquals(Long.MAX_VALUE,
                MatchDetailRefreshPolicy.nextDelay(
                        match(MatchStatus.POSTPONED, "Fútbol", NOW), null, NOW));
        assertEquals(Long.MAX_VALUE,
                MatchDetailRefreshPolicy.nextDelay(
                        match(MatchStatus.CANCELLED, "Fútbol", NOW), null, NOW));
    }

    private static Match match(MatchStatus status, String sport, long startMillis) {
        Match match = new Match();
        match.id = 42L;
        match.sourceId = "365scores";
        match.sport = sport;
        match.status = status;
        match.startMillis = startMillis;
        return match;
    }

    private static MatchDetail detail(MatchStatus status) {
        return new MatchDetail(42L, "365scores", "LaLiga", "Fútbol",
                1_760_000_000_000L, status, "", "", true, 1, 1,
                "Local", "Visitante", 10L, 20L, null,
                Collections.emptyList(), Collections.emptyList(), null, null,
                Collections.emptyList(), false, "", Collections.emptyList(),
                0L, false);
    }
}
