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
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35}, qualifiers = "w320dp-h640dp")
public class WebsiteStatsTest {
    static JSONObject sample() throws Exception {
        return new JSONObject().put("startedAt", "2026-10-11")
                .put("totals", new JSONObject().put("views", 54).put("phoneClicks", 8).put("requests", 3))
                .put("days", new JSONArray().put(new JSONObject().put("date", "2026-10-11").put("views", 54).put("phoneClicks", 8).put("requests", 3)));
    }
    @Test public void websiteStatisticsHaveSeparateMonthlyTotalsAndDailyRowsAndPreserveFinances() throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity a = controller.get(); Calendar month = AdminPlanner.calendar(); month.set(2026, Calendar.OCTOBER, 1);
            set(a, "statsPage", true); set(a, "calendarMonth", month);
            set(a, "websiteStats", sample()); set(a, "websiteLoadedMonth", "2026-10");
            set(a, "financeLoadedMonth", "2026-10");
            var inbox = MainActivity.class.getDeclaredMethod("showInbox"); inbox.setAccessible(true); inbox.invoke(a);
            View decor = a.getWindow().getDecorView();
            assertNotNull(PlannerInteractionTest.text(decor, "54회"));
            assertNotNull(PlannerInteractionTest.text(decor, "8회"));
            assertNotNull(PlannerInteractionTest.text(decor, "3건"));
            assertNotNull(PlannerInteractionTest.text(decor, "10/11"));
            PlannerInteractionTest.text(decor, "금액").performClick();
            assertNotNull(PlannerInteractionTest.text(decor, "월 금액 합계"));
            assertNull(PlannerInteractionTest.text(decor, "54회"));
            PlannerInteractionTest.text(decor, "홈페이지").performClick();
            assertNotNull(PlannerInteractionTest.text(decor, "54회"));
            PlannerInteractionTest.description(decor, "통계 다음 달").performClick();
            assertNull(PlannerInteractionTest.text(decor, "54회"));
            assertNotNull(PlannerInteractionTest.text(decor, "홈페이지 통계를 불러오는 중…"));
        }
    }
    @Test public void failedLoadsDoNotShowZeroAsSuccessfulDataAndEmptyMonthHasExplanation() throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity a = controller.get();
            View failed = WebsiteStats.panel(a, new JSONObject(), false, "failed");
            assertNull(PlannerInteractionTest.text(failed, "0회"));
            assertNotNull(PlannerInteractionTest.text(failed, "통계를 불러오지 못했습니다. 새로고침해 주세요."));
            View empty = WebsiteStats.panel(a, new JSONObject().put("days", new JSONArray()), true, "");
            assertNotNull(PlannerInteractionTest.text(empty, "이 달에는 집계된 기간이 없습니다."));
        }
    }
    private static void set(MainActivity a, String name, Object value) throws Exception {
        var field = MainActivity.class.getDeclaredField(name); field.setAccessible(true); field.set(a, value);
    }
}
