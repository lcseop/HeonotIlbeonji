package com.heonotilbeonji.admin;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import org.json.JSONObject;
import java.util.Locale;

/** Admin additions remain separate from the customer's submitted fields. */
final class RequestNotes {
    static String calendarTime(JSONObject item) {
        String time = item.optString("reservedTime");
        return time.isEmpty() ? item.optString("timeSlot", "시간 협의") : time;
    }

    static void edit(Activity a, JSONObject item, AdminPlanner.SaveRequest saver) {
        LinearLayout body = AdminPlanner.column(a);
        body.setPadding(AdminPlanner.dp(a, 20), AdminPlanner.dp(a, 8), AdminPlanner.dp(a, 20), AdminPlanner.dp(a, 16));
        body.addView(AdminPlanner.text(a, item.optString("name") + " 님 · " + RequestSummary.date(item.optString("date")), 15, Color.rgb(24, 51, 78), true));
        body.addView(AdminPlanner.text(a, "관리자 예약 시간", 13, Color.rgb(100, 122, 140), false), AdminPlanner.space(a, 18));
        String[] selected = {item.optString("reservedTime")};
        LinearLayout row = new LinearLayout(a); row.setGravity(Gravity.CENTER_VERTICAL);
        Button time = AdminPlanner.button(a, selected[0].isEmpty() ? "시간 선택" : selected[0], Color.rgb(24, 51, 78), Color.rgb(234, 242, 250), R.drawable.ic_material_schedule);
        time.setContentDescription("예약 시간 선택"); row.addView(time, new LinearLayout.LayoutParams(0, -2, 1));
        Button clear = AdminPlanner.button(a, "해제", Color.rgb(100, 122, 140), Color.TRANSPARENT, 0);
        row.addView(clear, new LinearLayout.LayoutParams(AdminPlanner.dp(a, 64), -2)); body.addView(row, AdminPlanner.space(a, 8));
        time.setOnClickListener(v -> {
            String[] parts = selected[0].isEmpty() ? new String[]{"9", "0"} : selected[0].split(":");
            TimePickerDialog picker = new TimePickerDialog(a, (view, hour, minute) -> {
                selected[0] = String.format(Locale.KOREA, "%02d:%02d", hour, minute); time.setText(selected[0]);
            }, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), true);
            picker.setTitle("예약 시간"); picker.show();
        });
        clear.setOnClickListener(v -> { selected[0] = ""; time.setText("시간 선택"); });
        body.addView(AdminPlanner.text(a, "신청서 메모", 13, Color.rgb(100, 122, 140), false), AdminPlanner.space(a, 18));
        EditText note = new EditText(a); note.setText(item.optString("adminNote")); note.setHint("방문 안내, 수거할 물건, 추가 요청 등을 남겨주세요");
        note.setTextSize(16); note.setGravity(Gravity.TOP); note.setMinLines(5);
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        note.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});
        body.addView(note, AdminPlanner.space(a, 8));
        ScrollView scroll = new ScrollView(a); scroll.addView(body);
        AlertDialog dialog = new AlertDialog.Builder(a).setTitle("예약 시간 · 신청서 메모").setView(scroll)
                .setNegativeButton("취소", null).setPositiveButton("저장", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                JSONObject data = new JSONObject().put("action", "adminDetails").put("id", item.optString("id"))
                        .put("reservedTime", selected[0]).put("adminNote", note.getText().toString().trim());
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                saver.save(data, error -> a.runOnUiThread(() -> {
                    if (error == null) dialog.dismiss();
                    else { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); Toast.makeText(a, error, Toast.LENGTH_LONG).show(); }
                }));
            } catch (Exception error) { Toast.makeText(a, "저장 내용을 확인해 주세요.", Toast.LENGTH_LONG).show(); }
        }));
        dialog.show();
    }
}
