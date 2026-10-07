package com.heonotilbeonji.admin;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/** Native admin-only entry and monthly scheduling views. */
final class AdminPlanner {
    private static final int NAVY = Color.rgb(24, 51, 78), BLUE = Color.rgb(52, 111, 174),
            MUTED = Color.rgb(100, 122, 140), SURFACE = Color.rgb(244, 247, 249), CORAL = Color.rgb(233, 84, 72);
    static Calendar calendar() { return Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"), Locale.KOREA); }
    static String iso(Calendar day) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Seoul")); return format.format(day.getTime());
    }
    static int dp(Activity a, int value) { return Math.round(value * a.getResources().getDisplayMetrics().density); }
    static GradientDrawable bg(Activity a, int color, int radius) {
        GradientDrawable bg = new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(dp(a, radius)); return bg;
    }
    static TextView text(Activity a, String value, int size, int color, boolean bold) {
        TextView text = new TextView(a); text.setText(value); text.setTextSize(size); text.setTextColor(color);
        if (bold) text.setTypeface(null, Typeface.BOLD); return text;
    }
    static LinearLayout column(Activity a) { LinearLayout view = new LinearLayout(a); view.setOrientation(LinearLayout.VERTICAL); return view; }
    static LinearLayout.LayoutParams space(Activity a, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(a, top); return p;
    }
    static Button button(Activity a, String title, int color, int fill, int drawable) {
        Button b = new Button(a); b.setText(title); b.setTextSize(13); b.setAllCaps(false);
        b.setTextColor(color); b.setPadding(dp(a, 10), dp(a, 6), dp(a, 10), dp(a, 6));
        b.setBackground(bg(a, fill, 12)); b.setMinimumHeight(dp(a, 48)); b.setMinWidth(0);
        b.setSingleLine(true); b.setEllipsize(TextUtils.TruncateAt.END);
        if (drawable != 0) {
            var icon = a.getDrawable(drawable).mutate(); icon.setTint(color);
            icon.setBounds(0, 0, dp(a, 22), dp(a, 22));
            b.setCompoundDrawables(icon, null, null, null); b.setCompoundDrawablePadding(dp(a, 6));
        }
        return b;
    }
    static ImageView iconButton(Activity a, int resource, String description) {
        ImageView v = new ImageView(a); var icon = a.getDrawable(resource).mutate(); icon.setTint(NAVY);
        v.setImageDrawable(icon); v.setPadding(dp(a, 12), dp(a, 12), dp(a, 12), dp(a, 12));
        v.setBackground(bg(a, SURFACE, 24)); v.setContentDescription(description); v.setFocusable(true); return v;
    }

