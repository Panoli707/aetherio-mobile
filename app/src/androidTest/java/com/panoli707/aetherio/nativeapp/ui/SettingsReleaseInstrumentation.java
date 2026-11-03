package com.panoli707.aetherio.nativeapp.ui;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** Opt-in smoke test for real touch navigation in the minified release, on an emulator only. */
public final class SettingsReleaseInstrumentation extends Instrumentation {
    private boolean enabled;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        enabled = arguments != null && "true".equals(arguments.getString("manualSettingsNavigation")) &&
            Build.MODEL.toLowerCase(java.util.Locale.ROOT).contains("sdk");
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        if (!enabled) {
            result.putString("stream", "SKIPPED: enable manualSettingsNavigation on an emulator\n");
            finish(Activity.RESULT_CANCELED, result);
            return;
        }
        try {
            AccessibilityServiceInfo info = getUiAutomation().getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            getUiAutomation().setServiceInfo(info);
            startActivitySync(new Intent(Intent.ACTION_MAIN).setClassName(getTargetContext().getPackageName(),
                "com.panoli707.aetherio.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            tap("Ajustes");
            // Start with the reported case, before any other navigation can mask a stale destination.
            String[][] pages = {
                {"Fuentes y contenido", "Aetherio Provider Hub"},
                {"Perfil local", "Este perfil se guarda en el dispositivo. No necesitas una cuenta."},
                {"Reproducción", "Audio y subtítulos"},
                {"Actualizaciones", "Actualizaciones automáticas"},
                {"Acerca de", "Películas, series y anime en una aplicación nativa para Android."}
            };
            for (String[] page : pages) {
                tap(page[0]);
                await(page[1]);
                if (find("Configuración") != null) throw new AssertionError("Overview still visible after opening " + page[0]);
                // A tab change must preserve an already visible destination, never be required to open it.
                tap("Biblioteca");
                tap("Ajustes");
                await(page[1]);
                sendKeySync(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
                sendKeySync(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
                // The overview restores its scroll offset, so its heading may be off screen.
                await("Provider Hub, fuentes adicionales y proveedores propios");
            }
            result.putString("stream", "OK: all 5 settings pages open on first touch, survive tab changes and return with Back\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            try {
                android.graphics.Bitmap screenshot = getUiAutomation().takeScreenshot();
                java.io.File file = new java.io.File(getTargetContext().getExternalFilesDir(null), "settings-navigation-failure.png");
                try (java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
                    screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
                }
                screenshot.recycle();
            } catch (Exception ignored) { /* Preserve the original test failure. */ }
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private AccessibilityNodeInfo find(String text) {
        if (Build.VERSION.SDK_INT >= 33) getUiAutomation().clearCache();
        return find(getUiAutomation().getRootInActiveWindow(), text);
    }

    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        if (node.isVisibleToUser() && ((node.getText() != null && node.getText().toString().contains(text)) ||
            (node.getContentDescription() != null && node.getContentDescription().toString().contains(text)))) return node;
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo found = find(node.getChild(index), text);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo await(String text) {
        long deadline = SystemClock.uptimeMillis() + 30_000;
        do {
            AccessibilityNodeInfo node = find(text);
            if (node != null) return node;
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Screen did not show: " + text);
    }

    private void tap(String text) throws java.util.concurrent.TimeoutException {
        AccessibilityNodeInfo node = await(text);
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        // Scroll overview rows clear of the floating navigation bar before tapping them.
        if (!"Ajustes".equals(text) && !"Biblioteca".equals(text)) {
            Rect screen = new Rect();
            getUiAutomation().getRootInActiveWindow().getBoundsInScreen(screen);
            if (bounds.centerY() > screen.bottom * .78f) {
                long down = SystemClock.uptimeMillis();
                for (int step = 0; step <= 10; step++) {
                    int action = step == 0 ? MotionEvent.ACTION_DOWN : step == 10 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
                    MotionEvent event = MotionEvent.obtain(down, down + step * 30,
                        action, screen.centerX(), screen.bottom * (.7f - .035f * step), 0);
                    event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                    try { getUiAutomation().injectInputEvent(event, true); } finally { event.recycle(); }
                }
                getUiAutomation().waitForIdle(300, 3_000);
                node = await(text);
                node.refresh();
                node.getBoundsInScreen(bounds);
            }
        }
        long down = SystemClock.uptimeMillis();
        MotionEvent press = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, bounds.centerX(), bounds.centerY(), 0);
        MotionEvent release = MotionEvent.obtain(down, down + 50, MotionEvent.ACTION_UP, bounds.centerX(), bounds.centerY(), 0);
        try {
            press.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            release.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            getUiAutomation().injectInputEvent(press, true);
            getUiAutomation().injectInputEvent(release, true);
        } finally { press.recycle(); release.recycle(); }
        getUiAutomation().waitForIdle(300, 3_000);
    }
}
