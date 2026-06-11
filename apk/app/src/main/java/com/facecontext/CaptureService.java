package com.facecontext;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * CaptureService — Background pipeline for:
 *   1. Camera2 → frame capture → face detection (Phase 3: TFLite)
 *   2. AudioRecord → speech-to-text for name capture (Phase 3)
 *   3. OCR → badge / business card reading (Phase 3)
 *
 * Triggered by KEYCODE_POWER long press in HUDActivity.
 * Declared in AndroidManifest with foregroundServiceType="camera|microphone"
 */
public class CaptureService extends Service {

    private static final String CHANNEL_ID   = "facecontext_capture";
    private static final int    NOTIF_ID     = 1001;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("FaceContext")
                .setContentText("Modo Captura ativo")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build();

        startForeground(NOTIF_ID, notification);

        // TODO Phase 3: initialize Camera2 session
        // TODO Phase 3: initialize AudioRecord / SpeechRecognizer
        // TODO Phase 3: initialize ML Kit / TFLite face detector

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // TODO Phase 3: release Camera2, AudioRecord, TFLite resources
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "FaceContext Capture",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Câmera e microfone ativos no Modo Captura");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }
}
