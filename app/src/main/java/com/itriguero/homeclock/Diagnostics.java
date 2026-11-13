package com.itriguero.homeclock;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Diagnóstico en memoria de la última actualización de partidos y tiempo.
 *
 * No toca disco ni Android: la UI puede leer este estado y, si la app acaba
 * de arrancar, caer a {@link MatchCache} o a las preferencias como respaldo.
 * Todos los métodos son thread-safe.
 */
public final class Diagnostics {

    private static final long MINUTE_MS = 60_000L;
    private static final long HOUR_MS = 60L * MINUTE_MS;

    private static final Diagnostics INSTANCE = new Diagnostics();

    /** Store compartido por MainActivity y SettingsActivity (mismo proceso). */
    public static Diagnostics get() {
        return INSTANCE;
    }

    private long matchesAt;
    private long matchesDurationMs;
    private long weatherAt;
    private boolean stale;
    private int matchCount;
    private Map<String, SourceStatus> sources = Collections.emptyMap();
    private long fetchedAt;

    public synchronized void recordMatches(MatchesSnapshot snapshot,
                                           long durationMs) {
        if (snapshot == null) return;

        matchesAt = System.currentTimeMillis();
        matchesDurationMs = Math.max(0L, durationMs);
        stale = snapshot.stale;
        matchCount = snapshot.matches == null ? 0 : snapshot.matches.size();
        sources = snapshot.sources == null
                ? Collections.emptyMap()
                : new LinkedHashMap<>(snapshot.sources);
        fetchedAt = snapshot.fetchedAt;
    }

    public synchronized void recordWeather(long fetchedAt) {
        weatherAt = fetchedAt;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(matchesAt, matchesDurationMs, weatherAt, stale,
                matchCount, sources, fetchedAt);
    }

    /**
     * Etiqueta relativa pura: "ahora", "hace X min", "hace X h" o "—" si no
     * hay referencia temporal.
     */
    public static String ageLabel(long thenMillis, long nowMillis) {
        if (thenMillis <= 0L) return "—";

        long delta = nowMillis - thenMillis;
        if (delta < 0L) delta = 0L;
        if (delta < MINUTE_MS) return "ahora";
        if (delta < HOUR_MS) return "hace " + (delta / MINUTE_MS) + " min";
        return "hace " + (delta / HOUR_MS) + " h";
    }

    /**
     * Copia inmutable del último estado conocido.
     */
    public static final class Snapshot {

        public final long matchesAt;
        public final long matchesDurationMs;
        public final long weatherAt;
        public final boolean stale;
        public final int matchCount;
        public final Map<String, SourceStatus> sources;
        public final long fetchedAt;

        Snapshot(long matchesAt, long matchesDurationMs, long weatherAt,
                 boolean stale, int matchCount,
                 Map<String, SourceStatus> sources, long fetchedAt) {
            this.matchesAt = matchesAt;
            this.matchesDurationMs = matchesDurationMs;
            this.weatherAt = weatherAt;
            this.stale = stale;
            this.matchCount = matchCount;
            Map<String, SourceStatus> copy = new LinkedHashMap<>();
            if (sources != null) copy.putAll(sources);
            this.sources = Collections.unmodifiableMap(copy);
            this.fetchedAt = fetchedAt;
        }
    }
}
