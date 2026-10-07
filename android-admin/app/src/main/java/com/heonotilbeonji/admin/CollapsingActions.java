package com.heonotilbeonji.admin;

import android.animation.ValueAnimator;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** Reveal controls while scrolling back; keep the main app title visible. */
final class CollapsingActions {
    private boolean hidden, animating;
    private int distance;
    private final View controls;
    private final int threshold;

    private CollapsingActions(View controls) {
        this.controls = controls;
        threshold = Math.round(32 * controls.getResources().getDisplayMetrics().density);
    }
    static void attach(ScrollView scroll, View controls) {
        CollapsingActions behavior = new CollapsingActions(controls);
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> behavior.scroll(y, oldY));
    }
    void scroll(int y, int oldY) {
        if (animating || controls.findFocus() instanceof android.widget.EditText) return;
        int delta = y - oldY;
        if ((delta > 0 && distance < 0) || (delta < 0 && distance > 0)) distance = 0;
        distance += delta;
        if (y <= 0 || distance < -threshold) toggle(false);
        else if (distance > threshold) toggle(true);
    }
    private void toggle(boolean hide) {
        if (hidden == hide) return;
        hidden = hide; distance = 0;
        controls.setImportantForAccessibility(hide ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        int naturalHeight = controls.getHeight();
        if (!hide) {
            controls.measure(View.MeasureSpec.makeMeasureSpec(controls.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            naturalHeight = controls.getMeasuredHeight();
        }
        ValueAnimator animation = ValueAnimator.ofInt(controls.getHeight(), hide ? 0 : naturalHeight);
        animating = true;
        animation.setDuration(180);
        animation.addUpdateListener(value -> {
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) controls.getLayoutParams();
            p.height = (int) value.getAnimatedValue(); controls.setLayoutParams(p);
        });
        animation.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator value) {
                animating = false;
                if (!hidden) { var p = controls.getLayoutParams(); p.height = -2; controls.setLayoutParams(p); }
            }
        });
        animation.start();
    }
}
