package com.itriguero.homeclock;

import android.content.SharedPreferences;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PrefsTest {

    @Test
    public void loadCitiesMigratesLegacyAndWritesMirror() {
        FakePrefs store = new FakePrefs();
        store.values.put("city", "Vigo");
        store.values.put("lat", "42.23282");
        store.values.put("lon", "-8.72264");
        Prefs prefs = new Prefs(store);

        CityList list = prefs.loadCities();

        assertEquals(1, list.size());
        assertEquals("Vigo", list.selected().name);
        assertTrue(store.values.containsKey("cities_v2"));
        assertEquals("Vigo", store.values.get("city"));
        assertEquals("42.23282", store.values.get("lat"));
        assertEquals("-8.72264", store.values.get("lon"));
    }

    @Test
    public void futureVersionIsNotOverwrittenBySelection() {
        String raw = "{\"version\":2,\"selected\":\"42.233,-8.723\","
                + "\"cities\":[{\"name\":\"Vigo\",\"region\":\"\",\"country\":\"España\","
                + "\"lat\":42.23282,\"lon\":-8.72264,\"source\":\"manual\"}]}";
        FakePrefs store = new FakePrefs();
        store.values.put("cities_v2", raw);
        Prefs prefs = new Prefs(store);

        CityList list = prefs.loadCities();
        assertTrue(list.isReadOnly());

        prefs.saveSelection(list);

        assertEquals(raw, store.values.get("cities_v2"));
        assertEquals(0, store.applies);
    }

    @Test
    public void saveCitiesWritesMirrorOfFirstCity() {
        FakePrefs store = new FakePrefs();
        Prefs prefs = new Prefs(store);
        City first = city("A", 40.0, -3.0);
        City second = city("B", 41.0, -4.0);
        CityList list = new CityList(List.of(first, second), second.key());

        prefs.saveCities(list);

        assertEquals("A", store.values.get("city"));
        assertEquals("40.0", store.values.get("lat"));
        assertEquals("-3.0", store.values.get("lon"));
        String raw = (String) store.values.get("cities_v2");
        assertTrue(raw.contains(second.key()));
    }

    @Test
    public void saveSelectionKeepsMirror() {
        FakePrefs store = new FakePrefs();
        Prefs prefs = new Prefs(store);
        City first = city("A", 40.0, -3.0);
        City second = city("B", 41.0, -4.0);
        CityList list = new CityList(List.of(first, second), first.key());

        prefs.saveCities(list);
        list.select(second.key());
        prefs.saveSelection(list);

        assertEquals("A", store.values.get("city"));
        assertTrue(((String) store.values.get("cities_v2")).contains(second.key()));
    }

    @Test
    public void saveLocationAddsAndSelectsInExistingList() {
        FakePrefs store = new FakePrefs();
        Prefs prefs = new Prefs(store);
        prefs.saveCities(new CityList(
                List.of(city("Tres Cantos", 40.6009, -3.7086)),
                City.keyOf(40.6009, -3.7086)));

        prefs.saveLocation("Vigo", 42.23282, -8.72264);

        CityList list = prefs.loadCities();
        assertEquals(2, list.size());
        assertEquals("Vigo", list.selected().name);
        assertEquals("Tres Cantos", store.values.get("city"));
    }

    @Test
    public void saveLocationReplacesTheOnlyCityInLegacyMode() {
        FakePrefs store = new FakePrefs();
        store.values.put("city", "Tres Cantos");
        store.values.put("lat", String.valueOf(Prefs.DEFAULT_LAT));
        store.values.put("lon", String.valueOf(Prefs.DEFAULT_LON));
        Prefs prefs = new Prefs(store);

        prefs.saveLocation("Vigo", 42.23282, -8.72264);

        CityList list = prefs.loadCities();
        assertEquals(1, list.size());
        assertEquals("Vigo", list.selected().name);
        assertEquals("Vigo", store.values.get("city"));
    }

    @Test
    public void loadWeatherCacheMigratesLegacyAndRemovesIt() {
        FakePrefs store = new FakePrefs();
        store.values.put("city", "Vigo");
        store.values.put("lat", "42.23282");
        store.values.put("lon", "-8.72264");
        store.values.put("weather_cache", legacyWeather(11.7));
        Prefs prefs = new Prefs(store);

        String generated = prefs.loadWeatherCache();

        assertFalse(generated.isEmpty());
        assertFalse(store.values.containsKey("weather_cache"));
        WeatherCache.Cache cache = WeatherCache.decode(generated);
        assertNotNull(cache.entryFor("42.233,-8.723"));
        assertEquals(1, cache.byCity.size());
    }

    @Test
    public void saveCitiesRemovesLegacyCacheWhenFirstCityChanges() {
        FakePrefs store = new FakePrefs();
        store.values.put("city", "Tres Cantos");
        store.values.put("lat", String.valueOf(Prefs.DEFAULT_LAT));
        store.values.put("lon", String.valueOf(Prefs.DEFAULT_LON));
        store.values.put("weather_cache", legacyWeather(11.7));
        Prefs prefs = new Prefs(store);
        City vigo = city("Vigo", 42.23282, -8.72264);

        prefs.saveCities(new CityList(List.of(vigo), vigo.key()));

        assertFalse(store.values.containsKey("weather_cache"));
    }

    private static String legacyWeather(double temp) {
        return "{\"temp\":" + temp + ",\"feels\":9.1,\"humidity\":91,"
                + "\"wind\":17.9,\"weatherCode\":3,\"isDay\":1,"
                + "\"updatedAt\":\"10:00\",\"savedAt\":1763112600000}";
    }

    private static City city(String name, double lat, double lon) {
        return new City(name, "", "España", lat, lon, City.Source.MANUAL);
    }

    /** SharedPreferences en memoria para probar Prefs sin Android. */
    private static final class FakePrefs implements SharedPreferences {

        final Map<String, Object> values = new HashMap<>();
        int applies;

        @Override
        public Map<String, ?> getAll() {
            return new HashMap<>(values);
        }

        @Override
        public String getString(String key, String defValue) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : defValue;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Set<String> getStringSet(String key, Set<String> defValues) {
            Object value = values.get(key);
            return value instanceof Set ? new HashSet<>((Set<String>) value) : defValues;
        }

        @Override
        public int getInt(String key, int defValue) {
            Object value = values.get(key);
            return value instanceof Number ? ((Number) value).intValue() : defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            Object value = values.get(key);
            return value instanceof Number ? ((Number) value).longValue() : defValue;
        }

        @Override
        public float getFloat(String key, float defValue) {
            Object value = values.get(key);
            return value instanceof Number ? ((Number) value).floatValue() : defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : defValue;
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new FakeEditor();
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
        }

        private final class FakeEditor implements Editor {

            private final Map<String, Object> puts = new HashMap<>();
            private final Set<String> removes = new HashSet<>();
            private boolean clear;

            @Override
            public Editor putString(String key, String value) {
                puts.put(key, value);
                return this;
            }

            @Override
            public Editor putStringSet(String key, Set<String> values) {
                puts.put(key, values == null ? null : new HashSet<>(values));
                return this;
            }

            @Override
            public Editor putInt(String key, int value) {
                puts.put(key, value);
                return this;
            }

            @Override
            public Editor putLong(String key, long value) {
                puts.put(key, value);
                return this;
            }

            @Override
            public Editor putFloat(String key, float value) {
                puts.put(key, value);
                return this;
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                puts.put(key, value);
                return this;
            }

            @Override
            public Editor remove(String key) {
                removes.add(key);
                return this;
            }

            @Override
            public Editor clear() {
                clear = true;
                return this;
            }

            @Override
            public boolean commit() {
                apply();
                return true;
            }

            @Override
            public void apply() {
                if (clear) values.clear();
                for (String key : removes) values.remove(key);
                values.putAll(puts);
                applies++;
            }
        }
    }
}
