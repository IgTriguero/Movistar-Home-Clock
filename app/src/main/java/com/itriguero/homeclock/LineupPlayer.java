package com.itriguero.homeclock;

/**
 * Un jugador de la alineación de un equipo.
 *
 * El nombre y el dorsal se resuelven contra el diccionario {@code members}
 * del detalle; si no están disponibles se usan valores vacíos/seguros.
 */
final class LineupPlayer {

    public final long playerId;
    public final String name;
    public final int number;
    public final String position;
    public final String positionShort;
    /** Nota de la fuente; -1 si no está disponible. */
    public final double rating;
    public final boolean starter;

    public LineupPlayer(long playerId, String name, int number,
                        String position, String positionShort,
                        double rating, boolean starter) {
        this.playerId = playerId;
        this.name = name == null || name.trim().isEmpty() ? "Jugador" : name.trim();
        this.number = Math.max(0, number);
        this.position = position == null ? "" : position;
        this.positionShort = positionShort == null ? "" : positionShort;
        this.rating = rating < 0 ? -1 : rating;
        this.starter = starter;
    }
}
