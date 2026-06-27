package com.facecontext;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONObject;

/**
 * CaptureService — Camera2 + Face Recognition pipeline
 *
 * Fluxo:
 *   1. HUDActivity chama startCapture() via Intent
 *   2. Camera2 abre, espera AE/AF estabilizar (1.5s)
 *   3. Captura 1 frame YUV_420_888 → converte para JPEG
 *   4. Envia JPEG para API FaceContext (Base44 backend) via HTTPS
 *   5. API retorna perfil JSON → broadcast para HUDActivity exibir
 *
 * Lição aprendida da comunidade (RokidGlassAI):
 *   - YUV_420_888 é mais estável que JPEG direto no hardware Rokid
 *   - Usar AtomicBoolean readyToCapture para evitar processar frames de preview
 *   - ByteArrayOutputStream ao invés de List<Byte> para buffer eficiente
 */
public class CaptureService extends Service {

    private static final String TAG = "FC_Capture";
    private static final String CHANNEL_ID = "facecontext_capture";
    private static final int NOTIF_ID = 1001;

    // Intents
    public static final String ACTION_CAPTURE   = "com.facecontext.CAPTURE";
    public static final String ACTION_STOP       = "com.facecontext.STOP_CAPTURE";
    public static final String BROADCAST_PROFILE = "com.facecontext.PROFILE_FOUND";
    public static final String BROADCAST_ERROR   = "com.facecontext.CAPTURE_ERROR";
    public static final String EXTRA_PROFILE_JSON = "profile_json";
    public static final String EXTRA_ERROR_MSG    = "error_msg";

    // API endpoint — Base44 backend proxy
    private static final String API_URL =
        "https://base44.app/api/apps/6a11083db49430b410a8c066/functions/fcRecognize";

    // Camera
    private CameraManager      cameraManager;
    private CameraDevice       cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader        imageReader;
    private HandlerThread      cameraThread;
    private Handler            cameraHandler;

    // Captura
    private final AtomicBoolean readyToCapture = new AtomicBoolean(false);
    private final AtomicBoolean capturing      = new AtomicBoolean(false);

    // HTTP
    private OkHttpClient httpClient;

    // ─────────────────────────────────────────────────────────────
    @Override
    public void onCreate() {
        super.onCreate();
        httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();

        if (ACTION_CAPTURE.equals(action)) {
            if (!capturing.get()) {
                startForegroundNotification();
                startCapturePipeline();
            }
        } else if (ACTION_STOP.equals(action)) {
            stopCapturePipeline();
            stopForeground(true);
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        stopCapturePipeline();
        super.onDestroy();
    }

    // ─── Camera pipeline ──────────────────────────────────────────

    private void startCapturePipeline() {
        capturing.set(true);
        readyToCapture.set(false);

        cameraThread = new HandlerThread("FC_CameraThread");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());

        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);

        try {
            String cameraId = getBackCameraId();
            if (cameraId == null) {
                broadcastError("Câmera traseira não encontrada");
                return;
            }

            // ImageReader — YUV_420_888 mais estável que JPEG no hardware Rokid
            // (lição aprendida: RokidGlassAI / Medium article)
            imageReader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 3);
            imageReader.setOnImageAvailableListener(imageAvailableListener, cameraHandler);

