package com.china.fakeransom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

public class PulseView extends View {

    private static final long PERIOD_MS = 2200;
    private static final int RINGS = 4;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            invalidate();
            postOnAnimation(ticker);
        }
    };

    public PulseView(Context context) {
        super(context);
        init();
    }

    public PulseView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.STROKE);
        postOnAnimation(ticker);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float base = Math.min(getWidth(), getHeight()) * 0.38f;
        long t = SystemClock.uptimeMillis() % PERIOD_MS;
        for (int i = 0; i < RINGS; i++) {
            long phase = (t + i * PERIOD_MS / RINGS) % PERIOD_MS;
            float f = phase / (float) PERIOD_MS;
            float radius = base + f * base * 1.5f;
            int alpha = (int) (80 * (1f - f) * (1f - f));
            paint.setColor(0xFF5599BB);
            paint.setAlpha(alpha);
            paint.setStrokeWidth(2f + 1.5f * (1f - f));
            canvas.drawCircle(cx, cy, radius, paint);
        }
    }
}
