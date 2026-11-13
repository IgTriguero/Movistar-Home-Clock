package com.itriguero.homeclock;

import org.json.JSONException;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * Envoltorio tipado con el resultado de una fuente. Nunca lanza excepciones:
 * los fallos se representan como estado + mensaje.
 */
final class SourceResult<T> {

    final SourceState state;
    final T data;
    final String error;
    final long fetchedAt;
    final long durationMs;
    /**
     * true cuando la fuente ha servido datos de su propia caché porque la
     * red falló. Las fábricas normales lo dejan en false.
     */
    final boolean fromCache;

    private SourceResult(SourceState state, T data, String error,
                         long fetchedAt, long durationMs, boolean fromCache) {
        this.state = state;
        this.data = data;
        this.error = error;
        this.fetchedAt = fetchedAt;
        this.durationMs = durationMs;
        this.fromCache = fromCache;
    }

    static <T> SourceResult<T> ok(T data, long fetchedAt, long durationMs) {
        return ok(data, fetchedAt, durationMs, false);
    }

    static <T> SourceResult<T> ok(T data, long fetchedAt, long durationMs,
                                  boolean fromCache) {
        return new SourceResult<>(SourceState.OK, data, null,
                fetchedAt, durationMs, fromCache);
    }

    static <T> SourceResult<T> okFromCache(T data, long fetchedAt, long durationMs) {
        return ok(data, fetchedAt, durationMs, true);
    }

    static <T> SourceResult<T> empty(long fetchedAt, long durationMs) {
        return empty(fetchedAt, durationMs, false);
    }

    static <T> SourceResult<T> empty(long fetchedAt, long durationMs,
                                     boolean fromCache) {
        return new SourceResult<>(SourceState.EMPTY, null, null,
                fetchedAt, durationMs, fromCache);
    }

    static <T> SourceResult<T> emptyFromCache(long fetchedAt, long durationMs) {
        return empty(fetchedAt, durationMs, true);
    }

    static <T> SourceResult<T> error(SourceState state, String error,
                                     long fetchedAt, long durationMs) {
        SourceState safe = state == null || state == SourceState.OK || state == SourceState.EMPTY
                ? SourceState.ERROR : state;
        return new SourceResult<>(safe, null, error, fetchedAt, durationMs, false);
    }

    static <T> SourceResult<T> fromException(Exception exception, long startNanos) {
        long fetchedAt = System.currentTimeMillis();
        long durationMs = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
        Exception safe = exception == null
                ? new IllegalStateException("Error desconocido") : exception;
        return new SourceResult<>(
                classify(safe), null, message(safe), fetchedAt, durationMs, false);
    }

    boolean isOk() {
        return state == SourceState.OK || state == SourceState.EMPTY;
    }

    boolean hasData() {
        if (data == null) return false;
        if (data instanceof Collection) return !((Collection<?>) data).isEmpty();
        if (data instanceof Map) return !((Map<?, ?>) data).isEmpty();
        if (data instanceof Object[]) return ((Object[]) data).length > 0;
        return true;
    }

    private static SourceState classify(Exception exception) {
        if (hasCause(exception, SocketTimeoutException.class)) return SourceState.TIMEOUT;

        if (hasCause(exception, UnknownHostException.class)
                || hasCause(exception, ConnectException.class)
                || hasCause(exception, SocketException.class)) {
            return SourceState.OFFLINE;
        }

        if (hasMessage(exception, "http ")) return SourceState.HTTP_ERROR;
        if (hasCause(exception, JSONException.class)
                || hasMessage(exception, "parse")
                || hasMessage(exception, "json")) {
            return SourceState.PARSE_ERROR;
        }

        return SourceState.ERROR;
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 10) {
            if (type.isInstance(current)) return true;
            current = current.getCause();
            depth++;
        }
        return false;
    }

    private static boolean hasMessage(Throwable throwable, String needle) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 10) {
            String message = current.getMessage();
            if (message != null
                    && message.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }

    private static String message(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }
}
