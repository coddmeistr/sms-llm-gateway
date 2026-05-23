package com.example.smsllmgateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PhoneMatcherTest {

    @Test
    public void normalizeDropsFormatting() {
        assertEquals("+79123456789", PhoneMatcher.normalize("+7 (912) 345-67-89"));
        assertEquals("79123456789", PhoneMatcher.normalize("7-912-345-67-89"));
        assertEquals("", PhoneMatcher.normalize("abc"));
        assertEquals("", PhoneMatcher.normalize(null));
        assertEquals("12345", PhoneMatcher.normalize("12+345"));
    }

    @Test
    public void normalizeKeepsLeadingPlusOnly() {
        assertEquals("+123", PhoneMatcher.normalize("+1-2-3"));
        assertEquals("123", PhoneMatcher.normalize("(1)+23"));
    }

    @Test
    public void emptyOrBlankWhitelistAllowsAll() {
        assertTrue(PhoneMatcher.isAllowed("+79121234567", null));
        assertTrue(PhoneMatcher.isAllowed("+79121234567", ""));
        assertTrue(PhoneMatcher.isAllowed("+79121234567", "   \n  "));
    }

    @Test
    public void exactMatchWorksWithFormatting() {
        assertTrue(PhoneMatcher.isAllowed("+7 (912) 345-67-89", "+79123456789"));
        assertTrue(PhoneMatcher.isAllowed("+79123456789", "+7 912 345 67 89"));
    }

    @Test
    public void matchesIgnoringLeadingPlus() {
        assertTrue(PhoneMatcher.isAllowed("+79123456789", "79123456789"));
        assertTrue(PhoneMatcher.isAllowed("79123456789", "+79123456789"));
    }

    @Test
    public void matchesEntryAsSufficientSuffix() {
        // 7+ digits suffix is allowed
        assertTrue(PhoneMatcher.isAllowed("+79123456789", "9123456789"));
        assertTrue(PhoneMatcher.isAllowed("+79123456789", "3456789"));
    }

    @Test
    public void rejectsTooShortEntry() {
        // Old bug: "123" would match every number ending in 123.
        assertFalse(PhoneMatcher.isAllowed("+79123456789", "123"));
        assertFalse(PhoneMatcher.isAllowed("+79123456789", "456789"));
    }

    @Test
    public void rejectsReverseSuffixMatch() {
        // Old bug: entry "+79123456789" would match a sender "456789" via endsWith.
        // The new logic requires entry to be the suffix of sender, not vice versa.
        assertFalse(PhoneMatcher.isAllowed("456789", "+79123456789"));
    }

    @Test
    public void handlesSeveralEntries() {
        String list = "+79121111111, +79122222222\n+79123333333; 79129999999";
        assertTrue(PhoneMatcher.isAllowed("+79122222222", list));
        assertTrue(PhoneMatcher.isAllowed("79129999999", list));
        assertFalse(PhoneMatcher.isAllowed("+79124444444", list));
    }

    @Test
    public void rejectsEmptySender() {
        assertFalse(PhoneMatcher.isAllowed(null, "+79121234567"));
        assertFalse(PhoneMatcher.isAllowed("", "+79121234567"));
        assertFalse(PhoneMatcher.isAllowed("abc", "+79121234567"));
    }
}
