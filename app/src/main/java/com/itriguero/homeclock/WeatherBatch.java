package com.itriguero.homeclock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado de una consulta de tiempo para varias ciudades.
 *
 * {@link #requestedKeys} conserva el orden solicitado y puede contener claves
 * sin datos: una respuesta parcial es válida y no invalida el resto.
 */
final class WeatherBatch {

    final Map<String, Weather> byCity;
    final List<String> requestedKeys;
    final long fetchedAt;

    WeatherBatch(Map<String, Weather> byCity, List<String> requestedKeys, long fetchedAt) {
        Map<String, Weather> safe = new LinkedHashMap<>();
        if (byCity != null) {
            for (Map.Entry<String, Weather> entry : byCity.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                safe.put(entry.getKey(), entry.getValue());
            }
        }

        List<String> keys = new ArrayList<>();
        if (requestedKeys != null) {
            for (String key : requestedKeys) {
                if (key != null) keys.add(key);
            }
        }

        this.byCity = Collections.unmodifiableMap(safe);
        this.requestedKeys = Collections.unmodifiableList(keys);
        this.fetchedAt = fetchedAt;
    }

    Weather weatherFor(String key) {
        return key == null ? null : byCity.get(key);
    }

    boolean isEmpty() {
        return byCity.isEmpty();
    }
}
