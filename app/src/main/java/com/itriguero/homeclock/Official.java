package com.itriguero.homeclock;

/**
 * Árbitro u otro oficial del partido. Si la fuente no da rol se usa
 * "Árbitro", que es el único que suele publicar 365Scores.
 */
final class Official {

    public final String name;
    public final String role;

    public Official(String name, String role) {
        this.name = name == null ? "" : name;
        String normalized = role == null ? "" : role.trim();
        this.role = normalized.isEmpty() ? "Árbitro" : normalized;
    }
}
