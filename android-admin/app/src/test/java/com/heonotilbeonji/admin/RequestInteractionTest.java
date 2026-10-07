package com.heonotilbeonji.admin;

import android.app.Dialog;
import android.os.Looper;
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
import org.robolectric.shadows.ShadowDialog;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class RequestInteractionTest {
    @Test @Config(qualifiers = "w960dp-h1280dp-mdpi")
    public void tabletSummaryKeepsOtherCardsAndCalendarAttached() throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            call(activity, "showInbox");
            String day = AdminPlanner.iso(AdminPlanner.calendar());
            JSONArray items = new JSONArray();
            for (int i = 0; i < 2; i++) items.put(new JSONObject().put("id", "tablet-" + i)
                    .put("name", "고객" + i).put("date", day).put("status", "new")
                    .put("address", "경기도 김포시 전호로56번길 30").put("created_at", 2 - i));
            field("requests").set(activity, items);
            for (boolean calendar : new boolean[]{false, true}) {
                field("calendarPage").set(activity, calendar);
                call(activity, "renderRequests");
                LinearLayout list = (LinearLayout) field("list").get(activity);
                View[] original = new View[list.getChildCount()];
                for (int i = 0; i < original.length; i++) original[i] = list.getChildAt(i);
                ViewGroup first = (ViewGroup) PlannerInteractionTest.text(list, "고객0 님").getParent().getParent().getParent();
                ViewGroup second = (ViewGroup) PlannerInteractionTest.text(list, "고객1 님").getParent().getParent().getParent();
                for (int repeat = 0; repeat < 4; repeat++) {
                    first.getChildAt(0).performClick();
                    assertEquals(View.VISIBLE, first.getChildAt(1).getVisibility());
                    second.getChildAt(0).performClick();
                    assertEquals(View.GONE, first.getChildAt(1).getVisibility());
                    assertEquals(View.VISIBLE, second.getChildAt(1).getVisibility());
                    assertNotNull(PlannerInteractionTest.description(second, "요약 접기"));
                    PlannerInteractionTest.description(second, "요약 접기").performClick();
                    assertEquals(View.GONE, second.getChildAt(1).getVisibility());
                    assertEquals("", field("selectedRequestId").get(activity));
                    assertEquals(original.length, list.getChildCount());
                    for (int i = 0; i < original.length; i++) assertSame(original[i], list.getChildAt(i));
                    assertNull(list.getLayoutTransition());
                }
            }
        }
    }
    @Test public void allRequestIconsCanBeLoaded() {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            int[] icons = {R.drawable.ic_material_call, R.drawable.ic_material_check_circle,
                    R.drawable.ic_material_chevron_right, R.drawable.ic_material_close,
                    R.drawable.ic_material_delete, R.drawable.ic_material_expand_more,
                    R.drawable.ic_material_location_on, R.drawable.ic_material_note_add,
                    R.drawable.ic_material_person, R.drawable.ic_material_person_add,
                    R.drawable.ic_material_person_off, R.drawable.ic_material_schedule,
                    R.drawable.ic_material_settings, R.drawable.ic_material_contacts,
                    R.drawable.ic_material_history, R.drawable.ic_material_calendar_month,
                    R.drawable.ic_material_chevron_left, R.drawable.ic_material_edit};
            for (int icon : icons) assertNotNull(controller.get().getDrawable(icon));
        }
    }
    @Test public void expandAndOpenRequest() throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            call(activity, "showInbox");
            JSONObject item = new JSONObject().put("id", "test-1").put("name", "홍길동")
                    .put("phone", "01012345678").put("date", "2026-10-08")
                    .put("address", "경기도 김포시 전호로56번길 30, 1층 (우측)")
                    .put("pickupMethod", "비대면 수거").put("status", "new")
                    .put("timeSlot", "오후").put("amount", "20kg").put("created_at", 1L);
            field("requests").set(activity, new JSONArray().put(item));
            call(activity, "renderRequests");
            LinearLayout list = (LinearLayout) field("list").get(activity);
            list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.AT_MOST));
            list.layout(0, 0, 1080, list.getMeasuredHeight());
            ((ViewGroup) list.getChildAt(0)).getChildAt(0).performClick();
            shadowOf(Looper.getMainLooper()).idle();
            View details = text(list, "신청 상세 보기");
            assertNotNull(details);
            details.performClick();
            shadowOf(Looper.getMainLooper()).idle();
            Dialog dialog = ShadowDialog.getLatestDialog();
            assertTrue(dialog.isShowing());
            assertNotNull(text(dialog.getWindow().getDecorView(), "홍길동 님의 신청"));
            View decor = dialog.getWindow().getDecorView();
            assertNotNull(text(decor, "26년 10월 8일 (목)"));
            assertNotNull(text(decor, "관리자 기록"));
            View firstAction = PlannerInteractionTest.description(decor, "전화");
            assertNotNull(firstAction);
            ViewGroup actions = (ViewGroup) firstAction.getParent();
            assertEquals(6, actions.getChildCount());
            int width = Math.round(272 * activity.getResources().getDisplayMetrics().density);
            actions.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.AT_MOST));
            actions.layout(0, 0, width, actions.getMeasuredHeight());
            int measuredWidth = firstAction.getMeasuredWidth(), measuredHeight = firstAction.getMeasuredHeight();
            for (String title : new String[]{"전화", "연락처 추가", "메모 작성", "연락 완료", "처리 완료", "삭제"}) {
                View action = PlannerInteractionTest.description(decor, title);
                assertNotNull(title, action); assertSame(actions, action.getParent());
                assertEquals(title, measuredHeight, action.getMeasuredHeight());
                assertTrue(title, Math.abs(measuredWidth - action.getMeasuredWidth()) <= 2);
                assertEquals(title, 0, action.getLayoutParams().width);
                assertEquals(title, 1, ((ViewGroup) action).getChildCount());
                assertNull(text(action, title));
            }
            dialog.dismiss();
        }
    }
    private static Field field(String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static void call(MainActivity activity, String name) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(activity);
    }
    private static View text(View view, String value) {
        if (view instanceof TextView && value.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View match = text(group.getChildAt(i), value);
                if (match != null) return match;
            }
        }
        return null;
    }
}
