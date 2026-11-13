package com.itriguero.homeclock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MatchStatusMapperTest {

    @Test
    public void mapsScheduled() {
        assertEquals(MatchStatus.SCHEDULED,
                MatchStatusMapper.from365(2, "Programado"));
    }

    @Test
    public void mapsLive() {
        assertEquals(MatchStatus.LIVE,
                MatchStatusMapper.from365(3, "En juego"));
    }

    @Test
    public void mapsFinished() {
        assertEquals(MatchStatus.FINISHED,
                MatchStatusMapper.from365(4, "Final"));
        assertEquals(MatchStatus.FINISHED,
                MatchStatusMapper.from365(4, null));
    }

    @Test
    public void mapsPostponedInSpanishAndEnglish() {
        assertEquals(MatchStatus.POSTPONED,
                MatchStatusMapper.from365(4, "Aplazado"));
        assertEquals(MatchStatus.POSTPONED,
                MatchStatusMapper.from365(4, "Postponed"));
        assertEquals(MatchStatus.POSTPONED,
                MatchStatusMapper.from365(4, "Suspendido"));
    }

    @Test
    public void mapsCancelledInSpanishAndEnglish() {
        assertEquals(MatchStatus.CANCELLED,
                MatchStatusMapper.from365(4, "Cancelado"));
        assertEquals(MatchStatus.CANCELLED,
                MatchStatusMapper.from365(4, "Cancelled"));
        assertEquals(MatchStatus.CANCELLED,
                MatchStatusMapper.from365(4, "Canceled"));
    }

    @Test
    public void mapsUnknownGroups() {
        assertEquals(MatchStatus.UNKNOWN,
                MatchStatusMapper.from365(1, ""));
        assertEquals(MatchStatus.UNKNOWN,
                MatchStatusMapper.from365(0, ""));
        assertEquals(MatchStatus.UNKNOWN,
                MatchStatusMapper.from365(5, ""));
    }
}
