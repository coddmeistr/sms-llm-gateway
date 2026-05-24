package com.example.smsllmgateway.keepalive;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.smsllmgateway.GatewayConfig;

/**
 * Thin wrapper around the gateway {@link SharedPreferences} for the
 * keep-alive flag. Isolated as its own class so that
 * {@link KeepAliveController}, {@link com.example.smsllmgateway.SmsReceiver}
 * and the boot receiver all read/write the same source of truth without
 * duplicating string-key handling.
 */
public final class KeepAliveSettings {

    private final SharedPreferences prefs;

    public KeepAliveSettings(Context context) {
        this(context.getSharedPreferences(GatewayConfig.PREFS, Context.MODE_PRIVATE));
    }

    /** Visible for tests so we can inject a Robolectric-backed prefs. */
    KeepAliveSettings(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public boolean isEnabled() {
        return prefs.getBoolean(GatewayConfig.KEY_KEEP_ALIVE, false);
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(GatewayConfig.KEY_KEEP_ALIVE, enabled).apply();
    }
}
