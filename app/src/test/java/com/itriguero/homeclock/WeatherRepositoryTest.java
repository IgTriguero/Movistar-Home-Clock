package com.itriguero.homeclock;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WeatherRepositoryTest {

    private static final City A = city("A", 40.0, -3.0);
    private static final City B = city("B", 41.0, -4.0);
    private static final City C = city("C", 42.0, -5.0);
    private static final List<City> AB = List.of(A, B);

    @Test
    public void successReturnsFreshAndWritesCache() {
        FakeStore store = new FakeStore("");
        FakeSource source = new FakeSource(batch(5000L, A.key(), 11.7, B.key(), 12.3));
        WeatherRepository repository = new WeatherRepository(source, store);

        WeatherSnapshot snapshot = repository.refreshBlocking(AB);

        assertEquals(WeatherSnapshot.State.FRESH, snapshot.state);
        assertFalse(snapshot.partial);
        assertEquals("", snapshot.error);
        assertEquals(5000L, snapshot.fetchedAt);
        assertEquals(2, snapshot.byCity.size());
        assertEquals(11.7, snapshot.weatherFor(A.key()).temp, 0.00001);
        assertEquals(1, source.calls);
        assertEquals(1, store.writes);

        WeatherCache.Cache written = WeatherCache.decode(store.json);
        assertEquals(2, written.byCity.size());
        assertEquals(5000L, written.fetchedAt);
    }

    @Test
    public void partialRefreshKeepsOldEntryAndMarksPartial() {
        FakeStore store = new FakeStore(stored(2000L,
                A.key(), 5.0, 1000L,
                B.key(), 6.0, 2000L));
        FakeSource source = new FakeSource(batch(5000L, B.key(), 12.3));
        WeatherRepository repository = new WeatherRepository(source, store);

        WeatherSnapshot snapshot = repository.refreshBlocking(AB);

        assertEquals(WeatherSnapshot.State.FRESH, snapshot.state);
        assertTrue(snapshot.partial);
        assertEquals(5000L, snapshot.fetchedAt);
        assertEquals(1000L, snapshot.entryFor(A.key()).fetchedAt);
        assertEquals(5.0, snapshot.weatherFor(A.key()).temp, 0.00001);
        assertEquals(5000L, snapshot.entryFor(B.key()).fetchedAt);
        assertEquals(1, store.writes);

        WeatherCache.Cache written = WeatherCache.decode(store.json);
        assertEquals(1000L, written.entryFor(A.key()).fetchedAt);
        assertEquals(5000L, written.entryFor(B.key()).fetchedAt);
    }

    @Test
    public void failureWithCacheReturnsCachedAndDoesNotWrite() {
        String previous = stored(2000L,
                A.key(), 5.0, 1000L,
                B.key(), 6.0, 2000L);
        FakeStore store = new FakeStore(previous);
        FakeSource source = new FakeSource(failure(
                new WeatherClient.WeatherException(
                        WeatherClient.WeatherException.Kind.OFFLINE, "Sin red")));
        WeatherRepository repository = new WeatherRepository(source, store);

        WeatherSnapshot snapshot = repository.refreshBlocking(AB);

        assertEquals(WeatherSnapshot.State.CACHED, snapshot.state);
        assertEquals("offline", snapshot.error);
        assertFalse(snapshot.partial);
        assertEquals(2000L, snapshot.fetchedAt);
        assertEquals(2, snapshot.byCity.size());
        assertEquals(5.0, snapshot.weatherFor(A.key()).temp, 0.00001);
        assertEquals(0, store.writes);
        assertEquals(previous, store.json);
    }

    @Test
    public void failureWithoutCacheReturnsEmpty() {
        FakeStore store = new FakeStore("");
        FakeSource source = new FakeSource(failure(
                new WeatherClient.WeatherException(
                        WeatherClient.WeatherException.Kind.HTTP, "HTTP 503", 503)));
        WeatherRepository repository = new WeatherRepository(source, store);

        WeatherSnapshot snapshot = repository.refreshBlocking(AB);

        assertEquals(WeatherSnapshot.State.EMPTY, snapshot.state);
        assertEquals("http", snapshot.error);
        assertTrue(snapshot.isEmpty());
        assertEquals(0L, snapshot.fetchedAt);
        assertEquals(0, store.writes);
    }

    @Test
    public void allInvalidCitiesDoNotCallSource() {
        List<City> invalid = Arrays.asList(
                new City("", "", "", 40.0, -3.0, City.Source.MANUAL),
                new City("Fuera", "", "", 100.0, -3.0, City.Source.MANUAL));
        FakeSource source = new FakeSource(batch(5000L, A.key(), 11.7));
        FakeStore store = new FakeStore("");
        WeatherRepository repository = new WeatherRepository(source, store);

        WeatherSnapshot snapshot = repository.refreshBlocking(invalid);

        assertEquals(0, source.calls);
        assertEquals(WeatherSnapshot.State.EMPTY, snapshot.state);
        assertTrue(snapshot.isEmpty());
    }

    @Test
    public void invalidCitiesAndDuplicatesAreFilteredBeforeFetching() {
        City invalid = new City("", "", "", 40.0, -3.0, City.Source.MANUAL);
        List<City> input = Arrays.asList(A, invalid, null, A);
        FakeSource source = new FakeSource(batch(5000L, A.key(), 11.7));
        FakeStore store = new FakeStore("");
        WeatherRepository repository = new WeatherRepository(source, store);

        WeatherSnapshot snapshot = repository.refreshBlocking(input);

        assertEquals(1, source.calls);
        assertEquals(1, source.lastCities.size());
        assertEquals(A.key(), source.lastCities.get(0).key());
        assertEquals(WeatherSnapshot.State.FRESH, snapshot.state);
        assertFalse(snapshot.partial);
    }

    @Test
    public void emptyBatchWithCacheKeepsItAndDoesNotWrite() {
        String previous = stored(2000L,
                A.key(), 5.0, 1000L,
                B.key(), 6.0, 2000L);
        FakeStore store = new FakeStore(previous);
        FakeSource source = new FakeSource(batch(5000L));
        WeatherRepository repository = new WeatherRepository(source, store);

        WeatherSnapshot snapshot = repository.refreshBlocking(AB);

        assertEquals(WeatherSnapshot.State.CACHED, snapshot.state);
        assertEquals("invalid", snapshot.error);
        assertEquals(2, snapshot.byCity.size());
        assertEquals(2000L, snapshot.fetchedAt);
        assertEquals(0, store.writes);
        assertEquals(previous, store.json);
    }

    @Test
    public void emptyBatchWithoutCacheReturnsEmpty() {
        FakeStore store = new FakeStore("");
        FakeSource source = new FakeSource(batch(5000L));
        WeatherRepository repository = new WeatherRepository(source, store);

        WeatherSnapshot snapshot = repository.refreshBlocking(AB);

        assertEquals(WeatherSnapshot.State.EMPTY, snapshot.state);
        assertEquals("invalid", snapshot.error);
        assertEquals(0, store.writes);
    }

    @Test
    public void emptySnapshotHasNoFetchedAt() {
        FakeStore store = new FakeStore(stored(1000L,
                C.key(), 5.0, 1000L));
        WeatherRepository repository = new WeatherRepository(
                new FakeSource(batch(1000L)), store);

        WeatherSnapshot snapshot = repository.loadCached(List.of(A));

        assertEquals(WeatherSnapshot.State.EMPTY, snapshot.state);
        assertEquals(0L, snapshot.fetchedAt);
        assertTrue(snapshot.isEmpty());
    }

    @Test
    public void partialUsesBatchKeysInsteadOfTimestamps() {
        FakeStore store = new FakeStore(stored(5000L,
                A.key(), 5.0, 5000L,
                B.key(), 6.0, 5000L));
        // B llega con el mismo timestamp que la caché; A no llega.
        FakeSource source = new FakeSource(batch(5000L, B.key(), 12.3));
        WeatherRepository repository = new WeatherRepository(source, store);

        WeatherSnapshot snapshot = repository.refreshBlocking(AB);

        assertEquals(WeatherSnapshot.State.FRESH, snapshot.state);
        assertTrue(snapshot.partial);
        assertEquals(5000L, snapshot.entryFor(A.key()).fetchedAt);
        assertEquals(5000L, snapshot.entryFor(B.key()).fetchedAt);
    }

    @Test
    public void moreThanMaxCitiesIsTruncatedBeforeFetching() {
        List<City> many = new ArrayList<>();
        for (int i = 0; i < CityList.MAX_CITIES + 1; i++) {
            many.add(city("C" + i, 30.0 + i, -3.0 - i));
        }
        FakeSource source = new FakeSource(batch(5000L));
        WeatherRepository repository = new WeatherRepository(source, new FakeStore(""));

        repository.refreshBlocking(many);

        assertEquals(CityList.MAX_CITIES, source.lastCities.size());
    }

    @Test
    public void jsonExceptionIsClassifiedAsParse() {
        FakeSource source = new FakeSource(failure(
                new org.json.JSONException("json roto")));
        WeatherRepository repository = new WeatherRepository(source, new FakeStore(""));

        assertEquals("parse", repository.refreshBlocking(AB).error);
    }

    @Test
    public void nullSourceIsInvalid() {
        WeatherRepository repository = new WeatherRepository(null, new FakeStore(""));

        WeatherSnapshot snapshot = repository.refreshBlocking(AB);

        assertEquals(WeatherSnapshot.State.EMPTY, snapshot.state);
        assertEquals("invalid", snapshot.error);
    }

    @Test
    public void corruptCacheWithGoodFetchIsRecovered() {
        FakeStore store = new FakeStore("{esto no es json");
        FakeSource source = new FakeSource(batch(5000L, A.key(), 11.7, B.key(), 12.3));
        WeatherRepository repository = new WeatherRepository(source, store);

        WeatherSnapshot snapshot = repository.refreshBlocking(AB);

        assertEquals(WeatherSnapshot.State.FRESH, snapshot.state);
        assertEquals(2, snapshot.byCity.size());
        assertEquals(1, store.writes);
        assertEquals(2, WeatherCache.decode(store.json).byCity.size());
    }

    @Test
    public void errorKindsAreClassified() {
        assertClassified(WeatherClient.WeatherException.Kind.OFFLINE, null, "offline");
        assertClassified(WeatherClient.WeatherException.Kind.HTTP, 503, "http");
        assertClassified(WeatherClient.WeatherException.Kind.PARSE, null, "parse");
        assertClassified(WeatherClient.WeatherException.Kind.INVALID, null, "invalid");
    }

    @Test
    public void genericErrorsAreUnknown() {
        FakeStore store = new FakeStore("");
        FakeSource source = new FakeSource(failure(new RuntimeException("boom")));
        WeatherRepository repository = new WeatherRepository(source, store);

        assertEquals("unknown", repository.refreshBlocking(AB).error);
    }

    @Test
    public void illegalArgumentFromSourceIsClassifiedAsInvalid() {
        FakeStore store = new FakeStore("");
        FakeSource source = new FakeSource(failure(new IllegalArgumentException("mal")));
        WeatherRepository repository = new WeatherRepository(source, store);

        assertEquals("invalid", repository.refreshBlocking(AB).error);
    }

    @Test
    public void ioExceptionFromSourceIsClassifiedAsOffline() {
        FakeStore store = new FakeStore("");
        FakeSource source = new FakeSource(failure(new java.io.IOException("sin red")));
        WeatherRepository repository = new WeatherRepository(source, store);

        assertEquals("offline", repository.refreshBlocking(AB).error);
    }

    @Test
    public void loadCachedFiltersCitiesAndMarksPartial() {
        FakeStore store = new FakeStore(stored(1000L,
                A.key(), 5.0, 1000L,
                B.key(), 6.0, 1000L));
        WeatherRepository repository = new WeatherRepository(
                new FakeSource(batch(1000L)), store);

        WeatherSnapshot snapshot = repository.loadCached(List.of(A, C));

        assertEquals(WeatherSnapshot.State.CACHED, snapshot.state);
        assertEquals(1000L, snapshot.fetchedAt);
        assertEquals(1, snapshot.byCity.size());
        assertNotNull(snapshot.entryFor(A.key()));
        assertNull(snapshot.entryFor(C.key()));
        assertTrue(snapshot.partial);
    }

    @Test
    public void loadCachedWithEmptyStoreReturnsEmpty() {
        WeatherRepository repository = new WeatherRepository(
                new FakeSource(batch(1000L)), new FakeStore(""));

        WeatherSnapshot snapshot = repository.loadCached(AB);

        assertEquals(WeatherSnapshot.State.EMPTY, snapshot.state);
        assertTrue(snapshot.isEmpty());
    }

    private static void assertClassified(WeatherClient.WeatherException.Kind kind,
                                         Integer status, String expected) {
        WeatherClient.WeatherException error = status == null
                ? new WeatherClient.WeatherException(kind, "error")
                : new WeatherClient.WeatherException(kind, "error", status);
        WeatherRepository repository = new WeatherRepository(
                new FakeSource(failure(error)), new FakeStore(""));

        WeatherSnapshot snapshot = repository.refreshBlocking(AB);

        assertEquals(WeatherSnapshot.State.EMPTY, snapshot.state);
        assertEquals(expected, snapshot.error);
    }

    private static String stored(long rootFetchedAt, Object... pairs) {
        Map<String, WeatherCache.Entry> entries = new LinkedHashMap<>();
        for (int i = 0; i + 2 < pairs.length; i += 3) {
            String key = (String) pairs[i];
            double temp = (Double) pairs[i + 1];
            long fetchedAt = ((Number) pairs[i + 2]).longValue();
            entries.put(key, new WeatherCache.Entry(weather(temp), fetchedAt));
        }
        return WeatherCache.encode(
                new WeatherCache.Cache(rootFetchedAt, entries), entriesKeyList(entries));
    }

    private static List<City> entriesKeyList(Map<String, WeatherCache.Entry> entries) {
        List<City> cities = new ArrayList<>();
        for (String key : entries.keySet()) {
            if (key.equals(A.key())) cities.add(A);
            else if (key.equals(B.key())) cities.add(B);
            else if (key.equals(C.key())) cities.add(C);
        }
        return cities;
    }

    private static WeatherBatch batch(long fetchedAt, Object... pairs) {
        Map<String, Weather> byCity = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            byCity.put((String) pairs[i], weather((Double) pairs[i + 1]));
        }
        return new WeatherBatch(byCity, List.copyOf(byCity.keySet()), fetchedAt);
    }

    private static Weather weather(double temp) {
        return new Weather(temp, temp - 2.0, 10.0, 60, 0, 1, "2026-11-14T10:00");
    }

    private static City city(String name, double lat, double lon) {
        return new City(name, "", "España", lat, lon, City.Source.MANUAL);
    }

    private static Exception failure(Exception error) {
        return error;
    }

    private static final class FakeSource implements WeatherRepository.Source {

        private final WeatherBatch batch;
        private final Exception failure;
        int calls;
        List<City> lastCities = Collections.emptyList();

        FakeSource(WeatherBatch batch) {
            this(batch, null);
        }

        FakeSource(Exception failure) {
            this(null, failure);
        }

        private FakeSource(WeatherBatch batch, Exception failure) {
            this.batch = batch;
            this.failure = failure;
        }

        @Override
        public WeatherBatch fetch(List<City> cities) throws Exception {
            calls++;
            lastCities = new ArrayList<>(cities);
            if (failure != null) throw failure;
            return batch;
        }
    }

    private static final class FakeStore implements WeatherRepository.Store {

        String json;
        int writes;

        FakeStore(String json) {
            this.json = json;
        }

        @Override
        public String read() {
            return json;
        }

        @Override
        public void write(String value) {
            writes++;
            json = value;
        }
    }
}
