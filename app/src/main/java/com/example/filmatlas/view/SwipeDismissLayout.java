package com.example.filmatlas.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.widget.NestedScrollView;

public class SwipeDismissLayout extends ConstraintLayout {

    public interface Callback {
        void onDismiss();
    }

    private View card;
    private NestedScrollView scroll;
    private Callback callback;

    private int touchSlop;
    private int dismissDistancePx;

    private float startRawX;
    private float startRawY;

    private boolean dragging;

    private VelocityTracker vt;

    private boolean scrollAtBottom;

    private static final float FADE_MAX = 0.35f;

    private static final float FLING_UP_VELOCITY = -1200f;

    private static final float FLING_RIGHT_VELOCITY = 1200f;

    private static final int RESET_ANIM_MS = 150;

    public SwipeDismissLayout(@NonNull Context context) {
        super(context);
        init(context);
    }

    public SwipeDismissLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SwipeDismissLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(@NonNull Context context) {
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        float d = context.getResources().getDisplayMetrics().density;
        dismissDistancePx = (int) (120 * d + 0.5f);

        setClickable(true);
        setFocusable(true);
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private boolean isTouchInsideScroll(@NonNull MotionEvent ev) {
        if (scroll == null) return false;

        int[] loc = new int[2];
        scroll.getLocationOnScreen(loc);

        float x = ev.getRawX();
        float y = ev.getRawY();

        float left = loc[0];
        float top = loc[1];
        float right = left + scroll.getWidth();
        float bottom = top + scroll.getHeight();

        return x >= left && x <= right && y >= top && y <= bottom;
    }

    public void setup(@NonNull View cardToMove,
                      @Nullable NestedScrollView scrollView,
                      @NonNull Callback cb) {
        this.card = cardToMove;
        this.scroll = scrollView;
        this.callback = cb;

        card.setClickable(true);
        card.setOnTouchListener((v, ev) -> handleCardTouch(ev));

        if (this.scroll == null) {
            scrollAtBottom = true;
            return;
        }

        this.scroll.setNestedScrollingEnabled(true);

        this.scroll.post(() -> scrollAtBottom = !SwipeDismissLayout.this.scroll.canScrollVertically(1));

        this.scroll.setOnScrollChangeListener((NestedScrollView v, int sx, int sy, int osx, int osy) -> {
            scrollAtBottom = !v.canScrollVertically(1);
        });

        @SuppressLint("ClickableViewAccessibility")
        View.OnTouchListener gate = (v, ev) -> {
            // If this gesture becomes a dismiss swipe, consume it so scroll doesn't fight us.
            if (handleCardTouch(ev)) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }

            // Otherwise keep normal scroll behavior.
            boolean atBottomNow = !v.canScrollVertically(1);
            v.getParent().requestDisallowInterceptTouchEvent(!atBottomNow);

            if (ev.getActionMasked() == MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return false;
        };

        this.scroll.setOnTouchListener(gate);
    }

    private boolean handleCardTouch(@NonNull MotionEvent ev) {
        if (card == null || callback == null) return false;

        final boolean landscape = isLandscape();

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                startRawX = ev.getRawX();
                startRawY = ev.getRawY();
                dragging = false;

                if (vt != null) {
                    vt.recycle();
                    vt = null;
                }
                vt = VelocityTracker.obtain();
                vt.addMovement(ev);

                return false;
            }

            case MotionEvent.ACTION_MOVE: {
                if (vt != null) vt.addMovement(ev);

                float dx = ev.getRawX() - startRawX;
                float dy = ev.getRawY() - startRawY;

                if (!landscape) {
                    if (Math.abs(dy) <= Math.abs(dx)) return false;

                    boolean swipeUp = dy < -touchSlop;

                    boolean startedInScroll = isTouchInsideScroll(ev);
                    boolean allowDismissNow = (scroll == null) || scrollAtBottom || !startedInScroll;

                    if (!dragging && swipeUp && allowDismissNow) {
                        dragging = true;
                        requestDisallowInterceptTouchEvent(true);
                    }

                    if (dragging) {
                        float translateY = Math.min(0f, dy); // negative only
                        card.setTranslationY(translateY);
                        card.setTranslationX(0f);

                        float progress = Math.min(1f, Math.abs(translateY) / (float) dismissDistancePx);
                        card.setAlpha(1f - (FADE_MAX * progress));
                        return true;
                    }

                    return false;

                } else {
                    if (Math.abs(dx) <= Math.abs(dy)) return false;

                    boolean swipeRight = dx > touchSlop;

                    if (!dragging && swipeRight) {
                        dragging = true;
                        requestDisallowInterceptTouchEvent(true);
                    }

                    if (dragging) {
                        float translateX = Math.max(0f, dx); // positive only (right)
                        card.setTranslationX(translateX);
                        card.setTranslationY(0f);

                        float progress = Math.min(1f, Math.abs(translateX) / (float) dismissDistancePx);
                        card.setAlpha(1f - (FADE_MAX * progress));
                        return true;
                    }

                    return false;
                }
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!dragging) {
                    if (vt != null) {
                        vt.recycle();
                        vt = null;
                    }
                    return false;
                }

                if (vt != null) {
                    vt.addMovement(ev);
                    vt.computeCurrentVelocity(1000);
                }

                float translate;
                float velocity;

                if (!landscape) {
                    translate = card.getTranslationY();
                    velocity = (vt != null) ? vt.getYVelocity() : 0f;
                } else {
                    translate = card.getTranslationX();
                    velocity = (vt != null) ? vt.getXVelocity() : 0f;
                }

                if (vt != null) {
                    vt.recycle();
                    vt = null;
                }

                if (Math.abs(translate) < touchSlop) {
                    performClick();
                }

                boolean farEnough = Math.abs(translate) > dismissDistancePx;

                boolean fling;
                if (!landscape) {
                    fling = velocity < FLING_UP_VELOCITY;
                } else {
                    fling = velocity > FLING_RIGHT_VELOCITY;
                }

                if (farEnough || fling) {
                    callback.onDismiss();
                } else {
                    card.animate()
                            .translationX(0f)
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(RESET_ANIM_MS)
                            .start();
                }

                dragging = false;
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (card == null || callback == null) return super.onInterceptTouchEvent(ev);

        final boolean landscape = isLandscape();

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                startRawX = ev.getRawX();
                startRawY = ev.getRawY();
                dragging = false;

                if (vt != null) {
                    vt.recycle();
                    vt = null;
                }
                vt = VelocityTracker.obtain();
                vt.addMovement(ev);

                return false;
            }

            case MotionEvent.ACTION_MOVE: {
                if (vt != null) vt.addMovement(ev);

                float dx = ev.getRawX() - startRawX;
                float dy = ev.getRawY() - startRawY;

                if (!landscape) {
                    if (Math.abs(dy) <= Math.abs(dx)) return false;

                    boolean swipeUp = dy < -touchSlop;

                    boolean startedInScroll = isTouchInsideScroll(ev);
                    boolean allowDismissNow = (scroll == null) || scrollAtBottom || !startedInScroll;

                    if (swipeUp && allowDismissNow) {
                        dragging = true;
                        return true;
                    }
                    return false;

                } else {
                    if (Math.abs(dx) <= Math.abs(dy)) return false;

                    boolean swipeRight = dx > touchSlop;
                    if (swipeRight) {
                        dragging = true;
                        return true;
                    }
                    return false;
                }
            }

            default:
                return super.onInterceptTouchEvent(ev);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (handleCardTouch(event)) return true;
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
