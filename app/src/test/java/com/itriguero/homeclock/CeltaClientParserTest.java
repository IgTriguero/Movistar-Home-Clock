package com.itriguero.homeclock;

import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class CeltaClientParserTest {

    @Test
    public void parsesCeltaMatchFromFixture() throws Exception {
        JSONObject parsed = CeltaClient.parseMatch(fixture());

        assertNotNull(parsed);
        assertEquals("2026-09-30", parsed.getString("date"));
        assertEquals("17:30", parsed.getString("time"));
        assertEquals("AS Celtas", parsed.getString("home"));
        assertEquals("Real Oviedo", parsed.getString("away"));
        assertEquals(3, parsed.getInt("hs"));
        assertEquals(1, parsed.getInt("as"));
        assertEquals(1, parsed.getInt("status"));
        assertEquals("236837", parsed.getString("id"));
        assertEquals("https://t.resfu.com/img_data/equipos/138738.png?size=120x60",
                parsed.getString("homeLogo"));
    }

    private static JSONObject fixture() throws Exception {
        try (InputStream input = CeltaClientParserTest.class
                .getResourceAsStream("/fixtures/celta_match.json")) {
            assertNotNull(input);
            return new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
