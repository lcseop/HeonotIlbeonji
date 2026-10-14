package com.heonotilbeonji.admin;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.widget.Toast;
import org.json.JSONArray;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class PickupEdgeService extends Service {
    static final String STOP = "com.heonotilbeonji.admin.EDGE_STOP";
    private static volatile PickupEdgeService active;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private WindowManager windows;
    private View handle;
    private FrameLayout drawer;
    private LinearLayout list;
    private TextView notice;
    private ProgressBar progress;
    private WindowManager.LayoutParams handleParams;
    private boolean started, loading, registered;
    private volatile boolean destroyed;
    private final Runnable autoRefresh = new Runnable() {
        public void run() { if (destroyed) return; refreshRequests(); main.postDelayed(this, 5 * 60 * 1000L); }
    };
    private final Runnable permissionCheck = new Runnable() {
        public void run() {
            if (destroyed) return;
            if (!available(PickupEdgeService.this) || session().isEmpty()) { stopSelf(); return; }
            updateLockVisibility(); main.postDelayed(this, 30000);
        }
    };
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) { closeDrawer(); if (handle != null) handle.setVisibility(View.GONE); }
            else updateLockVisibility();
        }
    };
    static boolean enabled(Context context) { return context.getSharedPreferences("admin", MODE_PRIVATE).getBoolean("edge_enabled", false); }
    static boolean available(Context context) { return enabled(context) && Settings.canDrawOverlays(context); }
    static void enable(Context context) {
        context.getSharedPreferences("admin", MODE_PRIVATE).edit().putBoolean("edge_enabled", true).apply();
        resume(context);
    }
    static void resume(Context context) {
        if (!available(context) || context.getSharedPreferences("admin", MODE_PRIVATE).getString("session", "").isEmpty()) return;
        if (active != null) { dataChanged(); return; }
        try {
            Intent intent = new Intent(context, PickupEdgeService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent); else context.startService(intent);
        } catch (RuntimeException error) { Toast.makeText(context, "사이드 패널을 시작하지 못했습니다. 앱을 다시 열어 주세요.", Toast.LENGTH_LONG).show(); }
    }
    static void stop(Context context, boolean disable) {
        if (disable) context.getSharedPreferences("admin", MODE_PRIVATE).edit().putBoolean("edge_enabled", false).apply();
        context.stopService(new Intent(context, PickupEdgeService.class));
        if (!PickupWidgetProvider.hasConsumers(context)) PickupWidgetProvider.stopAutomaticRefresh(context);
    }
    static void dataChanged() {
        PickupEdgeService service = active;
        if (service != null) service.main.post(() -> {
            if (service.destroyed) return;
            if (service.session().isEmpty()) service.stopSelf(); else service.renderRows();
        });
    }
    private String session() { return getSharedPreferences("admin", MODE_PRIVATE).getString("session", ""); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape;
    }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view;
    }
    @Override public void onCreate() { super.onCreate(); active = this; windows = getSystemService(WindowManager.class); }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) { stop(this, true); return START_NOT_STICKY; }
        if (!available(this) || session().isEmpty()) { stopSelf(); return START_NOT_STICKY; }
        try {
            showNotification();
            if (!started) {
                started = true; addHandle();
                IntentFilter filter = new IntentFilter(); filter.addAction(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_SCREEN_ON); filter.addAction(Intent.ACTION_USER_PRESENT);
                if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(screen, filter);
                registered = true; main.post(permissionCheck); main.post(autoRefresh);
            }
        } catch (RuntimeException error) {
            Toast.makeText(this, "다른 앱 위에 표시 권한을 확인해 주세요.", Toast.LENGTH_LONG).show(); stopSelf(); return START_NOT_STICKY;
        }
        return START_STICKY;
    }
    private void showNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        String channel = "pickup_edge";
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(channel, "수거 신청 사이드 패널", NotificationManager.IMPORTANCE_LOW));
        Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent app = PendingIntent.getActivity(this, 1760, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1761, new Intent(this, PickupEdgeService.class).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, channel) : new Notification.Builder(this);
        Notification notification = builder.setSmallIcon(R.drawable.ic_notification).setContentTitle("수거 신청 사이드 패널")
            .setContentText("오른쪽 손잡이를 끌면 신청 목록이 열립니다.").setContentIntent(app).setOngoing(true)
            .addAction(new Notification.Action.Builder(R.drawable.ic_material_close, "패널 끄기", stop).build()).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(1760, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE); else startForeground(1760, notification);
    }
    private WindowManager.LayoutParams params(int width, int height) {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(width, height,
            Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.RIGHT; return p;
    }
    private void addHandle() {
        TextView grip = text("‹", 28, Color.WHITE); grip.setGravity(Gravity.CENTER); grip.setBackground(background(0xff18334e, 12));
        grip.setContentDescription("수거 신청 패널 열기. 왼쪽으로 끌거나 누르세요. 위아래로 이동할 수 있습니다.");
        handle = grip; handleParams = params(dp(24), dp(96));
        int height = getResources().getDisplayMetrics().heightPixels;
        handleParams.y = Math.round(height * getSharedPreferences("admin", MODE_PRIVATE).getFloat("edge_y", .35f));
        windows.addView(handle, handleParams);
        grip.setOnClickListener(view -> openDrawer());
        grip.setOnTouchListener(new View.OnTouchListener() {
            float x, y; int initialY; boolean moved, opened;
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) { x = event.getRawX(); y = event.getRawY(); initialY = handleParams.y; moved = false; opened = false; return true; }
                float dx = event.getRawX() - x, dy = event.getRawY() - y;
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    if (!opened && dx < -dp(24) && Math.abs(dx) > Math.abs(dy)) { opened = true; openDrawer(); }
                    else if (!opened && Math.abs(dy) > dp(8)) {
                        moved = true; int screenHeight = getResources().getDisplayMetrics().heightPixels;
                        handleParams.y = Math.max(dp(40), Math.min(screenHeight - dp(160), initialY + Math.round(dy)));
                        windows.updateViewLayout(handle, handleParams);
                    }
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    if (!moved && !opened) view.performClick();
                    if (moved) getSharedPreferences("admin", MODE_PRIVATE).edit().putFloat("edge_y", handleParams.y / (float)getResources().getDisplayMetrics().heightPixels).apply();
                    return true;
                }
                return true;
            }
        });
    }
    private ImageView iconButton(int icon, String label, Runnable click) {
        ImageView view = new ImageView(this); view.setImageResource(icon); view.setColorFilter(0xff18334e); view.setPadding(dp(12),dp(12),dp(12),dp(12));
        view.setContentDescription(label); view.setOnClickListener(v -> click.run()); return view;
    }
    private boolean locked() { KeyguardManager manager = getSystemService(KeyguardManager.class); return manager != null && manager.isKeyguardLocked(); }
    private void updateLockVisibility() {
        if (handle == null) return;
        if (locked()) { closeDrawer(); handle.setVisibility(View.GONE); } else handle.setVisibility(View.VISIBLE);
    }
    private void openDrawer() {
        if (destroyed || locked() || session().isEmpty() || drawer != null) return;
        drawer = new FrameLayout(this); drawer.setBackgroundColor(0x66000000); drawer.setOnClickListener(v -> closeDrawer());
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16),dp(12),dp(16),dp(12)); card.setBackground(background(0xfffafcfe,22)); card.setOnClickListener(v -> { });
        int width = Math.min(dp(360), getResources().getDisplayMetrics().widthPixels - dp(32));
        int height = Math.min(dp(640), getResources().getDisplayMetrics().heightPixels - dp(112));
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(width,height,Gravity.RIGHT | Gravity.CENTER_VERTICAL); cardParams.rightMargin = dp(8); drawer.addView(card,cardParams);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); card.addView(header);
        TextView title = text("헌옷일번지",18,0xff18334e); title.setTypeface(null,1); title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        progress = new ProgressBar(this); progress.setContentDescription("신청 목록 로딩 중"); progress.setVisibility(View.GONE);
        header.addView(progress,new LinearLayout.LayoutParams(dp(20),dp(20)));
        header.addView(iconButton(R.drawable.ic_material_refresh,"목록 새로고침",this::refreshRequests),new LinearLayout.LayoutParams(dp(44),dp(44)));
        header.addView(iconButton(R.drawable.ic_material_close,"패널 닫기",this::closeDrawer),new LinearLayout.LayoutParams(dp(44),dp(44)));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); card.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list);
        notice = text("",12,0xff43566a); notice.setPadding(0,dp(8),0,dp(8)); card.addView(notice);
        TextView app = text("앱에서 전체 신청 보기  ›",15,0xff2166ad); app.setGravity(Gravity.CENTER); app.setMinHeight(dp(48)); app.setOnClickListener(v -> openApp("")); card.addView(app);
        try { windows.addView(drawer,params(-1,-1)); renderRows(); if (handle != null) handle.setVisibility(View.GONE); refreshRequests(); }
        catch (RuntimeException error) { closeDrawer(); stopSelf(); }
    }
    private void closeDrawer() {
        if (drawer != null) { try { windows.removeView(drawer); } catch (RuntimeException ignored) { } drawer = null; list = null; notice = null; progress = null; }
        if (handle != null && !locked()) handle.setVisibility(View.VISIBLE);
    }
    private void openApp(String id) {
        closeDrawer();
        Intent open = new Intent(this,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (id.isEmpty()) open.setAction(PickupWidgetProvider.OPEN_HOME); else open.putExtra(PickupWidgetProvider.REQUEST_ID,id);
        try { startActivity(open); } catch (RuntimeException error) { Toast.makeText(this,"앱을 다시 열어 주세요.",Toast.LENGTH_SHORT).show(); }
    }
    private void renderRows() {
        if (list == null || notice == null) return;
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        list.removeAllViews(); JSONArray rows = PickupWidgetData.read(this);
        if (rows.length() == 0) { TextView empty = text("새 신청·연락 완료 신청이 없습니다.",15,0xff43566a); empty.setPadding(dp(8),dp(30),dp(8),dp(30)); list.addView(empty); }
        for (int i=0;i<rows.length();i++) {
            var item = rows.optJSONObject(i); if (item == null) continue;
            View row = PickupWidgetProvider.row(this,item).apply(this,new FrameLayout(this));
            row.setBackground(background(Color.WHITE,12)); row.setOnClickListener(v -> openApp(item.optString("id")));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.bottomMargin = dp(8); list.addView(row,p);
        }
        SimpleDateFormat time = new SimpleDateFormat("M/d HH:mm",Locale.KOREA); time.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        long updated = PickupWidgetData.prefs(this).getLong("updated",0);
        String error = PickupWidgetData.prefs(this).getString("error","");
        notice.setText(loading ? "신청을 불러오는 중…" : !error.isEmpty() ? error : "희망 날짜순 · " + rows.length()+"건"+(updated>0 ? " · "+time.format(new Date(updated))+" 갱신" : ""));
    }
    private void refreshRequests() {
        if (loading || destroyed || session().isEmpty()) return;
        loading = true; renderRows();
        String token=session(); String server=getSharedPreferences("admin",MODE_PRIVATE).getString("api_base_url",BuildConfig.API_BASE_URL);
        long revision=PickupWidgetData.prefs(this).getLong("revision",0);
        io.execute(() -> {
            boolean expired=false;
            try {
                JSONArray rows=ApiClient.request(this,"GET","/api/admin/native-requests",null,token).getJSONArray("requests");
                synchronized(PickupWidgetData.class) { if(current(token,server,revision)) PickupWidgetData.store(this,rows); }
            } catch(Exception error) {
                synchronized(PickupWidgetData.class) { if(current(token,server,revision)) {
                    expired=error instanceof ApiClient.ApiException && ((ApiClient.ApiException)error).status==401;
                    if(expired) PickupWidgetData.clear(this);
                    PickupWidgetData.prefs(this).edit().putString("error",expired?"앱에서 다시 로그인해 주세요":"연결을 확인해 주세요. 다시 자동 갱신됩니다.").apply();
                    PickupWidgetProvider.renderAll(this);
                } }
            } finally {
                boolean logout=expired;
                main.post(() -> { if(destroyed)return; loading=false; if(logout)stopSelf(); else renderRows(); });
            }
        });
    }
    private boolean current(String token,String server,long revision) {
        return !destroyed && !Thread.currentThread().isInterrupted() && token.equals(session())
            && server.equals(getSharedPreferences("admin",MODE_PRIVATE).getString("api_base_url",BuildConfig.API_BASE_URL)) && revision==PickupWidgetData.prefs(this).getLong("revision",0);
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config); closeDrawer();
        if(handle != null) {
            handleParams.y=Math.max(dp(40),Math.min(getResources().getDisplayMetrics().heightPixels-dp(160),handleParams.y));
            try { windows.updateViewLayout(handle,handleParams); } catch(RuntimeException ignored) { stopSelf(); }
        }
    }
    @Override public void onDestroy() {
        destroyed=true; if(active==this)active=null; main.removeCallbacksAndMessages(null); io.shutdownNow(); closeDrawer();
        if(handle != null) { try { windows.removeView(handle); } catch(RuntimeException ignored) { } handle=null; }
        if(registered) unregisterReceiver(screen); stopForeground(true); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
