package com.itriguero.homeclock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Codec puro del JSON de ciudades ({@code cities_v2}). No conoce Android: se
 * puede probar en la JVM y reutilizar desde cualquier otra capa.
 *
 * {@link #decode(String, Legacy)} nunca falla: ante datos ausentes, rotos o
 * incoherentes devuelve una lista válida y marca la reparación para que Prefs
 * pueda reescribir el valor una sola vez.
 */
final class CityListCodec {

    static final int VERSION = 1;

    private static final String KEY_VERSION = "version";
    private static final String KEY_SELECTED = "selected";
    private static final String KEY_CITIES = "cities";
    private static final String KEY_NAME = "name";
    private static final String KEY_REGION = "region";
    private static final String KEY_COUNTRY = "country";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LON = "lon";
    private static final String KEY_SOURCE = "source";

    private CityListCodec() {
    }

    static Decoded decode(String raw, Legacy legacy) {
        if (raw == null || raw.trim().isEmpty()) return fromLegacy(legacy);

        JSONObject root;
        try {
            root = new JSONObject(raw);
        } catch (Exception e) {
            // JSON roto o que no es un objeto: se conserva lo que hubiera antes.
            return fromLegacy(legacy);
        }

        boolean repaired = false;
        int version = root.optInt(KEY_VERSION, VERSION);
        if (version < VERSION) repaired = true;
        boolean readOnly = version > VERSION;

        List<City> parsed = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        JSONArray array = root.optJSONArray(KEY_CITIES);
        if (array == null) {
            repaired = true;
        } else {
            for (int i = 0; i < array.length(); i++) {
                City city = cityFromJson(array.optJSONObject(i));
                if (city == null || !city.isValid()) {
                    repaired = true;
                    continue;
                }
                if (!keys.add(city.key())) {
                    // Duplicado: se conserva el primero.
                    repaired = true;
                    continue;
                }
                if (parsed.size() >= CityList.MAX_CITIES) {
                    repaired = true;
                    continue;
                }
                parsed.add(city);
            }
        }
        if (parsed.isEmpty()) {
            // El JSON existe pero no aporta ninguna ciudad válida: antes de
            // rendirnos, recuperamos la ciudad del formato antiguo.
            parsed.add(migrateCity(legacy));
            repaired = true;
        }

        String selectedKey = findKey(parsed, root.optString(KEY_SELECTED, ""));
        if (selectedKey == null) {
            selectedKey = parsed.get(0).key();
            repaired = true;
        }

        Decoded decoded = new Decoded();
        decoded.list = new CityList(parsed, selectedKey, readOnly);
        decoded.migrated = false;
        decoded.repaired = repaired;
        decoded.readOnly = readOnly;
        return decoded;
    }

    /**
     * Serializa siempre con la forma actual. Es determinista para el mismo
     * contenido y tolera entradas nulas o incompletas.
     */
    static String encode(CityList list) {
        CityList safe = list;
        if (safe == null || safe.size() == 0) {
            City fallback = City.defaultCity();
            safe = new CityList(Collections.singletonList(fallback), fallback.key());
        }

        try {
            JSONObject root = new JSONObject();
            root.put(KEY_VERSION, VERSION);
            root.put(KEY_SELECTED, safe.selectedKey());

            JSONArray array = new JSONArray();
            for (City city : safe.cities()) {
                JSONObject item = new JSONObject();
                item.put(KEY_NAME, city.name);
                item.put(KEY_REGION, city.region);
                item.put(KEY_COUNTRY, city.country);
                item.put(KEY_LAT, city.lat);
                item.put(KEY_LON, city.lon);
                item.put(KEY_SOURCE, city.source.json);
                array.put(item);
            }
            root.put(KEY_CITIES, array);
            return root.toString();
        } catch (Exception e) {
            // Solo podría fallar con valores no finitos, que City ya rechaza.
            return "";
        }
    }

    static City defaultCity() {
        return City.defaultCity();
    }

    private static Decoded fromLegacy(Legacy legacy) {
        City city = migrateCity(legacy);

        Decoded decoded = new Decoded();
        decoded.list = new CityList(Collections.singletonList(city), city.key(), false);
        decoded.migrated = true;
        decoded.repaired = false;
        decoded.readOnly = false;
        return decoded;
    }

    /**
     * Crea la ciudad del formato antiguo. Si falta una coordenada se usa la de
     * la ciudad por defecto, como hacía el parser anterior; si está fuera de
     * rango, la ciudad se considera inválida y se usa la ciudad por defecto.
     */
    private static City migrateCity(Legacy legacy) {
        if (legacy == null) return City.defaultCity();

        String name = legacy.city == null ? "" : legacy.city.trim();
        if (name.isEmpty()) return City.defaultCity();

        double lat = coordinate(legacy.lat);
        double lon = coordinate(legacy.lon);
        if (Double.isNaN(lat)) lat = City.DEFAULT_LAT;
        if (Double.isNaN(lon)) lon = City.DEFAULT_LON;

        City.Source source = City.keyOf(lat, lon).equals(City.defaultCity().key())
                ? City.Source.DEFAULT
                : City.Source.MANUAL;
        City candidate = new City(name, "", "", lat, lon, source);
        return candidate.isValid() ? candidate : City.defaultCity();
    }

    private static City cityFromJson(JSONObject json) {
        if (json == null || !json.has(KEY_LAT) || !json.has(KEY_LON)) return null;

        double lat = coordinate(json.opt(KEY_LAT));
        double lon = coordinate(json.opt(KEY_LON));
        if (Double.isNaN(lat) || Double.isNaN(lon)) return null;

        Object nameValue = json.opt(KEY_NAME);
        if (!(nameValue instanceof String)) return null;
        String name = ((String) nameValue).trim();
        if (name.isEmpty()) return null;

        String region = json.opt(KEY_REGION) instanceof String
                ? (String) json.opt(KEY_REGION) : "";
        String country = json.opt(KEY_COUNTRY) instanceof String
                ? (String) json.opt(KEY_COUNTRY) : "";
        String source = json.opt(KEY_SOURCE) instanceof String
                ? (String) json.opt(KEY_SOURCE) : "";

        return new City(name, region, country, lat, lon,
                City.Source.fromJson(source));
    }

    private static String findKey(List<City> cities, String selected) {
        if (selected == null) return null;
        String trimmed = selected.trim();
        if (trimmed.isEmpty()) return null;
        for (City city : cities) {
            if (city.key().equals(trimmed)) return city.key();
        }
        return null;
    }

    /** Acepta números JSON y cadenas con punto o coma decimal. */
    private static double coordinate(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            String text = ((String) value).trim().replace(',', '.');
            if (text.isEmpty()) return Double.NaN;
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    static final class Legacy {
        String city;
        String lat;
        String lon;
    }

    static final class Decoded {
        CityList list;
        boolean migrated;
        boolean repaired;
        boolean readOnly;
    }
}
