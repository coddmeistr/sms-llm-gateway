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
import android.util.Log;

import java.util.Locale;

public class SmsReceiver extends android.content.BroadcastReceiver {
    private static final String TAG = "SmsReceiver";
    static final String EXTRA_SENDER = "sender";
    static final String EXTRA_BODY = "body";
    static final String EXTRA_SUBSCRIPTION_ID = "subscription_id";

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

            int subscriptionId = intent.getIntExtra(
                    "subscription",
                    intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1));
            saveStatus(context, "SMS получено от " + sender + ": " + body);
            scheduleReplyJob(context, sender, body.toString(), subscriptionId);
        } catch (Throwable e) {
            Log.e(TAG, "Failed to receive SMS", e);
            saveStatus(context, "Ошибка приема SMS: " + safeMessage(e));
        }
    }

    private void scheduleReplyJob(Context context, String sender, String body, int subscriptionId) {
        PersistableBundle extras = new PersistableBundle();
        extras.putString(EXTRA_SENDER, sender);
        extras.putString(EXTRA_BODY, body);
        extras.putInt(EXTRA_SUBSCRIPTION_ID, subscriptionId);

        int jobId = (int) (System.currentTimeMillis() & 0x7fffffff);
        JobInfo jobInfo = new JobInfo.Builder(
                jobId,
                new ComponentName(context, SmsReplyJobService.class))
                .setExtras(extras)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(0)
                .build();

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
