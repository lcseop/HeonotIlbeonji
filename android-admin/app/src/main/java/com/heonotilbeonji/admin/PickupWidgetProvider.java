package com.heonotilbeonji.admin;

import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class PickupWidgetProvider extends AppWidgetProvider {
    static final String REFRESH = "com.heonotilbeonji.admin.WIDGET_REFRESH";
    static final String REQUEST_ID = "widgetRequestId";
    static final String OPEN_HOME = "com.heonotilbeonji.admin.WIDGET_HOME";
    static final int JOB_ID = 1749;
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) { renderAll(context); requestRefresh(context); }
    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (REFRESH.equals(intent.getAction())) { renderAll(context); requestRefresh(context); }
    }
    @Override public void onDeleted(Context context, int[] ids) {
        if (widgetIds(context).length == 0) {
            JobScheduler scheduler = context.getSystemService(JobScheduler.class);
            if (scheduler != null) scheduler.cancel(JOB_ID);
        }
    }
    static int[] widgetIds(Context context) { return AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context, PickupWidgetProvider.class)); }
    static void requestRefresh(Context context) {
        if (widgetIds(context).length == 0) return;
        if (context.getSharedPreferences("admin", Context.MODE_PRIVATE).getString("session", "").isEmpty()) { PickupWidgetData.clear(context); return; }
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler != null) scheduler.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(context, PickupWidgetRefreshService.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(0).build());
    }
    static void renderAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (int id : widgetIds(context)) {
            manager.updateAppWidget(id, views(context, id));
            if (Build.VERSION.SDK_INT < 31) manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list);
        }
    }
    static RemoteViews row(Context context, JSONObject item) {
        RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.pickup_widget_row);
        boolean contacted = "contacted".equals(item.optString("status"));
        row.setTextViewText(R.id.widget_name, item.optString("name", "신청자"));
        row.setTextViewText(R.id.widget_date, PickupWidgetData.dateLabel(item.optString("date")));
        row.setTextViewText(R.id.widget_status, contacted ? "연락 완료" : "새 신청");
        row.setTextColor(R.id.widget_status, contacted ? 0xff2166ad : 0xffb53646);
        row.setImageViewResource(R.id.widget_state_icon, contacted ? R.drawable.ic_material_call : R.drawable.ic_material_schedule);
        String method = item.optString("pickupMethod");
        boolean knownMethod = "대면 수거".equals(method) || "비대면 수거".equals(method);
        row.setViewVisibility(R.id.widget_method_icon, knownMethod ? View.VISIBLE : View.GONE);
        row.setImageViewResource(R.id.widget_method_icon, "비대면 수거".equals(method) ? R.drawable.ic_material_person_off : R.drawable.ic_material_person);
        row.setContentDescription(R.id.widget_method_icon, knownMethod ? method : "");
        row.setContentDescription(R.id.widget_row, item.optString("name") + ", " + PickupWidgetData.dateLabel(item.optString("date")) + ", " + (contacted ? "연락 완료" : "새 신청") + (knownMethod ? ", " + method : ""));
        row.setOnClickFillInIntent(R.id.widget_row, new Intent().putExtra(REQUEST_ID, item.optString("id")));
        return row;
    }
    static RemoteViews views(Context context, int id) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.pickup_widget);
        JSONArray rows = PickupWidgetData.read(context);
        boolean loggedIn = !context.getSharedPreferences("admin", Context.MODE_PRIVATE).getString("session", "").isEmpty();
        views.setTextViewText(R.id.widget_count, rows.length() + "건");
        String error = PickupWidgetData.prefs(context).getString("error", "");
        views.setTextViewText(R.id.widget_empty, !loggedIn ? "앱에서 로그인해 주세요" : !error.isEmpty() ? error : "새 신청·연락 완료 신청이 없습니다");
        long updated = PickupWidgetData.prefs(context).getLong("updated", 0);
        SimpleDateFormat clock = new SimpleDateFormat("M/d HH:mm", Locale.KOREA); clock.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        String footer = !loggedIn ? "로그인 후 신청을 확인할 수 있어요" : !error.isEmpty() ? "연결 실패 · 새로고침해 주세요" : updated == 0 ? "새로고침으로 신청을 불러오세요" : "희망 날짜순 · " + clock.format(new Date(updated)) + " 갱신";
        if (rows.length() > 100) footer = "가까운 일정 100건 표시 · 앱에서 전체 보기";
        views.setTextViewText(R.id.widget_updated, footer);
        Intent app = new Intent(context, MainActivity.class).setAction(OPEN_HOME).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        views.setOnClickPendingIntent(R.id.widget_header, PendingIntent.getActivity(context, id, app, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        views.setOnClickPendingIntent(R.id.widget_empty, PendingIntent.getActivity(context, id, app, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        Intent refresh = new Intent(context, PickupWidgetProvider.class).setAction(REFRESH);
        views.setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(context, id, refresh, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        Intent template = new Intent(app).setAction("com.heonotilbeonji.admin.WIDGET_OPEN");
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        views.setPendingIntentTemplate(R.id.widget_list, PendingIntent.getActivity(context, id, template, flags));
        views.setEmptyView(R.id.widget_list, R.id.widget_empty);
        if (Build.VERSION.SDK_INT >= 31) {
            RemoteViews.RemoteCollectionItems.Builder items = new RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(false).setViewTypeCount(1);
            for (int i = 0; i < Math.min(rows.length(), 100); i++) items.addItem(i, row(context, rows.optJSONObject(i)));
            views.setRemoteAdapter(R.id.widget_list, items.build());
        } else {
            Intent adapter = new Intent(context, PickupWidgetListService.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            adapter.setData(Uri.parse("heonot-widget://list/" + id)); views.setRemoteAdapter(R.id.widget_list, adapter);
        }
        return views;
    }
}
