package com.itriguero.homeclock;

/**
 * Última selección de equipos publicada desde Ajustes y leída por el resto de
 * la app. El campo es volátil para que un refresco en segundo plano vea siempre
 * una selección completa, nunca una lista a medio mutar.
 */
final class TeamSelectionHolder {

    private volatile TeamSelection selection = TeamSelection.EMPTY;

    TeamSelection get() {
        return selection;
    }

    void set(TrackedTeamList teams) {
        selection = teams == null ? TeamSelection.EMPTY : teams.selection();
    }
}
