package com.itriguero.homeclock;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WeatherCacheTest {

    private static final City A = city("A", 40.0, -3.0);
    private static final City B = city("B", 41.0, -4.0);
    private static final City C = city("C", 42.0, -5.0);
    private static final List<City> AB = List.of(A, B);

    @Test
    public void roundTripPreservesAllFields() {
        Map<String, WeatherCache.Entry> entries = new LinkedHashMap<>();
        entries.put(A.key(), new WeatherCache.Entry(
                new Weather(11.7, 9.1, 17.9, 91, 3, 1, "2026-11-14T10:00"), 1000L));
        entries.put(B.key(), new WeatherCache.Entry(
                new Weather(12.3, 10.8, 12.4, 78, 2, 0, "2026-11-14T10:05"), 900L));
        WeatherCache.Cache original = new WeatherCache.Cache(1000L, entries);

        String encoded = WeatherCache.encode(original, AB);
        WeatherCache.Cache decoded = WeatherCache.decode(encoded);

        assertEquals(1000L, decoded.fetchedAt);
        assertEquals(2, decoded.byCity.size());

        WeatherCache.Entry first = decoded.byCity.get(A.key());
        assertNotNull(first);
        assertEquals(11.7, first.weather.temp, 0.00001);
        assertEquals(9.1, first.weather.feels, 0.00001);
        assertEquals(17.9, first.weather.wind, 0.00001);
        assertEquals(91, first.weather.humidity);
        assertEquals(3, first.weather.weatherCode);
        assertEquals(1, first.weather.isDay);
        assertEquals("2026-11-14T10:00", first.weather.observedAt);
        assertEquals(1000L, first.fetchedAt);

        WeatherCache.Entry second = decoded.byCity.get(B.key());
        assertNotNull(second);
        assertEquals(0, second.weather.isDay);
        assertEquals("2026-11-14T10:05", second.weather.observedAt);
        assertEquals(900L, second.fetchedAt);
    }

    @Test
    public void decodeReturnsEmptyForMissingOrBrokenInput() {
        assertTrue(WeatherCache.decode(null).isEmpty());
        assertTrue(WeatherCache.decode("").isEmpty());
        assertTrue(WeatherCache.decode("   ").isEmpty());
        assertTrue(WeatherCache.decode("{esto no es json").isEmpty());
        assertTrue(WeatherCache.decode("[]").isEmpty());
        assertTrue(WeatherCache.decode("{\"version\":1}").isEmpty());
        assertTrue(WeatherCache.decode(
                "{\"version\":1,\"fetchedAt\":0,\"cities\":{}}").isEmpty());
    }

    @Test
    public void decodeRejectsUnknownVersion() {
        String raw = "{\"version\":2,\"fetchedAt\":1000,\"cities\":{"
                + "\"a\":{\"temp\":10,\"feels\":9,\"humidity\":50,\"wind\":5,"
                + "\"weatherCode\":0,\"isDay\":1,\"observedAt\":\"\",\"fetchedAt\":1000}}}";

        assertTrue(WeatherCache.decode(raw).isEmpty());
    }

    @Test
    public void decodeDiscardsInvalidEntriesButKeepsValidOnes() throws Exception {
        JSONObject cities = new JSONObject()
                .put("humidity", entryJson(10.0, 150, 0, 1000L))
                .put("noFetchedAt", entryJson(10.0, 50, 0, 0L))
                .put("badCode", entryJson(10.0, 50, 999, 1000L))
                .put("notFinite", entryJson("NaN", 50, 0, 1000L))
                .put("valid", entryJson(10.0, 50, 0, 1000L));
        String raw = new JSONObject()
                .put("version", 1)
                .put("fetchedAt", 1000L)
                .put("cities", cities)
                .toString();

        WeatherCache.Cache decoded = WeatherCache.decode(raw);

        assertEquals(1, decoded.byCity.size());
        assertNotNull(decoded.byCity.get("valid"));
        assertNull(decoded.byCity.get("humidity"));
        assertNull(decoded.byCity.get("noFetchedAt"));
        assertNull(decoded.byCity.get("badCode"));
        assertNull(decoded.byCity.get("notFinite"));
    }

    @Test
    public void encodePrunesCitiesThatAreNoLongerInTheList() {
        Map<String, WeatherCache.Entry> entries = new LinkedHashMap<>();
        entries.put(A.key(), entry(11.7, 1000L));
        entries.put(B.key(), entry(12.3, 1000L));
        entries.put(C.key(), entry(13.1, 1000L));

        String encoded = WeatherCache.encode(
                new WeatherCache.Cache(1000L, entries), AB);
        WeatherCache.Cache decoded = WeatherCache.decode(encoded);

        assertEquals(2, decoded.byCity.size());
        assertNotNull(decoded.byCity.get(A.key()));
        assertNotNull(decoded.byCity.get(B.key()));
        assertNull(decoded.byCity.get(C.key()));
    }

    @Test
    public void encodeReturnsEmptyWhenThereAreNoEntries() {
        assertEquals("", WeatherCache.encode(WeatherCache.Cache.empty(), AB));
        assertEquals("", WeatherCache.encode(
                new WeatherCache.Cache(1000L, Collections.emptyMap()), AB));
        assertEquals("", WeatherCache.encode(null, AB));
    }

    @Test
    public void encodeIsDeterministic() {
        Map<String, WeatherCache.Entry> entries = new LinkedHashMap<>();
        entries.put(B.key(), entry(12.3, 1000L));
        entries.put(A.key(), entry(11.7, 1000L));
        WeatherCache.Cache cache = new WeatherCache.Cache(1000L, entries);

        String first = WeatherCache.encode(cache, AB);
        String second = WeatherCache.encode(cache, AB);

        assertEquals(first, second);
    }

    @Test
    public void mergeReplacesEntriesPresentInTheBatch() {
        Map<String, WeatherCache.Entry> entries = new LinkedHashMap<>();
        entries.put(A.key(), entry(5.0, 1000L));
        entries.put(B.key(), entry(6.0, 1000L));
        WeatherCache.Cache previous = new WeatherCache.Cache(1000L, entries);

        WeatherCache.Cache merged = WeatherCache.merge(
                previous, batch(5000L, A.key(), 11.7, B.key(), 12.3), AB);

        assertEquals(5000L, merged.fetchedAt);
        assertEquals(5000L, merged.entryFor(A.key()).fetchedAt);
        assertEquals(11.7, merged.entryFor(A.key()).weather.temp, 0.00001);
        assertEquals(12.3, merged.entryFor(B.key()).weather.temp, 0.00001);
        assertEquals(5000L, merged.entryFor(B.key()).fetchedAt);
    }

    @Test
    public void mergeKeepsAbsentEntriesAndTheirOldTimestamp() {
        Map<String, WeatherCache.Entry> entries = new LinkedHashMap<>();
        entries.put(A.key(), entry(5.0, 1000L));
        entries.put(B.key(), entry(6.0, 2000L));
        WeatherCache.Cache previous = new WeatherCache.Cache(2000L, entries);

        WeatherCache.Cache merged = WeatherCache.merge(
                previous, batch(5000L, A.key(), 11.7), AB);

        assertEquals(5000L, merged.fetchedAt);
        assertEquals(11.7, merged.entryFor(A.key()).weather.temp, 0.00001);
        assertEquals(5000L, merged.entryFor(A.key()).fetchedAt);
        assertEquals(6.0, merged.entryFor(B.key()).weather.temp, 0.00001);
        assertEquals(2000L, merged.entryFor(B.key()).fetchedAt);
    }

    @Test
    public void mergeWithoutNewEntriesKeepsPreviousFetchedAt() {
        Map<String, WeatherCache.Entry> entries = new LinkedHashMap<>();
        entries.put(A.key(), entry(5.0, 1000L));
        WeatherCache.Cache previous = new WeatherCache.Cache(1000L, entries);

        WeatherCache.Cache merged = WeatherCache.merge(
                previous, new WeatherBatch(Collections.emptyMap(),
                        List.of(A.key(), B.key()), 5000L), AB);

        assertEquals(1000L, merged.fetchedAt);
        assertEquals(1000L, merged.entryFor(A.key()).fetchedAt);
        assertEquals(5.0, merged.entryFor(A.key()).weather.temp, 0.00001);
    }

    @Test
    public void mergePrunesCitiesThatLeftTheList() {
        Map<String, WeatherCache.Entry> entries = new LinkedHashMap<>();
        entries.put(A.key(), entry(5.0, 1000L));
        entries.put(B.key(), entry(6.0, 2000L));
        entries.put(C.key(), entry(7.0, 3000L));
        WeatherCache.Cache previous = new WeatherCache.Cache(3000L, entries);

        WeatherCache.Cache merged = WeatherCache.merge(
                previous, new WeatherBatch(Collections.emptyMap(), List.of(), 5000L), AB);

        assertEquals(2, merged.byCity.size());
        assertNull(merged.entryFor(C.key()));
        assertEquals(3000L, merged.fetchedAt);
    }

    @Test
    public void migratesValidLegacyCache() {
        long savedAt = 1_763_112_600_000L;
        String legacy = "{\"temp\":11.7,\"feels\":9.1,\"humidity\":91,"
                + "\"wind\":17.9,\"weatherCode\":3,\"isDay\":1,"
                + "\"updatedAt\":\"10:00\",\"savedAt\":" + savedAt + "}";

        WeatherCache.Cache cache = WeatherCache.migrateLegacy(legacy, A.key());

        assertFalse(cache.isEmpty());
        assertEquals(savedAt, cache.fetchedAt);
        WeatherCache.Entry entry = cache.entryFor(A.key());
        assertNotNull(entry);
        assertEquals(savedAt, entry.fetchedAt);
        assertEquals("", entry.weather.observedAt);
        assertEquals(11.7, entry.weather.temp, 0.00001);
        assertEquals(9.1, entry.weather.feels, 0.00001);
        assertEquals(17.9, entry.weather.wind, 0.00001);
        assertEquals(91, entry.weather.humidity);
        assertEquals(3, entry.weather.weatherCode);
        assertEquals(1, entry.weather.isDay);
    }

    @Test
    public void invalidLegacyYieldsEmptyCache() {
        long savedAt = 1_763_112_600_000L;
        assertTrue(WeatherCache.migrateLegacy(null, A.key()).isEmpty());
        assertTrue(WeatherCache.migrateLegacy("", A.key()).isEmpty());
        assertTrue(WeatherCache.migrateLegacy("{no json", A.key()).isEmpty());
        assertTrue(WeatherCache.migrateLegacy(
                "{\"temp\":11.7,\"feels\":9.1,\"humidity\":91,\"wind\":17.9,"
                        + "\"weatherCode\":3,\"isDay\":1,\"savedAt\":0}", A.key()).isEmpty());
        assertTrue(WeatherCache.migrateLegacy(
                "{\"temp\":11.7,\"feels\":9.1,\"humidity\":150,\"wind\":17.9,"
                        + "\"weatherCode\":3,\"isDay\":1,\"savedAt\":" + savedAt + "}",
                A.key()).isEmpty());
        assertTrue(WeatherCache.migrateLegacy(
                "{\"temp\":11.7,\"feels\":9.1,\"humidity\":91,\"wind\":17.9,"
                        + "\"weatherCode\":3,\"isDay\":1,\"savedAt\":" + savedAt + "}",
                "").isEmpty());
    }

    private static WeatherCache.Entry entry(double temp, long fetchedAt) {
        return new WeatherCache.Entry(
                new Weather(temp, temp - 2.0, 15.0, 80, 3, 1, "2026-11-14T10:00"),
                fetchedAt);
    }

    private static WeatherBatch batch(long fetchedAt, Object... pairs) {
        Map<String, Weather> byCity = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String key = (String) pairs[i];
            double temp = (Double) pairs[i + 1];
            byCity.put(key, new Weather(
                    temp, temp - 2.0, 15.0, 80, 3, 1, "2026-11-14T10:00"));
        }
        return new WeatherBatch(byCity, List.copyOf(byCity.keySet()), fetchedAt);
    }

    private static JSONObject entryJson(double temp, int humidity, int code, long fetchedAt)
            throws Exception {
        return new JSONObject()
                .put("temp", temp)
                .put("feels", temp - 2.0)
                .put("humidity", humidity)
                .put("wind", 15.0)
                .put("weatherCode", code)
                .put("isDay", 1)
                .put("observedAt", "")
                .put("fetchedAt", fetchedAt);
    }

    private static JSONObject entryJson(String temp, int humidity, int code, long fetchedAt)
            throws Exception {
        return new JSONObject()
                .put("temp", temp)
                .put("feels", 9.0)
                .put("humidity", humidity)
                .put("wind", 15.0)
                .put("weatherCode", code)
                .put("isDay", 1)
                .put("observedAt", "")
                .put("fetchedAt", fetchedAt);
    }

    private static City city(String name, double lat, double lon) {
        return new City(name, "", "España", lat, lon, City.Source.MANUAL);
    }
}
