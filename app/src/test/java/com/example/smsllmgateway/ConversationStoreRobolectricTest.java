package com.example.smsllmgateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class ConversationStoreRobolectricTest {

    private static final String SENDER_A = "+79121111111";
    private static final String SENDER_B = "+79122222222";

    private ConversationStore store;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        // Wipe state across tests
        context.getSharedPreferences(ConversationStore.PREFS, Context.MODE_PRIVATE)
                .edit().clear().commit();
        store = new ConversationStore(context);
    }

    @Test
    public void defaultActiveChatIsMain() {
        assertEquals("main", store.getActiveChat(SENDER_A));
    }

    @Test
    public void setActiveChatAddsToListAndNormalizes() {
        store.setActiveChat(SENDER_A, "Work!");
        assertEquals("work", store.getActiveChat(SENDER_A));
        List<String> chats = store.listChats(SENDER_A);
        assertTrue(chats.contains("main"));
        assertTrue(chats.contains("work"));
    }

    @Test
    public void perSenderChatsAreIsolated() {
        store.setActiveChat(SENDER_A, "work");
        store.setActiveChat(SENDER_B, "home");
        List<String> chatsA = store.listChats(SENDER_A);
        List<String> chatsB = store.listChats(SENDER_B);
        assertTrue(chatsA.contains("work"));
        assertFalse(chatsA.contains("home"));
        assertTrue(chatsB.contains("home"));
        assertFalse(chatsB.contains("work"));
    }

    @Test
    public void appendExchangeKeepsAtMostMaxHistory() {
        for (int i = 0; i < GatewayConfig.MAX_HISTORY_MESSAGES; i++) {
            store.appendExchange(SENDER_A, "main", "q" + i, "a" + i);
        }
        JSONArray history = store.getRecentMessages(SENDER_A, "main");
        // Each appendExchange writes 2 messages (user+assistant), so the history is
        // capped at MAX_HISTORY_MESSAGES entries total.
        assertEquals(GatewayConfig.MAX_HISTORY_MESSAGES, history.length());
    }

    @Test
    public void clearChatRemovesOnlyThatChat() {
        store.appendExchange(SENDER_A, "main", "q1", "a1");
        store.appendExchange(SENDER_A, "work", "q2", "a2");
        store.clearChat(SENDER_A, "main");
        assertEquals(0, store.getRecentMessages(SENDER_A, "main").length());
        assertTrue(store.getRecentMessages(SENDER_A, "work").length() > 0);
    }

    @Test
    public void deleteChatRemovesFromListAndClearsMessages() {
        store.setActiveChat(SENDER_A, "work");
        store.appendExchange(SENDER_A, "work", "q1", "a1");
        assertTrue(store.listChats(SENDER_A).contains("work"));

        boolean removed = store.deleteChat(SENDER_A, "work");
        assertTrue(removed);
        assertFalse(store.listChats(SENDER_A).contains("work"));
        assertEquals(0, store.getRecentMessages(SENDER_A, "work").length());
    }

    @Test
    public void deleteChatResetsActiveIfDeletedChatWasActive() {
        store.setActiveChat(SENDER_A, "work");
        assertEquals("work", store.getActiveChat(SENDER_A));
        store.deleteChat(SENDER_A, "work");
        assertEquals("main", store.getActiveChat(SENDER_A));
    }

    @Test
    public void deleteChatKeepsActiveIfDeletedChatWasNotActive() {
        store.setActiveChat(SENDER_A, "work");
        store.setActiveChat(SENDER_A, "home");
        assertEquals("home", store.getActiveChat(SENDER_A));
        store.deleteChat(SENDER_A, "work");
        assertEquals("home", store.getActiveChat(SENDER_A));
    }

    @Test
    public void deleteChatMainIsRejected() {
        store.appendExchange(SENDER_A, "main", "q", "a");
        boolean removed = store.deleteChat(SENDER_A, "main");
        assertFalse("main must not be deletable", removed);
        assertTrue(store.listChats(SENDER_A).contains("main"));
        // history of main should be untouched
        assertTrue(store.getRecentMessages(SENDER_A, "main").length() > 0);
    }

    @Test
    public void deleteChatNormalizesName() {
        store.setActiveChat(SENDER_A, "Work!!");
        boolean removed = store.deleteChat(SENDER_A, "  work  ");
        assertTrue(removed);
        assertFalse(store.listChats(SENDER_A).contains("work"));
    }

    @Test
    public void deleteChatNonexistentReturnsFalse() {
        boolean removed = store.deleteChat(SENDER_A, "nope");
        assertFalse(removed);
    }

    @Test
    public void setModelIsNullAndEmptySafe() {
        store.setModel(SENDER_A, null);
        store.setModel(SENDER_A, "   ");
        assertEquals("fallback-model", store.getModel(SENDER_A, "fallback-model"));
    }

    @Test
    public void setModelTrimsValue() {
        store.setModel(SENDER_A, "  openai/gpt-4o-mini  ");
        assertEquals("openai/gpt-4o-mini", store.getModel(SENDER_A, "fallback"));
    }

    @Test
    public void tokensRoundTripAndClamp() {
        store.setTokens(SENDER_A, 500);
        assertEquals(500, store.getTokens(SENDER_A));

        store.setTokens(SENDER_A, -10);
        assertEquals(GatewayConfig.MIN_TOKENS, store.getTokens(SENDER_A));

        store.setTokens(SENDER_A, 1_000_000);
        assertEquals(GatewayConfig.MAX_TOKENS, store.getTokens(SENDER_A));
    }

    @Test
    public void temperatureRoundTripAndClamp() {
        store.setTemperature(SENDER_A, 0.9);
        assertEquals(0.9, store.getTemperature(SENDER_A), 0.001);

        store.setTemperature(SENDER_A, -1.0);
        assertEquals(GatewayConfig.MIN_TEMPERATURE, store.getTemperature(SENDER_A), 0.001);

        store.setTemperature(SENDER_A, 5.0);
        assertEquals(GatewayConfig.MAX_TEMPERATURE, store.getTemperature(SENDER_A), 0.001);
    }

    @Test
    public void thinkingAndWebSearchBooleans() {
        assertFalse(store.getThinking(SENDER_A));
        assertFalse(store.getWebSearch(SENDER_A));
        store.setThinking(SENDER_A, true);
        store.setWebSearch(SENDER_A, true);
        assertTrue(store.getThinking(SENDER_A));
        assertTrue(store.getWebSearch(SENDER_A));
    }

    @Test
    public void resetSenderSettingsClearsAllAndKeepsHistory() {
        store.setModel(SENDER_A, "openai/gpt-4o-mini");
        store.setTokens(SENDER_A, 400);
        store.setTemperature(SENDER_A, 0.9);
        store.setThinking(SENDER_A, true);
        store.setWebSearch(SENDER_A, true);
        store.appendExchange(SENDER_A, "main", "q", "a");

        store.resetSenderSettings(SENDER_A);

        assertEquals("fallback", store.getModel(SENDER_A, "fallback"));
        assertEquals(GatewayConfig.DEFAULT_TOKENS, store.getTokens(SENDER_A));
        assertEquals(GatewayConfig.DEFAULT_TEMPERATURE, store.getTemperature(SENDER_A), 0.001);
        assertFalse(store.getThinking(SENDER_A));
        assertFalse(store.getWebSearch(SENDER_A));
        // History preserved
        assertTrue(store.getRecentMessages(SENDER_A, "main").length() > 0);
    }

    @Test
    public void normalizeChatNameStripsSpecials() {
        assertEquals("work", ConversationStore.normalizeChatName("Work!!"));
        assertEquals("main", ConversationStore.normalizeChatName(null));
        assertEquals("main", ConversationStore.normalizeChatName(""));
        assertEquals("main", ConversationStore.normalizeChatName("   "));
        assertEquals("home-stuff", ConversationStore.normalizeChatName("Home Stuff"));
    }
}
