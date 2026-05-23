package com.example.smsllmgateway;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ConversationStore {
    public static final String PREFS = "conversation_store";
    private static final String DEFAULT_CHAT = "main";

    private static final String[] PER_SENDER_SETTING_SUFFIXES = {
            GatewayConfig.SENDER_KEY_MODEL,
            GatewayConfig.SENDER_KEY_TOKENS,
            GatewayConfig.SENDER_KEY_TEMP,
            GatewayConfig.SENDER_KEY_THINK,
            GatewayConfig.SENDER_KEY_WEB,
    };

    private final SharedPreferences prefs;

    public ConversationStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getActiveChat(String sender) {
        return prefs.getString(key(sender, "active_chat"), DEFAULT_CHAT);
    }

    public void setActiveChat(String sender, String chatName) {
        String normalized = normalizeChatName(chatName);
        addChat(sender, normalized);
        prefs.edit().putString(key(sender, "active_chat"), normalized).apply();
    }

    public void addChat(String sender, String chatName) {
        String normalized = normalizeChatName(chatName);
        List<String> chats = listChats(sender);
        if (!chats.contains(normalized)) {
            chats.add(normalized);
            saveChats(sender, chats);
        }
    }

    public List<String> listChats(String sender) {
        ArrayList<String> result = new ArrayList<>();
        String raw = prefs.getString(key(sender, "chats"), "");
        if (raw != null && !raw.trim().isEmpty()) {
            String[] parts = raw.split("\\|");
            for (String part : parts) {
                String normalized = normalizeChatName(part);
                if (!normalized.isEmpty() && !result.contains(normalized)) {
                    result.add(normalized);
                }
            }
        }
        if (!result.contains(DEFAULT_CHAT)) {
            result.add(0, DEFAULT_CHAT);
        }
        return result;
    }

    public String getModel(String sender, String fallback) {
        String model = prefs.getString(key(sender, GatewayConfig.SENDER_KEY_MODEL), "");
        return model == null || model.trim().isEmpty() ? fallback : model.trim();
    }

    public void setModel(String sender, String model) {
        if (model == null) {
            return;
        }
        String trimmed = model.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        prefs.edit().putString(key(sender, GatewayConfig.SENDER_KEY_MODEL), trimmed).apply();
    }

    public int getTokens(String sender) {
        return prefs.getInt(key(sender, GatewayConfig.SENDER_KEY_TOKENS), GatewayConfig.DEFAULT_TOKENS);
    }

    public void setTokens(String sender, int value) {
        int clamped = Math.max(GatewayConfig.MIN_TOKENS, Math.min(GatewayConfig.MAX_TOKENS, value));
        prefs.edit().putInt(key(sender, GatewayConfig.SENDER_KEY_TOKENS), clamped).apply();
    }

    public double getTemperature(String sender) {
        // SharedPreferences has no double; store as float.
        return prefs.getFloat(
                key(sender, GatewayConfig.SENDER_KEY_TEMP),
                (float) GatewayConfig.DEFAULT_TEMPERATURE);
    }

    public void setTemperature(String sender, double value) {
        double clamped = Math.max(
                GatewayConfig.MIN_TEMPERATURE,
                Math.min(GatewayConfig.MAX_TEMPERATURE, value));
        prefs.edit().putFloat(key(sender, GatewayConfig.SENDER_KEY_TEMP), (float) clamped).apply();
    }

    public boolean getThinking(String sender) {
        return prefs.getBoolean(key(sender, GatewayConfig.SENDER_KEY_THINK), false);
    }

    public void setThinking(String sender, boolean value) {
        prefs.edit().putBoolean(key(sender, GatewayConfig.SENDER_KEY_THINK), value).apply();
    }

    public boolean getWebSearch(String sender) {
        return prefs.getBoolean(key(sender, GatewayConfig.SENDER_KEY_WEB), false);
    }

    public void setWebSearch(String sender, boolean value) {
        prefs.edit().putBoolean(key(sender, GatewayConfig.SENDER_KEY_WEB), value).apply();
    }

    /** Removes per-sender preferences (model/tokens/temp/think/web). Chats remain untouched. */
    public void resetSenderSettings(String sender) {
        SharedPreferences.Editor editor = prefs.edit();
        for (String suffix : PER_SENDER_SETTING_SUFFIXES) {
            editor.remove(key(sender, suffix));
        }
        editor.apply();
    }

    public JSONArray getRecentMessages(String sender, String chatName) {
        String raw = prefs.getString(messagesKey(sender, chatName), "[]");
        try {
            JSONArray source = new JSONArray(raw);
            JSONArray result = new JSONArray();
            int start = Math.max(0, source.length() - GatewayConfig.MAX_HISTORY_MESSAGES);
            for (int i = start; i < source.length(); i++) {
                result.put(source.getJSONObject(i));
            }
            return result;
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    public void appendExchange(String sender, String chatName, String userText, String assistantText) {
        JSONArray messages = getRecentMessages(sender, chatName);
        messages.put(message("user", userText));
        messages.put(message("assistant", assistantText));

        JSONArray trimmed = new JSONArray();
        int start = Math.max(0, messages.length() - GatewayConfig.MAX_HISTORY_MESSAGES);
        for (int i = start; i < messages.length(); i++) {
            trimmed.put(messages.optJSONObject(i));
        }

        prefs.edit().putString(messagesKey(sender, chatName), trimmed.toString()).apply();
    }

    public void clearChat(String sender, String chatName) {
        prefs.edit().remove(messagesKey(sender, chatName)).apply();
    }

    /**
     * Removes a chat completely: its messages and its entry in the chats list.
     * The default {@value #DEFAULT_CHAT} chat cannot be deleted and the method
     * returns {@code false} for it. Returns {@code false} if the chat wasn't
     * in the list. If the deleted chat was active, the active chat is reset
     * to {@value #DEFAULT_CHAT}.
     */
    public boolean deleteChat(String sender, String chatName) {
        String normalized = normalizeChatName(chatName);
        if (DEFAULT_CHAT.equals(normalized)) {
            return false;
        }

        List<String> chats = listChats(sender);
        boolean existed = chats.remove(normalized);
        if (existed) {
            saveChats(sender, chats);
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(messagesKey(sender, normalized));
        if (normalized.equals(getActiveChat(sender))) {
            editor.putString(key(sender, "active_chat"), DEFAULT_CHAT);
        }
        editor.apply();

        return existed;
    }

    private void saveChats(String sender, List<String> chats) {
        StringBuilder value = new StringBuilder();
        for (String chat : chats) {
            if (value.length() > 0) {
                value.append('|');
            }
            value.append(chat);
        }
        prefs.edit().putString(key(sender, "chats"), value.toString()).apply();
    }

    private JSONObject message(String role, String content) {
        JSONObject object = new JSONObject();
        try {
            object.put("role", role);
            object.put("content", content == null ? "" : content);
        } catch (JSONException ignored) {
        }
        return object;
    }

    public static String normalizeChatName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_CHAT;
        }
        // Locale.ROOT so chat ids stay ASCII-stable regardless of device locale
        // (Turkish locale famously turns "I" into "ı" via toLowerCase() default).
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9_-]", "-");
        normalized = normalized.replaceAll("-+", "-");
        normalized = normalized.replaceAll("^-+|-+$", "");
        if (normalized.length() > 24) {
            normalized = normalized.substring(0, 24);
        }
        return normalized.isEmpty() ? DEFAULT_CHAT : normalized;
    }

    private String messagesKey(String sender, String chatName) {
        return key(sender, "messages_" + normalizeChatName(chatName));
    }

    private String key(String sender, String suffix) {
        String normalizedSender = PhoneMatcher.normalize(sender);
        if (normalizedSender.isEmpty()) {
            normalizedSender = "unknown";
        }
        return normalizedSender + "_" + suffix;
    }

    static List<String> perSenderSettingSuffixesForTest() {
        return Arrays.asList(PER_SENDER_SETTING_SUFFIXES);
    }
}
