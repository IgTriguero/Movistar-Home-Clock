package com.itriguero.homeclock;

import org.json.JSONException;
import org.junit.Test;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SourceResultTest {

    @Test
    public void classifiesSocketTimeoutAsTimeout() {
        assertEquals(SourceState.TIMEOUT, classify(new SocketTimeoutException("timeout")));
    }

    @Test
    public void classifiesHttpMessageAsHttpError() {
        assertEquals(SourceState.HTTP_ERROR, classify(new RuntimeException("HTTP 503")));
    }

    @Test
    public void classifiesJsonExceptionAsParseError() {
        assertEquals(SourceState.PARSE_ERROR, classify(new JSONException("Unexpected character")));
    }

    @Test
    public void classifiesParseMessageAsParseError() {
        assertEquals(SourceState.PARSE_ERROR, classify(new RuntimeException("parse error")));
    }

    @Test
    public void classifiesNetworkExceptionsAsOffline() {
        assertEquals(SourceState.OFFLINE, classify(new UnknownHostException("no dns")));
        assertEquals(SourceState.OFFLINE, classify(new ConnectException("refused")));
        assertEquals(SourceState.OFFLINE, classify(new SocketException("broken")));
    }

    @Test
    public void classifiesAnythingElseAsError() {
        assertEquals(SourceState.ERROR, classify(new IllegalStateException("boom")));
    }

    @Test
    public void okResultHasDataAndIsOk() {
        SourceResult<?> result = SourceResult.ok(Arrays.asList("a", "b"), 10L, 20L);

        assertEquals(SourceState.OK, result.state);
        assertTrue(result.isOk());
        assertTrue(result.hasData());
        assertEquals(10L, result.fetchedAt);
        assertEquals(20L, result.durationMs);
        assertNull(result.error);
        assertFalse(result.fromCache);
    }

    @Test
    public void emptyResultIsOkButHasNoData() {
        SourceResult<?> result = SourceResult.empty(10L, 20L);

        assertEquals(SourceState.EMPTY, result.state);
        assertTrue(result.isOk());
        assertFalse(result.hasData());
        assertFalse(result.fromCache);
    }

    @Test
    public void cacheFactoriesMarkFromCache() {
        SourceResult<?> ok = SourceResult.okFromCache(Arrays.asList("a"), 10L, 20L);
        SourceResult<?> empty = SourceResult.emptyFromCache(30L, 40L);

        assertTrue(ok.fromCache);
        assertTrue(ok.hasData());
        assertTrue(empty.fromCache);
        assertFalse(empty.hasData());
    }

    @Test
    public void errorResultIsNotOkAndKeepsStateAndMessage() {
        SourceResult<?> result = SourceResult.error(SourceState.OFFLINE, "sin red", 10L, 20L);

        assertEquals(SourceState.OFFLINE, result.state);
        assertFalse(result.isOk());
        assertFalse(result.hasData());
        assertEquals("sin red", result.error);
    }

    @Test
    public void fromExceptionFillsFetchedAtAndNonNegativeDuration() {
        SourceResult<?> result = SourceResult.fromException(
                new RuntimeException("boom"), System.nanoTime() - 5_000_000L);

        assertEquals(SourceState.ERROR, result.state);
        assertTrue(result.fetchedAt > 0);
        assertTrue(result.durationMs >= 0);
        assertEquals("boom", result.error);
    }

    private static SourceState classify(Exception exception) {
        return SourceResult.fromException(exception, System.nanoTime()).state;
    }
}
