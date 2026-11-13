package com.itriguero.homeclock;

import java.time.LocalDate;
import java.util.List;

/**
 * Fuente de partidos para una fecha.
 */
interface MatchSource {

    String id();

    SourceResult<List<Match>> fetch(LocalDate date);
}
