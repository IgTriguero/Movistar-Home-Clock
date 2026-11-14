package com.itriguero.homeclock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lista ordenada de equipos seguidos, guardada por {@link TrackedTeam#key()}.
 *
 * A diferencia de {@link CityList}, puede quedar vacía: el usuario puede
 * desactivar todos los equipos sin que la app imponga uno por defecto.
 */
final class TrackedTeamList {

    static final int MAX_TEAMS = 10;

    enum AddResult { ADDED, DUPLICATE, FULL, INVALID, READ_ONLY }

    enum RemoveResult { REMOVED, NOT_FOUND, READ_ONLY }

    private final List<TrackedTeam> teams;
    private final boolean readOnly;

    TrackedTeamList(List<TrackedTeam> teams, boolean readOnly) {
        List<TrackedTeam> safe = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        if (teams != null) {
            for (TrackedTeam team : teams) {
                if (team == null || !team.isValid()) continue;
                if (!keys.add(team.key())) continue;
                safe.add(team);
                if (safe.size() >= MAX_TEAMS) break;
            }
        }
        this.teams = safe;
        this.readOnly = readOnly;
    }

    /**
     * true si el JSON venía de una versión futura del esquema. En ese caso no
     * se debe reescribir desde esta versión para no perder campos o equipos.
     */
    boolean isReadOnly() {
        return readOnly;
    }

    /** Copia inmutable del estado actual. */
    List<TrackedTeam> teams() {
        return Collections.unmodifiableList(new ArrayList<>(teams));
    }

    /** Solo los equipos activos, conservando el orden configurado. */
    List<TrackedTeam> active() {
        List<TrackedTeam> result = new ArrayList<>();
        for (TrackedTeam team : teams) {
            if (team.enabled) result.add(team);
        }
        return Collections.unmodifiableList(result);
    }

    int size() {
        return teams.size();
    }

    int activeCount() {
        int count = 0;
        for (TrackedTeam team : teams) {
            if (team.enabled) count++;
        }
        return count;
    }

    TrackedTeam get(int index) {
        return teams.get(index);
    }

    int indexOf(String key) {
        if (key == null) return -1;
        String trimmed = key.trim();
        for (int i = 0; i < teams.size(); i++) {
            if (teams.get(i).key().equals(trimmed)) return i;
        }
        return -1;
    }

    TrackedTeam find(String key) {
        int index = indexOf(key);
        return index < 0 ? null : teams.get(index);
    }

    TrackedTeam find365(long competitorId) {
        for (TrackedTeam team : teams) {
            if (TrackedTeam.PROVIDER_365.equals(team.provider)
                    && team.competitorId == competitorId) {
                return team;
            }
        }
        return null;
    }

    TrackedTeam findAsCeltas() {
        for (TrackedTeam team : teams) {
            if (TrackedTeam.PROVIDER_AS_CELTAS.equals(team.provider)) return team;
        }
        return null;
    }

    /**
     * Los duplicados se comprueban antes que el límite: añadir un equipo que ya
     * existe informa DUPLICATE aunque la lista esté llena.
     */
    AddResult add(TrackedTeam team) {
        if (readOnly) return AddResult.READ_ONLY;
        if (team == null || !team.isValid()) return AddResult.INVALID;
        if (indexOf(team.key()) >= 0) return AddResult.DUPLICATE;
        if (teams.size() >= MAX_TEAMS) return AddResult.FULL;
        teams.add(team);
        return AddResult.ADDED;
    }

    /** Se permite borrar el último equipo: la lista vacía es válida. */
    RemoveResult remove(String key) {
        if (readOnly) return RemoveResult.READ_ONLY;
        int index = indexOf(key);
        if (index < 0) return RemoveResult.NOT_FOUND;
        teams.remove(index);
        return RemoveResult.REMOVED;
    }

    /** Devuelve false si el equipo no existe o la lista es de solo lectura. */
    boolean setEnabled(String key, boolean enabled) {
        if (readOnly) return false;
        int index = indexOf(key);
        if (index < 0) return false;
        TrackedTeam team = teams.get(index);
        if (team.enabled != enabled) teams.set(index, team.withEnabled(enabled));
        return true;
    }

    /**
     * Firma de toda la lista, incluido el estado de cada equipo. Cambia al
     * añadir, borrar, reordenar o activar/desactivar.
     */
    String signature() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < teams.size(); i++) {
            if (i > 0) result.append('|');
            TrackedTeam team = teams.get(i);
            result.append(team.key()).append('=').append(team.enabled);
        }
        return result.toString();
    }

    /**
     * Firma solo de los equipos activos, ordenados por clave, para comparar
     * selecciones sin depender del orden de configuración.
     */
    String activeSignature() {
        List<String> keys = new ArrayList<>();
        for (TrackedTeam team : teams) {
            if (team.enabled) keys.add(team.key());
        }
        Collections.sort(keys);
        return String.join("|", keys);
    }

    /**
     * Texto para el panel de un partido. Si el equipo no está en la lista se
     * devuelve el respaldo del propio partido.
     */
    String panelLabel(long competitorId, String fallback) {
        TrackedTeam team = competitorId == TrackedTeam.AS_CELTAS_ID
                ? findAsCeltas()
                : find365(competitorId);
        if (team == null) return fallback == null ? "" : fallback.trim();

        String label = team.panelLabel(fallback);
        if (team.sportId == TrackedTeam.SPORT_BASKETBALL
                && hasFootballSameName(team)) {
            label = TrackedTeam.withBasketballSuffix(label);
        }
        return label;
    }

    /**
     * Fútbol y baloncesto pueden compartir nombre (Real Madrid, España). En el
     * panel se añade el sufijo solo al baloncesto cuando ambos están en la
     * lista, sin tocar los datos guardados.
     */
    private boolean hasFootballSameName(TrackedTeam team) {
        String name = team.name.trim();
        for (TrackedTeam other : teams) {
            if (other.sportId != TrackedTeam.SPORT_FOOTBALL) continue;
            if (other.name.trim().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    TeamSelection selection() {
        List<Long> ids = new ArrayList<>();
        boolean asCeltas = false;
        for (TrackedTeam team : teams) {
            if (!team.enabled) continue;
            if (TrackedTeam.PROVIDER_365.equals(team.provider)) {
                ids.add(team.competitorId);
            } else if (TrackedTeam.PROVIDER_AS_CELTAS.equals(team.provider)) {
                asCeltas = true;
            }
        }
        return new TeamSelection(ids, asCeltas, activeSignature());
    }
}