            cameraManager.openCamera(cameraId, cameraStateCallback, cameraHandler);

        } catch (CameraAccessException | SecurityException e) {
            Log.e(TAG, "Erro ao abrir câmera: " + e.getMessage());
            broadcastError("Permissão de câmera negada");
        }
    }

    private void stopCapturePipeline() {
        capturing.set(false);
        readyToCapture.set(false);
        try {
            if (captureSession != null) { captureSession.close(); captureSession = null; }
            if (cameraDevice  != null) { cameraDevice.close();   cameraDevice  = null; }
            if (imageReader   != null) { imageReader.close();    imageReader   = null; }
        } catch (Exception e) { Log.w(TAG, "Erro ao fechar câmera: " + e.getMessage()); }
        if (cameraThread != null) { cameraThread.quitSafely(); cameraThread = null; }
    }

    // ─── Camera callbacks ─────────────────────────────────────────

    private final CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            startPreviewSession();
        }
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close(); cameraDevice = null;
        }
        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close(); cameraDevice = null;
            broadcastError("Camera2 erro: " + error);
        }
    };

    private void startPreviewSession() {
        try {
            CaptureRequest.Builder previewBuilder =
                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewBuilder.addTarget(imageReader.getSurface());

            cameraDevice.createCaptureSession(
                Arrays.asList(imageReader.getSurface()),
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        captureSession = session;
                        try {
                            CaptureRequest request = previewBuilder.build();
                            session.setRepeatingRequest(request, null, cameraHandler);
                            // Aguarda AE/AF estabilizar antes de capturar
                            // (lição aprendida: evita 168 frames dropados)
                            cameraHandler.postDelayed(() -> readyToCapture.set(true), 1500);
                        } catch (CameraAccessException e) {
                            broadcastError("Sessão de câmera falhou");
                        }
                    }
                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        broadcastError("Configuração de câmera falhou");
                    }
                },
                cameraHandler
            );
        } catch (CameraAccessException e) {
            broadcastError("Preview falhou: " + e.getMessage());
        }
    }

    // ─── ImageReader — só processa quando readyToCapture=true ─────

    private final ImageReader.OnImageAvailableListener imageAvailableListener =
        reader -> {
            Image image = reader.acquireLatestImage();
            if (image == null) return;

            if (readyToCapture.getAndSet(false)) {
                // Converte YUV → JPEG e envia para API
                byte[] jpeg = yuvToJpeg(image);
                image.close();
                if (jpeg != null) sendToFaceApi(jpeg);
            } else {
                // Frame de preview — descarta silenciosamente
                image.close();
            }
        };

    // ─── YUV → JPEG (lição aprendida: ByteArrayOutputStream eficiente) ──

    private byte[] yuvToJpeg(Image image) {
        try {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer yBuf  = planes[0].getBuffer();
            ByteBuffer uBuf  = planes[1].getBuffer();
            ByteBuffer vBuf  = planes[2].getBuffer();

            int ySize = yBuf.remaining();
            int uSize = uBuf.remaining();
            int vSize = vBuf.remaining();

            // ByteArrayOutputStream — O(n) ao invés de O(n²) com List<Byte>
            ByteArrayOutputStream nv21Stream = new ByteArrayOutputStream(ySize + uSize + vSize);
            byte[] yBytes = new byte[ySize];
            byte[] uBytes = new byte[uSize];
            byte[] vBytes = new byte[vSize];
            yBuf.get(yBytes);
            uBuf.get(uBytes);
            vBuf.get(vBytes);

            // NV21: Y + interleaved VU
            nv21Stream.write(yBytes);
            for (int i = 0; i < uSize; i++) {
                nv21Stream.write(vBytes[i]);
                nv21Stream.write(uBytes[i]);
            }

            byte[] nv21 = nv21Stream.toByteArray();
            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
                image.getWidth(), image.getHeight(), null);

            ByteArrayOutputStream jpegOut = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(
                new Rect(0, 0, image.getWidth(), image.getHeight()),
                80, jpegOut);

            return jpegOut.toByteArray();

        } catch (Exception e) {
            Log.e(TAG, "YUV→JPEG falhou: " + e.getMessage());
            return null;
        }
    }

    // ─── Envia JPEG para API de reconhecimento ────────────────────
    //
    // Fluxo (2 fases):
    //   1. Envia image_b64 → API responde needs_client_embedding=true
    //      (o servidor Deno não tem TFLite para extrair descriptor de JPEG)
    //   2. TODO Fase 3: integrar TFLite no APK para extrair descriptor
    //      localmente e enviar { descriptor: [128 floats] } direto
    //
    // Por ora a API retorna needs_client_embedding e o HUD mostra
    // "Identifique no Manager" — funcional para validação do cabo.

    private void sendToFaceApi(byte[] jpeg) {
        try {
            String b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP);

            JSONObject body = new JSONObject();
            body.put("image_b64", b64);

            RequestBody requestBody = RequestBody.create(
                MediaType.parse("application/json"),
                body.toString()
            );

            Request request = new Request.Builder()
                .url(API_URL)
                .post(requestBody)
                .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.w(TAG, "API offline: " + e.getMessage());
                    broadcastError("API offline");
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        broadcastError("API erro: " + response.code());
                        return;
                    }
                    try {
                        String raw = response.body() != null ? response.body().string() : "{}";
                        JSONObject json = new JSONObject(raw);

                        if (json.optBoolean("needs_client_embedding", false)) {
                            // Fase 3: APK vai extrair descriptor com TFLite
                            // Por ora, avisa que precisa do emulador ou Manager
                            broadcastError("Foto capturada — identifique no Manager");
                            stopCapturePipeline();
                            return;
                        }

                        if (json.optBoolean("match", false)) {
                            // Match encontrado — repassa o JSON completo pro HUD
                            broadcastProfile(raw);
                        } else {
                            broadcastError("Não identificado");
                        }
                        stopCapturePipeline();

                    } catch (Exception e) {
                        broadcastError("Resposta inválida");
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Erro ao montar requisição: " + e.getMessage());
            broadcastError("Erro interno");
        }
    }

    // ─── Broadcasts → HUDActivity ─────────────────────────────────

    private void broadcastProfile(String profileJson) {
        Intent i = new Intent(BROADCAST_PROFILE);
        i.putExtra(EXTRA_PROFILE_JSON, profileJson);
        sendBroadcast(i);
    }

    private void broadcastError(String msg) {
        Log.w(TAG, "Erro captura: " + msg);
        Intent i = new Intent(BROADCAST_ERROR);
        i.putExtra(EXTRA_ERROR_MSG, msg);
        sendBroadcast(i);
        capturing.set(false);
    }

    // ─── Helpers ──────────────────────────────────────────────────

    private String getBackCameraId() throws CameraAccessException {
        for (String id : cameraManager.getCameraIdList()) {
            CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id;
            }
        }
        // Fallback: primeira câmera disponível
        String[] ids = cameraManager.getCameraIdList();
        return ids.length > 0 ? ids[0] : null;
    }

    private void startForegroundNotification() {
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("FaceContext")
            .setContentText("Identificando pessoa...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build();
        startForeground(NOTIF_ID, notification);
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "FaceContext Capture",
            NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Câmera ativa durante identificação");
        NotificationManager mgr = getSystemService(NotificationManager.class);
        if (mgr != null) mgr.createNotificationChannel(channel);
    }
}
