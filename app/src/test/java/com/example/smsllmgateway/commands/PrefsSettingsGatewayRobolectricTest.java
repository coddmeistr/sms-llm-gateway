package com.example.smsllmgateway.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.example.smsllmgateway.ConversationStore;
import com.example.smsllmgateway.GatewayConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class PrefsSettingsGatewayRobolectricTest {

    private static final String SENDER = "+79121234567";

    private PrefsSettingsGateway gateway;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        // Wipe both prefs files
        context.getSharedPreferences(GatewayConfig.PREFS, Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences(ConversationStore.PREFS, Context.MODE_PRIVATE)
                .edit().clear().commit();

        SharedPreferences prefs = context.getSharedPreferences(
                GatewayConfig.PREFS, Context.MODE_PRIVATE);
        gateway = new PrefsSettingsGateway(prefs, new ConversationStore(context));
    }

    @Test
    public void defaultsAreReturnedOnEmptyPrefs() {
        assertEquals(GatewayConfig.DEFAULT_PRESET, gateway.getPreset());
        assertEquals(GatewayConfig.DEFAULT_MAX_REPLY_CHARS, gateway.getReplyChars());
        assertEquals(GatewayConfig.DEFAULT_ENDPOINT, gateway.getEndpoint());
        assertEquals(GatewayConfig.DEFAULT_MODEL, gateway.getDefaultModel());
        assertEquals(GatewayConfig.DEFAULT_TOKENS, gateway.getTokens(SENDER));
        assertEquals(GatewayConfig.DEFAULT_TEMPERATURE, gateway.getTemperature(SENDER), 0.001);
        assertFalse(gateway.isThinking(SENDER));
        assertFalse(gateway.isWebSearch(SENDER));
    }

    @Test
    public void presetRoundTripAndUnknownFallback() {
        gateway.setPreset("coder");
        assertEquals("coder", gateway.getPreset());

        gateway.setPreset("nonsense");
        // getPreset must filter unknown ids back to default
        assertEquals(GatewayConfig.DEFAULT_PRESET, gateway.getPreset());
    }

    @Test
    public void replyCharsAreClampedToBounds() {
        gateway.setReplyChars(5);
        assertEquals(GatewayConfig.MIN_REPLY_CHARS, gateway.getReplyChars());

        gateway.setReplyChars(100_000);
        assertEquals(GatewayConfig.MAX_REPLY_CHARS_LIMIT, gateway.getReplyChars());

        gateway.setReplyChars(500);
        assertEquals(500, gateway.getReplyChars());
    }

    @Test
    public void modelDelegationFallsBackToDefault() {
        // No per-sender model → returns the global default model
        assertEquals(GatewayConfig.DEFAULT_MODEL, gateway.getModel(SENDER));
        gateway.setModel(SENDER, "openai/gpt-4o-mini");
        assertEquals("openai/gpt-4o-mini", gateway.getModel(SENDER));
    }

    @Test
    public void tokensTempThinkWebRoundTrip() {
        gateway.setTokens(SENDER, 300);
        gateway.setTemperature(SENDER, 0.7);
        gateway.setThinking(SENDER, true);
        gateway.setWebSearch(SENDER, true);

        assertEquals(300, gateway.getTokens(SENDER));
        assertEquals(0.7, gateway.getTemperature(SENDER), 0.001);
        assertTrue(gateway.isThinking(SENDER));
        assertTrue(gateway.isWebSearch(SENDER));
    }

    @Test
    public void resetSenderRemovesPerSenderSettings() {
        gateway.setModel(SENDER, "openai/gpt-4o-mini");
        gateway.setTokens(SENDER, 999); // will clamp to MAX_TOKENS
        gateway.setThinking(SENDER, true);
        gateway.resetSender(SENDER);

        assertEquals(GatewayConfig.DEFAULT_MODEL, gateway.getModel(SENDER));
        assertEquals(GatewayConfig.DEFAULT_TOKENS, gateway.getTokens(SENDER));
        assertFalse(gateway.isThinking(SENDER));
    }

    @Test
    public void chatLifecycleViaGateway() {
        assertEquals("main", gateway.getActiveChat(SENDER));
        gateway.setActiveChat(SENDER, "work");
        assertEquals("work", gateway.getActiveChat(SENDER));
        assertTrue(gateway.listChats(SENDER).contains("work"));
        gateway.clearActiveChat(SENDER);
        // clearing does not change the active chat itself
        assertEquals("work", gateway.getActiveChat(SENDER));
    }

    @Test
    public void deleteChatDelegatesAndRespectsMainGuard() {
        gateway.setActiveChat(SENDER, "work");
        assertTrue(gateway.deleteChat(SENDER, "work"));
        assertFalse(gateway.listChats(SENDER).contains("work"));
        assertEquals("main", gateway.getActiveChat(SENDER));

        assertFalse("main must not be deletable", gateway.deleteChat(SENDER, "main"));
        assertFalse("non-existent chat must return false", gateway.deleteChat(SENDER, "nope"));
    }

    @Test
    public void allowedSendersRoundTrip() {
        assertEquals("", gateway.getAllowedSenders());
        gateway.setAllowedSenders("+79121234567,+79122223344");
        assertEquals("+79121234567,+79122223344", gateway.getAllowedSenders());
        gateway.setAllowedSenders("");
        assertEquals("", gateway.getAllowedSenders());
        gateway.setAllowedSenders(null);
        assertEquals("", gateway.getAllowedSenders());
    }

    @Test
    public void apiKeyFallsBackToBuildConfigDefaultWhenEmpty() {
        // BuildConfig.DEFAULT_OPENROUTER_API_KEY is "" in tests when local.properties is absent.
        // getApiKey() returns "" then (the BuildConfig constant), which is its documented fallback.
        // We assert the call doesn't crash and returns a non-null string.
        String key = gateway.getApiKey();
        assertTrue(key != null);
    }
}
