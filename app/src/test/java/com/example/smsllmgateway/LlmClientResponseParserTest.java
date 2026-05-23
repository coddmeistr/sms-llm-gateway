package com.example.smsllmgateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;

/**
 * Runs under Robolectric so we get a real {@code org.json} implementation
 * (the Android stub bundled with the SDK throws "Stub!" at runtime).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class LlmClientResponseParserTest {

    @Test
    public void parsesPlainStringContent() throws Exception {
        String body = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Hello world\"}}]}";
        assertEquals("Hello world", LlmClient.parseAnswer(body));
    }

    @Test
    public void parsesMultimodalContentArray() throws Exception {
        String body = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":["
                + "{\"type\":\"text\",\"text\":\"first\"},"
                + "{\"type\":\"text\",\"text\":\"second\"}"
                + "]}}]}";
        String answer = LlmClient.parseAnswer(body);
        assertTrue("expected first part: " + answer, answer.contains("first"));
        assertTrue("expected second part: " + answer, answer.contains("second"));
    }

    @Test
    public void fallsBackToReasoningWhenContentIsEmpty() throws Exception {
        String body = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"\",\"reasoning\":\"thought\"}}]}";
        assertEquals("thought", LlmClient.parseAnswer(body));
    }

    @Test
    public void fallsBackToRefusalWhenOtherFieldsEmpty() throws Exception {
        String body = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"refusal\":\"sorry\"}}]}";
        assertEquals("sorry", LlmClient.parseAnswer(body));
    }

    @Test
    public void usesTopLevelTextField() throws Exception {
        String body = "{\"choices\":[{\"text\":\"legacy completion\"}]}";
        assertEquals("legacy completion", LlmClient.parseAnswer(body));
    }

    @Test
    public void throwsWhenChoicesMissing() {
        try {
            LlmClient.parseAnswer("{}");
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("no choices"));
        }
    }

    @Test
    public void throwsWhenAllFieldsEmpty() {
        try {
            LlmClient.parseAnswer("{\"choices\":[{\"message\":{\"content\":null,\"reasoning\":\"\",\"refusal\":\"\"}}]}");
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("empty"));
        }
    }

    @Test
    public void isEmptyContentRecognizesNullAndUndefined() {
        assertTrue(LlmClient.isEmptyContent(null));
        assertTrue(LlmClient.isEmptyContent(""));
        assertTrue(LlmClient.isEmptyContent("   "));
        assertTrue(LlmClient.isEmptyContent("null"));
        assertTrue(LlmClient.isEmptyContent("NULL"));
        assertTrue(LlmClient.isEmptyContent("undefined"));
        assertFalse(LlmClient.isEmptyContent("ok"));
    }

    @Test
    public void valueToTextHandlesNested() throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(new JSONObject().put("text", "a"));
        arr.put(new JSONObject().put("text", "b"));
        String out = LlmClient.valueToText(arr);
        assertTrue(out.contains("a"));
        assertTrue(out.contains("b"));
    }

    @Test
    public void valueToTextOnNullReturnsEmpty() {
        assertEquals("", LlmClient.valueToText(null));
        assertEquals("", LlmClient.valueToText(JSONObject.NULL));
    }
}
