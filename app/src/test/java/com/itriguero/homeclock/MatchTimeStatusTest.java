package com.itriguero.homeclock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MatchTimeStatusTest {

    private static final long HOUR = 60L * 60L * 1000L;
    private static final long START = 10 * HOUR;
    private static final long SETTLE = 2 * HOUR;
    private static final long DURATION = 3 * HOUR;

    @Test
    public void futureMatchIsScheduled() {
        assertEquals(MatchStatus.SCHEDULED, resolve(false, false, false, START - HOUR));
    }

    @Test
    public void matchInWindowWithoutScoreIsLiveApprox() {
        assertEquals(MatchStatus.LIVE_APPROX,
                resolve(false, false, false, START + HOUR));
    }

    @Test
    public void scoreAfterSettleWindowIsFinished() {
        assertEquals(MatchStatus.FINISHED,
                resolve(false, false, true, START + SETTLE + 1));
    }

    @Test
    public void scoreBeforeSettleWindowIsStillLiveApprox() {
        assertEquals(MatchStatus.LIVE_APPROX,
                resolve(false, false, true, START + HOUR));
    }

    @Test
    public void noScoreAfterMaxDurationIsUnknown() {
        assertEquals(MatchStatus.UNKNOWN,
                resolve(false, false, false, START + DURATION + 1));
    }

    @Test
    public void pastDayWithoutScoreIsUnknown() {
        assertEquals(MatchStatus.UNKNOWN,
                resolve(true, true, false, START));
    }

    @Test
    public void pastDayWithScoreIsFinished() {
        assertEquals(MatchStatus.FINISHED,
                resolve(true, true, true, START));
    }

    @Test
    public void timeTbdFutureIsScheduled() {
        assertEquals(MatchStatus.SCHEDULED,
                resolve(false, true, false, START - HOUR));
    }

    private static MatchStatus resolve(boolean pastDay, boolean timeTbd, boolean scoreKnown,
                                       long now) {
        return MatchTimeStatus.resolve(pastDay, timeTbd, scoreKnown,
                START, now, SETTLE, DURATION);
    }
}
