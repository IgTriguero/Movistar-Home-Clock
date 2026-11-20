package com.itriguero.homeclock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MatchDetailRepositoryTest {

    private File dir;
    private MatchDetailCache cache;
    private ExecutorService executor;
    private MatchDetailRepository repository;

    @Before
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("homeclock-detail-repo").toFile();
        cache = new MatchDetailCache(dir);
        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "detail-test");
            thread.setDaemon(true);
            return thread;
        });
    }

    @After
    public void tearDown() {
        if (repository != null) repository.close();
        executor.shutdownNow();
        deleteRecursively(dir);
    }

    @Test
    public void okResultInvokesCallbackOnExecutorThread() {
        Match match = match(1L, MatchStatus.LIVE);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> threadName = new AtomicReference<>();
        AtomicReference<SourceResult<MatchDetail>> received = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        repository = repository((m, includeStats) -> {
            calls.incrementAndGet();
            assertTrue(includeStats);
            return SourceResult.ok(detail(m, true, ""), 777L, 25L);
        });

        repository.load(match, true, result -> {
            threadName.set(Thread.currentThread().getName());
            received.set(result);
            done.countDown();
        });

        await(done);
        assertEquals(1, calls.get());
        assertEquals("detail-test", threadName.get());
        assertNotNull(received.get());
        assertEquals(SourceState.OK, received.get().state);
        assertEquals(777L, received.get().fetchedAt);
        assertEquals(25L, received.get().durationMs);
        assertFalse(received.get().fromCache);
        assertTrue(received.get().data.hasStats);
    }

    @Test
    public void finishedMatchIsServedFromCacheWithoutNetwork() {
        Match match = match(2L, MatchStatus.FINISHED);
        cache.save(detail(match, true, "").withFetch(555L, false));

        AtomicInteger calls = new AtomicInteger();
        AtomicReference<SourceResult<MatchDetail>> received = new AtomicReference<>();
        repository = repository((m, includeStats) -> {
            calls.incrementAndGet();
            return SourceResult.error(SourceState.OFFLINE, "no debería llamarse", 1L, 1L);
        });

        repository.load(match, true, received::set);

        assertEquals(0, calls.get());
        assertNotNull(received.get());
        assertEquals(SourceState.OK, received.get().state);
        assertTrue(received.get().fromCache);
        assertEquals(555L, received.get().fetchedAt);
        assertEquals(0L, received.get().durationMs);
        assertTrue(received.get().data.fromCache);
        assertEquals(555L, received.get().data.fetchedAt);
    }

    @Test
    public void finishedResultIsStoredInCache() {
        Match match = match(3L, MatchStatus.FINISHED);
        CountDownLatch done = new CountDownLatch(1);

        repository = repository((m, includeStats) ->
                SourceResult.ok(detail(m, false, ""), 999L, 12L));

        repository.load(match, true, result -> done.countDown());
        await(done);

        MatchDetail cached = cache.load(match);
        assertNotNull(cached);
        assertEquals(999L, cached.fetchedAt);
        assertEquals(2, cached.homeScore);
        assertEquals(1, cached.awayScore);
    }

    @Test
    public void liveResultIsNotStoredInCache() {
        Match match = match(4L, MatchStatus.LIVE);
        CountDownLatch done = new CountDownLatch(1);

        repository = repository((m, includeStats) ->
                SourceResult.ok(detail(m, false, ""), 10L, 5L));

        repository.load(match, false, result -> done.countDown());
        await(done);

        assertNull(cache.load(match));
    }

    @Test
    public void errorResultIsNotStoredInCache() {
        Match match = match(5L, MatchStatus.FINISHED);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<SourceResult<MatchDetail>> received = new AtomicReference<>();

        repository = repository((m, includeStats) ->
                SourceResult.error(SourceState.TIMEOUT, "Timeout", 50L, 8_000L));

        repository.load(match, true, result -> {
            received.set(result);
            done.countDown();
        });
        await(done);

        assertNull(cache.load(match));
        assertEquals(SourceState.TIMEOUT, received.get().state);
        assertNull(received.get().data);
    }

    @Test
    public void partialStatsResultStillReachesCallbackAndCache() {
        Match match = match(6L, MatchStatus.FINISHED);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<SourceResult<MatchDetail>> received = new AtomicReference<>();

        repository = repository((m, includeStats) ->
                SourceResult.ok(detail(m, false, "Sin estadísticas"), 100L, 30L));

        repository.load(match, true, result -> {
            received.set(result);
            done.countDown();
        });
        await(done);

        assertEquals(SourceState.OK, received.get().state);
        assertFalse(received.get().data.hasStats);
        assertEquals("Sin estadísticas", received.get().data.statsError);
        assertNotNull(cache.load(match));
    }

    @Test
    public void cancelPreventsCallback() throws Exception {
        Match match = match(7L, MatchStatus.LIVE);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch callback = new CountDownLatch(1);

        repository = repository((m, includeStats) -> {
            started.countDown();
            awaitQuietly(release);
            return SourceResult.ok(detail(m, false, ""), 1L, 1L);
        });

        MatchDetailRepository.Request request =
                repository.load(match, false, result -> callback.countDown());
        assertTrue(started.await(2, TimeUnit.SECONDS));

        request.cancel();
        release.countDown();

        assertFalse("No debe haber callback tras cancelar",
                callback.await(300, TimeUnit.MILLISECONDS));
    }

    @Test
    public void deduplicatesRequestsForTheSameGame() throws Exception {
        Match match = match(8L, MatchStatus.LIVE);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch callbacks = new CountDownLatch(2);
        AtomicInteger calls = new AtomicInteger();
        List<SourceResult<MatchDetail>> results = new CopyOnWriteArrayList<>();

        repository = repository((m, includeStats) -> {
            calls.incrementAndGet();
            started.countDown();
            awaitQuietly(release);
            return SourceResult.ok(detail(m, false, ""), 10L, 5L);
        });

        MatchDetailRepository.Request first =
                repository.load(match, false, result -> {
                    results.add(result);
                    callbacks.countDown();
                });
        assertTrue(started.await(2, TimeUnit.SECONDS));
        MatchDetailRepository.Request second =
                repository.load(match, false, result -> {
                    results.add(result);
                    callbacks.countDown();
                });

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(1, calls.get());

        release.countDown();
        assertTrue(callbacks.await(2, TimeUnit.SECONDS));
        assertEquals(1, calls.get());
        assertEquals(2, results.size());
        assertEquals(SourceState.OK, results.get(0).state);
        assertEquals(SourceState.OK, results.get(1).state);
    }

    @Test
    public void slowLoaderDoesNotBlockCaller() throws Exception {
        Match match = match(9L, MatchStatus.LIVE);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch callback = new CountDownLatch(1);

        repository = repository((m, includeStats) -> {
            awaitQuietly(release);
            return SourceResult.ok(detail(m, false, ""), 10L, 5L);
        });

        repository.load(match, false, result -> callback.countDown());

        assertFalse("El callback debe ser asíncrono",
                callback.await(150, TimeUnit.MILLISECONDS));
        release.countDown();
        assertTrue(callback.await(2, TimeUnit.SECONDS));
    }

    @Test
    public void cancelOneOfTwoJoinedCallbacksKeepsTheOtherAlive() throws Exception {
        Match match = match(10L, MatchStatus.LIVE);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch survivor = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();

        repository = repository((m, includeStats) -> {
            calls.incrementAndGet();
            started.countDown();
            awaitQuietly(release);
            return SourceResult.ok(detail(m, false, ""), 10L, 5L);
        });

        MatchDetailRepository.Request first =
                repository.load(match, false, result -> cancelled.countDown());
        assertTrue(started.await(2, TimeUnit.SECONDS));
        repository.load(match, false, result -> survivor.countDown());

        first.cancel();
        release.countDown();

        assertTrue(survivor.await(2, TimeUnit.SECONDS));
        assertFalse(cancelled.await(200, TimeUnit.MILLISECONDS));
        assertEquals(1, calls.get());
    }

    @Test
    public void closeStopsCallbacks() throws Exception {
        Match match = match(11L, MatchStatus.LIVE);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch callback = new CountDownLatch(1);

        repository = repository((m, includeStats) -> {
            started.countDown();
            awaitQuietly(release);
            return SourceResult.ok(detail(m, false, ""), 10L, 5L);
        });

        repository.load(match, false, result -> callback.countDown());
        assertTrue(started.await(2, TimeUnit.SECONDS));

        repository.close();
        release.countDown();

        assertFalse(callback.await(300, TimeUnit.MILLISECONDS));
    }

    private MatchDetailRepository repository(MatchDetailRepository.Loader loader) {
        return new MatchDetailRepository(loader, cache, executor);
    }

    private static Match match(long id, MatchStatus status) {
        Match match = new Match();
        match.id = id;
        match.sourceId = "365scores";
        match.sport = "Fútbol";
        match.competition = "España, LaLiga";
        match.startMillis = 1_760_000_000_000L;
        match.status = status;
        match.statusText = status == MatchStatus.FINISHED ? "Finalizado" : "";
        return match;
    }

    private static MatchDetail detail(Match match, boolean hasStats, String statsError) {
        return new MatchDetail(match.id, match.sourceId, match.competition, match.sport,
                match.startMillis, match.status, match.statusText, "",
                true, 2, 1, "Real Madrid", "Rayo Vallecano",
                131L, 174L, null, Collections.emptyList(),
                Collections.emptyList(), null, null, Collections.emptyList(),
                hasStats, statsError, Collections.emptyList(), 0L, false);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("Latch agotado");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    /** Variante para loaders: una cancelación interrumpe el hilo a propósito. */
    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
