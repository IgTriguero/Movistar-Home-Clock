package com.itriguero.homeclock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Estado persistible del diagnóstico, sin dependencias de Android.
 *
 * Es inmutable: los constructores copian las colecciones y normalizan los
 * valores imposibles. El store de la fase 2 construirá una instancia nueva en
 * cada guardado; aquí no hay setters ni {@code with...}.
 */
final class DiagnosticsState {

    static final int MAX_SOURCES = 16;
    static final int MAX_EXITS = 5;
    static final int MAX_HISTORY = 48;
    static final int MAX_EXIT_KEYS = 16;

    static final int MAX_ERROR_CHARS = 120;
    static final int MAX_DESCRIPTION_CHARS = 160;
    static final int MAX_ID_CHARS = 32;

    final long savedAt;
    final long firstSeenAt;
    final long appVersionCode;
    final String appVersionName;
    final int sdk;
    final ProcessInfo process;
    final MatchesInfo matches;
    final WeatherInfo weather;
    final MemoryInfo memory;
    final List<ExitEvent> exits;
    final Counters counters;
    final List<String> exitScanKeys;

    DiagnosticsState(long savedAt, long firstSeenAt, long appVersionCode,
                     String appVersionName, int sdk,
                     ProcessInfo process, MatchesInfo matches,
                     WeatherInfo weather, MemoryInfo memory,
                     List<ExitEvent> exits, Counters counters,
                     List<String> exitScanKeys) {
        this.savedAt = Math.max(0L, savedAt);
        this.firstSeenAt = Math.max(0L, firstSeenAt);
        this.appVersionCode = Math.max(0L, appVersionCode);
        this.appVersionName = appVersionName == null ? "" : appVersionName;
        this.sdk = Math.max(0, sdk);
        this.process = process == null ? ProcessInfo.empty() : process;
        this.matches = matches == null ? MatchesInfo.empty() : matches;
        this.weather = weather == null ? WeatherInfo.empty() : weather;
        this.memory = memory == null ? MemoryInfo.empty() : memory;
        this.exits = newestExits(exits);
        this.counters = counters == null ? Counters.empty() : counters;
        this.exitScanKeys = keysCopy(exitScanKeys);
    }

    static DiagnosticsState empty() {
        return new DiagnosticsState(0L, 0L, 0L, "", 0,
                ProcessInfo.empty(), MatchesInfo.empty(), WeatherInfo.empty(),
                MemoryInfo.empty(), Collections.emptyList(), Counters.empty(),
                Collections.emptyList());
    }

    private static List<ExitEvent> newestExits(List<ExitEvent> events) {
        List<ExitEvent> copy = new ArrayList<>();
        if (events != null) {
            for (ExitEvent event : events) {
                if (event != null) copy.add(event);
            }
        }
        copy.sort(Comparator.comparingLong((ExitEvent event) -> event.at).reversed());
        if (copy.size() > MAX_EXITS) {
            copy = new ArrayList<>(copy.subList(0, MAX_EXITS));
        }
        return Collections.unmodifiableList(copy);
    }

    private static List<String> keysCopy(List<String> keys) {
        List<String> copy = new ArrayList<>();
        if (keys != null) {
            for (String key : keys) {
                if (key == null || key.isEmpty()) continue;
                if (copy.size() >= MAX_EXIT_KEYS) break;
                copy.add(key);
            }
        }
        return Collections.unmodifiableList(copy);
    }

    /** Datos del proceso actual y de sus pasos a primer/segundo plano. */
    static final class ProcessInfo {

        final int pid;
        final long startedAt;
        final long startElapsedRealtime;
        final int bootCount;
        final long lastHeartbeatAt;
        final long lastHeartbeatElapsedRealtime;
        final long lastForegroundAt;
        final long lastBackgroundAt;
        final int lastTrimLevel;
        final long lastTrimAt;
        final long activityClosedAt;
        final int sessionCount;

