package com.facecontext;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * HUDActivity — Display principal do FaceContext nos óculos Rokid
 *
 * Arquitetura do display:
 *   - Fundo: #000000 = transparente no waveguide (o usuário vê o mundo real)
 *   - HUD: informações flutuam sobre a realidade
 *   - Layout: 640×480 landscape fixo
 *
 * Input — 100% KeyEvents físicos (sem touchscreen):
 *   KEYCODE_POWER  (26) → curto: iniciar captura / longo: cancelar
 *   KEYCODE_BACK    (4) → dispensar HUD
 *   KEYCODE_VOLUME_UP  (24) → próximo perfil / scroll up
 *   KEYCODE_VOLUME_DOWN(25) → perfil anterior / scroll down
 *   KEYCODE_MENU   (82) → longo: exibir dicas de atalhos
 *
 * Comunicação:
 *   → Inicia CaptureService via Intent ACTION_CAPTURE
 *   ← Recebe BROADCAST_PROFILE (perfil encontrado) ou BROADCAST_ERROR
 */
public class HUDActivity extends Activity {

    private static final String TAG = "FC_HUD";

    // ── Views ──────────────────────────────────────────────────────
    private LinearLayout hudIdle;          // Estado inicial: aguardando
    private LinearLayout hudCapturing;     // Animação de captura
    private LinearLayout hudProfile;       // Card de perfil identificado
    private LinearLayout hudError;         // Erro / sem resultado
    private LinearLayout hudHints;         // Dicas de botões

    private TextView tvIdleHint;
    private TextView tvCapturingStatus;
    private TextView tvName;
    private TextView tvRole;
    private TextView tvCompany;
    private TextView tvRelation;
    private TextView tvLastSeen;
    private TextView tvMatchScore;
    private TextView tvErrorMsg;

    // ── Estado ────────────────────────────────────────────────────
    private enum HudState { IDLE, CAPTURING, PROFILE, ERROR }
    private HudState currentState = HudState.IDLE;

