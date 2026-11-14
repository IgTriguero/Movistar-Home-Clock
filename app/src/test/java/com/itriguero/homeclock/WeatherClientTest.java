package com.itriguero.homeclock;

import org.json.JSONException;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WeatherClientTest {

    private static final long FETCHED_AT = 1_763_112_600_000L;

    private static final String BASE = "https://api.open-meteo.com/v1/forecast";
    private static final String CURRENT = "current=temperature_2m,relative_humidity_2m,"
            + "apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m";
    private static final String UNITS = "&timezone=auto"
            + "&temperature_unit=celsius&wind_speed_unit=kmh&precipitation_unit=mm";

    private static final City TRES_CANTOS = city("Tres Cantos", 40.6009, -3.7086);
    private static final City MADRID = city("Madrid", 40.4168, -3.7038);
    private static final City VIGO = city("Vigo", 42.23282, -8.72264);
    private static final City A_CORUNA = city("A Coruña", 43.3623, -8.4115);
    private static final City SEVILLA = city("Sevilla", 37.3891, -5.9845);
    private static final City VALENCIA = city("Valencia", 39.4699, -0.3763);
    private static final City BILBAO = city("Bilbao", 43.2630, -2.9350);
    private static final City MALAGA = city("Málaga", 36.7213, -4.4214);

    private static final List<City> EIGHT = List.of(
            TRES_CANTOS, MADRID, VIGO, A_CORUNA,
            SEVILLA, VALENCIA, BILBAO, MALAGA);

    @Test
    public void buildUrlForSingleCityIsExact() {
        assertEquals(
                BASE + "?latitude=40.60090&longitude=-3.70860&" + CURRENT + UNITS,
                WeatherClient.buildUrl(List.of(TRES_CANTOS)));
    }

    @Test
    public void buildUrlForTwoCitiesKeepsOrderAndSeparatesCoordinates() {
        assertEquals(
                BASE + "?latitude=40.60090,40.41680&longitude=-3.70860,-3.70380&"
                        + CURRENT + UNITS,
                WeatherClient.buildUrl(List.of(TRES_CANTOS, MADRID)));
    }

    @Test
    public void buildUrlForEightCitiesUsesAllCoordinates() {
        String latitude = "40.60090,40.41680,42.23282,43.36230,"
                + "37.38910,39.46990,43.26300,36.72130";
        String longitude = "-3.70860,-3.70380,-8.72264,-8.41150,"
                + "-5.98450,-0.37630,-2.93500,-4.42140";

        assertEquals(
                BASE + "?latitude=" + latitude + "&longitude=" + longitude
                        + "&" + CURRENT + UNITS,
                WeatherClient.buildUrl(EIGHT));
    }

    @Test
    public void buildUrlUsesDotDecimalRegardlessOfDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("es", "ES"));
            String url = WeatherClient.buildUrl(List.of(TRES_CANTOS));
            assertTrue(url.contains("latitude=40.60090&longitude=-3.70860"));
            assertFalse(url.contains("40,60090"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildUrlRejectsEmptyList() {
        WeatherClient.buildUrl(List.of());
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildUrlRejectsNullList() {
        WeatherClient.buildUrl(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildUrlRejectsMoreThanEightCities() {
        List<City> nine = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            nine.add(city("C" + i, 40.0 + i * 0.1, -3.0));
        }
        WeatherClient.buildUrl(nine);
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildUrlRejectsInvalidCity() {
        WeatherClient.buildUrl(List.of(city(" ", 40.0, -3.0)));
    }

    @Test
    public void parsesSingleObject() throws Exception {
        WeatherBatch batch = WeatherClient.parse(
                fixture("open_meteo_single.json"), List.of(MADRID), FETCHED_AT);

        assertEquals(FETCHED_AT, batch.fetchedAt);
        assertEquals(List.of(MADRID.key()), batch.requestedKeys);
        assertEquals(1, batch.byCity.size());

        Weather weather = batch.byCity.get(MADRID.key());
        assertNotNull(weather);
        assertEquals(12.3, weather.temp, 0.00001);
        assertEquals(10.8, weather.feels, 0.00001);
        assertEquals(78, weather.humidity);
        assertEquals(12.4, weather.wind, 0.00001);
        assertEquals(2, weather.weatherCode);
        assertEquals(1, weather.isDay);
        assertEquals("2026-11-14T10:00", weather.observedAt);
    }

    @Test
    public void parsesArrayOfEightByIndexWithoutUsingLocationId() throws Exception {
        WeatherBatch batch = WeatherClient.parse(
                fixture("open_meteo_multi.json"), EIGHT, FETCHED_AT);

        assertEquals(8, batch.byCity.size());
        assertEquals(EIGHT.size(), batch.requestedKeys.size());

        // La primera entrada del fixture no trae location_id.
        assertWeather(batch, TRES_CANTOS, 11.7, 9.1, 91, 17.9, 3, 1);
        // La segunda trae location_id y coordenadas distintas: manda el índice.
        assertWeather(batch, MADRID, 12.3, 10.8, 78, 12.4, 2, 1);
        assertWeather(batch, VIGO, 14.1, 13.2, 85, 9.7, 61, 0);
        assertWeather(batch, A_CORUNA, 13.0, 11.9, 88, 21.3, 63, 1);
        assertWeather(batch, SEVILLA, 19.5, 19.1, 60, 6.8, 0, 1);
        assertWeather(batch, VALENCIA, 18.2, 17.8, 65, 14.2, 1, 1);
        assertWeather(batch, BILBAO, 15.6, 14.9, 74, 11.5, 45, 1);
        assertWeather(batch, MALAGA, 17.4, 17.0, 68, 8.3, 80, 1);
    }

    @Test
    public void mapsEntriesByIndexNotByReturnedCoordinates() throws Exception {
        String json = "["
                + "{\"latitude\":1.0,\"longitude\":1.0,\"current\":{"
                + "\"temperature_2m\":30,\"apparent_temperature\":28,"
                + "\"relative_humidity_2m\":40,\"weather_code\":0,"
                + "\"wind_speed_10m\":7}},"
                + "{\"latitude\":2.0,\"longitude\":2.0,\"current\":{"
                + "\"temperature_2m\":40,\"apparent_temperature\":38,"
                + "\"relative_humidity_2m\":45,\"weather_code\":1,"
                + "\"wind_speed_10m\":8}}"
                + "]";

        WeatherBatch batch = WeatherClient.parse(
                json, List.of(VIGO, MADRID), FETCHED_AT);

        assertEquals(30.0, batch.byCity.get(VIGO.key()).temp, 0.00001);
        assertEquals(40.0, batch.byCity.get(MADRID.key()).temp, 0.00001);
    }

    @Test
    public void shortArrayLeavesMissingKeysAbsent() throws Exception {
        List<City> requested = List.of(TRES_CANTOS, MADRID, VIGO, A_CORUNA);

        WeatherBatch batch = WeatherClient.parse(
                fixture("open_meteo_partial.json"), requested, FETCHED_AT);

        assertEquals(2, batch.byCity.size());
        assertEquals(requested.size(), batch.requestedKeys.size());
        assertNotNull(batch.byCity.get(TRES_CANTOS.key()));
        assertNotNull(batch.byCity.get(MADRID.key()));
        assertNull(batch.byCity.get(VIGO.key()));
        assertNull(batch.byCity.get(A_CORUNA.key()));
    }

    @Test
    public void longArrayIgnoresExtraEntries() throws Exception {
        WeatherBatch batch = WeatherClient.parse(
                fixture("open_meteo_multi.json"), List.of(TRES_CANTOS), FETCHED_AT);

        assertEquals(1, batch.byCity.size());
        assertNotNull(batch.byCity.get(TRES_CANTOS.key()));
        assertEquals(11.7, batch.byCity.get(TRES_CANTOS.key()).temp, 0.00001);
    }

    @Test
    public void missingRequiredFieldDiscardsOnlyThatEntry() throws Exception {
        String json = "["
                + "{\"current\":{\"temperature_2m\":10,"
                + "\"apparent_temperature\":9,\"relative_humidity_2m\":50,"
                + "\"weather_code\":0,\"is_day\":1}},"
                + "{\"current\":{\"temperature_2m\":20,"
                + "\"apparent_temperature\":19,\"relative_humidity_2m\":60,"
                + "\"weather_code\":3,\"wind_speed_10m\":5}}"
                + "]";

        WeatherBatch batch = WeatherClient.parse(
                json, List.of(TRES_CANTOS, MADRID), FETCHED_AT);

        assertEquals(1, batch.byCity.size());
        assertNull(batch.byCity.get(TRES_CANTOS.key()));
        Weather madrid = batch.byCity.get(MADRID.key());
        assertNotNull(madrid);
        assertEquals(20.0, madrid.temp, 0.00001);
    }

    @Test
    public void missingIsDayDefaultsToDay() throws Exception {
        String json = "{\"current\":{\"temperature_2m\":10,"
                + "\"apparent_temperature\":9,\"relative_humidity_2m\":50,"
                + "\"weather_code\":0,\"wind_speed_10m\":5}}";

        WeatherBatch batch = WeatherClient.parse(json, List.of(MADRID), FETCHED_AT);

        assertEquals(1, batch.byCity.size());
        assertEquals(1, batch.byCity.get(MADRID.key()).isDay);
        assertEquals("", batch.byCity.get(MADRID.key()).observedAt);
    }

    @Test
    public void nonFiniteValueDiscardsEntry() throws Exception {
        String json = "{\"current\":{\"temperature_2m\":\"NaN\","
                + "\"apparent_temperature\":9,\"relative_humidity_2m\":50,"
                + "\"weather_code\":0,\"wind_speed_10m\":5}}";

        WeatherBatch batch = WeatherClient.parse(json, List.of(MADRID), FETCHED_AT);

        assertTrue(batch.isEmpty());
    }

    @Test(expected = JSONException.class)
    public void brokenJsonThrows() throws Exception {
        WeatherClient.parse("{esto no es json", List.of(MADRID), FETCHED_AT);
    }

    @Test(expected = JSONException.class)
    public void brokenArrayJsonThrows() throws Exception {
        WeatherClient.parse("[{\"current\":]", List.of(MADRID), FETCHED_AT);
    }

    @Test
    public void errorObjectThrowsWithReason() throws Exception {
        try {
            WeatherClient.parse(
                    fixture("open_meteo_error.json"), List.of(MADRID), FETCHED_AT);
            fail("Debía lanzar");
        } catch (WeatherClient.WeatherException e) {
            assertEquals(WeatherClient.WeatherException.Kind.INVALID, e.kind);
            assertTrue(e.getMessage().contains("Latitude"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseRejectsEmptyRequestedList() throws Exception {
        WeatherClient.parse("{}", List.of(), FETCHED_AT);
    }

    @Test
    public void http400UsesReasonAndInvalidKind() {
        WeatherClient.WeatherException error = WeatherClient.httpError(
                400, "{\"error\":true,\"reason\":\"Latitude must be in range\"}");

        assertEquals(WeatherClient.WeatherException.Kind.INVALID, error.kind);
        assertEquals(400, error.httpStatus);
        assertTrue(error.getMessage().contains("Latitude"));
    }

    @Test
    public void http500StaysAsHttpError() {
        WeatherClient.WeatherException error = WeatherClient.httpError(
                503, "{\"error\":true,\"reason\":\"Servicio no disponible\"}");

        assertEquals(WeatherClient.WeatherException.Kind.HTTP, error.kind);
        assertEquals(503, error.httpStatus);
        assertTrue(error.getMessage().contains("Servicio"));
    }

    private static void assertWeather(WeatherBatch batch, City city,
                                      double temp, double feels, int humidity,
                                      double wind, int code, int isDay) {
        Weather weather = batch.byCity.get(city.key());
        assertNotNull("Falta el tiempo de " + city.name, weather);
        assertEquals(temp, weather.temp, 0.00001);
        assertEquals(feels, weather.feels, 0.00001);
        assertEquals(humidity, weather.humidity);
        assertEquals(wind, weather.wind, 0.00001);
        assertEquals(code, weather.weatherCode);
        assertEquals(isDay, weather.isDay);
    }

    private static City city(String name, double lat, double lon) {
        return new City(name, "", "España", lat, lon, City.Source.MANUAL);
    }

    private static String fixture(String name) throws Exception {
        try (InputStream input = WeatherClientTest.class
                .getResourceAsStream("/fixtures/" + name)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
