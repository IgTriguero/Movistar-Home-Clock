package com.itriguero.homeclock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orquesta la consulta multi-ciudad y la caché sin conocer Android.
 *
 * Las dependencias externas se inyectan mediante {@link Source} y {@link Store},
 * de modo que la lógica se prueba en la JVM con dobles sencillos.
 */
final class WeatherRepository {

    interface Source {
        WeatherBatch fetch(List<City> cities) throws Exception;
    }

    interface Store {
        String read();

        void write(String json);
    }

    private final Source source;
    private final Store store;

    WeatherRepository(Source source, Store store) {
        this.source = source;
        this.store = store;
    }

    /** Devuelve lo último guardado, filtrado a las ciudades actuales. */
    WeatherSnapshot loadCached(List<City> cities) {
        List<City> valid = validCities(cities);
        if (valid.isEmpty()) return WeatherSnapshot.empty();

        WeatherCache.Cache cache = readCache();
        Map<String, WeatherCache.Entry> entries = entriesFor(cache, valid);
        if (entries.isEmpty()) {
            return new WeatherSnapshot(Collections.emptyMap(), cache.fetchedAt,
                    WeatherSnapshot.State.EMPTY, false, "");
        }
        return new WeatherSnapshot(entries, cache.fetchedAt,
                WeatherSnapshot.State.CACHED, entries.size() < valid.size(), "");
    }

    /**
     * Consulta la red y combina el resultado con la caché anterior.
     *
     * Nunca lanza: los fallos se devuelven como CACHED (si había datos) o
     * EMPTY, con el error clasificado y sin reescribir la caché.
     */
    synchronized WeatherSnapshot refreshBlocking(List<City> cities) {
        List<City> valid = validCities(cities);
        if (valid.isEmpty()) return loadCached(cities);

        WeatherCache.Cache previous = readCache();

        WeatherBatch batch;
        try {
            if (source == null) {
                throw new WeatherClient.WeatherException(
                        WeatherClient.WeatherException.Kind.INVALID,
                        "Sin fuente de tiempo");
            }
            batch = source.fetch(Collections.unmodifiableList(valid));
        } catch (Exception e) {
            return failure(previous, valid, e);
        }

        if (batch == null) {
            return failure(previous, valid,
                    new IllegalStateException("Respuesta de tiempo nula"));
        }

        // Una respuesta 200 sin ninguna entrada válida no es un éxito: si se
        // tratara como FRESH se mostrarían datos viejos como recién obtenidos.
        if (countNewEntries(batch, valid) == 0) {
            return failure(previous, valid,
                    new WeatherClient.WeatherException(
                            WeatherClient.WeatherException.Kind.INVALID,
                            "Sin datos válidos"));
        }

        WeatherCache.Cache merged = WeatherCache.merge(previous, batch, valid);
        writeCache(merged, valid);

        Map<String, WeatherCache.Entry> entries = entriesFor(merged, valid);
        boolean partial = entries.size() < valid.size();
        if (!partial) {
            // El batch dice exactamente qué ciudades trajo datos nuevos; los
            // timestamps no son fiables si el reloj cambia o coincide.
            for (City city : valid) {
                if (!batch.byCity.containsKey(city.key())) {
                    partial = true;
                    break;
                }
            }
        }

        return new WeatherSnapshot(entries, merged.fetchedAt,
                WeatherSnapshot.State.FRESH, partial, "");
    }

    private WeatherSnapshot failure(WeatherCache.Cache previous, List<City> valid,
                                    Exception error) {
        String classified = classify(error);
        Map<String, WeatherCache.Entry> entries = entriesFor(previous, valid);
        if (entries.isEmpty()) {
            return new WeatherSnapshot(Collections.emptyMap(), previous.fetchedAt,
                    WeatherSnapshot.State.EMPTY, false, classified);
        }
        return new WeatherSnapshot(entries, previous.fetchedAt,
                WeatherSnapshot.State.CACHED,
                entries.size() < valid.size(), classified);
    }

    private void writeCache(WeatherCache.Cache cache, List<City> valid) {
        if (store == null) return;
        try {
            String encoded = WeatherCache.encode(cache, valid);
            if (!encoded.isEmpty()) store.write(encoded);
        } catch (Exception ignored) {
            // La caché es best-effort: el dato fresco ya se va a devolver.
        }
    }

    private WeatherCache.Cache readCache() {
        if (store == null) return WeatherCache.Cache.empty();
        try {
            return WeatherCache.decode(store.read());
        } catch (Exception e) {
            return WeatherCache.Cache.empty();
        }
    }

    private static Map<String, WeatherCache.Entry> entriesFor(
            WeatherCache.Cache cache, List<City> cities) {
        Map<String, WeatherCache.Entry> entries = new LinkedHashMap<>();
        if (cache == null) return entries;
        for (City city : cities) {
            WeatherCache.Entry entry = cache.entryFor(city.key());
            if (entry != null) entries.put(city.key(), entry);
        }
        return entries;
    }

    private static int countNewEntries(WeatherBatch batch, List<City> cities) {
        if (batch == null || batch.fetchedAt <= 0) return 0;
        int count = 0;
        for (City city : cities) {
            if (batch.byCity.containsKey(city.key())) count++;
        }
        return count;
    }

    /** Filtra ciudades nulas o inválidas y evita claves duplicadas. */
    private static List<City> validCities(List<City> cities) {
        List<City> valid = new ArrayList<>();
        if (cities == null) return valid;
        Set<String> keys = new HashSet<>();
        for (City city : cities) {
            if (city == null || !city.isValid()) continue;
            if (!keys.add(city.key())) continue;
            valid.add(city);
            if (valid.size() >= CityList.MAX_CITIES) break;
        }
        return valid;
    }

    private static String classify(Exception error) {
        if (error instanceof WeatherClient.WeatherException) {
            switch (((WeatherClient.WeatherException) error).kind) {
                case OFFLINE:
                    return "offline";
                case HTTP:
                    return "http";
                case PARSE:
                    return "parse";
                case INVALID:
                    return "invalid";
                default:
                    return "unknown";
            }
        }
        if (error instanceof java.io.IOException) return "offline";
        if (error instanceof org.json.JSONException) return "parse";
        if (error instanceof IllegalArgumentException) return "invalid";
        return "unknown";
    }
}
