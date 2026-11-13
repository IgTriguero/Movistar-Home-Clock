package com.itriguero.homeclock;

/**
 * Desplazamiento anti-retención de la pantalla. Lógica pura, sin Android.
 *
 * <p>El contenido se mueve cada {@link #INTERVAL_MS} por un anillo de nueve
 * posiciones: el centro y las ocho direcciones en sentido horario. Los cuatro
 * puntos cardinales quedan a 4 dp y las diagonales a 3 dp, de modo que el
 * contenido ocupa nueve posiciones distintas antes de repetir y nunca se queda
 * fijo en el mismo píxel durante dos intervalos seguidos.
 */
final class PixelShift {

    static final long INTERVAL_MS = 5 * 60 * 1000L;

    /**
     * Patrón en dp, empezando por el centro y siguiendo el sentido horario
     * desde el norte. Las diagonales usan 3 dp para que su desplazamiento
     * total (≈4,24 dp) se mantenga cerca del cardinal de 4 dp.
     */
    private static final int[][] PATTERN = {
            {0, 0},     // centro
            {0, -4},    // norte
            {3, -3},    // noreste
            {4, 0},     // este
            {3, 3},     // sureste
            {0, 4},     // sur
            {-3, 3},    // suroeste
            {-4, 0},    // oeste
            {-3, -3},   // noroeste
    };

    private PixelShift() {
    }

    /**
     * Paso alcanzado tras {@code elapsedMs}. Usa división con suelo para que
     * los valores negativos retrocedan de forma simétrica. Satura en el rango
     * de {@code int} para no desbordar con duraciones enormes.
     */
    static int stepForElapsed(long elapsedMs) {
        long step = Math.floorDiv(elapsedMs, INTERVAL_MS);
        if (step > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (step < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) step;
    }

    /**
     * Desplazamiento en dp ({@code x, y}) para un paso. El patrón se repite
     * cada nueve pasos y admite pasos negativos. Devuelve una copia para que
     * el llamador no pueda alterar el patrón compartido.
     */
    static int[] offsetForStep(int step) {
        return PATTERN[Math.floorMod(step, PATTERN.length)].clone();
    }

    /** Desplazamiento en dp acumulado tras {@code elapsedMs}. */
    static int[] offsetForElapsed(long elapsedMs) {
        return offsetForStep(stepForElapsed(elapsedMs));
    }
}
