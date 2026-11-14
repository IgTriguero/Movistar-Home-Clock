package com.itriguero.homeclock;

/**
 * Una estadística comparada entre los dos equipos.
 */
final class StatRow {

    public final int id;
    public final String name;
    public final String homeValue;
    public final String awayValue;
    /** Proporción 0..1 de la fuente; -1 si no está disponible. */
    public final double homePercent;
    public final double awayPercent;
    public final boolean major;

    public StatRow(int id, String name, String homeValue, String awayValue,
                   double homePercent, double awayPercent, boolean major) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.homeValue = homeValue == null ? "" : homeValue;
        this.awayValue = awayValue == null ? "" : awayValue;
        this.homePercent = normalizePercent(homePercent);
        this.awayPercent = normalizePercent(awayPercent);
        this.major = major;
    }

    private static double normalizePercent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return -1;
        if (value < 0) return -1;
        return Math.min(1.0, value);
    }
}
