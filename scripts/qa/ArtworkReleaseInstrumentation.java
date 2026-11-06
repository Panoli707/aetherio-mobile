package com.panoli707.aetherio.artworkqa;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileOutputStream;

/** Black-box verification: uses Android APIs only, including against an R8 APK. */
public final class ArtworkReleaseInstrumentation extends Instrumentation {
    private Bundle args;
    @Override public void onCreate(Bundle arguments) { args = arguments; start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            String name = getTargetContext().getPackageName();
            android.content.pm.PackageInfo info = getTargetContext().getPackageManager().getPackageInfo(name, 0);
            require("0.20.2".equals(info.versionName), "Wrong version");
            require((info.applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0, "Debug APK");
            File marker = new File(getTargetContext().getExternalFilesDir(null), "qa0201-update-marker.txt");
            require(marker.isFile() && marker.length() > 0, "Upgrade marker lost");
            Intent launch = getTargetContext().getPackageManager().getLaunchIntentForPackage(name);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivitySync(launch);
            getUiAutomation().waitForIdle(1000, 30000);
            click(waitFor("Buscar", 30000));
            AccessibilityNodeInfo edit = waitEditable(15000);
            Bundle text = new Bundle();
            text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Frieren");
            require(edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text), "Cannot enter search");
            // Search results contain the full display title, not just the text field value.
            AccessibilityNodeInfo title = waitFor("Frieren:", 45000);
            click(title);
            waitFor("Reproducir", 45000);
            getUiAutomation().waitForIdle(1000, 30000);
            SystemClock.sleep(6000); // Let the remote artwork/logo decode before capturing the release UI.
            Bitmap screenshot = getUiAutomation().takeScreenshot();
            require(screenshot != null, "No screenshot");
            File output = new File(getTargetContext().getExternalFilesDir(null), "qa0202/release-frieren.png");
            output.getParentFile().mkdirs();
            try (FileOutputStream stream = new FileOutputStream(output)) { screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream); }
            result.putString("stream", "0.20.2 optimized release: upgrade data retained; search/detail opened; screenshot " + output);
            finish(-1, result);
        } catch (Throwable error) {
            result.putString("stream", android.util.Log.getStackTraceString(error));
            finish(1, result);
        }
    }

    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, String label, boolean editable) {
        if (node == null) return null;
        if (editable && node.isEditable()) return node;
        if (!editable && ((node.getText() != null && node.getText().toString().contains(label)) ||
            (node.getContentDescription() != null && node.getContentDescription().toString().contains(label)))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = find(node.getChild(i), label, editable);
            if (found != null) return found;
        }
        return null;
    }
    private AccessibilityNodeInfo waitFor(String label, long timeout) {
        long end = SystemClock.uptimeMillis() + timeout;
        do {
            AccessibilityNodeInfo node = find(getUiAutomation().getRootInActiveWindow(), label, false);
            if (node != null) return node;
            SystemClock.sleep(250);
        } while (SystemClock.uptimeMillis() < end);
        throw new AssertionError("UI element missing: " + label);
    }
    private AccessibilityNodeInfo waitEditable(long timeout) {
        long end = SystemClock.uptimeMillis() + timeout;
        do {
            AccessibilityNodeInfo node = find(getUiAutomation().getRootInActiveWindow(), "", true);
            if (node != null) return node;
            SystemClock.sleep(250);
        } while (SystemClock.uptimeMillis() < end);
        throw new AssertionError("Search field missing");
    }
    private void click(AccessibilityNodeInfo node) {
        while (node != null) {
            if (node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
            node = node.getParent();
        }
        throw new AssertionError("UI element cannot be clicked");
    }
}
