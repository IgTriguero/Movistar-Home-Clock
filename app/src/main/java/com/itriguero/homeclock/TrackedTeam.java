package com.itriguero.homeclock;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Equipo seguido por el usuario. Inmutable y sin dependencias de Android para
 * poder probarlo y serializarlo en la JVM.
 *
 * La clave incluye proveedor, deporte e identificador: el Real Madrid de fútbol
 * (131) y el de baloncesto (204) son entradas distintas aunque compartan nombre.
 */
final class TrackedTeam {

    static final String PROVIDER_365 = "365scores";
    static final String PROVIDER_AS_CELTAS = "as-celtas";

    /** As Celtas no tiene ID de 365Scores; se identifica con un centinela. */
    static final long AS_CELTAS_ID = -1L;

    static final int SPORT_FOOTBALL = 1;
    static final int SPORT_BASKETBALL = 2;

    private static final int MAX_NAME_LENGTH = 80;
    private static final int MAX_SHORT_NAME_LENGTH = 24;
    private static final String BASKETBALL_SUFFIX = " BAL.";
    private static final String LOGO_BASE =
            "https://imagecache.365scores.com/image/upload/f_png,w_200,h_200,"
                    + "c_limit,q_auto:eco/v1/Competitors/";

    private static final List<TrackedTeam> DEFAULTS = Collections.unmodifiableList(
            Arrays.asList(
                    new TrackedTeam(PROVIDER_365, 131L, SPORT_FOOTBALL,
                            "Real Madrid", "REAL MADRID", "España", "LaLiga", "", true),
                    new TrackedTeam(PROVIDER_365, 174L, SPORT_FOOTBALL,
                            "Rayo Vallecano", "RAYO", "España", "LaLiga", "", true),
                    new TrackedTeam(PROVIDER_365, 108L, SPORT_FOOTBALL,
                            "Liverpool", "LIVERPOOL", "Inglaterra", "Premier League", "", true),
                    new TrackedTeam(PROVIDER_365, 5050L, SPORT_FOOTBALL,
                            "España", "ESPAÑA", "España", "Selección", "", true),
                    new TrackedTeam(PROVIDER_365, 204L, SPORT_BASKETBALL,
                            "Real Madrid", "R. MADRID BAL.", "España", "Liga Endesa", "", true),
                    new TrackedTeam(PROVIDER_AS_CELTAS, AS_CELTAS_ID, SPORT_FOOTBALL,
                            "As Celtas", "AS CELTAS", "España",
                            "2ª Federación Femenina", "", true)));

    final String provider;
    final long competitorId;
    final int sportId;
    final String name;
    final String shortName;
    final String country;
    final String competition;
    final String logoUrl;
    final boolean enabled;

    TrackedTeam(String provider, long competitorId, int sportId, String name,
                String shortName, String country, String competition,
                String logoUrl, boolean enabled) {
        this.provider = trim(provider);
        this.competitorId = competitorId;
        this.sportId = sportId;
        this.name = trim(name);
        this.country = trim(country);
        this.competition = trim(competition);
        this.enabled = enabled;

        String safeShortName = collapse(shortName);
        if (safeShortName.isEmpty()) {
            // Un nombre corto ausente se deriva del nombre completo; así toda
            // entrada válida puede mostrarse sin textos vacíos.
            safeShortName = autoShortName(this.name, "");
        }
        this.shortName = safeShortName;

        String safeLogo = trim(logoUrl);
        if (safeLogo.isEmpty() && PROVIDER_365.equals(this.provider)) {
            safeLogo = logoUrl(competitorId);
        }
        this.logoUrl = safeLogo;
    }

    /** Clave estable para deduplicar y persistir sin usar índices de lista. */
    String key() {
        return provider + ":" + sportId + ":" + competitorId;
    }

    String sport() {
        if (sportId == SPORT_FOOTBALL) return "Fútbol";
        if (sportId == SPORT_BASKETBALL) return "Baloncesto";
        return "";
    }

