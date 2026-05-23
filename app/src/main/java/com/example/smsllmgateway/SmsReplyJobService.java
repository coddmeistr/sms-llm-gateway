package com.example.smsllmgateway;

import android.Manifest;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SubscriptionManager;
import android.telephony.SmsManager;
import android.util.Log;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SmsReplyJobService extends JobService {
    private static final String TAG = "SmsReplyJobService";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public boolean onStartJob(JobParameters params) {
        executor.execute(() -> {
            try {
                handleMessage(params);
            } catch (Throwable e) {
                Log.e(TAG, "Failed to process SMS", e);
                saveStatus("Ошибка обработки SMS: " + safeMessage(e));
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
        String sender = params.getExtras().getString(SmsReceiver.EXTRA_SENDER, "");
        String body = params.getExtras().getString(SmsReceiver.EXTRA_BODY, "").trim();
        int subscriptionId = params.getExtras().getInt(
                SmsReceiver.EXTRA_SUBSCRIPTION_ID,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        SharedPreferences prefs = getSharedPreferences(GatewayConfig.PREFS, MODE_PRIVATE);
        ConversationStore conversations = new ConversationStore(this);

        if (!prefs.getBoolean(GatewayConfig.KEY_ENABLED, false)) {
            saveStatus("Получено SMS от " + sender + ", но шлюз выключен");
            return;
        }

        if (!isSenderAllowed(sender, prefs.getString(GatewayConfig.KEY_ALLOWED_SENDERS, ""))) {
            saveStatus("SMS от " + sender + " отклонено по списку разрешенных номеров");
            return;
        }

        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Нет разрешения SEND_SMS");
        }

        SmsManager smsManager = subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                ? SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
                : SmsManager.getDefault();

        String commandReply = handleCommand(conversations, sender, body);
        if (commandReply != null) {
            int parts = sendSms(smsManager, sender, commandReply);
            if (parts < 0) {
                saveStatus("Эмулятор: SMS-ответ не отправлен. Ответ: " + commandReply);
            } else {
                saveStatus("Команда обработана для " + sender + ", частей SMS: " + parts + ". Ответ: " + commandReply);
            }
            return;
        }

        String endpoint = prefs.getString(GatewayConfig.KEY_ENDPOINT, GatewayConfig.DEFAULT_ENDPOINT);
        String apiKey = prefs.getString(GatewayConfig.KEY_API_KEY, GatewayConfig.DEFAULT_API_KEY);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = GatewayConfig.DEFAULT_API_KEY;
        }
        String fallbackModel = prefs.getString(GatewayConfig.KEY_MODEL, GatewayConfig.DEFAULT_MODEL);
        String model = conversations.getModel(sender, fallbackModel);
        String systemPrompt = buildSystemPrompt(prefs.getString(
                GatewayConfig.KEY_SYSTEM_PROMPT,
                GatewayConfig.DEFAULT_SYSTEM_PROMPT));
        String activeChat = conversations.getActiveChat(sender);
        JSONArray history = conversations.getRecentMessages(sender, activeChat);

        String answer;
        try {
            answer = LlmClient.requestChatCompletion(endpoint, apiKey, model, systemPrompt, history, body);
        } catch (Exception e) {
            answer = "Не удалось получить ответ от нейросети. Попробуйте еще раз или выберите другую модель командой MODEL LIST.";
            int parts = sendSms(smsManager, sender, answer);
            saveStatus("Ошибка LLM для " + sender + ": " + safeMessage(e) + ". Отправлен fallback, частей SMS: " + parts);
            return;
        }
        int maxChars = prefs.getInt(
                GatewayConfig.KEY_MAX_REPLY_CHARS,
                GatewayConfig.DEFAULT_MAX_REPLY_CHARS);
        answer = limit(cleanPlainText(answer), maxChars);
        if (answer.trim().isEmpty() || "null".equalsIgnoreCase(answer.trim())) {
            answer = "Нейросеть вернула пустой ответ. Попробуйте еще раз или выберите другую модель командой MODEL LIST.";
        }

        conversations.appendExchange(sender, activeChat, body, answer);
        int parts = sendSms(smsManager, sender, answer);
        if (parts < 0) {
            saveStatus("Эмулятор: SMS-ответ не отправлен. Ответ: " + answer);
        } else {
            saveStatus("Ответ отправлен на " + sender + ", чат: " + activeChat + ", модель: " + model + ", частей SMS: " + parts + ". Ответ: " + answer);
        }
    }

    private String handleCommand(ConversationStore conversations, String sender, String body) {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.isEmpty()) {
            return "Отправьте вопрос или HELP для списка команд.";
        }

        String upper = trimmed.toUpperCase(Locale.US);
        if ("HELP".equals(upper) || "/HELP".equals(upper) || "ПОМОЩЬ".equals(upper)) {
            return "Команды: STATUS; CHAT LIST; CHAT NEW имя; CHAT USE имя; CHAT CLEAR; MODEL LIST; MODEL 1. Для выбора модели отправьте MODEL LIST, затем MODEL номер.";
        }

        if ("STATUS".equals(upper) || "/STATUS".equals(upper)) {
            String chat = conversations.getActiveChat(sender);
            String model = conversations.getModel(sender, GatewayConfig.DEFAULT_MODEL);
            return "Статус: включено. Чат: " + chat + ". Модель: " + model + ".";
        }

        if ("CHAT LIST".equals(upper) || "/CHAT LIST".equals(upper)) {
            List<String> chats = conversations.listChats(sender);
            return "Чаты: " + join(chats) + ". Текущий: " + conversations.getActiveChat(sender) + ".";
        }

        if (upper.startsWith("CHAT NEW ") || upper.startsWith("/CHAT NEW ")) {
            String name = trimmed.substring(trimmed.toUpperCase(Locale.US).indexOf("CHAT NEW ") + 9).trim();
            conversations.setActiveChat(sender, name);
            return "Создан и выбран чат: " + conversations.getActiveChat(sender) + ".";
        }

        if (upper.startsWith("CHAT USE ") || upper.startsWith("/CHAT USE ")) {
            String name = trimmed.substring(trimmed.toUpperCase(Locale.US).indexOf("CHAT USE ") + 9).trim();
            conversations.setActiveChat(sender, name);
            return "Выбран чат: " + conversations.getActiveChat(sender) + ".";
        }

        if ("CHAT CLEAR".equals(upper) || "/CHAT CLEAR".equals(upper)) {
            String chat = conversations.getActiveChat(sender);
            conversations.clearChat(sender, chat);
            return "История текущего чата очищена: " + chat + ".";
        }

        if ("MODEL LIST".equals(upper) || "/MODEL LIST".equals(upper)) {
            return "Доступные модели:\n" + numberedModelList() + "\nВыбор: отправьте MODEL 1, MODEL 2 или MODEL 3.";
        }

        if (upper.matches("/?MODEL\\s+\\d+")) {
            String numberText = upper.replace("/", "").replace("MODEL", "").trim();
            return selectModelByNumber(conversations, sender, numberText);
        }

        if (upper.startsWith("MODEL USE ") || upper.startsWith("/MODEL USE ")) {
            String model = trimmed.substring(upper.indexOf("MODEL USE ") + 10).trim();
            if (model.isEmpty()) {
                return "Укажите номер или id модели. Примеры: MODEL 1 или MODEL USE openrouter/free.";
            }
            if (model.matches("\\d+")) {
                return selectModelByNumber(conversations, sender, model);
            }
            conversations.setModel(sender, model);
            return "Выбрана модель: " + model + ".";
        }

        return null;
    }

    private int sendSms(SmsManager smsManager, String sender, String text) {
        if (isEmulator()) {
            Log.i(TAG, "Running on emulator, skipping outgoing SMS: " + text);
            return -1;
        }
        ArrayList<String> parts = smsManager.divideMessage(text);
        smsManager.sendMultipartTextMessage(sender, null, parts, null, null);
        return parts.size();
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

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }

    private String buildSystemPrompt(String savedPrompt) {
        String base = savedPrompt == null || savedPrompt.trim().isEmpty()
                ? GatewayConfig.DEFAULT_SYSTEM_PROMPT
                : savedPrompt.trim();
        return base + "\n\nMandatory SMS rules: plain text only; no emoji; no markdown; no lists unless necessary; answer in 1-4 short sentences by default.";
    }

    private String cleanPlainText(String value) {
        if (value == null) {
            return "";
        }

        String cleaned = value
                .replace("```", "")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("(?m)^>\\s*", "")
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        StringBuilder result = new StringBuilder();
        for (int offset = 0; offset < cleaned.length(); ) {
            int codePoint = cleaned.codePointAt(offset);
            offset += Character.charCount(codePoint);
            int type = Character.getType(codePoint);
            if (type == Character.SURROGATE
                    || type == Character.PRIVATE_USE
                    || type == Character.UNASSIGNED
                    || type == Character.OTHER_SYMBOL) {
                continue;
            }
            result.appendCodePoint(codePoint);
        }
        return result.toString().trim();
    }

    private String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(value);
        }
        return result.toString();
    }

    private String join(String[] values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(value);
        }
        return result.toString();
    }

    private String numberedModelList() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < GatewayConfig.SUGGESTED_MODELS.length; i++) {
            if (result.length() > 0) {
                result.append('\n');
            }
            String name = i < GatewayConfig.SUGGESTED_MODEL_NAMES.length
                    ? GatewayConfig.SUGGESTED_MODEL_NAMES[i]
                    : GatewayConfig.SUGGESTED_MODELS[i];
            result.append(i + 1)
                    .append(". ")
                    .append(name)
                    .append(" (")
                    .append(GatewayConfig.SUGGESTED_MODELS[i])
                    .append(")");
        }
        return result.toString();
    }

    private String selectModelByNumber(ConversationStore conversations, String sender, String numberText) {
        int index;
        try {
            index = Integer.parseInt(numberText.trim()) - 1;
        } catch (NumberFormatException e) {
            return "Не понял номер модели. Отправьте MODEL LIST, затем MODEL 1.";
        }

        if (index < 0 || index >= GatewayConfig.SUGGESTED_MODELS.length) {
            return "Нет модели с таким номером. Отправьте MODEL LIST и выберите номер из списка.";
        }

        String model = GatewayConfig.SUGGESTED_MODELS[index];
        conversations.setModel(sender, model);
        String name = index < GatewayConfig.SUGGESTED_MODEL_NAMES.length
                ? GatewayConfig.SUGGESTED_MODEL_NAMES[index]
                : model;
        return "Выбрана модель " + (index + 1) + ": " + name + ".";
    }

    private boolean isSenderAllowed(String sender, String allowedSenders) {
        if (allowedSenders == null || allowedSenders.trim().isEmpty()) {
            return true;
        }

        String normalizedSender = normalizePhone(sender);
        String[] entries = allowedSenders.split("[,;\\n\\r]+");
        for (String entry : entries) {
            String normalizedEntry = normalizePhone(entry);
            if (!normalizedEntry.isEmpty()
                    && (normalizedSender.equals(normalizedEntry)
                    || normalizedSender.endsWith(normalizedEntry)
                    || normalizedEntry.endsWith(normalizedSender))) {
                return true;
            }
        }
        return false;
    }

    private String normalizePhone(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isDigit(c) || (c == '+' && result.length() == 0)) {
                result.append(c);
            }
        }
        return result.toString();
    }

    private String limit(String value, int maxChars) {
        if (maxChars <= 0 || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "\n\n[Ответ обрезан]";
    }

    private void saveStatus(String status) {
        String value = String.format(Locale.US, "%tF %<tT: %s", System.currentTimeMillis(), status);
        getSharedPreferences(GatewayConfig.PREFS, MODE_PRIVATE)
                .edit()
                .putString(GatewayConfig.KEY_LAST_STATUS, value)
                .apply();
    }
}
