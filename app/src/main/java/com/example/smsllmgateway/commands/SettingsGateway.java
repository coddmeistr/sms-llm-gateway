package com.example.smsllmgateway.commands;

import java.util.List;

/**
 * Typed access to all SMS-configurable settings. Splits naturally into
 * global ones (preset, chars) and per-sender ones (model, tokens, temp, think, web).
 *
 * Pure Java interface; the production implementation backs onto SharedPreferences
 * and ConversationStore, while tests use {@code InMemorySettingsGateway}.
 */
public interface SettingsGateway {

    // --- Global settings ---

    String getPreset();

    void setPreset(String presetId);

    int getReplyChars();

    void setReplyChars(int chars);

    String getEndpoint();

    String getApiKey();

    String getAllowedSenders();

    void setAllowedSenders(String csv);

    String getDefaultModel();

    // --- Per-sender settings ---

    String getModel(String sender);

    void setModel(String sender, String modelId);

    int getTokens(String sender);

    void setTokens(String sender, int tokens);

    double getTemperature(String sender);

    void setTemperature(String sender, double temperature);

    boolean isThinking(String sender);

    void setThinking(String sender, boolean enabled);

    boolean isWebSearch(String sender);

    void setWebSearch(String sender, boolean enabled);

    /** Removes all per-sender preferences but keeps chats and message history. */
    void resetSender(String sender);

    // --- Chats (per-sender) ---

    String getActiveChat(String sender);

    void setActiveChat(String sender, String chatName);

    List<String> listChats(String sender);

    void clearActiveChat(String sender);

    /**
     * Permanently removes a chat (messages + entry in the list). The default
     * "main" chat cannot be removed; implementations return {@code false} in
     * that case. Returns {@code false} when the chat doesn't exist.
     */
    boolean deleteChat(String sender, String chatName);
}
