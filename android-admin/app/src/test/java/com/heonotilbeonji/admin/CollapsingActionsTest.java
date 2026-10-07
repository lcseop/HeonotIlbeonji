package com.heonotilbeonji.admin;

import android.app.Activity;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class CollapsingActionsTest {
    @Test public void revealOverShortClickableContentWithoutScrollEvents() throws Exception {
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            LinearLayout controls = new LinearLayout(activity);
            controls.addView(new Button(activity), new LinearLayout.LayoutParams(100, 100));
            root.addView(controls, new LinearLayout.LayoutParams(-1, -2));
            CollapsingActions.ScrollSurface scroll = new CollapsingActions.ScrollSurface(activity);
            root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            Button row = new Button(activity);
            row.setText("신청서");
            int[] clicks = {0};
            row.setOnClickListener(view -> clicks[0]++);
            scroll.addView(row, new android.widget.ScrollView.LayoutParams(-1, 150));
            activity.setContentView(root);
            CollapsingActions.attach(scroll, controls);
            layout(root);
            var field = CollapsingActions.ScrollSurface.class.getDeclaredField("behavior");
            field.setAccessible(true);
            // Simulate the menu left hidden after a previous, longer list.
            ((CollapsingActions) field.get(scroll)).scroll(200, 0);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
            layout(root);
            assertEquals(0, controls.getLayoutParams().height);
            assertFalse(scroll.canScrollVertically(1));
            assertFalse(scroll.canScrollVertically(-1));
            gesture(scroll, 20, 20, 22, 110);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
            assertEquals(-2, controls.getLayoutParams().height);
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, controls.getImportantForAccessibility());
            assertEquals(0, scroll.getScrollY());
            assertEquals(0, clicks[0]);
            layout(root);
            gesture(scroll, 20, 20, 20, 20);
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, clicks[0]);

            ((CollapsingActions) field.get(scroll)).scroll(200, 0);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
            gesture(scroll, 20, 20, 200, 40);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
            assertEquals(0, controls.getLayoutParams().height);
        }
    }
    private static void layout(View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 360, 640);
    }
    private static void gesture(View view, float x, float y, float endX, float endY) {
        int[] actions = {MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP};
        for (int i = 0; i < actions.length; i++) {
            MotionEvent event = MotionEvent.obtain(0, i * 30L, actions[i], i == 0 ? x : endX, i == 0 ? y : endY, 0);
            view.dispatchTouchEvent(event); event.recycle();
        }
    }
}
