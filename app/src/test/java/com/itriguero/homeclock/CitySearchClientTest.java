package com.itriguero.homeclock;

import org.json.JSONException;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CitySearchClientTest {

    @Test
    public void parsesResultsFromFixture() throws Exception {
        List<CitySearchClient.Result> results = CitySearchClient.parse(fixture());

        assertEquals(2, results.size());

        CitySearchClient.Result first = results.get(0);
        assertEquals("Vigo", first.name);
        assertEquals("Galicia", first.admin1);
        assertEquals("España", first.country);
        assertEquals(42.23282, first.lat, 0.00001);
        assertEquals(-8.72264, first.lon, 0.00001);

        CitySearchClient.Result second = results.get(1);
        assertEquals("Vigo", second.name);
        assertEquals("Galicia", second.admin1);
        assertEquals("España", second.country);
        assertEquals(42.2333, second.lat, 0.00001);
    }

    @Test
    public void missingResultsReturnsEmptyList() throws Exception {
        assertTrue(CitySearchClient.parse("{\"generationtime_ms\":0.8}").isEmpty());
    }

    @Test
    public void ignoresResultsWithoutCoordinates() throws Exception {
        List<CitySearchClient.Result> results = CitySearchClient.parse(
                "{\"results\":[{\"name\":\"Sin coordenadas\"},"
                        + "{\"name\":\"Vigo\",\"latitude\":42.2,\"longitude\":-8.7}]}");

        assertEquals(1, results.size());
        assertEquals("Vigo", results.get(0).name);
    }

    @Test(expected = JSONException.class)
    public void brokenJsonThrows() throws Exception {
        CitySearchClient.parse("{esto no es json");
    }

    @Test
    public void buildUrlEncodesQuery() {
        assertEquals(
                "https://geocoding-api.open-meteo.com/v1/search"
                        + "?name=A+Coru%C3%B1a&count=8&language=es&format=json",
                CitySearchClient.buildUrl("A Coruña"));
    }

    private static String fixture() throws Exception {
        try (InputStream input = CitySearchClientTest.class
                .getResourceAsStream("/fixtures/geocoding_vigo.json")) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
