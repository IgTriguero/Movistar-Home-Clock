package com.itriguero.homeclock;

import java.util.Map;

/**
 * Reglas puras de salud por fuente y clasificación global del snapshot.
 *
 * Un resultado servido desde caché nunca cuenta como éxito: la fuente no ha
 * respondido, aunque sus datos sigan siendo utilizables. Eso permite avisar de
 * fuentes caídas sin perder los partidos ya guardados.
 */
final class SourceHealthPolicy {

    /** Fallos consecutivos necesarios para marcar una fuente como caída. */
    static final int DOWN_AFTER_FAILURES = 3;

    /** Ventana sin ningún OK para considerar que la fuente lleva caída. */
    static final long DOWN_AFTER_MS = 15L * 60_000L;

    /** Pasado este tiempo sin intentos, la salud guardada ya no es fiable. */
    static final long HEALTH_STALE_MS = 24L * 60L * 60_000L;

    private SourceHealthPolicy() {
    }

    /** Éxito real: datos frescos, aunque estén vacíos. */
    static boolean isSuccess(SourceStatus status) {
        if (status == null) return false;
        if (status.fromCache) return false;
        return status.state == SourceState.OK || status.state == SourceState.EMPTY;
    }

    /** Cualquier otro caso, incluida una respuesta servida desde caché. */
    static boolean isFailure(SourceStatus status) {
        return status != null && !isSuccess(status);
    }

    /**
     * Aplica un intento al historial de la fuente. Un {@code status} nulo no
     * representa un intento y devuelve la salud anterior sin cambios.
     */
    static SourceHealth update(SourceHealth previous, SourceStatus status, long now) {
        if (status == null) return previous;

        SourceHealth base = previous == null
                ? SourceHealth.initial(status.sourceId)
                : previous;
        long at = Math.max(0L, now);
        int attempts = increment(base.attempts);
        boolean success = isSuccess(status);

        if (success) {
            return new SourceHealth(status.sourceId, status.state, status.error,
                    at, at, base.lastErrorAt, status.durationMs,
                    status.fromCache, status.matchCount,
                    0, 0L, attempts, base.failures);
        }

        int consecutiveFailures = increment(base.consecutiveFailures);
        long downSince = base.downSince;
        if (downSince <= 0L
                && consecutiveFailures >= DOWN_AFTER_FAILURES
                && (base.lastOkAt <= 0L || at - base.lastOkAt >= DOWN_AFTER_MS)) {
            downSince = at;
        }

        return new SourceHealth(status.sourceId, status.state, status.error,
                at, base.lastOkAt, at, status.durationMs,
                status.fromCache, status.matchCount,
                consecutiveFailures, downSince, attempts,
                increment(base.failures));
    }

    static boolean isDown(SourceHealth health, long now) {
        return health != null && health.isDown(now);
    }

    private static int increment(int value) {
        return value == Integer.MAX_VALUE ? value : value + 1;
    }

    /** Salud global que la UI puede resumir en una etiqueta. */
    enum GlobalHealth {
        OK,
        CACHED,
        PARTIAL,
        OFFLINE,
        ALL_FAILED
    }

    /**
     * Clasifica un snapshot completo:
     * <ul>
     *     <li>sin fuentes: {@link GlobalHealth#OK};</li>
     *     <li>todas OK/EMPTY: {@link GlobalHealth#CACHED} si alguna vino de
     *     caché y {@link GlobalHealth#OK} si no;</li>
     *     <li>ninguna OK/EMPTY: {@link GlobalHealth#OFFLINE} si todas son
     *     OFFLINE y {@link GlobalHealth#ALL_FAILED} en el resto;</li>
     *     <li>mezcla: {@link GlobalHealth#PARTIAL}.</li>
     * </ul>
     */
    static GlobalHealth classify(MatchesSnapshot snapshot) {
        if (snapshot == null || snapshot.sources == null
                || snapshot.sources.isEmpty()) {
            return GlobalHealth.OK;
        }

        int total = 0;
        int ok = 0;
        int offline = 0;
        boolean cached = false;

        for (Map.Entry<String, SourceStatus> entry : snapshot.sources.entrySet()) {
            SourceStatus status = entry.getValue();
            if (status == null) continue;

            total++;
            if (status.state == SourceState.OK || status.state == SourceState.EMPTY) {
                ok++;
                if (status.fromCache) cached = true;
            } else if (status.state == SourceState.OFFLINE) {
                offline++;
            }
        }

        if (total == 0) return GlobalHealth.OK;
        if (ok == total) return cached ? GlobalHealth.CACHED : GlobalHealth.OK;
        if (ok == 0) {
            return offline == total ? GlobalHealth.OFFLINE : GlobalHealth.ALL_FAILED;
        }
        return GlobalHealth.PARTIAL;
    }
}
