package com.itriguero.homeclock;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.net.ssl.SSLException;

/**
 * Detección de la ciudad actual por geolocalización IP con ipwho.is.
 *
 * Sin Android ni claves: una sola petición HTTPS al arranque. El resultado se
 * valida como cualquier otra {@link City}, de modo que un proveedor que devuelva
 * datos raros nunca llega a la lista de ciudades.
 */
final class IpLocationClient {

    static final String ENDPOINT = "https://ipwho.is/?lang=es"
            + "&fields=success,message,city,region,country,country_code,latitude,longitude";

    private static final String USER_AGENT = "HomeClock/" + BuildConfig.VERSION_NAME;
    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_MS = 5_000;
    private static final int MAX_ERROR_BYTES = 2 * 1024;
    private static final int MAX_SUCCESS_BYTES = 32 * 1024;
    private static final int MAX_CITY_LENGTH = 80;
    private static final int MAX_PLACE_LENGTH = 80;
    private static final int MAX_PROVIDER_MESSAGE_LENGTH = 120;

    private IpLocationClient() {
    }

    /**
     * Consulta la ubicación aproximada de la IP pública. Cualquier fallo se
     * normaliza a {@link IpLocationException} para que la UI decida si reintenta.
     */
    static Result fetch() throws IpLocationException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "application/json");
            conn.setUseCaches(false);

            int code = conn.getResponseCode();
            if (code != 200) {
                // El código manda aunque el cuerpo de error llegue cortado.
                String errorBody = "";
                try {
                    errorBody = readBody(conn.getErrorStream(), MAX_ERROR_BYTES);
                } catch (IOException ignored) {
                    // Sin detalle del proveedor: queda "HTTP <código>".
                }
                throw httpError(code, errorBody);
            }

            String body = readBody(conn.getInputStream(), MAX_SUCCESS_BYTES);
            return parse(body);
        } catch (IpLocationException e) {
            throw e;
        } catch (SocketTimeoutException e) {
            throw new IpLocationException(IpLocationException.Kind.TIMEOUT, message(e), e);
        } catch (UnknownHostException | ConnectException | SSLException e) {
            throw new IpLocationException(IpLocationException.Kind.OFFLINE, message(e), e);
        } catch (SocketException e) {
            throw new IpLocationException(IpLocationException.Kind.OFFLINE, message(e), e);
        } catch (IOException e) {
            // Cortes de red, DNS o SSL caen aquí: se pueden reintentar.
            throw new IpLocationException(IpLocationException.Kind.OFFLINE, message(e), e);
        } catch (Exception e) {
            throw new IpLocationException(IpLocationException.Kind.PARSE, message(e), e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Parsea la respuesta de ipwho.is de forma tolerante: acepta ausencia de
     * {@code success}, coordenadas numéricas o en texto y regiones vacías. Solo
     * lanza error cuando el proveedor falla o los datos no sirven como ciudad.
     */
    static Result parse(String json) throws IpLocationException {
        String text = json == null ? "" : json.trim();
        if (text.isEmpty()) {
            throw new IpLocationException(
                    IpLocationException.Kind.PARSE, "Respuesta vacía");
        }

        JSONObject root;
        try {
            root = new JSONObject(text);
        } catch (JSONException e) {
            throw new IpLocationException(
                    IpLocationException.Kind.PARSE, message(e), e);
        }

        if (root.has("success") && !root.optBoolean("success", true)) {
            String providerMessage = trimmed(text(root.opt("message")),
                    MAX_PROVIDER_MESSAGE_LENGTH);
            if (providerMessage.isEmpty()) providerMessage = "Error del proveedor";
            throw new IpLocationException(
                    IpLocationException.Kind.PROVIDER, providerMessage,
                    0, providerMessage, null);
        }

        Object cityValue = root.opt("city");
        if (!(cityValue instanceof String)) {
            throw invalid("city ausente o no textual");
        }
        String city = collapseSpaces((String) cityValue);
        if (city.isEmpty() || city.length() > MAX_CITY_LENGTH) {
            throw invalid("city vacía o demasiado larga");
        }

        String region = place(root.opt("region"));
        String country = place(root.opt("country"));
        String countryCode = countryCode(root);

        double lat = coordinate(root.opt("latitude"));
        double lon = coordinate(root.opt("longitude"));
        if (!inRange(lat, -90, 90) || !inRange(lon, -180, 180)) {
            throw invalid("Coordenadas fuera de rango");
        }
        if (lat == 0 && lon == 0) {
            throw invalid("Coordenadas nulas");
        }

        City candidate = new City(city, region, country, lat, lon, City.Source.DETECTED);
        if (!candidate.isValid()) {
            throw invalid("Ciudad inválida");
        }
        return new Result(city, region, country, countryCode, lat, lon);
    }

    /**
     * Errores que merece la pena reintentar: red, timeout, 5xx, 408 y 425.
     * Un 429 necesita esperar y el resto de 4xx son datos malos.
     */
    static boolean isRetryable(IpLocationException error) {
        if (error == null) return false;
        switch (error.kind) {
            case OFFLINE:
            case TIMEOUT:
                return true;
            case HTTP:
                return (error.httpStatus >= 500 && error.httpStatus <= 599)
                        || error.httpStatus == 408
                        || error.httpStatus == 425;
            default:
                return false;
        }
    }

    /**
     * Normaliza un error HTTP para los tests y para {@link #fetch()}. El cuerpo
     * de ipwho.is suele ser {@code {"success":false,"message":"..."}}.
     */
    static IpLocationException httpError(int code, String body) {
        String providerMessage = "";
        if (body != null && !body.trim().isEmpty()) {
            try {
                JSONObject root = new JSONObject(body);
                providerMessage = trimmed(text(root.opt("message")),
                        MAX_PROVIDER_MESSAGE_LENGTH);
            } catch (JSONException ignored) {
                // Cuerpo no JSON (HTML de un proxy, por ejemplo): sin detalle.
            }
        }
        String message = providerMessage.isEmpty()
                ? "HTTP " + code
                : "HTTP " + code + ": " + providerMessage;
        return new IpLocationException(
                IpLocationException.Kind.HTTP, message, code, providerMessage, null);
    }

    private static String readBody(InputStream stream, int maxBytes) throws IOException {
        if (stream == null) return "";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = stream) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                int remaining = maxBytes - output.size();
                if (remaining <= 0) break;
                output.write(buffer, 0, Math.min(read, remaining));
                if (output.size() >= maxBytes) break;
            }
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String countryCode(JSONObject root) throws IpLocationException {
        if (!root.has("country_code") || root.isNull("country_code")) return "";
        Object value = root.opt("country_code");
        if (!(value instanceof String)) {
            throw invalid("country_code inválido");
        }
        String code = ((String) value).trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z]{2}")) {
            throw invalid("country_code inválido");
        }
        return code;
    }

    /** Región/país opcionales: si no son texto o son enormes, se descartan. */
    private static String place(Object value) {
        if (!(value instanceof String)) return "";
        String text = ((String) value).trim();
        return text.length() > MAX_PLACE_LENGTH ? "" : text;
    }

    /** Acepta números JSON y cadenas con punto o coma decimal. */
    private static double coordinate(Object value) {
        double result;
        if (value instanceof Number) {
            result = ((Number) value).doubleValue();
        } else if (value instanceof String) {
            String text = ((String) value).trim().replace(',', '.');
            if (text.isEmpty()) return Double.NaN;
            try {
                result = Double.parseDouble(text);
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        } else {
            return Double.NaN;
        }
        return isFinite(result) ? result : Double.NaN;
    }

    private static boolean inRange(double value, double min, double max) {
        return isFinite(value) && value >= min && value <= max;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String collapseSpaces(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String text(Object value) {
        return value instanceof String ? (String) value : "";
    }

    private static String trimmed(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private static String message(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isEmpty()) {
            return error.getClass().getSimpleName();
        }
        return message;
    }

    private static IpLocationException invalid(String detail) {
        return new IpLocationException(IpLocationException.Kind.INVALID, detail);
    }

    /** Ubicación devuelta por el proveedor, ya validada como ciudad. */
    static final class Result {
        final String city;
        final String region;
        final String country;
        final String countryCode;
        final double lat;
        final double lon;

        Result(String city, String region, String country, String countryCode,
               double lat, double lon) {
            this.city = city;
            this.region = region;
            this.country = country;
            this.countryCode = countryCode;
            this.lat = lat;
            this.lon = lon;
        }
    }

    /** Error tipado para que la UI distinga red, proveedor y datos inválidos. */
    static final class IpLocationException extends Exception {

        enum Kind { OFFLINE, TIMEOUT, HTTP, PARSE, INVALID, PROVIDER }

        final Kind kind;
        /** Código HTTP cuando {@link Kind#HTTP}; 0 en el resto. */
        final int httpStatus;
        /** Mensaje textual del proveedor, si lo hubo. */
        final String providerMessage;

        IpLocationException(Kind kind, String message) {
            this(kind, message, 0, "", null);
        }

        IpLocationException(Kind kind, String message, Throwable cause) {
            this(kind, message, 0, "", cause);
        }

        IpLocationException(Kind kind, String message, int httpStatus) {
            this(kind, message, httpStatus, "", null);
        }

        IpLocationException(Kind kind, String message, int httpStatus,
                            String providerMessage, Throwable cause) {
            super(message, cause);
            this.kind = kind == null ? Kind.INVALID : kind;
            this.httpStatus = httpStatus;
            this.providerMessage = providerMessage == null ? "" : providerMessage;
        }
    }
}
