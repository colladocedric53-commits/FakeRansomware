package com.china.fakeransom;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.Locale;
import java.util.Random;

public class PaymentActivity extends AppCompatActivity {

    private TextView progressText;
    private TextView expiryText;
    private TextView amountText;
    private View[] confirmDots = new View[6];
    private int confirmations = 0;
    private int secondsLeft = 299;

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
        setContentView(R.layout.payment_screen);

        progressText = findViewById(R.id.paymentProgress);
        expiryText = findViewById(R.id.paymentExpiry);
        amountText = findViewById(R.id.paymentAmount);

        confirmDots[0] = findViewById(R.id.dot1);
        confirmDots[1] = findViewById(R.id.dot2);
        confirmDots[2] = findViewById(R.id.dot3);
        confirmDots[3] = findViewById(R.id.dot4);
        confirmDots[4] = findViewById(R.id.dot5);
        confirmDots[5] = findViewById(R.id.dot6);

        FrameLayout slot = findViewById(R.id.qrSlot);
        slot.addView(new QrView(this));

        findViewById(R.id.paymentSkull).setOnClickListener(v -> {
            SimUtil.broadcastEnd(this);
            finish();
        });

        ContextCompat.registerReceiver(this, killReceiver,
                new IntentFilter("com.china.fakeransom.END_SIM"),
                ContextCompat.RECEIVER_NOT_EXPORTED);

        tick();
        confirm();
    }

    private void updateDots() {
        for (int i = 0; i < 6; i++) {
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            if (i < confirmations) {
                d.setColor(0xFF88AAFF);
                d.setSize(24, 24);
            } else {
                d.setColor(0x3388AAFF);
                d.setSize(24, 24);
            }
            confirmDots[i].setBackground(d);
        }
    }

    private void tick() {
        secondsLeft = Math.max(0, secondsLeft - 1);
        long m = secondsLeft / 60;
        long s = secondsLeft % 60;
        expiryText.setText(String.format(Locale.US, "WALLET EXPIRES IN %d:%02d", m, s));
        if (secondsLeft <= 60) {
            expiryText.setTextColor(0xFFFF0000);
        } else if (secondsLeft <= 120) {
            expiryText.setTextColor(0xFFFF6600);
        }
        if (secondsLeft == 0) {
            amountText.setText("AMOUNT RAISED: 0.07 BTC - NETWORK CONGESTION");
            amountText.setTextColor(0xFFFF2222);
        }
        handler.postDelayed(this::tick, 1000);
    }

    private void confirm() {
        if (confirmations < 6) {
            confirmations++;
            progressText.setText("Confirming on network... " + confirmations + "/6");
            updateDots();
            if (confirmations == 6) {
                progressText.setText("✓ 6/6 confirmations — awaiting payment");
                progressText.setTextColor(0xFF88FFAA);
            }
            handler.postDelayed(this::confirm, (long) (4000 + Math.random() * 5000));
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(killReceiver);
        } catch (Throwable ignored) {
        }
    }

    static class QrView extends android.view.View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        QrView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int cells = 25;
            int size = Math.min(getWidth(), getHeight()) - 32;
            int x0 = (getWidth() - size) / 2;
            int y0 = (getHeight() - size) / 2;
            float cell = size / (float) cells;
            Random r = new Random(20250473L);

            paint.setColor(Color.WHITE);
            canvas.drawRect(x0 - 8, y0 - 8, x0 + size + 8, y0 + size + 8, paint);

            for (int j = 0; j < cells; j++) {
                for (int i = 0; i < cells; i++) {
                    paint.setColor(r.nextBoolean() ? Color.BLACK : Color.WHITE);
                    canvas.drawRect(x0 + i * cell, y0 + j * cell,
                            x0 + (i + 1) * cell, y0 + (j + 1) * cell, paint);
                }
            }
            drawFinder(canvas, x0, y0, cell);
            drawFinder(canvas, x0 + size - 7 * cell, y0, cell);
            drawFinder(canvas, x0, y0 + size - 7 * cell, cell);
        }

        private void drawFinder(Canvas canvas, float x, float y, float cell) {
            paint.setColor(Color.BLACK);
            canvas.drawRect(x, y, x + 7 * cell, y + 7 * cell, paint);
            paint.setColor(Color.WHITE);
            canvas.drawRect(x + cell, y + cell, x + 6 * cell, y + 6 * cell, paint);
            paint.setColor(Color.BLACK);
            canvas.drawRect(x + 2 * cell, y + 2 * cell, x + 5 * cell, y + 5 * cell, paint);
        }
    }
}
