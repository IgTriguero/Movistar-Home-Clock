package com.itriguero.homeclock;

/**
 * Salud acumulada de una fuente de datos.
 *
 * Es un modelo puro e inmutable: no toca disco ni Android. {@code error} puede
 * llegar sin recortar; el {@link DiagnosticsCodec} limita su tamaño al
 * persistirlo. {@code isDown} aplica la ventana de frescura de
 * {@link SourceHealthPolicy#HEALTH_STALE_MS}.
 */
final class SourceHealth {

    final String sourceId;
    final SourceState state;
    final String error;
    final long lastAttemptAt;
    final long lastOkAt;
    final long lastErrorAt;
    final long durationMs;
    final boolean fromCache;
    final int matchCount;
    final int consecutiveFailures;
    final long downSince;
    final int attempts;
    final int failures;

    SourceHealth(String sourceId, SourceState state, String error,
                 long lastAttemptAt, long lastOkAt, long lastErrorAt,
                 long durationMs, boolean fromCache, int matchCount,
                 int consecutiveFailures, long downSince,
                 int attempts, int failures) {
        this.sourceId = sourceId == null ? "" : sourceId;
        this.state = state == null ? SourceState.ERROR : state;
        this.error = error;
        this.lastAttemptAt = Math.max(0L, lastAttemptAt);
        this.lastOkAt = Math.max(0L, lastOkAt);
        this.lastErrorAt = Math.max(0L, lastErrorAt);
        this.durationMs = Math.max(0L, durationMs);
        this.fromCache = fromCache;
        this.matchCount = Math.max(0, matchCount);
        this.consecutiveFailures = Math.max(0, consecutiveFailures);
        this.downSince = Math.max(0L, downSince);
        this.attempts = Math.max(0, attempts);
        this.failures = Math.max(0, failures);
    }

    /**
     * Estado neutro de una fuente de la que todavía no se sabe nada. No cuenta
     * como intento y, por tanto, nunca está caída.
     */
    static SourceHealth initial(String sourceId) {
        return new SourceHealth(sourceId, SourceState.ERROR, null,
                0L, 0L, 0L, 0L, false, 0, 0, 0L, 0, 0);
    }

    /**
     * Una fuente está caída si se marcó la caída y su último intento no ha
     * quedado obsoleto (más de 24 h sin intentos). Sin intentos recientes no
     * tiene sentido seguir avisando de una caída.
     */
    boolean isDown(long now) {
        if (downSince <= 0L) return false;
        return now - lastAttemptAt <= SourceHealthPolicy.HEALTH_STALE_MS;
    }
}
