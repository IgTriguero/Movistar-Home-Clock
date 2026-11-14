package com.itriguero.homeclock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bloque de estadísticas (Remates, Pases, Disciplinarias...).
 */
final class StatCategory {

    public final String name;
    public final int order;
    public final List<StatRow> rows;

    public StatCategory(String name, int order, List<StatRow> rows) {
        this.name = name == null || name.trim().isEmpty() ? "General" : name.trim();
        this.order = order;
        if (rows == null || rows.isEmpty()) {
            this.rows = Collections.emptyList();
        } else {
            this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        }
    }
}
