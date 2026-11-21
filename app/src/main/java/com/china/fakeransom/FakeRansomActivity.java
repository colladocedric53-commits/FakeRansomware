package com.china.fakeransom;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
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
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class FakeRansomActivity extends AppCompatActivity {

    private static final long COUNTDOWN_TOTAL_SECONDS = 48L * 3600;
    private static final long EXIT_TAP_WINDOW_MS = 3000;
    private static final int EXIT_TAP_COUNT = 5;

    private TextView statusText;
    private View terminalScroll;
    private TextView terminalText;
    private TextView countdownText;
    private TextView ransomNote;
    private TextView victimInfo;
    private ProgressBar progressBar;
    private View ransomOverlay;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MediaPlayer siren;
    private Vibrator vibrator;

    private boolean simulationActive = false;
    private boolean exitUnlocked = false;
    private long secondsRemaining = COUNTDOWN_TOTAL_SECONDS;
    private long exitCountdownStart = 0;
    private int exitTaps = 0;
    private String victimId = "";
    private String sessionKey = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        setContentView(R.layout.activity_fake_ransom);

        statusText = findViewById(R.id.statusText);
        terminalScroll = findViewById(R.id.terminalScroll);
        terminalText = findViewById(R.id.terminalText);
        countdownText = findViewById(R.id.countdownText);
        ransomNote = findViewById(R.id.ransomNote);
        victimInfo = findViewById(R.id.victimInfo);
        progressBar = findViewById(R.id.progressBar);
        ransomOverlay = findViewById(R.id.ransomOverlay);

        countdownText.setOnClickListener(v -> onCountdownTapped());

        requestPermissions();
        startSiren();
        startVibrationLoop();
        enterFullscreen();
        startSimulation();
    }

    private void requestPermissions() {
        List<String> wanted = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            wanted.add(Manifest.permission.READ_MEDIA_IMAGES);
            wanted.add(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            wanted.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        wanted.add(Manifest.permission.READ_CONTACTS);
        wanted.add(Manifest.permission.REORDER_TASKS);

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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void startSimulation() {
        simulationActive = true;

        String victimId = DeviceId.secureId(this);
        String sessionKey = UUID.randomUUID().toString().toUpperCase();
        this.victimId = victimId;
        this.sessionKey = sessionKey;

        List<String> collected = new ArrayList<>();
        collected.addAll(photoNames());
        collected.addAll(downloadFileNames());
        collected.addAll(contactNames());
        if (collected.isEmpty()) {
            collected = fakeFileNames();
        }

        terminalSchedule(buildTerminalLines(collected, victimId, sessionKey));
    }

    private List<String> photoNames() {
        List<String> names = new ArrayList<>();
        try {
            String[] columns = {MediaStore.Images.Media.DISPLAY_NAME};
            Cursor c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    columns, null, null, null);
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
                Cursor c = getContentResolver().query(
                        ContactsContract.Contacts.CONTENT_URI,
                        new String[]{ContactsContract.Contacts.DISPLAY_NAME},
                        null, null, null);
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
        names.add("contacts.vcf");
        names.add("whatsapp_media.mp4");
        names.add("documents_backup.tar.gz");
        names.add("credentials.txt");
        return names;
    }

    private List<String> buildTerminalLines(List<String> collected, String victimId, String sessionKey) {
        List<String> lines = new ArrayList<>();
        lines.add("[*] initializing payload...");
        lines.add("[*] disabling sleep mode");
        lines.add("[!] root persistence: skipped (demo mode)");
        lines.add("[*] indexing /storage/emulated/0");

        for (String name : collected) {
            String type;
            if (name.contains("@")) {
                lines.add("[+] collected contact: " + name);
            } else {
                lines.add("[*] encrypting " + name + " -> " + name + ".locked (AES-256-CBC)");
            }
        }

        lines.add("[*] wiping volume shadow copies (simulated)");
        lines.add("[*] uploading session key (simulated)");
        lines.add("[*] writing victim id: " + victimId);
        lines.add("[*] session key: " + sessionKey);
        lines.add("[!] encryption complete. all " + collected.size() + " items encrypted.");
        lines.add("[!] preparing ransom note");
        return lines;
    }

    private void terminalSchedule(List<String> lines) {
        statusText.setText("ENCRYPTING SYSTEM...");
        ransomOverlay.setVisibility(View.GONE);
        ransomNote.setVisibility(View.GONE);
        countdownText.setText("");
        victimInfo.setText("");
        terminalScroll.setVisibility(View.VISIBLE);
        terminalText.setText("");

        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            final int step = i + 1;
            final boolean last = (i == lines.size() - 1);
            mainHandler.postDelayed(() -> {
                if (!simulationActive) return;
                String current = terminalText.getText().toString();
                terminalText.setText(current + line + "\n");
                progressBar.setProgress((int) (step * 100 / (float) lines.size()));
                if (last) {
                    showRansom();
                }
            }, 500 + (i * 700L));
        }
    }

    private void showRansom() {
        if (!simulationActive) return;

        ransomNote.setText("ALL YOUR FILES HAVE BEEN ENCRYPTED\n\n"
                + "Photos, videos, documents and databases on this device have been locked.\n"
                + "Without our key, recovery is mathematically impossible.\n\n"
                + "To restore your data, send 0.05 BTC to:\n"
                + "  1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVDemonstration\n\n"
                + "Send your Victim ID to: support@restoration-portal.demo\n"
                + "Payments are verified on-chain and unlock instantly.\n"
                + "DO NOT turn off the device. Attempts to remove the\n"
                + "software will corrupt the decryption guarantee.");

        victimInfo.setText("VICTIM ID: " + victimId
                + "\nSESSION KEY: " + sessionKey
                + "\nCONTACTS INDEXED: complete");

        statusText.setText("YOUR DEVICE IS LOCKED");
        terminalScroll.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        ransomNote.setVisibility(View.VISIBLE);
        ransomOverlay.setVisibility(View.VISIBLE);
        startCountdown();
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
                countdownText.setText(String.format(Locale.US, "FILES DELETED IN %02d:%02d:%02d", h, m, s));
                mainHandler.postDelayed(this, 1000);
            }
        }, 900);
    }

    private void onCountdownTapped() {
        long now = System.currentTimeMillis();
        if (now - exitCountdownStart > EXIT_TAP_WINDOW_MS) {
            exitTaps = 0;
        }
        if (exitTaps == 0) {
            exitCountdownStart = now;
        }
        exitTaps++;
        if (exitTaps >= EXIT_TAP_COUNT) {
            exitUnlocked = true;
            endSimulation();
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

    private void startSiren() {
        try {
            siren = new MediaPlayer();
            siren.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            siren.setDataSource(this, alarmUri);
            siren.setLooping(true);
            siren.prepare();
            siren.start();
        } catch (Exception e) {
            // Siren unavailable on this device profile; vibration loop still runs.
        }
    }

    private void startVibrationLoop() {
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null) return;
        long[] pattern = {0, 400, 300, 400, 1200};
        VibrationEffect effect = VibrationEffect.createWaveform(pattern, 0);
        vibrator.vibrate(effect);
    }

    private void stopSirenAndVibration() {
        if (siren != null) {
            try {
                if (siren.isPlaying()) siren.stop();
                siren.release();
            } catch (Exception ignored) {
            }
            siren = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    private void endSimulation() {
        simulationActive = false;
        mainHandler.removeCallbacksAndMessages(null);
        stopSirenAndVibration();
        Toast.makeText(this, "SIMULATION ENDED", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (simulationActive && !exitUnlocked) {
            mainHandler.postDelayed(this::returnToFront, 700);
        }
    }

    private void returnToFront() {
        if (!simulationActive || isFinishing()) return;
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            am.moveTaskToFront(getTaskId(), ActivityManager.MOVE_TASK_WITH_HOME);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSirenAndVibration();
        mainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onBackPressed() {
        Toast.makeText(this, "DEVICE LOCKED — SIMULATION IN PROGRESS", Toast.LENGTH_SHORT).show();
    }
}
