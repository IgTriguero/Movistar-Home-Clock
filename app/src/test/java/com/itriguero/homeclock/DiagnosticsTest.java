package com.itriguero.homeclock;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DiagnosticsTest {

    private static final long NOW = 1_760_000_000_000L;

    @Test
    public void ageLabelHandlesMissingNowMinutesAndHours() {
        assertEquals("—", Diagnostics.ageLabel(0L, NOW));
        assertEquals("ahora", Diagnostics.ageLabel(NOW - 30_000L, NOW));
        assertEquals("hace 1 min", Diagnostics.ageLabel(NOW - 60_000L, NOW));
        assertEquals("hace 5 min", Diagnostics.ageLabel(NOW - 5L * 60_000L, NOW));
        assertEquals("hace 1 h", Diagnostics.ageLabel(NOW - 60L * 60_000L, NOW));
        assertEquals("hace 3 h", Diagnostics.ageLabel(NOW - 3L * 60L * 60_000L, NOW));
        assertEquals("ahora", Diagnostics.ageLabel(NOW + 60_000L, NOW));
    }

    @Test
    public void freshDiagnosticsHasEmptySnapshot() {
        Diagnostics.Snapshot snapshot = new Diagnostics().snapshot();

        assertEquals(0L, snapshot.matchesAt);
        assertEquals(0L, snapshot.matchesDurationMs);
        assertEquals(0L, snapshot.weatherAt);
        assertEquals(0L, snapshot.fetchedAt);
        assertEquals(0, snapshot.matchCount);
        assertFalse(snapshot.stale);
        assertTrue(snapshot.sources.isEmpty());
    }

    @Test
    public void snapshotCopiesLatestValuesAndSources() {
        Diagnostics diagnostics = new Diagnostics();
        Map<String, SourceStatus> sources = new LinkedHashMap<>();
        SourceStatus status = new SourceStatus(
                "rfef", SourceState.OK, null, 111L, 25L, false, 1);
        sources.put("rfef", status);

        Match match = new Match();
        match.sourceId = "rfef";
        MatchesSnapshot matches = new MatchesSnapshot(
                LocalDate.of(2026, 11, 12),
                Collections.singletonList(match), sources, 999L, true);

        diagnostics.recordMatches(matches, 42L);
        sources.clear();
        diagnostics.recordWeather(777L);

        Diagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(42L, snapshot.matchesDurationMs);
        assertEquals(1, snapshot.matchCount);
        assertTrue(snapshot.stale);
        assertEquals(999L, snapshot.fetchedAt);
        assertEquals(777L, snapshot.weatherAt);
        assertTrue(snapshot.matchesAt > 0L);
        assertEquals(1, snapshot.sources.size());
        assertEquals(status, snapshot.sources.get("rfef"));
    }
}
