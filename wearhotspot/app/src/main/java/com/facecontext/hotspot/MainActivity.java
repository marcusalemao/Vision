package com.facecontext.hotspot;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * UI principal — tela simples e grande para o relógio.
 * Um botão gigante de ON/OFF + status.
 */
public class MainActivity extends Activity {
    private Button toggleBtn;
    private TextView statusText;
    private TextView infoText;
    private HotspotManager hotspot;
    private Handler handler;
    private boolean checking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Mantém tela ligada
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        toggleBtn = findViewById(R.id.toggleBtn);
        statusText = findViewById(R.id.statusText);
        infoText = findViewById(R.id.infoText);

        hotspot = new HotspotManager(this);
        handler = new Handler(Looper.getMainLooper());

        updateUI();

        toggleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (hotspot.isRunning()) {
                    stopHotspot();
                } else {
                    startHotspot();
                }
            }
        });
    }

    private void startHotspot() {
        toggleBtn.setEnabled(false);
        toggleBtn.setText("⏳ Ligando...");
        statusText.setText("Verificando root...");

        // Verifica root em background
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!RootExecutor.hasRoot()) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            toggleBtn.setEnabled(true);
                            toggleBtn.setText("📶 LIGAR");
                            statusText.setText("❌ Sem root!");
                            infoText.setText("Instale Magisk primeiro");
                            Toast.makeText(MainActivity.this, "Root necessário!", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }

                // Inicia o service
                Intent intent = new Intent(MainActivity.this, HotspotService.class);
                intent.setAction("START");
                startForegroundService(intent);

                // Aguarda o service subir
                try { Thread.sleep(3000); } catch (InterruptedException ignored) {}

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateUI();
                        startStatusPolling();
                    }
                });
            }
        }).start();
    }

    private void stopHotspot() {
        toggleBtn.setEnabled(false);
        toggleBtn.setText("⏳ Desligando...");

        Intent intent = new Intent(this, HotspotService.class);
        intent.setAction("STOP");
        startService(intent);

        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

        toggleBtn.setEnabled(true);
        updateUI();
        stopStatusPolling();
    }

    private void updateUI() {
        boolean running = hotspot.isRunning();
        toggleBtn.setText(running ? "⭕ DESLIGAR" : "📶 LIGAR");
        toggleBtn.setEnabled(true);

        if (running) {
            statusText.setText("✅ Hotspot ATIVO");
            infoText.setText("SSID: " + hotspot.getSSID() + "\nSenha: " + hotspot.getPass());
        } else {
            statusText.setText("📶 Hotspot OFF");
            infoText.setText("Toque para ligar");
        }
    }

    private void startStatusPolling() {
        checking = true;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!checking || !hotspot.isRunning()) return;
                final String status = hotspot.getStatus();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        infoText.setText("SSID: " + hotspot.getSSID() + "\n" + status);
                    }
                });
                handler.postDelayed(this, 5000);
            }
        }, 5000);
    }

    private void stopStatusPolling() {
        checking = false;
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopStatusPolling();
    }
}