    boolean isValid() {
        if (!PROVIDER_365.equals(provider) && !PROVIDER_AS_CELTAS.equals(provider)) {
            return false;
        }
        if (PROVIDER_365.equals(provider)) {
            if (competitorId <= 0) return false;
        } else if (competitorId != AS_CELTAS_ID) {
            return false;
        }
        if (sportId != SPORT_FOOTBALL && sportId != SPORT_BASKETBALL) return false;
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) return false;
        if (shortName.isEmpty() || shortName.length() > MAX_SHORT_NAME_LENGTH) return false;
        return country.length() <= MAX_NAME_LENGTH
                && competition.length() <= MAX_NAME_LENGTH;
    }

    TrackedTeam withEnabled(boolean value) {
        return new TrackedTeam(provider, competitorId, sportId, name, shortName,
                country, competition, logoUrl, value);
    }

    /** Texto principal de la fila de Ajustes. */
    String settingsLabel() {
        String sport = sport();
        return sport.isEmpty() ? name : name + " · " + sport;
    }

    /** Competición y país, omitiendo los que falten. */
    String settingsDetail() {
        if (competition.isEmpty()) return country;
        if (country.isEmpty()) return competition;
        return competition + " · " + country;
    }

    /** Etiqueta corta sobre el panel de partidos. */
    String badge() {
        if (PROVIDER_AS_CELTAS.equals(provider)) return "Fútbol · RFEF/Celta";
        return sport();
    }

    /**
     * Si hay nombre corto se usa tal cual. Sin él, se respeta el texto de
     * respaldo del partido y, como último recurso, el nombre en mayúsculas.
     */
    String panelLabel(String fallback) {
        if (!shortName.isEmpty()) return shortName;
        String safeFallback = collapse(fallback);
        if (!safeFallback.isEmpty()) return safeFallback;
        return collapse(name).toUpperCase(Locale.ROOT);
    }

    static List<TrackedTeam> defaults() {
        return DEFAULTS;
    }

    static long[] default365Ids() {
        return new long[]{131L, 174L, 108L, 5050L, 204L};
    }

    static TrackedTeam asCeltas() {
        return DEFAULTS.get(DEFAULTS.size() - 1);
    }

    static String logoUrl(long competitorId) {
        return LOGO_BASE + competitorId;
    }

    /**
     * Nombre corto para chips y listas. Si el nombre corto que da el API es un
     * prefijo del nombre completo se reutiliza; en caso contrario se usa el
     * nombre completo en mayúsculas. Siempre colapsa espacios y trunca a 24.
     */
    static String autoShortName(String name, String apiShortName) {
        String safeName = collapse(name);
        String api = collapse(apiShortName);
        String result;
        if (!api.isEmpty() && safeName.regionMatches(true, 0, api, 0, api.length())) {
            result = api;
        } else {
            result = safeName.toUpperCase(Locale.ROOT);
        }
        if (result.length() > MAX_SHORT_NAME_LENGTH) {
            result = result.substring(0, MAX_SHORT_NAME_LENGTH).trim();
        }
        return result;
    }

    /**
     * Añade el sufijo de baloncesto sin duplicarlo ni superar el máximo. Se usa
     * cuando fútbol y baloncesto comparten nombre y en el panel deben
     * distinguirse.
     */
    static String withBasketballSuffix(String shortName) {
        String value = shortName == null ? "" : shortName.trim();
        String upper = value.toUpperCase(Locale.ROOT);
        if (upper.endsWith("BAL.") || upper.endsWith("BAL")) return value;

        int maxBase = MAX_SHORT_NAME_LENGTH - BASKETBALL_SUFFIX.length();
        if (value.length() > maxBase) value = value.substring(0, maxBase).trim();
        return value + BASKETBALL_SUFFIX;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String collapse(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}
