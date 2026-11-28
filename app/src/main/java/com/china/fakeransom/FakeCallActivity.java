package com.china.fakeransom;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.graphics.PorterDuff;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.Locale;

public class FakeCallActivity extends AppCompatActivity {

    private static final int BASE_RATE = 8000;
    private static final long RING_TIMEOUT_MS = 25000;
    private static final long KILL_GESTURE_WINDOW_MS = 4000;
    private static final int KILL_GESTURE_TAPS = 7;

    private final SirenPlayer siren = SirenPlayer.get();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final NoisePlayer noise = new NoisePlayer();

    private TextToSpeech callTts;
    private AudioTrack ringTrack;
    private Vibrator vibrator;
    private boolean ringVibrating;
    private boolean muted;
    private boolean speakerBoost;
    private boolean answered;

    private TextView callTimer;
    private TextView callCaption;
    private View incomingView;
    private View inCallView;
    private ImageButton btnMute;
    private ImageButton btnSpeaker;

    private long callStartMs;
    private long killGestureStart = 0;
    private int killTaps = 0;

    private static final String[] SCRIPT = {
            "Do not hang up. We are inside your device right now.",
            "Every photo, every message, every file on this phone is encrypted.",
            "You have thirty minutes to move the payment.",
            "Do not contact anyone. We are listening to this line."
    };
    private static final long[] SCRIPT_AT_MS = {1500, 7000, 12500, 17500};
    private static final long HANGUP_AT_MS = 22500;

