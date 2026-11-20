package com.itriguero.homeclock;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MatchDetailParserTest {

    @Test
    public void parsesFinishedFootballDetailFromFixture() throws Exception {
        MatchDetail detail = parseFootball(null);

        assertNotNull(detail);
        assertEquals(4750793L, detail.gameId);
        assertEquals("365scores", detail.sourceId);
        assertEquals("España, LaLiga", detail.competition);
        assertEquals("Fútbol", detail.sport);
        assertEquals(OffsetDateTime.parse("2026-11-07T21:00:00+01:00")
                .toInstant().toEpochMilli(), detail.startMillis);
        assertEquals(MatchStatus.FINISHED, detail.status);
        assertEquals("Finalizado", detail.statusText);
        assertTrue(detail.scoreKnown);
        assertEquals(0, detail.homeScore);
        assertEquals(3, detail.awayScore);
        assertEquals("Valencia CF", detail.homeName);
        assertEquals("Real Madrid", detail.awayName);
        assertEquals(139L, detail.homeId);
        assertEquals(131L, detail.awayId);
        assertTrue(detail.isFinished());
        assertNotNull(detail.homeLineup);
        assertNotNull(detail.awayLineup);
        assertFalse(detail.homeLineup.starters.isEmpty());
        assertFalse(detail.awayLineup.starters.isEmpty());
        assertEquals(10, detail.events.size());
        assertEquals(0L, detail.fetchedAt);
        assertFalse(detail.fromCache);
    }

    @Test
    public void parsesNormalGoalWithScorerAndShirtNumber() throws Exception {
        MatchEvent goal = eventWithOrder(parseFootball(null), 2);

        assertEquals(MatchEvent.Type.GOAL, goal.type);
        assertEquals(MatchEvent.Side.AWAY, goal.side);
        assertEquals("Federico Valverde", goal.playerName);
        assertEquals(8, goal.playerNumber);
        assertEquals("", goal.secondaryName);
        assertEquals(28, goal.minute);
        assertEquals(0, goal.addedTime);
        assertEquals("28'", goal.display);
        assertTrue(goal.major);
    }

    @Test
    public void parsesGoalWithAssistant() throws Exception {
        MatchEvent goal = eventWithOrder(parseFootball(null), 3);

        assertEquals(MatchEvent.Type.GOAL, goal.type);
        assertEquals("Arda Güler", goal.playerName);
        assertEquals(15, goal.playerNumber);
        assertEquals("Jude Bellingham", goal.secondaryName);
    }

    @Test
    public void parsesPenaltyGoal() throws Exception {
        MatchDetail detail = parseEvent("{\"competitorId\":10,\"order\":1,"
                + "\"gameTime\":12,\"addedTime\":0,\"gameTimeDisplay\":\"12'\","
                + "\"playerId\":7,\"isMajor\":true,"
                + "\"eventType\":{\"id\":1,\"name\":\"Gol\",\"subTypeName\":\"Penalti\"}}");

        MatchEvent event = detail.events.get(0);
        assertEquals(MatchEvent.Type.PENALTY_GOAL, event.type);
        assertEquals("Jugador X", event.playerName);
        assertEquals(9, event.playerNumber);
    }

    @Test
    public void parsesDisallowedGoal() throws Exception {
        MatchDetail detail = parseEvent("{\"competitorId\":10,\"order\":1,"
                + "\"gameTime\":33,\"addedTime\":0,\"gameTimeDisplay\":\"33'\","
                + "\"playerId\":7,\"eventType\":{\"id\":11,\"name\":\"Gol anulado\"}}");

        assertEquals(MatchEvent.Type.DISALLOWED_GOAL, detail.events.get(0).type);
    }

    @Test
    public void parsesWoodworkAsOther() throws Exception {
        MatchDetail detail = parseEvent("{\"competitorId\":20,\"order\":1,"
                + "\"gameTime\":70,\"addedTime\":0,\"gameTimeDisplay\":\"70'\","
                + "\"playerId\":7,\"eventType\":{\"id\":12,\"name\":\"Palo\"}}");

        MatchEvent event = detail.events.get(0);
        assertEquals(MatchEvent.Type.OTHER, event.type);
        assertEquals(MatchEvent.Side.AWAY, event.side);
    }

    @Test
    public void parsesYellowAndRedCards() throws Exception {
        MatchDetail detail = parseFootball(null);

        MatchEvent yellow = eventWithOrder(detail, 1);
        assertEquals(MatchEvent.Type.YELLOW_CARD, yellow.type);
        assertEquals("Pablo Maffeo", yellow.playerName);
        assertEquals(24, yellow.playerNumber);
        assertEquals(MatchEvent.Side.HOME, yellow.side);

        MatchEvent red = eventWithOrder(detail, 12);
        assertEquals(MatchEvent.Type.RED_CARD, red.type);
        assertEquals("José Gayà", red.playerName);
    }

    @Test
    public void parsesSecondYellowCard() throws Exception {
        MatchDetail detail = parseEvent("{\"competitorId\":10,\"order\":1,"
                + "\"gameTime\":80,\"addedTime\":0,\"gameTimeDisplay\":\"80'\","
                + "\"playerId\":7,\"eventType\":{\"id\":2,\"name\":\"Tarjeta amarilla\","
                + "\"subTypeName\":\"Segunda amarilla\"}}");

        assertEquals(MatchEvent.Type.SECOND_YELLOW, detail.events.get(0).type);
    }

    @Test
    public void parsesOwnGoalAndVar() throws Exception {
        MatchDetail ownGoal = parseEvent("{\"competitorId\":10,\"order\":1,"
                + "\"gameTime\":20,\"addedTime\":0,\"gameTimeDisplay\":\"20'\","
                + "\"playerId\":7,\"eventType\":{\"id\":1,\"name\":\"Gol\","
                + "\"subTypeName\":\"Gol en propia puerta\"}}");
        assertEquals(MatchEvent.Type.OWN_GOAL, ownGoal.events.get(0).type);

        MatchDetail var = parseEvent("{\"competitorId\":20,\"order\":1,"
                + "\"gameTime\":55,\"addedTime\":0,\"gameTimeDisplay\":\"55'\","
                + "\"eventType\":{\"id\":13,\"name\":\"VAR\"}}");
        assertEquals(MatchEvent.Type.VAR, var.events.get(0).type);
    }

    @Test
    public void parsesSubstitutionWithPlayerInAndOut() throws Exception {
        MatchEvent substitution = eventWithOrder(parseFootball(null), 5);

        assertEquals(MatchEvent.Type.SUBSTITUTION, substitution.type);
        assertEquals(MatchEvent.Side.HOME, substitution.side);
        assertEquals("Luis Rioja", substitution.playerName);
        assertEquals(11, substitution.playerNumber);
        assertEquals("Justin de Haas", substitution.secondaryName);
        assertEquals("46'", substitution.display);
    }

    @Test
    public void formatsAddedTimeMinute() throws Exception {
        MatchDetail detail = parseFootball(null);

        MatchEvent yellow = eventWithOrder(detail, 4);
        assertEquals(45, yellow.minute);
        assertEquals(1, yellow.addedTime);
        assertEquals("45+1'", yellow.display);

        MatchEvent lateSubstitution = eventWithOrder(detail, 21);
        assertEquals(90, lateSubstitution.minute);
        assertEquals(1, lateSubstitution.addedTime);
        assertEquals("90+1'", lateSubstitution.display);
        assertEquals("Carlos Espí", lateSubstitution.playerName);
        assertEquals("Kylian Mbappe", lateSubstitution.secondaryName);
    }

    @Test
    public void parsesLineupsWithStartersSubstitutesMissingAndCoach() throws Exception {
        MatchDetail detail = parseFootball(null);

        TeamLineup home = detail.homeLineup;
        assertNotNull(home);
        assertEquals(139L, home.teamId);
        assertEquals("Valencia CF", home.teamName);
        assertEquals("5-3-2", home.formation);
        assertEquals("Confirmado", home.status);
        assertEquals("Javier Aguirre", home.coach);
        assertEquals(11, home.starters.size());
        assertEquals(7, home.substitutes.size());
        assertEquals(4, home.missing.size());

        LineupPlayer keeper = home.starters.get(0);
        assertEquals(502893L, keeper.playerId);
        assertEquals("Stole Dimitrievski", keeper.name);
        assertEquals(1, keeper.number);
        assertEquals("Portero", keeper.position);
        assertEquals("Goalkeeper", keeper.positionShort);
        assertEquals(6.1, keeper.rating, 0.001);
        assertTrue(keeper.starter);

        LineupPlayer substitute = home.substitutes.get(3);
        assertEquals("Luis Rioja", substitute.name);
        assertEquals(11, substitute.number);
        assertFalse(substitute.starter);

        TeamLineup away = detail.awayLineup;
        assertNotNull(away);
        assertEquals("Real Madrid", away.teamName);
        assertEquals("4-2-3-1", away.formation);
        assertEquals("José Mourinho", away.coach);
        assertEquals(11, away.starters.size());
        assertEquals(7, away.substitutes.size());
        assertEquals(4, away.missing.size());
        assertEquals(9.4, away.starters.get(9).rating, 0.001);
        assertEquals("Arda Güler", away.starters.get(9).name);
    }

    @Test
    public void parsesVenueAndReferee() throws Exception {
        MatchDetail detail = parseFootball(null);

        assertNotNull(detail.venue);
        assertEquals("Estadio de Mestalla", detail.venue.name);
        assertEquals(48600, detail.venue.capacity);
        assertEquals(47914, detail.venue.attendance);

        assertEquals(1, detail.officials.size());
        assertEquals("Francisco Hernández", detail.officials.get(0).name);
        assertEquals("Árbitro", detail.officials.get(0).role);
    }

    @Test
    public void parsesTvNetworksFromGame() throws Exception {
        MatchDetail detail = parseFootball(null);

        assertEquals(2, detail.tvNetworks.size());
        assertEquals("Movistar Plus+", detail.tvNetworks.get(0));
        assertEquals("DAZN", detail.tvNetworks.get(1));
    }

    @Test
    public void parsesTvNetworksFromRootWhenGameHasNone() throws Exception {
        MatchDetail detail = MatchDetailParser.parse(
                fixture("scores365_game_scheduled.json"), null, null);

        assertNotNull(detail);
        assertEquals(1, detail.tvNetworks.size());
        assertEquals("La 1", detail.tvNetworks.get(0));
    }

    @Test
    public void ignoresEmptyTvNetworkNamesAndMissingArray() throws Exception {
        JSONObject game = new JSONObject()
                .put("id", 88)
                .put("sportId", 1)
                .put("statusGroup", 2)
                .put("homeCompetitor", new JSONObject().put("id", 10).put("name", "Local"))
                .put("awayCompetitor", new JSONObject().put("id", 20).put("name", "Visitante"))
                .put("tvNetworks", new JSONArray()
                        .put(new JSONObject().put("name", ""))
                        .put(new JSONObject().put("name", "   "))
                        .put("texto-suelto")
                        .put(new JSONObject().put("name", "DAZN")));

        MatchDetail withNetworks = MatchDetailParser.parse(
                new JSONObject().put("game", game), null, null);
        assertEquals(1, withNetworks.tvNetworks.size());
        assertEquals("DAZN", withNetworks.tvNetworks.get(0));

        game.remove("tvNetworks");
        MatchDetail withoutNetworks = MatchDetailParser.parse(
                new JSONObject().put("game", game), null, null);
        assertTrue(withoutNetworks.tvNetworks.isEmpty());
    }

    @Test
    public void gameTvNetworksTakePrecedenceOverRoot() throws Exception {
        JSONObject root = fixture("scores365_game_football.json");
        root.put("tvNetworks", new JSONArray()
                .put(new JSONObject().put("name", "Otra cadena")));

        MatchDetail detail = MatchDetailParser.parse(root, null, null);

        assertEquals(2, detail.tvNetworks.size());
        assertEquals("Movistar Plus+", detail.tvNetworks.get(0));
    }

    @Test
    public void parsesStatsPairedByCompetitorAndCategory() throws Exception {
        MatchDetail detail = parseFootball(fixture("scores365_stats_football.json"));

        assertTrue(detail.hasStats);
        assertEquals("", detail.statsError);
        assertEquals(6, detail.statCategories.size());

        assertEquals("General", detail.statCategories.get(0).name);
        StatRow possession = detail.statCategories.get(0).rows.get(0);
        assertEquals("Posesión", possession.name);
        assertEquals("32%", possession.homeValue);
        assertEquals("68%", possession.awayValue);
        assertEquals(0.32, possession.homePercent, 0.001);
        assertEquals(0.68, possession.awayPercent, 0.001);
        assertTrue(possession.major);

        StatCategory shots = detail.statCategories.get(1);
        assertEquals("Remates", shots.name);
        assertEquals(2, shots.rows.size());
        assertEquals("Total Remates", shots.rows.get(0).name);
        assertEquals("4", shots.rows.get(0).homeValue);
        assertEquals("19", shots.rows.get(0).awayValue);
        assertEquals("Remates a Puerta", shots.rows.get(1).name);

        StatCategory discipline = detail.statCategories.get(5);
        assertEquals("Disciplinarias", discipline.name);
        assertEquals("Faltas", discipline.rows.get(0).name);
        assertEquals("Tarjetas Amarillas", discipline.rows.get(1).name);
        assertTrue(discipline.rows.get(1).major);

        int rows = 0;
        for (StatCategory category : detail.statCategories) {
            rows += category.rows.size();
        }
        assertEquals(8, rows);
    }

    @Test
    public void scheduledGameHasNoEventsLineupsOrStats() throws Exception {
        MatchDetail detail = MatchDetailParser.parse(
                fixture("scores365_game_scheduled.json"), null, null);

        assertNotNull(detail);
        assertEquals(MatchStatus.SCHEDULED, detail.status);
        assertFalse(detail.scoreKnown);
        assertEquals(-1, detail.homeScore);
        assertEquals(-1, detail.awayScore);
        assertTrue(detail.events.isEmpty());
        assertNull(detail.homeLineup);
        assertNull(detail.awayLineup);
        assertFalse(detail.hasStats);
        assertEquals("", detail.statsError);
        assertNotNull(detail.venue);
        assertEquals("Santiago Bernabéu", detail.venue.name);
        assertEquals(81044, detail.venue.capacity);
    }

    @Test
    public void parsesBasketballWithoutEventsAndWithLineupsAndStats() throws Exception {
        MatchDetail detail = MatchDetailParser.parse(
                fixture("scores365_game_basketball.json"),
                fixture("scores365_stats_basketball.json"), null);

        assertNotNull(detail);
        assertEquals(4819924L, detail.gameId);
        assertEquals("Baloncesto", detail.sport);
        assertEquals(MatchStatus.FINISHED, detail.status);
        assertEquals(90, detail.homeScore);
        assertEquals(71, detail.awayScore);
        assertTrue(detail.events.isEmpty());

        assertEquals(5, detail.homeLineup.starters.size());
        assertEquals(7, detail.homeLineup.substitutes.size());
        assertEquals(5, detail.homeLineup.missing.size());
        assertEquals("Pedro Martinez", detail.homeLineup.coach);
        assertEquals("Edy Tavares", detail.homeLineup.starters.get(3).name);
        assertEquals(22, detail.homeLineup.starters.get(3).number);

        assertEquals(5, detail.awayLineup.starters.size());
        assertEquals(7, detail.awayLineup.substitutes.size());
        assertEquals(5, detail.awayLineup.missing.size());
        assertEquals("Sito Alonso", detail.awayLineup.coach);

        assertTrue(detail.hasStats);
        assertEquals(1, detail.statCategories.size());
        assertEquals(11, detail.statCategories.get(0).rows.size());
        StatRow fieldGoals = detail.statCategories.get(0).rows.get(0);
        assertEquals("Tiro de campo", fieldGoals.name);
        assertEquals("34/71 (47.9%)", fieldGoals.homeValue);
        assertEquals("22/67 (32.8%)", fieldGoals.awayValue);
        assertTrue(fieldGoals.major);
        assertEquals("FP", detail.statCategories.get(0).rows.get(10).name);
    }

    @Test
    public void missingMembersFallBackToJugadorWithoutCrash() throws Exception {
        JSONObject game = new JSONObject()
                .put("id", 77)
                .put("sportId", 1)
                .put("statusGroup", 4)
                .put("statusText", "Finalizado")
                .put("homeCompetitor", new JSONObject()
                        .put("id", 10)
                        .put("name", "Local")
                        .put("score", 0)
                        .put("lineups", new JSONObject()
                                .put("formation", "4-4-2")
                                .put("members", new JSONArray()
                                        .put(new JSONObject()
                                                .put("status", 1)
                                                .put("id", 999)))))
                .put("awayCompetitor", new JSONObject()
                        .put("id", 20)
                        .put("name", "Visitante")
                        .put("score", 0))
                .put("events", new JSONArray()
                        .put(new JSONObject()
                                .put("competitorId", 10)
                                .put("order", 1)
                                .put("gameTime", 10)
                                .put("playerId", 999)
                                .put("eventType", new JSONObject()
                                        .put("id", 2)
                                        .put("name", "Tarjeta amarilla"))));

        MatchDetail detail = MatchDetailParser.parse(
                new JSONObject().put("game", game), null, null);

        assertNotNull(detail);
        assertEquals(1, detail.events.size());
        assertEquals("Jugador", detail.events.get(0).playerName);
        assertEquals(0, detail.events.get(0).playerNumber);
        assertEquals(1, detail.homeLineup.starters.size());
        assertEquals("Jugador", detail.homeLineup.starters.get(0).name);
        assertEquals(0, detail.homeLineup.starters.get(0).number);
    }

    @Test
    public void missingVenueAndOfficialsAreNullAndEmpty() throws Exception {
        JSONObject root = fixture("scores365_game_scheduled.json");
        JSONObject game = root.getJSONObject("game");
        game.remove("venue");
        game.remove("officials");

        MatchDetail detail = MatchDetailParser.parse(root, null, null);

        assertNotNull(detail);
        assertNull(detail.venue);
        assertTrue(detail.officials.isEmpty());
    }

    @Test
    public void nullOrEmptyStatsKeepGameDetailValid() throws Exception {
        MatchDetail noStats = parseFootball(null);
        assertFalse(noStats.hasStats);
        assertEquals("", noStats.statsError);
        assertFalse(noStats.events.isEmpty());
        assertNotNull(noStats.homeLineup);
        assertFalse(noStats.homeLineup.starters.isEmpty());

        MatchDetail emptyObject = parseFootball(new JSONObject());
        assertFalse(emptyObject.hasStats);
        assertEquals("", emptyObject.statsError);

        MatchDetail emptyArray = parseFootball(
                new JSONObject().put("statistics", new JSONArray()));
        assertFalse(emptyArray.hasStats);
        assertEquals("", emptyArray.statsError);

        MatchDetail invalid = parseFootball(
                new JSONObject().put("statistics", "no-es-un-array"));
        assertFalse(invalid.hasStats);
        assertFalse(invalid.statsError.isEmpty());
        assertFalse(invalid.events.isEmpty());
        assertNotNull(invalid.homeLineup);
        assertFalse(invalid.homeLineup.starters.isEmpty());
    }

    @Test
    public void missingGameReturnsNull() throws Exception {
        assertNull(MatchDetailParser.parse(new JSONObject(), null, null));
        assertNull(MatchDetailParser.parse(
                new JSONObject().put("sports", new JSONArray()), null, null));
        assertNull(MatchDetailParser.parse(null, null, null));
    }

    @Test
    public void bettingFieldsDoNotBreakParsing() throws Exception {
        JSONObject root = fixture("scores365_game_football.json");
        JSONObject game = root.getJSONObject("game");
        game.put("topPerformers", new JSONObject()
                .put("home", new JSONArray().put(new JSONObject()
                        .put("name", "Cuota local")
                        .put("odds", 1.75))));
        game.put("hasBets", true);
        game.put("hasPlayerBets", true);

        MatchDetail detail = MatchDetailParser.parse(root, null, null);
        assertNotNull(detail);
        assertEquals(10, detail.events.size());
    }

    private static MatchDetail parseFootball(JSONObject stats) throws Exception {
        return MatchDetailParser.parse(
                fixture("scores365_game_football.json"), stats, null);
    }

    private static MatchDetail parseEvent(String eventJson) throws Exception {
        JSONObject game = new JSONObject()
                .put("id", 1)
                .put("sportId", 1)
                .put("competitionDisplayName", "Test")
                .put("statusGroup", 4)
                .put("statusText", "Finalizado")
                .put("homeCompetitor", new JSONObject()
                        .put("id", 10).put("name", "Local").put("score", 1))
                .put("awayCompetitor", new JSONObject()
                        .put("id", 20).put("name", "Visitante").put("score", 0))
                .put("members", new JSONArray().put(new JSONObject()
                        .put("id", 7)
                        .put("name", "Jugador X")
                        .put("jerseyNumber", 9)))
                .put("events", new JSONArray().put(new JSONObject(eventJson)));

        return MatchDetailParser.parse(new JSONObject().put("game", game), null, null);
    }

    private static MatchEvent eventWithOrder(MatchDetail detail, int order) {
        for (MatchEvent event : detail.events) {
            if (event.order == order) return event;
        }
        throw new AssertionError("No hay evento con orden " + order);
    }

    private static JSONObject fixture(String name) throws Exception {
        try (InputStream input = MatchDetailParserTest.class
                .getResourceAsStream("/fixtures/" + name)) {
            assertNotNull("Falta el fixture " + name, input);
            return new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
