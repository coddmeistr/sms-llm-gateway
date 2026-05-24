package com.example.smsllmgateway.keepalive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.example.smsllmgateway.GatewayConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class KeepAliveControllerRobolectricTest {

    private Application app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        // Drain any leftovers from previous tests.
        Intent leftover;
        while ((leftover = shadowOf(app).getNextStartedService()) != null) {
            // intentionally ignored
        }
        getScheduler().cancelAll();
    }

    private JobScheduler getScheduler() {
        return (JobScheduler) app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    }

    private Intent drainStartedServiceIntents() {
        Intent last = null;
        Intent next;
        while ((next = shadowOf(app).getNextStartedService()) != null) {
            last = next;
        }
        return last;
    }

    @Test
    public void setEnabledTruePersistsFlagAndStartsService() {
        KeepAliveController.setEnabled(app, true);

        assertTrue("settings flag should be persisted",
                KeepAliveController.isEnabled(app));

        Intent started = drainStartedServiceIntents();
        assertNotNull("expected KeepAliveService to be started", started);
        ComponentName component = started.getComponent();
        assertNotNull(component);
        assertEquals(KeepAliveService.class.getName(), component.getClassName());
        assertEquals("user_toggle", started.getStringExtra(KeepAliveService.EXTRA_REASON));
    }

    @Test
    public void setEnabledTrueSchedulesPeriodicHeartbeat() {
        KeepAliveController.setEnabled(app, true);

        JobInfo info = getScheduler().getPendingJob(GatewayConfig.KEEP_ALIVE_HEARTBEAT_JOB_ID);
        assertNotNull("heartbeat job must be scheduled", info);
        assertEquals(KeepAliveJobService.class.getName(), info.getService().getClassName());
        assertEquals(GatewayConfig.KEEP_ALIVE_HEARTBEAT_INTERVAL_MS, info.getIntervalMillis());
        assertTrue("heartbeat must be persisted across reboots", info.isPersisted());
    }

    @Test
    public void setEnabledFalseClearsFlagAndCancelsJob() {
        KeepAliveController.setEnabled(app, true);
        // sanity-check pre-state
        assertNotNull(getScheduler().getPendingJob(GatewayConfig.KEEP_ALIVE_HEARTBEAT_JOB_ID));

        KeepAliveController.setEnabled(app, false);

        assertFalse(KeepAliveController.isEnabled(app));
        assertNull("heartbeat job must be cancelled when keep-alive turns off",
                getScheduler().getPendingJob(GatewayConfig.KEEP_ALIVE_HEARTBEAT_JOB_ID));
    }

    @Test
    public void ensureStartedIsIdempotentForHeartbeat() {
        KeepAliveController.ensureStarted(app, "first");
        KeepAliveController.ensureStarted(app, "second");

        List<JobInfo> all = getScheduler().getAllPendingJobs();
        int count = 0;
        for (JobInfo info : all) {
            if (info.getId() == GatewayConfig.KEEP_ALIVE_HEARTBEAT_JOB_ID) {
                count++;
            }
        }
        assertEquals("heartbeat must be scheduled exactly once", 1, count);
    }

    @Test
    public void ensureStartedPassesReasonExtra() {
        KeepAliveController.ensureStarted(app, "boot:android.intent.action.BOOT_COMPLETED");

        Intent started = drainStartedServiceIntents();
        assertNotNull(started);
        assertEquals(
                "boot:android.intent.action.BOOT_COMPLETED",
                started.getStringExtra(KeepAliveService.EXTRA_REASON));
    }

    @Test
    public void scheduleHeartbeatAloneIsEnoughToInstallJob() {
        // The boot receiver path calls this without setEnabled.
        KeepAliveController.scheduleHeartbeat(app);
        assertNotNull(getScheduler().getPendingJob(GatewayConfig.KEEP_ALIVE_HEARTBEAT_JOB_ID));
    }

    @Test
    public void cancelHeartbeatRemovesPendingJob() {
        KeepAliveController.scheduleHeartbeat(app);
        assertNotNull(getScheduler().getPendingJob(GatewayConfig.KEEP_ALIVE_HEARTBEAT_JOB_ID));

        KeepAliveController.cancelHeartbeat(app);
        assertNull(getScheduler().getPendingJob(GatewayConfig.KEEP_ALIVE_HEARTBEAT_JOB_ID));
    }

    @Test
    public void isEnabledMirrorsKeepAliveSettings() {
        assertFalse(KeepAliveController.isEnabled(app));
        new KeepAliveSettings(app).setEnabled(true);
        assertTrue(KeepAliveController.isEnabled(app));
    }
}
