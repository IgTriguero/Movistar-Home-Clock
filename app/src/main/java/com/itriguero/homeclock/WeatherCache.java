package com.itriguero.homeclock;

import org.json.JSONObject;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.HashSet;

/**
 * Caché del tiempo por ciudad ({@code weather_cache_v2}).
 *
 * {@link #decode(String)} nunca lanza: si el JSON está roto o la versión no se
 * reconoce devuelve una caché vacía. {@link #encode(Cache, List)} es
 * determinista y poda las ciudades que ya no están en la lista.
 */
final class WeatherCache {

    static final int VERSION = 1;

    private static final String KEY_VERSION = "version";
    private static final String KEY_FETCHED_AT = "fetchedAt";
    private static final String KEY_CITIES = "cities";
    private static final String KEY_TEMP = "temp";
    private static final String KEY_FEELS = "feels";
    private static final String KEY_HUMIDITY = "humidity";
    private static final String KEY_WIND = "wind";
    private static final String KEY_WEATHER_CODE = "weatherCode";
    private static final String KEY_IS_DAY = "isDay";
    private static final String KEY_OBSERVED_AT = "observedAt";

    private WeatherCache() {
    }

    /** Decodifica sin lanzar; cualquier incoherencia se traduce en caché vacía. */
    static Cache decode(String raw) {
        if (raw == null || raw.trim().isEmpty()) return Cache.empty();

        try {
            JSONObject root = new JSONObject(raw);
            if (root.optInt(KEY_VERSION, -1) != VERSION) return Cache.empty();

            long fetchedAt = root.optLong(KEY_FETCHED_AT, 0L);
            if (fetchedAt <= 0) return Cache.empty();

            JSONObject cities = root.optJSONObject(KEY_CITIES);
            if (cities == null) return Cache.empty();

            Map<String, Entry> entries = new LinkedHashMap<>();
            Iterator<String> keys = cities.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key == null || key.trim().isEmpty()) continue;
                try {
                    Entry entry = entryFromJson(cities.optJSONObject(key));
                    if (entry != null) entries.put(key, entry);
                } catch (Exception ignored) {
                    // Una entrada incoherente se descarta sin invalidar el resto.
                }
            }

