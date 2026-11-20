package com.itriguero.homeclock;

import java.util.Locale;

/**
 * Política de refresco del detalle de un partido.
 *
 * Es pura (sin Android ni reloj propio): el llamador pasa {@code now} y usa el
 * retardo devuelto para programar la siguiente consulta. Un partido finalizado
 * no se vuelve a pedir nunca.
 */
final class MatchDetailRefreshPolicy {

    static final long LIVE_FOOTBALL = 20_000L;
    static final long LIVE_BASKETBALL = 15_000L;
    static final long LIVE_APPROX = 30_000L;
    static final long SCHEDULED_NEAR = 60_000L;
    static final long ERROR = 30_000L;

    private static final long SCHEDULED_WINDOW_MS = 10L * 60L * 1000L;

    private MatchDetailRefreshPolicy() {
    }

    static long nextDelay(Match match, MatchDetail detail, long now) {
        if (match == null) {
            return detail != null && detail.isFinished() ? Long.MAX_VALUE : ERROR;
        }

        if (match.isFinished() || (detail != null && detail.isFinished())) {
            return Long.MAX_VALUE;
        }

        if (match.isLiveApprox()) return LIVE_APPROX;

        if (match.isLive()) {
            String sport = match.sport;
            if (sport == null || sport.trim().isEmpty()) {
                sport = detail == null ? "" : detail.sport;
            }
            String normalized = sport == null
                    ? "" : sport.toLowerCase(Locale.ROOT);
            if (normalized.contains("fútbol") || normalized.contains("futbol")) {
                return LIVE_FOOTBALL;
            }
            if (normalized.contains("baloncesto")) return LIVE_BASKETBALL;
            return LIVE_APPROX;
        }

        if (match.isScheduled()) {
            if (match.startMillis <= 0) return Long.MAX_VALUE;
            long untilStart = match.startMillis - now;
            return untilStart <= SCHEDULED_WINDOW_MS
                    ? SCHEDULED_NEAR : Long.MAX_VALUE;
        }

        // Aplazado o cancelado: no hay un instante conocido en el que mejore.
        if (match.isPostponed() || match.isCancelled()) return Long.MAX_VALUE;

        return ERROR;
    }
}
