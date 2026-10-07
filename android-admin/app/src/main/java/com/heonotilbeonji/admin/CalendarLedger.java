package com.heonotilbeonji.admin;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.util.Locale;

/** Daily won amounts kept independently of request status and filters. */
final class CalendarLedger {
    private static final int NAVY = Color.rgb(24, 51, 78), BLUE = Color.rgb(52, 111, 174),
            CORAL = Color.rgb(233, 84, 72), MUTED = Color.rgb(100, 122, 140), GREEN = Color.rgb(28, 125, 87);
    static String won(long amount) { return NumberFormat.getIntegerInstance(Locale.KOREA).format(amount) + "원"; }
    static long[] totals(JSONArray records, String month) {
        long paid = 0, received = 0;
        for (int i = 0; i < records.length(); i++) {
            JSONObject row = records.optJSONObject(i);
            if (row != null && row.optString("date").startsWith(month + "-")) {
                paid += row.optLong("paidAmount"); received += row.optLong("receivedAmount");
            }
        }
        return new long[]{paid, received, received - paid};
    }
    static JSONObject day(JSONArray records, String date) {
        for (int i = 0; i < records.length(); i++) {
            JSONObject row = records.optJSONObject(i);
            if (row != null && date.equals(row.optString("date"))) return row;
        }
        return new JSONObject();
    }
    static long parseAmount(String text) {
        String value = text.trim();
        if (value.isEmpty()) return 0;
        if (!value.matches("[0-9]{1,12}")) throw new IllegalArgumentException("금액은 원 단위 숫자로 입력해 주세요.");
        return Long.parseLong(value);
    }
    static LinearLayout summary(Activity a, String title, long paid, long received) {
        LinearLayout panel = AdminPlanner.column(a);
        panel.setPadding(AdminPlanner.dp(a, 16), AdminPlanner.dp(a, 14), AdminPlanner.dp(a, 16), AdminPlanner.dp(a, 14));
        panel.setBackground(AdminPlanner.bg(a, Color.WHITE, 14));
        panel.addView(AdminPlanner.text(a, title, 16, NAVY, true));
        amountRow(a, panel, "준 금액", paid, CORAL);
        amountRow(a, panel, "받은 금액", received, BLUE);
        amountRow(a, panel, "차액", received - paid, received >= paid ? GREEN : CORAL);
        return panel;
    }
    static LinearLayout daySummary(Activity a, long paid, long received) {
        return summary(a, "하루 금액 · 메모", paid, received);
    }
    private static void amountRow(Activity a, LinearLayout panel, String title, long amount, int color) {
        LinearLayout row = new LinearLayout(a); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = AdminPlanner.text(a, title, 13, MUTED, false);
        label.setSingleLine(true);
        LinearLayout.LayoutParams caption = new LinearLayout.LayoutParams(-2, -2);
        caption.rightMargin = AdminPlanner.dp(a, 12);
        row.addView(label, caption);
        TextView value = AdminPlanner.text(a, won(amount), 17, color, true); value.setGravity(Gravity.END);
        row.addView(value, new LinearLayout.LayoutParams(0, -2, 1)); panel.addView(row, AdminPlanner.space(a, 8));
    }
    static final class Editor {
        final Dialog dialog;
        final EditText paid, received, note;
        final Button save;
        final String date;
        boolean saving;
        Editor(Activity a, String date, JSONObject record, String content, AdminPlanner.SaveRequest saver) {
            this.date = date;
            dialog = new Dialog(a); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout shell = AdminPlanner.column(a); shell.setBackground(AdminPlanner.bg(a, Color.rgb(244, 247, 249), 20));
            shell.setClipToOutline(true);
            LinearLayout header = new LinearLayout(a); header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(AdminPlanner.dp(a, 16), AdminPlanner.dp(a, 8), AdminPlanner.dp(a, 8), AdminPlanner.dp(a, 8));
            TextView title = AdminPlanner.text(a, "하루 기록", 21, NAVY, true);
            header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            View close = AdminPlanner.iconButton(a, R.drawable.ic_material_close, "기록 닫기");
            header.addView(close, new LinearLayout.LayoutParams(AdminPlanner.dp(a, 48), AdminPlanner.dp(a, 48)));
            close.setOnClickListener(v -> { if (!saving) dialog.dismiss(); }); shell.addView(header);
            ScrollView scroll = new ScrollView(a); LinearLayout body = AdminPlanner.column(a);
            body.setPadding(AdminPlanner.dp(a, 16), 0, AdminPlanner.dp(a, 16), AdminPlanner.dp(a, 16));
            scroll.addView(body); shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            body.addView(AdminPlanner.text(a, RequestSummary.date(date), 15, NAVY, true), AdminPlanner.space(a, 4));
            paid = amountField(a, body, "준 금액", record.optLong("paidAmount"));
            received = amountField(a, body, "받은 금액", record.optLong("receivedAmount"));
            TextView balance = AdminPlanner.text(a, "", 17, GREEN, true); body.addView(balance, AdminPlanner.space(a, 14));
            TextWatcher watcher = new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    try { long net = parseAmount(received.getText().toString()) - parseAmount(paid.getText().toString());
                        balance.setText("차액  " + won(net)); balance.setTextColor(net >= 0 ? GREEN : CORAL); }
                    catch (IllegalArgumentException ignored) { balance.setText("금액을 확인해 주세요"); }
                }
                public void afterTextChanged(Editable s) { }
            };
            paid.addTextChangedListener(watcher); received.addTextChangedListener(watcher); watcher.onTextChanged("", 0, 0, 0);
            body.addView(AdminPlanner.text(a, "하루 메모", 14, NAVY, true), AdminPlanner.space(a, 20));
            note = new EditText(a); note.setText(content); note.setHint("동선, 추가 비용, 기억할 일"); note.setTextSize(16);
            note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); note.setMinLines(3);
            note.setGravity(Gravity.TOP); note.setPadding(AdminPlanner.dp(a, 12), AdminPlanner.dp(a, 10), AdminPlanner.dp(a, 12), AdminPlanner.dp(a, 10));
            note.setBackground(AdminPlanner.bg(a, Color.WHITE, 12)); note.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});
            body.addView(note, AdminPlanner.space(a, 8));
            TextView error = AdminPlanner.text(a, "", 13, CORAL, false); body.addView(error, AdminPlanner.space(a, 8));
            LinearLayout footer = AdminPlanner.column(a); footer.setPadding(AdminPlanner.dp(a, 16), AdminPlanner.dp(a, 10), AdminPlanner.dp(a, 16), AdminPlanner.dp(a, 12));
            save = AdminPlanner.button(a, "하루 기록 저장", Color.WHITE, BLUE, R.drawable.ic_material_check_circle);
            LinearLayout progress = LoadingTasks.indicator(a, "저장 중…"); footer.addView(progress);
            footer.addView(save); shell.addView(footer);
            save.setOnClickListener(v -> {
                if (saving) return;
                try {
                    JSONObject data = draft();
                    data.put("paidAmount", parseAmount(paid.getText().toString())).put("receivedAmount", parseAmount(received.getText().toString()));
                    saving = true; progress.setVisibility(android.view.View.VISIBLE); save.setEnabled(false); save.setText("저장 중…"); dialog.setCancelable(false);
                    saver.save(data, result -> a.runOnUiThread(() -> {
                        saving = false; progress.setVisibility(android.view.View.GONE); save.setEnabled(true); save.setText("하루 기록 저장"); dialog.setCancelable(true);
                        if (result == null) dialog.dismiss(); else error.setText(result);
                    }));
                } catch (Exception e) { error.setText(e.getMessage()); }
            });
            dialog.setContentView(shell); dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.show(); AdminPlanner.size(a, dialog);
        }
        JSONObject draft() {
            JSONObject data = new JSONObject();
            try { data.put("date", date).put("paidText", paid.getText().toString())
                    .put("receivedText", received.getText().toString()).put("content", note.getText().toString().trim()); }
            catch (Exception ignored) { }
            return data;
        }
        private EditText amountField(Activity a, LinearLayout body, String title, long amount) {
            body.addView(AdminPlanner.text(a, title + " (원)", 14, NAVY, true), AdminPlanner.space(a, 18));
            EditText input = new EditText(a); input.setInputType(InputType.TYPE_CLASS_NUMBER); input.setSingleLine(true);
            input.setTextSize(22); input.setHint("0"); if (amount != 0) input.setText(String.valueOf(amount));
            input.setPadding(AdminPlanner.dp(a, 12), AdminPlanner.dp(a, 10), AdminPlanner.dp(a, 12), AdminPlanner.dp(a, 10));
            input.setBackground(AdminPlanner.bg(a, Color.WHITE, 12));
            input.setContentDescription(title + " 원 단위 입력"); body.addView(input, AdminPlanner.space(a, 6)); return input;
        }
    }
}
