package com.itriguero.homeclock;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class TrackedTeamTest {

    @Test
    public void sameCompetitorInDifferentSportsHasDifferentKey() {
        TrackedTeam football = team(TrackedTeam.PROVIDER_365, 131, TrackedTeam.SPORT_FOOTBALL);
        TrackedTeam basketball = team(TrackedTeam.PROVIDER_365, 131, TrackedTeam.SPORT_BASKETBALL);
        TrackedTeam otherId = team(TrackedTeam.PROVIDER_365, 204, TrackedTeam.SPORT_BASKETBALL);

        assertNotEquals(football.key(), basketball.key());
        assertNotEquals(football.key(), otherId.key());
        assertNotEquals(basketball.key(), otherId.key());
    }

    @Test
    public void defaultsHaveSixEntriesInTheExpectedOrder() {
        List<TrackedTeam> defaults = TrackedTeam.defaults();

        assertEquals(6, defaults.size());
        assertEquals(131L, defaults.get(0).competitorId);
        assertEquals(174L, defaults.get(1).competitorId);
        assertEquals(108L, defaults.get(2).competitorId);
        assertEquals(5050L, defaults.get(3).competitorId);
        assertEquals(204L, defaults.get(4).competitorId);
        assertEquals(TrackedTeam.AS_CELTAS_ID, defaults.get(5).competitorId);
        assertEquals("Real Madrid", defaults.get(0).name);
        assertEquals("Real Madrid", defaults.get(4).name);
        assertEquals(TrackedTeam.SPORT_BASKETBALL, defaults.get(4).sportId);
        assertEquals("2ª Federación Femenina", defaults.get(5).competition);
        for (TrackedTeam team : defaults) {
            assertTrue(team.enabled);
            assertTrue(team.isValid());
        }
    }

    @Test
    public void default365IdsMatchesThe365Defaults() {
        List<Long> expected = new ArrayList<>();
        for (TrackedTeam team : TrackedTeam.defaults()) {
            if (TrackedTeam.PROVIDER_365.equals(team.provider)) {
                expected.add(team.competitorId);
            }
        }

        long[] ids = TrackedTeam.default365Ids();
        assertEquals(expected.size(), ids.length);
        for (int i = 0; i < ids.length; i++) {
            assertEquals(expected.get(i).longValue(), ids[i]);
        }
    }

    @Test
    public void asCeltasIsTheLastDefaultWithNoLogo() {
        TrackedTeam asCeltas = TrackedTeam.asCeltas();

        assertEquals(TrackedTeam.PROVIDER_AS_CELTAS, asCeltas.provider);
        assertEquals(TrackedTeam.AS_CELTAS_ID, asCeltas.competitorId);
        assertEquals(TrackedTeam.SPORT_FOOTBALL, asCeltas.sportId);
        assertEquals("As Celtas", asCeltas.name);
        assertEquals("AS CELTAS", asCeltas.shortName);
        assertEquals("", asCeltas.logoUrl);
        assertEquals("Fútbol · RFEF/Celta", asCeltas.badge());
    }

    @Test
    public void rejectsUnknownProviderAndInvalidIds() {
        assertFalse(team("otro", 131, TrackedTeam.SPORT_FOOTBALL).isValid());
        assertFalse(team(TrackedTeam.PROVIDER_365, 0, TrackedTeam.SPORT_FOOTBALL).isValid());
        assertFalse(team(TrackedTeam.PROVIDER_365, -1, TrackedTeam.SPORT_FOOTBALL).isValid());
        assertFalse(team(TrackedTeam.PROVIDER_AS_CELTAS, 5, TrackedTeam.SPORT_FOOTBALL).isValid());
        assertTrue(team(TrackedTeam.PROVIDER_AS_CELTAS,
                TrackedTeam.AS_CELTAS_ID, TrackedTeam.SPORT_FOOTBALL).isValid());
    }

    @Test
    public void rejectsUnknownSport() {
        assertFalse(team(TrackedTeam.PROVIDER_365, 131, 3).isValid());
        assertFalse(team(TrackedTeam.PROVIDER_365, 131, 0).isValid());
        assertTrue(team(TrackedTeam.PROVIDER_365, 131, TrackedTeam.SPORT_FOOTBALL).isValid());
        assertTrue(team(TrackedTeam.PROVIDER_365, 131, TrackedTeam.SPORT_BASKETBALL).isValid());
    }

    @Test
    public void rejectsTextFieldsOverTheLimit() {
        String longName = "a".repeat(81);
        String longShortName = "a".repeat(25);

        assertFalse(team("365scores", 131, 1, longName, "CORTO").isValid());
        assertTrue(team("365scores", 131, 1, longName.substring(0, 80), "CORTO").isValid());
        assertFalse(team("365scores", 131, 1, "Equipo", longShortName).isValid());
        assertFalse(new TrackedTeam("365scores", 131, 1, "Equipo", "CORTO",
                "a".repeat(81), "", "", true).isValid());
        assertFalse(new TrackedTeam("365scores", 131, 1, "Equipo", "CORTO",
                "", "a".repeat(81), "", true).isValid());
    }

    @Test
    public void generatesShortNameWhenMissing() {
        TrackedTeam generated = team("365scores", 131, 1, "Real Madrid");
        TrackedTeam explicit = team("365scores", 131, 1, "Real Madrid", "R. MADRID");

        assertEquals("REAL MADRID", generated.shortName);
        assertEquals("R. MADRID", explicit.shortName);
    }

    @Test
    public void autoShortNameKeepsAnApiPrefixAndUppercasesOtherwise() {
        assertEquals("Real", TrackedTeam.autoShortName("Real Madrid", "Real"));
        assertEquals("REAL MADRID", TrackedTeam.autoShortName("Real Madrid", "RM"));
        assertEquals("REAL MADRID", TrackedTeam.autoShortName("Real Madrid", ""));
        assertEquals("REAL MADRID", TrackedTeam.autoShortName("Real Madrid", null));
    }

    @Test
    public void autoShortNameCollapsesSpacesAndTruncates() {
        assertEquals("REAL MADRID", TrackedTeam.autoShortName("  Real   Madrid  ", ""));
        assertEquals("ABCDEFGHIJKLMNOPQRSTUVWX",
                TrackedTeam.autoShortName("abcdefghijklmnopqrstuvwxyz", ""));
    }

    @Test
    public void basketballSuffixIsIdempotentAndRespectsTheLimit() {
        assertEquals("España BAL.", TrackedTeam.withBasketballSuffix("España"));
        assertEquals("ESPAÑA BAL.", TrackedTeam.withBasketballSuffix("ESPAÑA BAL."));
        assertEquals("ESPAÑA BAL", TrackedTeam.withBasketballSuffix("ESPAÑA BAL"));
        assertEquals(24,
                TrackedTeam.withBasketballSuffix("abcdefghijklmnopqrstuvwxyz").length());
    }

    @Test
    public void generatesLogoFor365TeamsOnly() {
        TrackedTeam realMadrid = TrackedTeam.defaults().get(0);
        TrackedTeam asCeltas = TrackedTeam.asCeltas();

        assertTrue(realMadrid.logoUrl.contains("/Competitors/131"));
        assertEquals("", asCeltas.logoUrl);
    }

    @Test
    public void withEnabledReturnsANewCopy() {
        TrackedTeam original = TrackedTeam.defaults().get(1);
        TrackedTeam disabled = original.withEnabled(false);

        assertTrue(original.enabled);
        assertFalse(disabled.enabled);
        assertEquals(original.key(), disabled.key());
        assertEquals(original.name, disabled.name);
        assertEquals(original.shortName, disabled.shortName);
    }

    @Test
    public void settingsLabelAndDetailSkipMissingValues() {
        TrackedTeam realMadrid = TrackedTeam.defaults().get(0);
        TrackedTeam withoutCountry = new TrackedTeam("365scores", 5, 1,
                "Equipo", "EQ", "", "Liga", "", true);

        assertEquals("Real Madrid · Fútbol", realMadrid.settingsLabel());
        assertEquals("LaLiga · España", realMadrid.settingsDetail());
        assertEquals("Liga", withoutCountry.settingsDetail());
        assertEquals("Fútbol", realMadrid.badge());
        assertEquals("Baloncesto", TrackedTeam.defaults().get(4).badge());
    }

    @Test
    public void panelLabelUsesShortNameThenFallbackThenUpperCaseName() {
        TrackedTeam realMadrid = TrackedTeam.defaults().get(0);
        TrackedTeam withoutName = new TrackedTeam("365scores", 9, 1,
                "", "", "", "", "", true);

        assertEquals("REAL MADRID", realMadrid.panelLabel("R. MADRID"));
        assertEquals("RIVAL", withoutName.panelLabel(" RIVAL "));
        assertEquals("", withoutName.panelLabel(""));
    }

    @Test
    public void sportReturnsReadableNames() {
        assertEquals("Fútbol", team("365scores", 131, 1).sport());
        assertEquals("Baloncesto", team("365scores", 204, 2).sport());
        assertEquals("", new TrackedTeam("365scores", 131, 3,
                "Equipo", "EQ", "", "", "", true).sport());
    }

    private static TrackedTeam team(String provider, long id, int sportId) {
        return team(provider, id, sportId, "Equipo " + id);
    }

    private static TrackedTeam team(String provider, long id, int sportId, String name) {
        return team(provider, id, sportId, name, "");
    }

    private static TrackedTeam team(String provider, long id, int sportId,
                                    String name, String shortName) {
        return new TrackedTeam(provider, id, sportId, name, shortName,
                "España", "Liga", "", true);
    }
}
