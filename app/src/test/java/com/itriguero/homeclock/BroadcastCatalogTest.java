package com.itriguero.homeclock;

import org.junit.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class BroadcastCatalogTest {

    private static final Locale ES = new Locale("es", "ES");

    @Test
    public void euroligaAndEuroleagueMapToEuroliga() {
        BroadcastInfo liga = BroadcastCatalog.forMatch(match("Euroliga"));

        assertEquals("euroliga", liga.competitionKey);
        assertEquals(BroadcastInfo.Source.CURATED, liga.source);
        assertEquals("Movistar Plus+", liga.label);
        assertEquals("Exclusiva en España.", liga.detail);
        assertEquals(BroadcastCatalog.SEASON, liga.season);

        BroadcastInfo english = BroadcastCatalog.forMatch(match("Euroleague Basketball"));
        assertEquals("euroliga", english.competitionKey);
    }

    @Test
    public void copaDeLaReinaIsNotCopaDelRey() {
        BroadcastInfo reina = BroadcastCatalog.forMatch(match("Copa de la Reina"));
        BroadcastInfo rey = BroadcastCatalog.forMatch(match("Copa del Rey"));

        assertEquals("copa-de-la-reina", reina.competitionKey);
        assertEquals("RTVE Play / Teledeporte · RFEF TV", reina.label);

        assertEquals("copa-del-rey", rey.competitionKey);
        assertEquals("Movistar Plus+ · RTVE · Orange TV", rey.label);
        assertNotEquals(reina.competitionKey, rey.competitionKey);
    }

    @Test
    public void ligaEndesaAndAcbMapToAcb() {
        BroadcastInfo endesa = BroadcastCatalog.forMatch(match("Liga Endesa"));
        BroadcastInfo acb = BroadcastCatalog.forMatch(match("ACB"));

        assertEquals("acb", endesa.competitionKey);
        assertEquals("acb", acb.competitionKey);
        assertEquals(BroadcastInfo.Source.CURATED, endesa.source);
        assertEquals("DAZN · Movistar Plus+ · Orange TV", endesa.label);
    }

    @Test
    public void championshipDoesNotMatchChampions() {
        BroadcastInfo champions = BroadcastCatalog.forMatch(match("Champions League"));
        BroadcastInfo championship = BroadcastCatalog.forMatch(match("English Championship"));

        assertEquals("champions", champions.competitionKey);
        assertEquals(BroadcastInfo.Source.CURATED, champions.source);

        assertEquals(BroadcastCatalog.SEARCH_KEY, championship.competitionKey);
        assertEquals(BroadcastInfo.Source.SEARCH, championship.source);
    }

    @Test
    public void premierMapsToPremier() {
        BroadcastInfo info = BroadcastCatalog.forMatch(match("Premier League"));

        assertEquals("premier", info.competitionKey);
        assertEquals("DAZN · Orange TV · Movistar Plus+", info.label);
    }

    @Test
    public void laligaMapsToLaliga() {
        BroadcastInfo info = BroadcastCatalog.forMatch(match("LaLiga EA Sports"));

        assertEquals("laliga", info.competitionKey);
        assertEquals(BroadcastInfo.Source.CURATED, info.source);
        assertEquals("Movistar Plus+ · DAZN · Orange TV", info.label);
    }

    @Test
    public void laliga2AndHypermotionDoNotFallIntoLaliga() {
        BroadcastInfo segunda = BroadcastCatalog.forMatch(match("LaLiga 2"));
        BroadcastInfo hypermotion = BroadcastCatalog.forMatch(match("LaLiga Hypermotion"));

        assertNotEquals("laliga", segunda.competitionKey);
        assertNotEquals("laliga", hypermotion.competitionKey);
        assertEquals(BroadcastInfo.Source.SEARCH, segunda.source);
        assertEquals(BroadcastInfo.Source.SEARCH, hypermotion.source);
    }

    @Test
    public void segundaFederacionFemeninaIsCurated() {
        BroadcastInfo info = BroadcastCatalog.forMatch(match("Segunda Federación Femenina"));
        BroadcastInfo rfef = BroadcastCatalog.forMatch(match("2ª Federación Femenina"));

        assertEquals("segunda-federacion-femenina", info.competitionKey);
        assertEquals("segunda-federacion-femenina", rfef.competitionKey);
        assertEquals("YouTube: RFEF / RC Celta", info.label);
        assertEquals(BroadcastInfo.Source.CURATED, info.source);
    }

    @Test
    public void asCeltasInSegundaFederacionIsCurated() {
        BroadcastInfo rfef = BroadcastCatalog.forMatch(
                match("2ª Federación Femenina", "As Celtas"));
        BroadcastInfo league = BroadcastCatalog.forMatch(
                match("Segunda Federación", "As Celtas"));

        assertEquals("segunda-federacion-femenina", rfef.competitionKey);
        assertEquals("segunda-federacion-femenina", league.competitionKey);
        assertEquals(BroadcastInfo.Source.CURATED, rfef.source);
        assertEquals(BroadcastInfo.Source.CURATED, league.source);
    }

    @Test
    public void segundaFederacionMasculinaDoesNotFallIntoFemenina() {
        BroadcastInfo info = BroadcastCatalog.forMatch(match("Segunda Federación"));

        assertEquals(BroadcastInfo.Source.SEARCH, info.source);
        assertNotEquals("segunda-federacion-femenina", info.competitionKey);
        assertNotEquals("laliga", info.competitionKey);
    }

    @Test
    public void seleccionMapsToSeleccion() {
        BroadcastInfo naciones = BroadcastCatalog.forMatch(match("UEFA Nations League"));
        BroadcastInfo seleccion = BroadcastCatalog.forMatch(match("Selección Española"));

        assertEquals("seleccion", naciones.competitionKey);
        assertEquals("seleccion", seleccion.competitionKey);
        assertEquals("La 1 · RTVE Play · DAZN", naciones.label);
    }

    @Test
    public void supercopaAndEuropaLeagueHaveTheirOwnEntry() {
        BroadcastInfo supercopa = BroadcastCatalog.forMatch(match("Supercopa de España"));
        BroadcastInfo europa = BroadcastCatalog.forMatch(match("UEFA Europa League"));

        assertEquals("supercopa", supercopa.competitionKey);
        assertEquals("Movistar Plus+ · Orange TV", supercopa.label);

        assertEquals("europa-league", europa.competitionKey);
        assertEquals("Si el equipo cae de Champions.", europa.detail);
    }

    @Test
    public void amistosoEmptyAndUnknownCompetitionsFallBackToSearch() {
        BroadcastInfo amistoso = BroadcastCatalog.forMatch(match("Amistoso internacional"));
        BroadcastInfo empty = BroadcastCatalog.forMatch(match(null));
        BroadcastInfo unknown = BroadcastCatalog.forMatch(match("Competición rara"));

        assertEquals(BroadcastInfo.Source.SEARCH, amistoso.source);
        assertEquals(BroadcastInfo.Source.SEARCH, empty.source);
        assertEquals(BroadcastInfo.Source.SEARCH, unknown.source);

        assertEquals(BroadcastCatalog.SEARCH_KEY, empty.competitionKey);
        assertFalse(empty.label.isEmpty());
        assertFalse(empty.detail.isEmpty());
        assertFalse(empty.searchUrl.isEmpty());
    }

    @Test
    public void nullMatchStillBuildsSearchInfo() {
        BroadcastInfo info = BroadcastCatalog.forMatch(null);

        assertEquals(BroadcastInfo.Source.SEARCH, info.source);
        assertEquals(BroadcastCatalog.SEARCH_KEY, info.competitionKey);
        assertFalse(info.searchUrl.isEmpty());
    }

    @Test
    public void searchUrlEncodesHomeAwayAndDate() throws Exception {
        Match match = match("LaLiga");
        match.homeName = "Celta";
        match.awayName = "Barcelona";
        match.startMillis = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
                .parse("2026-11-14 18:30")
                .getTime();

        String date = new SimpleDateFormat("d MMM yyyy", ES)
                .format(new Date(match.startMillis));
        String query = "dónde ver Celta Barcelona " + date + " TV España";

        BroadcastInfo info = BroadcastCatalog.forMatch(match);

        assertTrue(info.searchUrl.startsWith("https://www.google.com/search?q="));
        assertTrue(info.searchUrl.endsWith(
                URLEncoder.encode(query, StandardCharsets.UTF_8.name())));
    }

    @Test
    public void searchUrlOmitsDateWithoutKickoff() throws Exception {
        Match match = match("Amistoso");
        match.homeName = "Celta";
        match.awayName = "Barcelona";
        match.startMillis = 0;

        BroadcastInfo info = BroadcastCatalog.forMatch(match);

        assertTrue(info.searchUrl.endsWith(URLEncoder.encode(
                "dónde ver Celta Barcelona TV España",
                StandardCharsets.UTF_8.name())));
        assertFalse(info.searchUrl.contains("null"));
    }

    @Test
    public void cleanNetworkNameRemovesTrailingParentheses() {
        assertEquals("DAZN LaLiga",
                BroadcastCatalog.cleanNetworkName("  DAZN   LaLiga (M55 O113) "));
        assertEquals("DAZN",
                BroadcastCatalog.cleanNetworkName("DAZN (M55) (O113)"));
        assertEquals("Movistar Plus+",
                BroadcastCatalog.cleanNetworkName("Movistar Plus+"));
    }

    @Test
    public void cleanNetworkNameKeepsOriginalWhenOnlyParentheses() {
        assertEquals("(M55 O113)", BroadcastCatalog.cleanNetworkName("  (M55 O113) "));
        assertEquals("", BroadcastCatalog.cleanNetworkName("   "));
        assertEquals("", BroadcastCatalog.cleanNetworkName(null));
    }

    private static Match match(String competition) {
        return match(competition, null);
    }

    private static Match match(String competition, String trackedName) {
        Match match = new Match();
        match.competition = competition;
        match.trackedName = trackedName;
        return match;
    }
}
