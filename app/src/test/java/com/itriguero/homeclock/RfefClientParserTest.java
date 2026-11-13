package com.itriguero.homeclock;

import org.json.JSONArray;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class RfefClientParserTest {

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

    private static String fixture() throws Exception {
        try (InputStream input = RfefClientParserTest.class
                .getResourceAsStream("/fixtures/rfef_journeys.html")) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
