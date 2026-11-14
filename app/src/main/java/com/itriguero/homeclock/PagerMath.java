package com.itriguero.homeclock;

/**
 * Decisiones puras del paginador de tiempo: umbral de arrastre, fling,
 * resistencia en los extremos y detección de gesto horizontal.
 *
 * <p>Convención de signos: {@code dragPx} y {@code velocityX} son positivos
 * cuando el dedo se mueve hacia la izquierda (página siguiente) y negativos
 * cuando se mueve hacia la derecha (página anterior). Es el signo contrario al
 * delta de {@code MotionEvent.getX()} y al de {@code GestureDetector}, que son
 * negativos al arrastrar a la izquierda; {@link WeatherPager} invierte esos
 * valores antes de llamar aquí.
 */
final class PagerMath {

    /** Fracción del ancho que hay que arrastrar para cambiar de página. */
    static final float DRAG_FRACTION = 0.30f;

    /** Velocidad de fling que cambia de página, en dp por segundo. */
    static final float FLING_DP_PER_SECOND = 800f;

    /** Factor de amortiguación al arrastrar más allá del primer/último índice. */
    static final float RESISTANCE_FACTOR = 0.25f;

    /** Tope del desplazamiento amortiguado, como fracción del ancho. */
    static final float RESISTANCE_MAX_FRACTION = 0.25f;

    private PagerMath() {
    }

    /**
     * Índice al que debe asentarse el paginador tras un gesto.
     *
     * <p>Un fling por encima de {@link #FLING_DP_PER_SECOND} manda sobre la
     * distancia arrastrada. Si no hay fling, se exige al menos
     * {@link #DRAG_FRACTION} del ancho. El resultado siempre queda dentro de
     * {@code [0, count - 1]}, de modo que los extremos no dan la vuelta.
     *
     * @param current  índice actual (se satura al rango válido)
     * @param count    número de páginas
     * @param dragPx   arrastre positivo hacia la izquierda (siguiente)
     * @param velocityX velocidad positiva hacia la izquierda (siguiente)
     * @param width    ancho de una página en píxeles
     * @param density  densidad de pantalla; 800 dp/s se convierte a px/s
     * @return índice de destino, nunca fuera de rango
     */
    static int settleTarget(int current, int count, float dragPx,
                            float velocityX, int width, float density) {
        if (count <= 1) return 0;

        int item = clamp(current, 0, count - 1);
        float flingPx = FLING_DP_PER_SECOND * (density > 0f ? density : 1f);

        int direction = 0;
        if (Math.abs(velocityX) >= flingPx) {
            direction = velocityX > 0f ? 1 : -1;
        } else if (width > 0 && Math.abs(dragPx) >= width * DRAG_FRACTION) {
            direction = dragPx > 0f ? 1 : -1;
        }

        return clamp(item + direction, 0, count - 1);
    }

    /**
     * Amortigua el arrastre cuando ya no quedan páginas en esa dirección.
     *
     * <p>El resultado conserva el signo de {@code dragPx} y nunca supera
     * {@code width / 4}. Si el gesto no está bloqueado se devuelve el valor
     * original sin tocarlo.
     *
     * @param dragPx arrastre positivo hacia la izquierda
     * @param atStart true si la página actual es la primera
     * @param atEnd   true si la página actual es la última
     */
    static float resistance(float dragPx, int width, boolean atStart, boolean atEnd) {
        boolean blocked = (atStart && dragPx < 0f) || (atEnd && dragPx > 0f);
        if (!blocked) return dragPx;

        if (width <= 0) return 0f;

        float max = width * RESISTANCE_MAX_FRACTION;
        float resisted = Math.abs(dragPx) * RESISTANCE_FACTOR;
        if (resisted > max) resisted = max;
        return dragPx < 0f ? -resisted : resisted;
    }

    /**
     * true si el desplazamiento es claramente horizontal. Un empate se
     * considera vertical para no robar gestos de desplazamiento vertical.
     */
    static boolean isHorizontal(float dx, float dy) {
        return Math.abs(dx) > Math.abs(dy);
    }

    /** Satura {@code value} al rango cerrado {@code [min, max]}. */
    static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
