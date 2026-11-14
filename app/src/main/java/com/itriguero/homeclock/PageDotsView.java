package com.itriguero.homeclock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Puntos de posición del paginador de tiempo.
 *
 * <p>Dibuja un punto por página, con el activo en {@code weather_dot_active} y
 * el resto en {@code weather_dot_inactive}. No es clicable ni accesible: el
 * nodo accesible es el {@link WeatherPager}. Con cero o una página se oculta.
 */
public class PageDotsView extends View {

    private static final int DOT_DP = 8;
    private static final int GAP_DP = 8;
    private static final int HEIGHT_DP = 16;

    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dotPx;
    private final float gapPx;
    private final int heightPx;
    private final int activeColor;
    private final int inactiveColor;

    private int count;
    private int selected;

    public PageDotsView(Context context) {
        this(context, null);
    }

    public PageDotsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PageDotsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        float density = getResources().getDisplayMetrics().density;
        dotPx = DOT_DP * density;
        gapPx = GAP_DP * density;
        heightPx = Math.round(HEIGHT_DP * density);
        activeColor = context.getColor(R.color.weather_dot_active);
        inactiveColor = context.getColor(R.color.weather_dot_inactive);

        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
        setDots(0, 0);
    }

    /**
     * Actualiza el número de puntos y el seleccionado. Ambos valores se
     * saturan; con {@code count <= 1} la vista pasa a {@link #GONE}.
     */
    public void setDots(int count, int selected) {
        int safeCount = Math.max(0, count);
        int safeSelected = safeCount == 0
                ? 0 : PagerMath.clamp(selected, 0, safeCount - 1);

        boolean countChanged = safeCount != this.count;
        this.count = safeCount;
        this.selected = safeSelected;

        setVisibility(safeCount > 1 ? VISIBLE : GONE);
        if (countChanged) requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float dotsWidth = count * dotPx + Math.max(0, count - 1) * gapPx;
        int desiredWidth = Math.round(dotsWidth)
                + getPaddingLeft() + getPaddingRight();
        int desiredHeight = heightPx + getPaddingTop() + getPaddingBottom();

        setMeasuredDimension(
                resolveSize(Math.max(desiredWidth, getSuggestedMinimumWidth()),
                        widthMeasureSpec),
                resolveSize(Math.max(desiredHeight, getSuggestedMinimumHeight()),
                        heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (count <= 1) return;

        float total = count * dotPx + (count - 1) * gapPx;
        float left = getPaddingLeft()
                + (getWidth() - getPaddingLeft() - getPaddingRight() - total) / 2f;
        float centerY = getPaddingTop()
                + (getHeight() - getPaddingTop() - getPaddingBottom()) / 2f;
        float radius = dotPx / 2f;

        for (int i = 0; i < count; i++) {
            dotPaint.setColor(i == selected ? activeColor : inactiveColor);
            canvas.drawCircle(left + radius + i * (dotPx + gapPx),
                    centerY, radius, dotPaint);
        }
    }
}
