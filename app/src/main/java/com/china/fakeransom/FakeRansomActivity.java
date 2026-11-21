package com.china.fakeransom;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class FakeRansomActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST = 4102;
    private TextView statusText, terminalText, countdownText, ransomNote, victimInfo;
    private ProgressBar progressBar;
    private Button startButton, soundButton, exitButton;
    private AudioTrack audioTrack;
    private boolean soundEnabled = true;
    private boolean running = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fake_ransom);

        statusText = findViewById(R.id.statusText);
        terminalText = findViewById(R.id.terminalText);
        countdownText = findViewById(R.id.countdownText);
        ransomNote = findViewById(R.id.ransomNote);
        victimInfo = findViewById(R.id.victimInfo);
        progressBar = findViewById(R.id.progressBar);
        startButton = findViewById(R.id.startButton);
        soundButton = findViewById(R.id.soundButton);
        exitButton = findViewById(R.id.exitButton);

        startButton.setOnClickListener(v -> requestDemoPermissions());
        soundButton.setOnClickListener(v -> {
            soundEnabled = !soundEnabled;
            soundButton.setText(soundEnabled ? "SOUND: ON" : "SOUND: OFF");
            if (soundEnabled && running) startWarningAudio();
            else stopWarningAudio();
        });
        exitButton.setOnClickListener(v -> finish());
        statusText.setText("SECURITY AWARENESS SIMULATION");
        countdownText.setText("DEMO MODE • NO FILES WILL BE CHANGED");
    }

    private void requestDemoPermissions() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_CONTACTS);
        }

        if (needed.isEmpty()) {
            beginDemo();
        } else {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) beginDemo();
    }

    private void beginDemo() {
        if (running) return;
        running = true;
        startButton.setEnabled(false);
        statusText.setText("SIMULATING A RANSOMWARE INCIDENT");
        countdownText.setText("SIMULATION ONLY • NO ENCRYPTION OR DELETION");
        terminalText.setText("");
        progressBar.setProgress(0);

        List<String> lines = new ArrayList<>();
        lines.add("[DEMO] Starting local security awareness exercise");
        lines.add("[DEMO] Checking granted read-only permissions");
        addPhotoNames(lines);
        addDownloadNames(lines);
        addContactNames(lines);
        lines.add("[SIMULATED] File encryption stage would begin here");
        lines.add("[SIMULATED] No file was renamed, encrypted, or modified");
        lines.add("[SIMULATED] No data was uploaded or transmitted");
        lines.add("[SAFE] Demonstration complete");

        if (soundEnabled) startWarningAudio();
        for (int i = 0; i < lines.size(); i++) {
            final int step = i + 1;
            final String line = lines.get(i);
            terminalText.postDelayed(() -> {
                if (isFinishing()) return;
                terminalText.append(line + "\n");
                progressBar.setProgress(step * 100 / lines.size());
                if (step == lines.size()) finishDemo();
            }, i * 650L);
        }
    }

    private void addPhotoNames(List<String> lines) {
        if (ContextCompat.checkSelfPermission(this, Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            lines.add("[SKIPPED] Photo names: permission not granted");
            return;
        }
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Images.Media.DISPLAY_NAME}, null, null,
                    MediaStore.Images.Media.DATE_ADDED + " DESC");
            int count = 0;
            while (cursor != null && cursor.moveToNext() && count < 8) {
                String name = cursor.getString(0);
                if (name != null) lines.add("[READ-ONLY PHOTO NAME] " + name);
                count++;
            }
            if (count == 0) lines.add("[INFO] No photo names available");
        } catch (Exception e) {
            lines.add("[INFO] Photo listing unavailable on this Android version");
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private void addDownloadNames(List<String> lines) {
        if (Build.VERSION.SDK_INT >= 33) {
            lines.add("[INFO] Download names are not accessed on Android 13+");
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            lines.add("[SKIPPED] Download names: permission not granted");
            return;
        }
        try {
            File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File[] files = folder.listFiles();
            int count = 0;
            if (files != null) {
                for (File file : files) {
                    if (count >= 8) break;
                    if (file.isFile()) {
                        lines.add("[READ-ONLY DOWNLOAD NAME] " + file.getName());
                        count++;
                    }
                }
            }
            if (count == 0) lines.add("[INFO] No download names available");
        } catch (Exception e) {
            lines.add("[INFO] Download listing unavailable");
        }
    }

    private void addContactNames(List<String> lines) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            lines.add("[SKIPPED] Contact names: permission not granted");
            return;
        }
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
                    new String[]{ContactsContract.Contacts.DISPLAY_NAME}, null, null, null);
            int count = 0;
            while (cursor != null && cursor.moveToNext() && count < 8) {
                String name = cursor.getString(0);
                if (name != null) lines.add("[READ-ONLY CONTACT NAME] " + name);
                count++;
            }
            if (count == 0) lines.add("[INFO] No contact names available");
        } catch (Exception e) {
            lines.add("[INFO] Contact listing unavailable");
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private void finishDemo() {
        stopWarningAudio();
        statusText.setText("SIMULATION COMPLETE");
        countdownText.setText("NO FILES WERE ENCRYPTED");
        ransomNote.setText("TRAINING RESULT\n\nThis screen imitated a ransomware incident.\nYour files and contacts were only listed by name when you granted permission.\nNo file contents were opened. Nothing was changed, deleted, encrypted, or sent.");
        ransomNote.setVisibility(View.VISIBLE);
        victimInfo.setText("DEMO SESSION: " + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.US));
        startButton.setText("RUN AGAIN");
        startButton.setEnabled(true);
        startButton.setOnClickListener(v -> {
            running = false;
            ransomNote.setVisibility(View.GONE);
            victimInfo.setText("");
            beginDemo();
        });
    }

    private void startWarningAudio() {
        stopWarningAudio();
        try {
            final int sampleRate = 22050;
            final int sampleCount = sampleRate * 2;
            short[] samples = new short[sampleCount];
            for (int i = 0; i < sampleCount; i++) {
                double t = i / (double) sampleRate;
                double pulse = 0.45 + 0.55 * Math.sin(2.0 * Math.PI * 1.4 * t);
                double sweep = Math.sin(2.0 * Math.PI * (420.0 + 180.0 * Math.sin(2.0 * Math.PI * 0.5 * t)) * t);
                double low = Math.sin(2.0 * Math.PI * 110.0 * t);
                samples[i] = (short) (Math.max(-1.0, Math.min(1.0, (0.22 * sweep + 0.12 * low) * pulse)) * 12000);
            }
            int min = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            audioTrack = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(Math.max(min, sampleCount * 2))
                    .setTransferMode(AudioTrack.MODE_STATIC).build();
            audioTrack.write(samples, 0, samples.length);
            audioTrack.setLoopPoints(0, samples.length, -1);
            audioTrack.play();
        } catch (Exception ignored) {
            stopWarningAudio();
        }
    }

    private void stopWarningAudio() {
        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
            } catch (Exception ignored) { }
            audioTrack = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopWarningAudio();
    }

    @Override
    protected void onDestroy() {
        stopWarningAudio();
        super.onDestroy();
    }
}
