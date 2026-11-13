package com.itriguero.homeclock;

import java.time.LocalTime;

/**
 * Lógica pura de la franja nocturna. La franja puede cruzar medianoche y el
 * final es exclusivo: a las 07:00 con fin 07:00 ya no es de noche.
 */
final class NightSchedule {

    private NightSchedule() {
    }

    /** Acepta únicamente HH:mm con dos dígitos y valores reales. */
    static boolean isValidHhMm(String value) {
        if (value == null || value.length() != 5 || value.charAt(2) != ':') {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (i == 2) continue;
            char c = value.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        int hour = Integer.parseInt(value.substring(0, 2));
        int minute = Integer.parseInt(value.substring(3, 5));
        return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59;
    }

    static LocalTime parseHhMm(String value) {
        if (!isValidHhMm(value)) {
            throw new IllegalArgumentException("Hora inválida: " + value);
        }
        return LocalTime.of(
                Integer.parseInt(value.substring(0, 2)),
                Integer.parseInt(value.substring(3, 5)));
    }

    /** Si el valor no es válido, usa el fallback (que debe ser válido). */
    static LocalTime parseOrDefault(String value, String fallback) {
        return isValidHhMm(value) ? parseHhMm(value) : parseHhMm(fallback);
    }

    static boolean isNight(LocalTime now, LocalTime start, LocalTime end) {
        if (now == null || start == null || end == null) return false;
        if (start.equals(end)) return false;

        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        // Cruza medianoche: [start, 24:00) ∪ [00:00, end).
        return !now.isBefore(start) || now.isBefore(end);
    }
}
