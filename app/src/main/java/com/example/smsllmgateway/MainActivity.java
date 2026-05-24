package com.example.smsllmgateway;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.example.smsllmgateway.commands.CommandRouter;
import com.example.smsllmgateway.keepalive.KeepAliveController;
import com.example.smsllmgateway.keepalive.OemAutostartHelper;

import org.json.JSONArray;

public class MainActivity extends Activity {
    private Switch enabledSwitch;
    private Switch keepAliveSwitch;
    private TextView summaryText;
    private TextView statusText;
    private TextView keepAliveStatusText;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener =
            (sharedPreferences, key) -> {
                if (GatewayConfig.KEY_LAST_STATUS.equals(key)) {
                    refreshStatus();
                } else if (GatewayConfig.KEY_ENABLED.equals(key)
                        || GatewayConfig.KEY_MODEL.equals(key)
                        || GatewayConfig.KEY_MAX_REPLY_CHARS.equals(key)
                        || GatewayConfig.KEY_ALLOWED_SENDERS.equals(key)
                        || GatewayConfig.KEY_PRESET.equals(key)) {
                    refreshSummary();
                } else if (GatewayConfig.KEY_KEEP_ALIVE.equals(key)) {
                    refreshKeepAliveStatus();
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
        getSharedPreferences(GatewayConfig.PREFS, MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(prefsListener);
        // Self-heal: if the user opens the app, ensure keep-alive is actually
        // running (it may have been killed by an OEM background-cleaner since
        // last launch). startForegroundService() from a visible activity is
        // always allowed, even on Android 12+.
        if (KeepAliveController.isEnabled(this)) {
            KeepAliveController.ensureStarted(this, "activity_resume");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        getSharedPreferences(GatewayConfig.PREFS, MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(prefsListener);
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
        });
        root.addView(enabledSwitch, matchWrap());

        keepAliveSwitch = new Switch(this);
        keepAliveSwitch.setText("Поддерживать работу 24/7");
        keepAliveSwitch.setTextSize(18);
        keepAliveSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                requestNotificationPermissionIfNeeded();
            }
            KeepAliveController.setEnabled(this, isChecked);
            refreshKeepAliveStatus();
        });
        root.addView(keepAliveSwitch, matchWrap());

        keepAliveStatusText = new TextView(this);
        keepAliveStatusText.setPadding(0, 4, 0, 16);
        keepAliveStatusText.setTextSize(12);
        root.addView(keepAliveStatusText, matchWrap());

        Button batteryButton = new Button(this);
        batteryButton.setText("Отключить экономию батареи для шлюза");
        batteryButton.setOnClickListener(v -> requestBatteryOptimizationExemption());
        root.addView(batteryButton, matchWrap());

