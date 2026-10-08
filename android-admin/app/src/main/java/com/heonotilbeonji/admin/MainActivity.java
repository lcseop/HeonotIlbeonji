package com.heonotilbeonji.admin;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public final class MainActivity extends Activity {
    private static final int NAVY = Color.rgb(24, 51, 78);
    private static final int CORAL = Color.rgb(233, 84, 72);
    private static final int MUTED = Color.rgb(100, 122, 140);
    private final LoadingTasks io = new LoadingTasks(this, this::busyChanged);
    private final LoadingTasks writes = new LoadingTasks(this, this::busyChanged);
    private int busyCount;
    private LinearLayout loadingIndicator;
    private boolean refreshInFlight;
    private long dataRevision;
    private LinearLayout root;
    private LinearLayout list;
    private TextView notice;
    private TextView count;
    private JSONArray requests = new JSONArray();
    private JSONArray memos = new JSONArray();
    private boolean memoPage = false;
    private boolean calendarPage = false;
    private boolean statsPage = false;
    private Calendar calendarMonth = AdminPlanner.calendar();
    private String selectedDay = AdminPlanner.iso(AdminPlanner.calendar());
    private JSONArray dayMemos = new JSONArray();
    private JSONArray dayFinances = new JSONArray();
    private String financeLoadedMonth = "", financeError = "";
    private CalendarLedger.Editor ledgerEditor;
    private AdminPlanner.FormEditor contactEditor;
    private AdminPlanner.FormEditor requestEditor;
    private int requestFilter = RequestListControls.ALL;
    private int requestSort = 0;
    private int memoSort = 0;
    private Spinner sortControl;
    private RequestListControls.Filters filterControls;
    private String memoSearch = "";
    private String selectedRequestId = "";
    private Runnable collapseRequestSummary;
    private boolean menuCollapsed;
    private AppUpdater updater;

    private static final int GREEN = Color.rgb(28, 125, 87);
    private static final int BLUE = Color.rgb(52, 111, 174);
    private static final int SURFACE = Color.rgb(244, 247, 249);

    private int statusColor(String status) {
        return "done".equals(status) ? GREEN : "contacted".equals(status) ? BLUE : CORAL;
    }

    private int statusIcon(String status) {
        return "done".equals(status) ? R.drawable.ic_material_check_circle :
                "contacted".equals(status) ? R.drawable.ic_material_call : R.drawable.ic_material_schedule;
    }

    private String statusLabel(String status) {
        return "done".equals(status) ? "처리 완료" : "contacted".equals(status) ? "연락 완료" : "처리 안됨";
    }

    private Drawable iconDrawable(int resource, int color) {
        Drawable drawable = getDrawable(resource).mutate();
        drawable.setTint(color);
        return drawable;
    }

    private ImageView icon(int resource, int color, String description) {
        ImageView image = new ImageView(this);
        image.setImageDrawable(iconDrawable(resource, color));
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        if (description != null) {
            image.setContentDescription(description);
            if (Build.VERSION.SDK_INT >= 26) image.setTooltipText(description);
            image.setOnLongClickListener(view -> {
                Toast.makeText(this, description, Toast.LENGTH_SHORT).show(); return true;
            });
        } else image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return image;
    }

    private Drawable touchBackground(int color, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(24, 24, 51, 78)),
                background(color, radius), background(Color.WHITE, radius));
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        updater = new AppUpdater(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NAVY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = Build.VERSION.SDK_INT >= 30 ? insets.getInsets(WindowInsets.Type.statusBars()).top : insets.getSystemWindowInsetTop();
            int bottom = Build.VERSION.SDK_INT >= 30 ? insets.getInsets(WindowInsets.Type.navigationBars()).bottom : insets.getSystemWindowInsetBottom();
            view.setPadding(0, top, 0, bottom);
            return insets;
        });
        setContentView(root);
        android.content.SharedPreferences preferences = getSharedPreferences("admin", MODE_PRIVATE);
        requestFilter = preferences.getInt("ui_filter", RequestListControls.ALL) & RequestListControls.ALL;
        requestSort = Math.max(0, Math.min(3, preferences.getInt("ui_request_sort", 0)));
        memoSort = Math.max(0, Math.min(3, preferences.getInt("ui_memo_sort", 0)));
        menuCollapsed = preferences.getBoolean("ui_menu_collapsed", false);
        if (state != null) {
            calendarPage = state.getBoolean("calendarPage"); memoPage = state.getBoolean("memoPage");
            statsPage = state.getBoolean("statsPage");
            requestFilter = state.getInt("requestFilterMask", RequestListControls.ALL);
            requestSort = state.getInt("requestSort", 0); memoSort = state.getInt("memoSort", 0);
            selectedDay = state.getString("selectedDay", selectedDay);
            calendarMonth.setTimeInMillis(state.getLong("calendarMonth", calendarMonth.getTimeInMillis()));
            menuCollapsed = state.getBoolean("menuCollapsed");
        }
        render();
        if (state != null && state.containsKey("requestDraft") && !session().isEmpty()) {
            try { editRequest(new JSONObject(state.getString("requestDraft"))); contactEditor = requestEditor; }
            catch (Exception ignored) { }
        }
        if (state != null && state.containsKey("ledgerDraft") && !session().isEmpty()) {
            try { JSONObject draft = new JSONObject(state.getString("ledgerDraft"));
                openDayRecord(draft.optString("date"), new JSONObject(), draft.optString("content"));
                ledgerEditor.paid.setText(draft.optString("paidText")); ledgerEditor.received.setText(draft.optString("receivedText")); }
            catch (Exception ignored) { }
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("calendarPage", calendarPage); state.putBoolean("memoPage", memoPage);
        state.putBoolean("statsPage", statsPage);
        state.putInt("requestFilterMask", requestFilter); state.putInt("requestSort", requestSort); state.putInt("memoSort", memoSort);
        state.putString("selectedDay", selectedDay); state.putLong("calendarMonth", calendarMonth.getTimeInMillis());
        if (requestEditor != null && requestEditor.dialog.isShowing()) state.putString("requestDraft", requestEditor.draft().toString());
        if (ledgerEditor != null && ledgerEditor.dialog.isShowing()) state.putString("ledgerDraft", ledgerEditor.draft().toString());
        state.putBoolean("menuCollapsed", menuCollapsed);
    }

    @Override public void onResume() {
        super.onResume();
        if (updater != null) updater.resume(!session().isEmpty());
        if (list != null && !session().isEmpty()) refresh();
    }

    @Override public void onDestroy() {
        if (requestEditor != null && requestEditor.dialog.isShowing()) requestEditor.dialog.dismiss();
        if (ledgerEditor != null && ledgerEditor.dialog.isShowing()) ledgerEditor.dialog.dismiss();
        super.onDestroy();
        if (updater != null) updater.close();
        io.shutdownNow(); writes.shutdownNow();
    }

    private String session() { return getSharedPreferences("admin", MODE_PRIVATE).getString("session", ""); }
    private void saveSession(String token) { getSharedPreferences("admin", MODE_PRIVATE).edit().putString("session", token).apply(); }
    private String serverUrl() { return getSharedPreferences("admin", MODE_PRIVATE).getString("api_base_url", BuildConfig.API_BASE_URL); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private Button button(String value, int color) {
        Button view = new Button(this);
        view.setText(value);
        view.setTextSize(13);
        view.setTextColor(Color.WHITE);
        view.setAllCaps(false);
        view.setBackground(background(color, 12));
        view.setPadding(dp(12), 0, dp(12), 0);
        return view;
    }

    private LinearLayout.LayoutParams margins(int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(top);
        p.bottomMargin = dp(bottom);
        return p;
    }

    private void render() {
        root.removeAllViews();
        list = null;
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), 0, dp(8), 0);
        header.setBackgroundColor(NAVY);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.heonot_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setPadding(dp(5), dp(5), dp(5), dp(5));
        logo.setBackground(background(Color.WHITE, 10));
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(logo, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView brand = label("헌옷일번지", 17, Color.WHITE, true);
        brand.setSingleLine(true);
        brand.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(0, -2, 1);
        brandParams.leftMargin = dp(10);
        header.addView(brand, brandParams);
        if (!session().isEmpty()) {
            ImageView settings = icon(R.drawable.ic_material_settings, Color.WHITE, "설정");
            settings.setPadding(dp(12), dp(12), dp(12), dp(12));
            settings.setBackground(touchBackground(NAVY, 24));
            settings.setFocusable(true);
            header.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
            settings.setOnClickListener(view -> showSettings());
        }
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(55)));
        if (session().isEmpty()) showLogin(); else showInbox();
    }

    private void showLogin() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), dp(36), dp(24), dp(24));
        panel.setBackgroundColor(Color.rgb(244, 247, 249));
        root.addView(panel, new LinearLayout.LayoutParams(-1, -1));
        panel.addView(label("PICKUP INBOX", 11, CORAL, true));
        panel.addView(label("수거 신청함", 30, NAVY, true), margins(12, 6));
        panel.addView(label("관리자만 신청 내용을 확인할 수 있습니다.", 14, MUTED, false));
        EditText password = new EditText(this);
        password.setHint("관리자 비밀번호");
        password.setSingleLine(true);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setPadding(dp(16), dp(12), dp(16), dp(12));
        password.setBackground(background(Color.WHITE, 12));
        panel.addView(password, margins(35, 14));
        Button login = button("신청함 열기  →", CORAL);
        panel.addView(login, new LinearLayout.LayoutParams(-1, dp(52)));
        loadingIndicator = LoadingTasks.indicator(this, "연결 중…");
        panel.addView(loadingIndicator, margins(8, 0)); busyChanged(0);
        notice = label("", 13, MUTED, false);
        panel.addView(notice, margins(16, 0));
        Button server = button("서버 주소 설정", NAVY);
        panel.addView(server, margins(20, 0));
        server.setOnClickListener(view -> editServerUrl());
        login.setOnClickListener(view -> {
            String entered = password.getText().toString();
            if (entered.isEmpty()) { notice.setText("비밀번호를 입력해 주세요."); return; }
            login.setEnabled(false);
            notice.setText("확인하고 있습니다…");
            writes.execute(() -> {
                try {
                    JSONObject input = new JSONObject().put("password", entered);
                    String token = ApiClient.request(this, "POST", "/api/admin/native-login", input, null).getString("token");
                    runOnUiThread(() -> { saveSession(token); password.setText(""); render(); registerDevice(); updater.resume(true); });
                } catch (Exception error) {
                    runOnUiThread(() -> { login.setEnabled(true); notice.setText(error.getMessage()); });
                }
            });
        });
    }

    private void showInbox() {
        LinearLayout menuHeader = new LinearLayout(this);
        menuHeader.setGravity(Gravity.CENTER_VERTICAL);
        menuHeader.setPadding(dp(16), dp(4), dp(12), dp(4));
        menuHeader.setBackgroundColor(SURFACE);
        root.addView(menuHeader, new LinearLayout.LayoutParams(-1, -2));
        count = label(statsPage ? "통계" : memoPage ? "신청서 메모" : calendarPage ? "수거 달력" : "수거 신청함", 20, NAVY, true);
        count.setSingleLine(true);
        count.setEllipsize(TextUtils.TruncateAt.END);
        menuHeader.addView(count, new LinearLayout.LayoutParams(0, -2, 1));
        Button menuToggle = AdminPlanner.button(this, menuCollapsed ? "메뉴 펼치기" : "메뉴 접기", BLUE, Color.WHITE,
                menuCollapsed ? R.drawable.ic_material_expand_more : R.drawable.ic_material_expand_less);
        menuToggle.setTextSize(13);
        menuToggle.setSingleLine(true);
        menuToggle.setVisibility(statsPage ? View.GONE : View.VISIBLE);
        menuHeader.addView(menuToggle, new LinearLayout.LayoutParams(-2, dp(48)));
        if (statsPage) {
            ImageView reload = AdminPlanner.iconButton(this, R.drawable.ic_material_refresh, "통계 새로고침");
            reload.setOnClickListener(v -> refresh()); menuHeader.addView(reload, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(dp(16), dp(10), dp(16), dp(8));
        actions.setBackgroundColor(Color.rgb(244, 247, 249));
        root.addView(actions);
        actions.setVisibility(statsPage || menuCollapsed ? View.GONE : View.VISIBLE);
        menuToggle.setOnClickListener(view -> {
            menuCollapsed = !menuCollapsed;
            saveViewPreferences();
            if (menuCollapsed) {
                View focused = actions.findFocus();
                if (focused != null) {
                    ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                            .hideSoftInputFromWindow(focused.getWindowToken(), 0);
                    focused.clearFocus();
                }
            }
            actions.setVisibility(menuCollapsed ? View.GONE : View.VISIBLE);
            menuToggle.setText(menuCollapsed ? "메뉴 펼치기" : "메뉴 접기");
            Drawable menuIcon = iconDrawable(menuCollapsed ?
                    R.drawable.ic_material_expand_more : R.drawable.ic_material_expand_less, BLUE);
            menuIcon.setBounds(0, 0, dp(22), dp(22));
            menuToggle.setCompoundDrawables(menuIcon, null, null, null);
        });
        if (!memoPage && !statsPage) {
            Button add = AdminPlanner.button(this, "신청서 직접 작성", Color.WHITE, CORAL, R.drawable.ic_material_note_add);
            actions.addView(add, margins(8, 0)); add.setOnClickListener(view -> newRequest());
        }
        LinearLayout controls = new LinearLayout(this);
        actions.addView(controls, margins(7, 0));
        controls.setGravity(Gravity.CENTER_VERTICAL);
        TextView sortLabel = label("정렬", 13, MUTED, true);
        sortLabel.setSingleLine(true);
        LinearLayout.LayoutParams labelSpace = new LinearLayout.LayoutParams(-2, -2); labelSpace.rightMargin = dp(10);
        controls.addView(sortLabel, labelSpace);
        sortControl = RequestListControls.sort(this, memoPage ? RequestListControls.MEMO_SORTS : RequestListControls.REQUEST_SORTS,
                memoPage ? memoSort : requestSort, this::changeSort);
        controls.addView(sortControl, new LinearLayout.LayoutParams(0, dp(48), 1));
        ImageView refresh = AdminPlanner.iconButton(this, R.drawable.ic_material_refresh, "새로고침");
        LinearLayout.LayoutParams refreshGap = new LinearLayout.LayoutParams(dp(48), dp(48)); refreshGap.leftMargin = dp(6);
        controls.addView(refresh, refreshGap);
        filterControls = null;
        if (!memoPage && !statsPage) {
            filterControls = new RequestListControls.Filters(this, requestFilter, mask -> {
                requestFilter = mask;
                if (requestSort == 3) { requestSort = 2; sortControl.setSelection(2); }
                saveViewPreferences();
                renderRequests();
            });
            actions.addView(filterControls, margins(8, 0));
        }
        if (memoPage) {
            Button addMemo = AdminPlanner.button(this, "메모 추가", Color.WHITE, CORAL, R.drawable.ic_material_note_add);
            actions.addView(addMemo, margins(8, 0)); addMemo.setOnClickListener(v -> chooseRequestNote());
            EditText search = new EditText(this);
            search.setSingleLine(true);
            search.setTextSize(14);
            search.setHint("이름 · 전화번호 · 메모 내용 검색");
            search.setText(memoSearch);
            search.setPadding(dp(14), dp(9), dp(14), dp(9));
            search.setBackground(background(Color.WHITE, 12));
            actions.addView(search, margins(7, 0));
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    memoSearch = s.toString(); renderMemos();
                }
                @Override public void afterTextChanged(Editable s) { }
            });
        }
        notice = label("", 13, MUTED, false);
        actions.addView(notice, margins(7, 0));
        loadingIndicator = LoadingTasks.indicator(this, "처리 중…");
        root.addView(loadingIndicator, margins(4, 4)); busyChanged(0);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(244, 247, 249));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), 0, dp(16), dp(22));
        scroll.addView(list);
        addNavigation();
        refresh.setOnClickListener(view -> refresh());
        if (statsPage) renderStats(); else if (memoPage) renderMemos(); else renderRequests();
        refresh();
    }

    private void addNavigation() {
        LinearLayout navigation = new LinearLayout(this);
        navigation.setPadding(dp(8), dp(5), dp(8), dp(5)); navigation.setBackgroundColor(Color.WHITE);
        navigation.setContentDescription("화면 이동 메뉴");
        String[] titles = {"신청서", "달력", "메모", "통계"};
        int[] icons = {R.drawable.ic_material_inbox, R.drawable.ic_material_calendar_month, R.drawable.ic_material_notes, R.drawable.ic_material_bar_chart};
        int active = statsPage ? 3 : memoPage ? 2 : calendarPage ? 1 : 0;
        for (int i = 0; i < titles.length; i++) {
            final int page = i;
            LinearLayout tab = new LinearLayout(this); tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER); tab.setBackground(touchBackground(i == active ? Color.rgb(231, 240, 249) : Color.WHITE, 12));
            tab.setFocusable(true); tab.setContentDescription(titles[i] + (i == active ? ", 선택됨" : ", 이동"));
            tab.setSelected(i == active);
            tab.addView(icon(icons[i], i == active ? BLUE : MUTED, null), new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView tabTitle = label(titles[i], 13, i == active ? BLUE : MUTED, i == active);
            tabTitle.setGravity(Gravity.CENTER); tab.addView(tabTitle, margins(4, 0));
            navigation.addView(tab, new LinearLayout.LayoutParams(0, dp(62), 1));
            tab.setOnClickListener(v -> {
                if (page == active) {
                    if (list != null) ((ScrollView) list.getParent()).smoothScrollTo(0, 0);
                    return;
                }
                memoPage = page == 2; calendarPage = page == 1; statsPage = page == 3; selectedRequestId = ""; render();
            });
        }
        root.addView(navigation, new LinearLayout.LayoutParams(-1, -2));
    }

    private String financeMonth() { return AdminPlanner.iso(calendarMonth).substring(0, 7); }

    private void renderStats() {
        if (list == null || !statsPage) return;
        list.removeAllViews(); count.setText("통계");
        LinearLayout period = new LinearLayout(this); period.setGravity(Gravity.CENTER_VERTICAL);
        period.setPadding(dp(8), dp(8), dp(8), dp(8)); period.setBackground(background(Color.WHITE, 14));
        ImageView previous = AdminPlanner.iconButton(this, R.drawable.ic_material_chevron_left, "통계 이전 달");
        ImageView next = AdminPlanner.iconButton(this, R.drawable.ic_material_chevron_right, "통계 다음 달");
        period.addView(previous, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView month = label(String.format(Locale.KOREA, "%02d년 %d월", calendarMonth.get(Calendar.YEAR) % 100,
                calendarMonth.get(Calendar.MONTH) + 1), 19, NAVY, true);
        month.setGravity(Gravity.CENTER); month.setSingleLine(true);
        period.addView(month, new LinearLayout.LayoutParams(0, -2, 1));
        period.addView(next, new LinearLayout.LayoutParams(dp(48), dp(48)));
        previous.setOnClickListener(v -> moveStatsMonth(-1)); next.setOnClickListener(v -> moveStatsMonth(1));
        list.addView(period, margins(6, 0));
        if (!financeLoadedMonth.equals(financeMonth())) {
            TextView loading = label(financeError.isEmpty() ? "월 금액을 불러오는 중…" : "금액을 불러오지 못했습니다. 새로고침을 눌러 주세요.", 14, MUTED, false);
            loading.setPadding(dp(16), dp(20), dp(16), dp(20)); list.addView(loading, margins(10, 0)); return;
        }
        long[] totals = CalendarLedger.totals(dayFinances, financeMonth());
        LinearLayout summary = CalendarLedger.summary(this, "월 금액 합계", totals[0], totals[1]);
        summary.addView(label("차액 = 받은 금액 − 준 금액", 12, MUTED, false), margins(12, 0));
        if (!financeError.isEmpty()) summary.addView(label("새로고침 실패 · 이전에 불러온 금액입니다", 12, CORAL, false), margins(8, 0));
        list.addView(summary, margins(12, 0));
        int days = 0;
        for (int i = 0; i < dayFinances.length(); i++) {
            JSONObject record = dayFinances.optJSONObject(i);
            if (record != null && record.optString("date").startsWith(financeMonth() + "-")) days++;
        }
        TextView recorded = label("금액 기록 " + days + "일 · 신청서 필터와 관계없이 달 전체 합계", 13, MUTED, false);
        recorded.setPadding(dp(4), dp(4), dp(4), dp(4)); list.addView(recorded, margins(10, 0));
    }

    private void moveStatsMonth(int offset) {
        calendarMonth.set(Calendar.DAY_OF_MONTH, 1); calendarMonth.add(Calendar.MONTH, offset);
        selectedDay = AdminPlanner.iso(calendarMonth); financeError = ""; renderStats(); refresh();
    }

    private void showSettings() {
        String[] choices = {"알림 설정", "서버 주소 설정", "업데이트 확인", "로그아웃"};
        new AlertDialog.Builder(this).setTitle("관리자 설정").setItems(choices, (dialog, which) -> {
            if (which == 0) requestAlerts();
            else if (which == 1) editServerUrl();
            else if (which == 2) updater.check(true);
            else {
                unregisterDevice();
                saveSession("");
                render();
            }
        }).setNegativeButton("닫기", null).show();
    }

    private void refresh() {
        if (list == null || session().isEmpty() || refreshInFlight) return;
        refreshInFlight = true;
        final long revision = dataRevision;
        final boolean loadMemos = memoPage, loadCalendar = calendarPage;
        final boolean loadStats = statsPage;
        final String month = financeMonth();
        notice.setText(statsPage ? "금액을 불러오는 중…" : memoPage ? "메모를 불러오는 중…" : "신청 내용을 불러오는 중…");
        io.execute(() -> {
            try {
                if (loadStats) {
                    JSONArray finances = ApiClient.request(this, "GET", "/api/admin/native-day-finances?month=" + month, null, session()).getJSONArray("records");
                    runOnUiThread(() -> {
                        if (revision != dataRevision) return;
                        if (!month.equals(financeMonth())) return;
                        dayFinances = finances; financeLoadedMonth = month; financeError = "";
                        if (statsPage) renderStats(); else if (calendarPage) renderRequests();
                    });
                } else if (loadMemos) {
                    JSONArray loaded = ApiClient.request(this, "GET", "/api/admin/native-memos", null, session()).getJSONArray("memos");
                    JSONArray loadedRequests = ApiClient.request(this, "GET", "/api/admin/native-requests", null, session()).getJSONArray("requests");
                    runOnUiThread(() -> {
                        if (revision != dataRevision) return; memos = loaded; requests = loadedRequests; if (memoPage) renderMemos(); });
                } else {
                    JSONArray loaded = ApiClient.request(this, "GET", "/api/admin/native-requests", null, session()).getJSONArray("requests");
                    JSONArray notes = loadCalendar ? ApiClient.request(this, "GET", "/api/admin/native-day-memos", null, session()).getJSONArray("memos") : null;
                    JSONArray finances = null; String loadError = "";
                    if (loadCalendar) {
                        try { finances = ApiClient.request(this, "GET", "/api/admin/native-day-finances?month=" + month, null, session()).getJSONArray("records"); }
                        catch (Exception e) { loadError = e.getMessage() == null ? "금액 기록 연결 실패" : e.getMessage(); }
                    }
                    final JSONArray loadedFinances = finances; final String financialError = loadError;
                    runOnUiThread(() -> {
                        if (revision != dataRevision) return; requests = loaded; if (notes != null) dayMemos = notes;
                        if (loadCalendar && month.equals(financeMonth())) {
                            financeError = financialError;
                            if (loadedFinances != null) { dayFinances = loadedFinances; financeLoadedMonth = month; }
                        }
                        if (statsPage && loadCalendar && month.equals(financeMonth())) renderStats();
                        else if (!statsPage && !memoPage && calendarPage == loadCalendar) renderRequests(); });
                }
            } catch (Exception error) {
                runOnUiThread(() -> {
                        if (revision != dataRevision) return;
                    if (error instanceof ApiClient.ApiException && ((ApiClient.ApiException) error).status == 401) {
                        saveSession(""); render();
                    } else {
                        if ((loadCalendar || loadStats) && (calendarPage || statsPage) && month.equals(financeMonth())) {
                            financeError = error.getMessage() == null ? "금액 기록 연결 실패" : error.getMessage();
                            if (statsPage) renderStats(); else renderRequests();
                        }
                        if (notice != null) notice.setText(error.getMessage());
                    }
                });
            } finally { runOnUiThread(() -> {
                refreshInFlight = false;
                if (revision != dataRevision || loadMemos != memoPage || loadCalendar != calendarPage || loadStats != statsPage || !month.equals(financeMonth())) refresh();
            }); }
        });
    }

    private void busyChanged(int change) {
        busyCount = Math.max(0, busyCount + change);
        if (loadingIndicator != null) loadingIndicator.setVisibility(busyCount > 0 ? View.VISIBLE : View.GONE);
    }

    private void applySavedRequest(JSONObject data) {
        dataRevision++; requests = RequestCache.apply(requests, data);
        if (memoPage) renderMemos(); else if (!statsPage) renderRequests();
    }

    private String displayDate(long epoch) {
        SimpleDateFormat format = new SimpleDateFormat("yy년 M월 d일 a h:mm", Locale.KOREA);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        return format.format(new Date(epoch));
    }

    private void renderRequests() {
        if (list == null) return;
        collapseRequestSummary = null;
        list.removeAllViews();
        int unread = 0;
        for (int i = 0; i < requests.length(); i++) {
            JSONObject item = requests.optJSONObject(i);
            if (item != null && "new".equals(item.optString("status"))) unread++;
        }
        count.setText(calendarPage ? "수거 달력" : "신청서 · 새 " + unread + "건");
        ArrayList<JSONObject> visible = new ArrayList<>();
        for (int i = 0; i < requests.length(); i++) {
            JSONObject item = requests.optJSONObject(i);
            if (item == null) continue;
            if (calendarPage && !selectedDay.equals(item.optString("date"))) continue;
            if (!RequestListControls.matches(item, requestFilter)) continue;
            visible.add(item);
        }
        Collections.sort(visible, (a, b) -> RequestListControls.compare(a, b, requestSort));
        notice.setText(calendarPage ? "날짜를 눌러 일정·금액·메모를 확인하세요" : "표시 " + visible.size() + "건 · 누르면 요약, 상세 보기로 전체 확인");
        if (calendarPage) {
            list.addView(AdminPlanner.month(this, calendarMonth, selectedDay, requests, requestFilter, dayMemos, dayFinances,
                    this::selectCalendarDay,
                    offset -> { calendarMonth.set(Calendar.DAY_OF_MONTH, 1); calendarMonth.add(Calendar.MONTH, offset);
                        selectedDay = AdminPlanner.iso(calendarMonth); selectedRequestId = ""; financeError = ""; renderRequests(); refresh(); }), margins(6, 0));
            LinearLayout dayHeader = new LinearLayout(this); dayHeader.setGravity(Gravity.CENTER_VERTICAL);
            dayHeader.setTag("selected-day-header");
            TextView dayTitle = label(RequestSummary.date(selectedDay) + " · " + visible.size() + "건", 16, NAVY, true);
            dayHeader.addView(dayTitle, new LinearLayout.LayoutParams(0, -2, 1));
            ImageView today = AdminPlanner.iconButton(this, R.drawable.ic_material_today, "오늘로 이동");
            today.setOnClickListener(v -> { calendarMonth = AdminPlanner.calendar(); selectedDay = AdminPlanner.iso(calendarMonth); financeError = ""; renderRequests(); refresh(); });
            dayHeader.addView(today, new LinearLayout.LayoutParams(dp(48), dp(48)));
            list.addView(dayHeader, margins(12, 4));
        }
        if (visible.isEmpty()) emptyCard(requestFilter == 0 ? "표시할 상태를 체크해 주세요." : "해당하는 신청서가 없습니다.");
        for (JSONObject item : visible) {
            boolean expanded = selectedRequestId.equals(item.optString("id"));
            String state = item.optString("status");
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable outline = background(expanded ? Color.rgb(238, 245, 251) : Color.WHITE, 14);
            if (expanded) outline.setStroke(dp(1), Color.rgb(187, 208, 229));
            card.setBackground(outline);

            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(64));
            row.setPadding(dp(12), dp(8), dp(4), dp(8));
            row.setBackground(touchBackground(Color.TRANSPARENT, 14));
            ImageView stateIcon = icon(statusIcon(state), statusColor(state), statusLabel(state));
            stateIcon.setPadding(dp(8), dp(8), dp(8), dp(8));
            stateIcon.setBackground(background(Color.WHITE, 12));
            row.addView(stateIcon, new LinearLayout.LayoutParams(dp(36), dp(36)));
            LinearLayout text = new LinearLayout(this);
            text.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1);
            textParams.leftMargin = dp(12);
            TextView name = label(item.optString("name") + " 님", expanded ? 18 : 16, NAVY, true);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            text.addView(name);
            text.addView(label(calendarPage ? RequestNotes.calendarTime(item) : "희망 " + RequestSummary.date(item.optString("date")),
                    calendarPage ? 15 : 13, calendarPage ? BLUE : MUTED, calendarPage), margins(3, 0));
            if (calendarPage && item.optString("reservedTime").isEmpty())
                text.addView(label("예약 시간 등록 안됨", 11, MUTED, false), margins(2, 0));
            row.addView(text, textParams);
            String flag = item.optString("customerFlag");
            if (!flag.isEmpty()) {
                ImageView flagIcon = icon(CustomerFlags.icon(flag), CustomerFlags.color(flag), CustomerFlags.label(flag));
                flagIcon.setPadding(dp(3), dp(3), dp(3), dp(3));
                row.addView(flagIcon, new LinearLayout.LayoutParams(dp(28), dp(28)));
            }
            ImageView arrow = icon(R.drawable.ic_material_expand_more, MUTED, expanded ? "요약 접기" : null);
            arrow.setRotation(expanded ? 180 : 0);
            arrow.setPadding(dp(13), dp(13), dp(13), dp(13));
            row.addView(arrow, new LinearLayout.LayoutParams(dp(48), dp(48)));
            card.addView(row);
            LinearLayout summary = new LinearLayout(this);
            summary.setGravity(Gravity.CENTER_VERTICAL);
            summary.setPadding(dp(16), dp(2), dp(16), dp(12));
            ImageView location = icon(R.drawable.ic_material_location_on, MUTED, null);
            summary.addView(location, new LinearLayout.LayoutParams(dp(18), dp(18)));
            TextView address = label(RequestSummary.address(item.optString("address")), 14, NAVY, false);
            address.setMaxLines(2);
            address.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(0, -2, 1);
            addressParams.leftMargin = dp(6);
            summary.addView(address, addressParams);
            String method = item.optString("pickupMethod");
            if (!method.isEmpty()) {
                boolean unattended = method.contains("비대면");
                ImageView methodIcon = icon(unattended ? R.drawable.ic_material_person_off : R.drawable.ic_material_person,
                        unattended ? BLUE : MUTED, method);
                methodIcon.setPadding(dp(8), dp(8), dp(8), dp(8));
                methodIcon.setBackground(background(Color.WHITE, 10));
                LinearLayout.LayoutParams methodParams = new LinearLayout.LayoutParams(dp(36), dp(36));
                methodParams.leftMargin = dp(10);
                summary.addView(methodIcon, methodParams);
            }
            card.addView(summary);
            TextView details = label("신청 상세 보기", 13, BLUE, true);
            details.setGravity(Gravity.CENTER);
            details.setCompoundDrawablesWithIntrinsicBounds(null, null,
                    iconDrawable(R.drawable.ic_material_chevron_right, BLUE), null);
            details.setPadding(dp(16), 0, dp(12), 0);
            details.setBackground(touchBackground(Color.WHITE, 12));
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, dp(48));
            detailParams.setMargins(dp(12), 0, dp(12), dp(12));
            card.addView(details, detailParams);
            details.setOnClickListener(view -> showDetail(item));
            Runnable collapse = () -> setRequestSummary(card, name, arrow, summary, details, false);
            Runnable expand = () -> {
                if (collapseRequestSummary != null) collapseRequestSummary.run();
                selectedRequestId = item.optString("id");
                setRequestSummary(card, name, arrow, summary, details, true);
                collapseRequestSummary = collapse;
            };
            row.setOnClickListener(view -> {
                if (selectedRequestId.equals(item.optString("id"))) showDetail(item);
                else expand.run();
            });
            arrow.setOnClickListener(view -> {
                if (selectedRequestId.equals(item.optString("id"))) {
                    collapse.run(); selectedRequestId = ""; collapseRequestSummary = null;
                } else expand.run();
            });
            setRequestSummary(card, name, arrow, summary, details, expanded);
            if (expanded) collapseRequestSummary = collapse;
            list.addView(card, margins(6, 0));
        }
        if (calendarPage) renderDayRecord();
    }

    // Keep the existing list and calendar attached; only change the selected card.
    private void setRequestSummary(LinearLayout card, TextView name, ImageView arrow,
                                   LinearLayout summary, TextView details, boolean expanded) {
        summary.setVisibility(expanded ? View.VISIBLE : View.GONE);
        details.setVisibility(expanded ? View.VISIBLE : View.GONE);
        name.setTextSize(expanded ? 18 : 16);
        arrow.setRotation(expanded ? 180 : 0);
        arrow.setContentDescription(expanded ? "요약 접기" : "요약 보기");
        GradientDrawable outline = background(expanded ? Color.rgb(238, 245, 251) : Color.WHITE, 14);
        if (expanded) outline.setStroke(dp(1), Color.rgb(187, 208, 229));
        card.setBackground(outline);
    }

    private void newRequest() {
        editRequest(null);
    }

    private void selectCalendarDay(String day) {
        selectedDay = day; selectedRequestId = ""; renderRequests();
        // Move to the selected day's records after the rebuilt calendar has laid out.
        final LinearLayout currentList = list;
        currentList.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                currentList.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                if (list != currentList || !calendarPage || !day.equals(selectedDay)) return;
                View header = currentList.findViewWithTag("selected-day-header");
                if (header != null) ((ScrollView) currentList.getParent()).smoothScrollTo(0, header.getTop());
            }
        });
    }
    private void editRequest(JSONObject existing) {
        requestEditor = new AdminPlanner.FormEditor(this, calendarPage ? selectedDay : "", existing, editor -> {
            contactEditor = editor;
            try { startActivityForResult(new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), 201); }
            catch (ActivityNotFoundException e) { Toast.makeText(this, "연락처 앱을 찾지 못했습니다. 번호를 직접 입력해 주세요.", Toast.LENGTH_LONG).show(); }
        }, phone -> {
            JSONObject newest = null;
            for (int i = 0; i < requests.length(); i++) {
                JSONObject item = requests.optJSONObject(i);
                if (item != null && phone.equals(AdminPlanner.normalizePhone(item.optString("phone"))) &&
                        (newest == null || item.optLong("created_at") > newest.optLong("created_at"))) newest = item;
            }
            return newest;
        }, (data, result) -> writes.execute(() -> {
            try {
                JSONObject response = ApiClient.request(this, "POST", "/api/admin/native-requests", data, session());
                data.put("customerFlag", response.optString("customerFlag"));
                runOnUiThread(() -> { applySavedRequest(data); result.accept(null); selectedRequestId = data.optString("requestId");
                    if ("create".equals(data.optString("action"))) {
                        requestFilter = RequestListControls.ALL;
                        if (filterControls != null) filterControls.bind(this, requestFilter);
                        if (requestSort == 3) { requestSort = 2; sortControl.setSelection(2); }
                        saveViewPreferences();
                    }
                    if (calendarPage && !data.optString("date").isEmpty()) {
                        selectedDay = data.optString("date"); String[] parts = selectedDay.split("-");
                        calendarMonth.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, 1);
                    } else if (calendarPage) { calendarPage = false; render(); }
                    if (!memoPage && !statsPage) renderRequests();
                    Toast.makeText(this, "신청서를 저장했습니다.", Toast.LENGTH_SHORT).show(); });
            } catch (Exception e) { runOnUiThread(() -> result.accept(e.getMessage())); }
        }));
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 201 || resultCode != RESULT_OK || data == null || data.getData() == null ||
                contactEditor == null || !contactEditor.dialog.isShowing()) return;
        try (Cursor cursor = getContentResolver().query(data.getData(),
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) contactEditor.contact(cursor.getString(0), cursor.getString(1));
        } catch (Exception e) { Toast.makeText(this, "선택한 연락처를 가져오지 못했습니다. 직접 입력해 주세요.", Toast.LENGTH_LONG).show(); }
        contactEditor = null;
    }

    private void renderDayRecord() {
        String content = "";
        for (int i = 0; i < dayMemos.length(); i++) {
            JSONObject note = dayMemos.optJSONObject(i);
            if (note != null && selectedDay.equals(note.optString("date"))) content = note.optString("content");
        }
        final String existing = content;
        boolean loaded = financeLoadedMonth.equals(financeMonth());
        JSONObject record = CalendarLedger.day(dayFinances, selectedDay);
        LinearLayout card;
        if (loaded) card = CalendarLedger.daySummary(this, record.optLong("paidAmount"), record.optLong("receivedAmount"));
        else {
            card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14)); card.setBackground(background(Color.WHITE, 14));
            card.addView(label("하루 금액 · 메모", 16, NAVY, true));
            card.addView(label(financeError.isEmpty() ? "금액 기록을 불러오는 중…" : "금액 기록을 불러오지 못했습니다", 13, MUTED, false), margins(10, 0));
        }
        TextView body = label(content.isEmpty() ? "동선·휴무·재연락할 내용을 남겨 보세요." : content, 14, MUTED, false);
        body.setLineSpacing(dp(4), 1); card.addView(body, margins(8, 12));
        Button edit = AdminPlanner.button(this, loaded ? "금액 · 메모 수정" : "다시 불러오기", BLUE, SURFACE, R.drawable.ic_material_edit);
        card.addView(edit); edit.setOnClickListener(v -> {
            if (!loaded) refresh(); else openDayRecord(selectedDay, record, existing);
        });
        list.addView(card, margins(4, 10));
    }

    private void openDayRecord(String day, JSONObject record, String content) {
        ledgerEditor = new CalendarLedger.Editor(this, day, record, content, (data, result) -> writes.execute(() -> {
            try {
                ApiClient.request(this, "POST", "/api/admin/native-day-finances", data, session());
                runOnUiThread(() -> {
                    if (day.startsWith(financeMonth() + "-") && financeLoadedMonth.equals(financeMonth())) {
                        JSONArray updated = new JSONArray();
                        for (int i = 0; i < dayFinances.length(); i++) {
                            JSONObject previous = dayFinances.optJSONObject(i);
                            if (previous != null && !day.equals(previous.optString("date"))) updated.put(previous);
                        }
                        updated.put(data); dayFinances = updated;
                    }
                    JSONArray notes = new JSONArray();
                    for (int i = 0; i < dayMemos.length(); i++) {
                        JSONObject previous = dayMemos.optJSONObject(i);
                        if (previous != null && !day.equals(previous.optString("date"))) notes.put(previous);
                    }
                    if (!data.optString("content").isEmpty()) notes.put(data); dayMemos = notes;
                    dataRevision++; result.accept(null); if (calendarPage) renderRequests(); else if (statsPage) renderStats();
                    Toast.makeText(this, "하루 기록을 저장했습니다.", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) { runOnUiThread(() -> result.accept(e.getMessage())); }
        }));
    }

    private void emptyCard(String message) {
        TextView empty = label(message, 16, MUTED, false);
        empty.setGravity(Gravity.CENTER);
        empty.setBackground(background(Color.WHITE, 18));
        empty.setPadding(dp(18), dp(55), dp(18), dp(55));
        list.addView(empty, margins(12, 0));
    }

    private void saveViewPreferences() {
        getSharedPreferences("admin", MODE_PRIVATE).edit().putInt("ui_filter", requestFilter)
            .putInt("ui_request_sort", requestSort).putInt("ui_memo_sort", memoSort)
            .putBoolean("ui_menu_collapsed", menuCollapsed).apply();
    }

    private void changeSort(int selected) {
        if (statsPage || list == null) return;
        if (memoPage) {
            if (memoSort == selected) return;
            memoSort = selected; saveViewPreferences(); renderMemos();
        } else {
            if (requestSort == selected) return;
            requestSort = selected;
            if (selected == 3) {
                requestFilter = RequestListControls.NEW | RequestListControls.CONTACTED;
                if (filterControls != null) filterControls.bind(this, requestFilter);
            }
            saveViewPreferences(); renderRequests();
        }
    }

    private void chooseRequestNote() {
        if (requests.length() == 0) { Toast.makeText(this, "신청서를 먼저 등록해 주세요.", Toast.LENGTH_LONG).show(); return; }
        ArrayList<JSONObject> choices = new ArrayList<>();
        for (int i = 0; i < requests.length(); i++) if (requests.optJSONObject(i) != null) choices.add(requests.optJSONObject(i));
        String[] names = new String[choices.size()];
        for (int i = 0; i < choices.size(); i++) {
            JSONObject item = choices.get(i);
            names[i] = item.optString("name") + " · " + RequestSummary.date(item.optString("date")) + " · " + item.optString("phone");
        }
        new AlertDialog.Builder(this).setTitle("메모를 남길 신청서").setItems(names,
                (d, which) -> editRequestNote(choices.get(which))).setNegativeButton("취소", null).show();
    }

    private void editRequestNote(JSONObject item) {
        RequestNotes.edit(this, item, (data, result) -> writes.execute(() -> {
            try {
                ApiClient.request(this, "POST", "/api/admin/native-requests", data, session());
                runOnUiThread(() -> { applySavedRequest(data); result.accept(null); });
            } catch (Exception error) { result.accept(error.getMessage()); }
        }));
    }

    private void renderMemos() {
        if (list == null || !memoPage) return;
        list.removeAllViews();
        String query = memoSearch.trim().toLowerCase(Locale.KOREA);
        ArrayList<JSONObject> notes = new ArrayList<>();
        for (int i = 0; i < requests.length(); i++) {
            JSONObject item = requests.optJSONObject(i);
            if (item == null || item.optString("adminNote").isEmpty()) continue;
            String haystack = (item.optString("name") + " " + item.optString("phone") + " " + item.optString("adminNote")).toLowerCase(Locale.KOREA);
            if (!haystack.contains(query) && !(query.matches("[0-9 ()+-]+") && item.optString("phone").contains(AdminPlanner.normalizePhone(query)))) continue;
            notes.add(item);
        }
        Collections.sort(notes, (a, b) -> {
            if (memoSort == 2) return a.optString("name").compareTo(b.optString("name"));
            if (memoSort == 3) return a.optString("date").compareTo(b.optString("date"));
            return memoSort == 1 ? Long.compare(a.optLong("adminNoteUpdatedAt"), b.optLong("adminNoteUpdatedAt")) :
                    Long.compare(b.optLong("adminNoteUpdatedAt"), a.optLong("adminNoteUpdatedAt"));
        });
        count.setText("신청서 메모"); notice.setText("메모 " + notes.size() + "건 · 누르면 해당 신청서 열기");
        if (notes.isEmpty()) emptyCard("신청서에 메모를 남기면 여기에 모아 보여드려요.");
        for (JSONObject item : notes) {
            LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14)); card.setBackground(touchBackground(Color.WHITE, 14));
            TextView memoCustomer = label(item.optString("name") + " 님 · " + item.optString("phone"), 16, NAVY, true);
            customerFlagTitle(memoCustomer, item); card.addView(memoCustomer);
            card.addView(label(RequestSummary.date(item.optString("date")) + " · " + RequestNotes.calendarTime(item), 12, BLUE, false), margins(5, 0));
            TextView content = label(item.optString("adminNote"), 15, NAVY, false); content.setMaxLines(3); content.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(content, margins(8, 0));
            card.setOnClickListener(v -> showDetail(item)); list.addView(card, margins(7, 0));
        }
        // Keep existing notes readable without guessing which same-phone request they belong to.
        ArrayList<JSONObject> legacy = new ArrayList<>();
        for (int i = 0; i < memos.length(); i++) {
            JSONObject item = memos.optJSONObject(i); if (item == null) continue;
            String text = (item.optString("name") + " " + item.optString("phone") + " " + item.optString("title") + " " + item.optString("content")).toLowerCase(Locale.KOREA);
            if (text.contains(query)) legacy.add(item);
        }
        Collections.sort(legacy, (a, b) -> memoSort == 2 ? a.optString("name").compareTo(b.optString("name")) :
                memoSort == 1 ? Long.compare(a.optLong("updated_at"), b.optLong("updated_at")) : Long.compare(b.optLong("updated_at"), a.optLong("updated_at")));
        if (!legacy.isEmpty()) list.addView(label("이전 메모 · 신청서 연결 전 기록", 13, MUTED, true), margins(24, 8));
        for (JSONObject item : legacy) {
            LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14)); card.setBackground(touchBackground(Color.WHITE, 14));
            card.addView(label(item.optString("name") + " · " + item.optString("title"), 15, NAVY, true));
            TextView content = label(item.optString("content"), 14, MUTED, false); content.setMaxLines(3); content.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(content, margins(6, 0)); card.setOnClickListener(v -> editMemo(item, "", "")); list.addView(card, margins(7, 0));
        }
    }

    private void showDetail(JSONObject item) {
        String name = item.optString("name");
        String phone = item.optString("phone");
        String status = item.optString("status");
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackground(background(SURFACE, 22));
        shell.setClipToOutline(true);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(12), dp(8), dp(12));
        header.setBackgroundColor(Color.WHITE);
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView customer = label(name + " 님의 신청", 21, NAVY, true);
        customerFlagTitle(customer, item);
        customer.setMaxLines(2);
        heading.addView(customer);
        TextView state = label(statusLabel(status) + "  ·  " + displayDate(item.optLong("created_at")) + " 접수", 12, statusColor(status), false);
        heading.addView(state, margins(5, 0));
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView edit = icon(R.drawable.ic_material_edit, BLUE, "신청서 수정");
        edit.setPadding(dp(12), dp(12), dp(12), dp(12)); edit.setBackground(touchBackground(Color.WHITE, 24));
        header.addView(edit, new LinearLayout.LayoutParams(dp(48), dp(48)));
        ImageView close = icon(R.drawable.ic_material_close, MUTED, "신청서 닫기");
        close.setPadding(dp(13), dp(13), dp(13), dp(13));
        close.setBackground(touchBackground(Color.WHITE, 24));
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        shell.addView(header);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(4), dp(16), dp(16));
        LinearLayout schedule = detailSection(body, "방문 일정");
        detailLine(schedule, "희망 날짜", RequestSummary.date(item.optString("date")));
        detailLine(schedule, "희망 시간대", item.optString("timeSlot", "미기재"));
        detailLine(schedule, "수거 방식", item.optString("pickupMethod", "미기재"));
        LinearLayout address = detailSection(body, "연락처 · 수거 장소");
        detailLine(address, "전화번호", phone);
        String customerFlag = item.optString("customerFlag");
        Button flagButton = AdminPlanner.button(this, "고객 표시 · " + CustomerFlags.label(customerFlag),
                CustomerFlags.color(customerFlag), SURFACE, CustomerFlags.icon(customerFlag));
        flagButton.setSingleLine(false); flagButton.setMaxLines(2);
        address.addView(flagButton, margins(8, 0));
        LinearLayout flagProgress = LoadingTasks.indicator(this, "고객 표시 저장 중…");
        address.addView(flagProgress, margins(4, 0));
        flagButton.setOnClickListener(v -> chooseCustomerFlag(item, flagButton, flagProgress, customer));
        detailLine(address, "수거 주소", item.optString("address"));
        LinearLayout belongings = detailSection(body, "수거 물품 · 요청사항");
        detailLine(belongings, "예상 수거량", item.optString("amount"));
        detailLine(belongings, "문의 내용", item.optString("message", "없음"));
        LinearLayout admin = detailSection(body, "관리자 기록");
        detailLine(admin, "예약 시간", item.optString("reservedTime").isEmpty() ? "등록 안됨" : item.optString("reservedTime"));
        detailLine(admin, "신청서 메모", item.optString("adminNote"));
        Button manage = AdminPlanner.button(this, "예약 시간 · 메모 수정", BLUE, SURFACE, R.drawable.ic_material_edit);
        admin.addView(manage, margins(12, 0));
        ScrollView detailScroll = new ScrollView(this);
        detailScroll.setFillViewport(false);
        detailScroll.addView(body);
        shell.addView(detailScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(dp(8), dp(8), dp(8), dp(8));
        actions.setBackgroundColor(Color.WHITE);
        LinearLayout contactActions = actions;
        LinearLayout statusActions = actions;
        LinearLayout dial = detailAction(contactActions, "전화", R.drawable.ic_material_call, Color.WHITE, CORAL);
        LinearLayout contact = detailAction(contactActions, "연락처 추가", R.drawable.ic_material_person_add, Color.WHITE, BLUE);
        LinearLayout addMemo = detailAction(contactActions, "메모 작성", R.drawable.ic_material_note_add, NAVY, SURFACE);
        LinearLayout contacted = detailAction(statusActions, "연락 완료", R.drawable.ic_material_contacts, BLUE,
                "contacted".equals(status) ? Color.rgb(221, 235, 250) : SURFACE);
        LinearLayout done = detailAction(statusActions, "처리 완료", R.drawable.ic_material_check_circle, GREEN,
                "done".equals(status) ? Color.rgb(221, 243, 231) : SURFACE);
        LinearLayout delete = detailAction(statusActions, "삭제", R.drawable.ic_material_delete, MUTED, Color.WHITE);
        shell.addView(actions);
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(shell, new ViewGroup.LayoutParams(-1, -1));
        close.setOnClickListener(view -> dialog.dismiss());
        edit.setOnClickListener(view -> { dialog.dismiss(); editRequest(item); });
        dial.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone));
            try { startActivity(intent); }
            catch (ActivityNotFoundException error) { Toast.makeText(this, "전화 앱을 찾지 못했습니다.", Toast.LENGTH_SHORT).show(); }
        });
        contact.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_INSERT);
            intent.setType(ContactsContract.Contacts.CONTENT_TYPE);
            intent.putExtra(ContactsContract.Intents.Insert.NAME, name);
            intent.putExtra(ContactsContract.Intents.Insert.PHONE, phone);
            try { startActivity(intent); }
            catch (ActivityNotFoundException error) { Toast.makeText(this, "연락처 앱을 찾지 못했습니다.", Toast.LENGTH_SHORT).show(); }
        });
        contacted.setOnClickListener(view -> { dialog.dismiss(); updateRequest(item.optString("id"), "contacted"); });
        done.setOnClickListener(view -> { dialog.dismiss(); updateRequest(item.optString("id"), "done"); });
        addMemo.setOnClickListener(view -> { dialog.dismiss(); editRequestNote(item); });
        manage.setOnClickListener(view -> { dialog.dismiss(); editRequestNote(item); });
        delete.setOnClickListener(view -> new AlertDialog.Builder(this).setTitle("신청서 삭제")
                .setMessage(name + " 님의 신청서와 이 신청서의 예약 시간·메모를 삭제할까요? 삭제 후에는 복구할 수 없습니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (d, which) -> {
                    dialog.dismiss();
                    writes.execute(() -> {
                        try {
                            ApiClient.request(this, "DELETE", "/api/admin/native-requests",
                                    new JSONObject().put("id", item.optString("id")), session());
                            runOnUiThread(() -> {
                                dataRevision++; requests = RequestCache.remove(requests, item.optString("id"));
                                if (memoPage) renderMemos(); else if (!statsPage) renderRequests();
                            });
                        } catch (Exception error) { showError(error); }
                    });
                }).show());
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            int height = Math.min(dp(760), (int) (getResources().getDisplayMetrics().heightPixels * .88f));
            dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels - dp(24), height);
        }
    }

    private void customerFlagTitle(TextView title, JSONObject item) {
        String flag = item.optString("customerFlag");
        Drawable symbol = flag.isEmpty() ? null : iconDrawable(CustomerFlags.icon(flag), CustomerFlags.color(flag));
        if (symbol != null) symbol.setBounds(0, 0, dp(24), dp(24));
        title.setCompoundDrawablesRelative(symbol, null, null, null); title.setCompoundDrawablePadding(dp(6));
        title.setContentDescription(title.getText() + (flag.isEmpty() ? "" : " · " + CustomerFlags.label(flag)));
    }

    private void chooseCustomerFlag(JSONObject item, Button button, LinearLayout progress, TextView title) {
        String[] flags = {"", "regular", "blacklist"};
        String[] names = {"표시 없음 · 해제", "★ 단골", "⊘ 블랙리스트"};
        String current = item.optString("customerFlag");
        int selected = "regular".equals(current) ? 1 : "blacklist".equals(current) ? 2 : 0;
        new AlertDialog.Builder(this).setTitle("전화번호별 고객 표시")
            .setSingleChoiceItems(names, selected, (dialog, which) -> {
                dialog.dismiss(); if (flags[which].equals(current)) return;
                button.setEnabled(false); progress.setVisibility(View.VISIBLE);
                writes.execute(() -> {
                    try {
                        JSONObject data = new JSONObject().put("action", "customerFlag").put("phone", item.optString("phone")).put("flag", flags[which]);
                        JSONObject response = ApiClient.request(this, "POST", "/api/admin/native-requests", data, session());
                        runOnUiThread(() -> {
                            try {
                                String flag = response.optString("flag");
                                dataRevision++; requests = CustomerFlags.apply(requests, response.optString("phone"), flag);
                                item.put("customerFlag", flag); customerFlagTitle(title, item);
                                button.setText("고객 표시 · " + CustomerFlags.label(flag)); button.setTextColor(CustomerFlags.color(flag));
                                button.setCompoundDrawablesWithIntrinsicBounds(iconDrawable(CustomerFlags.icon(flag), CustomerFlags.color(flag)), null, null, null);
                                if (memoPage) renderMemos(); else if (!statsPage) renderRequests();
                                Toast.makeText(this, "고객 표시를 저장했습니다.", Toast.LENGTH_SHORT).show();
                            } catch (Exception error) { showError(error); }
                            button.setEnabled(true); progress.setVisibility(View.GONE);
                        });
                    } catch (Exception error) {
                        runOnUiThread(() -> { button.setEnabled(true); progress.setVisibility(View.GONE); }); showError(error);
                    }
                });
            }).setNegativeButton("닫기", null).show();
    }

    private LinearLayout detailSection(LinearLayout parent, String title) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(16), dp(15), dp(16), dp(16));
        section.setBackground(background(Color.WHITE, 14));
        section.addView(label(title, 15, NAVY, true), margins(0, 3));
        parent.addView(section, margins(12, 0));
        return section;
    }

    private LinearLayout detailAction(LinearLayout parent, String title, int resource, int color, int fill) {
        LinearLayout action = new LinearLayout(this);
        action.setOrientation(LinearLayout.VERTICAL);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(2), dp(8), dp(2), dp(8));
        action.setMinimumHeight(dp(48));
        if (Build.VERSION.SDK_INT >= 26) action.setTooltipText(title);
        action.setBackground(touchBackground(fill, 12));
        action.setFocusable(true);
        action.setContentDescription(title);
        // Expose the complete action as a single button to accessibility services.
        action.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Button.class.getName());
            }
        });
        action.addView(icon(resource, color, null), new LinearLayout.LayoutParams(dp(26), dp(26)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
        if (parent.getChildCount() > 0) params.leftMargin = dp("삭제".equals(title) ? 8 : 3);
        parent.addView(action, params);
        return action;
    }

    private void updateRequest(String id, String status) {
        writes.execute(() -> {
            try {
                ApiClient.request(this, "POST", "/api/admin/native-requests",
                        new JSONObject().put("id", id).put("status", status), session());
                runOnUiThread(() -> { try { applySavedRequest(new JSONObject().put("id", id).put("status", status)); } catch (Exception error) { showError(error); } });
            } catch (Exception error) { showError(error); }
        });
    }

    private void showError(Exception error) {
        runOnUiThread(() -> Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show());
    }

    private EditText memoField(LinearLayout parent, String hint, String value, boolean multiline) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setText(value);
        field.setTextSize(15);
        field.setPadding(dp(13), dp(10), dp(13), dp(10));
        field.setBackground(background(Color.WHITE, 10));
        if (multiline) {
            field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            field.setMinLines(4);
            field.setGravity(Gravity.TOP);
        } else field.setSingleLine(true);
        parent.addView(field, margins(9, 0));
        return field;
    }

    private void editMemo(JSONObject existing, String prefillName, String prefillPhone) {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(20), dp(8), dp(20), dp(12));
        EditText name = memoField(fields, "고객 이름", existing == null ? prefillName : existing.optString("name"), false);
        EditText phone = memoField(fields, "전화번호", existing == null ? prefillPhone : existing.optString("phone"), false);
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        EditText title = memoField(fields, "메모 제목", existing == null ? "" : existing.optString("title"), false);
        EditText content = memoField(fields, "메모 내용", existing == null ? "" : existing.optString("content"), true);
        LinearLayout progress = LoadingTasks.indicator(this, "저장 중…"); fields.addView(progress);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(fields);
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(existing == null ? "새 메모" : "메모 수정")
                .setView(scroll).setNegativeButton("취소", null).setPositiveButton("저장", null);
        if (existing != null) builder.setNeutralButton("삭제", null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String n = name.getText().toString().trim();
                String p = AdminPlanner.normalizePhone(phone.getText().toString());
                String t = title.getText().toString().trim();
                String c = content.getText().toString().trim();
                if (n.isEmpty() || !p.matches("0\\d{8,10}") || t.isEmpty() || c.isEmpty()) {
                    Toast.makeText(this, "이름, 전화번호, 제목, 내용을 입력해 주세요.", Toast.LENGTH_LONG).show(); return;
                }
                progress.setVisibility(View.VISIBLE); dialog.setCancelable(false);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
                if (existing != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                writes.execute(() -> {
                    try {
                        JSONObject data = new JSONObject().put("name", n).put("phone", p).put("title", t).put("content", c);
                        if (existing != null) data.put("id", existing.optString("id"));
                        ApiClient.request(this, "POST", "/api/admin/native-memos", data, session());
                        runOnUiThread(() -> { dialog.dismiss(); memoPage = true; calendarPage = false; statsPage = false; render(); });
                    } catch (Exception error) {
                        runOnUiThread(() -> {
                            progress.setVisibility(View.GONE); dialog.setCancelable(true);
                            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                            if (existing != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        });
                        showError(error);
                    }
                });
            });
            if (existing != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view ->
                    new AlertDialog.Builder(this).setTitle("메모 삭제").setMessage("이 메모를 삭제할까요?")
                            .setNegativeButton("취소", null).setPositiveButton("삭제", (d, which) -> writes.execute(() -> {
                                try {
                                    ApiClient.request(this, "DELETE", "/api/admin/native-memos",
                                            new JSONObject().put("id", existing.optString("id")), session());
                                    runOnUiThread(() -> { dialog.dismiss(); refresh(); });
                                } catch (Exception error) { showError(error); }
                            })).show());
        });
        dialog.show();
    }

    private void detailLine(LinearLayout parent, String title, String value) {
        parent.addView(label(title, 12, MUTED, false), margins(12, 4));
        TextView content = label(value.isEmpty() ? "없음" : value, 16, NAVY, false);
        content.setLineSpacing(dp(4), 1);
        content.setTextIsSelectable(true);
        parent.addView(content);
    }

    private void editServerUrl() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(serverUrl());
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("홈페이지 서버 주소")
                .setMessage("홈페이지가 배포된 주소를 입력하세요. 예: https://example.com")
                .setView(input)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", (dialog, which) -> {
                    String value = input.getText().toString().trim().replaceAll("/+$", "");
                    Uri uri = Uri.parse(value);
                    String host = uri.getHost();
                    boolean local = "http".equals(uri.getScheme()) &&
                            ("10.0.2.2".equals(host) || "localhost".equals(host) || "127.0.0.1".equals(host));
                    if (host == null || uri.getUserInfo() != null || uri.getQuery() != null ||
                            uri.getFragment() != null || (uri.getPath() != null && !uri.getPath().isEmpty()) ||
                            !("https".equals(uri.getScheme()) || local)) {
                        Toast.makeText(this, "HTTPS 홈페이지 주소를 입력해 주세요.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    getSharedPreferences("admin", MODE_PRIVATE).edit()
                            .putString("api_base_url", value).remove("session").apply();
                    render();
                    Toast.makeText(this, "서버 주소를 저장했습니다. 다시 로그인해 주세요.", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void requestAlerts() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        } else registerDevice();
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 100 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) registerDevice();
        else if (code == 100) Toast.makeText(this, "알림 권한이 허용되지 않았습니다.", Toast.LENGTH_LONG).show();
    }

    private void registerDevice() {
        if (FirebaseApp.getApps(this).isEmpty()) {
            if (notice != null) notice.setText("Firebase 설정 후 휴대폰 알림을 켤 수 있습니다.");
            return;
        }
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            if (session().isEmpty()) return;
            io.execute(() -> {
                try {
                    ApiClient.request(this, "POST", "/api/admin/native-device", new JSONObject().put("token", token), session());
                    runOnUiThread(() -> { if (notice != null) notice.setText("이 휴대폰의 알림을 등록했습니다."); });
                } catch (Exception error) { runOnUiThread(() -> { if (notice != null) notice.setText(error.getMessage()); }); }
            });
        }).addOnFailureListener(error -> { if (notice != null) notice.setText("알림 토큰을 받지 못했습니다."); });
    }

    private void unregisterDevice() {
        if (FirebaseApp.getApps(this).isEmpty()) return;
        String token = session();
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(device -> io.execute(() -> {
            try { ApiClient.request(this, "DELETE", "/api/admin/native-device", new JSONObject().put("token", device), token); }
            catch (Exception ignored) { }
        }));
    }
}
