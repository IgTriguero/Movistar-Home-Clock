package com.itriguero.homeclock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LogoNegativeCacheTest {

    private static final long MINUTE_MS = 60_000L;

    @Test
    public void defaultTtlIsTenMinutes() {
        assertEquals(10 * MINUTE_MS, new LogoNegativeCache().ttlMs());
    }

    @Test
    public void customTtlControlsExpiration() {
        LogoNegativeCache cache = new LogoNegativeCache(5_000L);
        cache.markFailed("https://logos.example/a.png", 100L);

        assertTrue(cache.isFailed("https://logos.example/a.png", 5_099L));
        assertFalse(cache.isFailed("https://logos.example/a.png", 5_100L));
    }

    @Test
    public void expirationIsExclusiveAtTtlBoundary() {
        LogoNegativeCache cache = new LogoNegativeCache(1_000L);
        cache.markFailed("u", 0L);

        assertTrue(cache.isFailed("u", 999L));
        assertFalse(cache.isFailed("u", 1_000L));
        assertEquals(0, cache.size());
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
        assertEquals(2, cache.size());

        cache.clear();

        assertEquals(0, cache.size());
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
    public void expiredEntryIsRemovedWhenRead() {
        LogoNegativeCache cache = new LogoNegativeCache(1_000L);
        cache.markFailed("u", 0L);
        assertEquals(1, cache.size());

        assertFalse(cache.isFailed("u", 1_000L));

        assertEquals(0, cache.size());
    }

    @Test
    public void markFailedPrunesExpiredEntries() {
        LogoNegativeCache cache = new LogoNegativeCache(1_000L);
        cache.markFailed("old", 0L);
        cache.markFailed("fresh", 2_000L);

        assertEquals(1, cache.size());
        assertFalse(cache.isFailed("old", 2_000L));
        assertTrue(cache.isFailed("fresh", 2_000L));
    }

    @Test
    public void pruneExpiredReturnsRemovedCount() {
        LogoNegativeCache cache = new LogoNegativeCache(1_000L);
        cache.markFailed("u", 0L);

        assertEquals(0, cache.pruneExpired(999L));
        assertEquals(1, cache.size());

        assertEquals(1, cache.pruneExpired(1_000L));
        assertEquals(0, cache.size());
    }

    @Test
    public void ignoresNullOrEmptyUrls() {
        LogoNegativeCache cache = new LogoNegativeCache();

        cache.markFailed(null, 0L);
        cache.markFailed("", 0L);

        assertEquals(0, cache.size());
        assertFalse(cache.isFailed(null, 0L));
        assertFalse(cache.isFailed("", 0L));
    }

    @Test
    public void zeroTtlDisablesNegativeCache() {
        LogoNegativeCache cache = new LogoNegativeCache(0L);
        cache.markFailed("u", 123L);

        assertFalse(cache.isFailed("u", 123L));
        assertEquals(0, cache.size());
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
