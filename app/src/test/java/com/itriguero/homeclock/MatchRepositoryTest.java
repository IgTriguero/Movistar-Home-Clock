package com.itriguero.homeclock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MatchRepositoryTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 12);
    private static final long START = 1_760_000_000_000L;
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private File dir;
    private MatchCache cache;
    private ExecutorService executor;
    private MatchRepository repository;

    @Before
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("homeclock-repo").toFile();
        cache = new MatchCache(dir);
        executor = Executors.newFixedThreadPool(2);
    }

    @After
    public void tearDown() {
        if (repository != null) repository.close();
        executor.shutdownNow();
        deleteRecursively(dir);
    }

    @Test
    public void okSourcePlusErrorSourceKeepsMatchAndReportsError() {
        Match match = match(1L, "365scores", START,
                "Real Madrid", "Rayo Vallecano", MatchStatus.LIVE);

        repository = repository(
                new FakeSource("365scores",
                        SourceResult.ok(Collections.singletonList(match), 1000L, 15L)),
                new FakeSource("rfef",
                        SourceResult.error(SourceState.TIMEOUT, "Timeout", 2000L, 12_000L)));

        MatchesSnapshot snapshot = repository.fetchBlocking(DATE);

        assertEquals(1, snapshot.matches.size());
        assertEquals(1L, snapshot.matches.get(0).id);
        assertFalse(snapshot.stale);
        assertEquals(1, snapshot.errorCount());
        assertTrue(snapshot.hasErrors());
        assertEquals(SourceState.OK, snapshot.sources.get("365scores").state);
        assertEquals(SourceState.TIMEOUT, snapshot.sources.get("rfef").state);
        assertEquals("365scores: OK · rfef: TIMEOUT", snapshot.summary());
    }

    @Test
    public void allSourcesFailingWithCacheReturnsStaleCachedMatches() {
        Match cachedMatch = match(2L, "rfef", START,
                "As Celtas", "Rival", MatchStatus.FINISHED);
        Map<String, SourceStatus> cachedStatuses = new LinkedHashMap<>();
        cachedStatuses.put("365scores",
                new SourceStatus("365scores", SourceState.OK, null, 10L, 5L));
        cache.save(new MatchesSnapshot(DATE,
                Collections.singletonList(cachedMatch), cachedStatuses, 1234L, false));

        repository = repository(
                new FakeSource("365scores",
                        SourceResult.error(SourceState.OFFLINE, "Sin red", 3000L, 50L)),
                new FakeSource("rfef",
                        SourceResult.error(SourceState.HTTP_ERROR, "HTTP 503", 4000L, 80L)));

        MatchesSnapshot snapshot = repository.fetchBlocking(DATE);

        assertTrue(snapshot.stale);
        assertEquals(1, snapshot.matches.size());
        assertEquals(2L, snapshot.matches.get(0).id);
        assertEquals(1234L, snapshot.fetchedAt);
        assertEquals(2, snapshot.errorCount());
        assertTrue(snapshot.hasErrors());

        SourceStatus rfef = snapshot.sources.get("rfef");
        assertTrue(rfef.fromCache);
        assertEquals(1, rfef.matchCount);
        SourceStatus scores = snapshot.sources.get("365scores");
        assertFalse(scores.fromCache);
        assertEquals(0, scores.matchCount);
    }

    @Test
    public void emptySourceDoesNotOverwriteCacheWhenOtherSourcesFail() {
        Match cachedMatch = match(3L, "365scores", START,
                "Real Madrid", "Rayo Vallecano", MatchStatus.LIVE);
        Map<String, SourceStatus> cachedStatuses = new LinkedHashMap<>();
        cachedStatuses.put("365scores",
                new SourceStatus("365scores", SourceState.OK, null, 10L, 5L));
        cache.save(new MatchesSnapshot(DATE,
                Collections.singletonList(cachedMatch), cachedStatuses, 1234L, false));

        repository = repository(
                new FakeSource("365scores", SourceResult.empty(2000L, 5L)),
                new FakeSource("rfef",
                        SourceResult.error(SourceState.TIMEOUT, "Timeout", 3000L, 12_000L)));

        MatchesSnapshot snapshot = repository.fetchBlocking(DATE);

        assertTrue(snapshot.stale);
        assertEquals(1, snapshot.matches.size());
        assertEquals(3L, snapshot.matches.get(0).id);
        assertEquals(1, snapshot.errorCount());
        // La caché anterior no se ha pisado con un snapshot vacío.
        assertEquals(1, cache.load(DATE).matches.size());
    }

    @Test
    public void allSourcesFailingWithoutCacheReturnsEmptySnapshotWithErrors() {
        repository = repository(
                new FakeSource("365scores",
                        SourceResult.error(SourceState.TIMEOUT, "Timeout", 3000L, 12_000L)),
                new FakeSource("rfef",
                        SourceResult.error(SourceState.PARSE_ERROR, "JSON roto", 4000L, 10L)));

        MatchesSnapshot snapshot = repository.fetchBlocking(DATE);

        assertTrue(snapshot.matches.isEmpty());
        assertTrue(snapshot.hasErrors());
        assertEquals(2, snapshot.errorCount());
        assertFalse(snapshot.stale);
    }

    @Test
    public void duplicateMatchFromTwoSourcesIsMergedAndLiveWins() {
        Match live = match(10L, "365scores", START,
                "Real Madrid", "Rayo Vallecano", MatchStatus.LIVE);
        live.scoreKnown = true;
        live.homeScore = 1;
        live.awayScore = 0;

        // Misma pareja con local/visitante invertidos y escudos que faltan
        // en la fuente ganadora.
        Match scheduled = match(11L, "rfef", START,
                "Rayo Vallecano", "Real Madrid", MatchStatus.SCHEDULED);
        scheduled.homeLogoUrl = "https://logos.example/rayo.png";
        scheduled.awayLogoUrl = "https://logos.example/rm.png";

        repository = repository(
                new FakeSource("365scores",
                        SourceResult.ok(Collections.singletonList(live), 1000L, 5L)),
                new FakeSource("rfef",
                        SourceResult.ok(Collections.singletonList(scheduled), 1000L, 5L)));

        MatchesSnapshot snapshot = repository.fetchBlocking(DATE);

        assertEquals(1, snapshot.matches.size());
        Match merged = snapshot.matches.get(0);
        assertEquals(MatchStatus.LIVE, merged.status);
        assertEquals("365scores", merged.sourceId);
        assertEquals("Real Madrid", merged.homeName);
        assertEquals("https://logos.example/rm.png", merged.homeLogoUrl);
        assertEquals("https://logos.example/rayo.png", merged.awayLogoUrl);
        assertFalse(snapshot.stale);
    }

    @Test
    public void sameTeamsWithinTwoHoursAreMerged() {
        long start = at(18, 0);
        Match first = match(20L, "365scores", start,
                "Real Madrid", "Rayo Vallecano", MatchStatus.SCHEDULED);
        Match second = match(21L, "rfef", start + 90L * 60L * 1000L,
                "Real Madrid", "Rayo Vallecano", MatchStatus.SCHEDULED);

        repository = repository(
                new FakeSource("365scores",
                        SourceResult.ok(Collections.singletonList(first), 1L, 1L)),
                new FakeSource("rfef",
                        SourceResult.ok(Collections.singletonList(second), 1L, 1L)));

        MatchesSnapshot snapshot = repository.fetchBlocking(DATE);

        assertEquals(1, snapshot.matches.size());
    }

    @Test
    public void sameTeamsThreeHoursApartAreNotMerged() {
        long start = at(17, 0);
        Match first = match(22L, "365scores", start,
                "Real Madrid", "Rayo Vallecano", MatchStatus.SCHEDULED);
        Match second = match(23L, "rfef", start + 3L * 60L * 60L * 1000L,
                "Real Madrid", "Rayo Vallecano", MatchStatus.SCHEDULED);

        repository = repository(
                new FakeSource("365scores",
                        SourceResult.ok(Collections.singletonList(first), 1L, 1L)),
                new FakeSource("rfef",
                        SourceResult.ok(Collections.singletonList(second), 1L, 1L)));

        MatchesSnapshot snapshot = repository.fetchBlocking(DATE);

        assertEquals(2, snapshot.matches.size());
    }

    @Test
    public void tbdSameDayIsMergedWithTimedMatch() {
        long midnight = DATE.atStartOfDay(MADRID).toInstant().toEpochMilli();
        long kickoff = at(20, 0);
        Match timed = match(24L, "365scores", kickoff,
                "Real Madrid", "Rayo Vallecano", MatchStatus.SCHEDULED);
        Match tbd = match(25L, "rfef", midnight,
                "Rayo Vallecano", "Real Madrid", MatchStatus.SCHEDULED, true);

        repository = repository(
                new FakeSource("365scores",
                        SourceResult.ok(Collections.singletonList(timed), 1L, 1L)),
                new FakeSource("rfef",
                        SourceResult.ok(Collections.singletonList(tbd), 1L, 1L)));

        MatchesSnapshot snapshot = repository.fetchBlocking(DATE);

        assertEquals(1, snapshot.matches.size());
        Match merged = snapshot.matches.get(0);
        assertEquals(kickoff, merged.startMillis);
        assertFalse(merged.timeTbd);
    }

    @Test
    public void twoTbdMatchesOnSameDayAreMerged() {
        long midnight = DATE.atStartOfDay(MADRID).toInstant().toEpochMilli();
        Match first = match(37L, "365scores", midnight,
                "Real Madrid", "Rayo Vallecano", MatchStatus.SCHEDULED, true);
        Match second = match(38L, "rfef", midnight,
                "Rayo Vallecano", "Real Madrid", MatchStatus.SCHEDULED, true);

        repository = repository(
                new FakeSource("365scores",
                        SourceResult.ok(Collections.singletonList(first), 1L, 1L)),
                new FakeSource("rfef",
                        SourceResult.ok(Collections.singletonList(second), 1L, 1L)));

        MatchesSnapshot snapshot = repository.fetchBlocking(DATE);

        assertEquals(1, snapshot.matches.size());
    }

    @Test
    public void tbdMatchesOnDifferentDaysAreNotMerged() {
        long firstDay = DATE.atStartOfDay(MADRID).toInstant().toEpochMilli();
        long secondDay = DATE.plusDays(1).atStartOfDay(MADRID)
                .toInstant().toEpochMilli();
        Match first = match(35L, "365scores", firstDay,
                "Real Madrid", "Rayo Vallecano", MatchStatus.SCHEDULED, true);
        Match second = match(36L, "rfef", secondDay,
                "Real Madrid", "Rayo Vallecano", MatchStatus.SCHEDULED, true);

        repository = repository(
                new FakeSource("365scores",
                        SourceResult.ok(Collections.singletonList(first), 1L, 1L)),
                new FakeSource("rfef",
                        SourceResult.ok(Collections.singletonList(second), 1L, 1L)));

        MatchesSnapshot snapshot = repository.fetchBlocking(DATE);

        assertEquals(2, snapshot.matches.size());
    }

    @Test
    public void homeAndAwaySwappedAreMergedWithinWindow() {
        long start = at(18, 0);
        Match local = match(26L, "365scores", start,
                "Real Madrid", "Rayo Vallecano", MatchStatus.SCHEDULED);
        Match reversed = match(27L, "rfef", start + 60L * 60L * 1000L,
                "Rayo Vallecano", "Real Madrid", MatchStatus.SCHEDULED);

        repository = repository(
                new FakeSource("365scores",
                        SourceResult.ok(Collections.singletonList(local), 1L, 1L)),
                new FakeSource("rfef",
                        SourceResult.ok(Collections.singletonList(reversed), 1L, 1L)));

        MatchesSnapshot snapshot = repository.fetchBlocking(DATE);

        assertEquals(1, snapshot.matches.size());
    }

    @Test
    public void matchesWithoutNamesDoNotMergeDifferentIds() {
        long start = at(18, 0);
        Match first = match(28L, "365scores", start, "", "", MatchStatus.SCHEDULED);
        Match second = match(29L, "365scores", start, "", "", MatchStatus.SCHEDULED);

        repository = repository(new FakeSource("365scores",
                SourceResult.ok(Arrays.asList(first, second), 1L, 1L)));

        MatchesSnapshot snapshot = repository.fetchBlocking(DATE);

        assertEquals(2, snapshot.matches.size());
    }

    @Test
    public void sourceResultFromCacheIsExposedInStatus() {
        Match cached = match(30L, "rfef", at(18, 0),
                "As Celtas", "Rival", MatchStatus.SCHEDULED);

        repository = repository(new FakeSource("rfef",
                SourceResult.okFromCache(Collections.singletonList(cached), 1000L, 5L)));

        MatchesSnapshot snapshot = repository.fetchBlocking(DATE);

        SourceStatus status = snapshot.sources.get("rfef");
        assertTrue(status.fromCache);
        assertEquals(1, status.matchCount);
        assertFalse(snapshot.stale);
    }

    @Test
    public void failedSourceReusesCachedMatchesAndMarksThemFromCache() {
        Match cachedRfef = match(31L, "rfef", at(18, 0),
                "As Celtas", "Rival", MatchStatus.FINISHED);
        Map<String, SourceStatus> cachedStatuses = new LinkedHashMap<>();
        cachedStatuses.put("rfef",
                new SourceStatus("rfef", SourceState.OK, null, 10L, 5L));
        cache.save(new MatchesSnapshot(DATE,
                Collections.singletonList(cachedRfef), cachedStatuses, 1234L, false));

        Match live = match(32L, "365scores", at(20, 0),
                "Real Madrid", "Rayo Vallecano", MatchStatus.SCHEDULED);
        repository = repository(
                new FakeSource("365scores",
                        SourceResult.ok(Collections.singletonList(live), 1L, 1L)),
                new FakeSource("rfef",
                        SourceResult.error(SourceState.OFFLINE, "Sin red", 3000L, 50L)));

        MatchesSnapshot snapshot = repository.fetchBlocking(DATE);

        assertEquals(2, snapshot.matches.size());
        assertTrue(snapshot.stale);
        SourceStatus rfef = snapshot.sources.get("rfef");
        assertTrue(rfef.fromCache);
        assertEquals(1, rfef.matchCount);
        SourceStatus scores = snapshot.sources.get("365scores");
        assertFalse(scores.fromCache);
        assertEquals(1, scores.matchCount);
    }

    @Test
    public void cachedMatchWithinWindowIsNotDuplicatedWhenSourceFails() {
        long start = at(18, 0);
        Match cachedRfef = match(33L, "rfef", start + 90L * 60L * 1000L,
                "Real Madrid", "Rayo Vallecano", MatchStatus.SCHEDULED);
        Map<String, SourceStatus> cachedStatuses = new LinkedHashMap<>();
        cachedStatuses.put("rfef",
                new SourceStatus("rfef", SourceState.OK, null, 10L, 5L));
        cache.save(new MatchesSnapshot(DATE,
                Collections.singletonList(cachedRfef), cachedStatuses, 1234L, false));

        Match live = match(34L, "365scores", start,
                "Rayo Vallecano", "Real Madrid", MatchStatus.SCHEDULED);
        repository = repository(
                new FakeSource("365scores",
                        SourceResult.ok(Collections.singletonList(live), 1L, 1L)),
                new FakeSource("rfef",
                        SourceResult.error(SourceState.TIMEOUT, "Timeout", 3000L, 50L)));

        MatchesSnapshot snapshot = repository.fetchBlocking(DATE);

        assertEquals(1, snapshot.matches.size());
        assertEquals(0, snapshot.sources.get("rfef").matchCount);
    }

    private static long at(int hour, int minute) {
        return DATE.atTime(hour, minute).atZone(MADRID)
                .toInstant().toEpochMilli();
    }

    private MatchRepository repository(MatchSource... sources) {
        return new MatchRepository(
                Arrays.asList(sources), cache, executor, null);
    }


    private static Match match(long id, String sourceId, long start,
                               String home, String away, MatchStatus status) {
        return match(id, sourceId, start, home, away, status, false);
    }

    private static Match match(long id, String sourceId, long start,
                               String home, String away, MatchStatus status,
                               boolean timeTbd) {
        Match match = new Match();
        match.id = id;
        match.sourceId = sourceId;
        match.sport = "Fútbol";
        match.competition = "Test";
        match.startMillis = start;
        match.status = status;
        match.homeName = home;
        match.awayName = away;
        match.timeTbd = timeTbd;
        match.scoreKnown = false;
        return match;
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

    private static final class FakeSource implements MatchSource {

        private final String id;
        private final SourceResult<List<Match>> result;

        FakeSource(String id, SourceResult<List<Match>> result) {
            this.id = id;
            this.result = result;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public SourceResult<List<Match>> fetch(LocalDate date) {
            return result;
        }
    }
}
