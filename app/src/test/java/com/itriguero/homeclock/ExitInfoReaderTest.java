package com.itriguero.homeclock;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExitInfoReaderTest {

    @Test
    public void supportStartsAtApi30() {
        assertFalse(ExitInfoReader.isSupported(29));
        assertTrue(ExitInfoReader.isSupported(30));
        assertTrue(ExitInfoReader.isSupported(35));
    }
}
