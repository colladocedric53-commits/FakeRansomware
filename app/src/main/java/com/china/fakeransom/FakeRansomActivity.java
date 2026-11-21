package com.china.fakeransom;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Timer;
import java.util.TimerTask;

public class FakeRansomActivity extends AppCompatActivity {

    private TextView statusText;
    private ProgressBar progressBar;
    private Button unlockBtn;
    private int progress = 0;
    private int index = 0;
    private Timer lockTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fake_ransom);

        statusText = findViewById(R.id.statusText);
        progressBar = findViewById(R.id.progressBar);
        unlockBtn = findViewById(R.id.unlockBtn);

        unlockBtn.setOnClickListener(v -> {
            if (lockTimer != null) {
                lockTimer.cancel();
            }
            finish();
        });

        startFakeEncryption();
    }

    private void startFakeEncryption() {
        statusText.setText("INITIATING SYSTEM LOCK...");

        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(500);
            }
        }

        final String[] fakeFiles = {"photos.zip", "banking_app.db", "contacts.vcf", "whatsapp_media.mp4"};

        lockTimer = new Timer();
        lockTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    progress += 2;
                    progressBar.setProgress(progress);

                    String currentFile = fakeFiles[index % fakeFiles.length];
                    statusText.setText("ENCRYPTING: " + currentFile + "\nProgress: " + progress + "%");
                    index++;

                    if (progress >= 100) {
                        showRansomNote();
                        this.cancel();
                        if (v != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            v.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE));
                        }
                    }
                });
            }
        }, 0, 100);
    }

    private void showRansomNote() {
        statusText.setText("ALL FILES ENCRYPTED!\n\nPAY [SIMULATION] ONLINE CURRENCY\nTO RESTORE YOUR DEVICE");
    }

    @Override
    public void onBackPressed() {
        Toast.makeText(this, "SYSTEM LOCKED: CANNOT EXIT", Toast.LENGTH_SHORT).show();
    }
}
