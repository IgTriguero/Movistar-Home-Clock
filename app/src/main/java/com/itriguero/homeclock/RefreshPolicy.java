package com.itriguero.homeclock;

/**
 * Decide cuánto esperar antes del siguiente snapshot de partidos.
 */
final class RefreshPolicy {

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;

    private static final long LIVE_BASKETBALL = 15_000L;
    private static final long LIVE_BASKETBALL_CLUTCH = 5_000L;
    private static final int BASKETBALL_CLUTCH_SECONDS = 2 * 60;
    private static final long LIVE_FOOTBALL = MINUTE;
    private static final long SOON_KICKOFF = MINUTE;
    private static final long APPROX_LIVE = 5 * MINUTE;
    private static final long SOON_WINDOW = 10 * MINUTE;
    private static final long NEAR_WINDOW = 30 * MINUTE;
    private static final long ERROR_DELAY = 10 * MINUTE;
    private static final long STALE_DELAY = 15 * MINUTE;
    private static final long IDLE_DELAY = 6 * HOUR;

    private RefreshPolicy() {
    }

    static long nextDelay(MatchesSnapshot snapshot, long nowMillis) {
        if (snapshot == null) return STALE_DELAY;

        boolean liveBasketball = false;
        boolean basketballClutch = false;
        boolean liveFootball = false;
        boolean liveApprox = false;
        long nextScheduled = Long.MAX_VALUE;

        for (Match match : snapshot.matches) {
            if (match == null) continue;

            if (match.isLive()) {
                if ("Baloncesto".equalsIgnoreCase(match.sport)) {
                    liveBasketball = true;
                    int seconds = basketballSeconds(match.gameTimeDisplay);
                    if (seconds >= 0 && seconds <= BASKETBALL_CLUTCH_SECONDS) {
                        basketballClutch = true;
                    }
                } else {
                    liveFootball = true;
                }
            } else if (match.isLiveApprox()) {
                liveApprox = true;
            }

            if (match.isScheduled() && !match.timeTbd) {
                long delta = match.startMillis - nowMillis;
                if (delta < nextScheduled) nextScheduled = delta;
            }
        }

        long liveDelay = Long.MAX_VALUE;
        if (liveBasketball) {
            liveDelay = basketballClutch ? LIVE_BASKETBALL_CLUTCH : LIVE_BASKETBALL;
        } else if (liveFootball) {
            liveDelay = LIVE_FOOTBALL;
        } else if (liveApprox) {
            liveDelay = APPROX_LIVE;
        }

        long scheduledDelay = Long.MAX_VALUE;
        if (nextScheduled != Long.MAX_VALUE) {
            if (nextScheduled <= NEAR_WINDOW) {
                scheduledDelay = SOON_KICKOFF;
            } else if (nextScheduled <= 2 * HOUR) {
                scheduledDelay = SOON_WINDOW;
            } else if (nextScheduled <= 6 * HOUR) {
                scheduledDelay = NEAR_WINDOW;
            } else {
                scheduledDelay = IDLE_DELAY;
            }
        }

        long errorDelay = snapshot.stale
                ? STALE_DELAY
                : (snapshot.hasErrors() ? ERROR_DELAY : Long.MAX_VALUE);

        long delay = Math.min(liveDelay, Math.min(scheduledDelay, errorDelay));
        return delay == Long.MAX_VALUE ? IDLE_DELAY : delay;
    }

    private static int basketballSeconds(String display) {
        if (display == null) return -1;
        String[] parts = display.trim().split(":");
        if (parts.length != 2) return -1;
        try {
            int minutes = Integer.parseInt(parts[0].trim());
            int seconds = Integer.parseInt(parts[1].trim());
            if (minutes < 0 || seconds < 0 || seconds > 59) return -1;
            return minutes * 60 + seconds;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
