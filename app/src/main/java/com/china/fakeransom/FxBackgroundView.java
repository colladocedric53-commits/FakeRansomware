package com.china.fakeransom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

public class FxBackgroundView extends View {

    private final Paint paint = new Paint();
    private final Random random = new Random();
    private Shader base;
    private Shader glow;
    private Shader vignette;
    private boolean blue;

    public FxBackgroundView(Context context) {
        super(context);
        postInvalidateDelayed(90);
    }

    public FxBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        postInvalidateDelayed(90);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        blue = "blue".equals(getTag());
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w <= 0 || h <= 0) return;
        if (blue) {
            base = new LinearGradient(0, 0, 0, h,
                    0xFF0D254E, 0xFF060D1F, Shader.TileMode.CLAMP);
            glow = new RadialGradient(w * 0.5f, -h * 0.25f, h * 0.9f,
                    0x2EFFD700, 0x00000000, Shader.TileMode.CLAMP);
        } else {
            base = new LinearGradient(0, 0, 0, h,
                    0xFF1A0303, 0xFF060606, Shader.TileMode.CLAMP);
            glow = new RadialGradient(w * 0.5f, -h * 0.2f, h * 0.85f,
                    0x30FF1A1A, 0x00000000, Shader.TileMode.CLAMP);
        }
        vignette = new RadialGradient(w / 2f, h / 2f, Math.max(w, h) * 0.75f,
                0x00000000, 0xB3000000, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        if (base != null) {
            paint.setShader(base);
            canvas.drawRect(0, 0, w, h, paint);
        }
        if (glow != null) {
            paint.setShader(glow);
            canvas.drawRect(0, 0, w, h, paint);
        }

        long now = SystemClock.uptimeMillis();
        int drift = (int) ((now / 120) % 4);
        paint.setShader(null);
        paint.setColor(0x26000000);
        for (int y = -drift; y < h; y += 4) {
            canvas.drawRect(0, y, w, y + 2, paint);
        }

        random.setSeed(now / 500);
        paint.setColor(blue ? 0x12FFD700 : 0x14FF4444);
        for (int i = 0; i < 50; i++) {
            float x = random.nextFloat() * w;
            float y = random.nextFloat() * h;
            float s = 2f + random.nextFloat() * 2f;
            canvas.drawRect(x, y, x + s, y + s, paint);
        }

        if (now % 9000 < 450) {
            float gy = (float) (Math.abs(Math.sin(now / 90.0)) * h);
            paint.setColor(0x0DFFFFFF);
            canvas.drawRect(0, gy, w, gy + h * 0.06f, paint);
        }

        if (vignette != null) {
            paint.setShader(vignette);
            canvas.drawRect(0, 0, w, h, paint);
        }
        paint.setShader(null);
        paint.setColor(Color.TRANSPARENT);
    }
}
