package com.itriguero.homeclock;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Persistencia del diagnóstico en {@code noBackupFilesDir/diagnostics.json}.
 *
 * Mantiene el {@link DiagnosticsState} actualizado a partir de los avisos de
 * {@link Diagnostics}, de los ciclos de vida del proceso y de una muestra
 * periódica de PSS. La escritura es atómica (tmp + rename) y best-effort: un
 * fallo de disco nunca debe afectar al refresco de la app.
 *
 * Los cambios se agrupan con un debounce de 20 s desde el primer cambio y como
 * máximo 60 s; las transiciones de caída/recuperación de una fuente y los
 * pasos a segundo plano se guardan de inmediato. Si el fichero lo escribió una
 * versión futura ({@link DiagnosticsCodec.Decoded#readOnly}) el store no
 * sobrescribe nada.
 */
final class DiagnosticsStore implements Diagnostics.Listener {

    static final String FILE_NAME = "diagnostics.json";
    static final int MAX_FILE_BYTES = 64 * 1024;

    static final long HEARTBEAT_MS = 5L * 60_000L;
    static final long MEMORY_SAMPLE_MS = 30L * 60_000L;
    static final long DEBOUNCE_MS = 20_000L;
    static final long MAX_DEBOUNCE_MS = 60_000L;

    private static volatile DiagnosticsStore instance;

    /** Singleton del proceso; usa {@code noBackupFilesDir} para no respaldarlo. */
    static DiagnosticsStore get(Context context) {
        DiagnosticsStore local = instance;
        if (local != null) return local;

        synchronized (DiagnosticsStore.class) {
            if (instance == null) {
                Context app = context.getApplicationContext();
                if (app == null) app = context;
                File dir = app.getNoBackupFilesDir();
                if (dir == null) dir = app.getFilesDir();
                instance = new DiagnosticsStore(dir,
                        new AndroidProcessEnvironment(app), newExecutor());
            }
            return instance;
        }
    }

    private static ScheduledExecutorService newExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "homeclock-diagnostics");
            thread.setDaemon(true);
            return thread;
        });
    }

    private final File dir;
    private final File file;
    private final AndroidEnvironment env;
    private final ScheduledExecutorService executor;

    private DiagnosticsState state = DiagnosticsState.empty();
    private boolean started;
    private boolean readOnly;
    private boolean closed;
    private boolean dirty;
    private long dirtySince;
    private ScheduledFuture<?> pending;
    private long revision;

    /** Constructor inyectable para tests; el executor pasa a ser propiedad. */
    DiagnosticsStore(File dir, AndroidEnvironment env,
                     ScheduledExecutorService executor) {
        this.dir = dir;
        this.file = new File(dir, FILE_NAME);
        this.env = env;
        this.executor = executor;
    }

    // ------------------------------------------------------------------
    // Arranque
    // ------------------------------------------------------------------

    /**
     * Lee el estado persistido y abre la sesión actual. Es idempotente y
     * tolerante a ficheros corruptos o de versiones futuras.
     */
    synchronized void ensureStarted() {
        if (started) return;
        started = true;

        long now = env.now();
        DiagnosticsCodec.Decoded decoded = readFile();
        readOnly = decoded != null && decoded.readOnly;
        DiagnosticsState previous = decoded == null ? null : decoded.state;
        boolean firstStart = previous == null || previous.firstSeenAt <= 0L;

        int pid = Math.max(0, env.pid());
        long startElapsed = Math.max(0L, env.startElapsedRealtime());
        int bootCount = Math.max(0, env.bootCount());
        int versionCode = Math.max(0, env.appVersionCode());
        String versionName = env.appVersionName() == null
                ? "" : env.appVersionName();
        int sdk = Math.max(0, env.sdkInt());

        DiagnosticsStateBuilder builder;
        ExitInfoReader.Scan scan;
        int sessions;
        long sessionDurationMs = 0L;

        if (firstStart) {
            builder = DiagnosticsStateBuilder.from(null);
            builder.firstSeenAt(now);
            sessions = 1;
            // Primera instalación: solo interesan las claves, no las salidas
            // históricas de procesos anteriores a este diagnóstico.
            scan = env.scanExits(Collections.emptyList(), now, versionCode, 0L);
        } else {
            builder = DiagnosticsStateBuilder.from(previous);
            builder.firstSeenAt(previous.firstSeenAt > 0L
                    ? previous.firstSeenAt : now);

            long previousStartedAt = previous.process.startedAt;
            sessionDurationMs = previousStartedAt > 0L
                    ? Math.max(0L, now - previousStartedAt) : 0L;

            String previousSignature = signature(previous.process.pid,
                    previous.process.startElapsedRealtime,
                    previous.process.bootCount);
            String currentSignature = signature(pid, startElapsed, bootCount);

            boolean reboot = previous.process.bootCount != bootCount;
            boolean update = previous.appVersionCode != versionCode;
            boolean sameProcess = previousSignature.equals(currentSignature);

            scan = env.scanExits(previous.exitScanKeys, now, versionCode,
                    sessionDurationMs);

            // Si el sistema ya reporta la salida del proceso anterior, no se
            // añade además una salida inferida: sería el mismo evento dos veces.
            boolean systemReportedPrevious = false;
            if (!sameProcess) {
                for (DiagnosticsState.ExitEvent event : scan.events) {
                    if (event.pid == previous.process.pid) {
                        systemReportedPrevious = true;
                        break;
                    }
                }
            }

            List<DiagnosticsState.ExitEvent> inferred = new ArrayList<>();
            if (!systemReportedPrevious) {
                if (reboot) {
                    inferred.add(inferredExit("reboot", previous,
                            sessionDurationMs, now));
                } else if (update) {
                    inferred.add(inferredExit("update", previous,
                            sessionDurationMs, now));
                } else if (!sameProcess) {
                    inferred.add(inferredExit("unknown", previous,
                            sessionDurationMs, now));
                }
            }

            sessions = reboot || update || !sameProcess
                    ? increment(previous.counters.sessions)
                    : previous.counters.sessions;
            builder.addExits(inferred);
        }

        builder.version(versionCode, versionName, sdk);
        // En el primer arranque solo se recuerdan las claves: las salidas
        // anteriores no son atribuibles a este diagnóstico.
        if (!firstStart) {
            builder.addExits(scan.events);
        }
        builder.exitScanKeys(mergeKeys(scan.keys,
                previous == null
                        ? Collections.emptyList() : previous.exitScanKeys));

        DiagnosticsState.Counters counters = firstStart
                ? DiagnosticsState.Counters.empty() : previous.counters;
        counters = withSessions(counters, sessions);
        if (!firstStart) {
            for (DiagnosticsState.ExitEvent event : scan.events) {
                counters = countExit(counters, event);
            }
        }
        builder.counters(counters);

        DiagnosticsState.MemoryInfo memory = mergeMemory(
                firstStart ? DiagnosticsState.MemoryInfo.empty()
                        : previous.memory,
                env.sampleMemory(), now);
        builder.memory(memory);

        builder.process(new DiagnosticsState.ProcessInfo(
                pid, now, startElapsed, bootCount, now, 0L, now, 0L, 0, 0L,
                0L, sessions));

        state = builder.build(now);
        if (!readOnly) {
            writeLocked();
        }
    }

    private static String signature(int pid, long startElapsedRealtime,
                                    int bootCount) {
        return pid + ":" + startElapsedRealtime + ":" + bootCount;
    }

    private static DiagnosticsState.ExitEvent inferredExit(
            String cause, DiagnosticsState previous, long sessionDurationMs,
            long now) {
        return new DiagnosticsState.ExitEvent(now, now, cause, 0, 0,
                previous.process.pid, 0, "", "inferred",
                previous.appVersionCode, sessionDurationMs);
    }

    /**
     * Une las claves de esta pasada con las que ya se conocían, sin duplicados
     * y respetando {@link DiagnosticsState#MAX_EXIT_KEYS}.
     */
    private static List<String> mergeKeys(List<String> scanned,
                                          List<String> previous) {
        Set<String> merged = new LinkedHashSet<>();
        if (scanned != null) {
            for (String key : scanned) {
                if (key == null || key.isEmpty()) continue;
                if (merged.size() >= DiagnosticsState.MAX_EXIT_KEYS) break;
                merged.add(key);
            }
        }
        if (previous != null) {
            for (String key : previous) {
                if (key == null || key.isEmpty()) continue;
                if (merged.size() >= DiagnosticsState.MAX_EXIT_KEYS) break;
                merged.add(key);
            }
        }
        return new ArrayList<>(merged);
    }

    private static DiagnosticsState.Counters withSessions(
            DiagnosticsState.Counters counters, int sessions) {
        return new DiagnosticsState.Counters(sessions,
                counters.matchesAttempts, counters.matchesOk,
                counters.matchesFail, counters.weatherAttempts,
                counters.weatherOk, counters.weatherFail, counters.exits,
                counters.crashes, counters.anrs, counters.lmk,
                counters.unknownExits);
    }

    private static DiagnosticsState.Counters countExit(
            DiagnosticsState.Counters counters,
            DiagnosticsState.ExitEvent event) {
        if (event == null) return counters;

        int crashes = counters.crashes;
        int anrs = counters.anrs;
        int lmk = counters.lmk;
        int unknown = counters.unknownExits;

        if ("crash".equals(event.cause) || "crash_native".equals(event.cause)) {
            crashes = increment(crashes);
        } else if ("anr".equals(event.cause)) {
            anrs = increment(anrs);
        } else if ("lmk".equals(event.cause)) {
            lmk = increment(lmk);
        } else if ("unknown".equals(event.cause)) {
            unknown = increment(unknown);
        }

        return new DiagnosticsState.Counters(counters.sessions,
                counters.matchesAttempts, counters.matchesOk,
                counters.matchesFail, counters.weatherAttempts,
                counters.weatherOk, counters.weatherFail,
                increment(counters.exits), crashes, anrs, lmk, unknown);
    }

    private static DiagnosticsState.MemoryInfo mergeMemory(
            DiagnosticsState.MemoryInfo previous,
            ProcessMetrics.MemorySample sample, long at) {
        DiagnosticsState.MemoryInfo base = previous == null
                ? DiagnosticsState.MemoryInfo.empty() : previous;
        if (sample == null) return base;

        int pss = Math.max(0, sample.totalPssKb);
        int samples = increment(base.samples);
        int min = base.samples > 0 && base.minPssKb > 0
                ? Math.min(base.minPssKb, pss) : pss;
        int max = base.samples > 0
                ? Math.max(base.maxPssKb, pss) : pss;

        List<DiagnosticsState.Sample> history =
                new ArrayList<>(base.history);
        history.add(new DiagnosticsState.Sample(at, pss));

        return new DiagnosticsState.MemoryInfo(at, pss, sample.javaKb,
                sample.nativeKb, sample.codeKb, sample.graphicsKb,
                sample.privateDirtyKb, sample.importance, samples, min, max,
                history);
    }

    // ------------------------------------------------------------------
    // Listener de Diagnostics
    // ------------------------------------------------------------------

    @Override
    public synchronized void onChanged(Diagnostics.Snapshot snapshot) {
        if (snapshot == null) return;
        if (!started) ensureStarted();

        boolean transition = hasDownTransition(state, snapshot);

        DiagnosticsState.MatchesInfo matches = state.matches;
        if (snapshot.matchesAt > 0L) {
            matches = new DiagnosticsState.MatchesInfo(
                    snapshot.matchesDate, snapshot.matchesAttemptAt,
                    snapshot.matchesAt, snapshot.matchesDurationMs,
                    snapshot.fetchedAt, snapshot.stale, isPartial(snapshot),
                    snapshot.matchCount, snapshot.sourceHealth);
        }

        DiagnosticsState.WeatherInfo weather = state.weather;
        if (snapshot.weatherAttemptAt > 0L
                || (snapshot.weatherState != null
                && !snapshot.weatherState.isEmpty())) {
            weather = new DiagnosticsState.WeatherInfo(
                    snapshot.weatherAttemptAt, snapshot.weatherAt,
                    snapshot.weatherDurationMs, snapshot.weatherAt,
                    snapshot.weatherState, snapshot.weatherPartial,
                    snapshot.weatherError, snapshot.weatherCityCount,
                    snapshot.weatherConsecutiveFailures,
                    snapshot.weatherLastOkAt, snapshot.weatherLastErrorAt,
                    snapshot.weatherDownSince);
        }

        DiagnosticsState.Counters counters = state.counters;
        if (snapshot.matchesAt > 0L
                && snapshot.matchesAt != state.matches.finishedAt) {
            int attempts = increment(counters.matchesAttempts);
            int ok = counters.matchesOk;
            int fail = counters.matchesFail;
            if (isMatchesSuccess(snapshot)) {
                ok = increment(ok);
            } else {
                fail = increment(fail);
            }
            counters = new DiagnosticsState.Counters(counters.sessions,
                    attempts, ok, fail, counters.weatherAttempts,
                    counters.weatherOk, counters.weatherFail,
                    counters.exits, counters.crashes, counters.anrs,
                    counters.lmk, counters.unknownExits);
        }
        if (snapshot.weatherAttemptAt > 0L
                && snapshot.weatherAttemptAt != state.weather.attemptStartedAt) {
            int attempts = increment(counters.weatherAttempts);
            int ok = counters.weatherOk;
            int fail = counters.weatherFail;
            if (isWeatherSuccess(snapshot)) {
                ok = increment(ok);
            } else {
                fail = increment(fail);
            }
            counters = new DiagnosticsState.Counters(counters.sessions,
                    counters.matchesAttempts, counters.matchesOk,
                    counters.matchesFail, attempts, ok, fail,
                    counters.exits, counters.crashes, counters.anrs,
                    counters.lmk, counters.unknownExits);
        }

        state = DiagnosticsStateBuilder.from(state)
                .matches(matches)
                .weather(weather)
                .counters(counters)
                .build(state.savedAt);

        if (readOnly) {
            dirty = false;
            dirtySince = 0L;
            return;
        }

        if (transition) {
            saveNowLocked();
        } else {
            scheduleLocked(env.now());
        }
    }

    private static boolean isPartial(Diagnostics.Snapshot snapshot) {
        if (snapshot.stale) return true;
        for (SourceHealth health : snapshot.sourceHealth.values()) {
            if (health == null) continue;
            if (health.state != SourceState.OK
                    && health.state != SourceState.EMPTY) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMatchesSuccess(Diagnostics.Snapshot snapshot) {
        if (snapshot.stale) return false;
        for (SourceHealth health : snapshot.sourceHealth.values()) {
            if (health == null) continue;
            if (health.state != SourceState.OK
                    && health.state != SourceState.EMPTY) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWeatherSuccess(Diagnostics.Snapshot snapshot) {
        if (!"FRESH".equals(snapshot.weatherState)) return false;
        return snapshot.weatherError == null || snapshot.weatherError.isEmpty();
    }

    /**
     * ¿Alguna fuente (o el tiempo) cambió entre caída y recuperación? Solo se
     * mira la transición booleana, no un cambio de hora estando ya caída.
     */
    private static boolean hasDownTransition(DiagnosticsState state,
                                             Diagnostics.Snapshot snapshot) {
        if ((state.weather.downSince > 0L)
                != (snapshot.weatherDownSince > 0L)) {
            return true;
        }

        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(state.matches.sources.keySet());
        keys.addAll(snapshot.sourceHealth.keySet());

        for (String key : keys) {
            SourceHealth before = state.matches.sources.get(key);
            SourceHealth after = snapshot.sourceHealth.get(key);
            boolean downBefore = before != null && before.downSince > 0L;
            boolean downAfter = after != null && after.downSince > 0L;
            if (downBefore != downAfter) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Ciclo de vida del proceso
    // ------------------------------------------------------------------

    synchronized void onForeground(long now) {
        if (!started) ensureStarted();
        long at = at(now);
        DiagnosticsState.ProcessInfo process = state.process;
        state = DiagnosticsStateBuilder.from(state)
                .process(new DiagnosticsState.ProcessInfo(
                        process.pid, process.startedAt,
                        process.startElapsedRealtime, process.bootCount,
                        process.lastHeartbeatAt,
                        process.lastHeartbeatElapsedRealtime, at,
                        process.lastBackgroundAt, process.lastTrimLevel,
                        process.lastTrimAt, process.activityClosedAt,
                        process.sessionCount))
                .build(state.savedAt);
        scheduleLocked(at);
    }

    synchronized void onBackground(long now) {
        if (!started) ensureStarted();
        long at = at(now);
        DiagnosticsState.ProcessInfo process = state.process;
        state = DiagnosticsStateBuilder.from(state)
                .process(new DiagnosticsState.ProcessInfo(
                        process.pid, process.startedAt,
                        process.startElapsedRealtime, process.bootCount,
                        process.lastHeartbeatAt,
                        process.lastHeartbeatElapsedRealtime,
                        process.lastForegroundAt, at,
                        process.lastTrimLevel, process.lastTrimAt,
                        process.activityClosedAt, process.sessionCount))
                .build(state.savedAt);
        saveNowLocked();
    }

    synchronized void onTrimMemory(int level, long now) {
        if (!started) ensureStarted();
        long at = at(now);
        DiagnosticsState.ProcessInfo process = state.process;
        state = DiagnosticsStateBuilder.from(state)
                .process(new DiagnosticsState.ProcessInfo(
                        process.pid, process.startedAt,
                        process.startElapsedRealtime, process.bootCount,
                        process.lastHeartbeatAt,
                        process.lastHeartbeatElapsedRealtime,
                        process.lastForegroundAt, process.lastBackgroundAt,
                        level, at, process.activityClosedAt,
                        process.sessionCount))
                .build(state.savedAt);
        scheduleLocked(at);
    }

    /** Late como máximo cada {@link #HEARTBEAT_MS} para detectar procesos vivos. */
    synchronized void heartbeatIfDue(long now) {
        if (!started) ensureStarted();
        long at = at(now);
        DiagnosticsState.ProcessInfo process = state.process;
        if (process.lastHeartbeatAt > 0L
                && at - process.lastHeartbeatAt < HEARTBEAT_MS) {
            return;
        }

        state = DiagnosticsStateBuilder.from(state)
                .process(new DiagnosticsState.ProcessInfo(
                        process.pid, process.startedAt,
                        process.startElapsedRealtime, process.bootCount,
                        at, process.lastHeartbeatElapsedRealtime,
                        process.lastForegroundAt, process.lastBackgroundAt,
                        process.lastTrimLevel, process.lastTrimAt,
                        process.activityClosedAt, process.sessionCount))
                .build(state.savedAt);
        scheduleLocked(at);
    }

    /** Toma una muestra de PSS como máximo cada {@link #MEMORY_SAMPLE_MS}. */
    synchronized void sampleMemoryIfDue(long now) {
        if (!started) ensureStarted();
        long at = at(now);
        if (state.memory.sampledAt > 0L
                && at - state.memory.sampledAt < MEMORY_SAMPLE_MS) {
            return;
        }

        ProcessMetrics.MemorySample sample = env.sampleMemory();
        if (sample == null) return;

        state = DiagnosticsStateBuilder.from(state)
                .memory(mergeMemory(state.memory, sample, at))
                .build(state.savedAt);
        scheduleLocked(at);
    }

    /**
     * Escribe ya el estado actual y cancela el guardado pendiente. Si el
     * fichero es de una versión futura no hace nada.
     */
    synchronized void flush() {
        if (!started) return;
        cancelPendingLocked();
        if (readOnly) {
            dirty = false;
            dirtySince = 0L;
            return;
        }
        writeLocked();
    }

    /**
     * Devuelve el estado actual (inmutable) para Ajustes. Si aún no se llamó a
     * {@link #ensureStarted()} devuelve el estado vacío.
     */
    synchronized DiagnosticsState state() {
        return state;
    }

    /** Contador interno de escrituras correctas. */
    synchronized long revision() {
        return revision;
    }

    /** Cancela el guardado pendiente y libera el executor (tests). */
    synchronized void close() {
        cancelPendingLocked();
        closed = true;
        if (executor == null) return;
        try {
            executor.shutdownNow();
        } catch (RuntimeException ignored) {
            // Apagar un executor ya cerrado no debe romper nada.
        }
    }

    // ------------------------------------------------------------------
    // Debounce y escritura
    // ------------------------------------------------------------------

    private void scheduleLocked(long at) {
        if (readOnly || closed || executor == null) return;

        if (!dirty) {
            dirty = true;
            dirtySince = at;
        }
        long due = Math.min(at + DEBOUNCE_MS, dirtySince + MAX_DEBOUNCE_MS);
        long delay = Math.max(0L, due - at);

        cancelPendingLocked();
        try {
            pending = executor.schedule(this::flushFromScheduler, delay,
                    TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            pending = null;
        }
    }

    private void flushFromScheduler() {
        synchronized (this) {
            pending = null;
            if (closed || readOnly || !dirty) return;
            writeLocked();
        }
    }

    private void saveNowLocked() {
        cancelPendingLocked();
        writeLocked();
    }

    private void cancelPendingLocked() {
        if (pending != null) {
            pending.cancel(false);
            pending = null;
        }
    }

    private void writeLocked() {
        if (readOnly || state == null) return;

        state = DiagnosticsStateBuilder.from(state).build(env.now());
        dirty = false;
        dirtySince = 0L;

        if (writeFile(state)) {
            revision++;
        }
    }

    private boolean writeFile(DiagnosticsState snapshot) {
        String raw = DiagnosticsCodec.encode(snapshot);
        if (raw.isEmpty() || dir == null) return false;

        File tmp = new File(dir, FILE_NAME + ".tmp." + System.nanoTime());
        try {
            if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
                return false;
            }
            try (OutputStream out = new FileOutputStream(tmp)) {
                out.write(raw.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            try {
                Files.move(tmp.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (tmp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        }
    }

    private DiagnosticsCodec.Decoded readFile() {
        if (dir == null || file == null) return null;
        if (!file.isFile() || file.length() <= 0L
                || file.length() > MAX_FILE_BYTES) {
            return null;
        }

        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            return DiagnosticsCodec.decode(
                    new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private long at(long now) {
        return now > 0L ? now : env.now();
    }

    private static int increment(int value) {
        return value == Integer.MAX_VALUE ? value : value + 1;
    }
}
