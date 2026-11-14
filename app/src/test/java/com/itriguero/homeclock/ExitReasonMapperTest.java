package com.itriguero.homeclock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExitReasonMapperTest {

    @Test
    public void mapsUnknownReason() {
        assertEquals("unknown", ExitReasonMapper.cause(0, 0));
        assertEquals("inferred", ExitReasonMapper.confidence(0));
    }

    @Test
    public void mapsSelfExitAsOther() {
        assertEquals("other", ExitReasonMapper.cause(1, 0));
        assertEquals("system", ExitReasonMapper.confidence(1));
    }

    @Test
    public void mapsSignaledOnlySigkillAsSignal() {
        assertEquals("signal", ExitReasonMapper.cause(2, 9));
        assertEquals("other", ExitReasonMapper.cause(2, 15));
        assertEquals("inferred", ExitReasonMapper.confidence(2));
    }

    @Test
    public void mapsLowMemory() {
        assertEquals("lmk", ExitReasonMapper.cause(3, 0));
        assertEquals("system", ExitReasonMapper.confidence(3));
    }

    @Test
    public void mapsCrashesAndAnr() {
        assertEquals("crash", ExitReasonMapper.cause(4, 0));
        assertEquals("crash_native", ExitReasonMapper.cause(5, 0));
        assertEquals("anr", ExitReasonMapper.cause(6, 0));
        assertEquals("system", ExitReasonMapper.confidence(4));
        assertEquals("system", ExitReasonMapper.confidence(5));
        assertEquals("system", ExitReasonMapper.confidence(6));
    }

    @Test
    public void mapsInitializationAndPermissionAsOther() {
        assertEquals("other", ExitReasonMapper.cause(7, 0));
        assertEquals("other", ExitReasonMapper.cause(8, 0));
        assertEquals("system", ExitReasonMapper.confidence(7));
        assertEquals("system", ExitReasonMapper.confidence(8));
    }

    @Test
    public void mapsResourceAndUserReasons() {
        assertEquals("resource", ExitReasonMapper.cause(9, 0));
        assertEquals("user", ExitReasonMapper.cause(10, 0));
        assertEquals("user", ExitReasonMapper.cause(11, 0));
        assertEquals("system", ExitReasonMapper.confidence(9));
        assertEquals("system", ExitReasonMapper.confidence(10));
        assertEquals("system", ExitReasonMapper.confidence(11));
    }

    @Test
    public void mapsRemainingSystemReasons() {
        assertEquals("other", ExitReasonMapper.cause(12, 0));
        assertEquals("other", ExitReasonMapper.cause(13, 0));
        assertEquals("frozen", ExitReasonMapper.cause(14, 0));
        assertEquals("other", ExitReasonMapper.cause(15, 0));
        assertEquals("update", ExitReasonMapper.cause(16, 0));

        assertEquals("system", ExitReasonMapper.confidence(12));
        assertEquals("system", ExitReasonMapper.confidence(13));
        assertEquals("system", ExitReasonMapper.confidence(14));
        assertEquals("system", ExitReasonMapper.confidence(15));
        assertEquals("system", ExitReasonMapper.confidence(16));
    }

    @Test
    public void unknownCodesAreInferred() {
        assertEquals("unknown", ExitReasonMapper.cause(17, 0));
        assertEquals("unknown", ExitReasonMapper.cause(-1, 0));
        assertEquals("unknown", ExitReasonMapper.cause(99, 9));
        assertEquals("inferred", ExitReasonMapper.confidence(17));
        assertEquals("inferred", ExitReasonMapper.confidence(-1));
        assertEquals("inferred", ExitReasonMapper.confidence(99));
    }

    @Test
    public void supportStartsAtApi30() {
        assertFalse(ExitReasonMapper.isSupported(29));
        assertTrue(ExitReasonMapper.isSupported(30));
        assertTrue(ExitReasonMapper.isSupported(35));
    }
}
