package com.itriguero.homeclock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cliente del endpoint de tiempo de Open-Meteo para varias ciudades.
 *
 * La respuesta de varias coordenadas es un array; la de una sola es un objeto.
 * El emparejamiento se hace por índice con la lista solicitada, nunca por
 * {@code location_id} ni por las coordenadas devueltas, porque Open-Meteo las
 * ajusta a la rejilla del modelo.
 */
final class WeatherClient {

    private static final String BASE = "https://api.open-meteo.com/v1/forecast";
    private static final String USER_AGENT = "HomeClock/1.0";
    private static final int TIMEOUT_MS = 10_000;

    private static final String CURRENT_FIELDS =
            "temperature_2m,relative_humidity_2m,apparent_temperature,"
                    + "is_day,precipitation,weather_code,wind_speed_10m";

    private WeatherClient() {
    }

    /**
     * Construye la URL con una entrada de latitud/longitud por ciudad. Se usa
     * {@link Locale#US} para que el separador decimal sea siempre un punto.
     */
    static String buildUrl(List<City> cities) {
        if (cities == null || cities.isEmpty()) {
            throw new IllegalArgumentException("Lista de ciudades vacía");
        }
        if (cities.size() > CityList.MAX_CITIES) {
            throw new IllegalArgumentException(
                    "Demasiadas ciudades: " + cities.size());
        }
        for (City city : cities) {
            if (city == null || !city.isValid()) {
                throw new IllegalArgumentException("Ciudad inválida");
            }
        }

        StringBuilder latitude = new StringBuilder();
        StringBuilder longitude = new StringBuilder();
        for (int i = 0; i < cities.size(); i++) {
            if (i > 0) {
                latitude.append(',');
                longitude.append(',');
            }
            City city = cities.get(i);
            latitude.append(String.format(Locale.US, "%.5f", city.lat));
            longitude.append(String.format(Locale.US, "%.5f", city.lon));
        }

        return BASE
                + "?latitude=" + latitude
                + "&longitude=" + longitude
                + "&current=" + CURRENT_FIELDS
                + "&timezone=auto"
                + "&temperature_unit=celsius"
                + "&wind_speed_unit=kmh"
                + "&precipitation_unit=mm";
    }

    /**
     * Parsea la respuesta y asocia cada entrada con la ciudad solicitada en la
     * misma posición. Una entrada inválida se descarta sin invalidar el resto.
     */
    static WeatherBatch parse(String json, List<City> requested, long fetchedAt)
            throws Exception {
        if (requested == null || requested.isEmpty()) {
            throw new IllegalArgumentException("Lista de ciudades vacía");
        }

        String text = json == null ? "" : json.trim();
        if (text.isEmpty()) {
            throw new WeatherException(WeatherException.Kind.PARSE, "Respuesta vacía");
        }

        List<JSONObject> items = new ArrayList<>();
        char first = text.charAt(0);
        if (first == '[') {
            JSONArray array = new JSONArray(text);
            for (int i = 0; i < array.length(); i++) {
                items.add(array.optJSONObject(i));
            }
        } else if (first == '{') {
            JSONObject root = new JSONObject(text);
            if (root.optBoolean("error", false)) {
                String reason = root.optString("reason", "").trim();
                throw new WeatherException(WeatherException.Kind.INVALID,
                        reason.isEmpty() ? "Error de Open-Meteo" : reason);
            }
            items.add(root);
        } else {
            throw new WeatherException(WeatherException.Kind.PARSE,
                    "Respuesta no reconocida");
        }

        Map<String, Weather> byCity = new LinkedHashMap<>();
        int count = Math.min(items.size(), requested.size());
        for (int i = 0; i < count; i++) {
            City city = requested.get(i);
            if (city == null || !city.isValid()) continue;
            Weather weather = weatherFrom(items.get(i));
            if (weather != null) byCity.put(city.key(), weather);
        }

        List<String> keys = new ArrayList<>();
        for (City city : requested) {
            if (city != null) keys.add(city.key());
        }
        return new WeatherBatch(byCity, keys, fetchedAt);
    }

    /**
     * Descarga el tiempo de las ciudades indicadas.
     *
     * Los fallos se normalizan a {@link WeatherException} para que el
     * repositorio pueda clasificarlos sin mirar mensajes de texto.
     */
    static WeatherBatch fetch(List<City> cities) throws WeatherException {
        String url;
        try {
            url = buildUrl(cities);
        } catch (IllegalArgumentException e) {
            throw new WeatherException(WeatherException.Kind.INVALID, e.getMessage(), e);
        }

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", USER_AGENT);

            int code = conn.getResponseCode();
            if (code != 200) {
                throw httpError(code, readErrorBody(conn));
            }

            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
            }

