package com.itriguero.homeclock;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CityListCodecTest {

    @Test
    public void roundTripPreservesCitiesAndSelection() {
        City vigo = new City("Vigo", "Galicia", "España",
                42.23282, -8.72264, City.Source.MANUAL);
        City madrid = new City("Madrid", "Comunidad de Madrid", "España",
                40.4168, -3.7038, City.Source.DETECTED);
        CityList original = new CityList(List.of(vigo, madrid), madrid.key());

        CityListCodec.Decoded decoded = CityListCodec.decode(
                CityListCodec.encode(original), null);

        assertFalse(decoded.migrated);
        assertFalse(decoded.repaired);
        assertFalse(decoded.readOnly);
        assertEquals(2, decoded.list.size());
        assertEquals("Vigo", decoded.list.get(0).name);
        assertEquals("Galicia", decoded.list.get(0).region);
        assertEquals("España", decoded.list.get(0).country);
        assertEquals(42.23282, decoded.list.get(0).lat, 0.00001);
        assertEquals(-8.72264, decoded.list.get(0).lon, 0.00001);
        assertEquals(City.Source.MANUAL, decoded.list.get(0).source);
        assertEquals(City.Source.DETECTED, decoded.list.get(1).source);
        assertEquals(madrid.key(), decoded.list.selectedKey());
    }

    @Test
    public void migratesValidLegacyLocation() {
        CityListCodec.Legacy legacy = legacy("Vigo", "42.23282", "-8.72264");

        CityListCodec.Decoded decoded = CityListCodec.decode(null, legacy);

        assertTrue(decoded.migrated);
        assertFalse(decoded.repaired);
        assertFalse(decoded.readOnly);
        assertEquals(1, decoded.list.size());
        City city = decoded.list.selected();
        assertEquals("Vigo", city.name);
        assertEquals("", city.region);
        assertEquals("", city.country);
        assertEquals(42.23282, city.lat, 0.00001);
        assertEquals(-8.72264, city.lon, 0.00001);
        assertEquals(City.Source.MANUAL, city.source);
    }

    @Test
    public void migratesLegacyLocationWithCommaDecimal() {
        CityListCodec.Legacy legacy = legacy("Vigo", "42,23282", "-8,72264");

        CityListCodec.Decoded decoded = CityListCodec.decode("", legacy);

        assertTrue(decoded.migrated);
        assertEquals(42.23282, decoded.list.selected().lat, 0.00001);
        assertEquals(-8.72264, decoded.list.selected().lon, 0.00001);
    }

    @Test
    public void legacyDefaultCoordinatesAreMarkedAsDefaultSource() {
        CityListCodec.Legacy legacy = legacy(
                "Tres Cantos", "40,6009", "-3,7086");

        CityListCodec.Decoded decoded = CityListCodec.decode(null, legacy);

        assertEquals(City.Source.DEFAULT, decoded.list.selected().source);
        assertEquals("Tres Cantos", decoded.list.selected().name);
    }

    @Test
    public void invalidLegacyFallsBackToDefaultCity() {
        CityListCodec.Decoded outOfRange = CityListCodec.decode(
                null, legacy("Vigo", "99", "-8.7"));
        CityListCodec.Decoded notANumber = CityListCodec.decode(
                null, legacy("Vigo", "abc", "-8.7"));
        CityListCodec.Decoded blankName = CityListCodec.decode(
                null, legacy("  ", "42.2", "-8.7"));

        assertDefaultMigration(outOfRange);
        assertDefaultMigration(blankName);

        // Si solo falta una coordenada, se conserva el nombre y se usa la
        // coordenada por defecto, como hacía el parser antiguo.
        assertTrue(notANumber.migrated);
        assertEquals("Vigo", notANumber.list.selected().name);
        assertEquals(City.DEFAULT_LAT, notANumber.list.selected().lat, 0.00001);
        assertEquals(-8.7, notANumber.list.selected().lon, 0.00001);
    }

    @Test
    public void missingOrBrokenJsonWithoutLegacyFallsBackToDefaultCity() {
        assertDefaultMigration(CityListCodec.decode(null, null));
        assertDefaultMigration(CityListCodec.decode("", null));
        assertDefaultMigration(CityListCodec.decode("   ", null));
        assertDefaultMigration(CityListCodec.decode("{esto no es json", null));
        assertDefaultMigration(CityListCodec.decode("[1,2,3]", null));
    }

    @Test
    public void duplicateCitiesKeepTheFirstOne() throws Exception {
        JSONArray cities = new JSONArray()
                .put(cityJson("Primera", 40.0, -3.0, "manual"))
                .put(cityJson("Segunda", 40.0004, -2.9996, "manual"));

        CityListCodec.Decoded decoded = CityListCodec.decode(
                json(1, City.keyOf(40.0, -3.0), cities), null);

        assertTrue(decoded.repaired);
        assertEquals(1, decoded.list.size());
        assertEquals("Primera", decoded.list.get(0).name);
        assertEquals(City.keyOf(40.0, -3.0), decoded.list.selectedKey());
    }

    @Test
    public void missingSelectedKeySelectsFirstCity() throws Exception {
        JSONArray cities = new JSONArray()
                .put(cityJson("A", 40.0, -3.0, "manual"))
                .put(cityJson("B", 41.0, -4.0, "manual"));

        CityListCodec.Decoded decoded = CityListCodec.decode(
                json(1, "99.000,99.000", cities), null);

        assertTrue(decoded.repaired);
        assertEquals("A", decoded.list.selected().name);
        assertEquals(0, decoded.list.selectedIndex());
    }

    @Test
    public void moreThanEightCitiesKeepsTheFirstEight() throws Exception {
        JSONArray cities = new JSONArray();
        for (int i = 0; i < 10; i++) {
            cities.put(cityJson("C" + i, 40.0 + i * 0.1, -3.0, "manual"));
        }

        CityListCodec.Decoded decoded = CityListCodec.decode(
                json(1, City.keyOf(40.0, -3.0), cities), null);

        assertTrue(decoded.repaired);
        assertEquals(CityList.MAX_CITIES, decoded.list.size());
        assertEquals("C0", decoded.list.get(0).name);
        assertEquals("C7", decoded.list.get(7).name);
    }

    @Test
    public void unknownSourceIsReadAsManual() throws Exception {
        JSONArray cities = new JSONArray()
                .put(cityJson("Vigo", 42.23282, -8.72264, "satelite"));

        CityListCodec.Decoded decoded = CityListCodec.decode(
                json(1, City.keyOf(42.23282, -8.72264), cities), null);

        assertEquals(City.Source.MANUAL, decoded.list.selected().source);
    }

    @Test
    public void futureVersionIsReadOnly() throws Exception {
        JSONArray cities = new JSONArray()
                .put(cityJson("Vigo", 42.23282, -8.72264, "manual"));

        CityListCodec.Decoded decoded = CityListCodec.decode(
                json(2, City.keyOf(42.23282, -8.72264), cities), null);

        assertTrue(decoded.readOnly);
        assertTrue(decoded.list.isReadOnly());
        assertFalse(decoded.migrated);
        assertFalse(decoded.repaired);
        assertEquals(1, decoded.list.size());
        assertEquals("Vigo", decoded.list.selected().name);
    }

    @Test
    public void emptyCityArrayFallsBackToLegacyCity() throws Exception {
        CityListCodec.Decoded decoded = CityListCodec.decode(
                json(1, "", new JSONArray()), legacy("Vigo", "42.2", "-8.7"));

        assertTrue(decoded.repaired);
        assertEquals("Vigo", decoded.list.selected().name);
    }

    @Test
    public void nonStringNameIsRejected() throws Exception {
        JSONObject bad = cityJson("Vigo", 42.2, -8.7, "manual")
                .put("name", new JSONObject().put("x", 1));

        CityListCodec.Decoded decoded = CityListCodec.decode(
                json(1, "", new JSONArray().put(bad)), null);

        assertEquals(City.defaultCity().name, decoded.list.selected().name);
        assertTrue(decoded.repaired);
    }

    @Test
    public void emptyCityArrayFallsBackToDefaultCity() throws Exception {
        CityListCodec.Decoded decoded = CityListCodec.decode(
                json(1, "", new JSONArray()), null);

        assertTrue(decoded.repaired);
        assertFalse(decoded.migrated);
        assertEquals(1, decoded.list.size());
        assertEquals(City.defaultCity().name, decoded.list.selected().name);
        assertEquals(City.Source.DEFAULT, decoded.list.selected().source);
    }

    @Test
    public void invalidCitiesAreDiscarded() throws Exception {
        JSONObject broken = new JSONObject().put("name", "Sin coordenadas");
        JSONObject blank = cityJson("  ", 40.0, -3.0, "manual");
        JSONArray cities = new JSONArray()
                .put(JSONObject.NULL)
                .put(broken)
                .put(blank)
                .put(cityJson("Válida", 42.23282, -8.72264, "manual"));

        CityListCodec.Decoded decoded = CityListCodec.decode(
                json(1, City.keyOf(42.23282, -8.72264), cities), null);

        assertTrue(decoded.repaired);
        assertEquals(1, decoded.list.size());
        assertEquals("Válida", decoded.list.selected().name);
    }

    @Test
    public void encodeAndDecodeAreStable() {
        CityList original = new CityList(List.of(
                new City("Vigo", "Galicia", "España",
                        42.23282, -8.72264, City.Source.MANUAL),
                new City("Madrid", "Comunidad de Madrid", "España",
                        40.4168, -3.7038, City.Source.DETECTED)),
                City.keyOf(40.4168, -3.7038));

        String first = CityListCodec.encode(original);
        String second = CityListCodec.encode(original);
        CityListCodec.Decoded decoded = CityListCodec.decode(first, null);

        assertEquals(first, second);
        assertEquals(first, CityListCodec.encode(decoded.list));
    }

    @Test
    public void defaultCityDelegatesToCity() {
        assertNotNull(City.defaultCity());
        assertEquals(City.defaultCity().key(), CityListCodec.defaultCity().key());
        assertEquals(City.defaultCity().name, CityListCodec.defaultCity().name);
    }

    private static void assertDefaultMigration(CityListCodec.Decoded decoded) {
        assertTrue(decoded.migrated);
        assertEquals(1, decoded.list.size());
        assertEquals(City.defaultCity().name, decoded.list.selected().name);
        assertEquals(City.Source.DEFAULT, decoded.list.selected().source);
    }

    private static CityListCodec.Legacy legacy(String city, String lat, String lon) {
        CityListCodec.Legacy legacy = new CityListCodec.Legacy();
        legacy.city = city;
        legacy.lat = lat;
        legacy.lon = lon;
        return legacy;
    }

    private static String json(int version, String selected, JSONArray cities)
            throws Exception {
        return new JSONObject()
                .put("version", version)
                .put("selected", selected)
                .put("cities", cities)
                .toString();
    }

    private static JSONObject cityJson(String name, double lat, double lon, String source)
            throws Exception {
        return new JSONObject()
                .put("name", name)
                .put("region", "")
                .put("country", "España")
                .put("lat", lat)
                .put("lon", lon)
                .put("source", source);
    }
}
