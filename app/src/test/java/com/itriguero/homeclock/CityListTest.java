package com.itriguero.homeclock;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class CityListTest {

    @Test
    public void addsUpToMaximumAndThenReportsFull() {
        CityList list = list(city("C0", 40.0, -3.0));
        for (int i = 1; i < CityList.MAX_CITIES; i++) {
            assertEquals(CityList.AddResult.ADDED,
                    list.add(city("C" + i, 40.0 + i, -3.0 + i)));
        }

        assertEquals(8, list.size());
        assertEquals(CityList.AddResult.FULL, list.add(city("C8", 40.0, -2.0)));
    }

    @Test
    public void duplicateIsReportedBeforeFull() {
        CityList list = list(city("C0", 40.0, -3.0));
        for (int i = 1; i < CityList.MAX_CITIES; i++) {
            list.add(city("C" + i, 40.0 + i, -3.0 + i));
        }

        assertEquals(CityList.AddResult.DUPLICATE,
                list.add(city("Mismo sitio", 40.0004, -2.9996)));
        assertEquals(8, list.size());
    }

    @Test
    public void addRejectsInvalidCity() {
        CityList list = list(city("C0", 40.0, -3.0));

        assertEquals(CityList.AddResult.INVALID, list.add(null));
        assertEquals(CityList.AddResult.INVALID,
                list.add(new City("", "", "", 40, -3, City.Source.MANUAL)));
        assertEquals(CityList.AddResult.INVALID, list.add(city("Mal", Double.NaN, 0)));
        assertEquals(1, list.size());
    }

    @Test
    public void removingFirstSelectedCitySelectsNext() {
        City a = city("A", 40.0, -3.0);
        City b = city("B", 41.0, -4.0);
        CityList list = list(a, b);

        assertEquals(CityList.RemoveResult.REMOVED, list.remove(a.key()));

        assertEquals(1, list.size());
        assertEquals(b.key(), list.selectedKey());
        assertEquals(0, list.selectedIndex());
    }

    @Test
    public void removingMiddleSelectedCitySelectsSameIndex() {
        City a = city("A", 40.0, -3.0);
        City b = city("B", 41.0, -4.0);
        City c = city("C", 42.0, -5.0);
        CityList list = list(a, b, c);
        assertTrue(list.select(b.key()));

        assertEquals(CityList.RemoveResult.REMOVED, list.remove(b.key()));

        assertEquals(2, list.size());
        assertEquals(c.key(), list.selectedKey());
        assertEquals(1, list.selectedIndex());
    }

    @Test
    public void removingLastSelectedCitySelectsPrevious() {
        City a = city("A", 40.0, -3.0);
        City b = city("B", 41.0, -4.0);
        City c = city("C", 42.0, -5.0);
        CityList list = list(a, b, c);
        assertTrue(list.select(c.key()));

        assertEquals(CityList.RemoveResult.REMOVED, list.remove(c.key()));

        assertEquals(2, list.size());
        assertEquals(b.key(), list.selectedKey());
        assertEquals(1, list.selectedIndex());
    }

    @Test
    public void removingNonSelectedCityKeepsSelection() {
        City a = city("A", 40.0, -3.0);
        City b = city("B", 41.0, -4.0);
        City c = city("C", 42.0, -5.0);
        CityList list = list(a, b, c);
        assertTrue(list.select(b.key()));

        assertEquals(CityList.RemoveResult.REMOVED, list.remove(a.key()));

        assertEquals(b.key(), list.selectedKey());
        assertEquals(0, list.selectedIndex());
    }

    @Test
    public void removingTheOnlyCityReportsLastWithoutMutating() {
        City a = city("A", 40.0, -3.0);
        CityList list = list(a);

        assertEquals(CityList.RemoveResult.LAST, list.remove(a.key()));

        assertEquals(1, list.size());
        assertEquals(a.key(), list.selectedKey());
    }

    @Test
    public void removingUnknownKeyReportsNotFound() {
        CityList list = list(city("A", 40.0, -3.0));

        assertEquals(CityList.RemoveResult.NOT_FOUND, list.remove("99.000,99.000"));
        assertEquals(1, list.size());
    }

    @Test
    public void movePreservesSelectedCity() {
        City a = city("A", 40.0, -3.0);
        City b = city("B", 41.0, -4.0);
        City c = city("C", 42.0, -5.0);
        CityList list = list(a, b, c);
        assertTrue(list.select(b.key()));

        assertTrue(list.move(0, 2));

        assertEquals(List.of("B", "C", "A"), names(list));
        assertEquals(b.key(), list.selectedKey());
        assertEquals(0, list.selectedIndex());
    }

    @Test
    public void moveRejectsInvalidIndexes() {
        CityList list = list(city("A", 40.0, -3.0), city("B", 41.0, -4.0));

        assertFalse(list.move(-1, 0));
        assertFalse(list.move(0, 2));
        assertEquals(List.of("A", "B"), names(list));
    }

    @Test
    public void selectUnknownKeyKeepsCurrentSelection() {
        City a = city("A", 40.0, -3.0);
        City b = city("B", 41.0, -4.0);
        CityList list = list(a, b);

        assertFalse(list.select("99.000,99.000"));

        assertEquals(a.key(), list.selectedKey());
        assertEquals(0, list.selectedIndex());
    }

    @Test
    public void signatureChangesWithOrderButNotWithSelection() {
        City a = city("A", 40.0, -3.0);
        City b = city("B", 41.0, -4.0);
        CityList list = list(a, b);
        String signature = list.signature();

        assertTrue(list.select(b.key()));
        assertEquals(signature, list.signature());

        CityList reordered = list(b, a);
        assertNotEquals(signature, reordered.signature());
    }

    @Test
    public void labelForDisambiguatesRepeatedNames() {
        City vigoGalicia = new City("Vigo", "Galicia", "España",
                42.23282, -8.72264, City.Source.MANUAL);
        City vigoPontevedra = new City("Vigo", "Pontevedra", "España",
                42.3, -8.8, City.Source.MANUAL);
        City madrid = new City("Madrid", "Comunidad de Madrid", "España",
                40.4, -3.7, City.Source.MANUAL);
        CityList list = list(vigoGalicia, vigoPontevedra, madrid);

        assertEquals("Vigo · Galicia, España", list.labelFor(vigoGalicia.key()));
        assertEquals("Vigo · Pontevedra, España", list.labelFor(vigoPontevedra.key()));
        assertEquals("Madrid", list.labelFor(madrid.key()));
    }

    @Test
    public void missingSelectionFallsBackToFirstCity() {
        City a = city("A", 40.0, -3.0);
        City b = city("B", 41.0, -4.0);

        CityList list = new CityList(List.of(a, b), "99.000,99.000");

        assertEquals(a.key(), list.selectedKey());
        assertEquals(0, list.selectedIndex());
    }

    @Test
    public void constructorSkipsInvalidAndDuplicateCities() {
        City a = city("A", 40.0, -3.0);
        City duplicate = city("Duplicada", 40.0, -3.0);
        City invalid = new City("", "", "", 40, -3, City.Source.MANUAL);

        CityList list = new CityList(List.of(a, invalid, duplicate), a.key());

        assertEquals(1, list.size());
        assertEquals("A", list.get(0).name);
    }

    @Test
    public void emptyConstructorInputFallsBackToDefaultCity() {
        CityList list = new CityList(List.of(), "");

        assertEquals(1, list.size());
        assertEquals(City.defaultCity().name, list.selected().name);
    }

    @Test
    public void citiesReturnsAnImmutableCopy() {
        CityList list = list(city("A", 40.0, -3.0), city("B", 41.0, -4.0));
        List<City> snapshot = list.cities();

        list.add(city("C", 42.0, -5.0));

        assertEquals(2, snapshot.size());
        assertEquals(3, list.cities().size());
    }

    @Test
    public void readOnlyFlagIsExposed() {
        CityList list = new CityList(
                List.of(city("A", 40.0, -3.0)), city("A", 40.0, -3.0).key(), true);

        assertTrue(list.isReadOnly());
        assertFalse(list(city("A", 40.0, -3.0)).isReadOnly());
    }

    @Test
    public void promoteDetectedKeepsOrderWhenAlreadyFirst() {
        City tresCantos = city("Tres Cantos", 40.6009, -3.7086);
        City vigo = city("Vigo", 42.23282, -8.72264);
        CityList list = list(tresCantos, vigo);
        assertTrue(list.select(vigo.key()));

        assertEquals(CityList.DetectedResult.ALREADY_FIRST,
                list.promoteDetected(city("Tres Cantos", 40.6009, -3.7086)));

        assertEquals(List.of("Tres Cantos", "Vigo"), names(list));
        assertEquals(tresCantos.key(), list.selectedKey());
    }

    @Test
    public void promoteDetectedMovesExistingToFirstWithoutChangingIt() {
        City tresCantos = city("Tres Cantos", 40.6009, -3.7086);
        City vigo = city("Vigo", 42.23282, -8.72264);
        CityList list = list(tresCantos, vigo);
        City detected = new City("Vigo", "", "España",
                42.23282, -8.72264, City.Source.DETECTED);

        assertEquals(CityList.DetectedResult.PROMOTED_EXISTING,
                list.promoteDetected(detected));

        assertEquals(List.of("Vigo", "Tres Cantos"), names(list));
        assertEquals(vigo.key(), list.selectedKey());
        assertEquals(City.Source.MANUAL, list.get(0).source);
    }

    @Test
    public void promoteDetectedInsertsNewCityFirstAndSelectsIt() {
        City tresCantos = city("Tres Cantos", 40.6009, -3.7086);
        City vigo = city("Vigo", 42.23282, -8.72264);
        CityList list = list(tresCantos, vigo);
        City barcelona = new City("Barcelona", "", "España",
                41.3874, 2.1686, City.Source.DETECTED);

        assertEquals(CityList.DetectedResult.ADDED_FIRST,
                list.promoteDetected(barcelona));

        assertEquals(List.of("Barcelona", "Tres Cantos", "Vigo"), names(list));
        assertEquals(barcelona.key(), list.selectedKey());
        assertEquals(3, list.size());
    }

    @Test
    public void promoteDetectedOnFullListDoesNotAddNewCity() {
        CityList list = fullList();
        String signature = list.signature();
        City selected = list.selected();

        assertEquals(CityList.DetectedResult.FULL,
                list.promoteDetected(city("Nueva", 10.0, 10.0)));

        assertEquals(8, list.size());
        assertEquals(signature, list.signature());
        assertEquals(selected.key(), list.selectedKey());
    }

    @Test
    public void promoteDetectedOnFullListMovesExistingToFirst() {
        CityList list = fullList();
        City last = list.get(7);

        assertEquals(CityList.DetectedResult.PROMOTED_EXISTING,
                list.promoteDetected(last));

        assertEquals(8, list.size());
        assertEquals(last.key(), list.get(0).key());
        assertEquals(last.key(), list.selectedKey());
    }

    @Test
    public void promoteDetectedOnFullListKeepsExistingFirst() {
        CityList list = fullList();
        City first = list.get(0);

        assertEquals(CityList.DetectedResult.ALREADY_FIRST,
                list.promoteDetected(first));

        assertEquals(8, list.size());
        assertEquals(first.key(), list.get(0).key());
        assertEquals(first.key(), list.selectedKey());
    }

    @Test
    public void promoteDetectedOnReadOnlyListDoesNotMutate() {
        City a = city("A", 40.0, -3.0);
        City b = city("B", 41.0, -4.0);
        CityList list = new CityList(List.of(a, b), a.key(), true);

        assertEquals(CityList.DetectedResult.READ_ONLY,
                list.promoteDetected(city("Nueva", 10.0, 10.0)));

        assertEquals(List.of("A", "B"), names(list));
        assertEquals(a.key(), list.selectedKey());
    }

    @Test
    public void promoteDetectedRejectsInvalidCity() {
        CityList list = list(city("A", 40.0, -3.0));

        assertEquals(CityList.DetectedResult.INVALID, list.promoteDetected(null));
        assertEquals(CityList.DetectedResult.INVALID,
                list.promoteDetected(new City("", "", "", 40, -3,
                        City.Source.DETECTED)));
        assertEquals(CityList.DetectedResult.INVALID,
                list.promoteDetected(city("Mal", Double.NaN, 0)));
        assertEquals(1, list.size());
    }

    @Test
    public void promoteDetectedDeduplicatesByNameAndDistance() {
        City tresCantos = city("Tres Cantos", 40.6009, -3.7086);
        City sanJose = city("San José", 42.0, -8.0);
        CityList list = list(tresCantos, sanJose);
        City detected = new City("San Jose", "Galicia", "España",
                42.05, -8.0, City.Source.DETECTED);

        assertEquals(CityList.DetectedResult.PROMOTED_EXISTING,
                list.promoteDetected(detected));

        assertEquals(2, list.size());
        assertEquals(List.of("San José", "Tres Cantos"), names(list));
        assertEquals(sanJose.key(), list.selectedKey());
        assertEquals(City.Source.MANUAL, list.get(0).source);
    }

    @Test
    public void promoteDetectedAddsSameNameWhenItIsFarAway() {
        CityList list = list(city("Vigo", 42.23282, -8.72264));
        City vigoArgentina = new City("Vigo", "", "Argentina",
                -34.6037, -58.3816, City.Source.DETECTED);

        assertEquals(CityList.DetectedResult.ADDED_FIRST,
                list.promoteDetected(vigoArgentina));

        assertEquals(2, list.size());
        assertEquals(vigoArgentina.key(), list.selectedKey());
    }

    @Test
    public void addStillDeduplicatesOnlyByKey() {
        CityList list = list(city("Tres Cantos", 40.6009, -3.7086));

        assertEquals(CityList.AddResult.ADDED,
                list.add(city("Tres Cantos", 40.601, -3.708)));
        assertEquals(2, list.size());
    }

    private static CityList fullList() {
        City[] cities = new City[CityList.MAX_CITIES];
        for (int i = 0; i < cities.length; i++) {
            cities[i] = city("C" + i, 40.0 + i, -3.0 + i);
        }
        return list(cities);
    }

    private static CityList list(City... cities) {
        return new CityList(List.of(cities), cities[0].key());
    }

    private static City city(String name, double lat, double lon) {
        return new City(name, "", "España", lat, lon, City.Source.MANUAL);
    }

    private static List<String> names(CityList list) {
        List<String> names = new ArrayList<>();
        for (City city : list.cities()) names.add(city.name);
        return names;
    }
}
