package com.itriguero.homeclock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caché negativa en memoria para URLs de escudos que han fallado. Es lógica
 * pura (sin Android) y recibe el instante actual en cada operación para poder
 * probar la expiración sin depender del reloj del sistema.
 *
 * <p>Una entrada caduca cuando han pasado {@code ttlMs} milisegundos desde el
 * último fallo. El límite es exclusivo: en el instante exacto de expiración la
 * URL deja de estar en fallo. Un TTL de cero desactiva la caché negativa.
 */
public final class LogoNegativeCache {

    /** Diez minutos: suficiente para no martillear una URL rota sin ocultarla para siempre. */
    public static final long DEFAULT_TTL_MS = 10 * 60 * 1000L;

    private final long ttlMs;
    private final ConcurrentHashMap<String, Long> failedAt = new ConcurrentHashMap<>();

    public LogoNegativeCache() {
        this(DEFAULT_TTL_MS);
    }

    public LogoNegativeCache(long ttlMs) {
        if (ttlMs < 0) {
            throw new IllegalArgumentException("TTL negativo: " + ttlMs);
        }
        this.ttlMs = ttlMs;
    }

    public long ttlMs() {
        return ttlMs;
    }

    /**
     * Registra un fallo en {@code now}. Aprovecha para limpiar las entradas
     * caducadas, así la caché no crece con URLs que ya no están en fallo.
     */
    public void markFailed(String url, long now) {
        if (ttlMs == 0 || url == null || url.isEmpty()) return;
        failedAt.put(url, now);
        pruneExpired(now);
    }

    /** Indica si la URL sigue en fallo en el instante {@code now}. */
    public boolean isFailed(String url, long now) {
        if (url == null || url.isEmpty()) return false;

        Long at = failedAt.get(url);
        if (at == null) return false;
        if (!isExpired(at, now)) return true;

        // Solo borra si nadie ha registrado un fallo más reciente para la URL.
        failedAt.remove(url, at);
        return false;
    }

    /** Olvida todos los fallos. */
    public void clear() {
        failedAt.clear();
    }

    /**
     * Elimina las entradas caducadas y devuelve cuántas ha quitado.
     *
     * @return número de entradas eliminadas
     */
    public int pruneExpired(long now) {
        int removed = 0;
        for (Map.Entry<String, Long> entry : failedAt.entrySet()) {
            Long at = entry.getValue();
            if (at != null && isExpired(at, now)
                    && failedAt.remove(entry.getKey(), at)) {
                removed++;
            }
        }
        return removed;
    }

    /** Número de entradas actuales, incluidas las caducadas aún no limpiadas. */
    public int size() {
        return failedAt.size();
    }

    private boolean isExpired(long at, long now) {
        // Si el reloj retrocede, se mantiene el fallo: es la opción conservadora.
        if (now < at) return false;
        return now - at >= ttlMs;
    }
}
