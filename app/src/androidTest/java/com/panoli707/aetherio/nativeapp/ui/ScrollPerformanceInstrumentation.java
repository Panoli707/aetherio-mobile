package com.panoli707.aetherio.nativeapp.ui;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.FrameMetrics;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Opt-in, framework-only frame measurements on the real minified APK. Never touches playback or user data.
 * Build the test APK with -PscrollReleaseSmoke=true, install it alongside the release, then run
 * am instrument -w -e manualScrollPerformance true
 * com.panoli707.aetherio.nativeapp.test/com.panoli707.aetherio.nativeapp.ui.ScrollPerformanceInstrumentation.
 * Run baseline and candidate on the same idle emulator; this is not a device-independent FPS assertion.
 */
public final class ScrollPerformanceInstrumentation extends Instrumentation {
    private final List<Long> frames = Collections.synchronizedList(new ArrayList<>());
    private final List<long[]> costs = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean measuring;
    private boolean enabled;
    private int width;
    private int height;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        enabled = arguments != null && "true".equals(arguments.getString("manualScrollPerformance")) &&
            Build.MODEL.toLowerCase(Locale.ROOT).contains("sdk");
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        HandlerThread collector = new HandlerThread("scroll-frame-metrics");
        collector.start();
        Activity activity = null;
        android.view.Window.OnFrameMetricsAvailableListener listener = (window, metrics, dropped) -> {
            if (measuring && metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 0) {
                frames.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION));
                costs.add(new long[] { metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),
                    metrics.getMetric(FrameMetrics.DRAW_DURATION), metrics.getMetric(FrameMetrics.SYNC_DURATION),
                    metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION), metrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION),
                    Build.VERSION.SDK_INT >= 31 ? metrics.getMetric(FrameMetrics.GPU_DURATION) : 0 });
            }
        };
        try {
            if (!enabled) throw new IllegalStateException("Enable manualScrollPerformance on an emulator");
            activity = startActivitySync(new Intent(Intent.ACTION_MAIN).setClassName(getTargetContext().getPackageName(),
                "com.panoli707.aetherio.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await("Anime emitiéndose ahora");
            SystemClock.sleep(3_000);
            Rect screen = new Rect();
            getUiAutomation().getRootInActiveWindow().getBoundsInScreen(screen);
            width = screen.width(); height = screen.height();
            activity.getWindow().addOnFrameMetricsAvailableListener(listener, new Handler(collector.getLooper()));

            measureVertical("home-first-pass");
            measureVertical("home-warm-pass");
            // Bring the anime row away from the bottom bar, then exercise the horizontal gesture from Bug1.
            AccessibilityNodeInfo row = find("Anime emitiéndose ahora");
            if (row == null) {
                tapAt(width * .14f, height * .91f);
                SystemClock.sleep(1_000);
                row = await("Anime emitiéndose ahora");
            }
            Rect label = bounds(row);
            for (int attempt = 0; label.bottom > height * .35f && attempt < 3; attempt++) {
                swipe(.5f, .76f, .5f, .52f);
                label = bounds(await("Anime emitiéndose ahora"));
            }
            row = await("Anime emitiéndose ahora");
            label = bounds(row);
            float cardY = Math.min(.72f, (label.bottom + 160f) / height);
            AccessibilityNodeInfo card = cardAt(getUiAutomation().getRootInActiveWindow(), width * .22f, height * cardY);
            if (card == null || cardText(card).isEmpty()) throw new AssertionError("Cannot identify a stable detail fixture");
            String fixture = cardText(card);
            Bundle fixtureStatus = new Bundle();
            fixtureStatus.putString("stream", "DETAIL_FIXTURE hash=" + Integer.toHexString(fixture.hashCode()) + "\n");
            sendStatus(0, fixtureStatus);
            frames.clear(); costs.clear(); measuring = true;
            for (int repeat = 0; repeat < 4; repeat++) {
                swipe(.82f, cardY, .18f, cardY);
                swipe(.18f, cardY, .82f, cardY);
            }
            measuring = false; report("anime-horizontal");

            // Always open the same card, not whichever item a fling happened to stop on.
            for (int attempt = 0; findFixture(getUiAutomation().getRootInActiveWindow(), fixture) == null && attempt < 12; attempt++)
                swipe(.18f, cardY, .82f, cardY);
            AccessibilityNodeInfo selected = findFixture(getUiAutomation().getRootInActiveWindow(), fixture);
            if (selected == null) throw new AssertionError("Could not return to the original detail fixture");
            Rect selectedCard = bounds(selected);
            tapAt(selectedCard.centerX(), Math.min(selectedCard.centerY(), height * cardY));
            await("Información");
            SystemClock.sleep(3_000);
            measureVertical("detail-first-pass");
            measureVertical("detail-warm-pass");
            result.putString("stream", "OK: scroll frame measurements completed; timings apply only to this emulator\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            measuring = false;
            if (activity != null) activity.getWindow().removeOnFrameMetricsAvailableListener(listener);
            collector.quitSafely();
        }
    }

    private void measureVertical(String phase) {
        frames.clear(); costs.clear(); measuring = true;
        for (int repeat = 0; repeat < 3; repeat++) swipe(.55f, .78f, .55f, .30f);
        for (int repeat = 0; repeat < 3; repeat++) swipe(.55f, .30f, .55f, .78f);
        measuring = false; report(phase);
    }

    private void report(String phase) {
        List<Long> sorted;
        synchronized (frames) { sorted = new ArrayList<>(frames); }
        if (sorted.size() < 10) throw new AssertionError("Insufficient frames for " + phase);
        Collections.sort(sorted);
        long over50 = sorted.stream().filter(value -> value > 50_000_000).count();
        long over100 = sorted.stream().filter(value -> value > 100_000_000).count();
        String line = String.format(Locale.US,
            "FRAME_METRICS phase=%s frames=%d p50_ms=%.2f p95_ms=%.2f p99_ms=%.2f max_ms=%.2f over50=%d over100=%d%n",
            phase, sorted.size(), percentile(sorted, .50), percentile(sorted, .95), percentile(sorted, .99),
            sorted.get(sorted.size() - 1) / 1_000_000.0, over50, over100);
        Bundle status = new Bundle(); status.putString("stream", line); sendStatus(0, status);
        String[] labels = { "layout", "draw", "sync", "commands", "unknown_delay", "gpu" };
        List<long[]> costCopy;
        synchronized (costs) { costCopy = new ArrayList<>(costs); }
        StringBuilder breakdown = new StringBuilder("FRAME_COSTS phase=" + phase);
        for (int column = 0; column < labels.length; column++) {
            List<Long> samples = new ArrayList<>();
            for (long[] values : costCopy) samples.add(values[column]);
            Collections.sort(samples);
            breakdown.append(String.format(Locale.US, " %s_p95_ms=%.2f", labels[column], percentile(samples, .95)));
        }
        status.putString("stream", breakdown + "\n"); sendStatus(0, status);
    }

    private double percentile(List<Long> sorted, double fraction) {
        return sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * fraction) - 1)) / 1_000_000.0;
    }

    private Rect bounds(AccessibilityNodeInfo node) {
        node.refresh(); Rect rect = new Rect(); node.getBoundsInScreen(rect); return rect;
    }

    private AccessibilityNodeInfo cardAt(AccessibilityNodeInfo node, float x, float y) {
        if (node == null) return null;
        Rect rect = bounds(node);
        if (node.isVisibleToUser() && node.isClickable() && rect.contains((int) x, (int) y) &&
            rect.width() < width * .6f) return node;
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo found = cardAt(node.getChild(index), x, y);
            if (found != null) return found;
        }
        return null;
    }

    private String cardText(AccessibilityNodeInfo node) {
        if (node == null) return "";
        StringBuilder text = new StringBuilder(node.getText() == null ? "" : node.getText());
        for (int index = 0; index < node.getChildCount(); index++) text.append(cardText(node.getChild(index)));
        return text.toString();
    }

    private AccessibilityNodeInfo findFixture(AccessibilityNodeInfo node, String fixture) {
        if (node == null) return null;
        if (node.isVisibleToUser() && node.isClickable() && fixture.equals(cardText(node))) return node;
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo found = findFixture(node.getChild(index), fixture);
            if (found != null) return found;
        }
        return null;
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
        long deadline = SystemClock.uptimeMillis() + 60_000;
        do {
            AccessibilityNodeInfo node = find(text);
            if (node != null) return node;
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Screen did not show " + text);
    }

    private void tapAt(float x, float y) {
        long down = SystemClock.uptimeMillis();
        inject(down, down, MotionEvent.ACTION_DOWN, x, y);
        inject(down, down + 50, MotionEvent.ACTION_UP, x, y);
        SystemClock.sleep(350);
    }

    private void swipe(float startX, float startY, float endX, float endY) {
        long down = SystemClock.uptimeMillis();
        inject(down, down, MotionEvent.ACTION_DOWN, width * startX, height * startY);
        for (int step = 1; step < 25; step++) {
            SystemClock.sleep(16);
            float fraction = step / 25f;
            inject(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE,
                width * (startX + (endX - startX) * fraction), height * (startY + (endY - startY) * fraction));
        }
        inject(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, width * endX, height * endY);
        SystemClock.sleep(500);
    }

    private void inject(long down, long time, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, time, action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try { getUiAutomation().injectInputEvent(event, true); } finally { event.recycle(); }
    }
}
