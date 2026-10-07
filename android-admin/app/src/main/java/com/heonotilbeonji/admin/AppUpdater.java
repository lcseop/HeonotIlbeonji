package com.heonotilbeonji.admin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.ProgressBar;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Check on foreground entry; Android still asks the owner to confirm installation. */
final class AppUpdater {
    private final Activity activity;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final SharedPreferences prefs;
    private boolean checking, downloading;
    private volatile boolean closed;
    private AlertDialog progressDialog;
    private JSONObject pending;
    private static final int MAX_BYTES = 25 * 1024 * 1024;

    AppUpdater(Activity activity) {
        this.activity = activity; prefs = activity.getSharedPreferences("app_updates", Activity.MODE_PRIVATE);
        try { pending = new JSONObject(prefs.getString("pending", "")); } catch (Exception ignored) { }
    }
    void resume(boolean autoCheck) {
        if (prefs.getBoolean("waitingPermission", false)) {
            prefs.edit().putBoolean("waitingPermission", false).apply();
            if (Build.VERSION.SDK_INT < 26 || activity.getPackageManager().canRequestPackageInstalls()) installPending();
            else toast("업데이트 설치를 허용한 뒤 설정의 ‘업데이트 확인’을 눌러주세요.");
            return;
        }
        if (autoCheck && System.currentTimeMillis() - prefs.getLong("checkedAt", 0) > 6 * 60 * 60 * 1000L) check(false);
    }
    private String base() {
        return activity.getSharedPreferences("admin", Activity.MODE_PRIVATE).getString("api_base_url", BuildConfig.API_BASE_URL).replaceAll("/+$", "");
    }
    static boolean validRelease(JSONObject data) {
        return BuildConfig.APPLICATION_ID.equals(data.optString("packageName")) && data.optInt("versionCode") > 0 &&
                !data.optString("versionName").isEmpty() && data.optString("apkPath").matches("/admin-app/[a-zA-Z0-9.-]+\\.apk") &&
                data.optString("sha256").matches("[a-f0-9]{64}") && data.optLong("size") > 0 && data.optLong("size") <= MAX_BYTES;
    }
    void check(boolean manual) {
        if (closed || checking || downloading) return;
        checking = true;
        if (manual) toast("업데이트를 확인하고 있습니다.");
        io.execute(() -> {
            try {
                if (!new URL(base()).getProtocol().equals("https")) throw new Exception("업데이트는 HTTPS 홈페이지 주소에서 사용할 수 있습니다.");
                JSONObject release = ApiClient.request(activity, "GET", "/api/admin/native-update", null, null);
                if (!validRelease(release)) throw new Exception("업데이트 정보를 확인하지 못했습니다.");
                prefs.edit().putLong("checkedAt", System.currentTimeMillis()).apply();
                ui(() -> {
                    checking = false;
                    if (release.optInt("versionCode") <= BuildConfig.VERSION_CODE) { if (manual) toast("현재 최신 버전입니다. (" + BuildConfig.VERSION_NAME + ")"); return; }
                    new AlertDialog.Builder(activity).setTitle("새 업데이트 · " + release.optString("versionName"))
                            .setMessage(release.optString("releaseNotes", "앱 사용성이 개선되었습니다.") + "\n\n업데이트를 누르면 다운로드 후 설치 화면으로 이어집니다.")
                            .setNegativeButton("나중에", null).setPositiveButton("업데이트", (d, w) -> download(release)).show();
                });
            } catch (Exception error) { ui(() -> { checking = false; if (manual) toast(error.getMessage()); }); }
        });
    }
    private File apk() { return new File(new File(activity.getCacheDir(), "updates"), "update.apk"); }
    private void download(JSONObject release) {
        if (downloading || closed) return;
        downloading = true;
        LinearLayout box = AdminPlanner.column(activity); int padding = AdminPlanner.dp(activity, 24);
        box.setPadding(padding, padding, padding, padding);
        TextView text = new TextView(activity); text.setText("업데이트 다운로드 중…"); box.addView(text);
        ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100); box.addView(bar, AdminPlanner.space(activity, 12));
        progressDialog = new AlertDialog.Builder(activity).setTitle("헌옷일번지 업데이트").setView(box).setCancelable(false).create();
        progressDialog.show();
        io.execute(() -> {
            File destination = apk(); File partial = new File(destination.getParentFile(), "download.part");
            HttpURLConnection connection = null;
            try {
                if (!destination.getParentFile().isDirectory() && !destination.getParentFile().mkdirs()) throw new Exception("다운로드 공간을 준비하지 못했습니다.");
                if (!matchesFile(destination, release)) {
                    URL url = new URL(base() + release.getString("apkPath"));
                    if (!url.getProtocol().equals("https")) throw new Exception("안전한 업데이트 주소가 아닙니다.");
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(10000); connection.setReadTimeout(20000);
                    if (connection.getResponseCode() != 200) throw new Exception("업데이트를 다운로드하지 못했습니다. 잠시 후 다시 확인해 주세요.");
                    long expected = release.getLong("size"), total = 0;
                    try (var in = connection.getInputStream(); var out = new FileOutputStream(partial)) {
                        byte[] bytes = new byte[16384]; int read, previous = -1;
                        while ((read = in.read(bytes)) != -1) {
                            if (closed || Thread.currentThread().isInterrupted()) throw new Exception("다운로드를 중단했습니다. 앱에서 다시 업데이트해 주세요.");
                            total += read; if (total > expected || total > MAX_BYTES) throw new Exception("업데이트 파일 크기가 올바르지 않습니다.");
                            out.write(bytes, 0, read); int percent = (int) (total * 100 / expected);
                            if (percent != previous) { previous = percent; ui(() -> bar.setProgress(percent)); }
                        }
                    }
                    if (!matchesFile(partial, release)) throw new Exception("업데이트 파일 검증에 실패했습니다. 다시 다운로드해 주세요.");
                    if (destination.exists() && !destination.delete()) throw new Exception("이전 업데이트 파일을 정리하지 못했습니다.");
                    if (!partial.renameTo(destination)) throw new Exception("업데이트 파일을 저장하지 못했습니다.");
                }
                verifyPackage(destination, release);
                pending = release; prefs.edit().putString("pending", release.toString()).apply();
                ui(() -> { downloading = false; dismissProgress(); installPending(); });
            } catch (Exception error) { partial.delete(); ui(() -> { downloading = false; dismissProgress(); toast(error.getMessage()); }); }
            finally { if (connection != null) connection.disconnect(); }
        });
    }
    static boolean matchesFile(File file, JSONObject release) throws Exception {
        if (!file.isFile() || file.length() != release.optLong("size")) return false;
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (var input = new FileInputStream(file)) { byte[] buffer = new byte[16384]; int n; while ((n = input.read(buffer)) != -1) hash.update(buffer, 0, n); }
        StringBuilder hex = new StringBuilder(); for (byte b : hash.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return hex.toString().equals(release.optString("sha256"));
    }
    private void verifyPackage(File file, JSONObject release) throws Exception {
        PackageManager pm = activity.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo incoming = pm.getPackageArchiveInfo(file.getAbsolutePath(), flags), installed = pm.getPackageInfo(activity.getPackageName(), flags);
        if (incoming == null || !activity.getPackageName().equals(incoming.packageName) ||
                (Build.VERSION.SDK_INT >= 28 ? incoming.getLongVersionCode() : incoming.versionCode) != release.optInt("versionCode") ||
                !signers(installed).equals(signers(incoming)) || signers(incoming).isEmpty())
            throw new Exception("현재 앱과 일치하는 정식 업데이트 파일이 아닙니다.");
    }
    private static Set<String> signers(PackageInfo info) {
        var signatures = Build.VERSION.SDK_INT >= 28 ? info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners() : info.signatures;
        Set<String> result = new HashSet<>(); if (signatures != null) for (var s : signatures) result.add(s.toCharsString()); return result;
    }
    private void installPending() {
        if (pending == null || !apk().isFile()) { toast("설치할 업데이트 파일이 없습니다. 다시 확인해 주세요."); return; }
        if (pending.optInt("versionCode") <= BuildConfig.VERSION_CODE) { prefs.edit().remove("pending").apply(); pending = null; return; }
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(activity).setTitle("업데이트 설치 허용")
                    .setMessage("다음 화면에서 ‘이 출처 허용’을 켜고 뒤로 돌아오세요. 헌옷일번지 앱의 업데이트 설치에 사용됩니다.")
                    .setNegativeButton("나중에", null).setPositiveButton("설정 열기", (d, w) -> {
                        try { prefs.edit().putBoolean("waitingPermission", true).apply(); activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName()))); }
                        catch (Exception error) { prefs.edit().putBoolean("waitingPermission", false).apply(); toast("휴대폰 설정에서 이 앱의 업데이트 설치를 허용해 주세요."); }
                    }).show(); return;
        }
        // Recheck private cached files after returning from system settings.
        io.execute(() -> {
            try {
                if (!matchesFile(apk(), pending)) throw new Exception("업데이트 파일을 다시 다운로드해 주세요."); verifyPackage(apk(), pending);
                ui(() -> {
                    try {
                        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".updates", apk());
                        Intent install = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        activity.startActivity(install);
                    } catch (Exception error) { toast("설치 화면을 열지 못했습니다. 설정에서 업데이트를 다시 확인해 주세요."); }
                });
            } catch (Exception error) { ui(() -> toast(error.getMessage())); }
        });
    }
    private void ui(Runnable action) { activity.runOnUiThread(() -> { if (!closed && !activity.isFinishing() && !activity.isDestroyed()) action.run(); }); }
    private void toast(String text) { Toast.makeText(activity, text, Toast.LENGTH_LONG).show(); }
    private void dismissProgress() { if (progressDialog != null) { progressDialog.dismiss(); progressDialog = null; } }
    void close() { closed = true; dismissProgress(); io.shutdownNow(); }
}
