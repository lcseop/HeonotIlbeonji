package com.heonotilbeonji.admin;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.json.JSONArray;

public final class PickupWidgetRefreshService extends JobService {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Future<?> work;
    @Override public boolean onStartJob(JobParameters job) {
        work = io.submit(() -> {
            String token = getSharedPreferences("admin", Context.MODE_PRIVATE).getString("session", "");
            String server = getSharedPreferences("admin", Context.MODE_PRIVATE).getString("api_base_url", BuildConfig.API_BASE_URL);
            long revision = PickupWidgetData.prefs(this).getLong("revision", 0);
            try {
                if (token.isEmpty() || !PickupWidgetProvider.hasConsumers(this)) { PickupWidgetProvider.stopAutomaticRefresh(this); PickupWidgetData.clear(this); return; }
                PickupWidgetProvider.ensureAutomaticRefresh(this);
                JSONArray rows = ApiClient.request(this, "GET", "/api/admin/native-requests", null, token).getJSONArray("requests");
                synchronized (PickupWidgetData.class) {
                    if (current(token, server, revision) && !Thread.currentThread().isInterrupted()) PickupWidgetData.store(this, rows);
                }
            } catch (Exception error) {
                synchronized (PickupWidgetData.class) {
                    if (current(token, server, revision) && !Thread.currentThread().isInterrupted()) {
                        if (error instanceof ApiClient.ApiException && ((ApiClient.ApiException) error).status == 401) {
                            PickupWidgetData.clear(this);
                            PickupWidgetData.prefs(this).edit().putString("error", "앱에서 다시 로그인해 주세요").apply();
                        } else PickupWidgetData.prefs(this).edit().putString("error", "연결을 확인한 뒤 새로고침해 주세요").apply();
                        PickupWidgetProvider.renderAll(this);
                    }
                }
            } finally { jobFinished(job, false); }
        });
        return true;
    }
    private boolean current(String token, String server, long revision) {
        return token.equals(getSharedPreferences("admin", Context.MODE_PRIVATE).getString("session", ""))
            && server.equals(getSharedPreferences("admin", Context.MODE_PRIVATE).getString("api_base_url", BuildConfig.API_BASE_URL))
            && revision == PickupWidgetData.prefs(this).getLong("revision", 0);
    }
    @Override public boolean onStopJob(JobParameters job) { if (work != null) work.cancel(true); return true; }
    @Override public void onDestroy() { io.shutdownNow(); super.onDestroy(); }
}