            if (entries.isEmpty()) return Cache.empty();
            return new Cache(fetchedAt, entries);
        } catch (Exception e) {
            return Cache.empty();
        }
    }

    /**
     * Serializa solo las entradas de ciudades presentes en {@code cities}.
     * Devuelve cadena vacía si no queda ninguna entrada.
     */
    static String encode(Cache cache, List<City> cities) {
        if (cache == null) return "";

        Set<String> allowed = keysOf(cities);
        Map<String, Entry> sorted = new TreeMap<>();
        for (Map.Entry<String, Entry> entry : cache.byCity.entrySet()) {
            if (entry.getValue() == null) continue;
            if (!allowed.contains(entry.getKey())) continue;
            sorted.put(entry.getKey(), entry.getValue());
        }
        if (sorted.isEmpty()) return "";

        try {
            JSONObject root = new JSONObject();
            root.put(KEY_VERSION, VERSION);
            root.put(KEY_FETCHED_AT, cache.fetchedAt > 0
                    ? cache.fetchedAt : maxFetchedAt(sorted));

            JSONObject citiesJson = new JSONObject();
            for (Map.Entry<String, Entry> entry : sorted.entrySet()) {
                citiesJson.put(entry.getKey(), entryToJson(entry.getValue()));
            }
            root.put(KEY_CITIES, citiesJson);
            return root.toString();
        } catch (Exception e) {
            // Solo fallaría con valores no finitos, que Weather ya rechaza.
            return "";
        }
    }

    /**
     * Combina la caché anterior con una respuesta nueva:
     * las ciudades presentes se sustituyen, las ausentes conservan su entrada
     * y su marca de tiempo, y las que ya no están se podan.
     */
    static Cache merge(Cache previous, WeatherBatch batch, List<City> cities) {
        Set<String> allowed = keysOf(cities);
        Map<String, Entry> merged = new LinkedHashMap<>();

        long previousFetchedAt = previous == null ? 0L : previous.fetchedAt;
        if (previous != null) {
            for (Map.Entry<String, Entry> entry : previous.byCity.entrySet()) {
                if (entry.getValue() == null) continue;
                if (!allowed.contains(entry.getKey())) continue;
                merged.put(entry.getKey(), entry.getValue());
            }
        }

        int added = 0;
        if (batch != null && batch.fetchedAt > 0) {
            for (Map.Entry<String, Weather> entry : batch.byCity.entrySet()) {
                if (entry.getValue() == null) continue;
                if (!allowed.contains(entry.getKey())) continue;
                merged.put(entry.getKey(), new Entry(entry.getValue(), batch.fetchedAt));
                added++;
            }
        }

        if (merged.isEmpty()) return Cache.empty();
        long fetchedAt = added > 0 ? batch.fetchedAt : previousFetchedAt;
        return new Cache(fetchedAt, merged);
    }

    /**
     * Adapta el formato antiguo de una sola ciudad. Exige {@code savedAt > 0}
     * y valores válidos; en cualquier otro caso devuelve caché vacía.
     */
    static Cache migrateLegacy(String legacyRaw, String cityKey) {
        if (legacyRaw == null || legacyRaw.trim().isEmpty()) return Cache.empty();
        String key = cityKey == null ? "" : cityKey.trim();
        if (key.isEmpty()) return Cache.empty();

        try {
            JSONObject root = new JSONObject(legacyRaw);
            long savedAt = root.optLong("savedAt", 0L);
            if (savedAt <= 0) return Cache.empty();

            Weather weather = weatherFromJson(root, "");
            if (weather == null) return Cache.empty();

            return new Cache(savedAt,
                    Collections.singletonMap(key, new Entry(weather, savedAt)));
        } catch (Exception e) {
            return Cache.empty();
        }
    }

    private static Entry entryFromJson(JSONObject json) {
        if (json == null) return null;

        long fetchedAt = json.optLong(KEY_FETCHED_AT, 0L);
        if (fetchedAt <= 0) return null;

        Weather weather = weatherFromJson(json, json.isNull(KEY_OBSERVED_AT)
                ? "" : json.optString(KEY_OBSERVED_AT, ""));
        if (weather == null) return null;
        return new Entry(weather, fetchedAt);
    }

    private static Weather weatherFromJson(JSONObject json, String observedAt) {
        double temp = number(json, KEY_TEMP);
        double feels = number(json, KEY_FEELS);
        double wind = number(json, KEY_WIND);
        double humidity = number(json, KEY_HUMIDITY);
        double code = number(json, KEY_WEATHER_CODE);
        if (!isFinite(temp) || !isFinite(feels) || !isFinite(wind)
                || !isFinite(humidity) || !isFinite(code)) {
            return null;
        }
        if (humidity != Math.rint(humidity) || code != Math.rint(code)) return null;

        double isDayValue = number(json, KEY_IS_DAY);
        int isDay = Double.isNaN(isDayValue) ? 1 : (isDayValue == 0 ? 0 : 1);

        try {
            return new Weather(temp, feels, wind,
                    (int) humidity, (int) code, isDay, observedAt);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static JSONObject entryToJson(Entry entry) throws Exception {
        Weather weather = entry.weather;
        JSONObject json = new JSONObject();
        json.put(KEY_TEMP, weather.temp);
        json.put(KEY_FEELS, weather.feels);
        json.put(KEY_HUMIDITY, weather.humidity);
        json.put(KEY_WIND, weather.wind);
        json.put(KEY_WEATHER_CODE, weather.weatherCode);
        json.put(KEY_IS_DAY, weather.isDay);
        json.put(KEY_OBSERVED_AT, weather.observedAt);
        json.put(KEY_FETCHED_AT, entry.fetchedAt);
        return json;
    }

    private static Set<String> keysOf(List<City> cities) {
        Set<String> keys = new HashSet<>();
        if (cities == null) return keys;
        for (City city : cities) {
            if (city == null || !city.isValid()) continue;
            keys.add(city.key());
        }
        return keys;
    }

    private static long maxFetchedAt(Map<String, Entry> entries) {
        long max = 0L;
        for (Entry entry : entries.values()) {
            if (entry != null && entry.fetchedAt > max) max = entry.fetchedAt;
        }
        return max;
    }

    private static double number(JSONObject json, String key) {
        Object value = json.opt(key);
        if (value instanceof Number) {
            double result = ((Number) value).doubleValue();
            return isFinite(result) ? result : Double.NaN;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) return Double.NaN;
            try {
                double result = Double.parseDouble(text);
                return isFinite(result) ? result : Double.NaN;
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    /** Tiempo de una ciudad junto con la marca del último fetch que lo trajo. */
    static final class Entry {
        final Weather weather;
        final long fetchedAt;

        Entry(Weather weather, long fetchedAt) {
            if (weather == null) throw new IllegalArgumentException("weather nulo");
            this.weather = weather;
            this.fetchedAt = fetchedAt;
        }
    }

    /** Contenido completo de la caché v2. */
    static final class Cache {
        final long fetchedAt;
        final Map<String, Entry> byCity;

        Cache(long fetchedAt, Map<String, Entry> byCity) {
            Map<String, Entry> safe = new LinkedHashMap<>();
            if (byCity != null) {
                for (Map.Entry<String, Entry> entry : byCity.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) continue;
                    safe.put(entry.getKey(), entry.getValue());
                }
            }
            this.fetchedAt = fetchedAt;
            this.byCity = Collections.unmodifiableMap(safe);
        }

        static Cache empty() {
            return new Cache(0L, Collections.emptyMap());
        }

        boolean isEmpty() {
            return byCity.isEmpty();
        }

        Entry entryFor(String key) {
            return key == null ? null : byCity.get(key);
        }
    }
}
