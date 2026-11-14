package com.itriguero.homeclock;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Repositorio asíncrono del detalle de partidos.
 *
 * Aplica la caché de finalizados, deduplica peticiones en vuelo y garantiza
 * que un {@link Request} cancelado no recibe callback. No usa {@code Looper}
 * ni APIs de UI: el consumidor debe reenviar el resultado a su hilo.
 */
public final class MatchDetailRepository {

    interface Loader {
        SourceResult<MatchDetail> load(Match match, boolean includeStats);
    }

    interface Callback {
        void onResult(SourceResult<MatchDetail> result);
    }

    interface Request {
        void cancel();
    }

    private static final Request COMPLETED = () -> {
        // Nada que cancelar: la respuesta ya se entregó o se descartó.
    };

    private final Object lock = new Object();
    private final Loader loader;
    private final MatchDetailCache cache;
    private final ExecutorService executor;

    private final Map<String, Entry> inFlight = new HashMap<>();
    private final Map<String, Long> keyGeneration = new HashMap<>();
    private long generation;
    private boolean closed;

    public MatchDetailRepository(Context context) {
        this(MatchDetailClient::fetch,
                context == null ? null
                        : new MatchDetailCache(new File(context.getFilesDir(), "match_details")),
                Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "match-detail");
                    thread.setDaemon(true);
                    return thread;
                }));
    }

    MatchDetailRepository(Loader loader, MatchDetailCache cache,
                          ExecutorService executor) {
        this.loader = loader;
        this.cache = cache;
        this.executor = executor;
    }

    public Request load(Match match, boolean includeStats, Callback callback) {
        synchronized (lock) {
            if (closed) return COMPLETED;
        }

        if (match != null && match.isFinished() && cache != null) {
            MatchDetail cached = cache.load(match);
            if (cached != null) {
                if (callback != null) {
                    callback.onResult(SourceResult.ok(
                            cached, cached.fetchedAt, 0L, true));
                }
                return COMPLETED;
            }
        }

        Entry entry;
        Handle handle;
        synchronized (lock) {
            if (closed) return COMPLETED;

            String key = key(match);
            Entry existing = key == null ? null : inFlight.get(key);
            if (existing != null && !existing.done) {
                handle = new Handle(existing, callback);
                existing.handles.add(handle);
                return handle;
            }

            long token = ++generation;
            entry = new Entry(key, token, match, includeStats);
            handle = new Handle(entry, callback);
            entry.handles.add(handle);

            if (key != null) {
                inFlight.put(key, entry);
                keyGeneration.put(key, token);
            }

            try {
                entry.future = executor.submit(() -> execute(entry));
            } catch (Exception e) {
                entry.done = true;
                if (key != null && inFlight.get(key) == entry) {
                    inFlight.remove(key);
                    keyGeneration.remove(key);
                }
                // Se entrega el error fuera del bloqueo para no retener el lock.
                SourceResult<MatchDetail> failure = SourceResult.fromException(
                        e instanceof Exception ? (Exception) e
                                : new RuntimeException(e),
                        System.nanoTime());
                entry.handles.remove(handle);
                handle.completed = true;
                callbackOutsideLock(callback, failure);
                return COMPLETED;
            }
        }
        return handle;
    }

    public void close() {
        synchronized (lock) {
            closed = true;
            for (Entry entry : inFlight.values()) {
                entry.done = true;
                if (entry.future != null) entry.future.cancel(true);
                entry.handles.clear();
            }
            inFlight.clear();
            keyGeneration.clear();
        }
        if (executor != null) executor.shutdownNow();
    }

    private void execute(Entry entry) {
        if (entry.cancelled()) return;

        SourceResult<MatchDetail> result;
        long start = System.nanoTime();
        try {
            result = loader == null ? null : loader.load(entry.match, entry.includeStats);
        } catch (Exception e) {
            result = SourceResult.fromException(e, start);
        }
        if (result == null) {
            long now = System.currentTimeMillis();
            result = SourceResult.error(SourceState.ERROR, "Sin resultado",
                    now, elapsedMs(start));
        }

        if (entry.cancelled()) return;
        persist(result);

        List<Handle> handles;
        synchronized (lock) {
            if (entry.done || closed || !isCurrent(entry)) {
                entry.done = true;
                if (entry.key != null && inFlight.get(entry.key) == entry) {
                    inFlight.remove(entry.key);
                    keyGeneration.remove(entry.key);
                }
                return;
            }

            entry.done = true;
            if (entry.key != null && inFlight.get(entry.key) == entry) {
                inFlight.remove(entry.key);
                keyGeneration.remove(entry.key);
            }
            handles = new ArrayList<>(entry.handles);
            entry.handles.clear();
        }

        for (Handle handle : handles) {
            synchronized (lock) {
                if (handle.cancelled || handle.completed) continue;
                handle.completed = true;
            }
            callbackOutsideLock(handle.callback, result);
        }
    }

    private void persist(SourceResult<MatchDetail> result) {
        if (cache == null || result == null || !result.isOk()) return;

        MatchDetail detail = result.data;
        if (detail == null || !detail.isFinished()) return;

        long fetchedAt = detail.fetchedAt > 0 ? detail.fetchedAt : result.fetchedAt;
        if (fetchedAt <= 0) fetchedAt = System.currentTimeMillis();
        cache.save(detail.withFetch(fetchedAt, false));
    }

    private boolean isCurrent(Entry entry) {
        if (entry.key == null) return true;
        Long latest = keyGeneration.get(entry.key);
        return latest != null && latest == entry.token
                && inFlight.get(entry.key) == entry;
    }

    private void cancel(Handle handle) {
        if (handle == null) return;

        synchronized (lock) {
            if (handle.cancelled || handle.completed || handle.entry.done) {
                handle.cancelled = true;
                return;
            }
            handle.cancelled = true;
            handle.entry.handles.remove(handle);

            if (!handle.entry.handles.isEmpty()) return;

            Entry entry = handle.entry;
            entry.done = true;
            if (entry.key != null && inFlight.get(entry.key) == entry) {
                inFlight.remove(entry.key);
                keyGeneration.remove(entry.key);
            }
            if (entry.future != null) entry.future.cancel(true);
        }
    }

    private static void callbackOutsideLock(Callback callback,
                                            SourceResult<MatchDetail> result) {
        if (callback == null) return;
        try {
            callback.onResult(result);
        } catch (RuntimeException ignored) {
            // Un consumidor que falla no debe romper el executor ni otros callbacks.
        }
    }

    private static String key(Match match) {
        if (match == null || match.sourceId == null
                || match.sourceId.trim().isEmpty() || match.id <= 0) {
            return null;
        }
        return match.sourceId + ":" + match.id;
    }

    private static long elapsedMs(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private final class Handle implements Request {
        final Entry entry;
        final Callback callback;
        volatile boolean cancelled;
        boolean completed;

        Handle(Entry entry, Callback callback) {
            this.entry = entry;
            this.callback = callback;
        }

        @Override
        public void cancel() {
            MatchDetailRepository.this.cancel(this);
        }
    }

    private final class Entry {
        final String key;
        final long token;
        final Match match;
        final boolean includeStats;
        final List<Handle> handles = new ArrayList<>();
        Future<?> future;
        boolean done;

        Entry(String key, long token, Match match, boolean includeStats) {
            this.key = key;
            this.token = token;
            this.match = match;
            this.includeStats = includeStats;
        }

        boolean cancelled() {
            synchronized (lock) {
                return done || closed;
            }
        }
    }
}
