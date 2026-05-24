package com.example.smsllmgateway.keepalive;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Best-effort helper that points the user at the manufacturer-specific
 * "autostart" / "background app" screen on Chinese OEMs that aggressively
 * kill apps regardless of foreground-service status.
 *
 * <p>On stock Android (Pixel, AOSP) there is no such screen — Android's own
 * battery-optimisation exemption does the job — so we transparently fall
 * back to the standard application-details screen.
 */
public final class OemAutostartHelper {

    /** Candidate components we'll try in order. First one that resolves wins. */
    static final List<ComponentName> CANDIDATES = Collections.unmodifiableList(
            new ArrayList<ComponentName>() {{
                // Xiaomi (MIUI) — Autostart manager
                add(new ComponentName("com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"));
                // Huawei
                add(new ComponentName("com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"));
                add(new ComponentName("com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"));
                add(new ComponentName("com.huawei.systemmanager",
                        "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"));
                // Oppo / ColorOS
                add(new ComponentName("com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
                add(new ComponentName("com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"));
                add(new ComponentName("com.oppo.safe",
                        "com.oppo.safe.permission.startup.StartupAppListActivity"));
                // OnePlus
                add(new ComponentName("com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"));
                // Vivo
                add(new ComponentName("com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
                add(new ComponentName("com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"));
                // Samsung
                add(new ComponentName("com.samsung.android.lool",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"));
                // ASUS
                add(new ComponentName("com.asus.mobilemanager",
                        "com.asus.mobilemanager.entry.FunctionActivity"));
                // LeEco
                add(new ComponentName("com.letv.android.letvsafe",
                        "com.letv.android.letvsafe.AutobootManageActivity"));
            }});

    private OemAutostartHelper() {
    }

    /**
     * Picks the first candidate that's actually installed on the device, or
     * {@code null} when none of them resolve (stock Android, generic builds).
     * Pure logic — visible for testing.
     */
    public static ComponentName pickAvailableComponent(PackageManager pm) {
        if (pm == null) {
            return null;
        }
        for (ComponentName candidate : CANDIDATES) {
            Intent probe = new Intent();
            probe.setComponent(candidate);
            if (pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Returns an Intent that opens the manufacturer autostart screen when
     * available, or the standard application-details fallback otherwise.
     * Caller is responsible for adding {@link Intent#FLAG_ACTIVITY_NEW_TASK}
     * and handling potential {@link android.content.ActivityNotFoundException}.
     */
    public static Intent buildAutostartIntent(Context context) {
        if (context == null) {
            return null;
        }
        ComponentName picked = pickAvailableComponent(context.getPackageManager());
        if (picked != null) {
            Intent oem = new Intent();
            oem.setComponent(picked);
            oem.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return oem;
        }
        return fallbackAppDetailsIntent(context.getPackageName());
    }

    public static Intent fallbackAppDetailsIntent(String packageName) {
        Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        fallback.setData(Uri.parse("package:" + (packageName == null ? "" : packageName)));
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return fallback;
    }

    /**
     * Coarse manufacturer label for the UI hint, e.g. "MIUI (Xiaomi)" or
     * "Стандартный Android". Pure function for easy testing.
     */
    public static String describeManufacturer(String rawManufacturer) {
        if (rawManufacturer == null) {
            return "Стандартный Android";
        }
        String m = rawManufacturer.toLowerCase(Locale.ROOT);
        if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")) {
            return "MIUI/HyperOS (Xiaomi)";
        }
        if (m.contains("huawei") || m.contains("honor")) {
            return "EMUI/HarmonyOS (Huawei/Honor)";
        }
        if (m.contains("oppo")) {
            return "ColorOS (Oppo)";
        }
        if (m.contains("oneplus")) {
            return "OxygenOS (OnePlus)";
        }
        if (m.contains("vivo") || m.contains("iqoo")) {
            return "Funtouch/OriginOS (Vivo)";
        }
        if (m.contains("samsung")) {
            return "One UI (Samsung)";
        }
        if (m.contains("asus")) {
            return "ZenUI (Asus)";
        }
        if (m.contains("realme")) {
            return "Realme UI";
        }
        if (m.contains("meizu")) {
            return "Flyme (Meizu)";
        }
        return "Стандартный Android";
    }

    /** Public wrapper using the current device's manufacturer, kept thin for testability. */
    public static String describeCurrentManufacturer() {
        return describeManufacturer(Build.MANUFACTURER);
    }
}
