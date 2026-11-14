package com.itriguero.homeclock;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MatchDetailCacheTest {

    private File dir;
    private MatchDetailCache cache;

    @Before
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("homeclock-detail-cache").toFile();
        cache = new MatchDetailCache(dir);
    }

    @After
    public void tearDown() {
        deleteRecursively(dir);
    }

    @Test
    public void roundTripPreservesUiFields() throws Exception {
        MatchDetail detail = footballDetail(123_456L);
        cache.save(detail);

        MatchDetail loaded = cache.load(finishedMatch(4750793L));

        assertNotNull(loaded);
        assertEquals(4750793L, loaded.gameId);
        assertEquals("365scores", loaded.sourceId);
        assertEquals("España, LaLiga", loaded.competition);
        assertEquals("Fútbol", loaded.sport);
        assertEquals(detail.startMillis, loaded.startMillis);
        assertEquals(MatchStatus.FINISHED, loaded.status);
        assertEquals("Finalizado", loaded.statusText);
        assertTrue(loaded.scoreKnown);
        assertEquals(0, loaded.homeScore);
        assertEquals(3, loaded.awayScore);
        assertEquals("Valencia CF", loaded.homeName);
        assertEquals("Real Madrid", loaded.awayName);
        assertEquals(139L, loaded.homeId);
        assertEquals(131L, loaded.awayId);
        assertEquals(123_456L, loaded.fetchedAt);
        assertTrue(loaded.fromCache);
        assertTrue(loaded.isFinished());

        assertNotNull(loaded.venue);
        assertEquals("Estadio de Mestalla", loaded.venue.name);
        assertEquals(48600, loaded.venue.capacity);
        assertEquals(47914, loaded.venue.attendance);

        assertEquals(1, loaded.officials.size());
        assertEquals("Francisco Hernández", loaded.officials.get(0).name);
        assertEquals("Árbitro", loaded.officials.get(0).role);

        assertEquals(10, loaded.events.size());
        MatchEvent goal = eventWithOrder(loaded, 2);
        assertEquals(MatchEvent.Type.GOAL, goal.type);
        assertEquals(MatchEvent.Side.AWAY, goal.side);
        assertEquals("Federico Valverde", goal.playerName);
        assertEquals(8, goal.playerNumber);
        assertEquals("28'", goal.display);

        assertEquals(11, loaded.homeLineup.starters.size());
        assertEquals(7, loaded.homeLineup.substitutes.size());
        assertEquals(4, loaded.homeLineup.missing.size());
        assertEquals("5-3-2", loaded.homeLineup.formation);
        assertEquals("Javier Aguirre", loaded.homeLineup.coach);
        assertEquals("Stole Dimitrievski", loaded.homeLineup.starters.get(0).name);
        assertEquals(6.1, loaded.homeLineup.starters.get(0).rating, 0.001);

        assertTrue(loaded.hasStats);
        assertEquals(6, loaded.statCategories.size());
        StatRow possession = loaded.statCategories.get(0).rows.get(0);
        assertEquals("Posesión", possession.name);
        assertEquals("32%", possession.homeValue);
        assertEquals(0.32, possession.homePercent, 0.001);
        assertTrue(possession.major);

        assertEquals(2, loaded.tvNetworks.size());
        assertEquals("Movistar Plus+", loaded.tvNetworks.get(0));
        assertEquals("DAZN", loaded.tvNetworks.get(1));
    }

    @Test
    public void onlySavesAndLoadsFinishedMatches() throws Exception {
        MatchDetail scheduled = MatchDetailParser.parse(
                fixture("scores365_game_scheduled.json"), null, null);
        assertNotNull(scheduled);

        cache.save(scheduled);
        assertEquals(0, cache.size());
        assertNull(cache.load(scheduledMatch()));

        MatchDetail live = footballDetail(10L).withFetch(10L, false);
        MatchDetail liveDetail = new MatchDetail(
                live.gameId, live.sourceId, live.competition, live.sport,
                live.startMillis, MatchStatus.LIVE, "78'", "78'",
                true, 1, 1, live.homeName, live.awayName, live.homeId, live.awayId,
                live.venue, live.officials, live.events, live.homeLineup,
                live.awayLineup, Collections.emptyList(), false, "",
                live.tvNetworks, 10L, false);
        cache.save(liveDetail);
        assertEquals(0, cache.size());

        Match liveMatch = finishedMatch(4750793L);
        liveMatch.status = MatchStatus.LIVE;
        assertNull(cache.load(liveMatch));
    }

    @Test
    public void missingFileReturnsNull() {
        assertNull(cache.load(finishedMatch(999L)));
    }

    @Test
    public void brokenJsonIsDeleted() throws Exception {
        File file = new File(dir, "365scores_4750793.json");
        Files.write(file.toPath(), "{no-json".getBytes(StandardCharsets.UTF_8));

        assertNull(cache.load(finishedMatch(4750793L)));
        assertFalse(file.exists());
    }

    @Test
    public void unknownVersionIsDeleted() throws Exception {
        File file = new File(dir, "365scores_4750793.json");
        Files.write(file.toPath(), "{\"version\":99,\"final\":true,"
                .concat("\"sourceId\":\"365scores\",\"gameId\":4750793}")
                .getBytes(StandardCharsets.UTF_8));

        assertNull(cache.load(finishedMatch(4750793L)));
        assertFalse(file.exists());
    }

    @Test
    public void mismatchedKeyOrNonFinalPayloadIsDeleted() throws Exception {
        File wrongKey = new File(dir, "365scores_4750793.json");
        Files.write(wrongKey.toPath(), "{\"version\":1,\"final\":true,"
                .concat("\"sourceId\":\"otra\",\"gameId\":4750793}")
                .getBytes(StandardCharsets.UTF_8));
        assertNull(cache.load(finishedMatch(4750793L)));
        assertFalse(wrongKey.exists());

        cache.save(footballDetail(1L));
        File file = new File(dir, "365scores_4750793.json");
        String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        Files.write(file.toPath(),
                raw.replace("\"final\":true", "\"final\":false")
                        .getBytes(StandardCharsets.UTF_8));
        assertNull(cache.load(finishedMatch(4750793L)));
        assertFalse(file.exists());
    }

    @Test
    public void readingRefreshesLastModifiedForLru() throws Exception {
        cache.save(footballDetail(10L));
        File file = new File(dir, "365scores_4750793.json");
        assertTrue(file.setLastModified(1_000L));
        assertEquals(1_000L, file.lastModified());

        assertNotNull(cache.load(finishedMatch(4750793L)));
        assertTrue(file.lastModified() > 1_000L);
    }

    @Test
    public void prunesOldestFilesBeyondLimit() throws Exception {
        for (int i = 1; i <= MatchDetailCache.MAX_FILES; i++) {
            cache.save(smallDetail(i));
            File file = new File(dir, "365scores_" + i + ".json");
            assertTrue(file.isFile());
            assertTrue(file.setLastModified(1_000L + i * 1_000L));
        }
        assertEquals(MatchDetailCache.MAX_FILES, cache.size());

        cache.save(smallDetail(MatchDetailCache.MAX_FILES + 1));

        assertEquals(MatchDetailCache.MAX_FILES, cache.size());
        assertFalse(new File(dir, "365scores_1.json").exists());
        assertTrue(new File(dir, "365scores_2.json").exists());
        assertTrue(new File(dir, "365scores_61.json").exists());
    }

    @Test
    public void atomicWriteLeavesNoTemporaryFile() throws Exception {
        cache.save(footballDetail(10L));

        File[] files = dir.listFiles();
        assertNotNull(files);
        for (File file : files) {
            assertFalse("Quedó un temporal: " + file.getName(),
                    file.getName().contains(".tmp"));
        }
        assertTrue(new File(dir, "365scores_4750793.json").isFile());
    }

    @Test
    public void clearRemovesAllEntries() throws Exception {
        cache.save(footballDetail(10L));
        cache.save(smallDetail(20L));
        assertEquals(2, cache.size());

        cache.clear();
        assertEquals(0, cache.size());
    }

    private static MatchDetail footballDetail(long fetchedAt) throws Exception {
        MatchDetail detail = MatchDetailParser.parse(
                fixture("scores365_game_football.json"),
                fixture("scores365_stats_football.json"), null);
        assertNotNull(detail);
        return detail.withFetch(fetchedAt, false);
    }

    private static MatchDetail smallDetail(long gameId) {
        return new MatchDetail(gameId, "365scores", "LaLiga", "Fútbol",
                1_760_000_000_000L, MatchStatus.FINISHED, "Finalizado", "",
                true, 1, 0, "Local", "Visitante", 10L, 20L,
                null, Collections.emptyList(), Collections.emptyList(),
                null, null, Collections.emptyList(), false, "",
                Collections.emptyList(), 1_000L, false);
    }

    private static Match finishedMatch(long gameId) {
        Match match = new Match();
        match.id = gameId;
        match.sourceId = "365scores";
        match.sport = "Fútbol";
        match.status = MatchStatus.FINISHED;
        return match;
    }

    private static Match scheduledMatch() {
        Match match = finishedMatch(4900001L);
        match.status = MatchStatus.SCHEDULED;
        return match;
    }

    private static MatchEvent eventWithOrder(MatchDetail detail, int order) {
        for (MatchEvent event : detail.events) {
            if (event.order == order) return event;
        }
        throw new AssertionError("No hay evento con orden " + order);
    }

    private static JSONObject fixture(String name) throws Exception {
        try (InputStream input = MatchDetailCacheTest.class
                .getResourceAsStream("/fixtures/" + name)) {
            assertNotNull("Falta el fixture " + name, input);
            return new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
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
