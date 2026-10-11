package com.heonotilbeonji.admin;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.util.Locale;

/** Anonymous website activity, independent of advertising attribution and request filters. */
final class WebsiteStats {
    private static final int NAVY = Color.rgb(24, 51, 78), BLUE = Color.rgb(52, 111, 174),
            MUTED = Color.rgb(100, 122, 140), CORAL = Color.rgb(233, 84, 72), SURFACE = Color.rgb(244, 247, 249);
    static LinearLayout panel(Activity a, JSONObject data, boolean loaded, String error) {
        LinearLayout body = AdminPlanner.column(a);
        LinearLayout summary = card(a);
        summary.addView(AdminPlanner.text(a, "홈페이지 · 월 합계", 17, NAVY, true));
        if (!loaded) {
            summary.addView(AdminPlanner.text(a, error.isEmpty() ? "홈페이지 통계를 불러오는 중…" : "통계를 불러오지 못했습니다. 새로고침해 주세요.",
                    14, MUTED, false), AdminPlanner.space(a, 12));
            body.addView(summary); return body;
        }
        JSONObject totals = data.optJSONObject("totals");
        if (totals == null) totals = new JSONObject();
        metric(a, summary, "홈페이지 조회", totals.optLong("views"), R.drawable.ic_material_history, BLUE);
        metric(a, summary, "전화 버튼 클릭", totals.optLong("phoneClicks"), R.drawable.ic_material_call, CORAL);
        metric(a, summary, "온라인 신청 완료", totals.optLong("requests"), R.drawable.ic_material_inbox, NAVY);
        if (!error.isEmpty()) summary.addView(AdminPlanner.text(a, "새로고침 실패 · 이전 통계입니다", 12, CORAL, false), AdminPlanner.space(a, 10));
        body.addView(summary);
        String started = data.optString("startedAt");
        TextView note = AdminPlanner.text(a, "전체 접속 경로 포함 · 한국 시간 기준\n조회는 재방문·새로고침 포함, 전화는 버튼 클릭 기준\n" +
                (started.isEmpty() ? "" : RequestSummary.date(started) + "부터 집계 · 이전 기록 없음"), 12, MUTED, false);
        note.setLineSpacing(AdminPlanner.dp(a, 3), 1); body.addView(note, AdminPlanner.space(a, 12));
        LinearLayout daily = card(a);
        daily.addView(AdminPlanner.text(a, "날짜별 기록", 16, NAVY, true));
        JSONArray days = data.optJSONArray("days");
        if (days == null || days.length() == 0) {
            daily.addView(AdminPlanner.text(a, "이 달에는 집계된 기간이 없습니다.", 14, MUTED, false), AdminPlanner.space(a, 12));
        } else {
            HorizontalScrollView scroll = new HorizontalScrollView(a); scroll.setFillViewport(true);
            LinearLayout table = AdminPlanner.column(a);
            int[] widths = columnWidths(a, days);
            table.setMinimumWidth(widths[0] + widths[1] + widths[2] + widths[3] + AdminPlanner.dp(a, 16));
            scroll.addView(table); daily.addView(scroll, AdminPlanner.space(a, 12));
            table.addView(row(a, "날짜", "조회", "전화", "신청", true, false, widths));
            String today = AdminPlanner.iso(AdminPlanner.calendar());
            for (int i = 0; i < days.length(); i++) {
                JSONObject day = days.optJSONObject(i); if (day == null) continue;
                String date = day.optString("date");
                table.addView(row(a, date.length() == 10 ? date.substring(5).replace('-', '/') : date,
                        number(day.optLong("views")), number(day.optLong("phoneClicks")), number(day.optLong("requests")),
                        false, today.equals(date), widths));
            }
        }
        body.addView(daily, AdminPlanner.space(a, 14)); return body;
    }
    private static String number(long value) { return NumberFormat.getIntegerInstance(Locale.KOREA).format(value); }
    private static LinearLayout card(Activity a) {
        LinearLayout panel = AdminPlanner.column(a);
        panel.setPadding(AdminPlanner.dp(a, 16), AdminPlanner.dp(a, 14), AdminPlanner.dp(a, 16), AdminPlanner.dp(a, 14));
        panel.setBackground(AdminPlanner.bg(a, Color.WHITE, 14)); return panel;
    }
    private static void metric(Activity a, LinearLayout body, String title, long count, int resource, int color) {
        LinearLayout row = new LinearLayout(a); row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView image = new ImageView(a); var drawable = a.getDrawable(resource).mutate(); drawable.setTint(color); image.setImageDrawable(drawable);
        LinearLayout.LayoutParams icon = new LinearLayout.LayoutParams(AdminPlanner.dp(a, 24), AdminPlanner.dp(a, 24)); icon.rightMargin = AdminPlanner.dp(a, 8);
        row.addView(image, icon);
        TextView label = AdminPlanner.text(a, title, 14, MUTED, false);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = AdminPlanner.text(a, number(count) + (resource == R.drawable.ic_material_inbox ? "건" : "회"), 20, color, true); value.setSingleLine(true);
        row.addView(value, new LinearLayout.LayoutParams(-2, -2)); body.addView(row, AdminPlanner.space(a, 14));
    }
    private static int[] columnWidths(Activity a, JSONArray days) {
        int[] widths = {AdminPlanner.dp(a, 65), AdminPlanner.dp(a, 60), AdminPlanner.dp(a, 60), AdminPlanner.dp(a, 60)};
        TextView probe = AdminPlanner.text(a, "", 14, NAVY, true);
        String[] titles = {"날짜", "조회", "전화", "신청"};
        for (int n = -1; n < days.length(); n++) {
            JSONObject day = n < 0 ? null : days.optJSONObject(n);
            String[] values = day == null ? titles : new String[]{"00/00", number(day.optLong("views")),
                    number(day.optLong("phoneClicks")), number(day.optLong("requests"))};
            for (int i = 0; i < 4; i++) widths[i] = Math.max(widths[i], (int)Math.ceil(probe.getPaint().measureText(values[i])) + AdminPlanner.dp(a, 12));
        }
        return widths;
    }
    private static LinearLayout row(Activity a, String date, String views, String phone, String requests, boolean header, boolean today, int[] widths) {
        LinearLayout row = new LinearLayout(a); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AdminPlanner.dp(a, 8), AdminPlanner.dp(a, 12), AdminPlanner.dp(a, 8), AdminPlanner.dp(a, 12));
        if (header || today) row.setBackground(AdminPlanner.bg(a, SURFACE, 8));
        String[] values = {date, views, phone, requests};
        for (int i = 0; i < values.length; i++) {
            TextView cell = AdminPlanner.text(a, values[i], 14, today ? BLUE : header ? MUTED : NAVY, header || today);
            cell.setSingleLine(true); cell.setGravity(i == 0 ? Gravity.START : Gravity.END);
            cell.setPadding(i == 0 ? 0 : AdminPlanner.dp(a, 6), 0, 0, 0);
            row.addView(cell, new LinearLayout.LayoutParams(0, -2, widths[i]));
        }
        return row;
    }
}
