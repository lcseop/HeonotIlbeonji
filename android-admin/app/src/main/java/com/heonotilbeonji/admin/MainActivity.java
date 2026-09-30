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
import android.text.InputType;
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
import java.util.Locale;
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

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(244, 247, 249));
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
        header.setPadding(dp(20), dp(20), dp(20), dp(20));
        header.setBackgroundColor(NAVY);
        TextView brand = label("①  헌옷일번지  ·  관리자", 18, Color.WHITE, true);
        header.addView(brand);
        root.addView(header);
        if (session().isEmpty()) showLogin(); else showInbox();
    }

    private void showLogin() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), dp(36), dp(24), dp(24));
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
        actions.setPadding(dp(20), dp(25), dp(20), dp(16));
        root.addView(actions);
        actions.addView(label("ADMIN APP", 11, CORAL, true));
        count = label("수거 신청함", 30, NAVY, true);
        actions.addView(count, margins(7, 7));
        actions.addView(label("새 신청을 확인하고 바로 연락하세요.", 14, MUTED, false));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        actions.addView(row, margins(17, 0));
        Button refresh = button("새로고침", NAVY);
        Button alert = button("알림 설정", CORAL);
        Button logout = button("로그아웃", MUTED);
        row.addView(refresh, new LinearLayout.LayoutParams(0, dp(43), 1));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(0, dp(43), 1); gap.leftMargin = dp(6);
        row.addView(alert, gap);
        LinearLayout.LayoutParams gap2 = new LinearLayout.LayoutParams(0, dp(43), 1); gap2.leftMargin = dp(6);
        row.addView(logout, gap2);
        notice = label("", 13, MUTED, false);
        actions.addView(notice, margins(12, 0));
        TextView server = label("서버 주소: " + serverUrl(), 12, MUTED, false);
        actions.addView(server, margins(10, 0));
        server.setOnClickListener(view -> editServerUrl());
        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(20), 0, dp(20), dp(30));
        scroll.addView(list);
        refresh.setOnClickListener(view -> refresh());
        alert.setOnClickListener(view -> requestAlerts());
        logout.setOnClickListener(view -> {
            unregisterDevice();
            saveSession("");
            render();
        });
        refresh();
    }

    private void refresh() {
        if (list == null || session().isEmpty()) return;
        notice.setText("신청 내용을 불러오는 중…");
        io.execute(() -> {
            try {
                JSONArray requests = ApiClient.request(this, "GET", "/api/admin/native-requests", null, session()).getJSONArray("requests");
                runOnUiThread(() -> renderRequests(requests));
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

    private void renderRequests(JSONArray requests) {
        if (list == null) return;
        list.removeAllViews();
        int unread = 0;
        for (int i = 0; i < requests.length(); i++) if ("new".equals(requests.optJSONObject(i).optString("status"))) unread++;
        count.setText("수거 신청함  ·  새 신청 " + unread);
        notice.setText("신청을 누르면 연락처와 수거 내용을 볼 수 있습니다.");
        if (requests.length() == 0) {
            TextView empty = label("아직 접수된 신청이 없습니다.", 16, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setBackground(background(Color.WHITE, 18));
            empty.setPadding(dp(18), dp(55), dp(18), dp(55));
            list.addView(empty, margins(12, 0));
            return;
        }
        for (int i = 0; i < requests.length(); i++) {
            JSONObject item = requests.optJSONObject(i);
            if (item == null) continue;
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
        contacted.setOnClickListener(view -> {
            dialog.dismiss();
            io.execute(() -> {
                try {
                    ApiClient.request(this, "POST", "/api/admin/native-requests",
                            new JSONObject().put("id", item.optString("id")).put("status", "contacted"), session());
                    runOnUiThread(this::refresh);
                } catch (Exception error) { runOnUiThread(() -> Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show()); }
            });
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
