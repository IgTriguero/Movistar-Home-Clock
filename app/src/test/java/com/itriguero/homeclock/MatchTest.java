package com.itriguero.homeclock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MatchTest {

    @Test
    public void defaultsToUnknownStatusAndUnknownScore() {
        Match match = new Match();
        assertEquals(MatchStatus.UNKNOWN, match.status);
        assertFalse(match.scoreKnown);
    }

    @Test
    public void liveMeansOnlyExactLiveStatus() {
        Match match = new Match();

        match.status = MatchStatus.LIVE;
        assertTrue(match.isLive());
        assertFalse(match.isLiveApprox());
        assertTrue(match.isInPlay());

        match.status = MatchStatus.LIVE_APPROX;
        assertFalse(match.isLive());
        assertTrue(match.isLiveApprox());
        assertTrue(match.isInPlay());

        match.status = MatchStatus.SCHEDULED;
        assertFalse(match.isLive());
        assertFalse(match.isInPlay());
    }

    @Test
    public void scheduledFinishedPostponedAndCancelledAreExclusive() {
        Match match = new Match();

        match.status = MatchStatus.SCHEDULED;
        assertTrue(match.isScheduled());
        assertFalse(match.isFinished());
        assertFalse(match.isPostponed());
        assertFalse(match.isCancelled());

        match.status = MatchStatus.FINISHED;
        assertFalse(match.isScheduled());
        assertTrue(match.isFinished());
        assertFalse(match.isPostponed());
        assertFalse(match.isCancelled());

        match.status = MatchStatus.POSTPONED;
        assertFalse(match.isScheduled());
        assertFalse(match.isFinished());
        assertTrue(match.isPostponed());
        assertFalse(match.isCancelled());

        match.status = MatchStatus.CANCELLED;
        assertFalse(match.isScheduled());
        assertFalse(match.isFinished());
        assertFalse(match.isPostponed());
        assertTrue(match.isCancelled());
    }
}
