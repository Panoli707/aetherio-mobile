package com.panoli707.aetherio.regressionqa;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.security.MessageDigest;

/** Android APIs only: exercises the installed optimized APK, without app/test-only hooks. */
public final class RegressionReleaseInstrumentation extends Instrumentation {
    private Bundle args;
    private static final String EXTRA = "com.panoli707.aetherio.nativeapp.player.";
    @Override public void onCreate(Bundle arguments) { args = arguments; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            String name = getTargetContext().getPackageName();
            android.content.pm.PackageInfo info = getTargetContext().getPackageManager().getPackageInfo(name, 0);
            require(args.getString("version", "0.20.3").equals(info.versionName), "Wrong version");
            require((info.applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0, "Debug APK");
            String mode = args.getString("mode", "all");
            if (mode.equals("all") || mode.equals("ui")) artworkAndHub();
            if (mode.equals("runtime-restore")) restoreRuntime();
            if (mode.equals("trailer-cold")) {
                File runtime = new File(getTargetContext().getNoBackupFilesDir(), "youtubedl-android");
                File preserved = new File(getTargetContext().getNoBackupFilesDir(), "qa0203-preserved-youtube");
                require(!preserved.exists(), "A previous cold-runtime probe needs restoration first");
                if (runtime.exists()) require(runtime.renameTo(preserved), "Cannot preserve existing runtime");
            }
            if (mode.equals("all") || mode.equals("trailer") || mode.equals("trailer-cold")) {
                trailer(true);
                trailer(false);
            }
            result.putString("stream", "Release " + info.versionName + ": regression probe passed (" + mode + ")\n");
            finish(-1, result);
        } catch (Throwable error) {
            result.putString("stream", android.util.Log.getStackTraceString(error));
            finish(1, result);
        }
    }

