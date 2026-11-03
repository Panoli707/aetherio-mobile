package com.panoli707.aetherio.nativeapp.player.torrent;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.util.Base64;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

/** Tests the real minified APK through stable JNI names, without an AndroidJUnitRunner/Kotlin linkage dependency. */
public final class TorrentReleaseInstrumentation extends Instrumentation {
    private Class<?> bridge;
    private Object instance;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    private Object call(String name, Object... arguments) throws Exception {
        for (Method method : bridge.getDeclaredMethods()) {
            if (method.getName().equals(name)) return method.invoke(instance, arguments);
        }
        throw new NoSuchMethodException(name);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    @Override public void onStart() {
        super.onStart();
        Bundle result = new Bundle();
        File root = new File(getTargetContext().getCacheDir(), "release-jni-test-" + System.nanoTime());
        long handle = 0;
        try {
            Context context = getTargetContext();
            ClassLoader loader = context.getClassLoader();
            bridge = loader.loadClass("com.nuvio.engine.internal.NativeBridge");
            for (Field field : bridge.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == bridge) {
                    field.setAccessible(true);
                    instance = field.get(null);
                }
            }
            check(instance != null, "JNI bridge instance unavailable");
            Class<?> fileType = loader.loadClass("com.nuvio.engine.internal.NativeFilePayload");
            Class<?> filesType = loader.loadClass("com.nuvio.engine.internal.NativeFilesPayload");
            filesType.getConstructor(int.class, java.lang.reflect.Array.newInstance(fileType, 0).getClass());
            Class<?> eventType = loader.loadClass("com.nuvio.engine.internal.NativeEventPayload");
            check(eventType.getConstructors().length == 1, "Event constructor removed");
            fileType.getConstructor(int.class, long.class, long.class, boolean.class, String.class)
                .newInstance(0, 0L, 1L, false, "fixture.mp4");
            File data = new File(root, "state");
            File cache = new File(root, "cache");
            check(data.mkdirs() && cache.mkdirs(), "Cannot create isolated smoke directories");
            KeyStore store = KeyStore.getInstance("AndroidCAStore");
            store.load(null);
            byte[] certificate = store.getCertificate(store.aliases().nextElement()).getEncoded();
            File trust = new File(root, "ca.pem");
            try (FileOutputStream output = new FileOutputStream(trust)) {
                output.write(("-----BEGIN CERTIFICATE-----\n" + Base64.encodeToString(certificate, Base64.NO_WRAP) +
                    "\n-----END CERTIFICATE-----\n").getBytes(StandardCharsets.US_ASCII));
            }
            long[] created = (long[]) call("nativeCreate", data.getAbsolutePath(), cache.getAbsolutePath(),
                1024L * 1024, 1024L * 1024, 1, 0, 0, 0L, 30000, 0, trust.getAbsolutePath());
            check(created[0] == 0 && created[1] != 0, "Native create failed");
            handle = created[1];
            // Unknown torrent still makes JNI allocate NativeFilesPayload; no public swarm or metadata lookup is needed.
            Object files = call("nativeGetFiles", handle, "0000000000000000000000000000000000000000");
            check(filesType.getField("status").getInt(files) != 0, "Unknown torrent unexpectedly present");
            Object[] values = (Object[]) filesType.getField("files").get(files);
            check(values.length == 0, "Unknown torrent returned files");
            result.putString("stream", "OK: minified JNI create, native file result and payload constructors\n");
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
        } finally {
            if (handle != 0) {
                try { call("nativeDestroy", handle); }
                catch (Exception error) { result.putString("stream", "FAIL: native shutdown " + error.getClass().getSimpleName()); }
            }
            deleteOwned(root);
        }
        finish(result.getString("stream", "").startsWith("OK:") ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    /** Only the unique test root, after native shutdown. */
    private static void deleteOwned(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteOwned(child);
        file.delete();
    }
}
