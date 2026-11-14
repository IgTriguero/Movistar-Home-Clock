package com.itriguero.homeclock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Alineación de un equipo: titulares, suplentes, bajas y entrenador.
 */
final class TeamLineup {

    public final long teamId;
    public final String teamName;
    public final String formation;
    public final String status;
    public final String coach;
    public final List<LineupPlayer> starters;
    public final List<LineupPlayer> substitutes;
    public final List<LineupPlayer> missing;

    public TeamLineup(long teamId, String teamName, String formation, String status,
                      String coach, List<LineupPlayer> starters,
                      List<LineupPlayer> substitutes, List<LineupPlayer> missing) {
        this.teamId = teamId;
        this.teamName = teamName == null ? "" : teamName;
        this.formation = formation == null ? "" : formation;
        this.status = status == null ? "" : status;
        this.coach = coach == null ? "" : coach;
        this.starters = immutable(starters);
        this.substitutes = immutable(substitutes);
        this.missing = immutable(missing);
    }

    private static List<LineupPlayer> immutable(List<LineupPlayer> source) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}