    private final BroadcastReceiver killReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handler.removeCallbacksAndMessages(null);
            finish();
        }
    };

    private final Runnable ringTimeout = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing() && !answered) {
                Toast.makeText(FakeCallActivity.this,
                        "CALL DROPPED \u2014 CALLER RETAINED EVIDENCE", Toast.LENGTH_LONG).show();
                end();
            }
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (isFinishing()) return;
            long s = (System.currentTimeMillis() - callStartMs) / 1000L;
            callTimer.setText(String.format(Locale.US, "%02d:%02d", s / 60, s % 60));
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fake_call);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        incomingView = findViewById(R.id.incomingView);
        inCallView = findViewById(R.id.inCallView);
        callTimer = findViewById(R.id.callTimer);
        callCaption = findViewById(R.id.callCaption);
        btnMute = findViewById(R.id.btnMute);
        btnSpeaker = findViewById(R.id.btnSpeaker);

        findViewById(R.id.declineBtn).setOnClickListener(v -> {
            Toast.makeText(this, "CALL REJECTED \u2014 EVIDENCE CHECKPOINT SAVED", Toast.LENGTH_LONG).show();
            end();
        });
        findViewById(R.id.acceptBtn).setOnClickListener(v -> answer());
        findViewById(R.id.hangupBtn).setOnClickListener(v -> {
            Toast.makeText(this, "CALL TERMINATED BY REMOTE ENFORCER \u2014 TRACE FAILED", Toast.LENGTH_LONG).show();
            end();
        });
        btnMute.setOnClickListener(v -> {
            muted = !muted;
            btnMute.setSelected(muted);
            if (muted) {
                btnMute.getDrawable().setColorFilter(0xFFD32F2F, PorterDuff.Mode.SRC_IN);
            } else {
                btnMute.getDrawable().clearColorFilter();
            }
            noise.setVolume(muted ? 0f : (speakerBoost ? 0.30f : 0.10f));
        });
        btnSpeaker.setOnClickListener(v -> {
            speakerBoost = !speakerBoost;
            btnSpeaker.setSelected(speakerBoost);
            if (speakerBoost) {
                btnSpeaker.getDrawable().setColorFilter(0xFF222222, PorterDuff.Mode.SRC_IN);
            } else {
                btnSpeaker.getDrawable().clearColorFilter();
            }
            noise.setVolume(muted ? 0f : (speakerBoost ? 0.30f : 0.10f));
        });
        findViewById(R.id.btnPad).setOnClickListener(v ->
                Toast.makeText(this, "KEYPAD LOCKED \u2014 CALL IS MONITORED", Toast.LENGTH_SHORT).show());

        ImageView skull = findViewById(R.id.callSkull);
        skull.setOnClickListener(v -> onKillGestureTap());

        ContextCompat.registerReceiver(this, killReceiver,
                new IntentFilter("com.china.fakeransom.END_SIM"),
                ContextCompat.RECEIVER_NOT_EXPORTED);

        siren.pause();
        startRing();
        handler.postDelayed(ringTimeout, RING_TIMEOUT_MS);

        callTts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && callTts != null) {
                callTts.setLanguage(Locale.US);
                callTts.setPitch(0.35f);
                callTts.setSpeechRate(0.80f);
            }
        });
    }

    private void startRing() {
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            try {
                vibrator.vibrate(VibrationEffect.createWaveform(
                        new long[]{0, 1000, 1000}, 0));
                ringVibrating = true;
            } catch (Throwable ignored) {
            }
        }
        try {
            int seconds = 6;
            int frames = BASE_RATE * seconds;
            short[] pcm = new short[frames];
            int ringFrames = BASE_RATE * 2;
            for (int i = 0; i < frames; i++) {
                if (i < ringFrames) {
                    double t = i / (double) BASE_RATE;
                    double s = Math.sin(2 * Math.PI * 440 * t) + Math.sin(2 * Math.PI * 480 * t);
                    pcm[i] = (short) (s * 6000);
                } else {
                    pcm[i] = 0;
                }
            }
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            AudioFormat fmt = new AudioFormat.Builder()
                    .setSampleRate(BASE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            ringTrack = new AudioTrack(attrs, fmt, frames * 2, AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE);
            ringTrack.write(pcm, 0, frames);
            ringTrack.setLoopPoints(0, frames, -1);
            ringTrack.setVolume(1.0f);
            ringTrack.play();
        } catch (Throwable t) {
            ringTrack = null;
        }
    }

    private void stopRing() {
        if (ringTrack != null) {
            try {
                ringTrack.stop();
                ringTrack.release();
            } catch (Throwable ignored) {
            }
            ringTrack = null;
        }
        if (ringVibrating && vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Throwable ignored) {
            }
            ringVibrating = false;
        }
    }

    private void answer() {
        if (answered || isFinishing()) return;
        answered = true;
        stopRing();
        incomingView.setVisibility(View.GONE);
        inCallView.setVisibility(View.VISIBLE);
        callStartMs = System.currentTimeMillis();
        handler.post(tick);
        noise.start(0.10f);

        for (int i = 0; i < SCRIPT.length; i++) {
            final String line = SCRIPT[i];
            handler.postDelayed(() -> {
                if (isFinishing()) return;
                say(line);
            }, SCRIPT_AT_MS[i]);
        }
        handler.postDelayed(() -> {
            if (!isFinishing()) noise.setVolume(0.55f);
        }, HANGUP_AT_MS - 1500);
        handler.postDelayed(() -> {
            if (!isFinishing()) {
                Toast.makeText(FakeCallActivity.this,
                        "CALL ENDED BY REMOTE ENFORCER \u2014 TRACE FAILED", Toast.LENGTH_LONG).show();
                end();
            }
        }, HANGUP_AT_MS);
    }

    private void say(String line) {
        if (callTts == null) return;
        try {
            callTts.speak(line, TextToSpeech.QUEUE_ADD, null, "call_" + System.nanoTime());
        } catch (Throwable ignored) {
        }
        callCaption.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            callCaption.setText(line);
            callCaption.animate().alpha(1f).setDuration(300).start();
        }).start();
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
            SimUtil.broadcastEnd(this);
            finish();
        }
    }

    private void end() {
        handler.removeCallbacksAndMessages(null);
        noise.stop();
        stopRing();
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
        noise.stop();
        stopRing();
        if (SimUtil.isSimActive()) {
            siren.applyRamp(1.0f);
            siren.resumePlaying();
        }
        if (callTts != null) {
            try {
                callTts.stop();
                callTts.shutdown();
            } catch (Throwable ignored) {
            }
            callTts = null;
        }
        try {
            unregisterReceiver(killReceiver);
        } catch (Throwable ignored) {
        }
    }
}
