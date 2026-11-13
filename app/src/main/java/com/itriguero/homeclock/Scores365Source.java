package com.itriguero.homeclock;

import java.time.LocalDate;
import java.util.List;
import java.util.TimeZone;

/**
 * Adaptador de 365Scores a la capa de fuentes.
 */
final class Scores365Source implements MatchSource {

    static final String ID = "365scores";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public SourceResult<List<Match>> fetch(LocalDate date) {
        long start = System.nanoTime();
        try {
            List<Match> matches = MatchesClient.fetch(
                    date, TimeZone.getDefault().getID());
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
