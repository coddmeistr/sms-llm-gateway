package com.example.smsllmgateway;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;

public class MainActivity extends Activity {
    private Switch enabledSwitch;
    private TextView summaryText;
    private TextView statusText;
    private final Handler statusRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            statusRefreshHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContentView());
        requestSmsPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMainState();
        refreshStatus();
        statusRefreshHandler.post(statusRefreshRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        statusRefreshHandler.removeCallbacks(statusRefreshRunnable);
    }

    private ScrollView createContentView() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("SMS LLM Gateway");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, matchWrap());

        TextView description = new TextView(this);
        description.setText("Телефон-шлюз принимает SMS, отправляет вопрос в LLM и возвращает ответ по SMS.");
        description.setPadding(0, 16, 0, 16);
        root.addView(description, matchWrap());

        enabledSwitch = new Switch(this);
        enabledSwitch.setText("Шлюз включен");
        enabledSwitch.setTextSize(18);
        enabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            getSharedPreferences(GatewayConfig.PREFS, MODE_PRIVATE)
                    .edit()
                    .putBoolean(GatewayConfig.KEY_ENABLED, isChecked)
                    .apply();
            refreshSummary();
        });
        root.addView(enabledSwitch, matchWrap());

        summaryText = new TextView(this);
        summaryText.setPadding(0, 16, 0, 16);
        root.addView(summaryText, matchWrap());

        Button settingsButton = new Button(this);
        settingsButton.setText("Настройки");
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        root.addView(settingsButton, matchWrap());

        Button permissionButton = new Button(this);
        permissionButton.setText("Запросить SMS-разрешения");
        permissionButton.setOnClickListener(v -> requestSmsPermissions());
        root.addView(permissionButton, matchWrap());

        Button testButton = new Button(this);
        testButton.setText("Тест LLM без SMS");
        testButton.setOnClickListener(v -> runLlmTest());
        root.addView(testButton, matchWrap());

        statusText = new TextView(this);
        statusText.setPadding(0, 24, 0, 0);
        root.addView(statusText, matchWrap());

        loadMainState();
        return scrollView;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void loadMainState() {
        SharedPreferences prefs = getSharedPreferences(GatewayConfig.PREFS, MODE_PRIVATE);
        enabledSwitch.setChecked(prefs.getBoolean(GatewayConfig.KEY_ENABLED, false));
        refreshSummary();
        refreshStatus();
    }

    private void refreshSummary() {
        if (summaryText == null) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences(GatewayConfig.PREFS, MODE_PRIVATE);
        String model = prefs.getString(GatewayConfig.KEY_MODEL, GatewayConfig.DEFAULT_MODEL);
        int maxChars = prefs.getInt(GatewayConfig.KEY_MAX_REPLY_CHARS, GatewayConfig.DEFAULT_MAX_REPLY_CHARS);
        String allowed = prefs.getString(GatewayConfig.KEY_ALLOWED_SENDERS, "");
        if (allowed == null || allowed.trim().isEmpty()) {
            allowed = "все номера";
        }
        summaryText.setText("Модель: " + model + "\nЛимит ответа: " + maxChars + " символов\nРазрешены: " + allowed + "\n\nSMS-команды: HELP, STATUS, CHAT LIST, CHAT NEW имя, CHAT USE имя, CHAT CLEAR, MODEL LIST, MODEL 1.");
    }

    private void refreshStatus() {
        if (statusText == null) {
            return;
        }
        String status = getSharedPreferences(GatewayConfig.PREFS, MODE_PRIVATE)
                .getString(GatewayConfig.KEY_LAST_STATUS, "Пока нет обработанных SMS");
        statusText.setText("Последний статус:\n" + status);
    }

    private void requestSmsPermissions() {
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        requestPermissions(new String[]{
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.SEND_SMS
        }, 100);
    }

    private void runLlmTest() {
        statusText.setText("Последний статус:\nПроверяю LLM...");

        new Thread(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences(GatewayConfig.PREFS, MODE_PRIVATE);
                String endpoint = prefs.getString(GatewayConfig.KEY_ENDPOINT, GatewayConfig.DEFAULT_ENDPOINT);
                String apiKey = prefs.getString(GatewayConfig.KEY_API_KEY, GatewayConfig.DEFAULT_API_KEY);
                if (apiKey == null || apiKey.isEmpty()) {
                    apiKey = GatewayConfig.DEFAULT_API_KEY;
                }
                String model = prefs.getString(GatewayConfig.KEY_MODEL, GatewayConfig.DEFAULT_MODEL);
                if (model == null || model.isEmpty()) {
                    model = GatewayConfig.DEFAULT_MODEL;
                }
                String systemPrompt = prefs.getString(
                        GatewayConfig.KEY_SYSTEM_PROMPT,
                        GatewayConfig.DEFAULT_SYSTEM_PROMPT);
                if (systemPrompt == null || systemPrompt.isEmpty()) {
                    systemPrompt = GatewayConfig.DEFAULT_SYSTEM_PROMPT;
                }

                String answer = LlmClient.requestChatCompletion(
                        endpoint,
                        apiKey,
                        model,
                        systemPrompt,
                        new JSONArray(),
                        "Ответь коротко простым текстом: тест связи успешен?");

                String finalAnswer = answer;
                runOnUiThread(() -> {
                    getSharedPreferences(GatewayConfig.PREFS, MODE_PRIVATE)
                            .edit()
                            .putString(GatewayConfig.KEY_LAST_STATUS, "LLM test OK: " + finalAnswer)
                            .apply();
                    refreshStatus();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    getSharedPreferences(GatewayConfig.PREFS, MODE_PRIVATE)
                            .edit()
                            .putString(GatewayConfig.KEY_LAST_STATUS, "LLM test error: " + e.getMessage())
                            .apply();
                    refreshStatus();
                });
            }
        }).start();
    }
}
