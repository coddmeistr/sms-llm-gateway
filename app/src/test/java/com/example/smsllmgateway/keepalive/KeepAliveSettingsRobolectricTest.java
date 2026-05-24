package com.example.smsllmgateway.keepalive;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.example.smsllmgateway.GatewayConfig;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class KeepAliveSettingsRobolectricTest {

    private Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    @Test
    public void defaultIsDisabled() {
        KeepAliveSettings settings = new KeepAliveSettings(context());
        assertFalse(settings.isEnabled());
    }

    @Test
    public void enabledFlagRoundTrips() {
        KeepAliveSettings settings = new KeepAliveSettings(context());
        settings.setEnabled(true);
        assertTrue(settings.isEnabled());

        // A fresh wrapper instance must see the same value (shared prefs).
        KeepAliveSettings other = new KeepAliveSettings(context());
        assertTrue(other.isEnabled());

        other.setEnabled(false);
        assertFalse(settings.isEnabled());
    }

    @Test
    public void wrapperStoresUnderTheDocumentedKey() {
        new KeepAliveSettings(context()).setEnabled(true);
        // We document KEY_KEEP_ALIVE as the canonical key; verify directly so
        // a rename never silently breaks downstream readers (boot receiver,
        // periodic heartbeat).
        boolean fromRaw = context()
                .getSharedPreferences(GatewayConfig.PREFS, Context.MODE_PRIVATE)
                .getBoolean(GatewayConfig.KEY_KEEP_ALIVE, false);
        assertTrue(fromRaw);
    }
}
