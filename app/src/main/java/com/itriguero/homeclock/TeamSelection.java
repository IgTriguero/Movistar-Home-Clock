package com.itriguero.homeclock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * Selección inmutable de equipos activos, preparada para que los clientes de
 * datos no dependan de la lista mutable de Ajustes.
 */
final class TeamSelection {

    static final TeamSelection EMPTY =
            new TeamSelection(Collections.emptyList(), false, "");

    private final List<Long> scores365Ids;
    private final boolean asCeltasEnabled;
    private final String signature;

    TeamSelection(List<Long> scores365Ids, boolean asCeltasEnabled, String signature) {
        TreeSet<Long> sorted = new TreeSet<>();
        if (scores365Ids != null) {
            for (Long id : scores365Ids) {
                if (id != null) sorted.add(id);
            }
        }
        this.scores365Ids = Collections.unmodifiableList(new ArrayList<>(sorted));
        this.asCeltasEnabled = asCeltasEnabled;
        this.signature = signature == null ? "" : signature;
    }

    /** IDs de 365Scores activos, sin duplicados y en orden ascendente. */
    List<Long> scores365Ids() {
        return scores365Ids;
    }

    boolean asCeltasEnabled() {
        return asCeltasEnabled;
    }

    boolean isEmpty() {
        return scores365Ids.isEmpty() && !asCeltasEnabled;
    }

    /** Coincide con {@link TrackedTeamList#activeSignature()}. */
    String signature() {
        return signature;
    }
}
