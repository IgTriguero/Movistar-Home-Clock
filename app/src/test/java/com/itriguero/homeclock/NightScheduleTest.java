package com.itriguero.homeclock;

import org.junit.Test;

import java.time.LocalTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NightScheduleTest {

    private static final LocalTime START = LocalTime.of(23, 0);
    private static final LocalTime END = LocalTime.of(7, 0);

    @Test
    public void typicalNightRangeIsExclusiveAtEnd() {
        assertFalse(NightSchedule.isNight(LocalTime.of(22, 59), START, END));
        assertTrue(NightSchedule.isNight(LocalTime.of(23, 0), START, END));
        assertTrue(NightSchedule.isNight(LocalTime.MIDNIGHT, START, END));
        assertTrue(NightSchedule.isNight(LocalTime.of(6, 59), START, END));
        assertFalse(NightSchedule.isNight(LocalTime.of(7, 0), START, END));
    }

    @Test
    public void nonCrossingRange() {
        LocalTime start = LocalTime.of(1, 0);
        LocalTime end = LocalTime.of(6, 0);

        assertFalse(NightSchedule.isNight(LocalTime.of(0, 59), start, end));
        assertTrue(NightSchedule.isNight(LocalTime.of(1, 0), start, end));
        assertTrue(NightSchedule.isNight(LocalTime.of(5, 59), start, end));
        assertFalse(NightSchedule.isNight(LocalTime.of(6, 0), start, end));
    }

    @Test
    public void sameStartAndEndIsNeverNight() {
        LocalTime start = LocalTime.of(23, 0);

        assertFalse(NightSchedule.isNight(LocalTime.of(22, 59), start, start));
        assertFalse(NightSchedule.isNight(LocalTime.of(23, 0), start, start));
        assertFalse(NightSchedule.isNight(LocalTime.MIDNIGHT, start, start));
    }

    @Test
    public void validatesHhMmFormat() {
        assertTrue(NightSchedule.isValidHhMm("00:00"));
        assertTrue(NightSchedule.isValidHhMm("23:59"));
        assertTrue(NightSchedule.isValidHhMm("07:00"));

        assertFalse(NightSchedule.isValidHhMm("7:00"));
        assertFalse(NightSchedule.isValidHhMm("24:00"));
        assertFalse(NightSchedule.isValidHhMm("23:60"));
        assertFalse(NightSchedule.isValidHhMm(null));
        assertFalse(NightSchedule.isValidHhMm(""));
    }

    @Test
    public void parseOrDefaultFallsBackForInvalidValues() {
        assertEquals(LocalTime.of(23, 0),
                NightSchedule.parseOrDefault("7:00", "23:00"));
        assertEquals(LocalTime.of(7, 0),
                NightSchedule.parseOrDefault(null, "07:00"));
        assertEquals(LocalTime.of(7, 0),
                NightSchedule.parseOrDefault("", "07:00"));
        assertEquals(LocalTime.of(1, 30),
                NightSchedule.parseOrDefault("01:30", "23:00"));
    }

    @Test
    public void parseHhMmRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class,
                () -> NightSchedule.parseHhMm("24:00"));
        assertThrows(IllegalArgumentException.class,
                () -> NightSchedule.parseHhMm("23:60"));
        assertThrows(IllegalArgumentException.class,
                () -> NightSchedule.parseHhMm(null));
    }
}
