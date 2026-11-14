package com.itriguero.homeclock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Diagnóstico en memoria de la última actualización de partidos y tiempo.
 *
 * No toca disco ni Android: la UI puede leer este estado y, si la app acaba
 * de arrancar, caer a {@link MatchCache} o a las preferencias como respaldo.
 * Todos los métodos son thread-safe.
 *
 * Además del último intento guarda la salud acumulada de cada fuente y del
 * tiempo. Cualquier cambio incrementa {@link Snapshot#revision} y avisa al
 * {@link Listener} registrado con una copia inmutable del estado.
 */
public final class Diagnostics {

    private static final long MINUTE_MS = 60_000L;
    private static final long HOUR_MS = 60L * MINUTE_MS;

    private static final Diagnostics INSTANCE = new Diagnostics();

    /** Store compartido por MainActivity y SettingsActivity (mismo proceso). */
    public static Diagnostics get() {
        return INSTANCE;
    }

    /**
     * Aviso de cambio de estado. Se invoca siempre fuera del lock interno y
     * con una copia inmutable, para que el receptor pueda leer o incluso
     * registrar otro cambio sin bloquearse.
     */
    public interface Listener {
        void onChanged(Snapshot snapshot);
    }

    private long matchesAt;
    private long matchesAttemptAt;
    private String matchesDate = "";
    private long matchesDurationMs;
    private long weatherAt;
    private boolean stale;
    private int matchCount;
    private Map<String, SourceStatus> sources = Collections.emptyMap();
    private Map<String, SourceHealth> sourceHealth = Collections.emptyMap();
    private long fetchedAt;

    private String weatherState = "";
    private boolean weatherPartial;
    private String weatherError = "";
    private int weatherCityCount;
    private long weatherAttemptAt;
    private long weatherDurationMs;
    private int weatherConsecutiveFailures;
    private long weatherLastOkAt;
    private long weatherLastErrorAt;
    private long weatherDownSince;

    private long revision;
    private Listener listener;

    /**
     * Registra el único listener de cambios. Pasar {@code null} equivale a
     * {@link #clearListener(Listener)}.
     */
    public synchronized void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Quita el listener si es el registrado. Un {@code null} lo quita siempre,
     * de modo que el llamador no necesita recordar la instancia exacta.
     */
    public synchronized void clearListener(Listener listener) {
        if (listener == null || this.listener == listener) {
            this.listener = null;
        }
    }

    /**
     * Compatibilidad: registra el intento como si hubiera empezado ahora.
     */
    public void recordMatches(MatchesSnapshot snapshot, long durationMs) {
        long now = System.currentTimeMillis();
        recordMatches(snapshot, now, durationMs, now);
    }

    public void recordMatches(MatchesSnapshot snapshot, long attemptStartedAt,
                              long durationMs) {
        recordMatches(snapshot, attemptStartedAt, durationMs,
                System.currentTimeMillis());
    }

    /**
     * Registra el intento de partidos con un reloj explícito para poder
     * probarlo sin depender de la hora del sistema.
     */
    void recordMatches(MatchesSnapshot snapshot, long attemptStartedAt,
                       long durationMs, long now) {
        if (snapshot == null) return;

        Snapshot copy;
        Listener target;
        synchronized (this) {
            long at = Math.max(0L, now);
            matchesAt = at;
            matchesAttemptAt = Math.max(0L, attemptStartedAt);
            matchesDate = snapshot.date == null ? "" : snapshot.date.toString();
            matchesDurationMs = Math.max(0L, durationMs);
            stale = snapshot.stale;
            matchCount = snapshot.matches == null ? 0 : snapshot.matches.size();
            sources = snapshot.sources == null
                    ? Collections.emptyMap()
                    : new LinkedHashMap<>(snapshot.sources);
            fetchedAt = snapshot.fetchedAt;
            updateSourceHealth(snapshot.sources, at);
            revision++;
            copy = new Snapshot(this);
            target = listener;
        }
        deliver(target, copy);
    }

    /**
     * Versión antigua: solo mueve la marca del tiempo de los datos. No toca la
     * salud porque no hay intento que contar (por ejemplo, al restaurar una
     * caché al arrancar).
     */
    public void recordWeather(long fetchedAt) {
        Snapshot copy;
        Listener target;
        synchronized (this) {
            weatherAt = fetchedAt;
            revision++;
            copy = new Snapshot(this);
            target = listener;
        }
        deliver(target, copy);
    }

    public void recordWeather(WeatherSnapshot snapshot, long attemptStartedAt,
                              long durationMs) {
        recordWeather(snapshot, attemptStartedAt, durationMs,
                System.currentTimeMillis());
    }

    /**
     * Registra el intento del tiempo y actualiza su salud:
     * <ul>
     *     <li>éxito si el estado es {@code FRESH} sin error, aunque sea
     *     parcial: limpia fallos y caída;</li>
     *     <li>fallo en cualquier otro caso, incluida una respuesta servida
     *     desde caché.</li>
     * </ul>
     */
    void recordWeather(WeatherSnapshot snapshot, long attemptStartedAt,
                       long durationMs, long now) {
        if (snapshot == null) return;

        Snapshot copy;
        Listener target;
        synchronized (this) {
            long at = Math.max(0L, now);
            weatherAt = snapshot.fetchedAt;
            weatherState = snapshot.state == null
                    ? WeatherSnapshot.State.EMPTY.name() : snapshot.state.name();
            weatherPartial = snapshot.partial;
            weatherError = snapshot.error == null ? "" : snapshot.error;
            weatherCityCount = snapshot.byCity == null ? 0 : snapshot.byCity.size();
            weatherAttemptAt = Math.max(0L, attemptStartedAt);
            weatherDurationMs = Math.max(0L, durationMs);

            boolean success = snapshot.state == WeatherSnapshot.State.FRESH
                    && weatherError.isEmpty();
            if (success) {
                weatherLastOkAt = at;
                weatherConsecutiveFailures = 0;
                weatherDownSince = 0L;
            } else {
                weatherConsecutiveFailures =
                        increment(weatherConsecutiveFailures);
                weatherLastErrorAt = at;
                if (weatherDownSince <= 0L
                        && weatherConsecutiveFailures
                        >= SourceHealthPolicy.DOWN_AFTER_FAILURES
                        && (weatherLastOkAt <= 0L
                        || at - weatherLastOkAt >= SourceHealthPolicy.DOWN_AFTER_MS)) {
                    weatherDownSince = at;
                }
            }

            revision++;
            copy = new Snapshot(this);
            target = listener;
        }
        deliver(target, copy);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(this);
    }

    /**
     * Aplica el estado de cada fuente del snapshot sin borrar las que no
     * aparezcan: una consulta parcial no debe perder la salud ya acumulada.
     */
    private void updateSourceHealth(Map<String, SourceStatus> statuses, long now) {
        if (statuses == null || statuses.isEmpty()) return;

        Map<String, SourceHealth> next = new LinkedHashMap<>(sourceHealth);
        for (Map.Entry<String, SourceStatus> entry : statuses.entrySet()) {
            SourceStatus status = entry.getValue();
            if (status == null) continue;

            String id = entry.getKey();
            if (id == null || id.isEmpty()) id = status.sourceId;
            if (id == null || id.isEmpty()) continue;

            SourceHealth previous = next.get(id);
            if (previous == null) previous = SourceHealth.initial(id);
            next.put(id, SourceHealthPolicy.update(previous, status, now));
        }
        sourceHealth = limitHealth(next);
    }

    /**
     * Acota el mapa a {@link DiagnosticsState#MAX_SOURCES} entradas. Si sobra
     * alguna se descartan las de último intento más antiguo, porque son las
     * que menos información aportan al diagnóstico.
     */
    private static Map<String, SourceHealth> limitHealth(
            Map<String, SourceHealth> health) {
        if (health.size() <= DiagnosticsState.MAX_SOURCES) {
            return Collections.unmodifiableMap(health);
        }

        List<Map.Entry<String, SourceHealth>> entries =
                new ArrayList<>(health.entrySet());
        entries.sort((left, right) -> Long.compare(
                lastAttempt(right.getValue()), lastAttempt(left.getValue())));

        Map<String, SourceHealth> trimmed = new LinkedHashMap<>();
        for (Map.Entry<String, SourceHealth> entry : entries) {
            if (trimmed.size() >= DiagnosticsState.MAX_SOURCES) break;
            trimmed.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(trimmed);
    }

    private static long lastAttempt(SourceHealth health) {
        return health == null ? 0L : health.lastAttemptAt;
    }

    private static int increment(int value) {
        return value == Integer.MAX_VALUE ? value : value + 1;
    }

    /**
     * Entrega la copia fuera del lock. Un listener defectuoso no debe romper
     * el refresco que provocó el aviso.
     */
    private static void deliver(Listener listener, Snapshot snapshot) {
        if (listener == null) return;
        try {
            listener.onChanged(snapshot);
        } catch (RuntimeException ignored) {
            // El diagnóstico nunca debe tumbar la actualización principal.
        }
    }

    /** Copia inmutable de un mapa, conservando el orden de inserción. */
    private static <K, V> Map<K, V> immutableCopy(Map<K, V> map) {
        if (map == null || map.isEmpty()) return Collections.emptyMap();
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    /**
     * Etiqueta relativa pura: "ahora", "hace X min", "hace X h" o "—" si no
     * hay referencia temporal.
     */
    public static String ageLabel(long thenMillis, long nowMillis) {
        if (thenMillis <= 0L) return "—";

        long delta = nowMillis - thenMillis;
        if (delta < 0L) delta = 0L;
        if (delta < MINUTE_MS) return "ahora";
        if (delta < HOUR_MS) return "hace " + (delta / MINUTE_MS) + " min";
        return "hace " + (delta / HOUR_MS) + " h";
    }

    /**
     * Copia inmutable del último estado conocido.
     *
     * {@code revision} crece con cada cambio registrado; sirve para distinguir
     * dos avisos seguidos. {@code sourceHealth} y {@code sources} son copias:
     * modificarlas no afecta al store ni a las copias ya entregadas.
     */
    public static final class Snapshot {

        public final long matchesAt;
        public final long matchesAttemptAt;
        public final String matchesDate;
        public final long matchesDurationMs;
        public final long weatherAt;
        public final boolean stale;
        public final int matchCount;
        public final Map<String, SourceStatus> sources;
        public final Map<String, SourceHealth> sourceHealth;
        public final long fetchedAt;

        public final String weatherState;
        public final boolean weatherPartial;
        public final String weatherError;
        public final int weatherCityCount;
        public final long weatherAttemptAt;
        public final long weatherDurationMs;
        public final int weatherConsecutiveFailures;
        public final long weatherLastOkAt;
        public final long weatherLastErrorAt;
        public final long weatherDownSince;

        public final long revision;

        /** Se construye siempre con el lock interno tomado. */
        private Snapshot(Diagnostics source) {
            this.matchesAt = source.matchesAt;
            this.matchesAttemptAt = source.matchesAttemptAt;
            this.matchesDate = source.matchesDate;
            this.matchesDurationMs = source.matchesDurationMs;
            this.weatherAt = source.weatherAt;
            this.stale = source.stale;
            this.matchCount = source.matchCount;
            this.sources = immutableCopy(source.sources);
            this.sourceHealth = immutableCopy(source.sourceHealth);
            this.fetchedAt = source.fetchedAt;

            this.weatherState = source.weatherState;
            this.weatherPartial = source.weatherPartial;
            this.weatherError = source.weatherError;
            this.weatherCityCount = source.weatherCityCount;
            this.weatherAttemptAt = source.weatherAttemptAt;
            this.weatherDurationMs = source.weatherDurationMs;
            this.weatherConsecutiveFailures = source.weatherConsecutiveFailures;
            this.weatherLastOkAt = source.weatherLastOkAt;
            this.weatherLastErrorAt = source.weatherLastErrorAt;
            this.weatherDownSince = source.weatherDownSince;

            this.revision = source.revision;
        }
    }
}
