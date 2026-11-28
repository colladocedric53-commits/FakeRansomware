package com.china.fakeransom;

import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Vibrator;

final class SimUtil {

    private static final String PREFS = "sim_state";
    private static final String KEY_DND = "dnd_filter";
    private static volatile boolean simActive = false;
    private static volatile int foregroundActivities = 0;

    private SimUtil() {
    }

    static boolean isSimActive() {
        return simActive;
    }

    static void setSimActive(boolean value) {
        simActive = value;
    }

    static void activityResumed() {
        foregroundActivities++;
    }

    static void activityPaused() {
        foregroundActivities = Math.max(0, foregroundActivities - 1);
    }

    static boolean isAppInForeground() {
        return foregroundActivities > 0;
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void killVibration(Context c) {
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.cancel();
        } catch (Throwable ignored) {
        }
    }

    static void clearClipboard(Context c) {
        try {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("notes", " "));
        } catch (Throwable ignored) {
        }
    }

    static void setCameraDisabled(Context c, boolean disabled) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(c, LockAdminReceiver.class);
            if (dpm != null && dpm.isAdminActive(admin)) {
                dpm.setCameraDisabled(admin, disabled);
            }
        } catch (Throwable ignored) {
        }
    }

    static void saveDndFilter(Context c, int filter) {
        prefs(c).edit().putInt(KEY_DND, filter).apply();
    }

    static int previousDndFilter(Context c) {
        return prefs(c).getInt(KEY_DND, NotificationManager.INTERRUPTION_FILTER_ALL);
    }

    static void applyDndAlarmsOnly(Context c) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.isNotificationPolicyAccessGranted()) {
                int current = nm.getCurrentInterruptionFilter();
                if (current != NotificationManager.INTERRUPTION_FILTER_ALARMS) {
                    saveDndFilter(c, current);
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    static void restoreDnd(Context c) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.isNotificationPolicyAccessGranted()) {
                nm.setInterruptionFilter(previousDndFilter(c));
            }
        } catch (Throwable ignored) {
        }
    }

    static void broadcastEnd(Context c) {
        try {
            c.sendBroadcast(new Intent("com.china.fakeransom.END_SIM"));
        } catch (Throwable ignored) {
        }
    }

    static void fullCleanup(Context c) {
        simActive = false;
        foregroundActivities = 0;
        SirenPlayer.get().stop();
        killVibration(c);
        clearClipboard(c);
        setCameraDisabled(c, false);
        restoreDnd(c);
        WallpaperUtil.restore(c);
        try {
            c.stopService(new Intent(c, OverlayService.class));
        } catch (Throwable ignored) {
        }
    }
}
