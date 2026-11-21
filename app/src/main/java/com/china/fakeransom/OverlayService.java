package com.china.fakeransom;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class OverlayService extends Service {

    private static final String CHANNEL_ID = "overlay_sim_channel";
    private static final int OVERLAY_NOTIFICATION_ID = 0x5151;
    private static final long WATCHDOG_MS = 600_000L;
    private static final long KILL_WINDOW_MS = 4000L;
    private static final int KILL_TAPS = 7;

    private WindowManager windowManager;
    private View overlayView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int taps = 0;
    private long tapStart = 0L;

    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            SimUtil.fullCleanup(OverlayService.this);
            boundedShutdown();
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        SimUtil.setSimActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "END".equals(intent.getAction())) {
            boundedShutdown();
            return START_NOT_STICKY;
        }
        promoteAndNotify();
        if (overlayView == null) {
            attachOverlay();
            handler.postDelayed(watchdog, WATCHDOG_MS);
        }
        return START_NOT_STICKY;
    }

    private void promoteAndNotify() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Lock overlay", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(channel);
        }
        Intent end = new Intent(this, OverlayService.class).setAction("END");
        PendingIntent endIntent = PendingIntent.getService(this, 7, end,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("SIM ACTIVE")
                .setContentText("Educational simulation running - tap to end it")
                .setOngoing(true)
                .setContentIntent(endIntent)
                .setPriority(Notification.PRIORITY_LOW);
        startForeground(OVERLAY_NOTIFICATION_ID, b.build());
    }

    private void attachOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null || !android.provider.Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        int statusBarHeight = 0;
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resId);
        }

        int flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.y = statusBarHeight;

        overlayView = android.view.LayoutInflater.from(this).inflate(R.layout.overlay_lock, null);
        overlayView.findViewById(R.id.overlaySkull).setOnClickListener(v -> onKillTap());
        try {
            windowManager.addView(overlayView, lp);
        } catch (Throwable t) {
            overlayView = null;
            stopSelf();
        }
    }

    private void onKillTap() {
        long now = System.currentTimeMillis();
        if (now - tapStart > KILL_WINDOW_MS) {
            taps = 0;
        }
        if (taps == 0) {
            tapStart = now;
        }
        taps++;
        if (taps >= KILL_TAPS) {
            SimUtil.broadcastEnd(this);
            SimUtil.fullCleanup(this);
            boundedShutdown();
        }
    }

    private void boundedShutdown() {
        handler.removeCallbacksAndMessages(null);
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Throwable ignored) {
            }
            overlayView = null;
        }
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Throwable ignored) {
            }
            overlayView = null;
        }
        super.onDestroy();
    }
}
