package com.itriguero.homeclock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Detalle completo de un partido de 365Scores.
 *
 * Es un objeto puro (sin Android): lo construye {@link MatchDetailParser} a
 * partir de las respuestas de {@code /web/game/} y {@code /web/game/stats/}.
 * La UI lo consume en una fase posterior.
 */
final class MatchDetail {

    public final long gameId;
    public final String sourceId;
    public final String competition;
    public final String sport;
    public final long startMillis;

    public final MatchStatus status;
    public final String statusText;
    public final String gameTimeDisplay;
    public final boolean scoreKnown;
    public final int homeScore;
    public final int awayScore;

    public final String homeName;
    public final String awayName;
    public final long homeId;
    public final long awayId;

    public final Venue venue;
    public final List<Official> officials;
    public final List<MatchEvent> events;
    public final TeamLineup homeLineup;
    public final TeamLineup awayLineup;

    public final List<StatCategory> statCategories;
    public final boolean hasStats;
    public final String statsError;
    /** Cadenas de televisión que emiten el partido en España; vacía si no hay. */
    public final List<String> tvNetworks;

    public final long fetchedAt;
    public final boolean fromCache;

    public MatchDetail(long gameId, String sourceId, String competition, String sport,
                       long startMillis, MatchStatus status, String statusText,
                       String gameTimeDisplay, boolean scoreKnown,
                       int homeScore, int awayScore, String homeName, String awayName,
                       long homeId, long awayId, Venue venue, List<Official> officials,
                       List<MatchEvent> events, TeamLineup homeLineup,
                       TeamLineup awayLineup, List<StatCategory> statCategories,
                       boolean hasStats, String statsError, List<String> tvNetworks,
                       long fetchedAt, boolean fromCache) {
        this.gameId = gameId;
        this.sourceId = sourceId == null ? "" : sourceId;
        this.competition = competition == null ? "" : competition;
        this.sport = sport == null ? "" : sport;
        this.startMillis = startMillis;
        this.status = status == null ? MatchStatus.UNKNOWN : status;
        this.statusText = statusText == null ? "" : statusText;
        this.gameTimeDisplay = gameTimeDisplay == null ? "" : gameTimeDisplay;
        this.scoreKnown = scoreKnown;
        this.homeScore = homeScore;
        this.awayScore = awayScore;
        this.homeName = homeName == null ? "" : homeName;
        this.awayName = awayName == null ? "" : awayName;
        this.homeId = homeId;
        this.awayId = awayId;
        this.venue = venue;
        this.officials = immutable(officials);
        this.events = immutable(events);
        this.homeLineup = homeLineup;
        this.awayLineup = awayLineup;
        this.statCategories = immutable(statCategories);
        this.hasStats = hasStats;
        this.statsError = statsError == null ? "" : statsError;
        this.tvNetworks = immutableNetworks(tvNetworks);
        this.fetchedAt = fetchedAt;
        this.fromCache = fromCache;
    }

    /** Copia con la marca temporal y el origen de caché indicados. */
    MatchDetail withFetch(long fetchedAt, boolean fromCache) {
        return new MatchDetail(gameId, sourceId, competition, sport, startMillis,
                status, statusText, gameTimeDisplay, scoreKnown, homeScore, awayScore,
                homeName, awayName, homeId, awayId, venue, officials, events,
                homeLineup, awayLineup, statCategories, hasStats, statsError,
                tvNetworks, fetchedAt, fromCache);
    }

    /** Copia sin estadísticas y con un error corto para la UI. */
    MatchDetail withStatsError(String error) {
        return new MatchDetail(gameId, sourceId, competition, sport, startMillis,
                status, statusText, gameTimeDisplay, scoreKnown, homeScore, awayScore,
                homeName, awayName, homeId, awayId, venue, officials, events,
                homeLineup, awayLineup, Collections.emptyList(), false,
                error == null ? "" : error, tvNetworks, fetchedAt, fromCache);
    }

    public boolean hasEvents() {
        return !events.isEmpty();
    }

    public boolean hasLineups() {
        return (homeLineup != null && !homeLineup.starters.isEmpty())
                || (awayLineup != null && !awayLineup.starters.isEmpty());
    }

    public boolean hasStats() {
        return hasStats;
    }

    public boolean isFinished() {
        return status.isFinished();
    }

    public boolean isLive() {
        return status.isLive();
    }

    private static <T> List<T> immutable(List<T> source) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static List<String> immutableNetworks(List<String> source) {
        if (source == null || source.isEmpty()) return Collections.emptyList();

        List<String> result = new ArrayList<>();
        for (String network : source) {
            if (network == null) continue;
            String value = network.trim();
            if (!value.isEmpty()) result.add(value);
        }
        return result.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(result);
    }
}
