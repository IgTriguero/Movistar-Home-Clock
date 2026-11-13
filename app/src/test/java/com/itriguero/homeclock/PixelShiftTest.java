package com.itriguero.homeclock;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PixelShiftTest {

    private static final long MINUTE = 60_000L;
    private static final int PATTERN_LENGTH = 9;
    private static final int MAX_DP = 4;

    @Test
    public void intervalIsFiveMinutes() {
        assertEquals(5 * MINUTE, PixelShift.INTERVAL_MS);
    }

    @Test
    public void stepZeroIsCentered() {
        assertArrayEquals(new int[]{0, 0}, PixelShift.offsetForStep(0));
        assertArrayEquals(new int[]{0, 0}, PixelShift.offsetForElapsed(0));
    }

    @Test
    public void stepAdvancesEveryFiveMinutes() {
        assertEquals(0, PixelShift.stepForElapsed(0));
        assertEquals(0, PixelShift.stepForElapsed(5 * MINUTE - 1));
        assertEquals(1, PixelShift.stepForElapsed(5 * MINUTE));
        assertEquals(1, PixelShift.stepForElapsed(10 * MINUTE - 1));
        assertEquals(2, PixelShift.stepForElapsed(10 * MINUTE));
        assertEquals(3, PixelShift.stepForElapsed(15 * MINUTE));
    }

    @Test
    public void offsetForElapsedCombinesStepAndPattern() {
        long[] samples = {
                0L,
                1L,
                PixelShift.INTERVAL_MS - 1,
                PixelShift.INTERVAL_MS,
                7 * PixelShift.INTERVAL_MS,
                42 * PixelShift.INTERVAL_MS + 12_345L,
                -1L,
                -7 * PixelShift.INTERVAL_MS,
                Long.MAX_VALUE / 2,
        };
        for (long elapsed : samples) {
            assertArrayEquals(
                    PixelShift.offsetForStep(PixelShift.stepForElapsed(elapsed)),
                    PixelShift.offsetForElapsed(elapsed));
        }
    }

    @Test
    public void patternWrapsAround() {
        for (int step = -20; step <= 20; step++) {
            assertArrayEquals("step " + step,
                    PixelShift.offsetForStep(step),
                    PixelShift.offsetForStep(step + PATTERN_LENGTH));
        }
        assertArrayEquals(new int[]{0, 0},
                PixelShift.offsetForStep(PATTERN_LENGTH));
        assertArrayEquals(new int[]{0, 0},
                PixelShift.offsetForElapsed(PATTERN_LENGTH * PixelShift.INTERVAL_MS));
    }

    @Test
    public void negativeElapsedGoesBackwardsSafely() {
        assertEquals(-1, PixelShift.stepForElapsed(-1));
        assertEquals(-1, PixelShift.stepForElapsed(-PixelShift.INTERVAL_MS));
        assertEquals(-2, PixelShift.stepForElapsed(-PixelShift.INTERVAL_MS - 1));

        assertArrayEquals(PixelShift.offsetForStep(-1),
                PixelShift.offsetForElapsed(-1));
        assertTrue(withinBounds(PixelShift.offsetForElapsed(-123_456_789L)));
    }

    @Test
    public void exactLimitsDoNotOverflow() {
        assertEquals(Integer.MAX_VALUE,
                PixelShift.stepForElapsed(Long.MAX_VALUE));
        assertEquals(Integer.MIN_VALUE,
                PixelShift.stepForElapsed(Long.MIN_VALUE));

        assertTrue(withinBounds(PixelShift.offsetForElapsed(Long.MAX_VALUE)));
        assertTrue(withinBounds(PixelShift.offsetForElapsed(Long.MIN_VALUE)));
        assertTrue(withinBounds(PixelShift.offsetForStep(Integer.MAX_VALUE)));
        assertTrue(withinBounds(PixelShift.offsetForStep(Integer.MIN_VALUE)));
    }

    @Test
    public void allOffsetsStayWithinFourDp() {
        for (int step = -1_000; step <= 1_000; step++) {
            int[] offset = PixelShift.offsetForStep(step);
            assertEquals("step " + step, 2, offset.length);
            assertTrue("x fuera de rango en step " + step,
                    offset[0] >= -MAX_DP && offset[0] <= MAX_DP);
            assertTrue("y fuera de rango en step " + step,
                    offset[1] >= -MAX_DP && offset[1] <= MAX_DP);
        }
    }

    @Test
    public void patternVisitsNineDistinctPositions() {
        Set<String> positions = new HashSet<>();
        for (int step = 0; step < PATTERN_LENGTH; step++) {
            int[] offset = PixelShift.offsetForStep(step);
            positions.add(offset[0] + "," + offset[1]);
        }

        assertEquals(PATTERN_LENGTH, positions.size());
    }

    @Test
    public void consecutiveStepsDoNotRepeatTheSamePosition() {
        for (int step = 0; step < 20; step++) {
            assertFalse("paso repetido en " + step,
                    Arrays.equals(PixelShift.offsetForStep(step),
                            PixelShift.offsetForStep(step + 1)));
        }
    }

    @Test
    public void returnedOffsetDoesNotExposeTheSharedPattern() {
        int[] offset = PixelShift.offsetForStep(1);
        offset[0] = 99;
        offset[1] = 99;

        assertArrayEquals(new int[]{0, -4}, PixelShift.offsetForStep(1));
    }

    private static boolean withinBounds(int[] offset) {
        return offset != null
                && offset.length == 2
                && offset[0] >= -MAX_DP && offset[0] <= MAX_DP
                && offset[1] >= -MAX_DP && offset[1] <= MAX_DP;
    }
}
