package com.china.fakeransom;

import android.content.Context;
import android.provider.Settings;

final class DeviceId {

    private DeviceId() {
    }

    static String secureId(Context context) {
        String id = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        return id != null ? id.toUpperCase() : "UNKNOWN";
    }
}
