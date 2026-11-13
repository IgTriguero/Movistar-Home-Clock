package com.itriguero.homeclock;

/**
 * Resultado de una fuente de datos, independiente de la fuente concreta.
 */
public enum SourceState {
    OK,
    EMPTY,
    TIMEOUT,
    HTTP_ERROR,
    PARSE_ERROR,
    OFFLINE,
    ERROR
}
