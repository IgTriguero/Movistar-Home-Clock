package com.itriguero.homeclock;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TrackedTeamCodecTest {

    @Test
    public void roundTripPreservesTeamsOrderAndEnabledState() {
        TrackedTeamList original = new TrackedTeamList(TrackedTeam.defaults(), false);
        original.setEnabled("365scores:1:174", false);

        TrackedTeamCodec.Decoded decoded =
                TrackedTeamCodec.decode(TrackedTeamCodec.encode(original));

        assertFalse(decoded.migrated);
        assertFalse(decoded.repaired);
        assertFalse(decoded.readOnly);
        assertEquals(6, decoded.list.size());
        assertEquals(TrackedTeam.defaults().get(0).key(), decoded.list.get(0).key());
        assertEquals("Real Madrid", decoded.list.get(0).name);
        assertEquals("REAL MADRID", decoded.list.get(0).shortName);
        assertEquals("LaLiga", decoded.list.get(0).competition);
        assertTrue(decoded.list.get(0).enabled);
        assertFalse(decoded.list.get(1).enabled);
        assertEquals(TrackedTeam.asCeltas().key(),
                decoded.list.get(5).key());
        assertEquals("", decoded.list.get(5).logoUrl);
    }

    @Test
    public void emptyListRoundTripsWithoutFlags() {
        TrackedTeamList empty = new TrackedTeamList(Collections.emptyList(), false);

        TrackedTeamCodec.Decoded decoded =
                TrackedTeamCodec.decode(TrackedTeamCodec.encode(empty));

        assertFalse(decoded.migrated);
        assertFalse(decoded.repaired);
        assertFalse(decoded.readOnly);
        assertEquals(0, decoded.list.size());
        assertTrue(decoded.list.selection().isEmpty());
    }

    @Test
    public void nullBlankOrEmptyRawSeedsDefaultsAsMigration() {
        assertMigration(TrackedTeamCodec.decode(null));
        assertMigration(TrackedTeamCodec.decode(""));
        assertMigration(TrackedTeamCodec.decode("   "));
    }

    @Test
    public void brokenJsonFallsBackToDefaultsAsRepair() {
        TrackedTeamCodec.Decoded decoded =
                TrackedTeamCodec.decode("{esto no es json");

        assertFalse(decoded.migrated);
        assertTrue(decoded.repaired);
        assertFalse(decoded.readOnly);
        assertEquals(6, decoded.list.size());
    }

    @Test
    public void missingTeamsArraySeedsDefaultsAsRepair() {
        TrackedTeamCodec.Decoded decoded =
                TrackedTeamCodec.decode("{\"version\":1}");

        assertFalse(decoded.migrated);
        assertTrue(decoded.repaired);
        assertEquals(6, decoded.list.size());
    }

    @Test
    public void duplicatesKeepTheFirstEntry() throws Exception {
        JSONArray teams = new JSONArray()
                .put(teamJson(131, 1, "Primero"))
                .put(teamJson(131, 1, "Segundo"));

        TrackedTeamCodec.Decoded decoded = TrackedTeamCodec.decode(json(1, teams));

        assertTrue(decoded.repaired);
        assertEquals(1, decoded.list.size());
        assertEquals("Primero", decoded.list.get(0).name);
    }

    @Test
    public void invalidEntriesAreDiscardedButValidOnesRemain() throws Exception {
        JSONArray teams = new JSONArray()
                .put(new JSONObject().put("id", 0).put("sportId", 1).put("name", "Sin id"))
                .put(teamJson(131, 3, "Deporte raro"))
                .put(teamJson(174, 1, "Rayo"));

        TrackedTeamCodec.Decoded decoded = TrackedTeamCodec.decode(json(1, teams));

        assertTrue(decoded.repaired);
        assertEquals(1, decoded.list.size());
        assertEquals("Rayo", decoded.list.get(0).name);
    }

    @Test
    public void nonEmptyArrayWithoutValidEntriesFallsBackToDefaults() throws Exception {
        JSONArray teams = new JSONArray()
                .put(teamJson(0, 1, "Sin id"))
                .put(teamJson(131, 9, "Deporte raro"));

        TrackedTeamCodec.Decoded decoded = TrackedTeamCodec.decode(json(1, teams));

        assertTrue(decoded.repaired);
        assertEquals(6, decoded.list.size());
        assertEquals(131L, decoded.list.get(0).competitorId);
    }

    @Test
    public void moreThanTenTeamsKeepsTheFirstTen() throws Exception {
        JSONArray teams = new JSONArray();
        for (int i = 1; i <= 12; i++) teams.put(teamJson(i, 1, "Equipo " + i));

        TrackedTeamCodec.Decoded decoded = TrackedTeamCodec.decode(json(1, teams));

        assertTrue(decoded.repaired);
        assertEquals(TrackedTeamList.MAX_TEAMS, decoded.list.size());
        assertEquals(1L, decoded.list.get(0).competitorId);
        assertEquals(10L, decoded.list.get(9).competitorId);
    }

    @Test
    public void futureVersionIsReadOnlyAndKeepsKnownFields() throws Exception {
        JSONArray teams = new JSONArray().put(teamJson(131, 1, "Real Madrid"));

        TrackedTeamCodec.Decoded decoded = TrackedTeamCodec.decode(json(2, teams));

        assertTrue(decoded.readOnly);
        assertTrue(decoded.list.isReadOnly());
        assertFalse(decoded.migrated);
        assertFalse(decoded.repaired);
        assertEquals(1, decoded.list.size());
        assertEquals("Real Madrid", decoded.list.get(0).name);
    }

    @Test
    public void missingFieldsGetDefaultsAndGeneratedValues() throws Exception {
        JSONArray teams = new JSONArray()
                .put(new JSONObject().put("id", 131).put("sportId", 1)
                        .put("name", "Real Madrid"))
                .put(new JSONObject().put("id", -1).put("sportId", 1)
                        .put("name", "As Celtas"));

        TrackedTeamCodec.Decoded decoded = TrackedTeamCodec.decode(json(1, teams));

        TrackedTeam madrid = decoded.list.get(0);
        assertEquals(TrackedTeam.PROVIDER_365, madrid.provider);
        assertEquals("REAL MADRID", madrid.shortName);
        assertEquals(TrackedTeam.logoUrl(131L), madrid.logoUrl);
        assertTrue(madrid.enabled);
        assertEquals("", madrid.country);

        TrackedTeam asCeltas = decoded.list.get(1);
        assertEquals(TrackedTeam.PROVIDER_AS_CELTAS, asCeltas.provider);
        assertEquals(TrackedTeam.AS_CELTAS_ID, asCeltas.competitorId);
        assertEquals("", asCeltas.logoUrl);
    }

    @Test
    public void explicitProviderIsRespectedButUnknownProviderIsInvalid() throws Exception {
        JSONArray teams = new JSONArray()
                .put(new JSONObject().put("provider", "otro").put("id", 131)
                        .put("sportId", 1).put("name", "Equipo"))
                .put(new JSONObject().put("provider", TrackedTeam.PROVIDER_365)
                        .put("id", 174).put("sportId", 1).put("name", "Rayo"));

        TrackedTeamCodec.Decoded decoded = TrackedTeamCodec.decode(json(1, teams));

        assertTrue(decoded.repaired);
        assertEquals(1, decoded.list.size());
        assertEquals("Rayo", decoded.list.get(0).name);
    }

    @Test
    public void encodeIsDeterministicAndStableAfterDecode() {
        TrackedTeamList original = new TrackedTeamList(TrackedTeam.defaults(), false);
        original.setEnabled(TrackedTeam.asCeltas().key(), false);

        String first = TrackedTeamCodec.encode(original);
        String second = TrackedTeamCodec.encode(original);

        assertNotNull(first);
        assertFalse(first.isEmpty());
        assertEquals(first, second);
        assertEquals(first,
                TrackedTeamCodec.encode(TrackedTeamCodec.decode(first).list));
        assertEquals("", TrackedTeamCodec.encode(null));
    }

    private static void assertMigration(TrackedTeamCodec.Decoded decoded) {
        assertTrue(decoded.migrated);
        assertFalse(decoded.repaired);
        assertFalse(decoded.readOnly);
        assertEquals(6, decoded.list.size());
        assertEquals(TrackedTeam.defaults().get(0).key(),
                decoded.list.get(0).key());
    }

    private static String json(int version, JSONArray teams) throws Exception {
        return new JSONObject()
                .put("version", version)
                .put("teams", teams)
                .toString();
    }

    private static JSONObject teamJson(long id, int sportId, String name) throws Exception {
        return new JSONObject()
                .put("provider", TrackedTeam.PROVIDER_365)
                .put("id", id)
                .put("sportId", sportId)
                .put("name", name)
                .put("shortName", "")
                .put("country", "España")
                .put("competition", "Liga")
                .put("logo", "")
                .put("enabled", true);
    }
}
