package com.china.fakeransom;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

final class WallpaperUtil {

    private WallpaperUtil() {
    }

    private static File origFile(Context c, String name) {
        return new File(c.getFilesDir(), name);
    }

    static void capture(Context c) {
        WallpaperManager wm = WallpaperManager.getInstance(c);
        save(c, decodable(wm, WallpaperManager.FLAG_SYSTEM), "orig_home.png");
        save(c, decodable(wm, WallpaperManager.FLAG_LOCK), "orig_lock.png");
    }

    private static Bitmap decodable(WallpaperManager wm, int which) {
        try {
            ParcelFileDescriptor fd = wm.getWallpaperFile(which);
            if (fd != null) {
                Bitmap b = BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor());
                try {
                    fd.close();
                } catch (IOException ignored) {
                }
                return b;
            }
        } catch (Throwable ignored) {
        }
        try {
            android.graphics.drawable.Drawable d = wm.getDrawable();
            if (d instanceof android.graphics.drawable.BitmapDrawable) {
                return ((android.graphics.drawable.BitmapDrawable) d).getBitmap();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void save(Context c, Bitmap b, String name) {
        if (b == null) return;
        try (FileOutputStream fos = new FileOutputStream(origFile(c, name))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, fos);
        } catch (IOException ignored) {
        }
    }

    static void setRansom(Context c) {
        Bitmap ransom = render(c);
        WallpaperManager wm = WallpaperManager.getInstance(c);
        try {
            wm.setBitmap(ransom, null, true, WallpaperManager.FLAG_SYSTEM);
        } catch (Throwable ignored) {
        }
        try {
            wm.setBitmap(ransom, null, true, WallpaperManager.FLAG_LOCK);
        } catch (Throwable ignored) {
        }
    }

    static void restore(Context c) {
        WallpaperManager wm = WallpaperManager.getInstance(c);
        Bitmap home = BitmapFactory.decodeFile(origFile(c, "orig_home.png").getAbsolutePath());
        if (home != null) {
            try {
                wm.setBitmap(home, null, true, WallpaperManager.FLAG_SYSTEM);
            } catch (Throwable ignored) {
            }
        }
        Bitmap lock = BitmapFactory.decodeFile(origFile(c, "orig_lock.png").getAbsolutePath());
        if (lock != null) {
            try {
                wm.setBitmap(lock, null, true, WallpaperManager.FLAG_LOCK);
            } catch (Throwable ignored) {
            }
        }
    }

    private static Bitmap render(Context c) {
        int w = c.getResources().getDisplayMetrics().widthPixels;
        int h = c.getResources().getDisplayMetrics().heightPixels;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        canvas.drawColor(Color.BLACK);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextAlign(Paint.Align.CENTER);

        p.setColor(Color.RED);
        p.setFakeBoldText(true);
        p.setTextSize(w * 0.085f);
        canvas.drawText("DEVICE LOCKED", w / 2f, h * 0.25f, p);

        p.setFakeBoldText(false);
        p.setColor(Color.WHITE);
        p.setTextSize(w * 0.032f);
        canvas.drawText("All files encrypted - AES-256 + RSA-4096", w / 2f, h * 0.32f, p);

        p.setColor(0xFFFFCC00);
        p.setFakeBoldText(true);
        p.setTextSize(w * 0.045f);
        canvas.drawText("FILES DELETED IN 48:00:00", w / 2f, h * 0.39f, p);

        float qrSize = Math.min(w, h) * 0.5f;
        drawQr(canvas, w / 2f - qrSize / 2f, h * 0.43f, qrSize);

        p.setColor(Color.WHITE);
        p.setFakeBoldText(false);
        p.setTextSize(w * 0.026f);
        canvas.drawText("1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVDemonstration", w / 2f, h * 0.93f, p);
        return b;
    }

    private static void drawQr(Canvas canvas, float x, float y, float size) {
        int cells = 25;
        float cell = size / cells;
        Paint p = new Paint();
        Random r = new Random(20250473L);
        for (int j = 0; j < cells; j++) {
            for (int i = 0; i < cells; i++) {
                p.setColor(r.nextBoolean() ? Color.WHITE : Color.BLACK);
                canvas.drawRect(x + i * cell, y + j * cell, x + (i + 1) * cell, y + (j + 1) * cell, p);
            }
        }
        drawFinder(canvas, x, y, cell);
        drawFinder(canvas, x + size - 7 * cell, y, cell);
        drawFinder(canvas, x, y + size - 7 * cell, cell);
    }

    private static void drawFinder(Canvas canvas, float x, float y, float cell) {
        Paint dark = new Paint();
        dark.setColor(Color.BLACK);
        canvas.drawRect(x, y, x + 7 * cell, y + 7 * cell, dark);
        Paint light = new Paint();
        light.setColor(Color.WHITE);
        canvas.drawRect(x + cell, y + cell, x + 6 * cell, y + 6 * cell, light);
        canvas.drawRect(x + 2 * cell, y + 2 * cell, x + 5 * cell, y + 5 * cell, dark);
    }
}
