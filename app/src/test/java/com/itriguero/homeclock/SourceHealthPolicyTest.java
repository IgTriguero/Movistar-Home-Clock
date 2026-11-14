package com.itriguero.homeclock;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SourceHealthPolicyTest {

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60L * MINUTE;
    private static final long NOW = 1_760_000_000_000L;

    @Test
    public void initialHasNoAttemptsAndIsNotDown() {
        SourceHealth health = SourceHealth.initial("365scores");

        assertEquals("365scores", health.sourceId);
        assertEquals(SourceState.ERROR, health.state);
        assertNull(health.error);
        assertEquals(0, health.attempts);
        assertEquals(0, health.failures);
        assertEquals(0, health.consecutiveFailures);
        assertEquals(0L, health.downSince);
        assertFalse(health.isDown(NOW));
        assertFalse(SourceHealthPolicy.isDown(health, NOW));
    }

    @Test
    public void successRecordsAttemptAndClearsFailures() {
        SourceHealth health = SourceHealth.initial("rfef");

        health = SourceHealthPolicy.update(
                health, status("rfef", SourceState.OK, false), NOW);

        assertEquals(SourceState.OK, health.state);
        assertEquals(1, health.attempts);
        assertEquals(0, health.failures);
        assertEquals(0, health.consecutiveFailures);
        assertEquals(NOW, health.lastAttemptAt);
        assertEquals(NOW, health.lastOkAt);
        assertEquals(0L, health.lastErrorAt);
        assertEquals(0L, health.downSince);
        assertFalse(health.isDown(NOW));
    }

    @Test
    public void emptyIsAlsoSuccess() {
        SourceHealth health = SourceHealthPolicy.update(
                null, status("celta", SourceState.EMPTY, false), NOW);

        assertEquals(SourceState.EMPTY, health.state);
        assertEquals(1, health.attempts);
        assertEquals(0, health.failures);
        assertEquals(NOW, health.lastOkAt);
        assertEquals(0, health.consecutiveFailures);
        assertFalse(health.isDown(NOW));
    }

    @Test
    public void fromCacheIsFailureEvenWhenStateIsOk() {
        SourceHealth health = SourceHealthPolicy.update(
                null, status("rfef", SourceState.OK, true), NOW);

        assertEquals(1, health.attempts);
        assertEquals(1, health.failures);
        assertEquals(1, health.consecutiveFailures);
        assertTrue(health.fromCache);
        assertEquals(0L, health.lastOkAt);
        assertEquals(NOW, health.lastErrorAt);
        assertFalse(health.isDown(NOW));
    }

    @Test
    public void threeFailuresWithNoPreviousOkMarkSourceDown() {
        SourceHealth health = SourceHealth.initial("celta");

        health = fail(health, NOW);
        assertFalse(health.isDown(NOW));

        health = fail(health, NOW + MINUTE);
        assertFalse(health.isDown(NOW + MINUTE));

        health = fail(health, NOW + 2 * MINUTE);
        assertEquals(3, health.consecutiveFailures);
        assertEquals(NOW + 2 * MINUTE, health.downSince);
        assertTrue(health.isDown(NOW + 2 * MINUTE));
    }

    @Test
    public void threeFailuresWithinFifteenMinutesOfOkDoNotMarkDown() {
        SourceHealth health = SourceHealthPolicy.update(
                null, status("365scores", SourceState.OK, false), NOW);

        health = fail(health, NOW + MINUTE);
        health = fail(health, NOW + 2 * MINUTE);
        health = fail(health, NOW + 3 * MINUTE);

        assertEquals(3, health.consecutiveFailures);
        assertEquals(0L, health.downSince);
        assertFalse(health.isDown(NOW + 3 * MINUTE));
    }

    @Test
    public void sourceGoesDownWhenFifteenMinutesHavePassedSinceLastOk() {
        SourceHealth health = SourceHealthPolicy.update(
                null, status("365scores", SourceState.OK, false), NOW);

        health = fail(health, NOW + MINUTE);
        health = fail(health, NOW + 2 * MINUTE);
        health = fail(health, NOW + 15 * MINUTE);

        assertEquals(3, health.consecutiveFailures);
        assertEquals(NOW + 15 * MINUTE, health.downSince);
        assertTrue(health.isDown(NOW + 15 * MINUTE));
    }

    @Test
    public void downSinceIsKeptAcrossLaterFailures() {
        SourceHealth health = fail(SourceHealth.initial("celta"), NOW);
        health = fail(health, NOW + MINUTE);
        health = fail(health, NOW + 2 * MINUTE);
        long downSince = health.downSince;

        health = fail(health, NOW + 3 * MINUTE);

        assertEquals(downSince, health.downSince);
        assertEquals(4, health.consecutiveFailures);
        assertEquals(4, health.failures);
    }

    @Test
    public void successRecoversDownSource() {
        SourceHealth health = fail(SourceHealth.initial("celta"), NOW);
        health = fail(health, NOW + MINUTE);
        health = fail(health, NOW + 2 * MINUTE);
        assertTrue(health.isDown(NOW + 2 * MINUTE));

        health = SourceHealthPolicy.update(
                health, status("celta", SourceState.OK, false), NOW + 3 * MINUTE);

        assertEquals(0L, health.downSince);
        assertEquals(0, health.consecutiveFailures);
        assertEquals(NOW + 3 * MINUTE, health.lastOkAt);
        assertEquals(NOW + 2 * MINUTE, health.lastErrorAt);
        assertEquals(3, health.failures);
        assertEquals(4, health.attempts);
        assertFalse(health.isDown(NOW + 3 * MINUTE));
    }

    @Test
    public void nullStatusDoesNotCountAsAttempt() {
        SourceHealth health = SourceHealthPolicy.update(
                null, status("rfef", SourceState.OK, false), NOW);

        SourceHealth same = SourceHealthPolicy.update(health, null, NOW + MINUTE);

        assertSame(health, same);
        assertEquals(1, same.attempts);
        assertEquals(health.lastAttemptAt, same.lastAttemptAt);
    }

    @Test
    public void nullPreviousStartsFromScratch() {
        SourceHealth health = SourceHealthPolicy.update(
                null, status("365scores", SourceState.TIMEOUT, false), NOW);

        assertEquals("365scores", health.sourceId);
        assertEquals(SourceState.TIMEOUT, health.state);
        assertEquals(1, health.attempts);
        assertEquals(1, health.failures);
        assertEquals(1, health.consecutiveFailures);
    }

    @Test
    public void isDownExpiresAfterTwentyFourHoursWithoutAttempts() {
        SourceHealth health = fail(SourceHealth.initial("celta"), NOW);
        health = fail(health, NOW);
        health = fail(health, NOW);

        assertTrue(health.isDown(NOW + 24 * HOUR));
        assertFalse(health.isDown(NOW + 24 * HOUR + 1L));
        assertFalse(SourceHealthPolicy.isDown(null, NOW));
    }

    @Test
    public void successAndFailureHelpers() {
        assertTrue(SourceHealthPolicy.isSuccess(status("x", SourceState.OK, false)));
        assertTrue(SourceHealthPolicy.isSuccess(status("x", SourceState.EMPTY, false)));
        assertFalse(SourceHealthPolicy.isSuccess(status("x", SourceState.OK, true)));
        assertFalse(SourceHealthPolicy.isSuccess(status("x", SourceState.ERROR, false)));
        assertFalse(SourceHealthPolicy.isSuccess(null));

        assertTrue(SourceHealthPolicy.isFailure(status("x", SourceState.OK, true)));
        assertTrue(SourceHealthPolicy.isFailure(status("x", SourceState.TIMEOUT, false)));
        assertFalse(SourceHealthPolicy.isFailure(status("x", SourceState.EMPTY, false)));
        assertFalse(SourceHealthPolicy.isFailure(null));
    }

    @Test
    public void classifyWithoutSnapshotOrSourcesIsOk() {
        assertEquals(SourceHealthPolicy.GlobalHealth.OK,
                SourceHealthPolicy.classify(null));
        assertEquals(SourceHealthPolicy.GlobalHealth.OK,
                SourceHealthPolicy.classify(snapshot(Collections.emptyMap())));
    }

    @Test
    public void classifyAllOkOrEmpty() {
        assertEquals(SourceHealthPolicy.GlobalHealth.OK,
                SourceHealthPolicy.classify(snapshot(sources(
                        status("365scores", SourceState.OK, false),
                        status("rfef", SourceState.EMPTY, false)))));

        assertEquals(SourceHealthPolicy.GlobalHealth.CACHED,
                SourceHealthPolicy.classify(snapshot(sources(
                        status("365scores", SourceState.OK, false),
                        status("rfef", SourceState.OK, true)))));
    }

    @Test
    public void classifyPartialMix() {
        assertEquals(SourceHealthPolicy.GlobalHealth.PARTIAL,
                SourceHealthPolicy.classify(snapshot(sources(
                        status("365scores", SourceState.OK, false),
                        status("rfef", SourceState.TIMEOUT, false)))));
    }

    @Test
    public void classifyAllFailedDistinguishesOffline() {
        assertEquals(SourceHealthPolicy.GlobalHealth.OFFLINE,
                SourceHealthPolicy.classify(snapshot(sources(
                        status("365scores", SourceState.OFFLINE, false),
                        status("rfef", SourceState.OFFLINE, false)))));

        assertEquals(SourceHealthPolicy.GlobalHealth.ALL_FAILED,
                SourceHealthPolicy.classify(snapshot(sources(
                        status("365scores", SourceState.TIMEOUT, false),
                        status("rfef", SourceState.HTTP_ERROR, false)))));

        assertEquals(SourceHealthPolicy.GlobalHealth.ALL_FAILED,
                SourceHealthPolicy.classify(snapshot(sources(
                        status("365scores", SourceState.OFFLINE, false),
                        status("rfef", SourceState.TIMEOUT, false)))));
    }

    @Test
    public void classifyIgnoresNullStatuses() {
        Map<String, SourceStatus> sources = new LinkedHashMap<>();
        sources.put("365scores", status("365scores", SourceState.OK, false));
        sources.put("rfef", null);

        assertEquals(SourceHealthPolicy.GlobalHealth.OK,
                SourceHealthPolicy.classify(snapshot(sources)));
    }

    private static SourceHealth fail(SourceHealth health, long now) {
        return SourceHealthPolicy.update(
                health, status(health.sourceId, SourceState.TIMEOUT, false), now);
    }

    private static SourceStatus status(String id, SourceState state, boolean fromCache) {
        return new SourceStatus(id, state, state == SourceState.OK ? null : "fallo",
                0L, 12L, fromCache, 1);
    }

    private static Map<String, SourceStatus> sources(SourceStatus... statuses) {
        Map<String, SourceStatus> map = new LinkedHashMap<>();
        for (SourceStatus status : statuses) {
            map.put(status.sourceId, status);
        }
        return map;
    }

    private static MatchesSnapshot snapshot(Map<String, SourceStatus> sources) {
        return new MatchesSnapshot(LocalDate.of(2026, 11, 14),
                Collections.emptyList(), sources, NOW, false);
    }
}
