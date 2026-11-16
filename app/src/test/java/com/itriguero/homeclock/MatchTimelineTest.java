package com.itriguero.homeclock;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Decisiones puras de la cronología: neutralidad del VAR, descanso y tiempo
 * de juego.
 */
public class MatchTimelineTest {

    @Test
    public void unVarConCompetidorSeTrataComoNeutral() {
        // El parser asigna HOME/AWAY si el evento trae competitorId.
        assertTrue(MatchTimeline.isNeutral(event(
                MatchEvent.Type.VAR, MatchEvent.Side.AWAY, 82)));
        assertTrue(MatchTimeline.isNeutral(event(
                MatchEvent.Type.VAR, MatchEvent.Side.HOME, 82)));
        assertTrue(MatchTimeline.isNeutral(event(
                MatchEvent.Type.VAR, MatchEvent.Side.NEUTRAL, 82)));
    }

    @Test
    public void unGolVisitanteNoEsNeutral() {
        assertFalse(MatchTimeline.isNeutral(event(
                MatchEvent.Type.GOAL, MatchEvent.Side.AWAY, 21)));
        assertTrue(MatchTimeline.isNeutral(event(
                MatchEvent.Type.GOAL, MatchEvent.Side.NEUTRAL, 21)));
    }

    @Test
    public void el45Mas2QuedaAntesDelDescansoYEl46Despues() {
        List<MatchEvent> events = Arrays.asList(
                event(MatchEvent.Type.GOAL, MatchEvent.Side.HOME, 21),
                event(MatchEvent.Type.GOAL, MatchEvent.Side.AWAY, 45, 2),
                event(MatchEvent.Type.GOAL, MatchEvent.Side.AWAY, 46));

        // El 45+2 tiene minute=45; el descanso va justo antes del 46.
        assertEquals(2, MatchTimeline.halftimeIndex(events, true));
    }

    @Test
    public void sinEventosDelSegundoTiempoElDescansoVaAlFinal() {
        List<MatchEvent> firstHalf = Arrays.asList(
                event(MatchEvent.Type.GOAL, MatchEvent.Side.HOME, 21),
                event(MatchEvent.Type.YELLOW_CARD, MatchEvent.Side.AWAY, 45, 2));

        assertEquals(firstHalf.size(), MatchTimeline.halftimeIndex(firstHalf, true));
    }

    @Test
    public void soloPrimeraParteEnDirectoNoTieneDescanso() {
        List<MatchEvent> events = Arrays.asList(
                event(MatchEvent.Type.GOAL, MatchEvent.Side.HOME, 21));

        assertFalse(MatchTimeline.hasPassedHalftime(
                MatchStatus.LIVE, "1st half", "30:00"));
        assertEquals(-1, MatchTimeline.halftimeIndex(events, false));
    }

    @Test
    public void soloSegundaParteElDescansoVaAlPrincipio() {
        List<MatchEvent> events = Arrays.asList(
                event(MatchEvent.Type.GOAL, MatchEvent.Side.AWAY, 60));

        assertEquals(0, MatchTimeline.halftimeIndex(events, true));
    }

    @Test
    public void unPartidoFinalizadoSiemprePasoElDescanso() {
        assertTrue(MatchTimeline.hasPassedHalftime(MatchStatus.FINISHED, "", ""));

        List<MatchEvent> firstHalf = Arrays.asList(
                event(MatchEvent.Type.GOAL, MatchEvent.Side.HOME, 21));
        assertEquals(firstHalf.size(),
                MatchTimeline.halftimeIndex(firstHalf, true));
    }

    @Test
    public void elDescansoSeDetectaPorTextoOTiempoDeJuego() {
        assertTrue(MatchTimeline.hasPassedHalftime(
                MatchStatus.LIVE, "Descanso", "45:00"));
        assertTrue(MatchTimeline.hasPassedHalftime(
                MatchStatus.LIVE, "Half Time", "45:00"));
        assertTrue(MatchTimeline.hasPassedHalftime(
                MatchStatus.LIVE, "Halftime", "45:00"));
        assertTrue(MatchTimeline.hasPassedHalftime(
                MatchStatus.LIVE, "Half-Time", "45:00"));
        assertTrue(MatchTimeline.hasPassedHalftime(
                MatchStatus.LIVE, "2nd half", "67:12"));
        assertFalse(MatchTimeline.hasPassedHalftime(
                MatchStatus.LIVE, "2nd half", "30:00"));
    }

    @Test
    public void elTextoDeDescansoNoConfundePrimeraYsegundaParte() {
        assertTrue(MatchTimeline.isBreakText("Descanso"));
        assertTrue(MatchTimeline.isBreakText("Half Time"));
        assertTrue(MatchTimeline.isBreakText("Halftime"));
        assertFalse(MatchTimeline.isBreakText("1st half"));
        assertFalse(MatchTimeline.isBreakText("2nd half"));
        assertFalse(MatchTimeline.isBreakText(null));
    }

    @Test
    public void elNombreCompletoTienePrioridadSobreElCorto() {
        assertEquals("Jude Bellingham",
                MatchTimeline.fitName("Jude Bellingham", "Bellingham", 300, 120, 400));
        assertEquals("Bellingham",
                MatchTimeline.fitName("Jude Bellingham", "Bellingham", 420, 120, 400));
        assertEquals("Mönchengladbach",
                MatchTimeline.fitName("Borussia Mönchengladbach", "Mönchengladbach",
                        500, 250, 400));
        assertNull(MatchTimeline.fitName(
                "Jude Bellingham", "Bellingham", 420, 410, 400));
    }

    @Test
    public void unPartidoProgramadoNoHaPasadoElDescanso() {
        assertFalse(MatchTimeline.hasPassedHalftime(MatchStatus.SCHEDULED, "", ""));
        assertFalse(MatchTimeline.hasPassedHalftime(null, null, null));
    }

    @Test
    public void elMinutoDeJuegoSeLeeDelReloj() {
        assertEquals(83, MatchTimeline.gameMinute("83:12"));
        assertEquals(45, MatchTimeline.gameMinute("45"));
        assertEquals(0, MatchTimeline.gameMinute("live"));
        assertEquals(0, MatchTimeline.gameMinute(null));
    }

    private static MatchEvent event(MatchEvent.Type type, MatchEvent.Side side, int minute) {
        return event(type, side, minute, 0);
    }

    private static MatchEvent event(MatchEvent.Type type, MatchEvent.Side side,
                                    int minute, int addedTime) {
        String display = addedTime > 0
                ? minute + "+" + addedTime + "'"
                : minute + "'";
        return new MatchEvent(1, minute, addedTime, display, type, side,
                "Jugador", 0, "", false);
    }
}
