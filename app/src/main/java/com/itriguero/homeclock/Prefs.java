package com.itriguero.homeclock;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Acceso único a las preferencias de HomeClock. Mantiene las mismas claves
 * que usaba MainActivity para no romper instalaciones existentes.
 */
final class Prefs {

    private static final String NAME = "homeclock";

    private static final String KEY_CITY = "city";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LON = "lon";
    private static final String KEY_SHOW_MATCHES = "show_matches";
    private static final String KEY_TEST_DATE = "test_date";
    private static final String KEY_WEATHER_CACHE = "weather_cache";
    private static final String KEY_CITIES = "cities_v2";
    private static final String KEY_WEATHER_CACHE_V2 = "weather_cache_v2";
    private static final String KEY_NIGHT_ENABLED = "night_enabled";
    private static final String KEY_NIGHT_START = "night_start";
    private static final String KEY_NIGHT_END = "night_end";
    private static final String KEY_LAST_UPDATE_CHECK = "last_update_check";
    private static final String KEY_UPDATE_SNOOZE_VERSION = "update_snooze_version";
    private static final String KEY_UPDATE_SNOOZE_UNTIL = "update_snooze_until";
    private static final String KEY_REFRESH_REQUEST = "refresh_requested";

    static final String DEFAULT_CITY = "Tres Cantos";
    static final double DEFAULT_LAT = 40.6009;
    static final double DEFAULT_LON = -3.7086;
    static final String DEFAULT_NIGHT_START = "23:00";
    static final String DEFAULT_NIGHT_END = "07:00";

    private final SharedPreferences prefs;

    Prefs(Context context) {
        prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    /** Constructor para tests JVM con una implementación en memoria. */
    Prefs(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    String city() {
        return string(KEY_CITY, DEFAULT_CITY);
    }

    double lat() {
        return parseDouble(string(KEY_LAT, String.valueOf(DEFAULT_LAT)), DEFAULT_LAT);
    }

    double lon() {
        return parseDouble(string(KEY_LON, String.valueOf(DEFAULT_LON)), DEFAULT_LON);
    }

    boolean showMatches() {
        return prefs.getBoolean(KEY_SHOW_MATCHES, true);
    }

    String testDate() {
        return string(KEY_TEST_DATE, "");
    }

    String weatherCache() {
        return string(KEY_WEATHER_CACHE, "");
    }

    boolean nightEnabled() {
        return prefs.getBoolean(KEY_NIGHT_ENABLED, false);
    }

    String nightStart() {
        return string(KEY_NIGHT_START, DEFAULT_NIGHT_START);
    }

    String nightEnd() {
        return string(KEY_NIGHT_END, DEFAULT_NIGHT_END);
    }

    long lastUpdateCheck() {
        return prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L);
    }

    void saveLastUpdateCheck(long value) {
        prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply();
    }

    int updateSnoozeVersion() {
        return prefs.getInt(KEY_UPDATE_SNOOZE_VERSION, 0);
    }

    long updateSnoozeUntil() {
        return prefs.getLong(KEY_UPDATE_SNOOZE_UNTIL, 0L);
    }

    void saveUpdateSnooze(int versionCode, long untilMillis) {
        prefs.edit()
                .putInt(KEY_UPDATE_SNOOZE_VERSION, versionCode)
                .putLong(KEY_UPDATE_SNOOZE_UNTIL, untilMillis)
                .apply();
    }

    /** Pide a MainActivity un refresco inmediato al volver de Ajustes. */
    void requestRefresh() {
        prefs.edit().putBoolean(KEY_REFRESH_REQUEST, true).apply();
    }

    /** Consume la petición una sola vez. */
    boolean consumeRefreshRequest() {
        if (!prefs.getBoolean(KEY_REFRESH_REQUEST, false)) return false;
        prefs.edit().remove(KEY_REFRESH_REQUEST).apply();
        return true;
    }

    /**
     * Firma estable para detectar cambios de ubicación sin comparar tres
     * valores por separado en cada onResume.
     */
    String locationSignature() {
        return city() + "|" + lat() + "|" + lon();
    }

    /**
     * Compatibilidad con el flujo antiguo de una sola ciudad. Si todavía no
     * existe {@code cities_v2}, sustituye la única ciudad; si ya existe la
     * lista, añade o selecciona la ciudad sin dejar dos escritores.
     */
    void saveLocation(String city, double lat, double lon) {
        City candidate = new City(city, "", "", lat, lon, City.Source.MANUAL);
        if (candidate.isValid()) {
            boolean legacyMode = string(KEY_CITIES, "").isEmpty();
            CityList list = loadCities();
            if (legacyMode) {
                // Migración desde una instalación antigua: una sola ciudad.
                CityList replaced = new CityList(
                        java.util.Collections.singletonList(candidate),
                        candidate.key(), false);
                saveCities(replaced);
                saveWeatherCache(null);
                return;
            }

            if (list.indexOf(candidate.key()) >= 0) {
                list.select(candidate.key());
            } else {
                CityList.AddResult result = list.add(candidate);
                if (result == CityList.AddResult.FULL) return;
                if (result == CityList.AddResult.INVALID) return;
                list.select(candidate.key());
            }
            saveCities(list);
            return;
        }

        String safeCity = city == null || city.trim().isEmpty()
                ? DEFAULT_CITY : city.trim();
        prefs.edit()
                .putString(KEY_CITY, safeCity)
                .putString(KEY_LAT, String.valueOf(lat))
                .putString(KEY_LON, String.valueOf(lon))
                .remove(KEY_WEATHER_CACHE)
                .apply();
    }

    /**
     * Lee la lista de ciudades. Si migra desde el formato antiguo o repara el
     * JSON, guarda el resultado y el espejo city/lat/lon de la primera ciudad
     * en un único editor. Si el JSON viene de una versión futura no se toca.
     */
    CityList loadCities() {
        CityListCodec.Legacy legacy = new CityListCodec.Legacy();
        legacy.city = string(KEY_CITY, DEFAULT_CITY);
        legacy.lat = string(KEY_LAT, String.valueOf(DEFAULT_LAT));
        legacy.lon = string(KEY_LON, String.valueOf(DEFAULT_LON));

        CityListCodec.Decoded decoded = CityListCodec.decode(
                string(KEY_CITIES, ""), legacy);
        if (!decoded.readOnly && (decoded.migrated || decoded.repaired)) {
            saveCities(decoded.list);
        }
        return decoded.list;
    }

    /** Guarda la lista completa y el espejo de su primera ciudad. */
    void saveCities(CityList list) {
        if (list == null || list.size() == 0 || list.isReadOnly()) return;
        String encoded = CityListCodec.encode(list);
        if (encoded.isEmpty()) return;

        City first = list.get(0);
        boolean firstChanged = !City.keyOf(lat(), lon()).equals(first.key());
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_CITIES, encoded)
                .putString(KEY_CITY, first.name)
                .putString(KEY_LAT, String.valueOf(first.lat))
                .putString(KEY_LON, String.valueOf(first.lon));
        if (firstChanged) editor.remove(KEY_WEATHER_CACHE);
        editor.apply();
    }

