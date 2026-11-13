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

    void saveLocation(String city, double lat, double lon) {
        String safeCity = city == null || city.trim().isEmpty()
                ? DEFAULT_CITY : city.trim();
        prefs.edit()
                .putString(KEY_CITY, safeCity)
                .putString(KEY_LAT, String.valueOf(lat))
                .putString(KEY_LON, String.valueOf(lon))
                .remove(KEY_WEATHER_CACHE)
                .apply();
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
