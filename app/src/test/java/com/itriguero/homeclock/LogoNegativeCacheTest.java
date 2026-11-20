package com.itriguero.homeclock;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LogoNegativeCacheTest {

    private static final long MINUTE_MS = 60_000L;

    @Test
    public void defaultTtlIsTenMinutes() {
        LogoNegativeCache cache = new LogoNegativeCache();
        cache.markFailed("https://logos.example/a.png", 0L);

        assertTrue(cache.isFailed("https://logos.example/a.png", 10 * MINUTE_MS - 1));
        assertFalse(cache.isFailed("https://logos.example/a.png", 10 * MINUTE_MS));
    }

    @Test
    public void customTtlControlsExpiration() {
        LogoNegativeCache cache = new LogoNegativeCache(5_000L);
        cache.markFailed("https://logos.example/a.png", 100L);

        assertTrue(cache.isFailed("https://logos.example/a.png", 5_099L));
        assertFalse(cache.isFailed("https://logos.example/a.png", 5_100L));
    }

    @Test
    public void markingAgainExtendsTtl() {
        LogoNegativeCache cache = new LogoNegativeCache(1_000L);
        cache.markFailed("u", 0L);
        cache.markFailed("u", 500L);

        assertTrue(cache.isFailed("u", 1_499L));
        assertFalse(cache.isFailed("u", 1_500L));
    }

    @Test
    public void clearForgetsAllFailures() {
        LogoNegativeCache cache = new LogoNegativeCache();
        cache.markFailed("a", 0L);
        cache.markFailed("b", 0L);

        cache.clear();

        assertFalse(cache.isFailed("a", 0L));
        assertFalse(cache.isFailed("b", 0L));
    }

    @Test
    public void failuresAreIndependentPerUrl() {
        LogoNegativeCache cache = new LogoNegativeCache(1_000L);
        cache.markFailed("a", 0L);

        assertTrue(cache.isFailed("a", 10L));
        assertFalse(cache.isFailed("b", 10L));
    }

    @Test
    public void ignoresNullOrEmptyUrls() {
        LogoNegativeCache cache = new LogoNegativeCache();

        cache.markFailed(null, 0L);
        cache.markFailed("", 0L);

        assertFalse(cache.isFailed(null, 0L));
        assertFalse(cache.isFailed("", 0L));
    }

    @Test
    public void zeroTtlDisablesNegativeCache() {
        LogoNegativeCache cache = new LogoNegativeCache(0L);
        cache.markFailed("u", 123L);

        assertFalse(cache.isFailed("u", 123L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeTtl() {
        new LogoNegativeCache(-1L);
    }

    @Test
    public void clockGoingBackwardsDoesNotExpireEntry() {
        LogoNegativeCache cache = new LogoNegativeCache(1_000L);
        cache.markFailed("u", 5_000L);

        assertTrue(cache.isFailed("u", 4_000L));
    }
}
