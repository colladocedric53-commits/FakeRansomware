package com.china.fakeransom;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

public class OverlayService extends Service {

    private static final String CHANNEL_ID = "overlay_sim_channel";
    private static final int OVERLAY_NOTIFICATION_ID = 0x5151;
    private static final long WATCHDOG_MS = 600_000L;
    private static final long KILL_WINDOW_MS = 3000L;
    private static final int KILL_TAPS = 10;
    private static final long VISIBILITY_CHECK_MS = 400L;

    private WindowManager windowManager;
    private View overlayView;
    private View lockPanel;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int taps = 0;
    private long tapStart = 0L;
    private boolean overlayVisible = false;

    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            SimUtil.fullCleanup(OverlayService.this);
            boundedShutdown();
        }
    };

    private final Runnable visibilityCheck = new Runnable() {
        @Override
        public void run() {
            updateOverlayVisibility();
            handler.postDelayed(this, VISIBILITY_CHECK_MS);
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
            handler.post(visibilityCheck);
        } else {
            updateOverlayVisibility();
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
                .setContentText("Educational simulation running - tap notification to end")
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

        int flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;

        overlayView = android.view.LayoutInflater.from(this).inflate(R.layout.overlay_lock, null);
        lockPanel = overlayView.findViewById(R.id.lockPanel);

        // Tapping the overlay (anywhere) returns to the ransom activity
        View.OnClickListener returnToApp = v -> returnToRansomActivity();
        overlayView.setOnClickListener(returnToApp);
        lockPanel.setOnClickListener(returnToApp);

        // Kill gesture on tiny skull icon (10 quick taps in 3 seconds)
        overlayView.findViewById(R.id.overlaySkull).setOnClickListener(v -> onKillTap());

        // Start hidden - only show when app is in background
        overlayView.setVisibility(View.GONE);
        overlayVisible = false;

        try {
            windowManager.addView(overlayView, lp);
        } catch (Throwable t) {
            overlayView = null;
            lockPanel = null;
            stopSelf();
        }
    }

    private void updateOverlayVisibility() {
        if (overlayView == null || windowManager == null) return;

        boolean shouldShow = SimUtil.isSimActive() && !SimUtil.isAppInForeground();

        if (shouldShow && !overlayVisible) {
            overlayView.setVisibility(View.VISIBLE);
            overlayVisible = true;
        } else if (!shouldShow && overlayVisible) {
            overlayView.setVisibility(View.GONE);
            overlayVisible = false;
        }
    }

    private void returnToRansomActivity() {
        try {
            Intent intent = new Intent(this, FakeRansomActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("BRING_TO_FRONT", true);
            startActivity(intent);
        } catch (Throwable ignored) {
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
            lockPanel = null;
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
            lockPanel = null;
        }
        super.onDestroy();
    }
}