    private void launchMain() {
        Intent launch = getTargetContext().getPackageManager().getLaunchIntentForPackage(getTargetContext().getPackageName());
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivitySync(launch);
    }
    private void artworkAndHub() throws Exception {
        File marker = new File(getTargetContext().getExternalFilesDir(null), "qa0201-update-marker.txt");
        require(marker.isFile() && marker.length() > 0, "Upgrade data marker lost");
        launchMain();
        click(waitFor("Buscar", 30000));
        AccessibilityNodeInfo edit = findEditable(15000);
        Bundle text = new Bundle();
        text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Frieren");
        require(edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text), "Cannot enter search");
        click(waitFor("Frieren:", 45000));
        waitFor("Reproducir", 45000);
        SystemClock.sleep(6000);
        capture("release-frieren");
        launchMain();
        click(waitFor("Ajustes", 30000));
        click(scrollTo("Fuentes y contenido"));
        waitFor("Aetherio Provider Hub", 20000);
        long end = SystemClock.uptimeMillis() + 30000;
        String directory = null, manifest = null;
        do {
            android.content.SharedPreferences prefs = getTargetContext().getSharedPreferences("aetherio_hub_directory", 0);
            directory = prefs.getString("public_directory_v1", null);
            manifest = prefs.getString("public_manifest_v1", null);
            if (directory != null && manifest != null) break;
            SystemClock.sleep(200);
        } while (SystemClock.uptimeMillis() < end);
        require(directory != null && manifest != null, "Hub metadata not cached");
        JSONArray addons = new JSONObject(directory).getJSONArray("addons");
        JSONArray runtime = new JSONObject(manifest).getJSONArray("scrapers");
        require(addons.length() > 0 && runtime.length() > 0, "Hub inventory empty");
        for (int i = addons.length() - 1; i >= 0; i--) {
            JSONObject addon = addons.getJSONObject(i);
            if (!addon.optBoolean("hiddenInUi", false)) { scrollTo(addon.getString("name")); break; }
        }
        capture("release-hub-addons");
        scrollTo("Proveedores del resolvedor (" + runtime.length() + ")");
        capture("release-hub-runtime");
        scrollTo(runtime.getJSONObject(runtime.length() - 1).getString("name"));
        capture("release-hub-last-provider");
        sendStatus(0, status("Upgrade data retained; detail screenshot captured; Hub settings show " + addons.length() + " addons and " + runtime.length() + " runtime providers, including the last published names\n"));
    }

    private AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isScrollable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findScrollable(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }
    private AccessibilityNodeInfo scrollTo(String label) {
        for (int i = 0; i < 60; i++) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            AccessibilityNodeInfo found = find(root, label, false);
            if (found != null && found.isVisibleToUser()) return found;
            AccessibilityNodeInfo scroll = findScrollable(root);
            if (scroll == null || !scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                SystemClock.sleep(300);
                found = find(getUiAutomation().getRootInActiveWindow(), label, false);
                if (found != null && found.isVisibleToUser()) return found;
                break;
            }
            SystemClock.sleep(200);
        }
        throw new AssertionError("Cannot scroll to: " + label);
    }
    private AccessibilityNodeInfo findEditable(long timeout) {
        long end = SystemClock.uptimeMillis() + timeout;
        do {
            AccessibilityNodeInfo node = find(getUiAutomation().getRootInActiveWindow(), "", true);
            if (node != null) return node;
            SystemClock.sleep(200);
        } while (SystemClock.uptimeMillis() < end);
        throw new AssertionError("Search field missing");
    }

    private void restoreRuntime() {
        File preserved = new File(getTargetContext().getNoBackupFilesDir(), "qa0203-preserved-youtube");
        if (!preserved.exists()) return;
        File runtime = new File(getTargetContext().getNoBackupFilesDir(), "youtubedl-android");
        File tested = new File(getTargetContext().getNoBackupFilesDir(), "qa0203-tested-youtube-" + System.currentTimeMillis());
        if (runtime.exists()) require(runtime.renameTo(tested), "Cannot preserve tested runtime");
        require(preserved.renameTo(runtime), "Cannot restore original runtime");
    }

    private void trailer(boolean youtube) throws Exception {
        Intent intent = new Intent().setClassName(getTargetContext(),
            "com.panoli707.aetherio.nativeapp.player.VideoActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(EXTRA + "TRAILER", true)
            .putExtra(EXTRA + "TITLE", youtube ? "YouTube QA" : "Direct QA");
        if (youtube) intent.putExtra(EXTRA + "YOUTUBE_TRAILER_ID", "zSWdZVtXT7E");
        else intent.putExtra(EXTRA + "URL", "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4");
        final Activity activity = startActivitySync(intent);
        waitFor("Volver", 20000);
        long end = SystemClock.uptimeMillis() + 85000;
        boolean playing = false, error = false;
        do {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            playing = find(root, "Pausar", false) != null;
            error = find(root, "Reintentar", false) != null;
            if (playing || error) break;
            SystemClock.sleep(200);
        } while (SystemClock.uptimeMillis() < end);
        boolean cold = args.getString("mode", "all").equals("trailer-cold");
        require(playing || (youtube && error), "Trailer did not play or provide a recoverable error");
        if (cold && youtube) {
            // The bundled extractor is installed only AFTER YoutubeDL.init() fully extracts Python.
            // A network restriction must not mask a failed extraction or the guarded linkage error.
            File extractor = new File(getTargetContext().getNoBackupFilesDir(), "youtubedl-android/yt-dlp/yt-dlp");
            require(extractor.isFile(), "Cold runtime extraction did not complete");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream input = new FileInputStream(extractor)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            StringBuilder hash = new StringBuilder();
            for (byte value : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            require(hash.toString().equals("e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3"), "Bundled extractor not installed after cold initialization");
            require(find(getUiAutomation().getRootInActiveWindow(), "No se pudo iniciar el motor", false) == null,
                "Runtime linkage guard fired: the cold extraction regression is not fixed");
            sendStatus(0, status("YouTube cold runtime: full extraction and bundled SHA-256 verified\n"));
        }
        if (playing) {
            click(waitFor("Pausar", 5000));
            waitFor("Reproducir", 5000);
        } else {
            click(waitFor("Reintentar", 5000));
            SystemClock.sleep(1000);
            waitFor("Volver", 5000);
        }
        capture(youtube ? "release-youtube" : "release-direct");
        runOnMainSync(new Runnable() { @Override public void run() { activity.finish(); } });
        sendStatus(0, status((youtube ? "YouTube" : "Direct") + ": " + (playing ? "native playback verified" : "unavailable; error/retry/back handled without closing app") + "\n"));
    }

    private Bundle status(String message) { Bundle out = new Bundle(); out.putString("stream", message); return out; }
    private void capture(String label) throws Exception {
        Bitmap screenshot = getUiAutomation().takeScreenshot();
        require(screenshot != null, "No screenshot");
        File file = new File(getTargetContext().getExternalFilesDir(null), "qa0203/" + label + ".png");
        file.getParentFile().mkdirs();
        try (FileOutputStream stream = new FileOutputStream(file)) { screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream); }
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
            SystemClock.sleep(200);
        } while (SystemClock.uptimeMillis() < end);
        throw new AssertionError("UI element missing: " + label);
    }
    private void click(AccessibilityNodeInfo node) {
        while (node != null) {
            if (node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
            node = node.getParent();
        }
        throw new AssertionError("UI element cannot be clicked");
    }
}
