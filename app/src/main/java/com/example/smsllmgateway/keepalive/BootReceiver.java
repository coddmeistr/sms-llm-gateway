package com.example.smsllmgateway.keepalive;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Re-arms keep-alive after device boot, package update or quick-boot.
 *
 * <p>If the user previously turned the "stay alive 24/7" toggle on, the boot
 * receiver re-asserts the foreground service so the gateway resumes listening
 * for SMS even before the user opens the launcher.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "KeepAliveBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        Log.i(TAG, "boot action=" + action);
        try {
            if (KeepAliveController.isEnabled(context)) {
                KeepAliveController.ensureStarted(context, "boot:" + action);
            }
        } catch (Throwable e) {
            Log.w(TAG, "boot keep-alive failed", e);
        }
    }
}
