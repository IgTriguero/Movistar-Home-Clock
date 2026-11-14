package com.itriguero.homeclock;

/**
 * Un evento de la línea temporal de un partido: gol, tarjeta, cambio, VAR...
 *
 * Es un objeto puro (sin Android) para poder construirlo desde el parser y
 * probarlo en la JVM.
 */
final class MatchEvent {

    /** Tipo normalizado del evento, independiente de los ids de la fuente. */
    enum Type {
        GOAL,
        PENALTY_GOAL,
        OWN_GOAL,
        DISALLOWED_GOAL,
        YELLOW_CARD,
        RED_CARD,
        SECOND_YELLOW,
        SUBSTITUTION,
        VAR,
        OTHER
    }

    /** Equipo al que pertenece el evento. */
    enum Side {
        HOME,
        AWAY,
        NEUTRAL
    }

    public final int order;
    public final int minute;
    public final int addedTime;
    /** Minuto ya formateado para la UI, p. ej. {@code 45+1'}. */
    public final String display;
    public final Type type;
    public final Side side;
    public final String playerName;
    public final int playerNumber;
    /**
     * Actor secundario: el asistente en un gol o el jugador que sale en una
     * sustitución. Cadena vacía si no aplica.
     */
    public final String secondaryName;
    public final boolean major;

    public MatchEvent(int order, int minute, int addedTime, String display,
                      Type type, Side side, String playerName, int playerNumber,
                      String secondaryName, boolean major) {
        this.order = order;
        this.minute = Math.max(0, minute);
        this.addedTime = Math.max(0, addedTime);
        this.display = display == null ? "" : display;
        this.type = type == null ? Type.OTHER : type;
        this.side = side == null ? Side.NEUTRAL : side;
        this.playerName = playerName == null ? "" : playerName;
        this.playerNumber = Math.max(0, playerNumber);
        this.secondaryName = secondaryName == null ? "" : secondaryName;
        this.major = major;
    }
}
