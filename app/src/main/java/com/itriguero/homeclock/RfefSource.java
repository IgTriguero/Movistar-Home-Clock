package com.itriguero.homeclock;

import android.content.Context;

import java.time.LocalDate;
import java.util.List;

/**
 * Adaptador de la RFEF a la capa de fuentes.
 */
final class RfefSource implements MatchSource {

    static final String ID = "rfef";

    private final Context context;

    RfefSource(Context context) {
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
            RfefClient.FetchResult fetched = RfefClient.fetch(context, date);
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
