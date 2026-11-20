package com.itriguero.homeclock;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TeamSearchClientTest {

    @Test
    public void buildUrlEncodesSpacesAndAccents() {
        assertEquals(
                "https://webws.365scores.com/web/search/"
                        + "?appTypeId=5&langId=14&userCountryId=225&query=Real+Madrid",
                TeamSearchClient.buildUrl("  Real   Madrid "));
        assertEquals(
                "https://webws.365scores.com/web/search/"
                        + "?appTypeId=5&langId=14&userCountryId=225&query=Espa%C3%B1a",
                TeamSearchClient.buildUrl("España"));
    }

    @Test
    public void buildCompetitionsUrlSortsDeduplicatesAndEncodesComma() {
        assertEquals(
                "https://webws.365scores.com/web/competitions/"
                        + "?appTypeId=5&langId=14&competitions=11%2C14",
                TeamSearchClient.buildCompetitionsUrl(
                        Arrays.asList(14L, 11L, 14L, -3L, 0L, null)));
        assertEquals(
                "https://webws.365scores.com/web/competitions/"
                        + "?appTypeId=5&langId=14&competitions=",
                TeamSearchClient.buildCompetitionsUrl(Collections.emptyList()));
    }

    @Test
    public void buildCompetitorUrlUsesTheId() {
        assertEquals(
                "https://webws.365scores.com/web/competitors/"
                        + "?appTypeId=5&langId=14&competitors=204",
                TeamSearchClient.buildCompetitorUrl(204L));
    }

    @Test
    public void searchRejectsShortQueriesWithoutNetwork() {
        for (String query : Arrays.asList("", " ", "R", " a ", "\t\n")) {
            try {
                TeamSearchClient.search(query);
                fail("La consulta '" + query + "' debería rechazarse");
            } catch (TeamSearchClient.TeamSearchException e) {
                assertEquals("Consulta: '" + query + "'",
                        TeamSearchClient.TeamSearchException.Kind.INVALID_QUERY, e.kind);
            }
        }
    }

    @Test
    public void parseSearchRealMadridKeepsFootballAndBasketball() throws Exception {
        List<TrackedTeam> teams = TeamSearchClient.parseSearch(
                fixture("scores365_search_real_madrid.json"));

        assertEquals(8, teams.size());

        TrackedTeam football = byId(teams, 131L);
        assertNotNull("Falta el Real Madrid de fútbol (131)", football);
        assertEquals(TrackedTeam.SPORT_FOOTBALL, football.sportId);
        assertEquals("Real Madrid", football.name);
        assertEquals("España", football.country);
        assertEquals("", football.competition);
        assertEquals(TrackedTeam.logoUrl(131L), football.logoUrl);
        assertEquals(TrackedTeam.PROVIDER_365, football.provider);
        assertTrue(football.enabled);
        assertEquals("REAL MADRID", football.shortName);

        TrackedTeam basketball = byId(teams, 204L);
        assertNotNull("Falta el Real Madrid de baloncesto (204)", basketball);
        assertEquals(TrackedTeam.SPORT_BASKETBALL, basketball.sportId);
        assertEquals("Real Madrid", basketball.name);
        assertEquals("España", basketball.country);
        assertEquals("", basketball.competition);
        assertEquals(TrackedTeam.logoUrl(204L), basketball.logoUrl);
    }

    @Test
    public void parseSearchDiscardsLegendTypeButKeepsCastilla() throws Exception {
        List<TrackedTeam> teams = TeamSearchClient.parseSearch(
                fixture("scores365_search_real_madrid.json"));

        assertNotNull("El Castilla (9220, type 1) debe conservarse",
                byId(teams, 9220L));
        assertNull("Las leyendas (77091, type 16) deben descartarse",
                byId(teams, 77091L));

        for (TrackedTeam team : teams) {
            assertTrue("Deporte inesperado en " + team, team.sportId == 1 || team.sportId == 2);
            assertTrue("Equipo inválido: " + team, team.isValid());
            assertFalse("Nombre corto vacío en " + team, team.shortName.isEmpty());
            assertTrue("Nombre corto demasiado largo en " + team,
                    team.shortName.length() <= 24);
        }
    }

    @Test
    public void parseSearchOrdersFootballFirstAndByPopularity() throws Exception {
        List<TrackedTeam> teams = TeamSearchClient.parseSearch(
                fixture("scores365_search_real_madrid.json"));

        // Fútbol por popularidad descendente y después baloncesto.
        assertEquals(Arrays.asList(131L, 9220L, 56903L, 27516L, 82460L,
                        90315L, 84489L, 204L),
                ids(teams));
    }

    @Test
    public void parseSearchDisambiguatesBasketballShortName() throws Exception {
        List<TrackedTeam> teams = TeamSearchClient.parseSearch(
                fixture("scores365_search_real_madrid.json"));

        TrackedTeam football = byId(teams, 131L);
        TrackedTeam basketball = byId(teams, 204L);
        assertNotNull(football);
        assertNotNull(basketball);

        assertFalse("El fútbol no lleva sufijo de baloncesto",
                football.shortName.endsWith("BAL."));
        assertEquals("REAL MADRID BAL.", basketball.shortName);
        assertFalse("Las dos entradas no pueden mostrar la misma etiqueta",
                football.shortName.equals(basketball.shortName));
    }

    @Test
    public void parseSearchDoesNotDuplicateBasketballSuffix() throws Exception {
        String json = "{\"sports\":[{\"id\":1,\"name\":\"Fútbol\"},"
                + "{\"id\":2,\"name\":\"Baloncesto\"}],"
                + "\"countries\":[{\"id\":2,\"name\":\"España\"}],"
                + "\"competitors\":["
                + club(10L, 1, "Prueba BAL.", 2)
                + "," + club(11L, 2, "Prueba BAL.", 1)
                + "," + club(12L, 1, "Prueba BAL", 1)
                + "," + club(13L, 2, "Prueba BAL", 1)
                + "]}";

        List<TrackedTeam> teams = TeamSearchClient.parseSearch(json);
        assertEquals("PRUEBA BAL.", byId(teams, 11L).shortName);
        assertEquals("PRUEBA BAL", byId(teams, 13L).shortName);
    }

    @Test
    public void parseSearchFiltersHiddenTeams() throws Exception {
        String json = "{\"sports\":[{\"id\":1,\"name\":\"Fútbol\"}],"
                + "\"countries\":[{\"id\":2,\"name\":\"España\"}],"
                + "\"competitors\":["
                + "{\"id\":1,\"countryId\":2,\"sportId\":1,\"name\":\"Visible\","
                + "\"type\":1,\"popularityRank\":1,\"mainCompetitionId\":0,"
                + "\"hideOnSearch\":false},"
                + "{\"id\":2,\"countryId\":2,\"sportId\":1,\"name\":\"Oculto\","
                + "\"type\":1,\"popularityRank\":2,\"mainCompetitionId\":0,"
                + "\"hideOnSearch\":true}"
                + "]}";

        List<TrackedTeam> teams = TeamSearchClient.parseSearch(json);
        assertEquals(1, teams.size());
        assertEquals(1L, teams.get(0).competitorId);
    }

    @Test
    public void parseSearchDeduplicatesRepeatedIds() throws Exception {
        String json = "{\"sports\":[{\"id\":1,\"name\":\"Fútbol\"}],"
                + "\"countries\":[{\"id\":2,\"name\":\"España\"}],"
                + "\"competitors\":["
                + club(7L, 1, "Primero", 10)
                + "," + club(7L, 1, "Segundo", 20)
                + "]}";

        List<TrackedTeam> teams = TeamSearchClient.parseSearch(json);
        assertEquals(1, teams.size());
        assertEquals("Primero", teams.get(0).name);
    }

    @Test
    public void parseSearchDropsInvalidSportTypeAndEmptyNames() throws Exception {
        String json = "{\"sports\":[{\"id\":1,\"name\":\"Fútbol\"},"
                + "{\"id\":2,\"name\":\"Baloncesto\"},{\"id\":5,\"name\":\"Balonmano\"}],"
                + "\"countries\":[{\"id\":2,\"name\":\"España\"}],"
                + "\"competitors\":["
                + club(1L, 5, "Balonmano", 10)
                + "," + "{\"id\":2,\"countryId\":2,\"sportId\":1,"
                + "\"name\":\"Leyendas\",\"type\":16,\"popularityRank\":10,"
                + "\"mainCompetitionId\":0}"
                + "," + "{\"id\":3,\"countryId\":2,\"sportId\":2,"
                + "\"name\":\"Tenis\",\"type\":4,\"popularityRank\":10,"
                + "\"mainCompetitionId\":0}"
                + "," + "{\"id\":4,\"countryId\":2,\"sportId\":1,"
                + "\"name\":\"\",\"type\":1,\"popularityRank\":10,"
                + "\"mainCompetitionId\":0}"
                + "," + "{\"id\":0,\"countryId\":2,\"sportId\":1,"
                + "\"name\":\"Sin id\",\"type\":1,\"popularityRank\":10,"
                + "\"mainCompetitionId\":0}"
                + "," + club(5L, 1, "Tipo ausente", 10)
                + "," + "{\"id\":6,\"countryId\":2,\"sportId\":2,"
                + "\"name\":\"Tipo cero\",\"type\":0,\"popularityRank\":5,"
                + "\"mainCompetitionId\":0}"
                + "]}";

        List<TrackedTeam> teams = TeamSearchClient.parseSearch(json);
        assertEquals(Arrays.asList(5L, 6L), ids(teams));
    }

    @Test
    public void parseSearchLimitsToTwelveAndFiltersOtherSports() throws Exception {
        List<TrackedTeam> teams = TeamSearchClient.parseSearch(
                fixture("scores365_search_espana.json"));

        assertEquals(12, teams.size());
        assertNull("El balonmano (8867, sportId 5) debe descartarse",
                byId(teams, 8867L));
        for (TrackedTeam team : teams) {
            assertTrue("Deporte inesperado: " + team.sportId,
                    team.sportId == 1 || team.sportId == 2);
        }

        TrackedTeam spainFootball = byId(teams, 5050L);
        assertNotNull(spainFootball);
        assertEquals("Selección", spainFootball.competition);
        assertEquals("ESPAÑA", spainFootball.shortName);

        TrackedTeam spainBasketball = byId(teams, 2393L);
        assertNotNull(spainBasketball);
        assertEquals(TrackedTeam.SPORT_BASKETBALL, spainBasketball.sportId);
        assertEquals("Selección", spainBasketball.competition);
        assertTrue("La selección de baloncesto necesita sufijo",
                spainBasketball.shortName.endsWith(" BAL."));
    }

    @Test
    public void parseSearchEmptyResponsesReturnEmptyList() throws Exception {
        assertTrue(TeamSearchClient.parseSearch(
                fixture("scores365_search_empty.json")).isEmpty());
        assertTrue(TeamSearchClient.parseSearch("").isEmpty());
        assertTrue(TeamSearchClient.parseSearch("   ").isEmpty());
        assertTrue(TeamSearchClient.parseSearch("{}").isEmpty());
    }

    @Test
    public void parseSearchBrokenJsonThrowsParse() throws Exception {
        try {
            TeamSearchClient.parseSearch(fixture("scores365_search_malformed.json"));
            fail("Un JSON truncado debe fallar");
        } catch (TeamSearchClient.TeamSearchException e) {
            assertEquals(TeamSearchClient.TeamSearchException.Kind.PARSE, e.kind);
        }
    }

    @Test
    public void parseSearchInvalidStructureThrowsParse() throws Exception {
        try {
            TeamSearchClient.parseSearch("{\"competitors\":42}");
            fail("competitors debe ser una lista");
        } catch (TeamSearchClient.TeamSearchException e) {
            assertEquals(TeamSearchClient.TeamSearchException.Kind.PARSE, e.kind);
        }
    }

    @Test
    public void parseSearchUsesCompetitionNamesWhenAvailable() throws Exception {
        Map<Long, String> competitions = new HashMap<>();
        competitions.put(11L, "LaLiga");
        competitions.put(14L, "Liga Endesa");
        competitions.put(7865L, "Primera División RFEF");

        List<TrackedTeam> teams = TeamSearchClient.parseSearch(
                fixture("scores365_search_real_madrid.json"), competitions);

        assertEquals("LaLiga", byId(teams, 131L).competition);
        assertEquals("Liga Endesa", byId(teams, 204L).competition);
        assertEquals("Primera División RFEF", byId(teams, 9220L).competition);
    }

    @Test
    public void parseCompetitionsReadsRealResponse() throws Exception {
        Map<Long, String> competitions = TeamSearchClient.parseCompetitions(
                fixture("scores365_competitions_11_14.json"));

        assertEquals(2, competitions.size());
        assertEquals("LaLiga", competitions.get(11L));
        assertEquals("Liga Endesa", competitions.get(14L));
    }

    @Test
    public void parseCompetitionsBrokenJsonReturnsEmptyMap() throws Exception {
        assertTrue(TeamSearchClient.parseCompetitions(
                fixture("scores365_search_malformed.json")).isEmpty());
        assertTrue(TeamSearchClient.parseCompetitions("").isEmpty());
        assertTrue(TeamSearchClient.parseCompetitions(null).isEmpty());
    }

    @Test
    public void parseCompetitorReadsBasketballById() throws Exception {
        TrackedTeam team = TeamSearchClient.parseCompetitor(
                fixture("scores365_competitor_204.json"), 204L);

        assertNotNull(team);
        assertEquals(TrackedTeam.PROVIDER_365, team.provider);
        assertEquals(204L, team.competitorId);
        assertEquals(TrackedTeam.SPORT_BASKETBALL, team.sportId);
        assertEquals("Real Madrid", team.name);
        assertEquals("REAL MADRID", team.shortName);
        assertEquals("España", team.country);
        assertEquals("Liga Endesa", team.competition);
        assertEquals(TrackedTeam.logoUrl(204L), team.logoUrl);
        assertTrue(team.enabled);
    }

    @Test
    public void parseCompetitorMissingIdThrowsNotFound() throws Exception {
        try {
            TeamSearchClient.parseCompetitor(
                    fixture("scores365_competitor_204.json"), 131L);
            fail("El competidor 131 no está en el fixture");
        } catch (TeamSearchClient.TeamSearchException e) {
            assertEquals(TeamSearchClient.TeamSearchException.Kind.NOT_FOUND, e.kind);
        }
    }

    @Test
    public void parseCompetitorInvalidIdThrowsInvalidQuery() throws Exception {
        try {
            TeamSearchClient.parseCompetitor(
                    fixture("scores365_competitor_204.json"), 0L);
            fail("Un ID no positivo debe rechazarse");
        } catch (TeamSearchClient.TeamSearchException e) {
            assertEquals(TeamSearchClient.TeamSearchException.Kind.INVALID_QUERY, e.kind);
        }
    }

    @Test
    public void parseCompetitorBrokenJsonThrowsParse() throws Exception {
        try {
            TeamSearchClient.parseCompetitor(
                    fixture("scores365_search_malformed.json"), 204L);
            fail("Un JSON truncado debe fallar");
        } catch (TeamSearchClient.TeamSearchException e) {
            assertEquals(TeamSearchClient.TeamSearchException.Kind.PARSE, e.kind);
        }
    }

    @Test
    public void curatedRecognizesAsCeltasWithoutNetwork() {
        for (String query : Arrays.asList("celta", "Celtas", "As Celtas",
                "  As   Celtas ", "Celta Femenino", "as celtas femenino",
                "as celtas sub19", "ÁS CÉLTAS")) {
            List<TrackedTeam> result = TeamSearchClient.curated(query);
            assertEquals("Consulta: '" + query + "'", 1, result.size());
            assertEquals(TrackedTeam.PROVIDER_AS_CELTAS, result.get(0).provider);
            assertEquals(TrackedTeam.AS_CELTAS_ID, result.get(0).competitorId);
            assertEquals("As Celtas", result.get(0).name);
            assertTrue(result.get(0).enabled);
        }
    }

    @Test
    public void curatedIgnoresOtherTeams() {
        for (String query : Arrays.asList("madrid", "celta de vigo", "as celta",
                "celtas femenino sub19", "")) {
            assertTrue("Consulta: '" + query + "'",
                    TeamSearchClient.curated(query).isEmpty());
        }
    }

    private static TrackedTeam byId(List<TrackedTeam> teams, long id) {
        for (TrackedTeam team : teams) {
            if (team.competitorId == id) return team;
        }
        return null;
    }

    private static List<Long> ids(List<TrackedTeam> teams) {
        Long[] result = new Long[teams.size()];
        for (int i = 0; i < teams.size(); i++) {
            result[i] = teams.get(i).competitorId;
        }
        return Arrays.asList(result);
    }

    private static String club(long id, int sportId, String name, long rank) {
        return "{\"id\":" + id + ",\"countryId\":2,\"sportId\":" + sportId
                + ",\"name\":\"" + name + "\",\"type\":1"
                + ",\"popularityRank\":" + rank
                + ",\"mainCompetitionId\":0,\"hideOnSearch\":false}";
    }

    private static String fixture(String name) throws Exception {
        try (InputStream input = TeamSearchClientTest.class
                .getResourceAsStream("/fixtures/" + name)) {
            assertNotNull("Falta el fixture " + name, input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
