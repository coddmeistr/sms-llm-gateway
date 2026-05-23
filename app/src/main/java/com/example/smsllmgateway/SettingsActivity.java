package com.example.smsllmgateway;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

public class SettingsActivity extends Activity {
    private EditText endpointInput;
    private EditText apiKeyInput;
    private EditText modelInput;
    private EditText allowedSendersInput;
    private EditText maxReplyCharsInput;
    private RadioGroup presetGroup;
    private TextView presetPreview;
    private TextView savedText;
    private final Handler autosaveHandler = new Handler(Looper.getMainLooper());
    private final Runnable autosaveRunnable = this::saveSettingsSilently;
    private boolean loading;
    private boolean dirty;
    private final Map<Integer, String> radioIdToPresetId = new HashMap<>();
    private final Map<String, Integer> presetIdToRadioId = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContentView());
        loadSettings();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSettingsSilently();
    }

    private ScrollView createContentView() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("Настройки");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, matchWrap());

        TextView hint = new TextView(this);
        hint.setText("Настройки сохраняются автоматически. Большинство параметров можно менять и по SMS — отправьте HELP.");
        hint.setPadding(0, 16, 0, 16);
        root.addView(hint, matchWrap());

        endpointInput = addInput(root, "LLM endpoint", false);
        apiKeyInput = addInput(root, "API key", true);
        modelInput = addInput(root, "Модель по умолчанию (id или openrouter/auto)", false);

        TextView presetLabel = new TextView(this);
        presetLabel.setText("Пресет системного промпта");
        presetLabel.setPadding(0, 24, 0, 8);
        root.addView(presetLabel, matchWrap());

        presetGroup = new RadioGroup(this);
        presetGroup.setOrientation(RadioGroup.VERTICAL);
        for (PromptPresets.Preset preset : PromptPresets.all()) {
            RadioButton button = new RadioButton(this);
            button.setText(preset.name + " (" + preset.id + ")");
            int viewId = android.view.View.generateViewId();
            button.setId(viewId);
            radioIdToPresetId.put(viewId, preset.id);
            presetIdToRadioId.put(preset.id, viewId);
            presetGroup.addView(button);
        }
        presetGroup.setOnCheckedChangeListener((group, checkedId) -> {
            updatePresetPreview();
            scheduleAutosave();
        });
        root.addView(presetGroup, matchWrap());

        presetPreview = new TextView(this);
        presetPreview.setPadding(16, 8, 16, 16);
        presetPreview.setTextSize(13);
        root.addView(presetPreview, matchWrap());

        allowedSendersInput = addInput(root, "Разрешенные номера через запятую, пусто = все", false);
        maxReplyCharsInput = addInput(root, "Максимум символов в одном ответе", false);
        maxReplyCharsInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        Button saveButton = new Button(this);
        saveButton.setText("Сохранить сейчас");
        saveButton.setOnClickListener(v -> {
            saveSettingsSilently();
            Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show();
        });
        root.addView(saveButton, matchWrap());

        Button backButton = new Button(this);
        backButton.setText("Назад");
        backButton.setOnClickListener(v -> finish());
        root.addView(backButton, matchWrap());

        savedText = new TextView(this);
        savedText.setPadding(0, 20, 0, 0);
        root.addView(savedText, matchWrap());

        return scrollView;
    }

    private EditText addInput(LinearLayout root, String hint, boolean password) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(false);
        if (password) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                scheduleAutosave();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        root.addView(input, matchWrap());
        return input;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void loadSettings() {
        loading = true;
        dirty = false;
        SharedPreferences prefs = getSharedPreferences(GatewayConfig.PREFS, MODE_PRIVATE);
        endpointInput.setText(prefs.getString(GatewayConfig.KEY_ENDPOINT, GatewayConfig.DEFAULT_ENDPOINT));
        String apiKey = prefs.getString(GatewayConfig.KEY_API_KEY, GatewayConfig.DEFAULT_API_KEY);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = GatewayConfig.DEFAULT_API_KEY;
        }
        apiKeyInput.setText(apiKey);
        modelInput.setText(prefs.getString(GatewayConfig.KEY_MODEL, GatewayConfig.DEFAULT_MODEL));

        String presetId = prefs.getString(GatewayConfig.KEY_PRESET, GatewayConfig.DEFAULT_PRESET);
        if (!PromptPresets.isKnown(presetId)) {
            presetId = GatewayConfig.DEFAULT_PRESET;
        }
        Integer radioId = presetIdToRadioId.get(presetId);
        if (radioId != null) {
            presetGroup.check(radioId);
        }
        updatePresetPreview();

        allowedSendersInput.setText(prefs.getString(GatewayConfig.KEY_ALLOWED_SENDERS, ""));
        maxReplyCharsInput.setText(String.valueOf(prefs.getInt(
                GatewayConfig.KEY_MAX_REPLY_CHARS,
                GatewayConfig.DEFAULT_MAX_REPLY_CHARS)));
        loading = false;
        updateSavedText("Готово");
    }

    private void updatePresetPreview() {
        if (presetPreview == null) {
            return;
        }
        String presetId = currentPresetId();
        PromptPresets.Preset preset = PromptPresets.byId(presetId);
        presetPreview.setText("Промпт: " + preset.prompt);
    }

    private String currentPresetId() {
        int checked = presetGroup.getCheckedRadioButtonId();
        String id = radioIdToPresetId.get(checked);
        return id != null ? id : GatewayConfig.DEFAULT_PRESET;
    }

    private void scheduleAutosave() {
        if (loading) {
            return;
        }
        dirty = true;
        updateSavedText("Сохраняю...");
        autosaveHandler.removeCallbacks(autosaveRunnable);
        autosaveHandler.postDelayed(autosaveRunnable, 600);
    }

    private void saveSettingsSilently() {
        if (loading) {
            return;
        }
        autosaveHandler.removeCallbacks(autosaveRunnable);
        // Если пользователь не редактировал поля — не перезаписываем prefs.
        // Иначе можно затереть изменения, прилетевшие по SMS (ALLOWED ADD, MODEL N и т.п.),
        // пока экран настроек был открыт.
        if (!dirty) {
            return;
        }

        int maxReplyChars = GatewayConfig.DEFAULT_MAX_REPLY_CHARS;
        try {
            maxReplyChars = Integer.parseInt(maxReplyCharsInput.getText().toString().trim());
        } catch (NumberFormatException ignored) {
            maxReplyCharsInput.setText(String.valueOf(maxReplyChars));
        }
        maxReplyChars = Math.max(
                GatewayConfig.MIN_REPLY_CHARS,
                Math.min(GatewayConfig.MAX_REPLY_CHARS_LIMIT, maxReplyChars));

        getSharedPreferences(GatewayConfig.PREFS, MODE_PRIVATE)
                .edit()
                .putString(GatewayConfig.KEY_ENDPOINT, endpointInput.getText().toString().trim())
                .putString(GatewayConfig.KEY_API_KEY, apiKeyInput.getText().toString().trim())
                .putString(GatewayConfig.KEY_MODEL, modelInput.getText().toString().trim())
                .putString(GatewayConfig.KEY_PRESET, currentPresetId())
                .putString(GatewayConfig.KEY_ALLOWED_SENDERS, allowedSendersInput.getText().toString())
                .putInt(GatewayConfig.KEY_MAX_REPLY_CHARS, maxReplyChars)
                .apply();

        dirty = false;
        updateSavedText("Сохранено автоматически");
    }

    private void updateSavedText(String text) {
        if (savedText != null) {
            savedText.setText(text);
        }
    }
}
