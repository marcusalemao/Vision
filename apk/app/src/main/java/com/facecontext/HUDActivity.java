package com.facecontext;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * FaceContext — Main HUD Activity
 *
 * Rokid AI Glasses / YodaOS
 * Display: 480 x 640 px portrait (vertical waveguide)
 * Background: #000000 = transparent on physical prism
 * Input: NO touchscreen — 100% physical temple buttons
 *
 * Button mapping:
 *   KEYCODE_POWER       (26)  → short: activate Capture Mode
 *   KEYCODE_BACK         (4)  → dismiss HUD / go back
 *   KEYCODE_MENU        (82)  → long press: open key hints
 *   KEYCODE_VOLUME_UP   (24)  → next profile / scroll up
 *   KEYCODE_VOLUME_DOWN (25)  → previous profile / scroll down
 */
public class HUDActivity extends Activity {

    // ── Views ──────────────────────────────────────────────────
    private LinearLayout hudProfile;
    private LinearLayout hudStatusBar;
    private LinearLayout hudKeyHints;
    private TextView     hudName;
    private TextView     hudRole;
    private TextView     hudCompany;
    private TextView     hudContext;
    private TextView     hudLastSeen;
    private TextView     hudContacts;
    private TextView     captureIndicator;

    // ── State ──────────────────────────────────────────────────
    private boolean captureMode  = false;
    private boolean menuVisible  = false;
    private int     currentIndex = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());

    // Long-press detection threshold (ms)
    private static final long LONG_PRESS_MS = 600;
    private long menuKeyDownAt = 0;

    // ──────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on while HUD is active
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_hud);

        hudProfile       = findViewById(R.id.hud_profile);
        hudStatusBar     = findViewById(R.id.hud_statusbar);
        hudKeyHints      = findViewById(R.id.hud_keyhints);
        hudName          = findViewById(R.id.hud_name);
        hudRole          = findViewById(R.id.hud_role);
        hudCompany       = findViewById(R.id.hud_company);
        hudContext       = findViewById(R.id.hud_context);
        hudLastSeen      = findViewById(R.id.hud_last_seen);
        hudContacts      = findViewById(R.id.hud_contacts);
        captureIndicator = findViewById(R.id.hud_capture_indicator);

        // Request focus so KeyEvents are dispatched here
        View root = findViewById(R.id.root_layout);
        root.requestFocus();
    }

    // ── Key handling — the ONLY interaction method on Rokid ───
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int  keyCode = event.getKeyCode();
        int  action  = event.getAction();

        switch (keyCode) {

            // ── ⏻ POWER — short press = Capture Mode toggle ──
            case KeyEvent.KEYCODE_POWER:
                if (action == KeyEvent.ACTION_UP) {
                    toggleCaptureMode();
                }
                return true;

            // ── ← BACK — dismiss HUD ─────────────────────────
            case KeyEvent.KEYCODE_BACK:
                if (action == KeyEvent.ACTION_UP) {
                    dismissHUD();
                }
                return true;

            // ── ☰ MENU — long press = show key hints ─────────
            case KeyEvent.KEYCODE_MENU:
                if (action == KeyEvent.ACTION_DOWN) {
                    menuKeyDownAt = System.currentTimeMillis();
                } else if (action == KeyEvent.ACTION_UP) {
                    long held = System.currentTimeMillis() - menuKeyDownAt;
                    if (held >= LONG_PRESS_MS) {
                        toggleKeyHints();
                    }
                }
                return true;

            // ── ▲ VOLUME UP — next profile ───────────────────
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (action == KeyEvent.ACTION_DOWN) {
                    navigateProfile(+1);
                }
                return true;

            // ── ▼ VOLUME DOWN — previous profile ─────────────
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == KeyEvent.ACTION_DOWN) {
                    navigateProfile(-1);
                }
                return true;
        }

        return super.dispatchKeyEvent(event);
    }

    // ── Actions ────────────────────────────────────────────────

    private void toggleCaptureMode() {
        captureMode = !captureMode;
        captureIndicator.setVisibility(captureMode ? View.VISIBLE : View.GONE);

        if (captureMode) {
            // TODO Phase 2: start CaptureService (Camera2 + mic pipeline)
            // Intent intent = new Intent(this, CaptureService.class);
            // startForegroundService(intent);
            showStatus("● CAPTURANDO");

            // Auto-cancel capture after 5 seconds if no face found
            handler.postDelayed(() -> {
                if (captureMode) toggleCaptureMode();
            }, 5000);
        } else {
            showStatus("");
        }
    }

    private void dismissHUD() {
        hudProfile.setVisibility(View.GONE);
        hudKeyHints.setVisibility(View.GONE);
        menuVisible = false;
        // Move to background — stays running for next trigger
        moveTaskToBack(true);
    }

    private void toggleKeyHints() {
        menuVisible = !menuVisible;
        hudKeyHints.setVisibility(menuVisible ? View.VISIBLE : View.GONE);

        // Auto-hide after 3 seconds
        if (menuVisible) {
            handler.postDelayed(() -> {
                menuVisible = false;
                hudKeyHints.setVisibility(View.GONE);
            }, 3000);
        }
    }

    private void navigateProfile(int direction) {
        // TODO Phase 2: load profile list from local DB (Room)
        // and cycle through matches found by CaptureService
        currentIndex = Math.max(0, currentIndex + direction);
        showStatus("Perfil #" + (currentIndex + 1));
    }

    /** Show a named person profile on the HUD overlay */
    public void showProfile(String name, String role, String company,
                             String context, String lastSeen, String contacts) {
        hudName.setText(name);
        hudRole.setText(role);
        hudCompany.setText(company);
        hudContext.setText(context);
        hudLastSeen.setText(lastSeen);
        hudContacts.setText(contacts);
        hudProfile.setVisibility(View.VISIBLE);
    }

    private void showStatus(String msg) {
        hudLastSeen.setText(msg);
    }
}