            long fetchedAt = System.currentTimeMillis();
            try {
                return parse(body.toString(), cities, fetchedAt);
            } catch (WeatherException e) {
                throw e;
            } catch (Exception e) {
                throw new WeatherException(
                        WeatherException.Kind.PARSE, message(e), e);
            }
        } catch (WeatherException e) {
            throw e;
        } catch (IOException e) {
            throw new WeatherException(WeatherException.Kind.OFFLINE, message(e), e);
        } catch (Exception e) {
            throw new WeatherException(WeatherException.Kind.PARSE, message(e), e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Lee el cuerpo de error de Open-Meteo. Un 400/422 suele traer
     * {@code {"error":true,"reason":"..."}}; se conserva el motivo para el
     * diagnóstico y se clasifica como INVALID, no como HTTP genérico.
     */
    static WeatherException httpError(int code, String body) {
        String reason = "";
        if (body != null && !body.trim().isEmpty()) {
            try {
                JSONObject root = new JSONObject(body);
                if (root.optBoolean("error", false)) {
                    reason = root.optString("reason", "").trim();
                }
            } catch (Exception ignored) {
                // Cuerpo no JSON: se usa solo si el código es de validación.
            }
            if (reason.isEmpty() && (code == 400 || code == 422)) {
                reason = body.trim();
            }
        }
        if (reason.length() > 200) reason = reason.substring(0, 200);

        WeatherException.Kind kind = (code == 400 || code == 422)
                ? WeatherException.Kind.INVALID
                : WeatherException.Kind.HTTP;
        String message = reason.isEmpty()
                ? "HTTP " + code : "HTTP " + code + ": " + reason;
        return new WeatherException(kind, message, code);
    }

    private static String readErrorBody(HttpURLConnection conn) {
        if (conn == null) return "";
        java.io.InputStream stream = conn.getErrorStream();
        if (stream == null) return "";
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (body.length() + line.length() > 8192) break;
                body.append(line);
            }
        } catch (Exception ignored) {
            return body.toString();
        }
        return body.toString();
    }

    private static Weather weatherFrom(JSONObject item) {
        if (item == null) return null;
        JSONObject current = item.optJSONObject("current");
        if (current == null) return null;

        double temp = number(current, "temperature_2m");
        double feels = number(current, "apparent_temperature");
        double wind = number(current, "wind_speed_10m");
        double humidity = number(current, "relative_humidity_2m");
        double code = number(current, "weather_code");
        if (!isFinite(temp) || !isFinite(feels) || !isFinite(wind)
                || !isFinite(humidity) || !isFinite(code)) {
            return null;
        }

        if (humidity < Weather.MIN_HUMIDITY || humidity > Weather.MAX_HUMIDITY
                || code < Weather.MIN_WEATHER_CODE || code > Weather.MAX_WEATHER_CODE) {
            return null;
        }

        double isDayValue = number(current, "is_day");
        int isDay = Double.isNaN(isDayValue) ? 1 : (isDayValue == 0 ? 0 : 1);
        String observedAt = current.isNull("time")
                ? "" : current.optString("time", "");

        try {
            return new Weather(temp, feels, wind,
                    (int) humidity, (int) code, isDay, observedAt);
        } catch (IllegalArgumentException e) {
            // Valores fuera de rango: se descarta solo esta ciudad.
            return null;
        }
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

    private static String message(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isEmpty()) {
            return error.getClass().getSimpleName();
        }
        return message;
    }

    /** Error tipado del cliente; el repositorio lo usa para clasificar fallos. */
    static final class WeatherException extends Exception {

        enum Kind { OFFLINE, HTTP, PARSE, INVALID }

        final Kind kind;
        /** Código HTTP cuando {@link Kind#HTTP}; 0 en el resto. */
        final int httpStatus;

        WeatherException(Kind kind, String message) {
            this(kind, message, 0, null);
        }

        WeatherException(Kind kind, String message, int httpStatus) {
            this(kind, message, httpStatus, null);
        }

        WeatherException(Kind kind, String message, Throwable cause) {
            this(kind, message, 0, cause);
        }

        WeatherException(Kind kind, String message, int httpStatus, Throwable cause) {
            super(message, cause);
            this.kind = kind == null ? Kind.INVALID : kind;
            this.httpStatus = httpStatus;
        }
    }
}
