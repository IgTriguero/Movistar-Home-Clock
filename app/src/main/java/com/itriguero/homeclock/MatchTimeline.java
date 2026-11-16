package com.itriguero.homeclock;

import java.util.List;
import java.util.Locale;

/**
 * Decisiones puras de la cronología: qué eventos van centrados (VAR y
 * neutrales) y dónde se coloca el descanso.
 *
 * <p>Sin Android, para poder probarlas en la JVM. No depende de
 * {@link MatchDetail}: recibe solo lo que necesita.
 */
final class MatchTimeline {

    private static final Locale ES = new Locale("es", "ES");

    private MatchTimeline() {
    }

    /**
     * Un VAR nunca pertenece a un lado, aunque el parser le asigne
     * {@link MatchEvent.Side#HOME} o {@link MatchEvent.Side#AWAY} porque el
     * evento traiga {@code competitorId}. El resto de eventos neutrales
     * tampoco tienen lado.
     */
    static boolean isNeutral(MatchEvent event) {
        return event != null
                && (event.type == MatchEvent.Type.VAR
                || event.side == MatchEvent.Side.NEUTRAL);
    }

    /**
     * Elige el nombre más largo que cabe en el ancho disponible: el completo,
     * el corto o {@code null} si ninguno cabe (elipsis como último recurso).
     * Las anchuras se pasan medidas para que la decisión sea pura y testeable.
     */
    static String fitName(String fullName, String shortName,
                          int fullWidth, int shortWidth, int availableWidth) {
        if (fullName == null || fullName.isEmpty()) return null;
        if (fullWidth <= availableWidth) return fullName;
        if (shortName != null && !shortName.isEmpty() && shortWidth <= availableWidth) {
            return shortName;
        }
        return null;
    }

    /**
     * Índice del evento ante el que va el descanso: el primero del segundo
     * tiempo (minuto >= 46). Un 45+2 sigue siendo del primer tiempo y queda
     * antes. Si ya se pasó el descanso y no hay eventos del segundo tiempo, el
     * divisor va al final de la lista.
     *
     * @return el índice, o {@code -1} si aún no se ha llegado al descanso.
     */
    static int halftimeIndex(List<MatchEvent> events, boolean passedHalftime) {
        if (events == null || events.isEmpty()) {
            return passedHalftime ? 0 : -1;
        }
        for (int i = 0; i < events.size(); i++) {
            MatchEvent event = events.get(i);
            if (event != null && event.minute >= 46) return i;
        }
        return passedHalftime ? events.size() : -1;
    }

    /**
     * Texto de estado que significa descanso. Los patrones son exactos: un
     * "1st half" o un "2nd half" no son descanso.
     */
    static boolean isBreakText(String statusText) {
        if (statusText == null) return false;
        String text = statusText.toLowerCase(ES);
        return text.contains("descanso")
                || text.contains("half time")
                || text.contains("halftime")
                || text.contains("half-time")
                || text.contains("medio tiempo");
    }

    /**
     * Indica si el partido ya pasó el descanso. En directo se apoya en el
     * tiempo de juego o en el texto de estado; un partido finalizado siempre
     * lo pasó.
     */
    static boolean hasPassedHalftime(MatchStatus status, String statusText,
                                     String gameTimeDisplay) {
        if (status == null) return false;
        if (status.isFinished()) return true;
        if (!status.isInPlay()) return false;
        if (isBreakText(statusText)) return true;
        return gameMinute(gameTimeDisplay) > 45;
    }

    /** Minutos transcurridos de un "83:12"; 0 si no se pueden leer. */
    static int gameMinute(String gameTimeDisplay) {
        if (gameTimeDisplay == null) return 0;
        String value = gameTimeDisplay.trim();
        int colon = value.indexOf(':');
        if (colon > 0) value = value.substring(0, colon);
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
