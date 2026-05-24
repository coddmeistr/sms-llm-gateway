package com.example.smsllmgateway.keepalive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class BootReceiverRobolectricTest {

    private Application app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        // Reset state between tests.
        new KeepAliveSettings(app).setEnabled(false);
        Intent leftover;
        while ((leftover = shadowOf(app).getNextStartedService()) != null) {
            // drain
        }
    }

    @Test
    public void bootDoesNothingWhenKeepAliveDisabled() {
        BootReceiver receiver = new BootReceiver();
        receiver.onReceive(app, new Intent(Intent.ACTION_BOOT_COMPLETED));

        assertNull("must not start any service when keep-alive is off",
                shadowOf(app).getNextStartedService());
    }

    @Test
    public void bootStartsKeepAliveServiceWhenEnabled() {
        new KeepAliveSettings(app).setEnabled(true);
        BootReceiver receiver = new BootReceiver();
        receiver.onReceive(app, new Intent(Intent.ACTION_BOOT_COMPLETED));

        Intent started = shadowOf(app).getNextStartedService();
        assertNotNull("expected KeepAliveService to be started after boot", started);
        assertNotNull(started.getComponent());
        assertEquals(KeepAliveService.class.getName(),
                started.getComponent().getClassName());
        String reason = started.getStringExtra(KeepAliveService.EXTRA_REASON);
        assertNotNull(reason);
        // Reason includes the broadcast action for diagnostics in logs.
        if (!reason.startsWith("boot:")) {
            throw new AssertionError("expected reason 'boot:*' but was: " + reason);
        }
    }

    @Test
    public void bootHandlesNullIntentGracefully() {
        new KeepAliveSettings(app).setEnabled(true);
        BootReceiver receiver = new BootReceiver();
        // Real boot intents are never null but defensive code should survive
        // unit/instrumentation harnesses that pass null.
        receiver.onReceive(app, new Intent());
        // Just make sure no exception leaked out; service intent is fine either way.
        @SuppressWarnings("unused")
        Intent started = shadowOf(app).getNextStartedService();
    }

    @Test
    public void bootHandlesMultipleActionsViaSeparateOnReceiveCalls() {
        new KeepAliveSettings(app).setEnabled(true);
        BootReceiver receiver = new BootReceiver();
        for (String action : new String[]{
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                "android.intent.action.LOCKED_BOOT_COMPLETED",
                "android.intent.action.QUICKBOOT_POWERON",
        }) {
            // Drain any pending starts from earlier iterations.
            while (shadowOf(app).getNextStartedService() != null) {
                // drain
            }
            receiver.onReceive(app, new Intent(action));
            Intent started = shadowOf(app).getNextStartedService();
            assertNotNull("expected service start for action " + action, started);
            assertEquals("expected KeepAliveService for action " + action,
                    KeepAliveService.class.getName(),
                    started.getComponent().getClassName());
        }
    }

    @Test
    public void contextParameterIsRespected() {
        new KeepAliveSettings(app).setEnabled(true);
        Context appCtx = app.getApplicationContext();
        new BootReceiver().onReceive(appCtx, new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertNotNull(shadowOf(app).getNextStartedService());
    }
}
