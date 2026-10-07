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
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int NAVY = Color.rgb(24, 51, 78);
    private static final int CORAL = Color.rgb(233, 84, 72);
    private static final int MUTED = Color.rgb(100, 122, 140);
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout root;
    private LinearLayout list;
    private TextView notice;
    private TextView count;
    private JSONArray requests = new JSONArray();
    private JSONArray memos = new JSONArray();
    private boolean memoPage = false;
    private boolean calendarPage = false;
    private Calendar calendarMonth = AdminPlanner.calendar();
    private String selectedDay = AdminPlanner.iso(AdminPlanner.calendar());
    private JSONArray dayMemos = new JSONArray();
    private AdminPlanner.FormEditor contactEditor;
    private AdminPlanner.FormEditor requestEditor;
    private int requestFilter = 0;
    private int requestSort = 0;
    private int memoSort = 0;
    private String memoSearch = "";
    private String selectedRequestId = "";

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
        if (state != null) {
            calendarPage = state.getBoolean("calendarPage"); memoPage = state.getBoolean("memoPage");
            selectedDay = state.getString("selectedDay", selectedDay);
            calendarMonth.setTimeInMillis(state.getLong("calendarMonth", calendarMonth.getTimeInMillis()));
        }
        render();
        if (state != null && state.containsKey("requestDraft") && !session().isEmpty()) {
            try { editRequest(new JSONObject(state.getString("requestDraft"))); contactEditor = requestEditor; }
            catch (Exception ignored) { }
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("calendarPage", calendarPage); state.putBoolean("memoPage", memoPage);
        state.putString("selectedDay", selectedDay); state.putLong("calendarMonth", calendarMonth.getTimeInMillis());
        if (requestEditor != null && requestEditor.dialog.isShowing()) state.putString("requestDraft", requestEditor.draft().toString());
    }

    @Override public void onResume() {
        super.onResume();
        if (list != null && !session().isEmpty()) refresh();
    }

    @Override public void onDestroy() {
        if (requestEditor != null && requestEditor.dialog.isShowing()) requestEditor.dialog.dismiss();
        super.onDestroy();
        io.shutdownNow();
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
            io.execute(() -> {
                try {
                    JSONObject input = new JSONObject().put("password", entered);
                    String token = ApiClient.request(this, "POST", "/api/admin/native-login", input, null).getString("token");
                    runOnUiThread(() -> { saveSession(token); password.setText(""); render(); registerDevice(); });
                } catch (Exception error) {
                    runOnUiThread(() -> { login.setEnabled(true); notice.setText(error.getMessage()); });
                }
            });
        });
    }

    private void showInbox() {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(dp(16), dp(10), dp(16), dp(8));
        actions.setBackgroundColor(Color.rgb(244, 247, 249));
        root.addView(actions);
        count = label(memoPage ? "신청서 메모" : calendarPage ? "수거 달력" : "수거 신청함", 23, NAVY, true);
        actions.addView(count);
        LinearLayout tabs = new LinearLayout(this);
        actions.addView(tabs, margins(8, 0));
        Button requestTab = button("신청서", !memoPage && !calendarPage ? NAVY : MUTED);
        Button memoTab = button("메모", memoPage ? NAVY : MUTED);
        Button calendarTab = button("달력", calendarPage ? NAVY : MUTED);
        tabs.addView(requestTab, new LinearLayout.LayoutParams(0, dp(40), 1));
        LinearLayout.LayoutParams calendarGap = new LinearLayout.LayoutParams(0, dp(40), 1); calendarGap.leftMargin = dp(7);
        tabs.addView(calendarTab, calendarGap);
        LinearLayout.LayoutParams tabGap = new LinearLayout.LayoutParams(0, dp(40), 1); tabGap.leftMargin = dp(7);
        tabs.addView(memoTab, tabGap);
        requestTab.setOnClickListener(view -> { memoPage = false; calendarPage = false; render(); });
        calendarTab.setOnClickListener(view -> { memoPage = false; calendarPage = true; selectedRequestId = ""; render(); });
        memoTab.setOnClickListener(view -> { memoPage = true; calendarPage = false; render(); });
        if (!memoPage) {
            Button add = AdminPlanner.button(this, "신청서 직접 작성", Color.WHITE, CORAL, R.drawable.ic_material_note_add);
            actions.addView(add, margins(8, 0)); add.setOnClickListener(view -> newRequest());
        }
        LinearLayout controls = new LinearLayout(this);
        actions.addView(controls, margins(7, 0));
        Button sort = button("정렬", NAVY);
        Button filter = button(memoPage ? "새 메모" : "필터", memoPage ? CORAL : NAVY);
        Button refresh = button("새로고침", MUTED);
        controls.addView(sort, new LinearLayout.LayoutParams(0, dp(39), 1));
        LinearLayout.LayoutParams controlGap = new LinearLayout.LayoutParams(0, dp(39), 1); controlGap.leftMargin = dp(6);
        controls.addView(filter, controlGap);
        LinearLayout.LayoutParams refreshGap = new LinearLayout.LayoutParams(0, dp(39), 1); refreshGap.leftMargin = dp(6);
        controls.addView(refresh, refreshGap);
        sort.setOnClickListener(view -> chooseSort());
        filter.setOnClickListener(view -> { if (memoPage) chooseRequestNote(); else chooseFilter(); });
        if (memoPage) {
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
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(244, 247, 249));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), 0, dp(16), dp(22));
        scroll.addView(list);
        CollapsingActions.attach(scroll, actions);
        refresh.setOnClickListener(view -> refresh());
        if (memoPage) renderMemos(); else renderRequests();
        refresh();
    }

    private void showSettings() {
        String[] choices = {"알림 설정", "서버 주소 설정", "로그아웃"};
        new AlertDialog.Builder(this).setTitle("관리자 설정").setItems(choices, (dialog, which) -> {
            if (which == 0) requestAlerts();
            else if (which == 1) editServerUrl();
            else {
                unregisterDevice();
                saveSession("");
                render();
            }
        }).setNegativeButton("닫기", null).show();
    }

    private void refresh() {
        if (list == null || session().isEmpty()) return;
        final boolean loadMemos = memoPage, loadCalendar = calendarPage;
        notice.setText(memoPage ? "메모를 불러오는 중…" : "신청 내용을 불러오는 중…");
        io.execute(() -> {
            try {
                if (loadMemos) {
                    JSONArray loaded = ApiClient.request(this, "GET", "/api/admin/native-memos", null, session()).getJSONArray("memos");
                    JSONArray loadedRequests = ApiClient.request(this, "GET", "/api/admin/native-requests", null, session()).getJSONArray("requests");
                    runOnUiThread(() -> { memos = loaded; requests = loadedRequests; if (memoPage) renderMemos(); });
                } else {
                    JSONArray loaded = ApiClient.request(this, "GET", "/api/admin/native-requests", null, session()).getJSONArray("requests");
                    JSONArray notes = loadCalendar ? ApiClient.request(this, "GET", "/api/admin/native-day-memos", null, session()).getJSONArray("memos") : null;
                    runOnUiThread(() -> { requests = loaded; if (notes != null) dayMemos = notes;
                        if (!memoPage && calendarPage == loadCalendar) renderRequests(); });
                }
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (error instanceof ApiClient.ApiException && ((ApiClient.ApiException) error).status == 401) {
                        saveSession(""); render();
                    } else if (notice != null) notice.setText(error.getMessage());
                });
            }
        });
    }

    private String displayDate(long epoch) {
        SimpleDateFormat format = new SimpleDateFormat("yy년 M월 d일 a h:mm", Locale.KOREA);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        return format.format(new Date(epoch));
    }

    private void renderRequests() {
        if (list == null) return;
        list.removeAllViews();
        int unread = 0;
        for (int i = 0; i < requests.length(); i++) {
            JSONObject item = requests.optJSONObject(i);
            if (item != null && "new".equals(item.optString("status"))) unread++;
        }
        count.setText(calendarPage ? "수거 달력" : "수거 신청함  ·  새 신청 " + unread);
        ArrayList<JSONObject> visible = new ArrayList<>();
        for (int i = 0; i < requests.length(); i++) {
            JSONObject item = requests.optJSONObject(i);
            if (item == null) continue;
            if (calendarPage && !selectedDay.equals(item.optString("date"))) continue;
            if (requestFilter == 1 && !"new".equals(item.optString("status"))) continue;
            if (requestFilter == 2 && !"contacted".equals(item.optString("status"))) continue;
            if (requestFilter == 3 && !"done".equals(item.optString("status"))) continue;
            visible.add(item);
        }
        Collections.sort(visible, (a, b) -> {
            if (requestSort == 2) {
                int dates = a.optString("date").compareTo(b.optString("date"));
                if (dates != 0) return dates;
            }
            return requestSort == 1 ? Long.compare(a.optLong("created_at"), b.optLong("created_at")) :
                    Long.compare(b.optLong("created_at"), a.optLong("created_at"));
        });
        notice.setText(calendarPage ? "날짜를 누르면 신청서와 메모를 볼 수 있어요" : "표시 " + visible.size() + "건 · 누르면 요약, 상세 보기로 전체 확인");
        if (calendarPage) {
            list.addView(AdminPlanner.month(this, calendarMonth, selectedDay, requests, requestFilter, dayMemos,
                    day -> { selectedDay = day; selectedRequestId = ""; renderRequests(); },
                    offset -> { calendarMonth.set(Calendar.DAY_OF_MONTH, 1); calendarMonth.add(Calendar.MONTH, offset);
                        selectedDay = AdminPlanner.iso(calendarMonth); selectedRequestId = ""; renderRequests(); }), margins(6, 0));
            LinearLayout dayHeader = new LinearLayout(this); dayHeader.setGravity(Gravity.CENTER_VERTICAL);
            TextView dayTitle = label(RequestSummary.date(selectedDay) + " · " + visible.size() + "건", 16, NAVY, true);
            dayHeader.addView(dayTitle, new LinearLayout.LayoutParams(0, -2, 1));
            ImageView today = AdminPlanner.iconButton(this, R.drawable.ic_material_today, "오늘로 이동");
            today.setOnClickListener(v -> { calendarMonth = AdminPlanner.calendar(); selectedDay = AdminPlanner.iso(calendarMonth); renderRequests(); });
            dayHeader.addView(today, new LinearLayout.LayoutParams(dp(48), dp(48)));
            list.addView(dayHeader, margins(12, 4));
        }
        if (visible.isEmpty()) emptyCard("해당하는 신청서가 없습니다.");
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
            ImageView arrow = icon(R.drawable.ic_material_expand_more, MUTED, expanded ? "요약 접기" : null);
            arrow.setRotation(expanded ? 180 : 0);
            arrow.setPadding(dp(13), dp(13), dp(13), dp(13));
            row.addView(arrow, new LinearLayout.LayoutParams(dp(48), dp(48)));
            row.setOnClickListener(view -> {
                if (expanded) showDetail(item);
                else {
                    selectedRequestId = item.optString("id");
                    TransitionManager.beginDelayedTransition(list, new AutoTransition().setDuration(160));
                    renderRequests();
                }
            });
            if (expanded) arrow.setOnClickListener(view -> {
                selectedRequestId = "";
                TransitionManager.beginDelayedTransition(list, new AutoTransition().setDuration(160));
                renderRequests();
            });
            card.addView(row);
            if (expanded) {
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
            }
            list.addView(card, margins(6, 0));
        }
        if (calendarPage) renderDayMemo();
    }

    private void newRequest() {
        editRequest(null);
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
        }, (data, result) -> io.execute(() -> {
            try {
                ApiClient.request(this, "POST", "/api/admin/native-requests", data, session());
                runOnUiThread(() -> { result.accept(null); selectedRequestId = data.optString("requestId");
                    if ("create".equals(data.optString("action"))) requestFilter = 0;
                    if (calendarPage && !data.optString("date").isEmpty()) {
                        selectedDay = data.optString("date"); String[] parts = selectedDay.split("-");
                        calendarMonth.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, 1);
                    } else if (calendarPage) { calendarPage = false; render(); }
                    refresh();
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

    private void renderDayMemo() {
        String content = "";
        for (int i = 0; i < dayMemos.length(); i++) {
            JSONObject note = dayMemos.optJSONObject(i);
            if (note != null && selectedDay.equals(note.optString("date"))) content = note.optString("content");
        }
        final String existing = content;
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14)); card.setBackground(background(Color.WHITE, 14));
        card.addView(label("날짜 메모", 16, NAVY, true));
        TextView body = label(content.isEmpty() ? "동선·휴무·재연락할 내용을 남겨 보세요." : content, 14, MUTED, false);
        body.setLineSpacing(dp(4), 1); card.addView(body, margins(8, 12));
        Button edit = AdminPlanner.button(this, content.isEmpty() ? "날짜 메모 작성" : "날짜 메모 수정", BLUE, SURFACE, R.drawable.ic_material_note_add);
        card.addView(edit); edit.setOnClickListener(v -> AdminPlanner.editNote(this, selectedDay, existing, (day, note, result) -> io.execute(() -> {
            try {
                ApiClient.request(this, "POST", "/api/admin/native-day-memos", new JSONObject().put("date", day).put("content", note), session());
                runOnUiThread(() -> { result.accept(null); refresh(); });
            } catch (Exception e) { runOnUiThread(() -> result.accept(e.getMessage())); }
        })));
        list.addView(card, margins(14, 8));
    }

    private void emptyCard(String message) {
        TextView empty = label(message, 16, MUTED, false);
        empty.setGravity(Gravity.CENTER);
        empty.setBackground(background(Color.WHITE, 18));
        empty.setPadding(dp(18), dp(55), dp(18), dp(55));
        list.addView(empty, margins(12, 0));
    }

    private void chooseSort() {
        String[] options = memoPage ? new String[]{"최근 기록순", "오래된 기록순", "이름순", "희망 날짜순"} :
                new String[]{"최신 신청순", "오래된 신청순", "희망 날짜순"};
        new AlertDialog.Builder(this).setTitle("정렬 기준").setSingleChoiceItems(options, memoPage ? memoSort : requestSort,
                (dialog, which) -> { if (memoPage) { memoSort = which; renderMemos(); } else { requestSort = which; renderRequests(); } dialog.dismiss(); })
                .setNegativeButton("취소", null).show();
    }

    private void chooseFilter() {
        String[] options = {"전체", "새 신청", "연락 완료", "처리 완료"};
        new AlertDialog.Builder(this).setTitle("신청서 필터").setSingleChoiceItems(options, requestFilter,
                (dialog, which) -> { requestFilter = which; renderRequests(); dialog.dismiss(); })
                .setNegativeButton("취소", null).show();
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
        RequestNotes.edit(this, item, (data, result) -> io.execute(() -> {
            try {
                ApiClient.request(this, "POST", "/api/admin/native-requests", data, session());
                runOnUiThread(() -> { result.accept(null); refresh(); });
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
            card.addView(label(item.optString("name") + " 님 · " + item.optString("phone"), 16, NAVY, true));
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
                    io.execute(() -> {
                        try {
                            ApiClient.request(this, "DELETE", "/api/admin/native-requests",
                                    new JSONObject().put("id", item.optString("id")), session());
                            runOnUiThread(this::refresh);
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
        io.execute(() -> {
            try {
                ApiClient.request(this, "POST", "/api/admin/native-requests",
                        new JSONObject().put("id", id).put("status", status), session());
                runOnUiThread(this::refresh);
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
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                io.execute(() -> {
                    try {
                        JSONObject data = new JSONObject().put("name", n).put("phone", p).put("title", t).put("content", c);
                        if (existing != null) data.put("id", existing.optString("id"));
                        ApiClient.request(this, "POST", "/api/admin/native-memos", data, session());
                        runOnUiThread(() -> { dialog.dismiss(); memoPage = true; calendarPage = false; render(); });
                    } catch (Exception error) {
                        runOnUiThread(() -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true));
                        showError(error);
                    }
                });
            });
            if (existing != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view ->
                    new AlertDialog.Builder(this).setTitle("메모 삭제").setMessage("이 메모를 삭제할까요?")
                            .setNegativeButton("취소", null).setPositiveButton("삭제", (d, which) -> io.execute(() -> {
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
