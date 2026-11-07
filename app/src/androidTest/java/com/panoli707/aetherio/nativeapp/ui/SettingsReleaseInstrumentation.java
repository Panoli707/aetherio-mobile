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
    private boolean designOnly;
    private boolean personalSpace;
    private boolean downloadOnly;
    private boolean diagnostic;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        enabled = arguments != null && "true".equals(arguments.getString("manualSettingsNavigation")) &&
            Build.MODEL.toLowerCase(java.util.Locale.ROOT).contains("sdk");
        designOnly = arguments != null && "true".equals(arguments.getString("designOnly"));
        personalSpace = arguments != null && "true".equals(arguments.getString("personalSpace"));
        downloadOnly = arguments != null && "true".equals(arguments.getString("downloadOnly"));
        diagnostic = arguments != null && "true".equals(arguments.getString("diagnostic"));
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
            if (diagnostic) {
                result.putString("stream", downloadDiagnostics() + "\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            AccessibilityServiceInfo info = getUiAutomation().getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            getUiAutomation().setServiceInfo(info);
            Intent launch = new Intent(Intent.ACTION_MAIN).setClassName(getTargetContext().getPackageName(),
                "com.panoli707.aetherio.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            if (downloadOnly) launch.putExtra("openDownloads", true);
            startActivitySync(launch);
            if (downloadOnly) {
                await("Cola de una descarga a la vez");
                checkReleaseDownload();
                result.putString("stream", "OK: minified release kept a paused debug download, resumed it, played it with Wi-Fi/data disabled and removed it\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (personalSpace) {
                checkPersonalSpace();
                result.putString("stream", "OK: minified release profile, editor, retained download resumed by WorkManager, playback with Wi-Fi/data disabled, notification route and OAuth state rejection\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            tap("Ajustes");
            // Start with the reported case, before any other navigation can mask a stale destination.
            String[][] pages = {
                {"Diseño", "Contenido principal"},
                {"Fuentes y contenido", "Aetherio Provider Hub"},
                {"Perfil local", "Mi espacio"},
                {"Reproducción", "Audio y subtítulos"},
                {"Actualizaciones", "Actualizaciones automáticas"},
                {"Acerca de", "Películas, series y anime en una aplicación nativa para Android."}
            };
            for (String[] page : pages) {
                if (designOnly && !"Diseño".equals(page[0])) continue;
                tap(page[0]);
                await(page[1]);
                if ("Diseño".equals(page[0])) {
                    android.graphics.Bitmap screenshot = getUiAutomation().takeScreenshot();
                    java.io.File file = new java.io.File(getTargetContext().getExternalFilesDir(null), "home-design-release.png");
                    try (java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
                        screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
                    }
                    screenshot.recycle();
                }
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
            result.putString("stream", designOnly
                ? "OK: Design opens on first touch, survives tab changes and returns with Back\n"
                : "OK: all 6 settings pages open on first touch, survive tab changes and return with Back\n");
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

    private String downloadDiagnostics() {
        String snapshot = getTargetContext().getSharedPreferences("aetherio_media_downloads", 0).getString("index", "[]");
        String remote;
        try {
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection)
                new java.net.URL("https://media.w3.org/2010/05/sintel/trailer.mp4").openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setRequestProperty("Range", "bytes=0-0");
            try { remote = "HTTP " + connection.getResponseCode() + " / " + connection.getContentType(); }
            finally { connection.disconnect(); }
        } catch (Exception error) { remote = error.getClass().getSimpleName() + ": " + error.getMessage(); }
        return "QA download index: " + snapshot + " / source: " + remote;
    }

    private void checkPersonalSpace() throws Exception {
        android.content.SharedPreferences library = getTargetContext().getSharedPreferences("aetherio_library", 0);
        String previous = library.getString("profile", "Usuario");
        String marker = "qa021-" + SystemClock.uptimeMillis();
        await("Abrir perfil");
        tap("Abrir perfil");
        await("Mi espacio");
        screenshot("profile-021-release.png");
        tap("Editar perfil");
        await("Nombre local");
        AccessibilityNodeInfo input = findEdit(getUiAutomation().getRootInActiveWindow());
        if (input == null) throw new AssertionError("Local name input missing");
        Bundle text = new Bundle();
        text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, marker);
        if (!input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text)) throw new AssertionError("Cannot edit local name");
        tap("Guardar nombre");
        await("Nombre guardado en este dispositivo.");
        if (!marker.equals(library.getString("profile", null))) throw new AssertionError("Local profile was not persisted");
        Throwable initialFailure = null;
        try {
            tap("Banners");
            await("Elegir banner de la galería");
            screenshot("profile-editor-021-release.png");
            // Route through the real notification intent; Back can leave the banner picker on the editor.
            getTargetContext().startActivity(new Intent().setClassName(getTargetContext().getPackageName(),
                "com.panoli707.aetherio.nativeapp.MainActivity").putExtra("openDownloads", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            await("Cola de una descarga a la vez");
            checkReleaseDownload();
            screenshot("downloads-021-release.png");
            // Unsolicited callbacks must never create a linked account or cause a process crash.
            getTargetContext().startActivity(new Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("aetherio://oauth/mal?code=not-a-real-code&state=not-a-real-state"))
                .setClassName(getTargetContext().getPackageName(), "com.panoli707.aetherio.nativeapp.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            await("Conectar AniList");
            scrollTo("No se pudo validar el retorno de autorización");
            // Verify notification navigation, including a new intent delivered to the already open profile.
            getTargetContext().startActivity(new Intent().setClassName(getTargetContext().getPackageName(),
                "com.panoli707.aetherio.nativeapp.MainActivity").putExtra("openDownloads", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            await("Cola de una descarga a la vez");
            if (!marker.equals(library.getString("profile", null))) throw new AssertionError("Data lost during external return");
        } catch (Exception | Error error) {
            initialFailure = error;
            throw error;
        } finally {
            // Restore only the name changed by this smoke test, through the same UI path.
            try {
                getTargetContext().startActivity(new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("aetherio://oauth/anilist#access_token=invalid&token_type=Bearer&state=invalid"))
                    .setClassName(getTargetContext().getPackageName(), "com.panoli707.aetherio.nativeapp.MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                await("Conectar AniList");
                tap("Editar perfil");
                await("Nombre local");
                input = findEdit(getUiAutomation().getRootInActiveWindow());
                text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, previous);
                if (input == null || !input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text)) throw new AssertionError("Cannot restore QA name");
                tap("Guardar nombre");
                await("Nombre guardado en este dispositivo.");
                if (!previous.equals(library.getString("profile", null))) throw new AssertionError("QA name restoration failed");
            } catch (Exception | Error restoreError) {
                if (initialFailure != null) initialFailure.addSuppressed(restoreError);
                else throw restoreError;
            }
        }
    }

    private void checkReleaseDownload() throws Exception {
        String marker = getTargetContext().getSharedPreferences("qa_release_021", 0).getString("marker", null);
        if (marker == null) throw new AssertionError("Missing debug-to-release download fixture");
        org.json.JSONArray records = new org.json.JSONArray(getTargetContext().getSharedPreferences("aetherio_media_downloads", 0).getString("index", "[]"));
        boolean retained = false;
        for (int i = 0; i < records.length(); i++) {
            org.json.JSONObject row = records.getJSONObject(i);
            if (marker.equals(row.getString("id"))) retained = "PAUSED".equals(row.getString("stage"));
        }
        if (!retained) throw new AssertionError("Paused download was not retained over APK installation");
        await("QA release download");
        tap("Reanudar");
        long deadline = SystemClock.uptimeMillis() + 120_000;
        while (find("Disponible sin conexión") == null && SystemClock.uptimeMillis() < deadline) {
            if (find("No se pudo descargar") != null) throw new AssertionError("Minified WorkManager download failed");
            SystemClock.sleep(200);
        }
        if (find("Disponible sin conexión") == null) {
            screenshot("download-stalled-021-release.png");
            throw new AssertionError("Download did not complete: " + getTargetContext()
                .getSharedPreferences("aetherio_media_downloads", 0).getString("index", "[]"));
        }
        // The same cached movie must play with BOTH transports disabled. Restore the QA network in finally.
        shell("svc data disable");
        shell("svc wifi disable");
        try {
            tap("Reproducir");
            await("Pausar");
            screenshot("offline-player-021-release.png");
            back();
            await("QA release download");
        } finally { shell("svc wifi enable"); shell("svc data enable"); }
        // The player returns from landscape; wait for the portrait window to settle before tapping its icon.
        getUiAutomation().waitForIdle(500, 5_000);
        tap("Eliminar QA release download");
        long dialogDeadline = SystemClock.uptimeMillis() + 5_000;
        while (find("Eliminar descarga") == null && SystemClock.uptimeMillis() < dialogDeadline) SystemClock.sleep(100);
        if (find("Eliminar descarga") == null) {
            tap("Eliminar QA release download");
        }
        await("Eliminar descarga");
        tap("Eliminar");
        await("Sin descargas");
        // Remove only this generated movie's progress; never restore or clear somebody else's history.
        android.content.SharedPreferences history = getTargetContext().getSharedPreferences("aetherio_watch_history", 0);
        String raw = history.getString("history", null);
        if (raw != null) {
            org.json.JSONObject json = new org.json.JSONObject(raw);
            org.json.JSONArray original = json.getJSONArray("entries");
            org.json.JSONArray kept = new org.json.JSONArray();
            for (int i = 0; i < original.length(); i++) {
                org.json.JSONObject row = original.getJSONObject(i);
                if (!"99021999".equals(row.getJSONObject("target").getJSONObject("item").optString("id"))) kept.put(row);
            }
            if (kept.length() != original.length() && !history.edit().putString("history", json.put("entries", kept).toString()).commit())
                throw new AssertionError("Cannot remove only QA movie progress");
        }
    }

    private void shell(String command) throws Exception {
        try (android.os.ParcelFileDescriptor descriptor = getUiAutomation().executeShellCommand(command);
             java.io.FileInputStream input = new java.io.FileInputStream(descriptor.getFileDescriptor())) {
            byte[] buffer = new byte[2048];
            while (input.read(buffer) != -1) { /* Drain shell output before the next device action. */ }
        }
    }

    private void back() {
        sendKeySync(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
        sendKeySync(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
    }

    private AccessibilityNodeInfo findEdit(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable() && node.isVisibleToUser()) return node;
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo found = findEdit(node.getChild(index));
            if (found != null) return found;
        }
        return null;
    }

    private void screenshot(String name) throws Exception {
        android.graphics.Bitmap image = getUiAutomation().takeScreenshot();
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null), name))) {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
        } finally { image.recycle(); }
    }

    private void scrollTo(String text) throws Exception {
        for (int attempt = 0; attempt < 7; attempt++) {
            if (find(text) != null) return;
            Rect screen = new Rect();
            getUiAutomation().getRootInActiveWindow().getBoundsInScreen(screen);
            long down = SystemClock.uptimeMillis();
            for (int step = 0; step <= 10; step++) {
                MotionEvent event = MotionEvent.obtain(down, down + step * 35,
                    step == 0 ? MotionEvent.ACTION_DOWN : step == 10 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE,
                    screen.centerX(), screen.bottom * (.72f - .04f * step), 0);
                event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                try { getUiAutomation().injectInputEvent(event, true); } finally { event.recycle(); }
            }
            getUiAutomation().waitForIdle(300, 3_000);
        }
        throw new AssertionError("Cannot scroll to " + text);
    }

    private AccessibilityNodeInfo find(String text) {
        if (Build.VERSION.SDK_INT >= 33) getUiAutomation().clearCache();
        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        AccessibilityNodeInfo exact = findExact(root, text);
        return exact != null ? exact : find(root, text);
    }

    private AccessibilityNodeInfo findExact(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        if (node.isVisibleToUser() && (text.contentEquals(node.getText() == null ? "" : node.getText()) ||
            text.contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription()))) return node;
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo found = findExact(node.getChild(index), text);
            if (found != null) return found;
        }
        return null;
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
        if (find(text) == null) {
            try { scrollTo(text); } catch (Exception error) { throw new AssertionError(error); }
        }
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
