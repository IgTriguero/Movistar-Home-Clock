package com.itriguero.homeclock;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CityTest {

    @Test
    public void keyRoundsCoordinatesToThreeDecimals() {
        City city = city("Vigo", 42.23282, -8.72264);

        assertEquals("42.233,-8.723", city.key());
    }

    @Test
    public void keyNormalizesAlmostZeroCoordinates() {
        City city = city("Origen", -0.0004, 0.0004);

        assertEquals("0.000,0.000", city.key());
    }

    @Test
    public void keyNormalizesNegativeZero() {
        City city = city("Origen", -0.0, -0.0);

        assertEquals("0.000,0.000", city.key());
    }

    @Test
    public void keyUsesDotRegardlessOfDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("es", "ES"));
            assertEquals("42.233,-8.723", city("Vigo", 42.23282, -8.72264).key());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void keyKeepsRealNegativeCoordinates() {
        City city = city("Sur", -42.23282, 8.72264);

        assertEquals("-42.233,8.723", city.key());
    }

    @Test
    public void rejectsBlankName() {
        assertFalse(new City("   ", "", "", 40, -3, City.Source.MANUAL).isValid());
    }

    @Test
    public void rejectsTooLongName() {
        String name = "a".repeat(81);

        assertFalse(new City(name, "", "", 40, -3, City.Source.MANUAL).isValid());
        assertTrue(new City(name.substring(0, 80), "", "", 40, -3,
                City.Source.MANUAL).isValid());
    }

    @Test
    public void rejectsOutOfRangeCoordinates() {
        assertFalse(city("Norte", 90.1, 0).isValid());
        assertFalse(city("Sur", -90.1, 0).isValid());
        assertFalse(city("Este", 0, 180.1).isValid());
        assertFalse(city("Oeste", 0, -180.1).isValid());
    }

    @Test
    public void rejectsNonFiniteCoordinates() {
        assertFalse(city("NaN", Double.NaN, 0).isValid());
        assertFalse(city("Infinito", Double.POSITIVE_INFINITY, 0).isValid());
        assertFalse(city("Menos infinito", 0, Double.NEGATIVE_INFINITY).isValid());
    }

    @Test
    public void acceptsBoundaryCoordinates() {
        assertTrue(city("Polo", 90, 180).isValid());
        assertTrue(city("Antipolo", -90, -180).isValid());
    }

    @Test
    public void settingsLabelIncludesRegionAndCountry() {
        City city = new City("Vigo", "Galicia", "España",
                42.23282, -8.72264, City.Source.MANUAL);

        assertEquals("Vigo · Galicia, España", city.settingsLabel());
    }

    @Test
    public void settingsLabelFallsBackToCountryWithoutRegion() {
        City city = new City("Vigo", "", "España",
                42.23282, -8.72264, City.Source.MANUAL);

        assertEquals("Vigo · España", city.settingsLabel());
    }

    @Test
    public void settingsLabelDoesNotRepeatEqualRegionAndCountry() {
        City city = new City("Vigo", "España", "España",
                42.23282, -8.72264, City.Source.MANUAL);

        assertEquals("Vigo · España", city.settingsLabel());
    }

    @Test
    public void settingsLabelIsOnlyNameWithoutRegionOrCountry() {
        City city = new City("Vigo", "", "",
                42.23282, -8.72264, City.Source.MANUAL);

        assertEquals("Vigo", city.settingsLabel());
    }

    @Test
    public void fromResultCopiesFieldsAndSource() {
        CitySearchClient.Result result = new CitySearchClient.Result(
                "Vigo", "Galicia", "España", 42.23282, -8.72264);

        City city = City.from(result, City.Source.DETECTED);

        assertEquals("Vigo", city.name);
        assertEquals("Galicia", city.region);
        assertEquals("España", city.country);
        assertEquals(42.23282, city.lat, 0.00001);
        assertEquals(-8.72264, city.lon, 0.00001);
        assertEquals(City.Source.DETECTED, city.source);
    }

    @Test
    public void fromDetectedCopiesFieldsAndMarksDetected() {
        IpLocationClient.Result result = new IpLocationClient.Result(
                "Vigo", "Galicia", "España", "ES", 42.23282, -8.72264);

        City city = City.fromDetected(result);

        assertEquals("Vigo", city.name);
        assertEquals("Galicia", city.region);
        assertEquals("España", city.country);
        assertEquals(42.23282, city.lat, 0.00001);
        assertEquals(-8.72264, city.lon, 0.00001);
        assertEquals(City.Source.DETECTED, city.source);
        assertTrue(city.isValid());
    }

    @Test
    public void fromDetectedReturnsNullWithoutResult() {
        assertNull(City.fromDetected(null));
    }

    @Test
    public void normalizeNameIgnoresAccentsAndSeparators() {
        assertEquals("san jose", City.normalizeName("  San   José "));
        assertEquals("a coruna", City.normalizeName("A Coruña"));
        assertEquals("sao paulo", City.normalizeName("São Paulo"));
        assertEquals("", City.normalizeName(null));
        assertEquals("", City.normalizeName("  --  "));
    }

    @Test
    public void distanceKmUsesHaversine() {
        assertEquals(505.0, City.distanceKm(40.4168, -3.7038, 41.3874, 2.1686), 2.0);
        assertEquals(0.0, City.distanceKm(40.0, -3.0, 40.0, -3.0), 0.000001);
        assertEquals(
                City.distanceKm(40.0, -3.0, 42.0, -8.0),
                City.distanceKm(42.0, -8.0, 40.0, -3.0),
                0.000001);
    }

    @Test
    public void samePlaceMatchesDefaultAndDetectedResponseWithDifferentKeys() {
        City detected = City.fromDetected(new IpLocationClient.Result(
                "Tres Cantos", "Comunidad de Madrid", "España", "ES",
                40.601, -3.708));

        assertNotEquals(City.defaultCity().key(), detected.key());
        assertTrue(City.defaultCity().samePlace(detected));
        assertTrue(detected.samePlace(City.defaultCity()));
    }

    @Test
    public void samePlaceMatchesAccentVariantWithin25Km() {
        City sanJose = city("San José", 40.0, -3.0);
        City sanJoseSinTilde = city("San Jose", 40.05, -3.0);

        assertTrue(sanJose.samePlace(sanJoseSinTilde));
    }

    @Test
    public void samePlaceRejectsSameNameFarAway() {
        City vigoEspaña = city("Vigo", 42.23282, -8.72264);
        City vigoArgentina = city("Vigo", -34.6037, -58.3816);

        assertFalse(vigoEspaña.samePlace(vigoArgentina));
    }

    @Test
    public void samePlaceRejectsDifferentNamesAndNull() {
        City tresCantos = city("Tres Cantos", 40.6009, -3.7086);
        City vigo = city("Vigo", 42.23282, -8.72264);

        assertFalse(tresCantos.samePlace(vigo));
        assertFalse(tresCantos.samePlace(null));
    }

    @Test
    public void defaultCityHasExpectedValues() {
        City city = City.defaultCity();

        assertEquals("Tres Cantos", city.name);
        assertEquals("Comunidad de Madrid", city.region);
        assertEquals("España", city.country);
        assertEquals(40.6009, city.lat, 0.00001);
        assertEquals(-3.7086, city.lon, 0.00001);
        assertEquals(City.Source.DEFAULT, city.source);
        assertEquals("40.601,-3.709", city.key());
    }

    @Test
    public void sourceSerializationFallsBackToManual() {
        assertEquals(City.Source.DEFAULT, City.Source.fromJson("default"));
        assertEquals(City.Source.MANUAL, City.Source.fromJson("manual"));
        assertEquals(City.Source.DETECTED, City.Source.fromJson("detected"));
        assertEquals(City.Source.MANUAL, City.Source.fromJson("desconocido"));
        assertEquals(City.Source.MANUAL, City.Source.fromJson(null));
    }

    private static City city(String name, double lat, double lon) {
        return new City(name, "", "", lat, lon, City.Source.MANUAL);
    }
}
