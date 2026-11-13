package com.itriguero.homeclock;

/**
 * Estado normalizado de un partido, independiente de la fuente.
 */
public enum MatchStatus {
    UNKNOWN,
    SCHEDULED,
    LIVE,
    LIVE_APPROX,
    FINISHED,
    POSTPONED,
    CANCELLED;

    public boolean isLive() {
        return this == LIVE;
    }

    public boolean isLiveApprox() {
        return this == LIVE_APPROX;
    }

    public boolean isInPlay() {
        return this == LIVE || this == LIVE_APPROX;
    }

    public boolean isScheduled() {
        return this == SCHEDULED;
    }

    public boolean isFinished() {
        return this == FINISHED;
    }

    public boolean isPostponed() {
        return this == POSTPONED;
    }

    public boolean isCancelled() {
        return this == CANCELLED;
    }

    public int rank() {
        if (isInPlay()) return 0;
        if (isScheduled()) return 1;
        if (isPostponed()) return 2;
        if (isFinished()) return 3;
        if (isCancelled()) return 4;
        return 5;
    }
}
