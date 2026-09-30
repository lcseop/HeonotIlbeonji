package com.heonotilbeonji.admin;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONObject;

public final class PickupMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL_ID = "pickup_requests";

    @Override public void onMessageReceived(RemoteMessage message) {
        if (!"pickup_request".equals(message.getData().get("type"))) return;

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "새 수거 신청", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("새 수거 신청이 접수되면 알려드립니다.");
            manager.createNotificationChannel(channel);
        }

        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("새 수거 신청이 도착했습니다")
                .setContentText("신청함을 열어 내용을 확인해 주세요.")
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setPriority(Notification.PRIORITY_HIGH)
                .build();
        manager.notify((int) (System.currentTimeMillis() & 0x7fffffff), notification);
    }

    @Override public void onNewToken(String token) {
        super.onNewToken(token);
        String session = getSharedPreferences("admin", MODE_PRIVATE).getString("session", "");
        if (session == null || session.isEmpty()) return;
        try {
            ApiClient.request(this, "POST", "/api/admin/native-device", new JSONObject().put("token", token), session);
        } catch (Exception ignored) {
            // The next app launch retries registration after login.
        }
    }
}
