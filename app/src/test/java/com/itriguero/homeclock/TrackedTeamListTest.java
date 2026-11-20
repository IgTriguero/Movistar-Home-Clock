package com.itriguero.homeclock;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TrackedTeamListTest {

    @Test
    public void emptyListIsValid() {
        TrackedTeamList list = new TrackedTeamList(Collections.emptyList(), false);

        assertEquals(0, list.size());
        assertEquals(0, list.activeCount());
        assertTrue(list.teams().isEmpty());
        assertTrue(list.selection().isEmpty());
        assertEquals("", list.activeSignature());
    }

    @Test
    public void nullListAlsoCreatesAnEmptyList() {
        TrackedTeamList list = new TrackedTeamList(null, false);

        assertEquals(0, list.size());
    }

    @Test
    public void constructorFiltersInvalidAndDuplicateEntries() {
        TrackedTeam repeated = team(131);
        List<TrackedTeam> input = new ArrayList<>();
        input.add(null);
        input.add(team(0));
        input.add(repeated);
        input.add(repeated);
        input.add(team(174));

        TrackedTeamList list = new TrackedTeamList(input, false);

        assertEquals(2, list.size());
        assertEquals(131L, list.get(0).competitorId);
        assertEquals(174L, list.get(1).competitorId);
    }

    @Test
    public void constructorCapsAtTenTeams() {
        List<TrackedTeam> input = new ArrayList<>();
        for (int i = 1; i <= 12; i++) input.add(team(i));

        TrackedTeamList list = new TrackedTeamList(input, false);

        assertEquals(TrackedTeamList.MAX_TEAMS, list.size());
        assertEquals(1L, list.get(0).competitorId);
        assertEquals(10L, list.get(9).competitorId);
    }

    @Test
    public void addWorksUntilFull() {
        TrackedTeamList list = new TrackedTeamList(Collections.emptyList(), false);

        for (int i = 1; i <= TrackedTeamList.MAX_TEAMS; i++) {
            assertEquals(TrackedTeamList.AddResult.ADDED, list.add(team(i)));
        }

        assertEquals(TrackedTeamList.MAX_TEAMS, list.size());
        assertEquals(TrackedTeamList.AddResult.FULL, list.add(team(99)));
    }

    @Test
    public void duplicateIsReportedBeforeFull() {
        TrackedTeamList list = new TrackedTeamList(Collections.emptyList(), false);
        for (int i = 1; i <= TrackedTeamList.MAX_TEAMS; i++) list.add(team(i));

        assertEquals(TrackedTeamList.AddResult.DUPLICATE, list.add(team(3)));
        assertEquals(TrackedTeamList.AddResult.FULL, list.add(team(99)));
    }

    @Test
    public void addRejectsNullAndInvalidTeams() {
        TrackedTeamList list = new TrackedTeamList(Collections.emptyList(), false);

        assertEquals(TrackedTeamList.AddResult.INVALID, list.add(null));
        assertEquals(TrackedTeamList.AddResult.INVALID, list.add(team(0)));
        assertEquals(0, list.size());
    }

    @Test
    public void removeAllowsLeavingTheListEmpty() {
        TrackedTeamList list = new TrackedTeamList(
                Collections.singletonList(team(131)), false);

        assertEquals(TrackedTeamList.RemoveResult.REMOVED,
                list.remove("365scores:1:131"));
        assertEquals(0, list.size());
        assertEquals(TrackedTeamList.RemoveResult.NOT_FOUND,
                list.remove("365scores:1:131"));
    }

    @Test
    public void setEnabledOnlyChangesExistingTeams() {
        TrackedTeamList list = new TrackedTeamList(
                Collections.singletonList(team(131)), false);

        assertTrue(list.setEnabled("365scores:1:131", false));
        assertFalse(list.get(0).enabled);
        assertEquals(0, list.activeCount());
        assertTrue(list.setEnabled("365scores:1:131", true));
        assertTrue(list.get(0).enabled);
        assertFalse(list.setEnabled("365scores:1:999", false));
    }

    @Test
    public void activeCountCountsOnlyEnabled() {
        TrackedTeamList list = new TrackedTeamList(TrackedTeam.defaults(), false);

        assertEquals(6, list.activeCount());
        list.setEnabled("365scores:1:174", false);
        list.setEnabled(TrackedTeam.asCeltas().key(), false);

        assertEquals(4, list.activeCount());
    }

    @Test
    public void activeSignatureIgnoresConfigurationOrderAndDisabledTeams() {
        TrackedTeamList list = new TrackedTeamList(TrackedTeam.defaults(), false);
        String initial = list.activeSignature();
        assertTrue(initial.contains("365scores:1:131"));
        assertTrue(initial.contains("as-celtas:1:-1"));

        List<TrackedTeam> reversed = new ArrayList<>(TrackedTeam.defaults());
        Collections.reverse(reversed);
        assertEquals(initial,
                new TrackedTeamList(reversed, false).activeSignature());

        list.setEnabled("365scores:1:108", false);

        assertNotEquals(initial, list.activeSignature());
        assertFalse(list.activeSignature().contains("365scores:1:108"));
    }

    @Test
    public void findAndIndexOfUseTheFullKey() {
        TrackedTeamList list = new TrackedTeamList(TrackedTeam.defaults(), false);

        assertEquals(0, list.indexOf("365scores:1:131"));
        assertEquals(-1, list.indexOf("365scores:2:131"));
        assertEquals("Real Madrid", list.find("365scores:1:131").name);
        assertNull(list.find(null));
        assertEquals(TrackedTeam.SPORT_BASKETBALL, list.find365(204L).sportId);
        assertNull(list.find365(9999L));
        assertNull(list.find365(TrackedTeam.AS_CELTAS_ID));
        assertEquals(TrackedTeam.asCeltas().key(), list.findAsCeltas().key());
    }

    @Test
    public void readOnlyBlocksEveryMutation() {
        TrackedTeamList list = new TrackedTeamList(TrackedTeam.defaults(), true);

        assertTrue(list.isReadOnly());
        assertEquals(TrackedTeamList.AddResult.READ_ONLY, list.add(team(999)));
        assertEquals(TrackedTeamList.RemoveResult.READ_ONLY,
                list.remove("365scores:1:131"));
        assertFalse(list.setEnabled("365scores:1:131", false));
        assertTrue(list.get(0).enabled);
    }

    @Test
    public void panelLabelFindsTeamsOrFallsBack() {
        TrackedTeamList list = new TrackedTeamList(TrackedTeam.defaults(), false);

        assertEquals("REAL MADRID", list.panelLabel(131L, "R. MADRID"));
        assertEquals("AS CELTAS", list.panelLabel(TrackedTeam.AS_CELTAS_ID, "CELTA"));
        assertEquals("Rival", list.panelLabel(9999L, " Rival "));
        assertEquals("", list.panelLabel(9999L, null));
    }

    @Test
    public void panelLabelDisambiguatesBasketballWhenFootballSharesName() {
        TrackedTeam football = new TrackedTeam(TrackedTeam.PROVIDER_365, 5050L,
                TrackedTeam.SPORT_FOOTBALL, "España", "ESPAÑA",
                "España", "Selección", "", true);
        TrackedTeam basketball = new TrackedTeam(TrackedTeam.PROVIDER_365, 2393L,
                TrackedTeam.SPORT_BASKETBALL, "España", "España",
                "España", "Selección", "", true);
        TrackedTeamList list = new TrackedTeamList(
                List.of(football, basketball), false);

        assertEquals("ESPAÑA", list.panelLabel(5050L, ""));
        assertEquals("España BAL.", list.panelLabel(2393L, ""));

        // Los defaults ya traen el sufijo: no se duplica.
        TrackedTeamList defaults = new TrackedTeamList(TrackedTeam.defaults(), false);
        assertEquals("R. MADRID BAL.", defaults.panelLabel(204L, ""));
    }

    @Test
    public void panelLabelLeavesBasketballAloneWithoutAFootballNamesake() {
        TrackedTeam basketball = new TrackedTeam(TrackedTeam.PROVIDER_365, 2393L,
                TrackedTeam.SPORT_BASKETBALL, "España", "España",
                "España", "Selección", "", true);
        TrackedTeamList list = new TrackedTeamList(
                Collections.singletonList(basketball), false);

        assertEquals("España", list.panelLabel(2393L, ""));
    }

    @Test
    public void selectionContainsOnlyActive365IdsAndAsCeltas() {
        TrackedTeamList list = new TrackedTeamList(TrackedTeam.defaults(), false);

        TeamSelection selection = list.selection();

        assertFalse(selection.isEmpty());
        assertEquals(List.of(108L, 131L, 174L, 204L, 5050L),
                selection.scores365Ids());
        assertTrue(selection.asCeltasEnabled());
        assertEquals(list.activeSignature(), selection.signature());

        list.setEnabled("365scores:1:131", false);
        list.setEnabled(TrackedTeam.asCeltas().key(), false);

        TeamSelection reduced = list.selection();
        assertEquals(List.of(108L, 174L, 204L, 5050L), reduced.scores365Ids());
        assertFalse(reduced.asCeltasEnabled());
    }

    @Test
    public void selectionStartsEmptyAndTeamsCopyIsImmutable() {
        TrackedTeamList list = new TrackedTeamList(
                Collections.singletonList(team(131)), false);

        assertTrue(new TrackedTeamList(null, false).selection().isEmpty());
        try {
            list.teams().add(team(174));
            fail("La copia de equipos debe ser inmutable");
        } catch (UnsupportedOperationException expected) {
            // Correcto.
        }
    }

    private static TrackedTeam team(long id) {
        return new TrackedTeam(TrackedTeam.PROVIDER_365, id,
                TrackedTeam.SPORT_FOOTBALL, "Equipo " + id, "EQ " + id,
                "España", "Liga", "", true);
    }
}
