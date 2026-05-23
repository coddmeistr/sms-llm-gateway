package com.example.smsllmgateway;

import android.Manifest;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PersistableBundle;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.example.smsllmgateway.commands.CommandBatch;
import com.example.smsllmgateway.commands.CommandParser;
import com.example.smsllmgateway.commands.CommandRouter;
import com.example.smsllmgateway.commands.ParsedCommand;
import com.example.smsllmgateway.commands.PrefsSettingsGateway;
import com.example.smsllmgateway.commands.SettingsGateway;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SmsReplyJobService extends JobService {
    private static final String TAG = "SmsReplyJobService";
    private ExecutorService executor;

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
    }

    @Override
    public void onDestroy() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        ExecutorService runner = executor;
        if (runner == null || runner.isShutdown()) {
            runner = Executors.newSingleThreadExecutor();
            executor = runner;
        }
        final ExecutorService finalRunner = runner;
        finalRunner.execute(() -> {
            try {
                handleMessage(params);
            } catch (Throwable e) {
                Log.e(TAG, "Failed to process SMS", e);
                saveStatus("Ошибка обработки SMS: " + describeError(e));
            } finally {
                jobFinished(params, false);
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    private void handleMessage(JobParameters params) throws Exception {
        PersistableBundle extras = params.getExtras();
        String sender = stringExtra(extras, SmsReceiver.EXTRA_SENDER).trim();
        String body = stringExtra(extras, SmsReceiver.EXTRA_BODY).trim();
        int subscriptionId = extras != null
                ? extras.getInt(SmsReceiver.EXTRA_SUBSCRIPTION_ID, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                : SubscriptionManager.INVALID_SUBSCRIPTION_ID;

        SharedPreferences prefs = getSharedPreferences(GatewayConfig.PREFS, MODE_PRIVATE);
        ConversationStore conversations = new ConversationStore(this);
        SettingsGateway settings = new PrefsSettingsGateway(prefs, conversations);

        if (!prefs.getBoolean(GatewayConfig.KEY_ENABLED, false)) {
            saveStatus("Получено SMS от " + sender + ", но шлюз выключен");
            return;
        }

        // Без отправителя ответ слать некуда: SmsManager упадёт на пустом destination.
        if (sender.isEmpty()) {
            saveStatus("SMS без отправителя проигнорировано");
            return;
        }

        if (!PhoneMatcher.isAllowed(sender, settings.getAllowedSenders())) {
            saveStatus("SMS от " + sender + " отклонено по списку разрешенных номеров");
            return;
        }

        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Нет разрешения SEND_SMS");
        }

        SmsManager smsManager = resolveSmsManager(subscriptionId);

        if (body.isEmpty()) {
            sendSms(smsManager, sender, "Пустое SMS. Отправьте вопрос или HELP.");
            saveStatus("Пустое SMS от " + sender);
            return;
        }

        CommandRouter router = new CommandRouter(settings);

        // Multi-command batch (e.g. "PRESET coder && TOKENS 400 && THINK on")
        // is a settings macro — never falls through to LLM, even when some
        // segment isn't a recognised command (we report it inline).
        if (CommandBatch.isBatch(body)) {
            String[] segments = CommandBatch.split(body);
            if (segments.length == 0) {
                sendSms(smsManager, sender,
                        "В SMS только разделители && без команд. "
                                + "Формат: CMD1 && CMD2 && ...");
                saveStatus("Батч без команд от " + sender);
                return;
            }
            if (segments.length >= 2) {
                String batchReply = router.routeBatch(sender, segments);
                int parts = sendSms(smsManager, sender, batchReply);
                if (parts < 0) {
                    saveStatus("Эмулятор: SMS-ответ не отправлен. Батч " + segments.length
                            + " команд. Ответ: " + batchReply);
                } else {
                    saveStatus("Батч из " + segments.length + " команд обработан для "
                            + sender + ", частей SMS: " + parts + ". Ответ: " + batchReply);
                }
                return;
            }
            // Single useful segment ("&&STATUS" / "STATUS&&"): fall through to
            // the single-command path below using the trimmed segment.
            body = segments[0];
        }

        ParsedCommand parsed = CommandParser.parse(body);
        String commandReply = router.route(sender, parsed);
        if (commandReply != null) {
            int parts = sendSms(smsManager, sender, commandReply);
            if (parts < 0) {
                saveStatus("Эмулятор: SMS-ответ не отправлен. Ответ: " + commandReply);
            } else {
                saveStatus("Команда обработана для " + sender + ", частей SMS: " + parts + ". Ответ: " + commandReply);
            }
            return;
        }

        // One-shot per-request overrides via inline markers [WEB] / [THINK].
        // We extract them *after* the command path, so they only ever influence
        // LLM calls (commands themselves never go through here).
        LlmMarkers markers = LlmMarkers.extract(body);
        String userMessage = markers.cleanedBody;
        if (userMessage.isEmpty()) {
            sendSms(smsManager, sender,
                    "Похоже, в SMS были только маркеры [WEB]/[THINK] без текста. "
                            + "Добавьте сам вопрос рядом с маркером.");
            saveStatus("SMS только с маркерами от " + sender);
            return;
        }

        String endpoint = settings.getEndpoint();
        String apiKey = settings.getApiKey();
        String model = settings.getModel(sender);
        int maxChars = settings.getReplyChars();
        int tokens = settings.getTokens(sender);
        double temperature = settings.getTemperature(sender);
        // Markers can only enable an option, never disable one the user already turned on.
        boolean thinking = settings.isThinking(sender) || markers.forceThinking;
        boolean webSearch = settings.isWebSearch(sender) || markers.forceWeb;

        LlmOptions options = new LlmOptions(tokens, temperature, thinking, webSearch);
        String systemPrompt = PromptPresets.composeSystemPrompt(settings.getPreset(), maxChars, tokens);

        String activeChat = settings.getActiveChat(sender);
        JSONArray history = conversations.getRecentMessages(sender, activeChat);

        String answer;
        try {
            answer = LlmClient.requestChatCompletion(
                    endpoint, apiKey, model, systemPrompt, history, userMessage, options);
        } catch (Exception e) {
            answer = "Не удалось получить ответ от нейросети. Попробуйте еще раз или выберите другую модель командой MODEL LIST.";
            int parts = sendSms(smsManager, sender, answer);
            saveStatus("Ошибка LLM для " + sender + ": " + describeError(e)
                    + ". Отправлен fallback, частей SMS: " + parts);
            return;
        }

        answer = TextSanitizer.limit(TextSanitizer.cleanPlainText(answer), maxChars);
        if (answer == null || answer.trim().isEmpty() || "null".equalsIgnoreCase(answer.trim())) {
            answer = "Нейросеть вернула пустой ответ. Попробуйте еще раз или выберите другую модель командой MODEL LIST.";
        }

        // Store the cleaned text in history so future turns don't carry stale markers.
        conversations.appendExchange(sender, activeChat, userMessage, answer);
        int parts = sendSms(smsManager, sender, answer);
        String overrideTag = describeOverrides(markers);
        if (parts < 0) {
            saveStatus("Эмулятор: SMS-ответ не отправлен" + overrideTag + ". Ответ: " + answer);
        } else {
            saveStatus("Ответ отправлен на " + sender + ", чат: " + activeChat
                    + ", модель: " + model + overrideTag
                    + ", частей SMS: " + parts + ". Ответ: " + answer);
        }
    }

    private static String describeOverrides(LlmMarkers markers) {
        if (!markers.forceWeb && !markers.forceThinking) {
            return "";
        }
        StringBuilder sb = new StringBuilder(", маркеры: ");
        if (markers.forceWeb) {
            sb.append("WEB");
        }
        if (markers.forceThinking) {
            if (markers.forceWeb) {
                sb.append('+');
            }
            sb.append("THINK");
        }
        return sb.toString();
    }

    private static String stringExtra(PersistableBundle extras, String key) {
        if (extras == null) {
            return "";
        }
        // PersistableBundle.getString(key, default) requires API 24; minSdk is 23.
        String value = extras.getString(key);
        return value != null ? value : "";
    }

    private int sendSms(SmsManager smsManager, String sender, String text) {
        if (isEmulator()) {
            Log.i(TAG, "Running on emulator, skipping outgoing SMS: " + text);
            return -1;
        }
        if (smsManager == null) {
            throw new RuntimeException("SmsManager недоступен");
        }
        ArrayList<String> parts;
        try {
            parts = smsManager.divideMessage(text);
        } catch (SecurityException e) {
            Log.w(TAG, "divideMessage denied, falling back to manual split", e);
            return sendSmsManual(smsManager, sender, text);
        } catch (Throwable e) {
            Log.e(TAG, "Failed to divide SMS for " + sender, e);
            throw new RuntimeException("Не удалось подготовить SMS: " + describeError(e), e);
        }
        try {
            smsManager.sendMultipartTextMessage(sender, null, parts, null, null);
        } catch (SecurityException e) {
            Log.w(TAG, "sendMultipartTextMessage denied, falling back to manual send", e);
            return sendSmsManual(smsManager, sender, text);
        } catch (Throwable e) {
            Log.e(TAG, "Failed to send SMS to " + sender, e);
            throw new RuntimeException("Не удалось отправить SMS: " + describeError(e), e);
        }
        return parts.size();
    }

    private int sendSmsManual(SmsManager smsManager, String sender, String text) {
        // 67 UTF-16 chars is safe for UCS-2 (cyrillic). Without divideMessage we can't
        // reliably detect GSM-7 vs UCS-2, so we take the conservative bound.
        final int partLength = 67;
        ArrayList<String> parts = new ArrayList<>();
        int length = text.length();
        for (int offset = 0; offset < length; ) {
            int end = Math.min(offset + partLength, length);
            // Don't split a surrogate pair across messages.
            if (end < length && Character.isHighSurrogate(text.charAt(end - 1))) {
                end -= 1;
            }
            if (end <= offset) {
                end = Math.min(offset + 1, length);
            }
            parts.add(text.substring(offset, end));
            offset = end;
        }
        if (parts.isEmpty()) {
            parts.add("");
        }
        try {
            for (String part : parts) {
                smsManager.sendTextMessage(sender, null, part, null, null);
            }
        } catch (Throwable e) {
            Log.e(TAG, "Manual sendTextMessage failed for " + sender, e);
            throw new RuntimeException("Не удалось отправить SMS вручную: " + describeError(e), e);
        }
        return parts.size();
    }

    private SmsManager resolveSmsManager(int subscriptionId) {
        SmsManager defaultManager = getDefaultSmsManager();

        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return defaultManager;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && defaultManager != null) {
                return defaultManager.createForSubscriptionId(subscriptionId);
            }
            return SmsManager.getSmsManagerForSubscriptionId(subscriptionId);
        } catch (Throwable e) {
            Log.w(TAG, "Cannot create SmsManager for subscription " + subscriptionId
                    + ", falling back to default", e);
            return defaultManager;
        }
    }

    private SmsManager getDefaultSmsManager() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                SmsManager managerFromService = getSystemService(SmsManager.class);
                if (managerFromService != null) {
                    return managerFromService;
                }
            } catch (Throwable e) {
                Log.w(TAG, "getSystemService(SmsManager.class) failed", e);
            }
        }
        try {
            return SmsManager.getDefault();
        } catch (Throwable e) {
            Log.e(TAG, "SmsManager.getDefault() failed", e);
            return null;
        }
    }

    private boolean isEmulator() {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT);
    }

    private String describeError(Throwable throwable) {
        if (throwable == null) {
            return "неизвестная ошибка";
        }
        StringBuilder result = new StringBuilder();
        result.append(throwable.getClass().getSimpleName());
        String message = throwable.getMessage();
        if (message != null && !message.trim().isEmpty()) {
            result.append(": ").append(message.trim());
        }
        String origin = topAppFrame(throwable);
        if (origin != null) {
            result.append(" @ ").append(origin);
        }
        Throwable cause = throwable.getCause();
        if (cause != null && cause != throwable) {
            result.append(" <- ").append(cause.getClass().getSimpleName());
            String causeMessage = cause.getMessage();
            if (causeMessage != null && !causeMessage.trim().isEmpty()) {
                result.append(": ").append(causeMessage.trim());
            }
        }
        return result.toString();
    }

    private String topAppFrame(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            StackTraceElement[] trace = current.getStackTrace();
            if (trace != null) {
                for (StackTraceElement frame : trace) {
                    String className = frame.getClassName();
                    if (className != null && className.startsWith("com.example.smsllmgateway")) {
                        String fileName = frame.getFileName();
                        return (fileName != null ? fileName : className)
                                + ":" + frame.getLineNumber()
                                + " " + frame.getMethodName();
                    }
                }
            }
            current = current.getCause();
        }
        return null;
    }

    private void saveStatus(String status) {
        String value = String.format(Locale.US, "%tF %<tT: %s", System.currentTimeMillis(), status);
        getSharedPreferences(GatewayConfig.PREFS, MODE_PRIVATE)
                .edit()
                .putString(GatewayConfig.KEY_LAST_STATUS, value)
                .apply();
    }
}
