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
    private Shader bottomGlow;
    private boolean blue;

    public FxBackgroundView(Context context) {
        super(context);
    }

    public FxBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        blue = "blue".equals(getTag());
        postInvalidateDelayed(100);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            postInvalidateDelayed(100);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w <= 0 || h <= 0) return;
        if (blue) {
            base = new LinearGradient(0, 0, 0, h,
                    0xFF0D254E, 0xFF060D1F, Shader.TileMode.CLAMP);
            glow = new RadialGradient(w * 0.5f, -h * 0.2f, h * 0.9f,
                    0x28FFD700, 0x00000000, Shader.TileMode.CLAMP);
            bottomGlow = new RadialGradient(w * 0.5f, h * 1.1f, h * 0.6f,
                    0x150033FF, 0x00000000, Shader.TileMode.CLAMP);
        } else {
            base = new LinearGradient(0, 0, 0, h,
                    0xFF1A0303, 0xFF060606, Shader.TileMode.CLAMP);
            glow = new RadialGradient(w * 0.5f, -h * 0.15f, h * 0.9f,
                    0x28FF1A1A, 0x00000000, Shader.TileMode.CLAMP);
            bottomGlow = new RadialGradient(w * 0.5f, h * 1.1f, h * 0.5f,
                    0x15FF0000, 0x00000000, Shader.TileMode.CLAMP);
        }
        vignette = new RadialGradient(w / 2f, h / 2f, Math.max(w, h) * 0.75f,
                0x00000000, 0xCC000000, Shader.TileMode.CLAMP);
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
        if (bottomGlow != null) {
            paint.setShader(bottomGlow);
            canvas.drawRect(0, 0, w, h, paint);
        }

        long now = SystemClock.uptimeMillis();
        int drift = (int) ((now / 100) % 4);
        paint.setShader(null);

        // Scanlines
        paint.setColor(0x1A000000);
        for (int y = -drift; y < h; y += 3) {
            canvas.drawRect(0, y, w, y + 1, paint);
        }

        // Noise particles
        random.setSeed(now / 400);
        int particleColor = blue ? 0x10FFD700 : 0x12FF4444;
        paint.setColor(particleColor);
        for (int i = 0; i < 60; i++) {
            float x = random.nextFloat() * w;
            float y = random.nextFloat() * h;
            float s = 1f + random.nextFloat() * 2.5f;
            canvas.drawRect(x, y, x + s, y + s, paint);
        }

        // Horizontal scan glitch
        if (now % 7000 < 400) {
            float gy = (float) (Math.abs(Math.sin(now / 80.0)) * h);
            paint.setColor(0x0FFFFFFF);
            canvas.drawRect(0, gy, w, gy + h * 0.04f, paint);
        }

        // Vignette
        if (vignette != null) {
            paint.setShader(vignette);
            canvas.drawRect(0, 0, w, h, paint);
        }
        paint.setShader(null);
        paint.setColor(Color.TRANSPARENT);

        postInvalidateDelayed(80);
    }
}
