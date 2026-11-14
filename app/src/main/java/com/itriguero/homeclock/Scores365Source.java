package com.itriguero.homeclock;

import java.time.LocalDate;
import java.util.List;
import java.util.TimeZone;

/**
 * Adaptador de 365Scores a la capa de fuentes.
 */
final class Scores365Source implements MatchSource {

    static final String ID = "365scores";

    private final TeamSelectionHolder teams;

    /**
     * Sin holder explícito se mantienen los equipos por defecto: el contrato
     * diario y cualquier uso directo siguen consultando algo útil.
     */
    Scores365Source() {
        this(null);
    }

    Scores365Source(TeamSelectionHolder holder) {
        if (holder == null) {
            holder = new TeamSelectionHolder();
            holder.set(new TrackedTeamList(TrackedTeam.defaults(), false));
        }
        this.teams = holder;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isEnabledFor(TeamSelection selection) {
        return selection != null && !selection.scores365Ids().isEmpty();
    }

    @Override
    public SourceResult<List<Match>> fetch(LocalDate date) {
        long start = System.nanoTime();
        TeamSelection selection = teams.get();
        if (selection == null || selection.scores365Ids().isEmpty()) {
            return SourceResult.empty(System.currentTimeMillis(), elapsedMs(start));
        }
        try {
            List<Match> matches = MatchesClient.fetch(
                    date, TimeZone.getDefault().getID(), selection.scores365Ids());
            long fetchedAt = System.currentTimeMillis();
            long duration = elapsedMs(start);
            if (matches == null || matches.isEmpty()) {
                return SourceResult.empty(fetchedAt, duration);
            }
            return SourceResult.ok(matches, fetchedAt, duration);
        } catch (Exception e) {
            return SourceResult.fromException(e, start);
        }
    }

    static long elapsedMs(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }
}
