package com.example.smsllmgateway.keepalive;

import android.annotation.SuppressLint;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.example.smsllmgateway.GatewayConfig;

/**
 * Single entry point for turning the keep-alive feature on or off.
 *
 * <p>Owning all the moving parts here keeps the rest of the app free of
 * platform plumbing: the toggle in {@link com.example.smsllmgateway.MainActivity}
 * and the {@link BootReceiver} both call {@link #setEnabled} /
 * {@link #ensureStarted} and don't need to know about foreground-service
 * APIs, job schedulers or notification channels.
 */
public final class KeepAliveController {

    private static final String TAG = "KeepAliveController";

    private KeepAliveController() {
    }

    /**
     * Persists the flag in shared preferences and starts/stops the
     * foreground service + heartbeat job accordingly.
     */
    public static void setEnabled(Context context, boolean enabled) {
        Context app = context.getApplicationContext();
        new KeepAliveSettings(app).setEnabled(enabled);
        if (enabled) {
            ensureStarted(app, "user_toggle");
        } else {
            stop(app);
        }
    }

    public static boolean isEnabled(Context context) {
        return new KeepAliveSettings(context.getApplicationContext()).isEnabled();
    }

    /** Starts the foreground service and schedules the heartbeat. Safe to call repeatedly. */
    public static void ensureStarted(Context context, String reason) {
        Context app = context.getApplicationContext();
        Intent intent = new Intent(app, KeepAliveService.class);
        intent.putExtra(KeepAliveService.EXTRA_REASON, reason);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent);
            } else {
                app.startService(intent);
            }
        } catch (Throwable e) {
            // Android 12+ may throw ForegroundServiceStartNotAllowedException
            // when started from the background without an exemption (Doze etc.).
            // The next heartbeat or boot event will retry.
            Log.w(TAG, "ensureStarted failed (reason=" + reason + ")", e);
        }
        scheduleHeartbeat(app);
    }

    public static void stop(Context context) {
        Context app = context.getApplicationContext();
        try {
            app.stopService(new Intent(app, KeepAliveService.class));
        } catch (Throwable e) {
            Log.w(TAG, "stopService failed", e);
        }
        cancelHeartbeat(app);
    }

    /**
     * Schedules a periodic JobScheduler check that re-creates the foreground
     * service if it died. The job is {@code persisted} so it survives reboots
     * even before the boot receiver fires.
     */
    public static void scheduleHeartbeat(Context context) {
        Context app = context.getApplicationContext();
        JobScheduler scheduler = (JobScheduler) app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            return;
        }
        // Skip the re-schedule when a job with our id is already queued so we
        // don't reset the periodic countdown on every ensureStarted() call.
        // getPendingJob is API 24+, and our minSdk is 23, so we guard.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && hasPendingHeartbeat(scheduler)) {
            return;
        }
        JobInfo.Builder builder = new JobInfo.Builder(
                GatewayConfig.KEEP_ALIVE_HEARTBEAT_JOB_ID,
                new ComponentName(app, KeepAliveJobService.class))
                .setPeriodic(GatewayConfig.KEEP_ALIVE_HEARTBEAT_INTERVAL_MS)
                .setPersisted(true)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false);
        try {
            int result = scheduler.schedule(builder.build());
            if (result != JobScheduler.RESULT_SUCCESS) {
                Log.w(TAG, "scheduleHeartbeat returned " + result);
            }
        } catch (Throwable e) {
            Log.w(TAG, "scheduleHeartbeat failed", e);
        }
    }

    @SuppressLint("NewApi")
    private static boolean hasPendingHeartbeat(JobScheduler scheduler) {
        try {
            return scheduler.getPendingJob(GatewayConfig.KEEP_ALIVE_HEARTBEAT_JOB_ID) != null;
        } catch (Throwable ignored) {
            // getPendingJob throws on some misbehaving OEM ROMs; let the caller
            // fall through and (re-)schedule. schedule() replaces any duplicate.
            return false;
        }
    }

    public static void cancelHeartbeat(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getApplicationContext()
                .getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            return;
        }
        try {
            scheduler.cancel(GatewayConfig.KEEP_ALIVE_HEARTBEAT_JOB_ID);
        } catch (Throwable e) {
            Log.w(TAG, "cancelHeartbeat failed", e);
        }
    }
}
