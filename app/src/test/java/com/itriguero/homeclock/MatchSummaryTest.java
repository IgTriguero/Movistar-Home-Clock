package com.itriguero.homeclock;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

/**
 * Reglas del resumen de goles de la cabecera: propia, penalti, anulado,
 * neutral y marcador oficial.
 */
public class MatchSummaryTest {

    @Test
    public void sinEventosNoHayGoleadoresNiNombres() {
        MatchSummary summary = MatchSummary.from(0, 0, Collections.emptyList());

        assertEquals(0, summary.count(true));
        assertEquals(0, summary.count(false));
        assertEquals("", summary.scorerLine(true, 2, 0));
        assertEquals("", summary.scorerLine(false, 2, 0));
    }

    @Test
    public void goleadoresSeRepartenPorLadoYElPenaltiCuenta() {
        MatchSummary summary = MatchSummary.from(2, 1, Arrays.asList(
                event(MatchEvent.Type.GOAL, MatchEvent.Side.HOME, "Pedro Porro"),
                event(MatchEvent.Type.PENALTY_GOAL, MatchEvent.Side.HOME, "Álvaro Morata"),
                event(MatchEvent.Type.GOAL, MatchEvent.Side.AWAY, "Harry Kane")));

        assertEquals("Porro, Morata", summary.scorerLine(true, 2, 2));
        assertEquals("Kane", summary.scorerLine(false, 2, 1));
        assertEquals(2, summary.count(true));
        assertEquals(1, summary.count(false));
    }

    @Test
    public void elGolEnPropiaCuentaParaElRival() {
        MatchSummary summary = MatchSummary.from(0, 1, Collections.singletonList(
                event(MatchEvent.Type.OWN_GOAL, MatchEvent.Side.HOME, "Declan Rice")));

        assertEquals("", summary.scorerLine(true, 2, 0));
        assertEquals("Rice (p.p.)", summary.scorerLine(false, 2, 1));
        assertEquals(1, summary.count(false));
    }

    @Test
    public void elGolEnPropiaVisitanteCuentaParaElLocal() {
        MatchSummary summary = MatchSummary.from(1, 0, Collections.singletonList(
                event(MatchEvent.Type.OWN_GOAL, MatchEvent.Side.AWAY, "Declan Rice")));

        assertEquals("Rice (p.p.)", summary.scorerLine(true, 2, 1));
        assertEquals("", summary.scorerLine(false, 2, 0));
        assertEquals(1, summary.count(true));
    }

    @Test
    public void elGolAnuladoNoCuenta() {
        MatchSummary summary = MatchSummary.from(0, 0, Arrays.asList(
                event(MatchEvent.Type.DISALLOWED_GOAL, MatchEvent.Side.HOME, "Lamine Yamal"),
                event(MatchEvent.Type.DISALLOWED_GOAL, MatchEvent.Side.AWAY, "Harry Kane")));

        assertEquals("", summary.scorerLine(true, 2, 0));
        assertEquals("", summary.scorerLine(false, 2, 0));
    }

    @Test
    public void elLadoNeutralSeIgnoraAunqueSeaGol() {
        MatchSummary summary = MatchSummary.from(0, 0, Collections.singletonList(
                event(MatchEvent.Type.GOAL, MatchEvent.Side.NEUTRAL, "Jugador neutral")));

        assertEquals("", summary.scorerLine(true, 2, 0));
        assertEquals("", summary.scorerLine(false, 2, 0));
    }

    @Test
    public void elMarcadorOficialMandaAunqueFaltenEventos() {
        MatchSummary summary = MatchSummary.from(3, 1, Collections.singletonList(
                event(MatchEvent.Type.GOAL, MatchEvent.Side.HOME, "Pedro Porro")));

        // La fuente no publica todos los eventos: el conteo es el oficial.
        assertEquals(3, summary.count(true));
        assertEquals(1, summary.count(false));
        assertEquals("Porro", summary.scorerLine(true, 2, 3));
        assertEquals("", summary.scorerLine(false, 2, 1));
    }

    @Test
    public void nombreCortoUsaLaUltimaPalabra() {
        assertEquals("Porro", MatchSummary.shortName("Pedro Porro"));
        assertEquals("Morata", MatchSummary.shortName("Álvaro Morata"));
        assertEquals("Morata", MatchSummary.shortName("Morata"));
        assertEquals("Porro", MatchSummary.shortName("  Pedro   Porro  "));
        assertEquals("", MatchSummary.shortName(""));
        assertEquals("", MatchSummary.shortName(null));
    }

    @Test
    public void laLineaDeGoleadoresLimitaYDonaElResto() {
        MatchSummary summary = MatchSummary.from(3, 0, Arrays.asList(
                event(MatchEvent.Type.GOAL, MatchEvent.Side.HOME, "Pedro Porro"),
                event(MatchEvent.Type.GOAL, MatchEvent.Side.HOME, "Álvaro Morata"),
                event(MatchEvent.Type.GOAL, MatchEvent.Side.HOME, "Mikel Oyarzabal")));

        assertEquals("Porro, Morata +1", summary.scorerLine(true, 2, 3));
        assertEquals("Porro +2", summary.scorerLine(true, 1, 3));
        assertEquals("Porro, Morata, Oyarzabal", summary.scorerLine(true, 5, 3));
    }

    @Test
    public void elMarcadorOficialLimitaLosNombresDeLaCabecera() {
        // Dos eventos de gol, pero el marcador oficial es 1-0: sobra un nombre.
        MatchSummary summary = MatchSummary.from(1, 0, Arrays.asList(
                event(MatchEvent.Type.GOAL, MatchEvent.Side.HOME, "Pedro Porro"),
                event(MatchEvent.Type.GOAL, MatchEvent.Side.HOME, "Álvaro Morata")));

        assertEquals("Porro", summary.scorerLine(true, 2, 1));
        assertEquals("", summary.scorerLine(true, 2, 0));
    }

    @Test
    public void laListaNulaEsSegura() {
        MatchSummary summary = MatchSummary.from(1, 2, null);

        assertEquals(1, summary.count(true));
        assertEquals(2, summary.count(false));
        assertEquals("", summary.scorerLine(true, 2, 1));
    }

    private static MatchEvent event(MatchEvent.Type type, MatchEvent.Side side, String player) {
        return new MatchEvent(1, 20, 0, "20'", type, side, player, 0, "", false);
    }
}
