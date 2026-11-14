package com.itriguero.homeclock;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Ciudad guardada en las preferencias. Inmutable y sin dependencias de Android
 * para poder probarla y serializarla en la JVM.
 */
final class City {

    static final String DEFAULT_NAME = "Tres Cantos";
    static final String DEFAULT_REGION = "Comunidad de Madrid";
    static final String DEFAULT_COUNTRY = "España";
    static final double DEFAULT_LAT = 40.6009;
    static final double DEFAULT_LON = -3.7086;

    private static final int MAX_NAME_LENGTH = 80;
    private static final double KEY_ROUNDING_THRESHOLD = 0.0005;
    private static final double EARTH_RADIUS_KM = 6371.0088;
    private static final double SAME_PLACE_KM = 25.0;

    enum Source {
        DEFAULT("default"),
        MANUAL("manual"),
        DETECTED("detected");

        final String json;

        Source(String json) {
            this.json = json;
        }

        static Source fromJson(String value) {
            if (value != null) {
                String normalized = value.trim().toLowerCase(Locale.US);
                for (Source source : values()) {
                    if (source.json.equals(normalized)) return source;
                }
            }
            return MANUAL;
        }
    }

    private static final City DEFAULT = new City(
            DEFAULT_NAME, DEFAULT_REGION, DEFAULT_COUNTRY,
            DEFAULT_LAT, DEFAULT_LON, Source.DEFAULT);

    final String name;
    final String region;
    final String country;
    final double lat;
    final double lon;
    final Source source;

    City(String name, String region, String country,
         double lat, double lon, Source source) {
        this.name = trim(name);
        this.region = trim(region);
        this.country = trim(country);
        this.lat = lat;
        this.lon = lon;
        this.source = source == null ? Source.MANUAL : source;
    }

    /**
     * Clave estable para comparar ciudades sin guardar índices. Redondea a tres
     * decimales (unos 100 m) y evita que un valor casi cero acabe como "-0.000".
     */
    String key() {
        return keyOf(lat, lon);
    }

    static String keyOf(double lat, double lon) {
        return String.format(Locale.US, "%.3f,%.3f",
                normalizeKeyCoord(lat), normalizeKeyCoord(lon));
    }

    boolean isValid() {
        String trimmedName = trim(name);
        return !trimmedName.isEmpty()
                && trimmedName.length() <= MAX_NAME_LENGTH
                && isFiniteInRange(lat, -90, 90)
                && isFiniteInRange(lon, -180, 180);
    }

    /** Texto para Ajustes; evita repetir región y país cuando coinciden. */
    String settingsLabel() {
        String detail;
        if (!region.isEmpty() && !country.isEmpty()) {
            detail = region.equalsIgnoreCase(country)
                    ? region
                    : region + ", " + country;
        } else if (!region.isEmpty()) {
            detail = region;
        } else {
            detail = country;
        }
        return detail.isEmpty() ? name : name + " · " + detail;
    }

    static City from(CitySearchClient.Result result, Source source) {
        if (result == null) return null;
        return new City(result.name, result.admin1, result.country,
                result.lat, result.lon, source);
    }

    /** Convierte la detección por IP en una ciudad más de la lista. */
    static City fromDetected(IpLocationClient.Result result) {
        if (result == null) return null;
        return new City(result.city, result.region, result.country,
                result.lat, result.lon, Source.DETECTED);
    }

    /**
     * Normaliza un nombre para comparar variantes con y sin tildes
     * ("San José" y "San Jose"). No se usa como clave persistida.
     */
    static String normalizeName(String name) {
        if (name == null) return "";
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String lower = decomposed.toLowerCase(Locale.ROOT);
        return lower.replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    /** Distancia ortodrómica en kilómetros (Haversine). */
    static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        // El redondeo puede dejar a fuera de [0,1] y producir NaN en asin.
        double clamped = Math.max(0.0, Math.min(1.0, a));
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(clamped));
    }

    /**
     * true si ambas entradas representan el mismo sitio. Solo se usa para la
     * detección por IP: la búsqueda manual sigue deduplicando por {@link #key()}.
     */
    boolean samePlace(City other) {
        if (other == null) return false;
        if (key().equals(other.key())) return true;

        String ownName = normalizeName(name);
        String otherName = normalizeName(other.name);
        if (ownName.isEmpty() || !ownName.equals(otherName)) return false;
        return distanceKm(lat, lon, other.lat, other.lon) <= SAME_PLACE_KM;
    }

    static City defaultCity() {
        return DEFAULT;
    }

    private static double normalizeKeyCoord(double value) {
        return Math.abs(value) < KEY_ROUNDING_THRESHOLD ? 0.0 : value;
    }

    private static boolean isFiniteInRange(double value, double min, double max) {
        return !Double.isNaN(value) && !Double.isInfinite(value)
                && value >= min && value <= max;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