    /** Guarda solo la selección; el espejo de la primera ciudad no cambia. */
    void saveSelection(CityList list) {
        if (list == null || list.isReadOnly()) return;
        String encoded = CityListCodec.encode(list);
        if (encoded.isEmpty()) return;
        prefs.edit()
                .putString(KEY_CITIES, encoded)
                .apply();
    }

    String weatherCacheV2() {
        return string(KEY_WEATHER_CACHE_V2, "");
    }

    /**
     * Caché de tiempo actual. Si aún no existe la v2, migra la caché antigua
     * de una sola ciudad usando la primera ciudad de la lista.
     */
    String loadWeatherCache() {
        String current = weatherCacheV2();
        if (!current.isEmpty()) return current;

        String legacy = weatherCache();
        if (legacy.isEmpty()) return "";

        CityList list = loadCities();
        WeatherCache.Cache migrated = WeatherCache.migrateLegacy(
                legacy, list.get(0).key());
        String encoded = WeatherCache.encode(migrated, list.cities());
        if (encoded.isEmpty()) return "";
        saveWeatherCacheV2(encoded);
        saveWeatherCache(null);
        return encoded;
    }

    void saveWeatherCacheV2(String json) {
        SharedPreferences.Editor editor = prefs.edit();
        if (json == null) {
            editor.remove(KEY_WEATHER_CACHE_V2);
        } else {
            editor.putString(KEY_WEATHER_CACHE_V2, json);
        }
        editor.apply();
    }

    void saveShowMatches(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_MATCHES, show).apply();
    }

    /** Con null o vacío se elimina la fecha de prueba, igual que antes. */
    void saveTestDate(String value) {
        SharedPreferences.Editor editor = prefs.edit();
        if (value == null || value.trim().isEmpty()) {
            editor.remove(KEY_TEST_DATE);
        } else {
            editor.putString(KEY_TEST_DATE, value.trim());
        }
        editor.apply();
    }

    void saveNight(boolean enabled, String start, String end) {
        prefs.edit()
                .putBoolean(KEY_NIGHT_ENABLED, enabled)
                .putString(KEY_NIGHT_START, start)
                .putString(KEY_NIGHT_END, end)
                .apply();
    }

    void saveWeatherCache(String json) {
        SharedPreferences.Editor editor = prefs.edit();
        if (json == null) {
            editor.remove(KEY_WEATHER_CACHE);
        } else {
            editor.putString(KEY_WEATHER_CACHE, json);
        }
        editor.apply();
    }

    private String string(String key, String fallback) {
        String value = prefs.getString(key, fallback);
        return value == null ? fallback : value;
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null) return fallback;
        try {
            return Double.parseDouble(value.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
