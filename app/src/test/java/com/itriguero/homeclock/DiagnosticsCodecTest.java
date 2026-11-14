package com.itriguero.homeclock;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DiagnosticsCodecTest {

    private static final long NOW = 1_760_000_000_000L;

    @Test
    public void roundTripPreservesAllFields() {
        DiagnosticsState original = fullState();

        String raw = DiagnosticsCodec.encode(original);
        DiagnosticsCodec.Decoded decoded = DiagnosticsCodec.decode(raw);

        assertNotNull(decoded);
        assertFalse(decoded.readOnly);
        DiagnosticsState state = decoded.state;

        assertEquals(NOW, state.savedAt);
        assertEquals(NOW - 10_000L, state.firstSeenAt);
        assertEquals(42L, state.appVersionCode);
        assertEquals("1.2.3", state.appVersionName);
        assertEquals(34, state.sdk);

        assertEquals(1234, state.process.pid);
        assertEquals(NOW - 5_000L, state.process.startedAt);
        assertEquals(999_000L, state.process.startElapsedRealtime);
        assertEquals(7, state.process.bootCount);
        assertEquals(NOW - 1_000L, state.process.lastHeartbeatAt);
        assertEquals(998_000L, state.process.lastHeartbeatElapsedRealtime);
        assertEquals(NOW - 2_000L, state.process.lastForegroundAt);
        assertEquals(NOW - 3_000L, state.process.lastBackgroundAt);
        assertEquals(15, state.process.lastTrimLevel);
        assertEquals(NOW - 4_000L, state.process.lastTrimAt);
        assertEquals(NOW - 6_000L, state.process.activityClosedAt);
        assertEquals(9, state.process.sessionCount);

        assertEquals("2026-11-14", state.matches.date);
        assertEquals(NOW - 20_000L, state.matches.attemptStartedAt);
        assertEquals(NOW - 19_000L, state.matches.finishedAt);
        assertEquals(1_000L, state.matches.durationMs);
        assertEquals(NOW - 18_000L, state.matches.fetchedAt);
        assertTrue(state.matches.stale);
        assertTrue(state.matches.partial);
        assertEquals(5, state.matches.matchCount);
        assertEquals(5, state.matches.sources.size());

        SourceHealth scores = state.matches.sources.get("365scores");
        assertNotNull(scores);
        assertEquals(SourceState.OK, scores.state);
        assertNull(scores.error);
        assertEquals(NOW - 21_000L, scores.lastAttemptAt);
        assertEquals(NOW - 21_000L, scores.lastOkAt);
        assertEquals(0L, scores.lastErrorAt);
        assertEquals(230L, scores.durationMs);
        assertFalse(scores.fromCache);
        assertEquals(4, scores.matchCount);
        assertEquals(0, scores.consecutiveFailures);
        assertEquals(0L, scores.downSince);
        assertEquals(12, scores.attempts);
        assertEquals(1, scores.failures);

        SourceHealth rfef = state.matches.sources.get("rfef");
        assertNotNull(rfef);
        assertEquals(SourceState.TIMEOUT, rfef.state);
        assertEquals("Timeout agotado", rfef.error);
        assertEquals(2, rfef.consecutiveFailures);
        assertEquals(0L, rfef.downSince);

        assertEquals(NOW - 30_000L, state.weather.attemptStartedAt);
        assertEquals(NOW - 29_000L, state.weather.finishedAt);
        assertEquals(800L, state.weather.durationMs);
        assertEquals(NOW - 28_000L, state.weather.fetchedAt);
        assertEquals("CACHED", state.weather.state);
        assertTrue(state.weather.partial);
        assertEquals("offline", state.weather.error);
        assertEquals(2, state.weather.cityCount);
        assertEquals(3, state.weather.consecutiveFailures);
        assertEquals(NOW - 1_000_000L, state.weather.lastOkAt);
        assertEquals(NOW - 28_000L, state.weather.lastErrorAt);
        assertEquals(NOW - 28_000L, state.weather.downSince);

        assertEquals(NOW - 2_000L, state.memory.sampledAt);
        assertEquals(45_000, state.memory.totalPssKb);
        assertEquals(12_000, state.memory.javaKb);
        assertEquals(14_000, state.memory.nativeKb);
        assertEquals(8_000, state.memory.codeKb);
        assertEquals(3_000, state.memory.graphicsKb);
        assertEquals(9_000, state.memory.privateDirtyKb);
        assertEquals(4, state.memory.importance);
        assertEquals(120, state.memory.samples);
        assertEquals(38_000, state.memory.minPssKb);
        assertEquals(52_000, state.memory.maxPssKb);
        assertEquals(3, state.memory.history.size());
        assertEquals(100L, state.memory.history.get(0).at);
        assertEquals(200L, state.memory.history.get(1).at);
        assertEquals(300L, state.memory.history.get(2).at);
        assertEquals(42_000, state.memory.history.get(1).totalPssKb);

        assertEquals(4, state.exits.size());
        assertEquals(500L, state.exits.get(0).at);
        assertEquals("crash", state.exits.get(0).cause);
        assertEquals(4, state.exits.get(0).reasonCode);
        assertEquals(11, state.exits.get(0).status);
        assertEquals(4321, state.exits.get(0).pid);
        assertEquals(300, state.exits.get(0).importance);
        assertEquals("FATAL EXCEPTION", state.exits.get(0).description);
        assertEquals("system", state.exits.get(0).confidence);
        assertEquals(42L, state.exits.get(0).appVersionCode);
        assertEquals(60_000L, state.exits.get(0).sessionDurationMs);

        assertEquals(5, state.counters.sessions);
        assertEquals(30, state.counters.matchesAttempts);
        assertEquals(25, state.counters.matchesOk);
        assertEquals(5, state.counters.matchesFail);
        assertEquals(20, state.counters.weatherAttempts);
        assertEquals(17, state.counters.weatherOk);
        assertEquals(3, state.counters.weatherFail);
        assertEquals(4, state.counters.exits);
        assertEquals(1, state.counters.crashes);
        assertEquals(1, state.counters.anrs);
        assertEquals(1, state.counters.lmk);
        assertEquals(1, state.counters.unknownExits);

        assertEquals(Arrays.asList("session-a", "123"), state.exitScanKeys);
    }

    @Test
    public void encodeIsDeterministicAndSortsSourcesExitsAndHistory() throws Exception {
        DiagnosticsState state = fullState();

        String first = DiagnosticsCodec.encode(state);
        String second = DiagnosticsCodec.encode(state);

        assertEquals(first, second);

        JSONObject root = new JSONObject(first);
        assertEquals(1, root.getInt("version"));

        JSONArray sources = root.getJSONObject("matches").getJSONArray("sources");
        assertEquals("365scores", sources.getJSONObject(0).getString("id"));
        assertEquals("rfef", sources.getJSONObject(1).getString("id"));
        assertEquals("celta", sources.getJSONObject(2).getString("id"));
        assertEquals("aaa", sources.getJSONObject(3).getString("id"));
        assertEquals("zzz", sources.getJSONObject(4).getString("id"));

        JSONArray exits = root.getJSONArray("exits");
        assertEquals(500L, exits.getJSONObject(0).getLong("at"));
        assertEquals(300L, exits.getJSONObject(1).getLong("at"));
        assertEquals(200L, exits.getJSONObject(2).getLong("at"));
        assertEquals(100L, exits.getJSONObject(3).getLong("at"));

        JSONArray history = root.getJSONObject("memory").getJSONArray("history");
        assertEquals(100L, history.getJSONObject(0).getLong("at"));
        assertEquals(200L, history.getJSONObject(1).getLong("at"));
        assertEquals(300L, history.getJSONObject(2).getLong("at"));
    }

    @Test
    public void futureVersionIsReadOnlyButStillParsed() {
        String raw = DiagnosticsCodec.encode(fullState())
                .replace("\"version\":1", "\"version\":2");

        DiagnosticsCodec.Decoded decoded = DiagnosticsCodec.decode(raw);

        assertNotNull(decoded);
        assertTrue(decoded.readOnly);
        assertEquals(NOW, decoded.state.savedAt);
        assertEquals(42L, decoded.state.appVersionCode);
    }

    @Test
    public void emptyBrokenAndUnknownVersionsReturnNull() {
        assertNull(DiagnosticsCodec.decode(null));
        assertNull(DiagnosticsCodec.decode(""));
        assertNull(DiagnosticsCodec.decode("   "));
        assertNull(DiagnosticsCodec.decode("{esto no es json"));
        assertNull(DiagnosticsCodec.decode("[]"));
        assertNull(DiagnosticsCodec.decode("null"));
        assertNull(DiagnosticsCodec.decode("{}"));
        assertNull(DiagnosticsCodec.decode("{\"version\":0}"));
        assertNull(DiagnosticsCodec.decode("{\"version\":-1}"));
        assertNull(DiagnosticsCodec.decode("{\"version\":\"futuro\"}"));
    }

    @Test
    public void missingFieldsUseSafeDefaults() {
        DiagnosticsCodec.Decoded decoded = DiagnosticsCodec.decode("{\"version\":1}");

        assertNotNull(decoded);
        assertFalse(decoded.readOnly);
        DiagnosticsState state = decoded.state;
        assertEquals(0L, state.savedAt);
        assertEquals(0L, state.firstSeenAt);
        assertEquals(0L, state.appVersionCode);
        assertEquals("", state.appVersionName);
        assertEquals(0, state.sdk);
        assertEquals(0, state.process.pid);
        assertEquals("", state.matches.date);
        assertTrue(state.matches.sources.isEmpty());
        assertEquals("EMPTY", state.weather.state);
        assertEquals(0, state.memory.history.size());
        assertTrue(state.exits.isEmpty());
        assertEquals(0, state.counters.sessions);
        assertTrue(state.exitScanKeys.isEmpty());
    }

    @Test
    public void unknownSourceStateBecomesError() {
        DiagnosticsCodec.Decoded decoded = DiagnosticsCodec.decode(
                "{\"version\":1,\"matches\":{\"sources\":["
                        + "{\"id\":\"raro\",\"state\":\"INVENTADO\"}]}}");

        assertNotNull(decoded);
        SourceHealth health = decoded.state.matches.sources.get("raro");
        assertNotNull(health);
        assertEquals(SourceState.ERROR, health.state);
        assertEquals(0, health.attempts);
    }

    @Test
    public void stringsAreTruncated() {
        String longId = repeat('i', 100);
        String longError = repeat('e', 200);
        String longDescription = repeat('d', 300);
        String longKey = repeat('k', 100);
        String longCause = repeat('c', 100);

        Map<String, SourceHealth> sources = new LinkedHashMap<>();
        sources.put(longId, new SourceHealth(longId, SourceState.ERROR, longError,
                1L, 0L, 1L, 1L, false, 0, 1, 0L, 1, 1));
        DiagnosticsState state = new DiagnosticsState(
                1L, 1L, 1L, repeat('v', 100), 30,
                DiagnosticsState.ProcessInfo.empty(),
                new DiagnosticsState.MatchesInfo("", 0L, 0L, 0L, 0L,
                        false, false, 0, sources),
                DiagnosticsState.WeatherInfo.empty(),
                DiagnosticsState.MemoryInfo.empty(),
                Collections.singletonList(new DiagnosticsState.ExitEvent(
                        1L, 1L, longCause, 4, 0, 1, 1, longDescription,
                        "system", 1L, 0L)),
                DiagnosticsState.Counters.empty(),
                Collections.singletonList(longKey));

        DiagnosticsCodec.Decoded decoded = DiagnosticsCodec.decode(
                DiagnosticsCodec.encode(state));

        assertNotNull(decoded);
        assertEquals(64, decoded.state.appVersionName.length());
        assertEquals(1, decoded.state.matches.sources.size());
        SourceHealth health = decoded.state.matches.sources.values().iterator().next();
        assertEquals(DiagnosticsState.MAX_ID_CHARS, health.sourceId.length());
        assertEquals(DiagnosticsState.MAX_ERROR_CHARS, health.error.length());
        assertEquals(1, decoded.state.exits.size());
        assertEquals(DiagnosticsState.MAX_DESCRIPTION_CHARS,
                decoded.state.exits.get(0).description.length());
        assertEquals(DiagnosticsState.MAX_ID_CHARS,
                decoded.state.exits.get(0).cause.length());
        assertEquals(DiagnosticsState.MAX_ID_CHARS,
                decoded.state.exitScanKeys.get(0).length());
    }

    @Test
    public void limitsAreApplied() {
        Map<String, SourceHealth> sources = new LinkedHashMap<>();
        for (int i = 0; i < 20; i++) {
            String id = "s" + (char) ('a' + i);
            sources.put(id, SourceHealth.initial(id));
        }

        List<DiagnosticsState.ExitEvent> exits = new java.util.ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            exits.add(new DiagnosticsState.ExitEvent(
                    i, i, "crash", 4, 0, i, 0, "", "system", 1L, 0L));
        }

        List<DiagnosticsState.Sample> history = new java.util.ArrayList<>();
        for (int i = 0; i < 60; i++) {
            history.add(new DiagnosticsState.Sample(i, 40_000 + i));
        }

        List<String> keys = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            keys.add("key" + i);
        }

        DiagnosticsState state = new DiagnosticsState(
                1L, 1L, 1L, "", 30,
                DiagnosticsState.ProcessInfo.empty(),
                new DiagnosticsState.MatchesInfo("", 0L, 0L, 0L, 0L,
                        false, false, 0, sources),
                DiagnosticsState.WeatherInfo.empty(),
                new DiagnosticsState.MemoryInfo(1L, 0, 0, 0, 0, 0, 0, 0,
                        60, 0, 0, history),
                exits, DiagnosticsState.Counters.empty(), keys);

        DiagnosticsCodec.Decoded decoded = DiagnosticsCodec.decode(
                DiagnosticsCodec.encode(state));

        assertNotNull(decoded);
        assertEquals(DiagnosticsState.MAX_SOURCES,
                decoded.state.matches.sources.size());
        assertEquals(DiagnosticsState.MAX_EXITS, decoded.state.exits.size());
        assertEquals(10L, decoded.state.exits.get(0).at);
        assertEquals(6L, decoded.state.exits.get(4).at);
        assertEquals(DiagnosticsState.MAX_HISTORY, decoded.state.memory.history.size());
        assertEquals(12L, decoded.state.memory.history.get(0).at);
        assertEquals(59L,
                decoded.state.memory.history.get(DiagnosticsState.MAX_HISTORY - 1).at);
        assertEquals(DiagnosticsState.MAX_EXIT_KEYS, decoded.state.exitScanKeys.size());
        assertEquals("key15", decoded.state.exitScanKeys.get(15));
    }

    @Test
    public void decodeTrimsOversizedRawArrays() throws Exception {
        JSONArray sources = new JSONArray();
        for (int i = 0; i < 20; i++) {
            sources.put(new JSONObject().put("id", "s" + i).put("state", "OK"));
        }

        JSONArray exits = new JSONArray();
        for (int i = 0; i < 8; i++) {
            exits.put(new JSONObject().put("at", i).put("cause", "crash"));
        }

        JSONArray history = new JSONArray();
        for (int i = 0; i < 60; i++) {
            history.put(new JSONObject().put("at", i).put("totalPssKb", i));
        }

        JSONArray keys = new JSONArray();
        for (int i = 0; i < 20; i++) {
            keys.put("key" + i);
        }

        JSONObject root = new JSONObject()
                .put("version", 1)
                .put("matches", new JSONObject().put("sources", sources))
                .put("exits", exits)
                .put("memory", new JSONObject().put("history", history))
                .put("exitScanKeys", keys);

        DiagnosticsCodec.Decoded decoded = DiagnosticsCodec.decode(root.toString());

        assertNotNull(decoded);
        assertEquals(DiagnosticsState.MAX_SOURCES,
                decoded.state.matches.sources.size());
        assertEquals(DiagnosticsState.MAX_EXITS, decoded.state.exits.size());
        assertEquals(7L, decoded.state.exits.get(0).at);
        assertEquals(3L, decoded.state.exits.get(4).at);
        assertEquals(DiagnosticsState.MAX_HISTORY, decoded.state.memory.history.size());
        assertEquals(12L, decoded.state.memory.history.get(0).at);
        assertEquals(59L,
                decoded.state.memory.history.get(DiagnosticsState.MAX_HISTORY - 1).at);
        assertEquals(DiagnosticsState.MAX_EXIT_KEYS, decoded.state.exitScanKeys.size());
        assertEquals("key15", decoded.state.exitScanKeys.get(15));
    }

    @Test
    public void negativeAndNonFiniteNumbersBecomeZero() {
        String raw = "{\"version\":1,"
                + "\"savedAt\":-5,"
                + "\"sdk\":1e999,"
                + "\"process\":{\"pid\":-2,\"sessionCount\":-3},"
                + "\"matches\":{\"matchCount\":-4,\"sources\":["
                + "{\"id\":\"x\",\"state\":\"OK\",\"lastAttemptAt\":-5,"
                + "\"matchCount\":-6,\"attempts\":-7,\"failures\":-8,"
                + "\"consecutiveFailures\":-9,\"downSince\":-10}]},"
                + "\"weather\":{\"cityCount\":-8,\"consecutiveFailures\":-9},"
                + "\"memory\":{\"totalPssKb\":-9,\"history\":["
                + "{\"at\":-1,\"totalPssKb\":-2}]},"
                + "\"exits\":[{\"at\":-3,\"reasonCode\":-4,\"status\":-5,"
                + "\"pid\":-6,\"importance\":-7,\"appVersionCode\":-8,"
                + "\"sessionDurationMs\":-9}],"
                + "\"counters\":{\"sessions\":-10}}";

        DiagnosticsCodec.Decoded decoded = DiagnosticsCodec.decode(raw);

        assertNotNull(decoded);
        DiagnosticsState state = decoded.state;
        assertEquals(0L, state.savedAt);
        assertEquals(0, state.sdk);
        assertEquals(0, state.process.pid);
        assertEquals(0, state.process.sessionCount);
        assertEquals(0, state.matches.matchCount);
        SourceHealth health = state.matches.sources.get("x");
        assertNotNull(health);
        assertEquals(0L, health.lastAttemptAt);
        assertEquals(0, health.matchCount);
        assertEquals(0, health.attempts);
        assertEquals(0, health.failures);
        assertEquals(0, health.consecutiveFailures);
        assertEquals(0L, health.downSince);
        assertEquals(0, state.weather.cityCount);
        assertEquals(0, state.memory.totalPssKb);
        assertEquals(0, state.memory.history.get(0).at);
        assertEquals(0, state.memory.history.get(0).totalPssKb);
        assertEquals(0, state.exits.get(0).at);
        assertEquals(0, state.exits.get(0).reasonCode);
        assertEquals(0, state.exits.get(0).status);
        assertEquals(0, state.exits.get(0).pid);
        assertEquals(0, state.counters.sessions);
    }

    @Test
    public void encodeNullStateReturnsEmptyString() {
        assertEquals("", DiagnosticsCodec.encode(null));
    }

    @Test
    public void emptyStateCanBeEncodedAndDecoded() {
        String raw = DiagnosticsCodec.encode(DiagnosticsState.empty());
        DiagnosticsCodec.Decoded decoded = DiagnosticsCodec.decode(raw);

        assertNotNull(decoded);
        assertFalse(decoded.readOnly);
        assertEquals(0L, decoded.state.savedAt);
        assertTrue(decoded.state.matches.sources.isEmpty());
        assertTrue(decoded.state.exits.isEmpty());
    }

    private static DiagnosticsState fullState() {
        Map<String, SourceHealth> sources = new LinkedHashMap<>();
        sources.put("zzz", new SourceHealth("zzz", SourceState.HTTP_ERROR, "http 500",
                NOW - 10_000L, NOW - 90_000L, NOW - 10_000L, 500L,
                false, 0, 1, NOW - 10_000L, 5, 2));
        sources.put("rfef", new SourceHealth("rfef", SourceState.TIMEOUT, "Timeout agotado",
                NOW - 22_000L, NOW - 100_000L, NOW - 22_000L, 12_000L,
                false, 0, 2, 0L, 8, 3));
        sources.put("365scores", new SourceHealth("365scores", SourceState.OK, null,
                NOW - 21_000L, NOW - 21_000L, 0L, 230L,
                false, 4, 0, 0L, 12, 1));
        sources.put("celta", new SourceHealth("celta", SourceState.EMPTY, null,
                NOW - 23_000L, NOW - 23_000L, 0L, 80L,
                false, 0, 0, 0L, 2, 0));
        sources.put("aaa", new SourceHealth("aaa", SourceState.OK, null,
                NOW - 24_000L, NOW - 24_000L, 0L, 60L,
                true, 1, 0, 0L, 1, 0));

        List<DiagnosticsState.ExitEvent> exits = Arrays.asList(
                new DiagnosticsState.ExitEvent(100L, 110L, "crash", 4, 11,
                        111, 300, "FATAL EXCEPTION", "system", 42L, 1_000L),
                new DiagnosticsState.ExitEvent(300L, 310L, "anr", 6, 0,
                        333, 100, "ANR", "system", 42L, 2_000L),
                new DiagnosticsState.ExitEvent(200L, 210L, "lmk", 3, 9,
                        222, 400, "Low memory", "system", 42L, 3_000L),
                new DiagnosticsState.ExitEvent(500L, 510L, "crash", 4, 11,
                        4321, 300, "FATAL EXCEPTION", "system", 42L, 60_000L));

        return new DiagnosticsState(
                NOW, NOW - 10_000L, 42L, "1.2.3", 34,
                new DiagnosticsState.ProcessInfo(1234, NOW - 5_000L, 999_000L, 7,
                        NOW - 1_000L, 998_000L, NOW - 2_000L, NOW - 3_000L,
                        15, NOW - 4_000L, NOW - 6_000L, 9),
                new DiagnosticsState.MatchesInfo("2026-11-14",
                        NOW - 20_000L, NOW - 19_000L, 1_000L,
                        NOW - 18_000L, true, true, 5, sources),
                new DiagnosticsState.WeatherInfo(NOW - 30_000L, NOW - 29_000L,
                        800L, NOW - 28_000L, "CACHED", true, "offline", 2, 3,
                        NOW - 1_000_000L, NOW - 28_000L, NOW - 28_000L),
                new DiagnosticsState.MemoryInfo(NOW - 2_000L, 45_000, 12_000,
                        14_000, 8_000, 3_000, 9_000, 4, 120, 38_000, 52_000,
                        Arrays.asList(
                                new DiagnosticsState.Sample(300L, 45_000),
                                new DiagnosticsState.Sample(100L, 40_000),
                                new DiagnosticsState.Sample(200L, 42_000))),
                exits,
                new DiagnosticsState.Counters(5, 30, 25, 5, 20, 17, 3, 4, 1, 1, 1, 1),
                Arrays.asList("session-a", "123"));
    }

    private static String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) builder.append(value);
        return builder.toString();
    }
}
