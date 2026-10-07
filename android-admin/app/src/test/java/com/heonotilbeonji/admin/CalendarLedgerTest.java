package com.heonotilbeonji.admin;

import android.view.View;
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
public class CalendarLedgerTest {
    @Test public void totalsKeepMonthBoundariesAndUseLongAmounts() throws Exception {
        JSONArray records = new JSONArray().put(new JSONObject().put("date", "2026-10-01").put("paidAmount", 3000000000L).put("receivedAmount", 4000000000L))
                .put(new JSONObject().put("date", "2026-10-31").put("paidAmount", 50000).put("receivedAmount", 0))
                .put(new JSONObject().put("date", "2026-11-01").put("paidAmount", 99999).put("receivedAmount", 99999));
        assertArrayEquals(new long[]{3000050000L, 4000000000L, 999950000L}, CalendarLedger.totals(records, "2026-10"));
        assertArrayEquals(new long[]{0, 0, 0}, CalendarLedger.totals(records, "2026-09"));
        assertEquals("3,000,000,000원", CalendarLedger.won(3000000000L));
        assertEquals("-50,000원", CalendarLedger.won(-50000));
        assertEquals(0, CalendarLedger.parseAmount(""));
        assertEquals(999999999999L, CalendarLedger.parseAmount("999999999999"));
        for (String invalid : new String[]{"-1", "1.2", "abc", "1000000000000"}) {
            try { CalendarLedger.parseAmount(invalid); fail(invalid); } catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void dailyEditorSavesAmountsAndMemoTogetherAndKeepsFailedDraft() throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            AtomicReference<JSONObject> saved = new AtomicReference<>();
            AtomicReference<java.util.function.Consumer<String>> completion = new AtomicReference<>();
            CalendarLedger.Editor editor = new CalendarLedger.Editor(controller.get(), "2026-10-08", new JSONObject(), "기존 동선",
                    (data, result) -> { saved.set(data); completion.set(result); });
            editor.paid.setText("12000"); editor.received.setText("23000"); editor.note.setText("판매 후 귀가");
            assertNotNull(PlannerInteractionTest.text(editor.dialog.getWindow().getDecorView(), "차액  11,000원"));
            editor.save.performClick(); editor.save.performClick();
            assertEquals(12000, saved.get().optLong("paidAmount")); assertEquals(23000, saved.get().optLong("receivedAmount"));
            assertEquals("2026-10-08", saved.get().optString("date")); assertEquals("판매 후 귀가", saved.get().optString("content"));
            assertFalse(editor.save.isEnabled());
            completion.get().accept("연결 실패");
            assertTrue(editor.dialog.isShowing()); assertTrue(editor.save.isEnabled());
            assertEquals("12000", editor.paid.getText().toString());
            assertNotNull(PlannerInteractionTest.text(editor.dialog.getWindow().getDecorView(), "연결 실패"));
            editor.paid.setText("1000000000000");
            assertEquals("1000000000000", editor.paid.getText().toString());
            JSONObject prior = saved.get(); editor.save.performClick(); assertSame(prior, saved.get());
            editor.paid.setText(""); editor.received.setText(""); editor.note.setText(""); editor.save.performClick();
            assertEquals(0, saved.get().optLong("paidAmount")); assertEquals(0, saved.get().optLong("receivedAmount"));
            completion.get().accept(null); assertFalse(editor.dialog.isShowing());
        }
    }
    @Test public void calendarMarksMoneyOnlyDays() throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            Calendar month = AdminPlanner.calendar(); month.set(2026, Calendar.OCTOBER, 1);
            JSONArray records = new JSONArray().put(new JSONObject().put("date", "2026-10-08").put("paidAmount", 12000));
            View panel = AdminPlanner.month(controller.get(), month, "2026-10-01", new JSONArray(), 0, new JSONArray(), records, d -> {}, n -> {});
            assertNotNull(PlannerInteractionTest.text(panel, "금액"));
            assertNotNull(PlannerInteractionTest.description(panel, "26년 10월 8일 (목), 신청 0건, 금액 기록 있음"));
        }
    }
    @Test public void filteredCalendarKeepsFullMonthTotalsAndBottomNavigation() throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            Calendar month = AdminPlanner.calendar(); month.set(2026, Calendar.OCTOBER, 1);
            set(activity, "calendarPage", true); set(activity, "calendarMonth", month); set(activity, "selectedDay", "2026-10-08");
            set(activity, "financeLoadedMonth", "2026-10"); set(activity, "requestFilter", RequestListControls.DONE);
            set(activity, "dayFinances", new JSONArray().put(new JSONObject().put("date", "2026-10-08").put("paidAmount", 1000).put("receivedAmount", 3000))
                    .put(new JSONObject().put("date", "2026-10-09").put("paidAmount", 2000).put("receivedAmount", 4000)));
            var inbox = MainActivity.class.getDeclaredMethod("showInbox"); inbox.setAccessible(true); inbox.invoke(activity);
            View decor = activity.getWindow().getDecorView();
            assertNull(PlannerInteractionTest.text(decor, "월 금액 합계"));
            assertNull(PlannerInteractionTest.text(decor, "7,000원"));
            assertNotNull(PlannerInteractionTest.text(decor, "금액 · 메모 수정"));
            View nav = PlannerInteractionTest.description(decor, "화면 이동 메뉴"); assertNotNull(nav);
            assertNotNull(PlannerInteractionTest.description(nav, "신청서, 이동"));
            assertNotNull(PlannerInteractionTest.description(nav, "달력, 선택됨"));
            assertNotNull(PlannerInteractionTest.description(nav, "메모, 이동"));
            assertNotNull(PlannerInteractionTest.description(nav, "통계, 이동"));
            PlannerInteractionTest.text(decor, "메뉴 접기").performClick();
            assertEquals(View.VISIBLE, nav.getVisibility());
        }
    }
    @Test public void statisticsHasSeparateTotalsAndChangesMonthWithoutShowingStaleTotals() throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get(); Calendar month = AdminPlanner.calendar(); month.set(2026, Calendar.OCTOBER, 1);
            set(activity, "statsPage", true); set(activity, "calendarMonth", month); set(activity, "financeLoadedMonth", "2026-10");
            set(activity, "dayFinances", new JSONArray().put(new JSONObject().put("date", "2026-10-08").put("paidAmount", 1000).put("receivedAmount", 3000))
                    .put(new JSONObject().put("date", "2026-10-09").put("paidAmount", 2000).put("receivedAmount", 4000)));
            var inbox = MainActivity.class.getDeclaredMethod("showInbox"); inbox.setAccessible(true); inbox.invoke(activity);
            View decor = activity.getWindow().getDecorView();
            assertNotNull(PlannerInteractionTest.text(decor, "월 금액 합계"));
            assertNotNull(PlannerInteractionTest.text(decor, "3,000원"));
            assertNotNull(PlannerInteractionTest.text(decor, "7,000원"));
            assertNotNull(PlannerInteractionTest.text(decor, "4,000원"));
            View nav = PlannerInteractionTest.description(decor, "화면 이동 메뉴");
            assertNotNull(PlannerInteractionTest.description(nav, "통계, 선택됨"));
            assertNull(PlannerInteractionTest.text(decor, "하루 금액 · 메모"));
            PlannerInteractionTest.description(decor, "통계 다음 달").performClick();
            assertNotNull(PlannerInteractionTest.text(decor, "26년 11월"));
            assertNull(PlannerInteractionTest.text(decor, "7,000원"));
            assertNotNull(PlannerInteractionTest.text(decor, "월 금액을 불러오는 중…"));
        }
    }
    @Test public void moneyLabelsStayOnOneLineWithLargeFonts() {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            var config = new android.content.res.Configuration(activity.getResources().getConfiguration());
            config.fontScale = 1.8f; activity.getResources().updateConfiguration(config, activity.getResources().getDisplayMetrics());
            View summary = CalendarLedger.summary(activity, "월 금액 합계", 120000, 230000);
            int width = AdminPlanner.dp(activity, 288);
            summary.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST));
            summary.layout(0, 0, width, summary.getMeasuredHeight());
            for (String label : new String[]{"준 금액", "받은 금액", "차액"}) {
                android.widget.TextView text = (android.widget.TextView) PlannerInteractionTest.text(summary, label);
                assertEquals(1, text.getLineCount()); assertEquals(0, text.getLayout().getEllipsisCount(0));
                assertTrue(text.getWidth() >= text.getPaint().measureText(label));
            }
        }
    }
    @Test public void calendarShowsRequestCardsBeforeDailyAmounts() throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get(); set(activity, "calendarPage", true); set(activity, "selectedDay", "2026-10-08");
            set(activity, "requests", new JSONArray().put(new JSONObject().put("id", "order-test").put("name", "순서 고객")
                    .put("date", "2026-10-08").put("status", "new")));
            var inbox = MainActivity.class.getDeclaredMethod("showInbox"); inbox.setAccessible(true); inbox.invoke(activity);
            var field = MainActivity.class.getDeclaredField("list"); field.setAccessible(true);
            android.widget.LinearLayout list = (android.widget.LinearLayout) field.get(activity);
            View request = (View) PlannerInteractionTest.text(list, "순서 고객 님").getParent().getParent().getParent();
            View daily = (View) PlannerInteractionTest.text(list, "하루 금액 · 메모").getParent();
            assertTrue(list.indexOfChild(request) < list.indexOfChild(daily));
            assertNull(PlannerInteractionTest.text(list, "월 금액 합계"));
        }
    }
    private static void set(MainActivity activity, String name, Object value) throws Exception {
        var field = MainActivity.class.getDeclaredField(name); field.setAccessible(true); field.set(activity, value);
    }
}
