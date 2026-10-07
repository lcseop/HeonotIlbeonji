package com.heonotilbeonji.admin;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35}, qualifiers = "w320dp-h640dp")
public class PlannerInteractionTest {
    @Test public void calendarCountsFilterAndLeapDaySelection() throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity a = controller.get(); Calendar month = AdminPlanner.calendar(); month.set(2028, Calendar.FEBRUARY, 1);
            JSONArray requests = new JSONArray().put(new JSONObject().put("date", "2028-02-29").put("status", "new"))
                    .put(new JSONObject().put("date", "2028-02-29").put("status", "done"));
            JSONArray notes = new JSONArray().put(new JSONObject().put("date", "2028-02-28").put("content", "동선 메모"));
            AtomicReference<String> selected = new AtomicReference<>();
            View panel = AdminPlanner.month(a, month, "2028-02-01", requests, RequestListControls.ALL, notes, selected::set, n -> {});
            assertNotNull(text(panel, "2건")); assertNotNull(text(panel, "메모"));
            View day = description(panel, "28년 2월 29일 (화), 신청 2건"); assertNotNull(day);
            day.performClick(); assertEquals("2028-02-29", selected.get());
            View filtered = AdminPlanner.month(a, month, "2028-02-01", requests, 1, notes, selected::set, n -> {});
            assertNotNull(text(filtered, "1건")); assertNull(text(filtered, "2건"));
        }
    }
    @Test public void contactImportAndQuickDateSaveWithoutTyping() throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity a = controller.get(); AtomicReference<JSONObject> saved = new AtomicReference<>();
            AtomicReference<AdminPlanner.FormEditor> picked = new AtomicReference<>();
            JSONObject previous = new JSONObject().put("name", "이전 고객").put("address", "김포시 예시 주소");
            AdminPlanner.FormEditor editor = new AdminPlanner.FormEditor(a, "", null, picked::set, p -> previous,
                    (data, result) -> { saved.set(data); result.accept(null); });
            text(editor.dialog.getWindow().getDecorView(), "연락처에서 가져오기").performClick();
            assertSame(editor, picked.get());
            editor.contact("전화 고객", "+82 10-1234-5678");
            assertEquals("01012345678", editor.phone.getText().toString());
            assertEquals("김포시 예시 주소", editor.address.getText().toString());
            text(editor.dialog.getWindow().getDecorView(), "내일").performClick();
            Calendar tomorrow = AdminPlanner.calendar(); tomorrow.add(Calendar.DAY_OF_MONTH, 1);
            assertEquals(AdminPlanner.iso(tomorrow), editor.date);
            editor.save.performClick(); assertNotNull(saved.get());
            assertEquals("create", saved.get().optString("action"));
            assertEquals("전화 고객", saved.get().optString("name"));
            assertEquals("시간 협의", saved.get().optString("timeSlot"));
            assertFalse(editor.dialog.isShowing());
        }
    }
    @Test public void editingAndRestoringDraftKeepsFieldsAndRequestId() throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity a = controller.get(); JSONObject existing = new JSONObject().put("id", "test-id")
                    .put("name", "고객").put("phone", "0212345678").put("address", "주소")
                    .put("date", "2026-10-08").put("timeSlot", "오후").put("pickupMethod", "비대면 수거");
            AdminPlanner.FormEditor editor = new AdminPlanner.FormEditor(a, "", existing, e -> {}, p -> null, (d, r) -> {});
            JSONObject draft = new JSONObject(editor.draft().toString()); editor.dialog.dismiss();
            AdminPlanner.FormEditor restored = new AdminPlanner.FormEditor(a, "", draft, e -> {}, p -> null, (d, r) -> {});
            assertEquals("edit", restored.action); assertEquals("test-id", restored.requestId);
            assertEquals("고객", restored.name.getText().toString());
            assertEquals("오후", restored.slot.getSelectedItem());
            assertEquals("비대면 수거", restored.method.getSelectedItem()); restored.dialog.dismiss();
        }
    }
    @Test public void calendarNavigationInMainActivityShowsOnlySelectedDayAndNote() throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity a = controller.get(); set(a, "calendarPage", true); set(a, "selectedDay", "2026-10-08");
            set(a, "requests", new JSONArray().put(new JSONObject().put("id", "1").put("name", "오늘 고객").put("date", "2026-10-08").put("status", "new"))
                    .put(new JSONObject().put("id", "2").put("name", "다른 날짜 고객").put("date", "2026-10-09").put("status", "done")));
            set(a, "dayMemos", new JSONArray().put(new JSONObject().put("date", "2026-10-08").put("content", "김포 먼저 방문")));
            var method = MainActivity.class.getDeclaredMethod("showInbox"); method.setAccessible(true); method.invoke(a);
            View root = a.getWindow().getDecorView();
            assertNotNull(text(root, "오늘 고객 님")); assertNull(text(root, "다른 날짜 고객 님"));
            assertNotNull(text(root, "김포 먼저 방문"));
        }
    }
    private static void set(MainActivity a, String name, Object value) throws Exception {
        var field = MainActivity.class.getDeclaredField(name); field.setAccessible(true); field.set(a, value);
    }
    static View text(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText())) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View match = text(((ViewGroup) root).getChildAt(i), text); if (match != null) return match;
        }
        return null;
    }
    static View description(View root, String text) {
        if (text.equals(root.getContentDescription())) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View match = description(((ViewGroup) root).getChildAt(i), text); if (match != null) return match;
        }
        return null;
    }
}