        ProcessInfo(int pid, long startedAt, long startElapsedRealtime,
                    int bootCount, long lastHeartbeatAt,
                    long lastHeartbeatElapsedRealtime, long lastForegroundAt,
                    long lastBackgroundAt, int lastTrimLevel, long lastTrimAt,
                    long activityClosedAt, int sessionCount) {
            this.pid = Math.max(0, pid);
            this.startedAt = Math.max(0L, startedAt);
            this.startElapsedRealtime = Math.max(0L, startElapsedRealtime);
            this.bootCount = Math.max(0, bootCount);
            this.lastHeartbeatAt = Math.max(0L, lastHeartbeatAt);
            this.lastHeartbeatElapsedRealtime = Math.max(0L, lastHeartbeatElapsedRealtime);
            this.lastForegroundAt = Math.max(0L, lastForegroundAt);
            this.lastBackgroundAt = Math.max(0L, lastBackgroundAt);
            this.lastTrimLevel = Math.max(0, lastTrimLevel);
            this.lastTrimAt = Math.max(0L, lastTrimAt);
            this.activityClosedAt = Math.max(0L, activityClosedAt);
            this.sessionCount = Math.max(0, sessionCount);
        }

        static ProcessInfo empty() {
            return new ProcessInfo(0, 0L, 0L, 0, 0L, 0L, 0L, 0L, 0, 0L, 0L, 0);
        }
    }

    /** Último intento de partidos y salud de cada fuente. */
    static final class MatchesInfo {

        final String date;
        final long attemptStartedAt;
        final long finishedAt;
        final long durationMs;
        final long fetchedAt;
        final boolean stale;
        final boolean partial;
        final int matchCount;
        final Map<String, SourceHealth> sources;

        MatchesInfo(String date, long attemptStartedAt, long finishedAt,
                    long durationMs, long fetchedAt, boolean stale,
                    boolean partial, int matchCount,
                    Map<String, SourceHealth> sources) {
            this.date = date == null ? "" : date;
            this.attemptStartedAt = Math.max(0L, attemptStartedAt);
            this.finishedAt = Math.max(0L, finishedAt);
            this.durationMs = Math.max(0L, durationMs);
            this.fetchedAt = Math.max(0L, fetchedAt);
            this.stale = stale;
            this.partial = partial;
            this.matchCount = Math.max(0, matchCount);

            Map<String, SourceHealth> copy = new LinkedHashMap<>();
            if (sources != null) {
                for (Map.Entry<String, SourceHealth> entry : sources.entrySet()) {
                    SourceHealth health = entry.getValue();
                    if (health == null) continue;
                    String key = entry.getKey();
                    if (key == null || key.isEmpty()) key = health.sourceId;
                    copy.put(key, health);
                }
            }
            this.sources = Collections.unmodifiableMap(copy);
        }

        static MatchesInfo empty() {
            return new MatchesInfo("", 0L, 0L, 0L, 0L, false, false, 0,
                    Collections.emptyMap());
        }
    }

    /** Último intento del tiempo por ciudad. */
    static final class WeatherInfo {

        final long attemptStartedAt;
        final long finishedAt;
        final long durationMs;
        final long fetchedAt;
        final String state;
        final boolean partial;
        final String error;
        final int cityCount;
        final int consecutiveFailures;
        final long lastOkAt;
        final long lastErrorAt;
        final long downSince;

        WeatherInfo(long attemptStartedAt, long finishedAt, long durationMs,
                    long fetchedAt, String state, boolean partial, String error,
                    int cityCount, int consecutiveFailures, long lastOkAt,
                    long lastErrorAt, long downSince) {
            this.attemptStartedAt = Math.max(0L, attemptStartedAt);
            this.finishedAt = Math.max(0L, finishedAt);
            this.durationMs = Math.max(0L, durationMs);
            this.fetchedAt = Math.max(0L, fetchedAt);
            this.state = normalizeState(state);
            this.partial = partial;
            this.error = error == null ? "" : error;
            this.cityCount = Math.max(0, cityCount);
            this.consecutiveFailures = Math.max(0, consecutiveFailures);
            this.lastOkAt = Math.max(0L, lastOkAt);
            this.lastErrorAt = Math.max(0L, lastErrorAt);
            this.downSince = Math.max(0L, downSince);
        }

        private static String normalizeState(String state) {
            if ("FRESH".equals(state) || "CACHED".equals(state)
                    || "EMPTY".equals(state)) {
                return state;
            }
            return "EMPTY";
        }

        static WeatherInfo empty() {
            return new WeatherInfo(0L, 0L, 0L, 0L, "EMPTY", false, "",
                    0, 0, 0L, 0L, 0L);
        }
    }

    /** Última muestra de memoria y su histórico acotado. */
    static final class MemoryInfo {

        final long sampledAt;
        final int totalPssKb;
        final int javaKb;
        final int nativeKb;
        final int codeKb;
        final int graphicsKb;
        final int privateDirtyKb;
        final int importance;
        final int samples;
        final int minPssKb;
        final int maxPssKb;
        final List<Sample> history;

