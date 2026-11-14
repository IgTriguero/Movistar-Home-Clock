package com.itriguero.homeclock;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Cliente HTTP del detalle de un partido de 365Scores.
 *
 * Hace dos peticiones como máximo: {@code /web/game/} (obligatoria) y
 * {@code /web/game/stats/} (solo en vivo/finalizado o si el partido declara
 * estadísticas). Cualquier fallo se traduce a {@link SourceResult}; nunca se
 * propaga una excepción a la UI.
 */
final class MatchDetailClient {

    private static final String GAME_BASE = "https://webws.365scores.com/web/game/";
    private static final String STATS_BASE = "https://webws.365scores.com/web/game/stats/";
    private static final String COMMON_PARAMS =
            "appTypeId=5&langId=14&timezoneName=Europe/Madrid&userCountryId=2";

    private static final int CONNECT_TIMEOUT_MS = 6000;
    private static final int READ_TIMEOUT_MS = 8000;
    private static final int MAX_BODY_BYTES = 1024 * 1024;

    private MatchDetailClient() {
    }

    static SourceResult<MatchDetail> fetch(Match match, boolean includeStats) {
        long start = System.nanoTime();
        if (!canFetch(match)) {
            return SourceResult.empty(System.currentTimeMillis(), 0L);
        }

        try {
            JSONObject gameRoot = readJson(buildGameUrl(match.id));
            if (MatchDetailParser.parse(gameRoot, null, match) == null) {
                return SourceResult.empty(System.currentTimeMillis(), elapsedMs(start));
            }

            JSONObject statsRoot = null;
            if (includeStats && shouldLoadStats(match, gameRoot)) {
                try {
                    statsRoot = readJson(buildStatsUrl(match.id));
                } catch (Exception ignored) {
                    // Las estadísticas son un extra: el detalle del partido sigue siendo válido.
                    statsRoot = null;
                }
            }

            long fetchedAt = System.currentTimeMillis();
            return buildResult(match, gameRoot, statsRoot, includeStats,
                    fetchedAt, elapsedMs(start));
        } catch (HttpStatusException e) {
            return httpFailure(e.code, start);
        } catch (Exception e) {
            return exceptionResult(e, start);
        }
    }

    static String buildGameUrl(long gameId) {
        return GAME_BASE + "?" + COMMON_PARAMS + "&gameId=" + gameId;
    }

    static String buildStatsUrl(long gameId) {
        return STATS_BASE + "?" + COMMON_PARAMS + "&games=" + gameId;
    }

    /**
     * Construye el resultado a partir de respuestas ya descargadas. Se expone
     * al paquete para probar el parser y la política de estadísticas sin red.
     */
    static SourceResult<MatchDetail> buildResult(Match match, JSONObject gameRoot,
                                                 JSONObject statsRoot,
                                                 boolean includeStats,
                                                 long fetchedAt, long durationMs) {
        if (!canFetch(match) || gameRoot == null) {
            return SourceResult.empty(fetchedAt, durationMs);
        }

        MatchDetail detail = MatchDetailParser.parse(gameRoot, null, match);
        if (detail == null) return SourceResult.empty(fetchedAt, durationMs);

        if (includeStats && shouldLoadStats(match, gameRoot)) {
            if (statsRoot == null) {
                detail = detail.withStatsError("Sin estadísticas");
            } else {
                MatchDetail parsed = MatchDetailParser.parse(gameRoot, statsRoot, match);
                if (parsed != null) detail = parsed;
            }
        }

        return SourceResult.ok(detail.withFetch(fetchedAt, false),
                fetchedAt, durationMs);
    }

    /**
     * Fallo HTTP del endpoint principal: 404 se considera vacío y el resto
     * error de HTTP (incluidos 429 y 5xx).
     */
    static SourceResult<MatchDetail> httpFailure(int code, long startNanos) {
        long fetchedAt = System.currentTimeMillis();
        long durationMs = elapsedMs(startNanos);
        if (code == 404) return SourceResult.empty(fetchedAt, durationMs);
        return SourceResult.error(SourceState.HTTP_ERROR, "HTTP " + code,
                fetchedAt, durationMs);
    }

    /**
     * Clasifica una excepción de red: timeout, JSON roto, IO (offline) o error
     * genérico. El orden importa: {@link SocketTimeoutException} es un IO.
     */
    static SourceResult<MatchDetail> exceptionResult(Exception exception, long startNanos) {
        long fetchedAt = System.currentTimeMillis();
        long durationMs = elapsedMs(startNanos);
        Exception safe = exception == null
                ? new IllegalStateException("Error desconocido") : exception;

        if (hasCause(safe, SocketTimeoutException.class)) {
            return SourceResult.error(SourceState.TIMEOUT, message(safe),
                    fetchedAt, durationMs);
        }
        if (hasCause(safe, JSONException.class)) {
            return SourceResult.error(SourceState.PARSE_ERROR, message(safe),
                    fetchedAt, durationMs);
        }
        if (hasCause(safe, IOException.class)) {
            return SourceResult.error(SourceState.OFFLINE, message(safe),
                    fetchedAt, durationMs);
        }
        return SourceResult.error(SourceState.ERROR, message(safe),
                fetchedAt, durationMs);
    }

    static boolean canFetch(Match match) {
        return match != null
                && Scores365Source.ID.equals(match.sourceId)
                && match.id > 0;
    }

    static boolean shouldLoadStats(Match match, JSONObject gameRoot) {
        if (match == null || gameRoot == null) return false;
        if (match.isInPlay() || match.isFinished()) return true;

        JSONObject game = gameRoot.optJSONObject("game");
        if (game == null && gameRoot.has("homeCompetitor")) game = gameRoot;
        return game != null && game.optBoolean("hasStats", false);
    }

    private static JSONObject readJson(String url)
            throws IOException, JSONException, HttpStatusException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent",
                    "HomeClock/" + BuildConfig.VERSION_NAME);
            connection.setRequestProperty("Accept-Language", "es-ES,es;q=0.9");
            connection.setRequestProperty("Accept", "application/json");

            int code = connection.getResponseCode();
            if (code != 200) throw new HttpStatusException(code);

            try (InputStream input = connection.getInputStream()) {
                return new JSONObject(readBody(input));
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String readBody(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BODY_BYTES) {
                throw new IOException("Respuesta demasiado grande");
            }
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 10) {
            if (type.isInstance(current)) return true;
            current = current.getCause();
            depth++;
        }
        return false;
    }

    private static String message(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }

    private static long elapsedMs(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    /** Código HTTP distinto de 200; se traduce fuera del método de lectura. */
    private static final class HttpStatusException extends Exception {
        final int code;

        HttpStatusException(int code) {
            super("HTTP " + code);
            this.code = code;
        }
    }
}
