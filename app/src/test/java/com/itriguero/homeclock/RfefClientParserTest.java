package com.itriguero.homeclock;

import org.json.JSONArray;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RfefClientParserTest {

    @Test
    public void cleansTeamNamesWithoutAndroidHtml() {
        assertEquals("Madrid CF Femenino 'B'",
                RfefClient.cleanText("Madrid CF Femenino &#039;B&#039;"));
        assertEquals("As Celtas", RfefClient.cleanText("  As\n\tCeltas  "));
        assertEquals("Rayo & Cía", RfefClient.cleanText("Rayo &amp; C&#237;a"));
        assertEquals("Real Avilés", RfefClient.cleanText("<span>Real</span> Avil&eacute;s"));
        assertEquals("", RfefClient.cleanText(null));
        assertEquals("", RfefClient.cleanText("   "));
        assertEquals("&desconocida;", RfefClient.cleanText("&desconocida;"));
    }

    @Test
    public void buildsResultsUrlWithCurrentSeason() {
        assertEquals(22, RfefClient.seasonId(LocalDate.of(2026, 11, 23)));
        assertEquals(22, RfefClient.seasonId(LocalDate.of(2026, 7, 1)));
        assertEquals(21, RfefClient.seasonId(LocalDate.of(2026, 6, 30)));
        assertEquals(21, RfefClient.seasonId(LocalDate.of(2025, 9, 1)));
        assertTrue(RfefClient.resultsUrl(LocalDate.of(2026, 11, 23))
                .endsWith("&season=22"));
    }

    @Test
    public void parsesJourneyOptionsFromFixture() throws Exception {
        JSONArray journeys = RfefClient.parseJourneys(fixture());

        assertNotNull(journeys);
        assertEquals(2, journeys.length());
        assertEquals(1, journeys.getJSONArray(0).getInt(0));
        assertEquals("2026-09-06", journeys.getJSONArray(0).getString(1));
        assertEquals(9, journeys.getJSONArray(1).getInt(0));
        assertEquals("2026-11-15", journeys.getJSONArray(1).getString(1));
    }

    @Test
    public void parsesJornadaHeading() {
        assertEquals(9, RfefClient.parseJourneyNumber(
                "<h3 class=\"text-center mt-2\"> Jornada 9 </h3>"));
        assertEquals(0, RfefClient.parseJourneyNumber("<h3>Sin jornada</h3>"));
        assertEquals(0, RfefClient.parseJourneyNumber(null));
    }

    private static String fixture() throws Exception {
        try (InputStream input = RfefClientParserTest.class
                .getResourceAsStream("/fixtures/rfef_journeys.html")) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
