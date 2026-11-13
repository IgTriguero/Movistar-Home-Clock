package com.itriguero.homeclock;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class RefreshPolicyTest {

    private static final long NOW = 1_000_000L;
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;

    @Test
    public void liveBasketballRefreshesEveryFifteenSeconds() {
        MatchesSnapshot snapshot = snapshot(
                match("Baloncesto", MatchStatus.LIVE, NOW));

        assertEquals(15_000L, RefreshPolicy.nextDelay(snapshot, NOW));
    }

    @Test
    public void liveBasketballWithPartialSourceErrorStillRefreshesFast() {
        MatchesSnapshot snapshot = new MatchesSnapshot(
                LocalDate.of(2026, 1, 1),
                Arrays.asList(match("Baloncesto", MatchStatus.LIVE, NOW)),
                errorSources(), NOW, false);

        assertEquals(15_000L, RefreshPolicy.nextDelay(snapshot, NOW));
    }

    @Test
    public void basketballAtEndOfQuarterRefreshesEveryFiveSeconds() {
        Match match = match("Baloncesto", MatchStatus.LIVE, NOW);
        match.gameTimeDisplay = "01:30";
        MatchesSnapshot snapshot = snapshot(match);

        assertEquals(5_000L, RefreshPolicy.nextDelay(snapshot, NOW));
    }

    @Test
    public void basketballAtTwoMinutesExactlyRefreshesEveryFiveSeconds() {
        Match match = match("Baloncesto", MatchStatus.LIVE, NOW);
        match.gameTimeDisplay = "02:00";
        MatchesSnapshot snapshot = snapshot(match);

        assertEquals(5_000L, RefreshPolicy.nextDelay(snapshot, NOW));
    }

    @Test
    public void basketballAtTwoMinutesOneSecondRefreshesEveryFifteenSeconds() {
        Match match = match("Baloncesto", MatchStatus.LIVE, NOW);
        match.gameTimeDisplay = "02:01";
        MatchesSnapshot snapshot = snapshot(match);

        assertEquals(15_000L, RefreshPolicy.nextDelay(snapshot, NOW));
    }

    @Test
    public void basketballAtZeroStillRefreshesEveryFiveSeconds() {
        Match match = match("Baloncesto", MatchStatus.LIVE, NOW);
        match.gameTimeDisplay = "00:00";
        MatchesSnapshot snapshot = snapshot(match);

        assertEquals(5_000L, RefreshPolicy.nextDelay(snapshot, NOW));
    }

    @Test
    public void liveFootballRefreshesEveryMinute() {
        MatchesSnapshot snapshot = snapshot(
                match("Fútbol", MatchStatus.LIVE, NOW));

        assertEquals(MINUTE, RefreshPolicy.nextDelay(snapshot, NOW));
    }

    @Test
    public void liveApproxWaitsFiveMinutes() {
        MatchesSnapshot snapshot = snapshot(
                match("Fútbol", MatchStatus.LIVE_APPROX, NOW));

        assertEquals(5 * MINUTE, RefreshPolicy.nextDelay(snapshot, NOW));
    }

    @Test
    public void staleSnapshotWaitsFifteenMinutesWhenThereIsNoMatchSoon() {
        MatchesSnapshot snapshot = new MatchesSnapshot(
                LocalDate.of(2026, 1, 1), Arrays.asList(
                        match("Fútbol", MatchStatus.SCHEDULED, NOW + 10 * HOUR)),
                okSources(), NOW, true);

        assertEquals(15 * MINUTE, RefreshPolicy.nextDelay(snapshot, NOW));
    }

    @Test
    public void staleSnapshotStillRespectsPreMatchWindow() {
        MatchesSnapshot snapshot = new MatchesSnapshot(
                LocalDate.of(2026, 1, 1), Arrays.asList(
                        match("Fútbol", MatchStatus.SCHEDULED, NOW + HOUR)),
                okSources(), NOW, true);

        assertEquals(10 * MINUTE, RefreshPolicy.nextDelay(snapshot, NOW));
    }

    @Test
    public void liveApproxDoesNotHideAnImminentKickoff() {
        MatchesSnapshot snapshot = new MatchesSnapshot(
                LocalDate.of(2026, 1, 1), Arrays.asList(
                        match("Fútbol", MatchStatus.LIVE_APPROX, NOW),
                        match("Fútbol", MatchStatus.SCHEDULED, NOW + 10 * MINUTE)),
                okSources(), NOW, false);

        assertEquals(MINUTE, RefreshPolicy.nextDelay(snapshot, NOW));
    }

    @Test
    public void partialErrorDoesNotHideAnImminentKickoff() {
        MatchesSnapshot snapshot = new MatchesSnapshot(
                LocalDate.of(2026, 1, 1), Arrays.asList(
                        match("Fútbol", MatchStatus.SCHEDULED, NOW + 10 * MINUTE)),
                errorSources(), NOW, false);

        assertEquals(MINUTE, RefreshPolicy.nextDelay(snapshot, NOW));
    }

    @Test
    public void timeTbdDoesNotTriggerPreMatchUrgency() {
        Match match = match("Fútbol", MatchStatus.SCHEDULED, NOW);
        match.timeTbd = true;
        MatchesSnapshot snapshot = new MatchesSnapshot(
                LocalDate.of(2026, 1, 1), Arrays.asList(match),
                okSources(), NOW, false);

        assertEquals(6 * HOUR, RefreshPolicy.nextDelay(snapshot, NOW));
    }

    @Test
    public void errorSnapshotWaitsTenMinutes() {
        MatchesSnapshot snapshot = new MatchesSnapshot(
                LocalDate.of(2026, 1, 1), Arrays.asList(),
                errorSources(), NOW, false);

        assertEquals(10 * MINUTE, RefreshPolicy.nextDelay(snapshot, NOW));
    }

    @Test
    public void scheduledInTenMinutesRefreshesEveryMinute() {
        MatchesSnapshot snapshot = snapshot(
                match("Fútbol", MatchStatus.SCHEDULED, NOW + 10 * MINUTE));

        assertEquals(MINUTE, RefreshPolicy.nextDelay(snapshot, NOW));
    }

    @Test
    public void scheduledInOneHourWaitsTenMinutes() {
        MatchesSnapshot snapshot = snapshot(
                match("Fútbol", MatchStatus.SCHEDULED, NOW + HOUR));

        assertEquals(10 * MINUTE, RefreshPolicy.nextDelay(snapshot, NOW));
    }

    @Test
    public void scheduledInThreeHoursWaitsThirtyMinutes() {
        MatchesSnapshot snapshot = snapshot(
                match("Fútbol", MatchStatus.SCHEDULED, NOW + 3 * HOUR));

        assertEquals(30 * MINUTE, RefreshPolicy.nextDelay(snapshot, NOW));
    }

    @Test
    public void scheduledInTenHoursWaitsSixHours() {
        MatchesSnapshot snapshot = snapshot(
                match("Fútbol", MatchStatus.SCHEDULED, NOW + 10 * HOUR));

        assertEquals(6 * HOUR, RefreshPolicy.nextDelay(snapshot, NOW));
    }

    @Test
    public void emptySnapshotWaitsSixHours() {
        assertEquals(6 * HOUR, RefreshPolicy.nextDelay(snapshot(), NOW));
    }

    @Test
    public void nullSnapshotWaitsFifteenMinutes() {
        assertEquals(15 * MINUTE, RefreshPolicy.nextDelay(null, NOW));
    }

    private static MatchesSnapshot snapshot(Match... matches) {
        return new MatchesSnapshot(LocalDate.of(2026, 1, 1),
                Arrays.asList(matches), okSources(), NOW, false);
    }

    private static Map<String, SourceStatus> okSources() {
        Map<String, SourceStatus> sources = new LinkedHashMap<>();
        sources.put("test", new SourceStatus("test", SourceState.OK,
                null, NOW, 0));
        return sources;
    }

    private static Map<String, SourceStatus> errorSources() {
        Map<String, SourceStatus> sources = new LinkedHashMap<>();
        sources.put("test", new SourceStatus("test", SourceState.ERROR,
                "boom", NOW, 0));
        return sources;
    }

    private static Match match(String sport, MatchStatus status, long startMillis) {
        Match match = new Match();
        match.sport = sport;
        match.status = status;
        match.startMillis = startMillis;
        return match;
    }
}
