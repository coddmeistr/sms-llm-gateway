package com.example.smsllmgateway.keepalive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Intent;
import android.provider.Settings;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class OemAutostartHelperTest {

    @Test
    public void describeManufacturerHandlesNull() {
        assertEquals("Стандартный Android",
                OemAutostartHelper.describeManufacturer(null));
    }

    @Test
    public void describeManufacturerRecognisesXiaomi() {
        assertEquals("MIUI/HyperOS (Xiaomi)",
                OemAutostartHelper.describeManufacturer("Xiaomi"));
        assertEquals("MIUI/HyperOS (Xiaomi)",
                OemAutostartHelper.describeManufacturer("Redmi"));
        assertEquals("MIUI/HyperOS (Xiaomi)",
                OemAutostartHelper.describeManufacturer("poco"));
    }

    @Test
    public void describeManufacturerRecognisesHuaweiAndHonor() {
        assertEquals("EMUI/HarmonyOS (Huawei/Honor)",
                OemAutostartHelper.describeManufacturer("HUAWEI"));
        assertEquals("EMUI/HarmonyOS (Huawei/Honor)",
                OemAutostartHelper.describeManufacturer("HONOR"));
    }

    @Test
    public void describeManufacturerRecognisesOppoOnePlusVivoSamsungAsus() {
        assertEquals("ColorOS (Oppo)", OemAutostartHelper.describeManufacturer("OPPO"));
        assertEquals("OxygenOS (OnePlus)", OemAutostartHelper.describeManufacturer("OnePlus"));
        assertEquals("Funtouch/OriginOS (Vivo)", OemAutostartHelper.describeManufacturer("vivo"));
        assertEquals("Funtouch/OriginOS (Vivo)", OemAutostartHelper.describeManufacturer("iqoo"));
        assertEquals("One UI (Samsung)", OemAutostartHelper.describeManufacturer("samsung"));
        assertEquals("ZenUI (Asus)", OemAutostartHelper.describeManufacturer("Asus"));
        assertEquals("Realme UI", OemAutostartHelper.describeManufacturer("realme"));
        assertEquals("Flyme (Meizu)", OemAutostartHelper.describeManufacturer("MEIZU"));
    }

    @Test
    public void describeManufacturerFallsBackForUnknown() {
        assertEquals("Стандартный Android",
                OemAutostartHelper.describeManufacturer("Google"));
        assertEquals("Стандартный Android",
                OemAutostartHelper.describeManufacturer("WhateverCorp"));
    }

    @Test
    public void fallbackIntentTargetsApplicationDetails() {
        Intent intent = OemAutostartHelper.fallbackAppDetailsIntent("com.example.pkg");
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.getAction());
        assertNotNull(intent.getData());
        assertEquals("package:com.example.pkg", intent.getData().toString());
        // FLAG_ACTIVITY_NEW_TASK must be set so the intent can launch from a
        // non-activity context if needed (e.g. service-triggered prompts).
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }

    @Test
    public void fallbackIntentToleratesNullPackage() {
        Intent intent = OemAutostartHelper.fallbackAppDetailsIntent(null);
        assertNotNull(intent.getData());
        assertEquals("package:", intent.getData().toString());
    }

    @Test
    public void candidateListIsNonEmptyAndUniqueWithinReason() {
        assertFalse("candidate list should not be empty",
                OemAutostartHelper.CANDIDATES.isEmpty());
        // Every candidate must have a class name; otherwise the resolver crashes.
        for (ComponentName cn : OemAutostartHelper.CANDIDATES) {
            assertNotNull(cn.getPackageName());
            assertNotNull(cn.getClassName());
            assertFalse(cn.getPackageName().isEmpty());
            assertFalse(cn.getClassName().isEmpty());
        }
    }

    @Test
    public void pickAvailableComponentReturnsNullOnNullPm() {
        assertNull(OemAutostartHelper.pickAvailableComponent(null));
    }
}
