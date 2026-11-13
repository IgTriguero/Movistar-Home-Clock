package com.itriguero.homeclock;

import java.time.LocalDate;

/**
 * Resuelve la temporada deportiva (año de fin) a partir de una fecha.
 */
final class SeasonResolver {

    private SeasonResolver() {
    }

    static String forDate(LocalDate date) {
        int year = date.getYear();
        return String.valueOf(date.getMonthValue() >= 7 ? year + 1 : year);
    }
}
