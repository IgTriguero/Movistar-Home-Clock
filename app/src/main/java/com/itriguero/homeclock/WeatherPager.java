package com.itriguero.homeclock;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * Paginador horizontal de tiempo con tres páginas reutilizadas: anterior,
 * actual y siguiente. No hay bucle: en los extremos el arrastre se amortigua y
 * vuelve a la página actual.
 *
 * <p>El paginador es el único nodo accesible; las páginas quedan ocultas para
 * accesibilidad. La integración debe llamar a {@link #setPageDescription}
 * (normalmente desde {@link OnPageChangedListener#onPageChanged(int)}) para que
 * los cambios se anuncien con TalkBack.
 */
public class WeatherPager extends FrameLayout {

    /** Contrato para pintar una página con los datos de su índice. */
    public interface Binder {
        void bind(WeatherPageView page, int index);
    }

    /** Aviso de cambio de página ya asentado. */
    public interface OnPageChangedListener {
        void onPageChanged(int index);
    }

    private static final int PAGE_SLOTS = 3;
    private static final long SETTLE_DURATION_MS = 220L;

    private final WeatherPageView[] pages = new WeatherPageView[PAGE_SLOTS];
    private final GestureDetector gestureDetector;
    private final int touchSlop;
    private final float density;

    private Binder binder;
    private OnPageChangedListener listener;
    private CharSequence pageDescription;
    private ValueAnimator animator;

    private int pageCount;
    private int currentItem;
    private int pageWidth;
    private float currentOffset;
    private float downX;
    private float downY;
    private float dragPx;
    private float flingVelocityLeft;
    private boolean dragging;

    public WeatherPager(Context context) {
        this(context, null);
    }

    public WeatherPager(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WeatherPager(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        density = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        gestureDetector = new GestureDetector(context, new GestureListener());
        gestureDetector.setIsLongpressEnabled(false);

        setClickable(false);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        setClipChildren(true);

        for (int i = 0; i < PAGE_SLOTS; i++) {
            WeatherPageView page = new WeatherPageView(context);
            page.setVisibility(INVISIBLE);
            pages[i] = page;
            addView(page, new LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }

        updatePageVisibility();
    }

    // ---------------------------------------------------------------------
    // API pública
    // ---------------------------------------------------------------------

    public void setBinder(Binder binder) {
        this.binder = binder;
        bindPages();
    }

    public void setPageCount(int count) {
        int safeCount = Math.max(0, count);
        int previous = currentItem;

        cancelAnimation();
        pageCount = safeCount;
        currentItem = safeCount == 0
                ? 0 : PagerMath.clamp(currentItem, 0, safeCount - 1);
        currentOffset = 0f;
        dragging = false;
        dragPx = 0f;
        flingVelocityLeft = 0f;
        applyOffsets();
        updatePageVisibility();
        bindPages();

        if (safeCount > 0 && previous != currentItem && listener != null) {
            listener.onPageChanged(currentItem);
        }
    }

    public int getCurrentItem() {
        return currentItem;
    }

    public int getPageCount() {
        return pageCount;
    }

    /**
     * Cambia de página. Si {@code animate} es true y el salto es de una sola
     * página se usa la animación de asentamiento; en cualquier otro caso el
     * cambio es inmediato.
     */
    public void setCurrentItem(int index, boolean animate) {
        if (pageCount <= 0) {
            cancelAnimation();
            currentItem = 0;
            currentOffset = 0f;
            applyOffsets();
            return;
        }

        int target = PagerMath.clamp(index, 0, pageCount - 1);
        if (target == currentItem) {
            cancelAnimation();
            if (currentOffset != 0f) {
                currentOffset = 0f;
                applyOffsets();
            }
            return;
        }

        cancelAnimation();
        currentOffset = 0f;
        applyOffsets();

        boolean adjacent = Math.abs(target - currentItem) == 1;
        if (animate && adjacent && pageWidth > 0
                && ValueAnimator.areAnimatorsEnabled()) {
            animateTo(target, false);
        } else {
            commitPage(target, false);
        }
    }

    /** Rebinda las tres páginas tras cambiar los datos sin mover el índice. */
    public void notifyPagesChanged() {
        bindPages();
        updatePageVisibility();
    }

    public void setOnPageChangedListener(OnPageChangedListener listener) {
        this.listener = listener;
    }

    /**
     * Descripción accesible de la página actual. La integración la actualiza
     * al cambiar de ciudad; se usa también para anunciar el cambio.
     */
    public void setPageDescription(CharSequence description) {
        pageDescription = description;
        setContentDescription(description);
    }

    // ---------------------------------------------------------------------
    // Posición y datos
    // ---------------------------------------------------------------------

    private void bindPages() {
        if (binder == null || pageCount <= 0) return;

        for (int slot = 0; slot < PAGE_SLOTS; slot++) {
            int index = currentItem + slot - 1;
            if (isValidIndex(index)) {
                binder.bind(pages[slot], index);
            }
        }
    }

    private void updatePageVisibility() {
        for (int slot = 0; slot < PAGE_SLOTS; slot++) {
            int index = currentItem + slot - 1;
            pages[slot].setVisibility(isValidIndex(index) ? VISIBLE : INVISIBLE);
        }
    }

    private boolean isValidIndex(int index) {
        return index >= 0 && index < pageCount;
    }

    private void applyOffsets() {
        float width = pageWidth > 0 ? pageWidth : 0f;
        pages[0].setTranslationX(-width + currentOffset);
        pages[1].setTranslationX(currentOffset);
        pages[2].setTranslationX(width + currentOffset);
    }

    private void commitPage(int target, boolean announce) {
        cancelAnimation();

        boolean changed = target != currentItem;
        currentItem = pageCount <= 0
                ? 0 : PagerMath.clamp(target, 0, pageCount - 1);
        currentOffset = 0f;
        applyOffsets();
        updatePageVisibility();
        bindPages();

        if (changed) {
            if (listener != null) listener.onPageChanged(currentItem);
            if (announce) announcePageChange();
        }
    }

    private void animateTo(final int target, final boolean announce) {
        cancelAnimation();

        if (pageWidth <= 0) {
            commitPage(target, announce);
            return;
        }

        float end = (currentItem - target) * (float) pageWidth;
        if (currentOffset == end) {
            commitPage(target, announce);
            return;
        }

        ValueAnimator animation = ValueAnimator.ofFloat(currentOffset, end);
        animation.setDuration(SETTLE_DURATION_MS);
        animation.setInterpolator(new DecelerateInterpolator());
        animation.addUpdateListener(valueAnimator -> {
            if (animator != valueAnimator) return;
            currentOffset = (Float) valueAnimator.getAnimatedValue();
            applyOffsets();
        });
        animation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animator != animation) return;
                animator = null;
                currentOffset = 0f;
                commitPage(target, announce);
            }
        });

        animator = animation;
        animation.start();
    }

    private void cancelAnimation() {
        ValueAnimator running = animator;
        if (running == null) return;

        animator = null;
        running.removeAllUpdateListeners();
        running.removeAllListeners();
        running.cancel();
    }

    // ---------------------------------------------------------------------
    // Gestos
    // ---------------------------------------------------------------------

    private final class GestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDown(MotionEvent event) {
            return true;
        }

        @Override
        public boolean onFling(MotionEvent first, MotionEvent second,
                               float velocityX, float velocityY) {
            if (first == null || second == null) return false;
            if (!PagerMath.isHorizontal(second.getX() - first.getX(),
                    second.getY() - first.getY())) {
                return false;
            }
            // MotionEvent usa positivo a la derecha; PagerMath, a la izquierda.
            flingVelocityLeft = -velocityX;
            return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || pageCount <= 1) {
            return super.onTouchEvent(event);
        }

        gestureDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                beginGesture(event);
                return true;

            case MotionEvent.ACTION_MOVE:
                updateDrag(event);
                return true;

            case MotionEvent.ACTION_UP:
                if (!dragging) performClick();
                finishGesture(true);
                return true;

            case MotionEvent.ACTION_CANCEL:
                finishGesture(false);
                return true;

            default:
                return true;
        }
    }

    private void beginGesture(MotionEvent event) {
        downX = event.getX();
        downY = event.getY();
        dragPx = 0f;
        flingVelocityLeft = 0f;
        dragging = false;

        cancelAnimation();
        if (currentOffset != 0f) {
            currentOffset = 0f;
            applyOffsets();
        }
    }

    private void updateDrag(MotionEvent event) {
        if (!dragging) {
            float dx = event.getX() - downX;
            float dy = event.getY() - downY;
            if (Math.abs(dx) > touchSlop && PagerMath.isHorizontal(dx, dy)) {
                dragging = true;
            }
        }

        if (!dragging) return;

        // Positivo al arrastrar hacia la izquierda, igual que PagerMath.
        dragPx = downX - event.getX();
        boolean atStart = currentItem == 0;
        boolean atEnd = currentItem == pageCount - 1;
        float effective = PagerMath.resistance(dragPx, pageWidth, atStart, atEnd);
        // Solo hay una página a cada lado: no se puede arrastrar más allá.
        if (pageWidth > 0) {
            if (effective > pageWidth) effective = pageWidth;
            else if (effective < -pageWidth) effective = -pageWidth;
        }
        // translationX crece hacia la derecha: el signo se invierte.
        currentOffset = -effective;
        applyOffsets();
    }

    private void finishGesture(boolean settleToTarget) {
        if (dragging) {
            int target = settleToTarget
                    ? PagerMath.settleTarget(currentItem, pageCount, dragPx,
                            flingVelocityLeft, pageWidth, density)
                    : currentItem;

            if (pageWidth > 0 && ValueAnimator.areAnimatorsEnabled()
                    && (target != currentItem || currentOffset != 0f)) {
                animateTo(target, true);
            } else {
                commitPage(target, true);
            }
        } else if (currentOffset != 0f) {
            currentOffset = 0f;
            applyOffsets();
        }

        dragging = false;
        dragPx = 0f;
        flingVelocityLeft = 0f;
    }

    // ---------------------------------------------------------------------
    // Accesibilidad
    // ---------------------------------------------------------------------

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);

        boolean scrollable = pageCount > 1;
        info.setScrollable(scrollable);
        if (!scrollable) return;

        if (currentItem < pageCount - 1) {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
                    getContext().getString(R.string.weather_next_city)));
        }
        if (currentItem > 0) {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
                    getContext().getString(R.string.weather_previous_city)));
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
            if (pageCount <= 1 || currentItem >= pageCount - 1) return false;
            changePageFromAccessibility(currentItem + 1);
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            if (pageCount <= 1 || currentItem <= 0) return false;
            changePageFromAccessibility(currentItem - 1);
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }

    private void changePageFromAccessibility(int target) {
        cancelAnimation();
        currentOffset = 0f;
        applyOffsets();

        if (pageWidth > 0 && ValueAnimator.areAnimatorsEnabled()) {
            animateTo(target, true);
        } else {
            commitPage(target, true);
        }
    }

    private void announcePageChange() {
        CharSequence description = pageDescription;
        if (description == null || description.length() == 0) {
            description = getContentDescription();
        }
        if (description == null || description.length() == 0) return;

        AccessibilityManager manager = (AccessibilityManager)
                getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null || !manager.isEnabled()
                || !manager.isTouchExplorationEnabled()) {
            return;
        }
        announceForAccessibility(description);
    }

    // ---------------------------------------------------------------------
    // Ciclo de vida
    // ---------------------------------------------------------------------

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);

        cancelAnimation();
        pageWidth = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        currentOffset = 0f;
        applyOffsets();
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelAnimation();
        currentOffset = 0f;
        dragging = false;
        dragPx = 0f;
        flingVelocityLeft = 0f;
        applyOffsets();
        super.onDetachedFromWindow();
    }
}
