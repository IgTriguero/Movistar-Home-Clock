package com.itriguero.homeclock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resumen de goles de un partido calculado a partir de la lista de eventos.
 *
 * <p>Es un objeto puro (sin Android) para poder probarlo en la JVM. El conteo
 * que manda es el marcador oficial; los nombres de los goleadores se obtienen
 * de los eventos válidos, en su orden. Las reglas son las del fútbol:
 *
 * <ul>
 *   <li>{@link MatchEvent.Type#GOAL} y {@link MatchEvent.Type#PENALTY_GOAL}
 *       cuentan para el lado del evento.</li>
 *   <li>{@link MatchEvent.Type#OWN_GOAL} cuenta para el lado contrario.</li>
 *   <li>{@link MatchEvent.Type#DISALLOWED_GOAL} no cuenta.</li>
 *   <li>Los eventos {@link MatchEvent.Side#NEUTRAL} se ignoran.</li>
 * </ul>
 *
 * <p>La fuente puede no publicar todos los goles, por eso el conteo nunca se
 * deduce del tamaño de la lista.
 */
final class MatchSummary {

    private final int homeCount;
    private final int awayCount;
    private final List<Scorer> homeScorers;
    private final List<Scorer> awayScorers;

    private MatchSummary(int homeCount, int awayCount,
                         List<Scorer> homeScorers, List<Scorer> awayScorers) {
        this.homeCount = Math.max(0, homeCount);
        this.awayCount = Math.max(0, awayCount);
        this.homeScorers = homeScorers;
        this.awayScorers = awayScorers;
    }

    /** Goleador con la marca de propia puerta, para la cabecera. */
    private static final class Scorer {
        final String name;
        final boolean ownGoal;

        Scorer(String name, boolean ownGoal) {
            this.name = name;
            this.ownGoal = ownGoal;
        }
    }

    /**
     * Construye el resumen con el marcador oficial y los eventos disponibles.
     * {@code events} puede ser {@code null}.
     */
    static MatchSummary from(int homeCount, int awayCount, List<MatchEvent> events) {
        if (events == null || events.isEmpty()) {
            return new MatchSummary(homeCount, awayCount,
                    Collections.emptyList(), Collections.emptyList());
        }

        List<Scorer> home = new ArrayList<>();
        List<Scorer> away = new ArrayList<>();
        for (MatchEvent event : events) {
            if (event == null || event.type == null) continue;

            boolean homeScorer;
            switch (event.type) {
                case GOAL:
                case PENALTY_GOAL:
                    if (event.side == MatchEvent.Side.HOME) {
                        homeScorer = true;
                    } else if (event.side == MatchEvent.Side.AWAY) {
                        homeScorer = false;
                    } else {
                        continue;
                    }
                    break;
                case OWN_GOAL:
                    // El gol en propia puerta suma al rival.
                    if (event.side == MatchEvent.Side.HOME) {
                        homeScorer = false;
                    } else if (event.side == MatchEvent.Side.AWAY) {
                        homeScorer = true;
                    } else {
                        continue;
                    }
                    break;
                default:
                    continue;
            }

            String name = event.playerName == null ? "" : event.playerName.trim();
            if (name.isEmpty()) continue;
            (homeScorer ? home : away).add(
                    new Scorer(name, event.type == MatchEvent.Type.OWN_GOAL));
        }

        return new MatchSummary(homeCount, awayCount,
                Collections.unmodifiableList(home),
                Collections.unmodifiableList(away));
    }

    /** Goles oficiales de un lado, tal y como los publica la fuente. */
    int count(boolean home) {
        return home ? homeCount : awayCount;
    }

    /**
     * Nombres cortos para la cabecera: hasta {@code maxNames}, separados por
     * coma y con {@code +N} si quedan más. Los goles en propia se marcan como
     * {@code (p.p.)}.
     *
     * <p>El número de nombres nunca supera el marcador oficial: si la fuente
     * publica más eventos de gol que goles, se ignoran los sobrantes. El
     * {@code +N} solo cuenta nombres disponibles que no se muestran, nunca
     * goles de los que no hay evento.
     */
    String scorerLine(boolean home, int maxNames, int officialCount) {
        List<Scorer> entries = home ? homeScorers : awayScorers;
        int total = Math.min(entries.size(), Math.max(0, officialCount));
        if (total <= 0) return "";

        int limit = Math.max(1, maxNames);
        int shown = Math.min(limit, total);
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < shown; i++) {
            if (i > 0) line.append(", ");
            Scorer scorer = entries.get(i);
            line.append(shortName(scorer.name));
            if (scorer.ownGoal) line.append(" (p.p.)");
        }
        int remaining = total - shown;
        if (remaining > 0) line.append(" +").append(remaining);
        return line.toString();
    }

    /**
     * Nombre corto para la cabecera: la última palabra. "Pedro Porro" queda en
     * "Porro"; un nombre de una sola palabra se deja igual.
     */
    static String shortName(String fullName) {
        if (fullName == null) return "";
        String value = fullName.trim();
        if (value.isEmpty()) return "";

        int space = value.lastIndexOf(' ');
        return space < 0 ? value : value.substring(space + 1).trim();
    }
}
