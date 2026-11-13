package com.itriguero.homeclock;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KickoffGuardTest {

    @Test
    public void firstCallRefreshes() {
        KickoffGuard guard = new KickoffGuard();
        assertTrue(guard.shouldRefresh(1_000L, 60_000L));
    }

    @Test
    public void secondCallInsideCooldownDoesNotRefresh() {
        KickoffGuard guard = new KickoffGuard();
        assertTrue(guard.shouldRefresh(1_000L, 60_000L));
        assertFalse(guard.shouldRefresh(2_000L, 60_000L));
        assertFalse(guard.shouldRefresh(60_999L, 60_000L));
    }

    @Test
    public void refreshAllowedAfterCooldown() {
        KickoffGuard guard = new KickoffGuard();
        assertTrue(guard.shouldRefresh(1_000L, 60_000L));
        assertTrue(guard.shouldRefresh(61_000L, 60_000L));
    }
}