    // Long-press
    private static final long LONG_PRESS_MS = 600;
    private long powerPressTime = 0;
    private long menuPressTime  = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());

    // Auto-dismiss do HUD após 8 segundos
    private final Runnable autoDismissRunnable = this::showIdle;

    // ── BroadcastReceiver — resultados do CaptureService ──────────
    private final BroadcastReceiver captureReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (CaptureService.BROADCAST_PROFILE.equals(action)) {
                String json = intent.getStringExtra(CaptureService.EXTRA_PROFILE_JSON);
                showProfile(json);
            } else if (CaptureService.BROADCAST_ERROR.equals(action)) {
                String msg = intent.getStringExtra(CaptureService.EXTRA_ERROR_MSG);
                showError(msg);
            }
        }
    };

    // ─────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Tela cheia, sempre ligada, sem barra de status
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        );

        setContentView(R.layout.activity_hud);
        bindViews();
        showIdle();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(CaptureService.BROADCAST_PROFILE);
        filter.addAction(CaptureService.BROADCAST_ERROR);
        registerReceiver(captureReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(captureReceiver); } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ─── KeyEvents — todos os botões físicos do Rokid ─────────────

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {

            case KeyEvent.KEYCODE_POWER: // 26 — botão principal
                powerPressTime = System.currentTimeMillis();
                return true;

            case KeyEvent.KEYCODE_BACK: // 4 — dispensar HUD
                showIdle();
                return true;

            case KeyEvent.KEYCODE_VOLUME_UP: // 24 — próximo
                // TODO: navegar entre múltiplos perfis candidatos
                return true;

            case KeyEvent.KEYCODE_VOLUME_DOWN: // 25 — anterior
                // TODO: navegar entre múltiplos perfis candidatos
                return true;

            case KeyEvent.KEYCODE_MENU: // 82 — dicas
                menuPressTime = System.currentTimeMillis();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {

            case KeyEvent.KEYCODE_POWER: {
                long held = System.currentTimeMillis() - powerPressTime;
                if (held >= LONG_PRESS_MS) {
                    // Long press → cancelar captura em andamento
                    if (currentState == HudState.CAPTURING) {
                        stopCapture();
                        showIdle();
                    }
                } else {
                    // Short press → iniciar captura
                    if (currentState == HudState.IDLE || currentState == HudState.ERROR) {
                        startCapture();
                    }
                }
                return true;
            }

            case KeyEvent.KEYCODE_MENU: {
                long held = System.currentTimeMillis() - menuPressTime;
                if (held >= LONG_PRESS_MS) {
                    toggleHints();
                }
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    // ─── Captura ──────────────────────────────────────────────────

    private void startCapture() {
        setState(HudState.CAPTURING);
        Intent i = new Intent(this, CaptureService.class);
        i.setAction(CaptureService.ACTION_CAPTURE);
        startForegroundService(i);
    }

    private void stopCapture() {
        Intent i = new Intent(this, CaptureService.class);
        i.setAction(CaptureService.ACTION_STOP);
        startService(i);
    }

    // ─── Estados do HUD ───────────────────────────────────────────

    private void showIdle() {
        stopCapture();
        handler.removeCallbacks(autoDismissRunnable);
        setState(HudState.IDLE);
    }

    private void showProfile(String json) {
        try {
            JSONObject p = new JSONObject(json);

            // Extrai campos do perfil FaceContextPerson
            String name     = p.optString("name",             "—");
            String role     = p.optString("role",             "");
            String company  = p.optString("company",          "");
            String relation = p.optString("relation_context",
                             p.optString("relation",          ""));
            String lastSeen = p.optString("last_seen_date",   "");
            int    score    = p.optInt("match_score",          0);

            tvName.setText(name);
            tvRole.setText(role);
            tvCompany.setText(company);
            tvRelation.setText(relation);
            tvLastSeen.setText(lastSeen.isEmpty() ? "" : "Último: " + lastSeen);
            tvMatchScore.setText(score > 0 ? score + "% match" : "");
            tvMatchScore.setVisibility(score > 0 ? View.VISIBLE : View.GONE);

            setState(HudState.PROFILE);

            // Auto-dismiss após 8 segundos
            handler.removeCallbacks(autoDismissRunnable);
            handler.postDelayed(autoDismissRunnable, 8000);

        } catch (JSONException e) {
            showError("Perfil inválido");
        }
    }

    private void showError(String msg) {
        tvErrorMsg.setText(msg != null ? msg : "Não identificado");
        setState(HudState.ERROR);
        // Auto-dismiss erro após 4 segundos
        handler.removeCallbacks(autoDismissRunnable);
        handler.postDelayed(autoDismissRunnable, 4000);
    }

    private void toggleHints() {
        boolean visible = hudHints.getVisibility() == View.VISIBLE;
        hudHints.setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    // ─── Utilitários ──────────────────────────────────────────────

    private void setState(HudState state) {
        currentState = state;
        hudIdle      .setVisibility(state == HudState.IDLE       ? View.VISIBLE : View.GONE);
        hudCapturing .setVisibility(state == HudState.CAPTURING  ? View.VISIBLE : View.GONE);
        hudProfile   .setVisibility(state == HudState.PROFILE    ? View.VISIBLE : View.GONE);
        hudError     .setVisibility(state == HudState.ERROR      ? View.VISIBLE : View.GONE);
    }

    private void bindViews() {
        hudIdle       = findViewById(R.id.hud_idle);
        hudCapturing  = findViewById(R.id.hud_capturing);
        hudProfile    = findViewById(R.id.hud_profile);
        hudError      = findViewById(R.id.hud_error);
        hudHints      = findViewById(R.id.hud_hints);

        tvIdleHint        = findViewById(R.id.tv_idle_hint);
        tvCapturingStatus = findViewById(R.id.tv_capturing_status);
        tvName            = findViewById(R.id.tv_name);
        tvRole            = findViewById(R.id.tv_role);
        tvCompany         = findViewById(R.id.tv_company);
        tvRelation        = findViewById(R.id.tv_relation);
        tvLastSeen        = findViewById(R.id.tv_last_seen);
        tvMatchScore      = findViewById(R.id.tv_match_score);
        tvErrorMsg        = findViewById(R.id.tv_error_msg);
    }
}
