package com.example.smsllmgateway;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class ConversationStore {
    private static final String PREFS = "conversation_store";
    private static final String DEFAULT_CHAT = "main";
    private static final String DEFAULT_MODEL = "";

    private final SharedPreferences prefs;

    ConversationStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    String getActiveChat(String sender) {
        return prefs.getString(key(sender, "active_chat"), DEFAULT_CHAT);
    }

    void setActiveChat(String sender, String chatName) {
        String normalized = normalizeChatName(chatName);
        addChat(sender, normalized);
        prefs.edit().putString(key(sender, "active_chat"), normalized).apply();
    }

    void addChat(String sender, String chatName) {
        String normalized = normalizeChatName(chatName);
        List<String> chats = listChats(sender);
        if (!chats.contains(normalized)) {
            chats.add(normalized);
            saveChats(sender, chats);
        }
    }

    List<String> listChats(String sender) {
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

    String getModel(String sender, String fallback) {
        String model = prefs.getString(key(sender, "model"), DEFAULT_MODEL);
        return model == null || model.trim().isEmpty() ? fallback : model.trim();
    }

    void setModel(String sender, String model) {
        prefs.edit().putString(key(sender, "model"), model.trim()).apply();
    }

    JSONArray getRecentMessages(String sender, String chatName) {
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

    void appendExchange(String sender, String chatName, String userText, String assistantText) {
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

    void clearChat(String sender, String chatName) {
        prefs.edit().remove(messagesKey(sender, chatName)).apply();
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
            object.put("content", content);
        } catch (JSONException ignored) {
        }
        return object;
    }

    private String normalizeChatName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_CHAT;
        }
        String normalized = value.trim().toLowerCase();
        normalized = normalized.replaceAll("[^a-z0-9_-]", "-");
        normalized = normalized.replaceAll("-+", "-");
        if (normalized.length() > 24) {
            normalized = normalized.substring(0, 24);
        }
        return normalized.isEmpty() ? DEFAULT_CHAT : normalized;
    }

    private String messagesKey(String sender, String chatName) {
        return key(sender, "messages_" + normalizeChatName(chatName));
    }

    private String key(String sender, String suffix) {
        String normalizedSender = sender == null ? "unknown" : sender.replaceAll("[^0-9+]", "");
        if (normalizedSender.isEmpty()) {
            normalizedSender = "unknown";
        }
        return normalizedSender + "_" + suffix;
    }
}
