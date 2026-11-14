package com.itriguero.homeclock;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class IpLocationClientTest {

    @Test
    public void parsesRealResponseFromFixture() throws Exception {
        IpLocationClient.Result result =
                IpLocationClient.parse(fixture("ipwhois_tres_cantos.json"));

        assertEquals("Tres Cantos", result.city);
        assertEquals("Comunidad de Madrid", result.region);
        assertEquals("España", result.country);
        assertEquals("ES", result.countryCode);
        assertEquals(40.601, result.lat, 0.000001);
        assertEquals(-3.708, result.lon, 0.000001);
    }

    @Test
    public void parsesStringCoordinatesWithComma() throws Exception {
        IpLocationClient.Result result =
                IpLocationClient.parse(fixture("ipwhois_strings.json"));

        assertEquals("Vigo", result.city);
        assertEquals("Galicia", result.region);
        assertEquals("ES", result.countryCode);
        assertEquals(42.23282, result.lat, 0.00001);
        assertEquals(-8.72264, result.lon, 0.00001);
    }

    @Test
    public void missingRegionAndCountryAreEmpty() throws Exception {
        IpLocationClient.Result result =
                IpLocationClient.parse(fixture("ipwhois_no_region.json"));

        assertEquals("Ceuta", result.city);
        assertEquals("", result.region);
        assertEquals("España", result.country);
        assertEquals(35.8894, result.lat, 0.00001);
        assertEquals(-5.3213, result.lon, 0.00001);
    }

    @Test
    public void nonTextRegionAndCountryBecomeEmpty() throws Exception {
        IpLocationClient.Result result = IpLocationClient.parse(
                "{\"city\":\"Vigo\",\"region\":42,\"country\":null,"
                        + "\"latitude\":42.2,\"longitude\":-8.7}");

        assertEquals("", result.region);
        assertEquals("", result.country);
    }

    @Test
    public void tooLongRegionBecomesEmpty() throws Exception {
        IpLocationClient.Result result = IpLocationClient.parse(
                "{\"city\":\"Vigo\",\"region\":\"" + "r".repeat(81) + "\","
                        + "\"country\":\"España\","
                        + "\"latitude\":42.2,\"longitude\":-8.7}");

        assertEquals("", result.region);
        assertEquals("España", result.country);
    }

    @Test
    public void cityCollapsesRepeatedSpaces() throws Exception {
        IpLocationClient.Result result = IpLocationClient.parse(
                "{\"city\":\"  San   José \",\"latitude\":40.0,\"longitude\":-3.0}");

        assertEquals("San José", result.city);
    }

    @Test
    public void cityMustBePresentAndTextual() throws Exception {
        assertInvalid("{\"city\":\"\",\"latitude\":40.0,\"longitude\":-3.0}");
        assertInvalid("{\"city\":\"   \",\"latitude\":40.0,\"longitude\":-3.0}");
        assertInvalid("{\"latitude\":40.0,\"longitude\":-3.0}");
        assertInvalid("{\"city\":42,\"latitude\":40.0,\"longitude\":-3.0}");
        assertInvalid("{\"city\":\"" + "a".repeat(81) + "\","
                + "\"latitude\":40.0,\"longitude\":-3.0}");
    }

    @Test
    public void coordinatesMustBeInRange() throws Exception {
        assertInvalid("{\"city\":\"Norte\",\"latitude\":90.1,\"longitude\":0}");
        assertInvalid("{\"city\":\"Sur\",\"latitude\":-90.1,\"longitude\":0}");
        assertInvalid("{\"city\":\"Este\",\"latitude\":0,\"longitude\":180.1}");
        assertInvalid("{\"city\":\"Oeste\",\"latitude\":0,\"longitude\":-180.1}");
        assertInvalid(fixture("ipwhois_bad_coords.json"));
    }

    @Test
    public void nonFiniteOrMissingCoordinatesAreRejected() throws Exception {
        assertInvalid("{\"city\":\"Vigo\",\"latitude\":\"NaN\",\"longitude\":-8.7}");
        assertInvalid("{\"city\":\"Vigo\",\"latitude\":40.0,"
                + "\"longitude\":\"Infinity\"}");
        assertInvalid("{\"city\":\"Vigo\",\"latitude\":40.0}");
        assertInvalid("{\"city\":\"Vigo\",\"latitude\":\"\",\"longitude\":-8.7}");
    }

    @Test
    public void nullIslandIsRejected() throws Exception {
        assertInvalid(fixture("ipwhois_null_island.json"));
    }

    @Test
    public void countryCodeMustBeTwoAsciiLettersWhenPresent() throws Exception {
        assertInvalid("{\"city\":\"Vigo\",\"country_code\":\"ESP\","
                + "\"latitude\":40.0,\"longitude\":-3.0}");
        assertInvalid("{\"city\":\"Vigo\",\"country_code\":\"E1\","
                + "\"latitude\":40.0,\"longitude\":-3.0}");
        assertInvalid("{\"city\":\"Vigo\",\"country_code\":42,"
                + "\"latitude\":40.0,\"longitude\":-3.0}");

        IpLocationClient.Result result = IpLocationClient.parse(
                "{\"city\":\"Londres\",\"country_code\":\"gb\","
                        + "\"latitude\":51.5,\"longitude\":-0.12}");
        assertEquals("GB", result.countryCode);
    }

    @Test
    public void missingSuccessIsAccepted() throws Exception {
        IpLocationClient.Result result = IpLocationClient.parse(
                "{\"city\":\"Vigo\",\"latitude\":42.2,\"longitude\":-8.7}");

        assertEquals("Vigo", result.city);
    }

    @Test
    public void providerErrorUsesItsMessage() throws Exception {
        try {
            IpLocationClient.parse(fixture("ipwhois_reserved.json"));
            fail("Debía lanzar");
        } catch (IpLocationClient.IpLocationException e) {
            assertEquals(IpLocationClient.IpLocationException.Kind.PROVIDER, e.kind);
            assertEquals("Reserved range", e.getMessage());
            assertEquals("Reserved range", e.providerMessage);
            assertFalse(IpLocationClient.isRetryable(e));
        }
    }

    @Test
    public void providerErrorWithoutMessageHasFallback() throws Exception {
        try {
            IpLocationClient.parse("{\"success\":false}");
            fail("Debía lanzar");
        } catch (IpLocationClient.IpLocationException e) {
            assertEquals(IpLocationClient.IpLocationException.Kind.PROVIDER, e.kind);
            assertEquals("Error del proveedor", e.getMessage());
        }
    }

    @Test
    public void htmlBodyIsParseError() throws Exception {
        assertKind(IpLocationClient.IpLocationException.Kind.PARSE,
                fixture("ipwhois_html.html"));
    }

    @Test
    public void arrayBodyIsParseError() throws Exception {
        assertKind(IpLocationClient.IpLocationException.Kind.PARSE,
                "[{\"city\":\"Vigo\",\"latitude\":42.2,\"longitude\":-8.7}]");
    }

    @Test
    public void brokenOrEmptyJsonIsParseError() throws Exception {
        assertKind(IpLocationClient.IpLocationException.Kind.PARSE,
                "{esto no es json");
        assertKind(IpLocationClient.IpLocationException.Kind.PARSE, "");
        assertKind(IpLocationClient.IpLocationException.Kind.PARSE, null);
        assertKind(IpLocationClient.IpLocationException.Kind.PARSE,
                fixture("ipwhois_empty.json"));
    }

    @Test
    public void httpErrorExtractsProviderMessage() {
        IpLocationClient.IpLocationException error = IpLocationClient.httpError(
                429, "{\"success\":false,\"message\":\"Too Many Requests\"}");

        assertEquals(IpLocationClient.IpLocationException.Kind.HTTP, error.kind);
        assertEquals(429, error.httpStatus);
        assertEquals("HTTP 429: Too Many Requests", error.getMessage());
        assertEquals("Too Many Requests", error.providerMessage);
        assertFalse(IpLocationClient.isRetryable(error));
    }

    @Test
    public void httpErrorWithoutJsonKeepsStatus() {
        IpLocationClient.IpLocationException error = IpLocationClient.httpError(
                503, "<html>Servicio no disponible</html>");

        assertEquals(IpLocationClient.IpLocationException.Kind.HTTP, error.kind);
        assertEquals(503, error.httpStatus);
        assertEquals("HTTP 503", error.getMessage());
        assertTrue(IpLocationClient.isRetryable(error));
    }

    @Test
    public void retryableClassification() {
        assertTrue(IpLocationClient.isRetryable(
                new IpLocationClient.IpLocationException(
                        IpLocationClient.IpLocationException.Kind.OFFLINE, "sin red")));
        assertTrue(IpLocationClient.isRetryable(
                new IpLocationClient.IpLocationException(
                        IpLocationClient.IpLocationException.Kind.TIMEOUT, "timeout")));
        assertTrue(IpLocationClient.isRetryable(IpLocationClient.httpError(500, "")));
        assertTrue(IpLocationClient.isRetryable(IpLocationClient.httpError(599, "")));
        assertTrue(IpLocationClient.isRetryable(IpLocationClient.httpError(408, "")));
        assertTrue(IpLocationClient.isRetryable(IpLocationClient.httpError(425, "")));

        assertFalse(IpLocationClient.isRetryable(IpLocationClient.httpError(429, "")));
        assertFalse(IpLocationClient.isRetryable(IpLocationClient.httpError(400, "")));
        assertFalse(IpLocationClient.isRetryable(IpLocationClient.httpError(404, "")));
        assertFalse(IpLocationClient.isRetryable(IpLocationClient.httpError(600, "")));
        assertFalse(IpLocationClient.isRetryable(
                new IpLocationClient.IpLocationException(
                        IpLocationClient.IpLocationException.Kind.PARSE, "json")));
        assertFalse(IpLocationClient.isRetryable(
                new IpLocationClient.IpLocationException(
                        IpLocationClient.IpLocationException.Kind.INVALID, "datos")));
        assertFalse(IpLocationClient.isRetryable(
                new IpLocationClient.IpLocationException(
                        IpLocationClient.IpLocationException.Kind.PROVIDER, "proveedor")));
        assertFalse(IpLocationClient.isRetryable(null));
    }

    @Test
    public void fromDetectedProducesValidDetectedCity() throws Exception {
        IpLocationClient.Result result =
                IpLocationClient.parse(fixture("ipwhois_tres_cantos.json"));

        City city = City.fromDetected(result);

        assertNotNull(city);
        assertTrue(city.isValid());
        assertEquals(City.Source.DETECTED, city.source);
        assertEquals("Tres Cantos", city.name);
    }

    private static void assertInvalid(String json) throws Exception {
        assertKind(IpLocationClient.IpLocationException.Kind.INVALID, json);
    }

    private static void assertKind(IpLocationClient.IpLocationException.Kind kind,
                                   String json) throws Exception {
        try {
            IpLocationClient.parse(json);
            fail("Debía lanzar para: " + json);
        } catch (IpLocationClient.IpLocationException e) {
            assertEquals(kind, e.kind);
        }
    }

    private static String fixture(String name) throws Exception {
        try (InputStream input = IpLocationClientTest.class
                .getResourceAsStream("/fixtures/" + name)) {
            assertNotNull("Falta el fixture " + name, input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
