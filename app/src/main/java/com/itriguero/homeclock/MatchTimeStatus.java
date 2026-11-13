package com.itriguero.homeclock;

/**
 * Resuelve el estado de un partido cuando la fuente no da un estado vivo real.
 * Es deliberadamente conservador: no convierte en "finalizado" un partido con
 * marcador hasta que ha pasado el margen de resultado, y no inventa un vivo
 * después de la duración máxima.
 */
final class MatchTimeStatus {

    private MatchTimeStatus() {
    }

    static MatchStatus resolve(boolean pastDay, boolean timeTbd, boolean scoreKnown,
                               long startMillis, long nowMillis,
                               long settleMs, long durationMs) {
        if (pastDay) {
            return scoreKnown ? MatchStatus.FINISHED : MatchStatus.UNKNOWN;
        }
        if (nowMillis > startMillis + durationMs) {
            return scoreKnown ? MatchStatus.FINISHED : MatchStatus.UNKNOWN;
        }
        if (!timeTbd && scoreKnown && nowMillis > startMillis + settleMs) {
            return MatchStatus.FINISHED;
        }
        if (!timeTbd && nowMillis >= startMillis) {
            return MatchStatus.LIVE_APPROX;
        }
        return MatchStatus.SCHEDULED;
    }
}
