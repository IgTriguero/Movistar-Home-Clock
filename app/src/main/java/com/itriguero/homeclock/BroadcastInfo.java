package com.itriguero.homeclock;

/**
 * Información de emisión en España de un partido.
 *
 * <p>Objeto puro (sin Android): lo construye {@link BroadcastCatalog} a partir
 * de la competición y el equipo seguido. {@link Source#CURATED} indica que la
 * etiqueta procede de la tabla curada por competición; {@link Source#SEARCH}
 * que solo hay un enlace de búsqueda como respaldo.
 */
final class BroadcastInfo {

    /** Origen de la información de emisión. */
    enum Source { CURATED, SEARCH }

    public final String competitionKey;
    public final String label;
    public final String detail;
    public final String searchUrl;
    public final String season;
    public final Source source;

    BroadcastInfo(String competitionKey, String label, String detail,
                  String searchUrl, String season, Source source) {
        this.competitionKey = safe(competitionKey);
        this.label = safe(label);
        this.detail = safe(detail);
        this.searchUrl = safe(searchUrl);
        this.season = safe(season);
        this.source = source == null ? Source.SEARCH : source;
    }

    /** Hay una emisora curada que mostrar, no solo la búsqueda. */
    boolean isCurated() {
        return source == Source.CURATED && !label.isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
