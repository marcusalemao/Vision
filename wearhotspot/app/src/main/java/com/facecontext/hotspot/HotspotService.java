package com.facecontext.hotspot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

/**
 * Service foreground que mantém o hotspot ativo e o CPU acordado.
 * Impede que o Wear OS mate o processo em background.
 */
public class HotspotService extends Service {
    private static final String TAG = "WatchHotspot";
    private static final String CHANNEL_ID = "hotspot_channel";
    private static final int NOTIF_ID = 1;

    private HotspotManager hotspot;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        hotspot = new HotspotManager(this);

        // Wake lock pra manter CPU ativo
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WatchHotspot:service");
        wakeLock.acquire(24 * 60 * 60 * 1000L); // 24h max

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : "START";

        if ("START".equals(action)) {
            boolean ok = hotspot.start();
            Notification notif = buildNotification(ok);
            startForeground(NOTIF_ID, notif);

            if (!ok) {
                Log.e(TAG, "Falha ao iniciar hotspot, parando service");
                stopSelf();
            }
        } else if ("STOP".equals(action)) {
            hotspot.stop();
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            stopForeground(true);
            stopSelf();
        }

        return START_STICKY;
    }

    private Notification buildNotification(boolean active) {
        String title = active ? "📶 Hotspot ativo" : "❌ Hotspot falhou";
        String text = active ? "SSID: " + hotspot.getSSID() + " | Senha: " + hotspot.getPass() : "Verifique root";

        return new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(active)
            .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Hotspot", NotificationManager.IMPORTANCE_LOW
            );
            ch.setDescription("WatchHotspot service");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override
    public void onDestroy() {
        hotspot.stop();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
