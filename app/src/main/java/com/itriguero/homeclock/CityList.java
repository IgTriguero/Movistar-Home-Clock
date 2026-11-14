package com.itriguero.homeclock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lista ordenada de ciudades con selección guardada por clave, nunca por
 * índice, para que reordenar o borrar no cambie la ciudad activa por error.
 *
 * La lista nunca queda vacía: como mínimo contiene la ciudad por defecto.
 */
final class CityList {

    static final int MAX_CITIES = 8;

    enum AddResult { ADDED, DUPLICATE, FULL, INVALID }

    enum RemoveResult { REMOVED, NOT_FOUND, LAST }

    private final List<City> cities;
    private final boolean readOnly;
    private String selectedKey;

    CityList(List<City> cities, String selectedKey) {
        this(cities, selectedKey, false);
    }

    CityList(List<City> cities, String selectedKey, boolean readOnly) {
        List<City> safe = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        if (cities != null) {
            for (City city : cities) {
                if (city == null || !city.isValid()) continue;
                if (!keys.add(city.key())) continue;
                safe.add(city);
                if (safe.size() >= MAX_CITIES) break;
            }
        }
        if (safe.isEmpty()) safe.add(City.defaultCity());

        this.cities = safe;
        this.readOnly = readOnly;
        int index = indexOf(selectedKey);
        this.selectedKey = safe.get(index < 0 ? 0 : index).key();
    }

    /**
     * true si el JSON venía de una versión futura del esquema. En ese caso no
     * se debe reescribir desde esta versión para no perder campos o ciudades.
     */
    boolean isReadOnly() {
        return readOnly;
    }

    /**
     * Copia inmutable del estado actual. Devolver una copia evita que un
     * consumidor en otro hilo recorra la lista mientras la UI la muta.
     */
    List<City> cities() {
        return Collections.unmodifiableList(new ArrayList<>(cities));
    }

    City get(int index) {
        return cities.get(index);
    }

    int size() {
        return cities.size();
    }

    City selected() {
        return cities.get(selectedIndex());
    }

    int selectedIndex() {
        int index = indexOf(selectedKey);
        return index < 0 ? 0 : index;
    }

    String selectedKey() {
        return selectedKey;
    }

    int indexOf(String key) {
        if (key == null) return -1;
        String trimmed = key.trim();
        for (int i = 0; i < cities.size(); i++) {
            if (cities.get(i).key().equals(trimmed)) return i;
        }
        return -1;
    }

    /**
     * Los duplicados se comprueban antes que el límite: añadir una ciudad que
     * ya existe informa DUPLICATE aunque la lista esté llena.
     */
    AddResult add(City city) {
        if (city == null || !city.isValid()) return AddResult.INVALID;
        if (indexOf(city.key()) >= 0) return AddResult.DUPLICATE;
        if (cities.size() >= MAX_CITIES) return AddResult.FULL;
        cities.add(city);
        return AddResult.ADDED;
    }

    RemoveResult remove(String key) {
        int index = indexOf(key);
        if (index < 0) return RemoveResult.NOT_FOUND;
        if (cities.size() == 1) return RemoveResult.LAST;

        boolean removingSelected = index == selectedIndex();
        cities.remove(index);
        if (removingSelected) {
            int next = Math.min(index, cities.size() - 1);
            selectedKey = cities.get(next).key();
        }
        return RemoveResult.REMOVED;
    }

    /** Mueve una ciudad y conserva la seleccionada aunque cambie de posición. */
    boolean move(int from, int to) {
        if (from < 0 || from >= cities.size()) return false;
        if (to < 0 || to >= cities.size()) return false;
        if (from == to) return true;

        City current = selected();
        City moved = cities.remove(from);
        cities.add(to, moved);
        selectedKey = current.key();
        return true;
    }

    boolean select(String key) {
        int index = indexOf(key);
        if (index < 0) return false;
        selectedKey = cities.get(index).key();
        return true;
    }

    /** Claves en orden; cambia al reordenar pero no al cambiar la selección. */
    String signature() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < cities.size(); i++) {
            if (i > 0) result.append('|');
            result.append(cities.get(i).key());
        }
        return result.toString();
    }

    /**
     * Nombre para la UI. Si otra ciudad comparte nombre, se añade región/país
     * para que las dos entradas se puedan distinguir.
     */
    String labelFor(String key) {
        int index = indexOf(key);
        if (index < 0) return "";

        City city = cities.get(index);
        for (int i = 0; i < cities.size(); i++) {
            if (i != index && cities.get(i).name.equalsIgnoreCase(city.name)) {
                return city.settingsLabel();
            }
        }
        return city.name;
    }
}
