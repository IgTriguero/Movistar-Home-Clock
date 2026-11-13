package com.itriguero.homeclock;

import android.content.Context;

import java.time.LocalDate;
import java.util.List;

/**
 * Adaptador de la web del Celta a la capa de fuentes.
 */
final class CeltaSource implements MatchSource {

    static final String ID = "celta";

    private final Context context;

    CeltaSource(Context context) {
        this.context = context;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public SourceResult<List<Match>> fetch(LocalDate date) {
        long start = System.nanoTime();
        try {
            CeltaClient.FetchResult fetched = CeltaClient.fetch(context, date);
            List<Match> matches = fetched.matches;
            long fetchedAt = System.currentTimeMillis();
            long duration = Scores365Source.elapsedMs(start);
            if (matches == null || matches.isEmpty()) {
                return SourceResult.empty(fetchedAt, duration, fetched.fromCache);
            }
            return SourceResult.ok(matches, fetchedAt, duration, fetched.fromCache);
        } catch (Exception e) {
            return SourceResult.fromException(e, start);
        }
    }
}
