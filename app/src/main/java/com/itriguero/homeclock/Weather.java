package com.itriguero.homeclock;

/**
 * Tiempo actual de una ciudad. Inmutable y sin dependencias de Android para
 * poder probarlo y cachearlo en la JVM.
 *
 * La validación es deliberadamente estricta: una entrada incoherente se
 * descarta en el parser en lugar de llegar a la UI.
 */
final class Weather {

    static final int MIN_HUMIDITY = 0;
    static final int MAX_HUMIDITY = 100;
    static final int MIN_WEATHER_CODE = 0;
    static final int MAX_WEATHER_CODE = 99;

    final double temp;
    final double feels;
    final double wind;
    final int humidity;
    final int weatherCode;
    /** 1 = día, 0 = noche. Cualquier otro valor se normaliza. */
    final int isDay;
    final String observedAt;

    Weather(double temp, double feels, double wind,
            int humidity, int weatherCode, int isDay, String observedAt) {
        if (!isFinite(temp)) throw new IllegalArgumentException("temp no finita");
        if (!isFinite(feels)) throw new IllegalArgumentException("feels no finita");
        if (!isFinite(wind)) throw new IllegalArgumentException("wind no finita");
        if (humidity < MIN_HUMIDITY || humidity > MAX_HUMIDITY) {
            throw new IllegalArgumentException("humidity fuera de rango: " + humidity);
        }
        if (weatherCode < MIN_WEATHER_CODE || weatherCode > MAX_WEATHER_CODE) {
            throw new IllegalArgumentException("weatherCode inválido: " + weatherCode);
        }

        this.temp = temp;
        this.feels = feels;
        this.wind = wind;
        this.humidity = humidity;
        this.weatherCode = weatherCode;
        this.isDay = isDay == 0 ? 0 : 1;
        this.observedAt = observedAt == null ? "" : observedAt;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
