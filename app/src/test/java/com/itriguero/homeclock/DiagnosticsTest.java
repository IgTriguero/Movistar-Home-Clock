package com.itriguero.homeclock;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DiagnosticsTest {

    private static final long NOW = 1_760_000_000_000L;
    private static final long MINUTE = 60_000L;
    private static final LocalDate DAY = LocalDate.of(2026, 11, 14);

    // ------------------------------------------------------------------
    // Estado y etiquetas
    // ------------------------------------------------------------------

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

        diagnostics.recordMatches(matches, NOW, 42L, NOW);
        sources.clear();

        Diagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(42L, snapshot.matchesDurationMs);
        assertEquals(1, snapshot.matchCount);
        assertTrue(snapshot.stale);
        assertEquals(999L, snapshot.fetchedAt);
        assertTrue(snapshot.matchesAt > 0L);
        assertEquals(1, snapshot.sources.size());
        assertEquals(status, snapshot.sources.get("rfef"));
    }

    @Test
    public void recordMatchesStoresAttemptDateAndRevision() {
        Diagnostics diagnostics = new Diagnostics();

        diagnostics.recordMatches(
                matches(status("rfef", SourceState.OK, false)),
                NOW - 500L, 42L, NOW);

        Diagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(NOW, snapshot.matchesAt);
        assertEquals(NOW - 500L, snapshot.matchesAttemptAt);
        assertEquals(DAY.toString(), snapshot.matchesDate);
        assertEquals(42L, snapshot.matchesDurationMs);
        assertEquals(1L, snapshot.revision);
        assertEquals(1, snapshot.sourceHealth.size());
    }

    // ------------------------------------------------------------------
    // Salud de las fuentes de partidos
    // ------------------------------------------------------------------

    @Test
    public void recordMatchesSuccessClearsFailures() {
        Diagnostics diagnostics = new Diagnostics();

        diagnostics.recordMatches(
                matches(status("rfef", SourceState.TIMEOUT, false)),
                NOW, 5L, NOW);
        assertEquals(1, health(diagnostics, "rfef").consecutiveFailures);

        diagnostics.recordMatches(
                matches(status("rfef", SourceState.OK, false)),
                NOW + MINUTE, 5L, NOW + MINUTE);

        SourceHealth health = health(diagnostics, "rfef");
        assertEquals(SourceState.OK, health.state);
        assertEquals(0, health.consecutiveFailures);
        assertEquals(1, health.failures);
        assertEquals(2, health.attempts);
        assertEquals(NOW + MINUTE, health.lastOkAt);
        assertEquals(NOW, health.lastErrorAt);
        assertEquals(0L, health.downSince);
        assertFalse(health.isDown(NOW + MINUTE));
    }

    @Test
    public void recordMatchesKeepsHealthOfSourcesMissingFromSnapshot() {
        Diagnostics diagnostics = new Diagnostics();

        diagnostics.recordMatches(matches(
                        status("rfef", SourceState.OK, false),
                        status("celta", SourceState.TIMEOUT, false)),
                NOW, 5L, NOW);
        assertEquals(2, diagnostics.snapshot().sourceHealth.size());

        // Consulta parcial: solo llega rfef; celta conserva su salud.
        diagnostics.recordMatches(
                matches(status("rfef", SourceState.OK, false)),
                NOW + MINUTE, 5L, NOW + MINUTE);

        Diagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(2, snapshot.sourceHealth.size());
        assertEquals(NOW, snapshot.sourceHealth.get("celta").lastAttemptAt);
        assertEquals(1, snapshot.sourceHealth.get("celta").consecutiveFailures);
        assertEquals(NOW + MINUTE,
                snapshot.sourceHealth.get("rfef").lastAttemptAt);
    }

    @Test
    public void recordMatchesKeepsTheMostRecentSourceHealth() {
        Diagnostics diagnostics = new Diagnostics();

        // Cada fuente llega en un intento distinto para que su antigüedad difiera.
        for (int i = 0; i < 20; i++) {
            String id = "fuente" + i;
            Map<String, SourceStatus> one = new LinkedHashMap<>();
            one.put(id, status(id, SourceState.OK, false));
            long at = NOW + i;
            diagnostics.recordMatches(
                    new MatchesSnapshot(DAY, Collections.emptyList(), one, at, false),
                    at, 5L, at);
        }

        Diagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(DiagnosticsState.MAX_SOURCES, snapshot.sourceHealth.size());
        assertFalse(snapshot.sourceHealth.containsKey("fuente0"));
        assertFalse(snapshot.sourceHealth.containsKey("fuente3"));
        assertTrue(snapshot.sourceHealth.containsKey("fuente4"));
        assertTrue(snapshot.sourceHealth.containsKey("fuente19"));
    }

    // ------------------------------------------------------------------
    // Tiempo
    // ------------------------------------------------------------------

    @Test
    public void recordWeatherStoresFieldsAndSuccessHealth() {
        Diagnostics diagnostics = new Diagnostics();

        diagnostics.recordWeather(fresh(), NOW - 200L, 33L, NOW);

        Diagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(NOW, snapshot.weatherAt);
        assertEquals("FRESH", snapshot.weatherState);
        assertTrue(snapshot.weatherPartial);
        assertEquals("", snapshot.weatherError);
        assertEquals(1, snapshot.weatherCityCount);
        assertEquals(NOW - 200L, snapshot.weatherAttemptAt);
        assertEquals(33L, snapshot.weatherDurationMs);
        assertEquals(NOW, snapshot.weatherLastOkAt);
        assertEquals(0, snapshot.weatherConsecutiveFailures);
        assertEquals(0L, snapshot.weatherDownSince);
        assertEquals(1L, snapshot.revision);
    }

    @Test
    public void recordWeatherFreshResetsFailuresAndDown() {
        Diagnostics diagnostics = new Diagnostics();

        diagnostics.recordWeather(cached(), NOW, 5L, NOW);
        diagnostics.recordWeather(empty(), NOW, 5L, NOW + MINUTE);
        diagnostics.recordWeather(cached(), NOW, 5L, NOW + 2L * MINUTE);
        assertTrue(diagnostics.snapshot().weatherDownSince > 0L);

        diagnostics.recordWeather(fresh(), NOW + 3L * MINUTE, 5L,
                NOW + 3L * MINUTE);

        Diagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals("FRESH", snapshot.weatherState);
        assertEquals(0, snapshot.weatherConsecutiveFailures);
        assertEquals(0L, snapshot.weatherDownSince);
        assertEquals(NOW + 3L * MINUTE, snapshot.weatherLastOkAt);
        assertEquals(NOW + 2L * MINUTE, snapshot.weatherLastErrorAt);
    }

    @Test
    public void recordWeatherCachedAndEmptyCountAsFailures() {
        Diagnostics diagnostics = new Diagnostics();

        diagnostics.recordWeather(cached(), NOW, 5L, NOW);
        assertEquals(1, diagnostics.snapshot().weatherConsecutiveFailures);
        assertEquals("CACHED", diagnostics.snapshot().weatherState);
        assertEquals(NOW, diagnostics.snapshot().weatherLastErrorAt);

        diagnostics.recordWeather(empty(), NOW, 5L, NOW + MINUTE);
        Diagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(2, snapshot.weatherConsecutiveFailures);
        assertEquals("EMPTY", snapshot.weatherState);
        assertEquals(0, snapshot.weatherCityCount);
        assertEquals(0L, snapshot.weatherAt);
        assertEquals(0L, snapshot.weatherLastOkAt);
        assertEquals(NOW + MINUTE, snapshot.weatherLastErrorAt);
        assertEquals(0L, snapshot.weatherDownSince);
    }

    @Test
    public void recordWeatherMarksDownAfterThreeFailuresAndFifteenMinutes() {
        Diagnostics diagnostics = new Diagnostics();
        long okAt = NOW;

        diagnostics.recordWeather(fresh(), okAt, 5L, okAt);
        diagnostics.recordWeather(cached(), okAt, 5L, okAt + MINUTE);
        diagnostics.recordWeather(empty(), okAt, 5L, okAt + 2L * MINUTE);
        diagnostics.recordWeather(cached(), okAt, 5L, okAt + 14L * MINUTE);
        assertEquals(0L, diagnostics.snapshot().weatherDownSince);

        diagnostics.recordWeather(cached(), okAt, 5L, okAt + 15L * MINUTE);

        Diagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(4, snapshot.weatherConsecutiveFailures);
        assertEquals(okAt + 15L * MINUTE, snapshot.weatherDownSince);
    }

    @Test
    public void recordWeatherWithErrorIsFailureEvenWhenFresh() {
        Diagnostics diagnostics = new Diagnostics();

        diagnostics.recordWeather(
                weather(WeatherSnapshot.State.FRESH, false, "parse"),
                NOW, 5L, NOW);

        Diagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals("FRESH", snapshot.weatherState);
        assertEquals("parse", snapshot.weatherError);
        assertEquals(1, snapshot.weatherConsecutiveFailures);
        assertEquals(0L, snapshot.weatherLastOkAt);
        assertEquals(NOW, snapshot.weatherLastErrorAt);
    }

    // ------------------------------------------------------------------
    // Listener y copias inmutables
    // ------------------------------------------------------------------

    @Test
    public void listenerReceivesSnapshotsAndClearStopsNotifications() {
        Diagnostics diagnostics = new Diagnostics();
        List<Diagnostics.Snapshot> received = new ArrayList<>();
        Diagnostics.Listener listener = received::add;

        diagnostics.setListener(listener);
        diagnostics.recordMatches(
                matches(status("rfef", SourceState.OK, false)),
                NOW, 5L, NOW);
        diagnostics.recordWeather(fresh(), NOW, 10L, NOW);

        assertEquals(2, received.size());
        assertTrue(received.get(0).revision < received.get(1).revision);
        assertEquals(1, received.get(0).sourceHealth.size());
        assertEquals("FRESH", received.get(1).weatherState);
        assertEquals(2L, received.get(1).revision);

        diagnostics.clearListener(listener);
        diagnostics.recordWeather(fresh(), NOW, 10L, NOW);
        assertEquals(2, received.size());
    }

    @Test
    public void clearListenerIgnoresAnotherInstanceAndSetNullStops() {
        Diagnostics diagnostics = new Diagnostics();
        List<Diagnostics.Snapshot> received = new ArrayList<>();
        Diagnostics.Listener listener = received::add;
        Diagnostics.Listener other = snapshot -> { };

        diagnostics.setListener(listener);
        diagnostics.clearListener(other);
        diagnostics.recordWeather(fresh(), NOW, 1L, NOW);
        assertEquals(1, received.size());

        diagnostics.setListener(null);
        diagnostics.recordWeather(fresh(), NOW + 1L, 1L, NOW + 1L);
        assertEquals(1, received.size());
    }

    @Test
    public void listenerIsNotifiedOutsideTheLock() throws Exception {
        Diagnostics diagnostics = new Diagnostics();
        AtomicBoolean readFromOtherThread = new AtomicBoolean(false);
        AtomicReference<Thread> worker = new AtomicReference<>();

        diagnostics.setListener(snapshot -> {
            Thread thread = new Thread(() -> {
                // Si el aviso se hiciera con el lock tomado, esta lectura
                // quedaría bloqueada hasta que el listener terminase.
                diagnostics.snapshot();
                readFromOtherThread.set(true);
            });
            worker.set(thread);
            thread.start();
            try {
                thread.join(5_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        diagnostics.recordWeather(fresh(), NOW, 1L, NOW);

        assertTrue(readFromOtherThread.get());
    }

    @Test
    public void snapshotHealthMapIsCopiedAndImmutable() {
        Diagnostics diagnostics = new Diagnostics();
        diagnostics.recordMatches(
                matches(status("rfef", SourceState.TIMEOUT, false)),
                NOW, 5L, NOW);

        Diagnostics.Snapshot before = diagnostics.snapshot();
        assertEquals(1, before.sourceHealth.get("rfef").consecutiveFailures);

        diagnostics.recordMatches(
                matches(status("rfef", SourceState.TIMEOUT, false)),
                NOW + MINUTE, 5L, NOW + MINUTE);

        // La copia anterior no cambia y el mapa publicado es inmutable.
        assertEquals(1, before.sourceHealth.get("rfef").consecutiveFailures);
        assertEquals(2, diagnostics.snapshot()
                .sourceHealth.get("rfef").consecutiveFailures);
        assertThrows(UnsupportedOperationException.class,
                () -> before.sourceHealth.put("x", SourceHealth.initial("x")));
        assertThrows(UnsupportedOperationException.class,
                () -> before.sources.put("x",
                        status("x", SourceState.OK, false)));
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static SourceHealth health(Diagnostics diagnostics, String sourceId) {
        SourceHealth health = diagnostics.snapshot().sourceHealth.get(sourceId);
        if (health == null) throw new AssertionError("sin salud para " + sourceId);
        return health;
    }

    private static SourceStatus status(String id, SourceState state,
                                       boolean fromCache) {
        return new SourceStatus(id, state, state == SourceState.OK ? null : "fallo",
                0L, 12L, fromCache, 1);
    }

    private static MatchesSnapshot matches(SourceStatus... statuses) {
        Map<String, SourceStatus> sources = new LinkedHashMap<>();
        for (SourceStatus status : statuses) {
            sources.put(status.sourceId, status);
        }
        return new MatchesSnapshot(DAY, Collections.emptyList(), sources, NOW, false);
    }

    private static WeatherSnapshot fresh() {
        return weather(WeatherSnapshot.State.FRESH, true, "");
    }

    private static WeatherSnapshot cached() {
        return weather(WeatherSnapshot.State.CACHED, true, "offline");
    }

    private static WeatherSnapshot empty() {
        return new WeatherSnapshot(Collections.emptyMap(), 0L,
                WeatherSnapshot.State.EMPTY, false, "offline");
    }

    private static WeatherSnapshot weather(WeatherSnapshot.State state,
                                           boolean partial, String error) {
        Map<String, WeatherCache.Entry> byCity = new LinkedHashMap<>();
        Weather weather = new Weather(20.0, 19.0, 5.0, 50, 1, 1,
                "2026-11-14T10:00:00Z");
        byCity.put("madrid", new WeatherCache.Entry(weather, NOW - MINUTE));
        return new WeatherSnapshot(byCity, NOW, state, partial, error);
    }
}
