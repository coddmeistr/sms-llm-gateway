package com.example.smsllmgateway.keepalive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import com.example.smsllmgateway.GatewayConfig;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class KeepAliveServiceRobolectricTest {

    private Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    @Test
    public void ensureNotificationChannelCreatesLowImportanceSilentChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        KeepAliveService.ensureNotificationChannel(context());

        NotificationManager nm = context().getSystemService(NotificationManager.class);
        assertNotNull(nm);
        NotificationChannel channel = nm.getNotificationChannel(GatewayConfig.NOTIFICATION_CHANNEL_ID);
        assertNotNull("expected keep-alive notification channel to exist", channel);
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.getImportance());
        assertEquals(GatewayConfig.NOTIFICATION_CHANNEL_NAME, channel.getName().toString());
    }

    @Test
    public void ensureNotificationChannelIsIdempotent() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        KeepAliveService.ensureNotificationChannel(context());
        KeepAliveService.ensureNotificationChannel(context());
        KeepAliveService.ensureNotificationChannel(context());
        // No exception thrown == idempotent. Channel still exists.
        NotificationManager nm = context().getSystemService(NotificationManager.class);
        assertNotNull(nm.getNotificationChannel(GatewayConfig.NOTIFICATION_CHANNEL_ID));
    }

    @Test
    public void buildNotificationIsOngoingAndUsesAppChannel() {
        KeepAliveService.ensureNotificationChannel(context());
        Notification notification = KeepAliveService.buildNotification(context());

        assertNotNull(notification);
        assertTrue("notification must be ongoing so users can't swipe it away",
                (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertEquals(GatewayConfig.NOTIFICATION_CHANNEL_ID, notification.getChannelId());
        }
        assertNotNull("notification must open MainActivity on tap",
                notification.contentIntent);
    }
}
