package com.example.smsllmgateway.keepalive;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.example.smsllmgateway.GatewayConfig;
import com.example.smsllmgateway.MainActivity;
import com.example.smsllmgateway.R;

/**
 * Long-running foreground service whose only job is to keep the application
 * process alive so {@link com.example.smsllmgateway.SmsReceiver} can react to
 * incoming SMS without being killed by Android's background restrictions.
 *
 * <p>Multiple defences are layered:
 * <ul>
 *   <li>{@link #onStartCommand} returns {@link Service#START_STICKY} so the
 *       system re-creates the service after it kills it.</li>
 *   <li>{@link #onTaskRemoved} re-schedules itself via {@link AlarmManager}
 *       to defeat swipe-from-recents kills on most OEM ROMs.</li>
 *   <li>An ongoing notification anchors the process at {@code IMPORTANCE_LOW}
 *       so the channel doesn't make sound but the service is foreground.</li>
 * </ul>
 */
public class KeepAliveService extends Service {

    private static final String TAG = "KeepAliveService";

    /** Intent extra used by {@link KeepAliveController} when starting. */
    public static final String EXTRA_REASON = "reason";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");
        ensureNotificationChannel(this);
        startInForeground();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Re-arming foreground is cheap and protects us from missing it after
        // a process restart triggered via START_STICKY.
        startInForeground();
        String reason = intent != null ? intent.getStringExtra(EXTRA_REASON) : null;
        Log.i(TAG, "onStartCommand reason=" + reason);
        // Re-arm the periodic heartbeat — it might have been cancelled across
        // a reboot before the boot receiver had a chance to wire it again.
        KeepAliveController.scheduleHeartbeat(this);
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.i(TAG, "onTaskRemoved: scheduling restart");
        scheduleSelfRestart(this);
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startInForeground() {
        Notification notification = buildNotification(this);
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(GatewayConfig.KEEP_ALIVE_NOTIFICATION_ID, notification,
                        foregroundServiceType());
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(GatewayConfig.KEEP_ALIVE_NOTIFICATION_ID, notification, 0);
            } else {
                startForeground(GatewayConfig.KEEP_ALIVE_NOTIFICATION_ID, notification);
            }
        } catch (Throwable e) {
            // Most likely cause: ForegroundServiceStartNotAllowedException on Android 12+
            // when started from background without a valid exemption. We log and let
            // the heartbeat re-attempt later, when the app comes to foreground again.
            Log.e(TAG, "startForeground failed", e);
        }
    }

    @SuppressLint("InlinedApi")
    private static int foregroundServiceType() {
        // FOREGROUND_SERVICE_TYPE_SPECIAL_USE is API 34. The constant value is
        // a compile-time int so it's safe to reference; it's only *passed* to
        // startForeground inside an SDK-version guard.
        return ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
    }

    static Notification buildNotification(Context context) {
        Intent open = new Intent(context, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        // FLAG_IMMUTABLE is available from API 23 — required from API 31+ for
        // PendingIntents that target services/broadcasts.
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, open, piFlags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, GatewayConfig.NOTIFICATION_CHANNEL_ID);
        } else {
            //noinspection deprecation — required on API < 26 where channels don't exist.
            builder = new Notification.Builder(context);
        }
        builder.setContentTitle("SMS LLM Gateway")
                .setContentText("Шлюз активен: ждём входящие SMS")
                .setSmallIcon(R.drawable.ic_keepalive)
                .setOngoing(true)
                .setContentIntent(contentIntent)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PUBLIC);
        return builder.build();
    }

    static void ensureNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        NotificationChannel existing = nm.getNotificationChannel(GatewayConfig.NOTIFICATION_CHANNEL_ID);
        if (existing != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                GatewayConfig.NOTIFICATION_CHANNEL_ID,
                GatewayConfig.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Уведомление о работе SMS-шлюза в фоне.");
        channel.setShowBadge(false);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.enableLights(false);
        nm.createNotificationChannel(channel);
    }

    private static void scheduleSelfRestart(Context context) {
        Intent restart = new Intent(context, KeepAliveService.class);
        restart.putExtra(EXTRA_REASON, "task_removed");
        // FLAG_IMMUTABLE is available from API 23, and our minSdk is 23.
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getService(context, 1, restart, piFlags);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null || pi == null) {
            return;
        }
        long when = System.currentTimeMillis() + 2_000L;
        try {
            // Exact alarms got progressively restricted on API 31+. Probe first
            // and fall back to inexact set(): we just need to fire in ~2s, the
            // device is already awake right after a swipe-from-recents.
            boolean useExact = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                useExact = am.canScheduleExactAlarms();
            }
            if (useExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, when, pi);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Exact alarm denied, falling back to inexact", e);
            try {
                am.set(AlarmManager.RTC_WAKEUP, when, pi);
            } catch (Throwable inner) {
                Log.w(TAG, "Inexact alarm also failed", inner);
            }
        } catch (Throwable e) {
            Log.w(TAG, "Failed to schedule restart alarm", e);
        }
    }
}
