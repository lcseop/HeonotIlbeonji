package com.heonotilbeonji.admin;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.function.IntConsumer;

/** Inline selection controls shared by the request inbox and calendar. */
final class RequestListControls {
    static final int NEW = 1, CONTACTED = 2, DONE = 4, ALL = 7;
    static final String[] REQUEST_SORTS = {"최신 신청순", "오래된 신청순", "희망 날짜순", "미완료 날짜순"};
    static final String[] MEMO_SORTS = {"최근 기록순", "오래된 기록순", "이름순", "희망 날짜순"};
    static boolean matches(JSONObject item, int mask) {
        String state = item.optString("status");
        int bit = "new".equals(state) ? NEW : "contacted".equals(state) ? CONTACTED : "done".equals(state) ? DONE : 0;
        return (mask & bit) != 0;
    }
    static int compare(JSONObject a, JSONObject b, int order) {
        if (order == 2 || order == 3) {
            String first = a.optString("date"), second = b.optString("date");
            if (first.isEmpty() != second.isEmpty()) return first.isEmpty() ? 1 : -1;
            int dates = first.compareTo(second); if (dates != 0) return dates;
        }
        int created = order == 1 ? Long.compare(a.optLong("created_at"), b.optLong("created_at")) :
                Long.compare(b.optLong("created_at"), a.optLong("created_at"));
        return created != 0 ? created : a.optString("id").compareTo(b.optString("id"));
    }
    static Spinner sort(Activity a, String[] options, int selected, IntConsumer change) {
        Spinner spinner = new Spinner(a, Spinner.MODE_DROPDOWN);
        spinner.setContentDescription("정렬 방식");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(a, android.R.layout.simple_spinner_item, options) {
            @Override public View getView(int position, View convert, ViewGroup parent) {
                TextView text = (TextView) super.getView(position, convert, parent);
                text.setTextSize(14); text.setTextColor(Color.rgb(24, 51, 78));
                text.setSingleLine(true); text.setEllipsize(android.text.TextUtils.TruncateAt.END);
                var arrow = a.getDrawable(R.drawable.ic_material_expand_more).mutate();
                arrow.setTint(Color.rgb(52, 111, 174));
                arrow.setBounds(0, 0, AdminPlanner.dp(a, 24), AdminPlanner.dp(a, 24));
                text.setCompoundDrawablesRelative(null, null, arrow, null);
                text.setCompoundDrawablePadding(AdminPlanner.dp(a, 8));
                return text;
            }
            @Override public View getDropDownView(int position, View convert, ViewGroup parent) {
                TextView text = (TextView) super.getDropDownView(position, convert, parent);
                if (options == REQUEST_SORTS && position == 3) text.setText("새 신청·연락 완료 · 희망 날짜순");
                text.setTextSize(15); text.setSingleLine(false); text.setMinHeight(AdminPlanner.dp(a, 48));
                text.setGravity(Gravity.CENTER_VERTICAL); return text;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter); spinner.setSelection(selected);
        spinner.setBackground(AdminPlanner.bg(a, Color.WHITE, 12));
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { change.accept(position); }
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        }); return spinner;
    }
    static final class Filters extends HorizontalScrollView {
        private final CheckBox[] chips = new CheckBox[3];
        private int mask;
        private boolean binding;
        Filters(Activity a, int initial, IntConsumer change) {
            super(a); mask = initial;
            setFillViewport(true); setHorizontalScrollBarEnabled(true);
            LinearLayout row = new LinearLayout(a); row.setOrientation(LinearLayout.HORIZONTAL);
            addView(row, new HorizontalScrollView.LayoutParams(-2, -2));
            String[] names = {"새 신청", "연락 완료", "처리 완료"};
            for (int i = 0; i < 3; i++) {
                final int bit = 1 << i; CheckBox chip = new CheckBox(a); chips[i] = chip;
                chip.setText(names[i]); chip.setTextSize(13); chip.setSingleLine(true);
                chip.setMinWidth(0); chip.setMinimumHeight(AdminPlanner.dp(a, 48));
                chip.setPadding(AdminPlanner.dp(a, 4), 0, AdminPlanner.dp(a, 6), 0);
                chip.setButtonTintList(ColorStateList.valueOf(Color.rgb(52, 111, 174)));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2, 1f);
                p.setMargins(0, 0, AdminPlanner.dp(a, 4), AdminPlanner.dp(a, 4)); row.addView(chip, p);
                chip.setOnCheckedChangeListener((button, checked) -> {
                    if (binding) return;
                    mask = checked ? mask | bit : mask & ~bit; style(a, chip); change.accept(mask);
                });
            }
            bind(a, initial);
        }
        void bind(Activity a, int value) {
            binding = true; mask = value;
            for (int i = 0; i < chips.length; i++) { chips[i].setChecked((mask & (1 << i)) != 0); style(a, chips[i]); }
            binding = false;
        }
        private void style(Activity a, CheckBox chip) {
            chip.setTextColor(chip.isChecked() ? Color.rgb(52, 111, 174) : Color.rgb(100, 122, 140));
            var background = AdminPlanner.bg(a, chip.isChecked() ? Color.rgb(231, 240, 249) : Color.WHITE, 10);
            background.setStroke(AdminPlanner.dp(a, 1), chip.isChecked() ? Color.rgb(165, 193, 221) : Color.rgb(220, 228, 236));
            chip.setBackground(background);
        }
    }
}
