package com.itriguero.homeclock;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Estado del tiempo listo para pintar: datos frescos de red o recuperados de
 * la caché. La UI no necesita saber de dónde vienen, solo mirar {@link #state}.
 */
final class WeatherSnapshot {

    enum State { FRESH, CACHED, EMPTY }

    final Map<String, WeatherCache.Entry> byCity;
    final long fetchedAt;
    final State state;
    /** true si falta alguna ciudad o alguna entrada conserva datos anteriores. */
    final boolean partial;
    /** Clasificación estable del fallo: offline, http, parse, invalid, unknown. */
    final String error;

    WeatherSnapshot(Map<String, WeatherCache.Entry> byCity, long fetchedAt,
                    State state, boolean partial, String error) {
        Map<String, WeatherCache.Entry> safe = new LinkedHashMap<>();
        if (byCity != null) {
            for (Map.Entry<String, WeatherCache.Entry> entry : byCity.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                safe.put(entry.getKey(), entry.getValue());
            }
        }
        boolean empty = safe.isEmpty();
        this.byCity = Collections.unmodifiableMap(safe);
        // Sin datos no hay una marca de actualización que mostrar.
        this.fetchedAt = empty ? 0L : fetchedAt;
        this.state = empty ? State.EMPTY : (state == null ? State.EMPTY : state);
        this.partial = !empty && partial;
        this.error = error == null ? "" : error;
    }

    static WeatherSnapshot empty() {
        return new WeatherSnapshot(Collections.emptyMap(), 0L, State.EMPTY, false, "");
    }

    WeatherCache.Entry entryFor(String key) {
        return key == null ? null : byCity.get(key);
    }

    Weather weatherFor(String key) {
        WeatherCache.Entry entry = entryFor(key);
        return entry == null ? null : entry.weather;
    }

    boolean isEmpty() {
        return byCity.isEmpty();
    }
}
