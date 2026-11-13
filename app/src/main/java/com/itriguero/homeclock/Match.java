package com.itriguero.homeclock;

public class Match {
    public long id;
    public String sourceId;
    public String sport;
    public String competition;
    public long startMillis;
    public MatchStatus status = MatchStatus.UNKNOWN;
    public String statusText;
    public String gameTimeDisplay;
    public double gameTime;
    public boolean timeTbd;
    public boolean scoreKnown;
    public long homeId;
    public long awayId;
    public String homeName;
    public String awayName;
    public String homeLogoUrl;
    public String awayLogoUrl;
    public double homeScore;
    public double awayScore;
    public boolean trackedIsHome;
    public String trackedName;
    public String opponentName;

    public boolean isLive() {
        return status.isLive();
    }

    public boolean isLiveApprox() {
        return status.isLiveApprox();
    }

    public boolean isInPlay() {
        return status.isInPlay();
    }

    public boolean isScheduled() {
        return status.isScheduled();
    }

    public boolean isFinished() {
        return status.isFinished();
    }

    public boolean isPostponed() {
        return status.isPostponed();
    }

    public boolean isCancelled() {
        return status.isCancelled();
    }

    public int scoreFor() {
        return (int) Math.round(trackedIsHome ? homeScore : awayScore);
    }

    public int scoreAgainst() {
        return (int) Math.round(trackedIsHome ? awayScore : homeScore);
    }

    public String venueLabel() {
        return (trackedIsHome ? "vs " : "en ") + opponentName;
    }
}
