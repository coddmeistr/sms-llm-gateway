package com.example.smsllmgateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TextSanitizerTest {

    @Test
    public void nullInputReturnsEmpty() {
        assertEquals("", TextSanitizer.cleanPlainText(null));
        assertEquals("", TextSanitizer.limit(null, 10));
    }

    @Test
    public void stripsMarkdownMarkers() {
        String input = "**bold** and `code` and ```fence```\n# header\n> quote";
        String out = TextSanitizer.cleanPlainText(input);
        assertFalse("backticks left: " + out, out.contains("`"));
        assertFalse("asterisks left: " + out, out.contains("**"));
        assertFalse("hash left: " + out, out.startsWith("#"));
        assertFalse("quote left: " + out, out.contains("> "));
    }

    @Test
    public void preservesUsefulSymbols() {
        String input = "Температура 36.6° сегодня. Цена 100 ₽. № 5. Apple™.";
        String out = TextSanitizer.cleanPlainText(input);
        assertTrue("degree sign lost: " + out, out.contains("°"));
        assertTrue("ruble sign lost: " + out, out.contains("₽"));
        assertTrue("numero sign lost: " + out, out.contains("№"));
        assertTrue("tm sign lost: " + out, out.contains("™"));
    }

    @Test
    public void dropsEmojiPictographs() {
        String input = "Hello \uD83D\uDE00 world \u2705 ok";
        String out = TextSanitizer.cleanPlainText(input);
        assertFalse("smiley emoji left: " + out, out.contains("\uD83D\uDE00"));
        assertFalse("check-mark emoji left: " + out, out.contains("\u2705"));
        assertTrue("text destroyed: " + out, out.contains("Hello"));
        assertTrue("text destroyed: " + out, out.contains("world"));
    }

    @Test
    public void collapsesWhitespaceButKeepsParagraphs() {
        String input = "line one\n\n\n\nline two   with    spaces";
        String out = TextSanitizer.cleanPlainText(input);
        assertEquals("line one\n\nline two with spaces", out);
    }

    @Test
    public void limitShorterThanValueReturnsAsIs() {
        assertEquals("abc", TextSanitizer.limit("abc", 10));
        assertEquals("abc", TextSanitizer.limit("abc", 3));
    }

    @Test
    public void limitTruncatesAndAddsSuffix() {
        String input = "abcdefghijklmnopqrst";
        String out = TextSanitizer.limit(input, 5);
        assertTrue("expected prefix: " + out, out.startsWith("abcde"));
        assertTrue("expected suffix: " + out, out.contains("[Ответ обрезан]"));
    }

    @Test
    public void limitDoesNotSplitSurrogatePair() {
        // Smiley emoji (U+1F600) is encoded as a surrogate pair: D83D + DE00
        String input = "ab\uD83D\uDE00cd";
        // input.length() == 6 (2 + 2 + 2). Cap at 4 → would land between D83D and DE00.
        String out = TextSanitizer.limit(input, 4);
        // Truncated string must not end with a lone high surrogate.
        String prefix = out.substring(0, out.length() - "\n\n[Ответ обрезан]".length());
        if (!prefix.isEmpty()) {
            char last = prefix.charAt(prefix.length() - 1);
            assertFalse("lone high surrogate at end: " + (int) last, Character.isHighSurrogate(last));
        }
    }

    @Test
    public void limitZeroOrNegativeKeepsString() {
        assertEquals("abc", TextSanitizer.limit("abc", 0));
        assertEquals("abc", TextSanitizer.limit("abc", -5));
    }
}
