package com.itriguero.homeclock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PagerMathTest {

    private static final int WIDTH = 1000;
    /** 800 dp/s con densidad 2 son 1600 px/s. */
    private static final float DENSITY = 2f;
    private static final float FLING = 1600f;

    @Test
    public void dragBelowThresholdKeepsPage() {
        assertEquals(2,
                PagerMath.settleTarget(2, 5, 290f, 0f, WIDTH, DENSITY));
    }

    @Test
    public void dragAboveThresholdMovesToNextPage() {
        assertEquals(3,
                PagerMath.settleTarget(2, 5, 310f, 0f, WIDTH, DENSITY));
    }

    @Test
    public void dragExactlyAtThresholdMovesToNextPage() {
        assertEquals(3,
                PagerMath.settleTarget(2, 5, 300f, 0f, WIDTH, DENSITY));
    }

    @Test
    public void negativeDragMovesToPreviousPage() {
        assertEquals(1,
                PagerMath.settleTarget(2, 5, -310f, 0f, WIDTH, DENSITY));
    }

    @Test
    public void negativeDragBelowThresholdKeepsPage() {
        assertEquals(2,
                PagerMath.settleTarget(2, 5, -290f, 0f, WIDTH, DENSITY));
    }

    @Test
    public void flingAboveEightHundredDpPerSecondChangesPage() {
        assertEquals(3,
                PagerMath.settleTarget(2, 5, -10f, FLING + 1f, WIDTH, DENSITY));
    }

    @Test
    public void flingExactlyAtThresholdChangesPage() {
        assertEquals(3,
                PagerMath.settleTarget(2, 5, 0f, FLING, WIDTH, DENSITY));
    }

    @Test
    public void flingBelowThresholdUsesDragDistance() {
        assertEquals(2,
                PagerMath.settleTarget(2, 5, 100f, FLING - 1f, WIDTH, DENSITY));
        assertEquals(3,
                PagerMath.settleTarget(2, 5, 400f, FLING - 1f, WIDTH, DENSITY));
    }

    @Test
    public void negativeFlingMovesToPreviousPage() {
        assertEquals(1,
                PagerMath.settleTarget(2, 5, 10f, -FLING, WIDTH, DENSITY));
    }

    @Test
    public void flingWinsOverOppositeSmallDrag() {
        assertEquals(3,
                PagerMath.settleTarget(2, 5, -10f, FLING, WIDTH, DENSITY));
    }

    @Test
    public void firstPageDoesNotMoveBeforeStart() {
        assertEquals(0,
                PagerMath.settleTarget(0, 4, -500f, -FLING, WIDTH, DENSITY));
    }

    @Test
    public void lastPageDoesNotMovePastEnd() {
        assertEquals(3,
                PagerMath.settleTarget(3, 4, 500f, FLING, WIDTH, DENSITY));
    }

    @Test
    public void singlePageNeverChanges() {
        assertEquals(0,
                PagerMath.settleTarget(0, 1, 900f, FLING, WIDTH, DENSITY));
    }

    @Test
    public void emptyPagerReturnsZero() {
        assertEquals(0,
                PagerMath.settleTarget(4, 0, 900f, FLING, WIDTH, DENSITY));
    }

    @Test
    public void currentIndexIsClamped() {
        assertEquals(0,
                PagerMath.settleTarget(-3, 4, -500f, 0f, WIDTH, DENSITY));
        assertEquals(3,
                PagerMath.settleTarget(99, 4, 500f, 0f, WIDTH, DENSITY));
    }

    @Test
    public void resistanceOnlyAppliesWhenBlocked() {
        assertEquals(100.0,
                PagerMath.resistance(100f, WIDTH, false, false), 0.001);
        assertEquals(100.0,
                PagerMath.resistance(100f, WIDTH, true, false), 0.001);
        assertEquals(-25.0,
                PagerMath.resistance(-100f, WIDTH, true, false), 0.001);
        assertEquals(25.0,
                PagerMath.resistance(100f, WIDTH, false, true), 0.001);
        assertEquals(-100.0,
                PagerMath.resistance(-100f, WIDTH, false, true), 0.001);
    }

    @Test
    public void resistanceIsCappedAtQuarterWidth() {
        assertEquals(250.0,
                PagerMath.resistance(2000f, WIDTH, false, true), 0.001);
        assertEquals(-250.0,
                PagerMath.resistance(-2000f, WIDTH, true, false), 0.001);
    }

    @Test
    public void resistanceWithZeroWidthIsZero() {
        assertEquals(0.0,
                PagerMath.resistance(300f, 0, false, true), 0.001);
    }

    @Test
    public void horizontalOnlyWhenDxDominates() {
        assertTrue(PagerMath.isHorizontal(100f, 10f));
        assertTrue(PagerMath.isHorizontal(-100f, 10f));
        assertFalse(PagerMath.isHorizontal(10f, 100f));
        assertFalse(PagerMath.isHorizontal(100f, 100f));
        assertFalse(PagerMath.isHorizontal(0f, 0f));
    }

    @Test
    public void thresholdsMatchTheSpecification() {
        assertEquals(0.30, PagerMath.DRAG_FRACTION, 0.0001);
        assertEquals(800.0, PagerMath.FLING_DP_PER_SECOND, 0.001);
        assertEquals(0.25, PagerMath.RESISTANCE_FACTOR, 0.0001);
        assertEquals(0.25, PagerMath.RESISTANCE_MAX_FRACTION, 0.0001);
    }
}
