package com.example.smsllmgateway;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PersistableBundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import android.util.Log;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class SmsReceiver extends android.content.BroadcastReceiver {
    private static final String TAG = "SmsReceiver";
    static final String EXTRA_SENDER = "sender";
    static final String EXTRA_BODY = "body";
    static final String EXTRA_SUBSCRIPTION_ID = "subscription_id";

    // Monotonic counter avoids collisions when two SMS arrive in the same millisecond.
    private static final AtomicInteger JOB_ID = new AtomicInteger(
            (int) (System.currentTimeMillis() & 0x7fffffff));

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
                return;
            }

            SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            if (messages == null || messages.length == 0) {
                return;
            }

            String sender = messages[0].getDisplayOriginatingAddress();
            StringBuilder body = new StringBuilder();
            for (SmsMessage message : messages) {
                body.append(message.getMessageBody());
            }

            int subscriptionId = resolveSubscriptionId(intent, messages[0]);
            saveStatus(context, "SMS получено от " + sender);
            scheduleReplyJob(context, sender, body.toString(), subscriptionId);
        } catch (Throwable e) {
            Log.e(TAG, "Failed to receive SMS", e);
            saveStatus(context, "Ошибка приема SMS: " + safeMessage(e));
        }
    }

    private int resolveSubscriptionId(Intent intent, SmsMessage firstMessage) {
        // SmsMessage.getSubscriptionId() is @hide in public SDK, so we read the same
        // value from the broadcast's intent extras. We probe both the documented
        // (SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX) and the legacy "subscription"
        // key — different OEMs deliver the value under different names.
        return intent.getIntExtra(
                "subscription",
                intent.getIntExtra(
                        "android.telephony.extra.SUBSCRIPTION_INDEX",
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID));
    }

    private void scheduleReplyJob(Context context, String sender, String body, int subscriptionId) {
        PersistableBundle extras = new PersistableBundle();
        extras.putString(EXTRA_SENDER, sender);
        extras.putString(EXTRA_BODY, body);
        extras.putInt(EXTRA_SUBSCRIPTION_ID, subscriptionId);

        // incrementAndGet is available since API 1 (unlike updateAndGet which is API 24).
        // The counter is seeded with a positive value from currentTimeMillis,
        // so practical wrap-around (after >2 billion SMS) is not a concern.
        int jobId = JOB_ID.incrementAndGet();

        JobInfo.Builder builder = new JobInfo.Builder(
                jobId,
                new ComponentName(context, SmsReplyJobService.class))
                .setExtras(extras)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(0);
        // Persist the job so SMSes that arrive moments before the OS kills our
        // process — or right before the user reboots — are still answered after
        // recovery instead of vanishing.
        try {
            builder.setPersisted(true);
        } catch (Throwable e) {
            // setPersisted requires RECEIVE_BOOT_COMPLETED. The manifest grants
            // it, but some custom ROMs revoke it. Fall back to a non-persistent
            // job rather than crashing the whole receiver.
            Log.w(TAG, "setPersisted unavailable, falling back to non-persistent job", e);
        }
        JobInfo jobInfo = builder.build();

        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null || scheduler.schedule(jobInfo) != JobScheduler.RESULT_SUCCESS) {
            Log.e(TAG, "Failed to schedule SMS reply job");
            saveStatus(context, "SMS получено, но job не запущен");
        } else {
            saveStatus(context, "SMS job запущен для " + sender);
        }
    }

    private void saveStatus(Context context, String status) {
        String value = String.format(Locale.US, "%tF %<tT: %s", System.currentTimeMillis(), status);
        SharedPreferences prefs = context.getSharedPreferences(GatewayConfig.PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(GatewayConfig.KEY_LAST_STATUS, value).apply();
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }
}
