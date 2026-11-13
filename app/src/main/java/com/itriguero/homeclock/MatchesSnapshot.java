package com.itriguero.homeclock;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Foto inmutable de los partidos de una fecha junto al estado de cada fuente.
 */
public final class MatchesSnapshot {

    public final LocalDate date;
    public final List<Match> matches;
    public final Map<String, SourceStatus> sources;
    public final long fetchedAt;
    public final boolean stale;

    public MatchesSnapshot(LocalDate date, List<Match> matches,
                           Map<String, SourceStatus> sources,
                           long fetchedAt, boolean stale) {
        this.date = date;
        this.matches = Collections.unmodifiableList(new ArrayList<>(
                matches == null ? Collections.emptyList() : matches));
        Map<String, SourceStatus> copy = new LinkedHashMap<>();
        if (sources != null) copy.putAll(sources);
        this.sources = Collections.unmodifiableMap(copy);
        this.fetchedAt = fetchedAt;
        this.stale = stale;
    }

    public boolean hasErrors() {
        return errorCount() > 0;
    }

    public int errorCount() {
        int count = 0;
        for (SourceStatus status : sources.values()) {
            if (status != null && status.state != SourceState.OK
                    && status.state != SourceState.EMPTY) {
                count++;
            }
        }
        return count;
    }

    /**
     * Resumen compacto para diagnóstico, p. ej. "365scores: OK · rfef: TIMEOUT".
     */
    public String summary() {
        StringBuilder builder = new StringBuilder();
        for (SourceStatus status : sources.values()) {
            if (status == null) continue;
            if (builder.length() > 0) builder.append(" · ");
            builder.append(status.sourceId).append(": ").append(status.state.name());
        }
        return builder.toString();
    }

    /**
     * Devuelve una copia con los partidos actuales marcados como obsoletos y
     * los estados de fuente de este intento.
     */
    public MatchesSnapshot withStale(Map<String, SourceStatus> statuses) {
        return new MatchesSnapshot(date, matches,
                statuses == null ? sources : statuses, fetchedAt, true);
    }
}
