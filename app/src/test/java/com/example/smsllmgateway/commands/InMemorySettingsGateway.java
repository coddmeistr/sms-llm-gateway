package com.example.smsllmgateway.commands;

import com.example.smsllmgateway.GatewayConfig;
import com.example.smsllmgateway.PhoneMatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Test fake for {@link SettingsGateway}. */
public final class InMemorySettingsGateway implements SettingsGateway {

    private String preset = GatewayConfig.DEFAULT_PRESET;
    private int chars = GatewayConfig.DEFAULT_MAX_REPLY_CHARS;
    private String endpoint = GatewayConfig.DEFAULT_ENDPOINT;
    private String apiKey = "";
    private String allowedSenders = "";
    private String defaultModel = GatewayConfig.DEFAULT_MODEL;

    private final Map<String, String> model = new HashMap<>();
    private final Map<String, Integer> tokens = new HashMap<>();
    private final Map<String, Double> temperature = new HashMap<>();
    private final Map<String, Boolean> thinking = new HashMap<>();
    private final Map<String, Boolean> webSearch = new HashMap<>();

    private final Map<String, String> activeChat = new LinkedHashMap<>();
    private final Map<String, LinkedHashSet<String>> chats = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> messages = new LinkedHashMap<>();

    @Override
    public String getPreset() {
        return preset;
    }

    @Override
    public void setPreset(String presetId) {
        preset = presetId;
    }

    @Override
    public int getReplyChars() {
        return chars;
    }

    @Override
    public void setReplyChars(int chars) {
        this.chars = chars;
    }

    @Override
    public String getEndpoint() {
        return endpoint;
    }

    @Override
    public String getApiKey() {
        return apiKey;
    }

    @Override
    public String getAllowedSenders() {
        return allowedSenders;
    }

    @Override
    public void setAllowedSenders(String csv) {
        allowedSenders = csv == null ? "" : csv;
    }

    @Override
    public String getDefaultModel() {
        return defaultModel;
    }

    @Override
    public String getModel(String sender) {
        String value = model.get(normalize(sender));
        return value != null ? value : defaultModel;
    }

    @Override
    public void setModel(String sender, String modelId) {
        if (modelId == null || modelId.trim().isEmpty()) {
            return;
        }
        model.put(normalize(sender), modelId.trim());
    }

    @Override
    public int getTokens(String sender) {
        Integer v = tokens.get(normalize(sender));
        return v != null ? v : GatewayConfig.DEFAULT_TOKENS;
    }

    @Override
    public void setTokens(String sender, int t) {
        tokens.put(normalize(sender), t);
    }

    @Override
    public double getTemperature(String sender) {
        Double v = temperature.get(normalize(sender));
        return v != null ? v : GatewayConfig.DEFAULT_TEMPERATURE;
    }

    @Override
    public void setTemperature(String sender, double t) {
        temperature.put(normalize(sender), t);
    }

    @Override
    public boolean isThinking(String sender) {
        Boolean v = thinking.get(normalize(sender));
        return v != null && v;
    }

    @Override
    public void setThinking(String sender, boolean enabled) {
        thinking.put(normalize(sender), enabled);
    }

    @Override
    public boolean isWebSearch(String sender) {
        Boolean v = webSearch.get(normalize(sender));
        return v != null && v;
    }

    @Override
    public void setWebSearch(String sender, boolean enabled) {
        webSearch.put(normalize(sender), enabled);
    }

    @Override
    public void resetSender(String sender) {
        String key = normalize(sender);
        model.remove(key);
        tokens.remove(key);
        temperature.remove(key);
        thinking.remove(key);
        webSearch.remove(key);
    }

    @Override
    public String getActiveChat(String sender) {
        String key = normalize(sender);
        String value = activeChat.get(key);
        return value != null ? value : "main";
    }

    @Override
    public void setActiveChat(String sender, String chatName) {
        String key = normalize(sender);
        String chat = chatName == null || chatName.trim().isEmpty()
                ? "main"
                : chatName.trim().toLowerCase(Locale.ROOT);
        chat = chat.replaceAll("[^a-z0-9_-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-+|-+$", "");
        if (chat.isEmpty()) {
            chat = "main";
        }
        activeChat.put(key, chat);
        LinkedHashSet<String> set = chats.get(key);
        if (set == null) {
            set = new LinkedHashSet<>();
            chats.put(key, set);
        }
        set.add(chat);
    }

    @Override
    public List<String> listChats(String sender) {
        String key = normalize(sender);
        LinkedHashSet<String> set = chats.get(key);
        List<String> out = new ArrayList<>();
        out.add("main");
        if (set != null) {
            for (String chat : set) {
                if (!out.contains(chat)) {
                    out.add(chat);
                }
            }
        }
        return Collections.unmodifiableList(out);
    }

    @Override
    public void clearActiveChat(String sender) {
        String key = normalize(sender);
        Map<String, String> chatMessages = messages.get(key);
        if (chatMessages != null) {
            chatMessages.remove(getActiveChat(sender));
        }
    }

    @Override
    public boolean deleteChat(String sender, String chatName) {
        if (chatName == null) {
            return false;
        }
        String normalized = chatName.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.isEmpty() || "main".equals(normalized)) {
            return false;
        }
        String key = normalize(sender);
        LinkedHashSet<String> set = chats.get(key);
        boolean existed = set != null && set.remove(normalized);
        Map<String, String> chatMessages = messages.get(key);
        if (chatMessages != null) {
            chatMessages.remove(normalized);
        }
        if (normalized.equals(activeChat.get(key))) {
            activeChat.put(key, "main");
        }
        return existed;
    }

    private static String normalize(String sender) {
        return PhoneMatcher.normalize(sender);
    }

    public void setEndpoint(String value) {
        endpoint = value;
    }

    public void setApiKey(String value) {
        apiKey = value;
    }
}