        MemoryInfo(long sampledAt, int totalPssKb, int javaKb, int nativeKb,
                   int codeKb, int graphicsKb, int privateDirtyKb, int importance,
                   int samples, int minPssKb, int maxPssKb,
                   List<Sample> history) {
            this.sampledAt = Math.max(0L, sampledAt);
            this.totalPssKb = Math.max(0, totalPssKb);
            this.javaKb = Math.max(0, javaKb);
            this.nativeKb = Math.max(0, nativeKb);
            this.codeKb = Math.max(0, codeKb);
            this.graphicsKb = Math.max(0, graphicsKb);
            this.privateDirtyKb = Math.max(0, privateDirtyKb);
            this.importance = Math.max(0, importance);
            this.samples = Math.max(0, samples);
            this.minPssKb = Math.max(0, minPssKb);
            this.maxPssKb = Math.max(0, maxPssKb);
            this.history = historyCopy(history);
        }

        private static List<Sample> historyCopy(List<Sample> samples) {
            List<Sample> copy = new ArrayList<>();
            if (samples != null) {
                for (Sample sample : samples) {
                    if (sample != null) copy.add(sample);
                }
            }
            copy.sort(Comparator.comparingLong((Sample sample) -> sample.at));
            if (copy.size() > MAX_HISTORY) {
                copy = new ArrayList<>(
                        copy.subList(copy.size() - MAX_HISTORY, copy.size()));
            }
            return Collections.unmodifiableList(copy);
        }

        static MemoryInfo empty() {
            return new MemoryInfo(0L, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    Collections.emptyList());
        }
    }

    /** Una muestra puntual de PSS para ver la tendencia. */
    static final class Sample {

        final long at;
        final int totalPssKb;

        Sample(long at, int totalPssKb) {
            this.at = Math.max(0L, at);
            this.totalPssKb = Math.max(0, totalPssKb);
        }
    }

    /** Una salida del proceso observada con ApplicationExitInfo. */
    static final class ExitEvent {

        final long at;
        final long observedAt;
        final String cause;
        final int reasonCode;
        final int status;
        final int pid;
        final int importance;
        final String description;
        final String confidence;
        final long appVersionCode;
        final long sessionDurationMs;

        ExitEvent(long at, long observedAt, String cause, int reasonCode,
                  int status, int pid, int importance, String description,
                  String confidence, long appVersionCode,
                  long sessionDurationMs) {
            this.at = Math.max(0L, at);
            this.observedAt = Math.max(0L, observedAt);
            this.cause = cause == null || cause.isEmpty() ? "unknown" : cause;
            this.reasonCode = Math.max(0, reasonCode);
            this.status = Math.max(0, status);
            this.pid = Math.max(0, pid);
            this.importance = Math.max(0, importance);
            this.description = description == null ? "" : description;
            this.confidence = confidence == null || confidence.isEmpty()
                    ? "inferred" : confidence;
            this.appVersionCode = Math.max(0L, appVersionCode);
            this.sessionDurationMs = Math.max(0L, sessionDurationMs);
        }
    }

    /** Contadores acumulados de arranques, refrescos y salidas. */
    static final class Counters {

        final int sessions;
        final int matchesAttempts;
        final int matchesOk;
        final int matchesFail;
        final int weatherAttempts;
        final int weatherOk;
        final int weatherFail;
        final int exits;
        final int crashes;
        final int anrs;
        final int lmk;
        final int unknownExits;

        Counters(int sessions, int matchesAttempts, int matchesOk,
                 int matchesFail, int weatherAttempts, int weatherOk,
                 int weatherFail, int exits, int crashes, int anrs, int lmk,
                 int unknownExits) {
            this.sessions = Math.max(0, sessions);
            this.matchesAttempts = Math.max(0, matchesAttempts);
            this.matchesOk = Math.max(0, matchesOk);
            this.matchesFail = Math.max(0, matchesFail);
            this.weatherAttempts = Math.max(0, weatherAttempts);
            this.weatherOk = Math.max(0, weatherOk);
            this.weatherFail = Math.max(0, weatherFail);
            this.exits = Math.max(0, exits);
            this.crashes = Math.max(0, crashes);
            this.anrs = Math.max(0, anrs);
            this.lmk = Math.max(0, lmk);
            this.unknownExits = Math.max(0, unknownExits);
        }

        static Counters empty() {
            return new Counters(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }
}
