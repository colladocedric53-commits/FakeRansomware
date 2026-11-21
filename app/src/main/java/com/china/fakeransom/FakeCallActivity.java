package com.china.fakeransom;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class FakeCallActivity extends AppCompatActivity {

    private final SirenPlayer siren = SirenPlayer.get();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver killReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fake_call);

        findViewById(R.id.declineBtn).setOnClickListener(v -> {
            Toast.makeText(this, "CALL REJECTED - EVIDENCE CHECKPOINT SAVED", Toast.LENGTH_LONG).show();
            end();
        });
        findViewById(R.id.acceptBtn).setOnClickListener(v -> {
            Toast.makeText(this, "CALL TERMINATED BY REMOTE ENFORCER", Toast.LENGTH_LONG).show();
            end();
        });
        findViewById(R.id.callSkull).setOnClickListener(v -> {
            SimUtil.broadcastEnd(this);
            finish();
        });

        ContextCompat.registerReceiver(this, killReceiver,
                new IntentFilter("com.china.fakeransom.END_SIM"),
                ContextCompat.RECEIVER_NOT_EXPORTED);

        siren.pause();
        siren.applyRamp(1.35f);
        siren.resumePlaying();

        handler.postDelayed(() -> {
            if (!isFinishing()) {
                Toast.makeText(this, "CALL DROPPED - CALLER RETAINED EVIDENCE", Toast.LENGTH_LONG).show();
                end();
            }
        }, 25000);
    }

    private void end() {
        handler.removeCallbacksAndMessages(null);
        if (SimUtil.isSimActive()) {
            siren.applyRamp(1.0f);
            siren.resumePlaying();
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        Toast.makeText(this, "CALL MUST BE ANSWERED OR REJECTED", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        siren.applyRamp(1.0f);
        try {
            unregisterReceiver(killReceiver);
        } catch (Throwable ignored) {
        }
    }
}