        Button autostartButton = new Button(this);
        autostartButton.setText("Открыть autostart (OEM)");
        autostartButton.setOnClickListener(v -> openOemAutostartScreen());
        root.addView(autostartButton, matchWrap());

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
        keepAliveSwitch.setChecked(prefs.getBoolean(GatewayConfig.KEY_KEEP_ALIVE, false));
        refreshSummary();
        refreshStatus();
        refreshKeepAliveStatus();
    }

    private void refreshKeepAliveStatus() {
        if (keepAliveStatusText == null) {
            return;
        }
        boolean enabled = KeepAliveController.isEnabled(this);
        StringBuilder hint = new StringBuilder();
        hint.append(enabled
                ? "Keep-alive ON: foreground service + boot receiver + heartbeat."
                : "Keep-alive OFF: ОС может усыпить процесс в любой момент.");
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            boolean ignoring = pm.isIgnoringBatteryOptimizations(getPackageName());
            hint.append("\nЭкономия батареи: ")
                    .append(ignoring ? "отключена" : "ВКЛЮЧЕНА (нажмите кнопку ниже)");
        }
        hint.append("\nПроизводитель: ").append(OemAutostartHelper.describeCurrentManufacturer());
        keepAliveStatusText.setText(hint.toString());
    }

    @SuppressWarnings("BatteryLife")
    private void requestBatteryOptimizationExemption() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Toast.makeText(this, "Уже отключена", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent direct = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        direct.setData(Uri.parse("package:" + getPackageName()));
        try {
            startActivity(direct);
            return;
        } catch (ActivityNotFoundException ignored) {
            // Some OEMs hide this intent. Fall back to the general list.
        } catch (SecurityException ignored) {
            // Play Policy can revoke the permission silently.
        }
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Throwable e) {
            Toast.makeText(this, "Не удалось открыть настройки батареи: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void openOemAutostartScreen() {
        Intent intent = OemAutostartHelper.buildAutostartIntent(this);
        if (intent == null) {
            Toast.makeText(this, "Контекст приложения недоступен", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(intent);
        } catch (Throwable e) {
            Toast.makeText(this,
                    "Не удалось открыть autostart: " + e.getMessage()
                            + ". Откройте настройки приложения вручную.",
                    Toast.LENGTH_LONG).show();
            try {
                startActivity(OemAutostartHelper.fallbackAppDetailsIntent(getPackageName()));
            } catch (Throwable ignored) {
            }
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
    }

    private void refreshSummary() {
        if (summaryText == null) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences(GatewayConfig.PREFS, MODE_PRIVATE);
        String model = prefs.getString(GatewayConfig.KEY_MODEL, GatewayConfig.DEFAULT_MODEL);
        int maxChars = prefs.getInt(GatewayConfig.KEY_MAX_REPLY_CHARS, GatewayConfig.DEFAULT_MAX_REPLY_CHARS);
        String preset = prefs.getString(GatewayConfig.KEY_PRESET, GatewayConfig.DEFAULT_PRESET);
        String allowed = prefs.getString(GatewayConfig.KEY_ALLOWED_SENDERS, "");
        if (allowed == null || allowed.trim().isEmpty()) {
            allowed = "все номера";
        }
        summaryText.setText(
                "Модель по умолчанию: " + model
                        + "\nПресет: " + preset
                        + "\nЛимит ответа: " + maxChars + " символов"
                        + "\nРазрешены: " + allowed
                        + "\n\nSMS-команды: " + CommandRouter.helpText());
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
        boolean smsOk = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
        boolean notifOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        if (smsOk && notifOk) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.POST_NOTIFICATIONS
            }, 100);
        } else {
            requestPermissions(new String[]{
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.READ_PHONE_STATE
            }, 100);
        }
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
                String preset = prefs.getString(GatewayConfig.KEY_PRESET, GatewayConfig.DEFAULT_PRESET);
                int maxChars = prefs.getInt(GatewayConfig.KEY_MAX_REPLY_CHARS, GatewayConfig.DEFAULT_MAX_REPLY_CHARS);

                String systemPrompt = PromptPresets.composeSystemPrompt(
                        preset, maxChars, GatewayConfig.DEFAULT_TOKENS);

                LlmOptions options = LlmOptions.defaults();
                String answer = LlmClient.requestChatCompletion(
                        endpoint,
                        apiKey,
                        model,
                        systemPrompt,
                        new JSONArray(),
                        "Ответь коротко простым текстом: тест связи успешен?",
                        options);

                String finalAnswer = answer;
                runOnUiThread(() -> {
                    getSharedPreferences(GatewayConfig.PREFS, MODE_PRIVATE)
                            .edit()
                            .putString(GatewayConfig.KEY_LAST_STATUS, "LLM test OK: " + finalAnswer)
                            .apply();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    getSharedPreferences(GatewayConfig.PREFS, MODE_PRIVATE)
                            .edit()
                            .putString(GatewayConfig.KEY_LAST_STATUS, "LLM test error: " + e.getMessage())
                            .apply();
                });
            }
        }).start();
    }
}
