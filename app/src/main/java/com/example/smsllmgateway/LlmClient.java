package com.example.smsllmgateway;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class LlmClient {
    private LlmClient() {
    }

    static String requestChatCompletion(
            String endpoint,
            String apiKey,
            String model,
            String systemPrompt,
            JSONArray history,
            String userMessage) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("model", model);
        payload.put("temperature", 0.4);
        payload.put("max_tokens", 220);

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject()
                .put("role", "system")
                .put("content", systemPrompt));
        if (history != null) {
            for (int i = 0; i < history.length(); i++) {
                JSONObject message = history.optJSONObject(i);
                if (message != null) {
                    messages.put(message);
                }
            }
        }
        messages.put(new JSONObject()
                .put("role", "user")
                .put("content", userMessage));
        payload.put("messages", messages);

        byte[] requestBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(90_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("X-Title", "SMS LLM Gateway");
        connection.setRequestProperty("HTTP-Referer", "https://local.sms-llm-gateway");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
        }

        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(requestBytes);
        }

        int code = connection.getResponseCode();
        String response = readFully(code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream());

        if (code < 200 || code >= 300) {
            throw new IOException("LLM HTTP " + code + ": " + response);
        }

        JSONObject json = new JSONObject(response);
        JSONArray choices = json.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new IOException("LLM response has no choices: " + response);
        }

        JSONObject choice = choices.getJSONObject(0);
        String content = extractText(choice).trim();
        if (isEmptyContent(content)) {
            throw new IOException("LLM response is empty");
        }
        return content;
    }

    private static String extractText(JSONObject choice) {
        JSONObject message = choice.optJSONObject("message");
        if (message != null) {
            String content = valueToText(message.opt("content"));
            if (!isEmptyContent(content)) {
                return content;
            }

            String reasoning = valueToText(message.opt("reasoning"));
            if (!isEmptyContent(reasoning)) {
                return reasoning;
            }

            String refusal = valueToText(message.opt("refusal"));
            if (!isEmptyContent(refusal)) {
                return refusal;
            }
        }

        String text = valueToText(choice.opt("text"));
        if (!isEmptyContent(text)) {
            return text;
        }

        return "";
    }

    private static String valueToText(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "";
        }

        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                if (item instanceof JSONObject) {
                    String text = valueToText(((JSONObject) item).opt("text"));
                    if (!isEmptyContent(text)) {
                        if (result.length() > 0) {
                            result.append('\n');
                        }
                        result.append(text);
                    }
                } else {
                    String text = valueToText(item);
                    if (!isEmptyContent(text)) {
                        if (result.length() > 0) {
                            result.append('\n');
                        }
                        result.append(text);
                    }
                }
            }
            return result.toString();
        }

        return String.valueOf(value);
    }

    private static boolean isEmptyContent(String content) {
        if (content == null) {
            return true;
        }

        String trimmed = content.trim();
        return trimmed.isEmpty()
                || "null".equalsIgnoreCase(trimmed)
                || "undefined".equalsIgnoreCase(trimmed);
    }

    private static String readFully(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }
        return result.toString();
    }
}
