package com.itriguero.homeclock;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;

public class SeasonResolverTest {

    @Test
    public void novemberBelongsToNextYearSeason() {
        assertEquals("2027", SeasonResolver.forDate(LocalDate.of(2026, 11, 12)));
    }

    @Test
    public void juneStillBelongsToCurrentSeason() {
        assertEquals("2027", SeasonResolver.forDate(LocalDate.of(2027, 6, 30)));
    }

    @Test
    public void julyStartsNextSeason() {
        assertEquals("2028", SeasonResolver.forDate(LocalDate.of(2027, 7, 1)));
    }

    @Test
    public void usesRequestedDateInsteadOfCurrentDate() {
        assertEquals("2025", SeasonResolver.forDate(LocalDate.of(2024, 8, 15)));
    }
}
