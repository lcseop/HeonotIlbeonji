package com.heonotilbeonji.admin;

import android.app.Dialog;
import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Calendar;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35}, qualifiers = "w320dp-h640dp")
public class RequestNotesTest {
    @Test public void reservationFallbackAndSeparateAdminSave() throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity a = controller.get();
            JSONObject item = new JSONObject().put("id", "request-one").put("name", "홍길동").put("phone", "01012345678")
                    .put("date", "2026-10-08").put("timeSlot", "오후").put("message", "고객 원본 요청")
                    .put("reservedTime", "14:35").put("adminNote", "현관 앞 확인");
            assertEquals("14:35", RequestNotes.calendarTime(item));
            AtomicReference<JSONObject> saved = new AtomicReference<>();
            RequestNotes.edit(a, item, (data, result) -> { saved.set(data); result.accept(null); });
            shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
            assertNotNull(PlannerInteractionTest.text(dialog.getWindow().getDecorView(), "14:35"));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals("request-one", saved.get().optString("id"));
            assertEquals("14:35", saved.get().optString("reservedTime"));
            assertEquals("현관 앞 확인", saved.get().optString("adminNote"));
            assertFalse(saved.get().has("message")); assertEquals("고객 원본 요청", item.optString("message"));
            RequestNotes.edit(a, item, (data, result) -> { saved.set(data); result.accept(null); });
            shadowOf(android.os.Looper.getMainLooper()).idle();
            dialog = (AlertDialog) ShadowDialog.getLatestDialog();
            PlannerInteractionTest.text(dialog.getWindow().getDecorView(), "해제").performClick();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals("", saved.get().optString("reservedTime"));
            item.put("reservedTime", ""); assertEquals("오후", RequestNotes.calendarTime(item));
        }
    }

    @Test public void toolbarCollapsesAndReturnsWhenScrollingBack() {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity a = controller.get();
            LinearLayout shell = AdminPlanner.column(a); LinearLayout actions = AdminPlanner.column(a);
            actions.addView(new View(a), new LinearLayout.LayoutParams(-1, AdminPlanner.dp(a, 150)));
            shell.addView(actions);
            CollapsingActions.ScrollSurface scroll = new CollapsingActions.ScrollSurface(a); LinearLayout rows = AdminPlanner.column(a);
            for (int i = 0; i < 22; i++) rows.addView(new View(a), new LinearLayout.LayoutParams(-1, AdminPlanner.dp(a, 100)));
            scroll.addView(rows);
            shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            a.setContentView(shell); CollapsingActions.attach(scroll, actions);
            shadowOf(android.os.Looper.getMainLooper()).idle();
            int width = AdminPlanner.dp(a, 320), height = AdminPlanner.dp(a, 640);
            shell.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            shell.layout(0, 0, width, height);
            assertTrue(scroll.getChildAt(0).getHeight() > scroll.getHeight());
            scroll.scrollTo(0, AdminPlanner.dp(a, 100)); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
            assertEquals(0, actions.getLayoutParams().height);
            scroll.scrollTo(0, AdminPlanner.dp(a, 50)); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
            assertEquals(-2, actions.getLayoutParams().height);
        }
    }

    @Test public void calendarBadgeHasItsOwnSpaceAtLargeFontSize() throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity a = controller.get();
            android.content.res.Configuration config = new android.content.res.Configuration(a.getResources().getConfiguration());
            config.fontScale = 1.5f; a.getResources().updateConfiguration(config, a.getResources().getDisplayMetrics());
            Calendar month = AdminPlanner.calendar(); month.set(2026, Calendar.OCTOBER, 1);
            JSONArray requests = new JSONArray().put(new JSONObject().put("date", "2026-10-08").put("status", "new"))
                    .put(new JSONObject().put("date", "2026-10-08").put("status", "new"));
            View panel = AdminPlanner.month(a, month, "2026-10-08", requests, 0, new JSONArray(), day -> {}, move -> {});
            int width = AdminPlanner.dp(a, 288);
            panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.AT_MOST));
            panel.layout(0, 0, width, panel.getMeasuredHeight());
            TextView badge = (TextView) PlannerInteractionTest.text(panel, "2건");
            assertNotNull(badge); ViewGroup cell = (ViewGroup) badge.getParent();
            TextView date = (TextView) cell.getChildAt(0);
            assertTrue(badge.getTop() >= date.getBottom());
            assertTrue(badge.getBottom() <= cell.getHeight() - cell.getPaddingBottom());
            assertTrue(badge.getPaint().measureText("2건") <= badge.getWidth());
        }
    }

    @Test public void memoListUsesRequestNotesAndOpensTheirOwnRequest() throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity a = controller.get();
            JSONObject first = new JSONObject().put("id", "one").put("name", "고객 하나").put("phone", "01012345678")
                    .put("date", "2026-10-08").put("adminNote", "첫 방문 기록").put("reservedTime", "09:05");
            JSONObject second = new JSONObject().put("id", "two").put("name", "고객 둘").put("phone", "01012345678")
                    .put("date", "2026-10-09").put("adminNote", "재방문 기록").put("timeSlot", "오후");
            set(a, "memoPage", true); set(a, "requests", new JSONArray().put(first).put(second));
            call(a, "showInbox"); View root = a.getWindow().getDecorView();
            assertNotNull(PlannerInteractionTest.text(root, "첫 방문 기록"));
            assertNotNull(PlannerInteractionTest.text(root, "재방문 기록"));
            set(a, "memoSearch", "재방문"); call(a, "renderMemos");
            assertNull(PlannerInteractionTest.text(root, "첫 방문 기록"));
            View match = PlannerInteractionTest.text(root, "재방문 기록");
            ((View) match.getParent()).performClick();
            Dialog dialog = ShadowDialog.getLatestDialog();
            assertNotNull(PlannerInteractionTest.text(dialog.getWindow().getDecorView(), "고객 둘 님의 신청")); dialog.dismiss();
        }
    }
    private static void set(MainActivity a, String field, Object value) throws Exception {
        var f = MainActivity.class.getDeclaredField(field); f.setAccessible(true); f.set(a, value);
    }
    private static void call(MainActivity a, String method) throws Exception {
        var m = MainActivity.class.getDeclaredMethod(method); m.setAccessible(true); m.invoke(a);
    }
}
