package com.itriguero.homeclock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests JVM de {@link DiagnosticsStore} con un entorno falso. No se prueba
 * {@code ApplicationExitInfo} real: el mapper y el lector se ejercitan a través
 * del fake.
 */
public class DiagnosticsStoreTest {

    private static final long NOW = 1_760_000_000_000L;
    private static final long MINUTE = 60_000L;
    private static final LocalDate DAY = LocalDate.of(2026, 11, 14);

    private File dir;
    private FakeEnvironment env;
    private ScheduledExecutorService executor;
    private DiagnosticsStore store;

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("homeclock-diagnostics").toFile();
        env = new FakeEnvironment();
        executor = Executors.newSingleThreadScheduledExecutor();
        store = new DiagnosticsStore(dir, env, executor);
    }

    @After
    public void tearDown() {
        if (store != null) store.close();
        if (executor != null) executor.shutdownNow();
        deleteRecursively(dir);
    }

    // ------------------------------------------------------------------
    // Arranque y sesiones
    // ------------------------------------------------------------------

    @Test
    public void ensureStartedWritesFirstSession() throws Exception {
        store.ensureStarted();

        assertTrue(file().isFile());
        DiagnosticsState state = read();

        assertEquals(NOW, state.savedAt);
        assertEquals(NOW, state.firstSeenAt);
        assertEquals(1, state.counters.sessions);
        assertEquals(1, state.process.sessionCount);
        assertEquals(env.pid, state.process.pid);
        assertEquals(NOW, state.process.startedAt);
        assertEquals(env.startElapsed, state.process.startElapsedRealtime);
        assertEquals(env.boot, state.process.bootCount);
        assertEquals(NOW, state.process.lastHeartbeatAt);
        assertEquals(NOW, state.process.lastForegroundAt);
        assertEquals(env.versionCode, state.appVersionCode);
        assertEquals(env.versionName, state.appVersionName);
        assertEquals(env.sdk, state.sdk);

        assertEquals(1, state.memory.samples);
        assertEquals(45_000, state.memory.totalPssKb);
        assertEquals(45_000, state.memory.minPssKb);
        assertEquals(45_000, state.memory.maxPssKb);
        assertEquals(1, state.memory.history.size());
        assertEquals(NOW, state.memory.sampledAt);

        assertTrue(state.exits.isEmpty());
    }

    @Test
    public void repeatedEnsureDoesNotDuplicateSession() throws Exception {
        store.ensureStarted();
        store.ensureStarted();

        assertEquals(1, read().counters.sessions);
    }

    @Test
    public void sameSignatureDoesNotDuplicateSession() throws Exception {
        store.ensureStarted();

        env.now = NOW + MINUTE;
        restart();

        DiagnosticsState state = read();
        assertEquals(1, state.counters.sessions);
        assertEquals(NOW + MINUTE, state.process.startedAt);
        assertTrue(state.exits.isEmpty());
    }

    @Test
    public void differentSignatureAddsInferredUnknownExit() throws Exception {
        store.ensureStarted();

        env.now = NOW + 2L * MINUTE;
        env.pid = 200;
        env.startElapsed = 9_000L;
        restart();

        DiagnosticsState state = read();
        assertEquals(2, state.counters.sessions);
        assertEquals(2, state.process.sessionCount);
        assertEquals(200, state.process.pid);
        assertEquals(NOW + 2L * MINUTE, state.process.startedAt);
        assertEquals(1, state.exits.size());

        DiagnosticsState.ExitEvent exit = state.exits.get(0);
        assertEquals("unknown", exit.cause);
        assertEquals("inferred", exit.confidence);
        assertEquals(2L * MINUTE, exit.sessionDurationMs);
        assertEquals(NOW + 2L * MINUTE, exit.observedAt);
    }

    @Test
    public void systemExitPreventsInferredDuplicate() throws Exception {
        store.ensureStarted();

        env.now = NOW + 2L * MINUTE;
        env.pid = 200;
        env.startElapsed = 9_000L;
        env.exits.add(new DiagnosticsState.ExitEvent(
                NOW + MINUTE, NOW + MINUTE, "user", 10, 0, 100, 100,
                "[FORCE STOP]", "system", 42, 0L));
        restart();

        DiagnosticsState state = read();
        assertEquals(2, state.counters.sessions);
        assertEquals(1, state.exits.size());
        assertEquals("user", state.exits.get(0).cause);
        assertEquals("system", state.exits.get(0).confidence);
        assertEquals(1, state.counters.exits);
    }

    @Test
    public void bootChangeAddsRebootExit() throws Exception {
        store.ensureStarted();

        env.now = NOW + MINUTE;
        env.boot = env.boot + 1;
        restart();

        DiagnosticsState state = read();
        assertEquals(2, state.counters.sessions);
        assertEquals("reboot", state.exits.get(0).cause);
        assertEquals("inferred", state.exits.get(0).confidence);
        assertEquals(env.boot, state.process.bootCount);
    }

    @Test
    public void versionChangeAddsUpdateExit() throws Exception {
        store.ensureStarted();

        env.now = NOW + MINUTE;
        env.versionCode = 43;
        env.pid = 201;
        env.startElapsed = 10_000L;
        restart();

        DiagnosticsState state = read();
        assertEquals(2, state.counters.sessions);
        assertEquals("update", state.exits.get(0).cause);
        assertEquals("inferred", state.exits.get(0).confidence);
        assertEquals(43L, state.appVersionCode);
    }

    // ------------------------------------------------------------------
    // Salidas observadas (fake de ApplicationExitInfo)
    // ------------------------------------------------------------------

    @Test
    public void firstStartStoresOnlyExitKeys() throws Exception {
        env.exits.add(crashEvent(1_000L, 321));

        store.ensureStarted();

        DiagnosticsState state = read();
        assertTrue(state.exits.isEmpty());
        assertEquals(0, state.counters.exits);
        assertEquals(1, state.exitScanKeys.size());
        assertEquals(ExitInfoReader.key(1_000L, 321,
                ExitReasonMapper.REASON_CRASH, 0), state.exitScanKeys.get(0));
    }

    @Test
    public void laterStartsAddNewExitsAndCounters() throws Exception {
        store.ensureStarted();

        env.exits.add(crashEvent(2_000L, 322));
        env.now = NOW + MINUTE;
        restart();

        DiagnosticsState state = read();
        assertEquals(1, state.exits.size());
        assertEquals("crash", state.exits.get(0).cause);
        assertEquals("system", state.exits.get(0).confidence);
        assertEquals(322, state.exits.get(0).pid);
        assertEquals(1, state.counters.exits);
        assertEquals(1, state.counters.crashes);
        assertEquals(0, state.counters.anrs);
        assertEquals(1, state.exitScanKeys.size());

        env.exits.add(anrEvent(3_000L, 323));
        env.now = NOW + 2L * MINUTE;
        restart();

        state = read();
        assertEquals(2, state.exits.size());
        assertEquals(2, state.counters.exits);
        assertEquals(1, state.counters.anrs);
        assertEquals(1, state.counters.crashes);

        env.exits.add(lmkEvent(4_000L, 324));
        env.exits.add(unknownEvent(5_000L, 325));
        env.now = NOW + 3L * MINUTE;
        restart();

        state = read();
        assertEquals(4, state.exits.size());
        assertEquals(4, state.counters.exits);
        assertEquals(1, state.counters.lmk);
        assertEquals(1, state.counters.unknownExits);
    }

    @Test
    public void repeatedExitsAreDeduplicatedByKey() throws Exception {
        store.ensureStarted();

        env.exits.add(crashEvent(2_000L, 322));
        env.now = NOW + MINUTE;
        restart();

        assertEquals(1, read().counters.exits);

        env.now = NOW + 2L * MINUTE;
        restart();
        restart();

        DiagnosticsState state = read();
        assertEquals(1, state.exits.size());
        assertEquals(1, state.counters.exits);
        assertEquals(1, exitKeysCount(state));
    }

    // ------------------------------------------------------------------
    // Listener de Diagnostics
    // ------------------------------------------------------------------

    @Test
    public void onChangedPersistsMatchesWeatherAndHealth() throws Exception {
        store.ensureStarted();

        Diagnostics diagnostics = new Diagnostics();
        diagnostics.setListener(store);

        SourceStatus scores = new SourceStatus("365scores", SourceState.OK,
                null, NOW, 120L, false, 2);
        SourceStatus rfef = new SourceStatus("rfef", SourceState.TIMEOUT,
                "Timeout", NOW, 80L, false, 0);
        diagnostics.recordMatches(matches(scores, rfef), NOW - 1_000L,
                1_000L, NOW);
        diagnostics.recordWeather(weather(WeatherSnapshot.State.FRESH, false,
                ""), NOW - 500L, 500L, NOW);
        store.flush();

        DiagnosticsState state = read();
        assertEquals(DAY.toString(), state.matches.date);
        assertEquals(NOW - 1_000L, state.matches.attemptStartedAt);
        assertEquals(NOW, state.matches.finishedAt);
        assertEquals(1_000L, state.matches.durationMs);
        assertEquals(NOW, state.matches.fetchedAt);
        assertFalse(state.matches.stale);
        assertTrue(state.matches.partial);
        assertEquals(2, state.matches.matchCount);
        assertEquals(2, state.matches.sources.size());

        SourceHealth rfefHealth = state.matches.sources.get("rfef");
        assertNotNull(rfefHealth);
        assertEquals(SourceState.TIMEOUT, rfefHealth.state);
        assertEquals(1, rfefHealth.consecutiveFailures);
        assertEquals(1, rfefHealth.attempts);

        SourceHealth scoresHealth = state.matches.sources.get("365scores");
        assertNotNull(scoresHealth);
        assertEquals(SourceState.OK, scoresHealth.state);
        assertEquals(1, scoresHealth.attempts);

        assertEquals("FRESH", state.weather.state);
        assertEquals(NOW - 500L, state.weather.attemptStartedAt);
        assertEquals(NOW, state.weather.finishedAt);
        assertEquals(NOW, state.weather.fetchedAt);
        assertEquals(500L, state.weather.durationMs);
        assertEquals(1, state.weather.cityCount);
        assertEquals(0, state.weather.consecutiveFailures);

        assertEquals(1, state.counters.matchesAttempts);
        assertEquals(0, state.counters.matchesOk);
        assertEquals(1, state.counters.matchesFail);
        assertEquals(1, state.counters.weatherAttempts);
        assertEquals(1, state.counters.weatherOk);
        assertEquals(0, state.counters.weatherFail);
    }

    @Test
    public void sourceDownAndRecoveryAreSavedImmediately() throws Exception {
        store.ensureStarted();

        Diagnostics diagnostics = new Diagnostics();
        diagnostics.setListener(store);
        SourceStatus timeout = new SourceStatus("rfef", SourceState.TIMEOUT,
                "Timeout", NOW, 10L);

        diagnostics.recordMatches(matches(timeout), NOW, 10L, NOW);
        diagnostics.recordMatches(matches(timeout), NOW + 1_000L, 10L,
                NOW + 1_000L);

        long downAt = NOW + 2_000L;
        env.now = downAt;
        diagnostics.recordMatches(matches(timeout), downAt - 1_000L, 10L,
                downAt);

        SourceHealth health = diagnostics.snapshot().sourceHealth.get("rfef");
        assertNotNull(health);
        assertTrue(health.downSince > 0L);

        // Sin flush: la transición debe haber forzado la escritura.
        DiagnosticsState state = read();
        assertEquals(downAt,
                state.matches.sources.get("rfef").downSince);

        long okAt = downAt + MINUTE;
        env.now = okAt;
        SourceStatus ok = new SourceStatus("rfef", SourceState.OK, null,
                okAt, 10L, false, 1);
        diagnostics.recordMatches(matches(ok), okAt - 1_000L, 10L, okAt);

        state = read();
        assertEquals(0L, state.matches.sources.get("rfef").downSince);
        assertEquals(okAt, state.matches.sources.get("rfef").lastOkAt);
        assertEquals(1, state.counters.matchesOk);
    }

    @Test
    public void flushWritesPendingChanges() throws Exception {
        store.ensureStarted();

        Diagnostics diagnostics = new Diagnostics();
        diagnostics.setListener(store);
        SourceStatus ok = new SourceStatus("365scores", SourceState.OK, null,
                NOW, 10L, false, 1);
        diagnostics.recordMatches(matches(ok), NOW - 100L, 100L, NOW);

        assertEquals("", read().matches.date);

        store.flush();

        assertEquals(DAY.toString(), read().matches.date);
        assertEquals(1, read().counters.matchesAttempts);
    }

    // ------------------------------------------------------------------
    // Robustez del fichero
    // ------------------------------------------------------------------

    @Test
    public void futureVersionIsReadOnlyAndNotOverwritten() throws Exception {
        String future = DiagnosticsCodec.encode(DiagnosticsState.empty())
                .replace("\"version\":1", "\"version\":2");
        writeRaw(future);

        store.ensureStarted();
        store.flush();

        assertEquals(future, readRaw());
    }

    @Test
    public void corruptFileStartsFreshWithoutCrash() throws Exception {
        writeRaw("{esto no es json");

        store.ensureStarted();

        DiagnosticsState state = read();
        assertEquals(NOW, state.firstSeenAt);
        assertEquals(1, state.counters.sessions);
        assertEquals(env.pid, state.process.pid);
    }

    // ------------------------------------------------------------------
    // Memoria
    // ------------------------------------------------------------------

    @Test
    public void memorySamplesAccumulateAndKeepHistoryLimit() throws Exception {
        store.ensureStarted();
        assertEquals(1, store.state().memory.samples);

        env.memory = sample(50_000);
        env.now = NOW + 29L * MINUTE;
        store.sampleMemoryIfDue(env.now);
        assertEquals(1, store.state().memory.samples);

        env.now = NOW + 31L * MINUTE;
        store.sampleMemoryIfDue(env.now);

        DiagnosticsState state = store.state();
        assertEquals(2, state.memory.samples);
        assertEquals(45_000, state.memory.minPssKb);
        assertEquals(50_000, state.memory.maxPssKb);
        assertEquals(2, state.memory.history.size());
        assertEquals(env.now, state.memory.sampledAt);
        assertEquals(50_000, state.memory.totalPssKb);

        for (int i = 0; i < 50; i++) {
            env.now += 31L * MINUTE;
            env.memory = sample(40_000 + (i % 5) * 1_000);
            store.sampleMemoryIfDue(env.now);
        }
        store.flush();

        DiagnosticsState persisted = read();
        assertEquals(52, persisted.memory.samples);
        assertEquals(48, persisted.memory.history.size());
        assertEquals(40_000, persisted.memory.minPssKb);
        assertEquals(50_000, persisted.memory.maxPssKb);
    }

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    @Test
    public void heartbeatTrimAndLifecycleUpdateProcess() throws Exception {
        store.ensureStarted();

        long heartbeatAt = NOW + 6L * MINUTE;
        env.now = heartbeatAt;
        store.heartbeatIfDue(heartbeatAt);
        assertEquals(heartbeatAt, store.state().process.lastHeartbeatAt);

        store.heartbeatIfDue(heartbeatAt + 1_000L);
        assertEquals(heartbeatAt, store.state().process.lastHeartbeatAt);

        long trimAt = heartbeatAt + 2_000L;
        store.onTrimMemory(15, trimAt);
        assertEquals(15, store.state().process.lastTrimLevel);
        assertEquals(trimAt, store.state().process.lastTrimAt);

        long foregroundAt = heartbeatAt + 3_000L;
        store.onForeground(foregroundAt);
        assertEquals(foregroundAt, store.state().process.lastForegroundAt);

        long backgroundAt = heartbeatAt + 4_000L;
        store.onBackground(backgroundAt);

        DiagnosticsState state = read();
        assertEquals(heartbeatAt, state.process.lastHeartbeatAt);
        assertEquals(15, state.process.lastTrimLevel);
        assertEquals(trimAt, state.process.lastTrimAt);
        assertEquals(foregroundAt, state.process.lastForegroundAt);
        assertEquals(backgroundAt, state.process.lastBackgroundAt);
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private void restart() {
        store.close();
        executor = Executors.newSingleThreadScheduledExecutor();
        store = new DiagnosticsStore(dir, env, executor);
        store.ensureStarted();
    }

    private File file() {
        return new File(dir, DiagnosticsStore.FILE_NAME);
    }

    private DiagnosticsState read() throws Exception {
        DiagnosticsCodec.Decoded decoded =
                DiagnosticsCodec.decode(readRaw());
        assertNotNull(decoded);
        assertFalse(decoded.readOnly);
        return decoded.state;
    }

    private String readRaw() throws Exception {
        return new String(Files.readAllBytes(file().toPath()),
                StandardCharsets.UTF_8);
    }

    private void writeRaw(String raw) throws Exception {
        Files.write(file().toPath(), raw.getBytes(StandardCharsets.UTF_8));
    }

    private static void deleteRecursively(File file) {
        if (file == null) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static MatchesSnapshot matches(SourceStatus... statuses) {
        Map<String, SourceStatus> sources = new LinkedHashMap<>();
        if (statuses != null) {
            for (SourceStatus status : statuses) {
                if (status != null) sources.put(status.sourceId, status);
            }
        }

        List<Match> matches = new ArrayList<>();
        Match first = new Match();
        first.id = 1L;
        first.sourceId = "365scores";
        first.homeName = "Real Madrid";
        first.awayName = "Rayo";
        matches.add(first);

        Match second = new Match();
        second.id = 2L;
        second.sourceId = "rfef";
        second.homeName = "As Celtas";
        second.awayName = "Barcelona";
        matches.add(second);

        return new MatchesSnapshot(DAY, matches, sources, NOW, false);
    }

    private static WeatherSnapshot weather(WeatherSnapshot.State stateValue,
                                           boolean partial, String error) {
        Map<String, WeatherCache.Entry> byCity = new LinkedHashMap<>();
        byCity.put("tres-cantos", new WeatherCache.Entry(
                new Weather(20.5, 19.0, 3.2, 55, 1, 1, "2026-11-14T10:00:00Z"),
                NOW));
        return new WeatherSnapshot(byCity, NOW, stateValue, partial, error);
    }

    private static DiagnosticsState.ExitEvent crashEvent(long at, int pid) {
        return new DiagnosticsState.ExitEvent(at, at, "crash",
                ExitReasonMapper.REASON_CRASH, 0, pid, 4, "Fallo de prueba",
                "system", 42L, 0L);
    }

    private static DiagnosticsState.ExitEvent anrEvent(long at, int pid) {
        return new DiagnosticsState.ExitEvent(at, at, "anr",
                ExitReasonMapper.REASON_ANR, 0, pid, 4, "ANR de prueba",
                "system", 42L, 0L);
    }

    private static DiagnosticsState.ExitEvent lmkEvent(long at, int pid) {
        return new DiagnosticsState.ExitEvent(at, at, "lmk",
                ExitReasonMapper.REASON_LOW_MEMORY, 0, pid, 4, "",
                "system", 42L, 0L);
    }

    private static DiagnosticsState.ExitEvent unknownEvent(long at, int pid) {
        return new DiagnosticsState.ExitEvent(at, at, "unknown",
                ExitReasonMapper.REASON_UNKNOWN, 0, pid, 4, "",
                "inferred", 42L, 0L);
    }

    private static ProcessMetrics.MemorySample sample(int pssKb) {
        return new ProcessMetrics.MemorySample(pssKb, pssKb / 4, pssKb / 5,
                pssKb / 6, pssKb / 10, pssKb / 3, 4);
    }

    private int exitKeysCount(DiagnosticsState state) {
        return state.exitScanKeys.size();
    }

    /** Entorno mutable e independiente de Android. */
    private static final class FakeEnvironment implements AndroidEnvironment {

        long now = NOW;
        int pid = 100;
        long startElapsed = 5_000L;
        int boot = 7;
        int versionCode = 42;
        String versionName = "1.2.3";
        int sdk = 34;
        ProcessMetrics.MemorySample memory = sample(45_000);
        final List<DiagnosticsState.ExitEvent> exits = new ArrayList<>();

        @Override
        public long now() {
            return now;
        }

        @Override
        public int pid() {
            return pid;
        }

        @Override
        public long startElapsedRealtime() {
            return startElapsed;
        }

        @Override
        public int bootCount() {
            return boot;
        }

        @Override
        public int appVersionCode() {
            return versionCode;
        }

        @Override
        public String appVersionName() {
            return versionName;
        }

        @Override
        public int sdkInt() {
            return sdk;
        }

        @Override
        public ProcessMetrics.MemorySample sampleMemory() {
            return memory;
        }

        @Override
        public ExitInfoReader.Scan scanExits(List<String> seenKeys, long now,
                                             int appVersionCode,
                                             long sessionDurationMs) {
            Set<String> seen = new HashSet<>();
            if (seenKeys != null) seen.addAll(seenKeys);

            List<DiagnosticsState.ExitEvent> events = new ArrayList<>();
            List<String> keys = new ArrayList<>();
            for (DiagnosticsState.ExitEvent template : exits) {
                String key = ExitInfoReader.key(template.at, template.pid,
                        template.reasonCode, template.status);
                keys.add(key);
                if (seen.contains(key)) continue;

                events.add(new DiagnosticsState.ExitEvent(
                        template.at, now, template.cause,
                        template.reasonCode, template.status, template.pid,
                        template.importance, template.description,
                        template.confidence, appVersionCode,
                        sessionDurationMs));
            }
            return new ExitInfoReader.Scan(events, keys);
        }
    }
}
