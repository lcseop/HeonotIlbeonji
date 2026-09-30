package com.heonotilbeonji.admin;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
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
    private int requestFilter = 0;
    private int requestSort = 0;
    private int memoSort = 0;
    private String memoSearch = "";
    private String selectedMemoPhone = "";

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
        render();
    }

    @Override public void onResume() {
        super.onResume();
        if (list != null && !session().isEmpty()) refresh();
    }

    @Override public void onDestroy() {
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
        header.setPadding(dp(18), dp(8), dp(12), dp(8));
        header.setBackgroundColor(NAVY);
        TextView brand = label("①  헌옷일번지  ·  관리자", 17, Color.WHITE, true);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        if (!session().isEmpty()) {
            Button settings = button("설정", NAVY);
            header.addView(settings, new LinearLayout.LayoutParams(dp(62), dp(38)));
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
        count = label(memoPage ? "고객 메모" : "수거 신청함", 23, NAVY, true);
        actions.addView(count);
        LinearLayout tabs = new LinearLayout(this);
        actions.addView(tabs, margins(8, 0));
        Button requestTab = button("신청서", memoPage ? MUTED : NAVY);
        Button memoTab = button("메모", memoPage ? NAVY : MUTED);
        tabs.addView(requestTab, new LinearLayout.LayoutParams(0, dp(40), 1));
        LinearLayout.LayoutParams tabGap = new LinearLayout.LayoutParams(0, dp(40), 1); tabGap.leftMargin = dp(7);
        tabs.addView(memoTab, tabGap);
        requestTab.setOnClickListener(view -> { memoPage = false; render(); });
        memoTab.setOnClickListener(view -> { memoPage = true; selectedMemoPhone = ""; render(); });
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
        filter.setOnClickListener(view -> { if (memoPage) editMemo(null, customerName(selectedMemoPhone), selectedMemoPhone); else chooseFilter(); });
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
                    memoSearch = s.toString(); selectedMemoPhone = ""; renderMemos();
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
        refresh.setOnClickListener(view -> refresh());
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
        notice.setText(memoPage ? "메모를 불러오는 중…" : "신청 내용을 불러오는 중…");
        io.execute(() -> {
            try {
                if (memoPage) {
                    JSONArray loaded = ApiClient.request(this, "GET", "/api/admin/native-memos", null, session()).getJSONArray("memos");
                    JSONArray loadedRequests = ApiClient.request(this, "GET", "/api/admin/native-requests", null, session()).getJSONArray("requests");
                    runOnUiThread(() -> { memos = loaded; requests = loadedRequests; if (memoPage) renderMemos(); });
                } else {
                    JSONArray loaded = ApiClient.request(this, "GET", "/api/admin/native-requests", null, session()).getJSONArray("requests");
                    runOnUiThread(() -> { requests = loaded; if (!memoPage) renderRequests(); });
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
        SimpleDateFormat format = new SimpleDateFormat("M월 d일 a h:mm", Locale.KOREA);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        return format.format(new Date(epoch));
    }

    private void renderRequests() {
        if (list == null) return;
        list.removeAllViews();
        int unread = 0;
        for (int i = 0; i < requests.length(); i++) if ("new".equals(requests.optJSONObject(i).optString("status"))) unread++;
        count.setText("수거 신청함  ·  새 신청 " + unread);
        ArrayList<JSONObject> visible = new ArrayList<>();
        for (int i = 0; i < requests.length(); i++) {
            JSONObject item = requests.optJSONObject(i);
            if (item == null) continue;
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
        notice.setText("표시 " + visible.size() + "건 · 신청서를 누르면 상세 내용을 볼 수 있습니다.");
        if (visible.isEmpty()) { emptyCard("해당하는 신청서가 없습니다."); return; }
        for (JSONObject item : visible) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(18), dp(17), dp(18), dp(17));
            card.setBackground(background(Color.WHITE, 17));
            String state = item.optString("status");
            String stateText = "new".equals(state) ? "새 신청" : "contacted".equals(state) ? "연락 완료" : "처리 완료";
            card.addView(label(item.optString("name") + " 님   ·   " + stateText, 18, NAVY, true));
            card.addView(label(displayDate(item.optLong("created_at")) + "   ·   " + item.optString("amount") +
                    "   ·   " + item.optString("pickupMethod", "미기재"), 13, MUTED, false), margins(8, 0));
            card.setOnClickListener(view -> showDetail(item));
            list.addView(card, margins(10, 0));
        }
    }

    private void emptyCard(String message) {
        TextView empty = label(message, 16, MUTED, false);
        empty.setGravity(Gravity.CENTER);
        empty.setBackground(background(Color.WHITE, 18));
        empty.setPadding(dp(18), dp(55), dp(18), dp(55));
        list.addView(empty, margins(12, 0));
    }

    private void chooseSort() {
        String[] options = memoPage ? new String[]{"최근 기록순", "오래된 기록순", "이름순", "제목순"} :
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

    private String customerName(String phone) {
        if (phone.isEmpty()) return "";
        String name = "고객";
        long latest = -1;
        for (int i = 0; i < requests.length(); i++) {
            JSONObject item = requests.optJSONObject(i);
            if (item != null && phone.equals(item.optString("phone")) && item.optLong("created_at") > latest) {
                latest = item.optLong("created_at"); name = item.optString("name", "고객");
            }
        }
        for (int i = 0; i < memos.length(); i++) {
            JSONObject item = memos.optJSONObject(i);
            if (item != null && phone.equals(item.optString("phone")) && item.optLong("updated_at") > latest) {
                latest = item.optLong("updated_at"); name = item.optString("name", "고객");
            }
        }
        return name;
    }

    private void renderMemos() {
        if (list == null || !memoPage) return;
        list.removeAllViews();
        if (!selectedMemoPhone.isEmpty()) { renderCustomerMemos(); return; }
        Map<String, Long> latest = new HashMap<>();
        Map<String, Long> nameTimes = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        Map<String, Integer> memoCounts = new HashMap<>();
        Map<String, String> titles = new HashMap<>();
        Set<String> matches = new HashSet<>();
        String query = memoSearch.trim().toLowerCase(Locale.KOREA);
        String digits = query.replaceAll("\\D", "");
        for (int i = 0; i < requests.length(); i++) {
            JSONObject item = requests.optJSONObject(i);
            if (item == null) continue;
            String phone = item.optString("phone");
            if (phone.isEmpty()) continue;
            long created = item.optLong("created_at");
            latest.put(phone, Math.max(latest.getOrDefault(phone, 0L), created));
            if (created >= nameTimes.getOrDefault(phone, -1L)) {
                nameTimes.put(phone, created); names.put(phone, item.optString("name", "고객"));
            }
            if (query.isEmpty() || item.optString("name").toLowerCase(Locale.KOREA).contains(query) ||
                    phone.contains(digits) && !digits.isEmpty()) matches.add(phone);
        }
        for (int i = 0; i < memos.length(); i++) {
            JSONObject item = memos.optJSONObject(i);
            if (item == null) continue;
            String phone = item.optString("phone");
            if (phone.isEmpty()) continue;
            long updated = item.optLong("updated_at");
            if (updated >= latest.getOrDefault(phone, 0L)) titles.put(phone, item.optString("title"));
            latest.put(phone, Math.max(latest.getOrDefault(phone, 0L), updated));
            if (updated >= nameTimes.getOrDefault(phone, -1L)) {
                nameTimes.put(phone, updated); names.put(phone, item.optString("name", "고객"));
            }
            memoCounts.put(phone, memoCounts.getOrDefault(phone, 0) + 1);
            String searchable = (item.optString("name") + " " + item.optString("title") + " " +
                    item.optString("content")).toLowerCase(Locale.KOREA);
            if (query.isEmpty() || searchable.contains(query) || phone.contains(digits) && !digits.isEmpty()) matches.add(phone);
        }
        ArrayList<String> phones = new ArrayList<>(matches);
        Collections.sort(phones, (a, b) -> {
            if (memoSort == 2) return names.getOrDefault(a, "고객").compareTo(names.getOrDefault(b, "고객"));
            if (memoSort == 3) return titles.getOrDefault(a, "").compareTo(titles.getOrDefault(b, ""));
            return memoSort == 1 ? Long.compare(latest.getOrDefault(a, 0L), latest.getOrDefault(b, 0L)) :
                    Long.compare(latest.getOrDefault(b, 0L), latest.getOrDefault(a, 0L));
        });
        count.setText("고객 메모  ·  " + latest.size() + "명");
        notice.setText("표시 " + phones.size() + "명 · 번호를 누르면 메모가 열립니다.");
        if (phones.isEmpty()) { emptyCard("찾는 고객이 없습니다."); return; }
        for (String phone : phones) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.setBackground(background(Color.WHITE, 15));
            card.addView(label(names.getOrDefault(phone, "고객") + "   ›", 18, NAVY, true));
            card.addView(label(phone + "  ·  메모 " + memoCounts.getOrDefault(phone, 0) + "건  ·  " +
                    displayDate(latest.getOrDefault(phone, 0L)), 12, MUTED, false), margins(4, 0));
            card.setOnClickListener(view -> { selectedMemoPhone = phone; renderMemos(); });
            list.addView(card, margins(7, 0));
        }
    }

    private void renderCustomerMemos() {
        String phone = selectedMemoPhone;
        String name = customerName(phone);
        ArrayList<JSONObject> customerMemos = new ArrayList<>();
        for (int i = 0; i < memos.length(); i++) {
            JSONObject item = memos.optJSONObject(i);
            if (item != null && phone.equals(item.optString("phone"))) customerMemos.add(item);
        }
        Collections.sort(customerMemos, (a, b) -> {
            if (memoSort == 3) return a.optString("title").compareTo(b.optString("title"));
            return memoSort == 1 ? Long.compare(a.optLong("updated_at"), b.optLong("updated_at")) :
                    Long.compare(b.optLong("updated_at"), a.optLong("updated_at"));
        });
        count.setText(name + " 님의 메모");
        notice.setText(phone + " · 메모 " + customerMemos.size() + "건");
        Button back = button("← 고객 목록", MUTED);
        list.addView(back, margins(7, 3));
        back.setOnClickListener(view -> { selectedMemoPhone = ""; renderMemos(); });
        if (customerMemos.isEmpty()) { emptyCard("아직 메모가 없습니다. 위의 '새 메모'를 눌러 작성해 주세요."); return; }
        for (JSONObject item : customerMemos) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.setBackground(background(Color.WHITE, 15));
            card.addView(label(item.optString("title"), 18, NAVY, true));
            card.addView(label(displayDate(item.optLong("updated_at")), 12, MUTED, false), margins(4, 4));
            String content = item.optString("content");
            card.addView(label(content.length() > 120 ? content.substring(0, 120) + "…" : content, 14, NAVY, false));
            card.setOnClickListener(view -> editMemo(item, "", ""));
            list.addView(card, margins(7, 0));
        }
    }

    private void showDetail(JSONObject item) {
        String name = item.optString("name");
        String phone = item.optString("phone");
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22), dp(12), dp(22), dp(12));
        detailLine(body, "전화번호", phone);
        detailLine(body, "수거 주소", item.optString("address"));
        detailLine(body, "예상 수거량", item.optString("amount"));
        detailLine(body, "희망 날짜", item.optString("date"));
        detailLine(body, "희망 시간대", item.optString("timeSlot", "미기재"));
        detailLine(body, "수거 방식", item.optString("pickupMethod", "미기재"));
        detailLine(body, "문의 내용", item.optString("message", "없음"));
        Button dial = button("전화 앱에서 번호 열기", CORAL);
        body.addView(dial, margins(20, 8));
        Button contact = button("연락처에 추가", NAVY);
        body.addView(contact, margins(0, 8));
        Button contacted = button("연락 완료로 표시", MUTED);
        body.addView(contacted, margins(0, 6));
        Button done = button("처리 완료로 표시", NAVY);
        body.addView(done, margins(0, 6));
        Button addMemo = button("이 번호로 메모 작성", NAVY);
        body.addView(addMemo, margins(0, 6));
        Button delete = button("신청서 삭제", CORAL);
        body.addView(delete, margins(0, 6));
        ScrollView detailScroll = new ScrollView(this);
        detailScroll.addView(body);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(name + " 님의 신청").setView(detailScroll)
                .setNegativeButton("닫기", null).create();
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
        addMemo.setOnClickListener(view -> { dialog.dismiss(); selectedMemoPhone = phone; editMemo(null, name, phone); });
        delete.setOnClickListener(view -> new AlertDialog.Builder(this).setTitle("신청서 삭제")
                .setMessage(name + " 님의 신청서를 삭제할까요? 삭제한 신청서는 복구할 수 없습니다.")
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
        EditText phone = memoField(fields, "휴대폰 번호", existing == null ? prefillPhone : existing.optString("phone"), false);
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
                String p = phone.getText().toString().replaceAll("\\D", "");
                String t = title.getText().toString().trim();
                String c = content.getText().toString().trim();
                if (n.isEmpty() || !p.matches("01[016789]\\d{7,8}") || t.isEmpty() || c.isEmpty()) {
                    Toast.makeText(this, "이름, 휴대폰 번호, 제목, 내용을 입력해 주세요.", Toast.LENGTH_LONG).show(); return;
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                io.execute(() -> {
                    try {
                        JSONObject data = new JSONObject().put("name", n).put("phone", p).put("title", t).put("content", c);
                        if (existing != null) data.put("id", existing.optString("id"));
                        ApiClient.request(this, "POST", "/api/admin/native-memos", data, session());
                        runOnUiThread(() -> { dialog.dismiss(); memoPage = true; selectedMemoPhone = p; render(); });
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
        parent.addView(label(title, 12, MUTED, true), margins(11, 3));
        parent.addView(label(value.isEmpty() ? "없음" : value, 15, NAVY, false));
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
