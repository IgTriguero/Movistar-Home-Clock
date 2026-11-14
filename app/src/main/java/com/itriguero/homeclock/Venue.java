package com.itriguero.homeclock;

/**
 * Estadio de un partido. Los valores desconocidos quedan a 0.
 */
final class Venue {

    public final String name;
    public final int capacity;
    public final int attendance;

    public Venue(String name, int capacity, int attendance) {
        this.name = name == null ? "" : name;
        this.capacity = Math.max(0, capacity);
        this.attendance = Math.max(0, attendance);
    }
}
