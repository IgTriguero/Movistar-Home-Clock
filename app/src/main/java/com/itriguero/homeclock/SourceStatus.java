package com.itriguero.homeclock;

/**
 * Estado observable de una fuente dentro de un snapshot.
 *
 * {@code fromCache} indica que los datos de esta fuente se sirvieron de una
 * caché tras un fallo de red (la propia de RFEF/Celta o la caché de partidos
 * del repositorio). {@code matchCount} es el número de partidos de la fuente
 * que sobreviven en el snapshot final.
 */
public final class SourceStatus {

    public final String sourceId;
    public final SourceState state;
    public final String error;
    public final long fetchedAt;
    public final long durationMs;
    public final boolean fromCache;
    public final int matchCount;

    public SourceStatus(String sourceId, SourceState state, String error,
                        long fetchedAt, long durationMs) {
        this(sourceId, state, error, fetchedAt, durationMs, false, 0);
    }

    public SourceStatus(String sourceId, SourceState state, String error,
                        long fetchedAt, long durationMs,
                        boolean fromCache, int matchCount) {
        this.sourceId = sourceId == null ? "" : sourceId;
        this.state = state == null ? SourceState.ERROR : state;
        this.error = error;
        this.fetchedAt = fetchedAt;
        this.durationMs = durationMs;
        this.fromCache = fromCache;
        this.matchCount = Math.max(0, matchCount);
    }

    /**
     * Marca la fuente como servida desde caché y fija cuántos partidos se
     * reutilizaron. Se usa al recuperar partidos cacheados de una fuente caída.
     */
    public SourceStatus withCache(int reusedMatches) {
        return new SourceStatus(sourceId, state, error, fetchedAt, durationMs,
                true, reusedMatches);
    }

    /**
     * Ajusta el conteo final de partidos sin alterar el resto del estado.
     */
    public SourceStatus withMatchCount(int finalMatches) {
        return new SourceStatus(sourceId, state, error, fetchedAt, durationMs,
                fromCache, finalMatches);
    }
}
