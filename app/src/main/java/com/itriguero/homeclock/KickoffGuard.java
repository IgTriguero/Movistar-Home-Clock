package com.itriguero.homeclock;

/**
 * Evita lanzar varias refrescaciones seguidas al cruzar el inicio de un partido.
 */
final class KickoffGuard {

    private long lastRefreshMs = 0;

    boolean shouldRefresh(long nowMs, long cooldownMs) {
        if (lastRefreshMs != 0 && nowMs - lastRefreshMs < cooldownMs) return false;
        lastRefreshMs = nowMs;
        return true;
    }
}