    static View month(Activity a, Calendar month, String selected, JSONArray requests, int filter,
                      JSONArray notes, Consumer<String> select, Consumer<Integer> move) {
        return month(a, month, selected, requests, filter, notes, new JSONArray(), select, move);
    }
    static View month(Activity a, Calendar month, String selected, JSONArray requests, int filter,
                      JSONArray notes, JSONArray finances, Consumer<String> select, Consumer<Integer> move) {
        LinearLayout panel = column(a); panel.setPadding(dp(a, 10), dp(a, 8), dp(a, 10), dp(a, 10));
        panel.setBackground(bg(a, Color.WHITE, 16));
        LinearLayout header = new LinearLayout(a); header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView previous = iconButton(a, R.drawable.ic_material_chevron_left, "이전 달");
        header.addView(previous, new LinearLayout.LayoutParams(dp(a, 48), dp(a, 48)));
        TextView title = text(a, String.format(Locale.KOREA, "%02d년 %d월", month.get(Calendar.YEAR) % 100, month.get(Calendar.MONTH) + 1), 19, NAVY, true);
        title.setGravity(Gravity.CENTER); header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView next = iconButton(a, R.drawable.ic_material_chevron_right, "다음 달");
        header.addView(next, new LinearLayout.LayoutParams(dp(a, 48), dp(a, 48)));
        previous.setOnClickListener(v -> move.accept(-1)); next.setOnClickListener(v -> move.accept(1));
        panel.addView(header);
        LinearLayout weekdays = new LinearLayout(a);
        String[] names = {"일", "월", "화", "수", "목", "금", "토"};
        for (int i = 0; i < 7; i++) {
            TextView label = text(a, names[i], 12, i == 0 ? CORAL : i == 6 ? BLUE : MUTED, false);
            label.setGravity(Gravity.CENTER); weekdays.addView(label, new LinearLayout.LayoutParams(0, dp(a, 30), 1));
        }
        panel.addView(weekdays);
        Calendar day = (Calendar) month.clone(); day.set(Calendar.DAY_OF_MONTH, 1);
        int offset = day.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        int days = day.getActualMaximum(Calendar.DAY_OF_MONTH);
        String today = iso(calendar());
        for (int week = 0; week < (offset + days + 6) / 7; week++) {
            LinearLayout row = new LinearLayout(a);
            for (int i = 0; i < 7; i++) {
                int number = week * 7 + i - offset + 1;
                LinearLayout cell = column(a); cell.setGravity(Gravity.CENTER); cell.setPadding(0, dp(a, 4), 0, dp(a, 4));
                float scale = a.getResources().getConfiguration().fontScale;
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(a, Math.max(68, (int) (42 * scale + 16))), 1);
                p.setMargins(dp(a, 1), dp(a, 1), dp(a, 1), dp(a, 1)); row.addView(cell, p);
                if (number < 1 || number > days) continue;
                day.set(Calendar.DAY_OF_MONTH, number); String date = iso(day);
                int count = 0; boolean hasMemo = false;
                for (int n = 0; n < requests.length(); n++) {
                    JSONObject item = requests.optJSONObject(n);
                    if (item != null && date.equals(item.optString("date")) && matchesFilter(item, filter)) count++;
                }
                for (int n = 0; n < notes.length(); n++) {
                    JSONObject memo = notes.optJSONObject(n);
                    if (memo != null && date.equals(memo.optString("date"))) hasMemo = true;
                }
                JSONObject money = CalendarLedger.day(finances, date);
                boolean hasMoney = money.optLong("paidAmount") != 0 || money.optLong("receivedAmount") != 0;
                boolean active = date.equals(selected);
                GradientDrawable background = bg(a, active ? NAVY : date.equals(today) ? SURFACE : Color.WHITE, 10);
                if (date.equals(today)) background.setStroke(dp(a, 1), BLUE);
                cell.setBackground(background);
                TextView dayNumber = text(a, String.valueOf(number), 15, active ? Color.WHITE : i == 0 ? CORAL : NAVY, true);
                dayNumber.setGravity(Gravity.CENTER); dayNumber.setIncludeFontPadding(false);
                cell.addView(dayNumber, new LinearLayout.LayoutParams(-1, -2));
                TextView badge = text(a, count > 0 ? count + "건" : hasMoney ? "금액" : hasMemo ? "메모" : "", 10, active ? Color.WHITE : BLUE, true);
                badge.setGravity(Gravity.CENTER); badge.setIncludeFontPadding(false); badge.setSingleLine(true);
                badge.setMinHeight(dp(a, 20));
                LinearLayout.LayoutParams badgeSpace = new LinearLayout.LayoutParams(-1, -2);
                badgeSpace.topMargin = dp(a, 5); cell.addView(badge, badgeSpace);
                cell.setContentDescription(RequestSummary.date(date) + ", 신청 " + count + "건" + (hasMemo ? ", 메모 있음" : "") + (hasMoney ? ", 금액 기록 있음" : ""));
                cell.setFocusable(true); cell.setOnClickListener(v -> select.accept(date));
            }
            panel.addView(row);
        }
        return panel;
    }
    static boolean matchesFilter(JSONObject item, int filter) {
        return RequestListControls.matches(item, filter);
    }

    interface SaveRequest { void save(JSONObject data, Consumer<String> result); }
    interface SaveNote { void save(String date, String content, Consumer<String> result); }
    static final class FormEditor {
        final Activity activity;
        final Dialog dialog;
        final EditText name, phone, address, message;
        final Spinner amount, slot, method;
        final Button dateButton, save;
        final Function<String, JSONObject> previous;
        String date, requestId = UUID.randomUUID().toString();
        String action = "create";
        boolean saving;

        FormEditor(Activity a, String initialDate, JSONObject existing, Consumer<FormEditor> pickContact,
                   Function<String, JSONObject> previous, SaveRequest saver) {
            activity = a; this.previous = previous; date = initialDate;
            dialog = new Dialog(a); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout shell = column(a); shell.setBackground(bg(a, SURFACE, 20)); shell.setClipToOutline(true);
            LinearLayout header = new LinearLayout(a); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(a, 20), dp(a, 8), dp(a, 8), dp(a, 8));
            boolean editing = existing != null && (!existing.optString("id").isEmpty() || "edit".equals(existing.optString("action")));
            header.addView(text(a, editing ? "신청서 수정" : "신청서 직접 작성", 21, NAVY, true), new LinearLayout.LayoutParams(0, -2, 1));
            ImageView close = iconButton(a, R.drawable.ic_material_close, "작성 취소");
            header.addView(close, new LinearLayout.LayoutParams(dp(a, 48), dp(a, 48))); shell.addView(header);
            close.setOnClickListener(v -> { if (!saving) dialog.dismiss(); });
            ScrollView scroll = new ScrollView(a); LinearLayout body = column(a); body.setPadding(dp(a, 16), 0, dp(a, 16), dp(a, 16));
            scroll.addView(body); shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            Button contacts = button(a, "연락처에서 가져오기", BLUE, Color.WHITE, R.drawable.ic_material_contacts);
            body.addView(contacts, space(a, 4)); contacts.setOnClickListener(v -> pickContact.accept(this));
            name = field(body, "이름 *", "고객 이름", false, InputType.TYPE_CLASS_TEXT);
            phone = field(body, "전화번호 *", "010-0000-0000", false, InputType.TYPE_CLASS_PHONE);
            Button reuse = button(a, "이전 주소 불러오기", NAVY, Color.WHITE, R.drawable.ic_material_history);
            body.addView(reuse, space(a, 8)); reuse.setOnClickListener(v -> fillPrevious(true));
            address = field(body, "수거 주소", "상담 중이면 나중에 확인해도 됩니다", true, InputType.TYPE_CLASS_TEXT);
            body.addView(text(a, "희망 날짜", 13, NAVY, true), space(a, 16));
            dateButton = button(a, "", NAVY, Color.WHITE, R.drawable.ic_material_calendar_month);
            body.addView(dateButton, space(a, 6)); updateDate();
            dateButton.setOnClickListener(v -> {
                Calendar c = calendar();
                if (!date.isEmpty()) { String[] parts = date.split("-"); c.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2])); }
                DatePickerDialog pickerDialog = new DatePickerDialog(a, (picker, year, month, day) -> {
                    Calendar chosen = calendar(); chosen.set(year, month, day); date = iso(chosen); updateDate();
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
                Calendar first = calendar(), last = calendar(); first.set(2000, Calendar.JANUARY, 1); last.set(2100, Calendar.DECEMBER, 31);
                pickerDialog.getDatePicker().setMinDate(first.getTimeInMillis()); pickerDialog.getDatePicker().setMaxDate(last.getTimeInMillis());
                pickerDialog.show();
            });
            LinearLayout quick = new LinearLayout(a); body.addView(quick, space(a, 6));
            String[] dates = {"오늘", "내일", "날짜 협의"};
            for (int i = 0; i < dates.length; i++) {
                final int choice = i; Button b = button(a, dates[i], NAVY, Color.WHITE, 0);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(a, 48), 1); if (i > 0) p.leftMargin = dp(a, 6);
                quick.addView(b, p); b.setOnClickListener(v -> { Calendar c = calendar(); c.add(Calendar.DAY_OF_MONTH, choice); date = choice == 2 ? "" : iso(c); updateDate(); });
            }
            slot = options(body, "희망 시간대", new String[]{"시간 협의", "오전", "오후"});
            method = options(body, "수거 방식", new String[]{"방식 협의", "대면 수거", "비대면 수거"});
            amount = options(body, "예상 수거량", new String[]{"수거량 확인 필요", "20~30kg", "30kg 이상", "기타 품목 상담"});
            message = field(body, "상담 내용", "품목, 고객 요청 등을 적어 주세요", true, InputType.TYPE_CLASS_TEXT);
            if (existing != null) {
                action = editing ? "edit" : "create";
                requestId = existing.optString("requestId", existing.optString("id", requestId));
                name.setText(existing.optString("name")); phone.setText(existing.optString("phone"));
                address.setText(existing.optString("address")); message.setText(existing.optString("message"));
                date = existing.optString("date"); updateDate();
                select(slot, existing.optString("timeSlot")); select(method, existing.optString("pickupMethod"));
                select(amount, existing.optString("amount"));
            }
            TextView error = text(a, "", 13, CORAL, false); body.addView(error, space(a, 8));
            LinearLayout footer = column(a); footer.setPadding(dp(a, 16), dp(a, 10), dp(a, 16), dp(a, 12));
            save = button(a, "신청서 저장", Color.WHITE, CORAL, R.drawable.ic_material_check_circle);
            LinearLayout progress = LoadingTasks.indicator(a, "저장 중…"); footer.addView(progress);
            footer.addView(save); shell.addView(footer);
            save.setOnClickListener(v -> {
                if (saving) return;
                String n = name.getText().toString().trim(), p = normalizePhone(phone.getText().toString());
                if (n.isEmpty()) { name.setError("이름을 입력해 주세요"); name.requestFocus(); return; }
                if (!p.matches("0\\d{8,10}")) { phone.setError("전화번호를 확인해 주세요"); phone.requestFocus(); return; }
                try {
                    JSONObject data = new JSONObject().put("action", action).put("requestId", requestId)
                            .put("name", n).put("phone", p).put("address", address.getText().toString().trim())
                            .put("date", date).put("timeSlot", slot.getSelectedItem()).put("pickupMethod", method.getSelectedItem())
                            .put("amount", amount.getSelectedItem()).put("message", message.getText().toString().trim());
                    saving = true; progress.setVisibility(android.view.View.VISIBLE); save.setEnabled(false); save.setText("저장 중…"); dialog.setCancelable(false);
                    saver.save(data, result -> { saving = false; progress.setVisibility(android.view.View.GONE); dialog.setCancelable(true); save.setEnabled(true); save.setText("신청서 저장");
                        if (result == null) dialog.dismiss(); else error.setText(result); });
                } catch (Exception e) { error.setText("입력 내용을 확인해 주세요."); }
            });
            dialog.setContentView(shell); dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.show(); size(a, dialog);
        }
        EditText field(LinearLayout body, String title, String hint, boolean multiline, int input) {
            body.addView(text(activity, title, 13, NAVY, true), space(activity, 14));
            EditText field = new EditText(activity); field.setTextSize(16); field.setHint(hint);
            field.setInputType(input | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
            field.setSingleLine(!multiline); if (multiline) { field.setMinLines(2); field.setMaxLines(4); field.setGravity(Gravity.TOP); }
            field.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
            field.setBackground(bg(activity, Color.WHITE, 12));
            field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(title.startsWith("이름") ? 40 : title.startsWith("전화") ? 30 : title.startsWith("수거") ? 150 : 350)});
            body.addView(field, space(activity, 6)); return field;
        }
        Spinner options(LinearLayout body, String title, String[] choices) {
            body.addView(text(activity, title, 13, NAVY, true), space(activity, 14));
            Spinner picker = new Spinner(activity);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, choices);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); picker.setAdapter(adapter);
            picker.setBackgroundColor(Color.TRANSPARENT); picker.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
            picker.setContentDescription(title);
            LinearLayout row = new LinearLayout(activity); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(bg(activity, Color.WHITE, 12));
            row.addView(picker, new LinearLayout.LayoutParams(0, -1, 1));
            ImageView arrow = iconButton(activity, R.drawable.ic_material_expand_more, title + " 선택");
            arrow.setBackgroundColor(Color.TRANSPARENT); arrow.setOnClickListener(v -> picker.performClick());
            row.addView(arrow, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(activity, 50)); p.topMargin = dp(activity, 6);
            body.addView(row, p); return picker;
        }
        void updateDate() { dateButton.setText(RequestSummary.date(date)); }
        JSONObject draft() {
            JSONObject data = new JSONObject();
            try { data.put("action", action).put("requestId", requestId).put("name", name.getText())
                    .put("phone", phone.getText()).put("address", address.getText()).put("message", message.getText())
                    .put("date", date).put("timeSlot", slot.getSelectedItem()).put("pickupMethod", method.getSelectedItem())
                    .put("amount", amount.getSelectedItem()); } catch (Exception ignored) { }
            return data;
        }
        void select(Spinner spinner, String value) {
            for (int i = 0; i < spinner.getCount(); i++) if (value.equals(spinner.getItemAtPosition(i))) spinner.setSelection(i);
        }
        void contact(String customer, String number) { name.setText(customer == null ? "" : customer); phone.setText(normalizePhone(number == null ? "" : number)); fillPrevious(false); }
        void fillPrevious(boolean showMissing) {
            JSONObject item = previous.apply(normalizePhone(phone.getText().toString()));
            if (item != null) {
                if (name.getText().toString().trim().isEmpty()) name.setText(item.optString("name"));
                if (address.getText().toString().trim().isEmpty()) address.setText(item.optString("address"));
                android.widget.Toast.makeText(activity, "이전 신청의 주소를 가져왔습니다.", android.widget.Toast.LENGTH_SHORT).show();
            } else if (showMissing) android.widget.Toast.makeText(activity, "같은 번호의 이전 신청이 없습니다.", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
    static String normalizePhone(String number) { return number.replaceAll("[\\s()-]", "").replaceFirst("^\\+82", "0"); }
    static void size(Activity a, Dialog dialog) {
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setLayout(a.getResources().getDisplayMetrics().widthPixels - dp(a, 24),
                (int) (a.getResources().getDisplayMetrics().heightPixels * .88f));
    }
    static void editNote(Activity a, String date, String original, SaveNote saver) {
        LinearLayout body = column(a); body.setPadding(dp(a, 20), dp(a, 8), dp(a, 20), dp(a, 8));
        body.addView(text(a, "방문 동선, 휴무, 재연락할 고객 등을 기록하세요.\n내용을 비우고 저장하면 메모가 지워집니다.", 13, MUTED, false));
        EditText input = new EditText(a); input.setText(original); input.setHint("이 날짜에 기억할 내용"); input.setTextSize(16);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); input.setMinLines(4);
        input.setGravity(Gravity.TOP); input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});
        body.addView(input, space(a, 10)); TextView error = text(a, "", 13, CORAL, false); body.addView(error);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(a).setTitle(RequestSummary.date(date) + " 메모")
                .setView(body).setNegativeButton("취소", null).setPositiveButton("저장", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            dialog.getButton(-1).setEnabled(false); dialog.setCancelable(false);
            saver.save(date, input.getText().toString().trim(), result -> {
                dialog.setCancelable(true); dialog.getButton(-1).setEnabled(true);
                if (result == null) dialog.dismiss(); else error.setText(result);
            });
        })); dialog.show();
    }
}
