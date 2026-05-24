package com.example.smsllmgateway.keepalive;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

/**
 * Periodic (15+ minute) heartbeat that re-asserts the foreground service.
 *
 * <p>If the OS killed {@link KeepAliveService} (low memory, user swiped from
 * recents on an aggressive OEM ROM, ...) the next heartbeat will recreate it.
 * This is the safety net behind {@code START_STICKY} — heartbeats are managed
 * by the system's {@code JobScheduler}, which is decoupled from our process
 * lifecycle, so even a fully-dead app gets a wake-up.
 */
public class KeepAliveJobService extends JobService {

    private static final String TAG = "KeepAliveJobService";

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i(TAG, "heartbeat");
        try {
            KeepAliveSettings settings = new KeepAliveSettings(this);
            if (settings.isEnabled()) {
                KeepAliveController.ensureStarted(this, "heartbeat");
            } else {
                // User turned the toggle off but a previously persisted job is
                // still firing. Cancel ourselves so we don't drain the battery.
                KeepAliveController.cancelHeartbeat(this);
            }
        } catch (Throwable e) {
            Log.w(TAG, "heartbeat failed", e);
        }
        // No long-running work on this thread.
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // System reclaimed us; reschedule by returning true.
        return true;
    }
}
