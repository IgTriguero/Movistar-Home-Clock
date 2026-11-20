package com.itriguero.homeclock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MatchCacheTest {

    private File dir;
    private MatchCache cache;

    @Before
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("homeclock-cache").toFile();
        cache = new MatchCache(dir);
    }

    @After
    public void tearDown() {
        deleteRecursively(dir);
    }

    @Test
    public void savesAndLoadsSnapshotWithAllFields() {
        LocalDate date = LocalDate.of(2026, 11, 12);

        Match live = new Match();
        live.id = 123456L;
        live.sourceId = "365scores";
        live.sport = "Fútbol";
        live.competition = "LaLiga";
        live.startMillis = 1_760_000_000_000L;
        live.status = MatchStatus.LIVE;
        live.statusText = "78'";
        live.gameTimeDisplay = "78'";
        live.gameTime = 78;
        live.timeTbd = false;
        live.scoreKnown = true;
        live.homeId = 131L;
        live.awayId = 174L;
        live.homeName = "Real Madrid";
        live.awayName = "Rayo Vallecano";
        live.homeLogoUrl = "https://logos.example/rm.png";
        live.awayLogoUrl = "https://logos.example/rayo.png";
        live.homeScore = 2;
        live.awayScore = 1;
        live.trackedIsHome = true;
        live.trackedId = 131L;
        live.homeTracked = true;
        live.awayTracked = false;
        live.trackedName = "Real Madrid";
        live.opponentName = "Rayo Vallecano";

        Match postponed = new Match();
        postponed.id = -77L;
        postponed.sourceId = "rfef";
        postponed.sport = "Fútbol";
        postponed.competition = "2ª Federación Femenina";
        postponed.startMillis = 1_760_100_000_000L;
        postponed.status = MatchStatus.POSTPONED;
        postponed.timeTbd = true;
        postponed.scoreKnown = false;
        postponed.homeName = "As Celtas";
        postponed.awayName = "Rival";
        postponed.homeLogoUrl = "";
        postponed.awayLogoUrl = null;
        postponed.homeScore = -1;
        postponed.awayScore = -1;
        postponed.trackedIsHome = false;
        postponed.trackedId = -1L;
        postponed.homeTracked = false;
        postponed.awayTracked = true;
        postponed.trackedName = "As Celtas";
        postponed.opponentName = "Rival";

        Map<String, SourceStatus> sources = new LinkedHashMap<>();
        sources.put("365scores",
                new SourceStatus("365scores", SourceState.OK, null,
                        111L, 25L, true, 1));
        sources.put("rfef",
                new SourceStatus("rfef", SourceState.TIMEOUT, "Timeout", 222L, 12_000L));

        MatchesSnapshot snapshot = new MatchesSnapshot(
                date, Arrays.asList(live, postponed), sources, 999L, false);
        cache.save(snapshot, null);

        MatchesSnapshot loaded = cache.load(date);
        assertNotNull(loaded);
        assertEquals(date, loaded.date);
        assertEquals(999L, loaded.fetchedAt);
        assertFalse(loaded.stale);
        assertEquals(2, loaded.matches.size());
        assertEquals(2, loaded.sources.size());

        Match loadedLive = loaded.matches.get(0);
        assertEquals(123456L, loadedLive.id);
        assertEquals("365scores", loadedLive.sourceId);
        assertEquals("Fútbol", loadedLive.sport);
        assertEquals("LaLiga", loadedLive.competition);
        assertEquals(1_760_000_000_000L, loadedLive.startMillis);
        assertEquals(MatchStatus.LIVE, loadedLive.status);
        assertEquals("78'", loadedLive.statusText);
        assertEquals("78'", loadedLive.gameTimeDisplay);
        assertEquals(78.0, loadedLive.gameTime, 0.0);
        assertFalse(loadedLive.timeTbd);
        assertTrue(loadedLive.scoreKnown);
        assertEquals(131L, loadedLive.homeId);
        assertEquals(174L, loadedLive.awayId);
        assertEquals("Real Madrid", loadedLive.homeName);
        assertEquals("Rayo Vallecano", loadedLive.awayName);
        assertEquals("https://logos.example/rm.png", loadedLive.homeLogoUrl);
        assertEquals("https://logos.example/rayo.png", loadedLive.awayLogoUrl);
        assertEquals(2.0, loadedLive.homeScore, 0.0);
        assertEquals(1.0, loadedLive.awayScore, 0.0);
        assertTrue(loadedLive.trackedIsHome);
        assertEquals(131L, loadedLive.trackedId);
        assertTrue(loadedLive.homeTracked);
        assertFalse(loadedLive.awayTracked);
        assertEquals("Real Madrid", loadedLive.trackedName);
        assertEquals("Rayo Vallecano", loadedLive.opponentName);

        Match loadedPostponed = loaded.matches.get(1);
        assertEquals(-77L, loadedPostponed.id);
        assertEquals(MatchStatus.POSTPONED, loadedPostponed.status);
        assertFalse(loadedPostponed.scoreKnown);
        assertTrue(loadedPostponed.timeTbd);
        assertEquals(-1.0, loadedPostponed.homeScore, 0.0);
        assertEquals(-1.0, loadedPostponed.awayScore, 0.0);
        assertEquals(-1L, loadedPostponed.trackedId);
        assertFalse(loadedPostponed.homeTracked);
        assertTrue(loadedPostponed.awayTracked);
        assertNull(loadedPostponed.homeLogoUrl);
        assertNull(loadedPostponed.awayLogoUrl);

        SourceStatus liveStatus = loaded.sources.get("365scores");
        assertNotNull(liveStatus);
        assertEquals(SourceState.OK, liveStatus.state);
        assertNull(liveStatus.error);
        assertEquals(111L, liveStatus.fetchedAt);
        assertEquals(25L, liveStatus.durationMs);
        assertTrue(liveStatus.fromCache);
        assertEquals(1, liveStatus.matchCount);

        SourceStatus rfefStatus = loaded.sources.get("rfef");
        assertNotNull(rfefStatus);
        assertEquals(SourceState.TIMEOUT, rfefStatus.state);
        assertEquals("Timeout", rfefStatus.error);
        assertEquals(222L, rfefStatus.fetchedAt);
        assertEquals(12_000L, rfefStatus.durationMs);
        assertFalse(rfefStatus.fromCache);
        assertEquals(0, rfefStatus.matchCount);
    }

    @Test
    public void loadsCacheWithoutNewTrackingFieldsWithDefaults() throws Exception {
        LocalDate date = LocalDate.of(2026, 11, 12);
        File file = new File(dir, "matches_" + date + ".json");
        String json = "{\"version\":2,\"date\":\"2026-11-12\",\"fetchedAt\":99,"
                + "\"matches\":[{\"id\":5,\"trackedIsHome\":true}],"
                + "\"sources\":[{\"sourceId\":\"365scores\","
                + "\"state\":\"OK\",\"fetchedAt\":1,\"durationMs\":2}]}";
        Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));

        MatchesSnapshot loaded = cache.load(date);

        assertNotNull(loaded);
        SourceStatus status = loaded.sources.get("365scores");
        assertNotNull(status);
        assertFalse(status.fromCache);
        assertEquals(0, status.matchCount);

        Match match = loaded.matches.get(0);
        assertTrue(match.trackedIsHome);
        assertEquals(0L, match.trackedId);
        assertFalse(match.homeTracked);
        assertFalse(match.awayTracked);
    }

    @Test
    public void signatureMustMatchWhenBothAreNonEmpty() {
        LocalDate date = LocalDate.of(2026, 11, 12);
        cache.save(snapshot(date), "a=b");

        assertNotNull(cache.load(date, "a=b"));
        assertNull(cache.load(date, "other"));
        // Sin firma pedida se sigue sirviendo la caché (Ajustes, tests).
        assertNotNull(cache.load(date));
    }

    @Test
    public void emptyStoredSignatureIsAcceptedForAnyRequest() {
        LocalDate date = LocalDate.of(2026, 11, 12);
        cache.save(snapshot(date), null);

        assertNotNull(cache.load(date, "cualquier-firma"));
    }

    @Test
    public void loadReturnsNullWhenFileDoesNotExist() {
        assertNull(cache.load(LocalDate.of(2020, 1, 1)));
    }

    @Test
    public void loadReturnsNullWhenVersionIsUnknown() throws Exception {
        LocalDate date = LocalDate.of(2026, 11, 12);
        File file = new File(dir, "matches_" + date + ".json");
        Files.write(file.toPath(),
                "{\"version\":99,\"matches\":[]}".getBytes(StandardCharsets.UTF_8));

        assertNull(cache.load(date));
    }

    @Test
    public void loadReturnsNullWhenJsonIsBroken() throws Exception {
        LocalDate date = LocalDate.of(2026, 11, 12);
        File file = new File(dir, "matches_" + date + ".json");
        Files.write(file.toPath(), "{not-json".getBytes(StandardCharsets.UTF_8));

        assertNull(cache.load(date));
    }

    private static MatchesSnapshot snapshot(LocalDate date) {
        Match match = new Match();
        match.id = 1L;
        match.sourceId = "365scores";
        match.sport = "Fútbol";
        match.status = MatchStatus.SCHEDULED;
        return new MatchesSnapshot(date, Collections.singletonList(match),
                Collections.emptyMap(), 123L, false);
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
