package com.china.fakeransom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

final class PulseView extends View {

    private static final long PERIOD_MS = 1800;
    private static final int RINGS = 3;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            invalidate();
            postOnAnimation(ticker);
        }
    };

    PulseView(Context context) {
        super(context);
        postOnAnimation(ticker);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float base = Math.min(getWidth(), getHeight()) * 0.30f;
        long t = SystemClock.uptimeMillis() % PERIOD_MS;
        for (int i = 0; i < RINGS; i++) {
            long phase = (t + i * PERIOD_MS / RINGS) % PERIOD_MS;
            float f = phase / (float) PERIOD_MS;
            paint.setColor(0xFF2E9BD6);
            paint.setAlpha((int) (140 * (1f - f)));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4f);
            canvas.drawCircle(cx, cy, base + f * base * 1.6f, paint);
        }
    }
}
