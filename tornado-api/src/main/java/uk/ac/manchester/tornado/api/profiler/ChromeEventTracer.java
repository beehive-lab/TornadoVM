package uk.ac.manchester.tornado.api.profiler;

import java.io.*;
import java.util.Map;

public class ChromeEventTracer {
    /**
     * The filename for ChromeEventTracer to write json file.
     */
    public static final String CHROME_EVENT_TRACER_FILENAME_KEY = "tornado.chrome.event.tracer.filename";
    public static final String CHROME_EVENT_TRACER_FILENAME = System.getProperties().getProperty(CHROME_EVENT_TRACER_FILENAME_KEY, "chrome.json");

    public static String getChromeEventTracerFileName() {
        return System.getProperties().getProperty(CHROME_EVENT_TRACER_FILENAME_KEY, "chrome.json");
    }

    public static final String CHROME_EVENT_TRACER_ENABLED_KEY = "tornado.chrome.event.tracer.enabled";

    private static boolean getBooleanValue(String property, String defaultValue) {
        return Boolean.parseBoolean(System.getProperties().getProperty(property, defaultValue));
    }

    /**
     * Option to enable chrome event format for profiler. It can be disabled at any
     * point during runtime.
     *
     * @return boolean.
     */
    public static boolean isChromeEventTracerEnabled() {
        return getBooleanValue(CHROME_EVENT_TRACER_ENABLED_KEY, "False");
    }

    public static ChromeEventTracer create() {
        return new ChromeEventTracer();
    }

    static public final ChromeEventJSonWriter json = new ChromeEventJSonWriter();
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> json.write(new File(getChromeEventTracerFileName()))));
    }

    static public boolean isEnabled() {
        return isChromeEventTracerEnabled();
    }

    static public void enqueueWriteIfEnabled(String tag, long bytes, long startNs, long endNs) {
        if (isEnabled()) {
            json.x(tag, "write", startNs, endNs, () -> {
                json.kv("bytes", bytes);
            });
        }
    }

    static public void enqueueReadIfEnabled(String tag, long bytes, long startNs, long endNs) {
        if (isEnabled()) {
            json.x(tag, "read", startNs, endNs, () -> {
                json.kv("bytes", bytes);
            });
        }
    }

    static public void enqueueNDRangeKernelIfEnabled(String tag, long startNs, long endNs) {
        if (isEnabled()) {
            json.x(tag, "NDRangeKernel", startNs, endNs, null);
        }
    }

    static public void enqueueTaskIfEnabled(String tag, long startNs, long endNs) {
        if (isEnabled()) {
            json.x(tag, "exec", startNs, endNs, null);
        }
    }

    static public void trace(String tag, Runnable r) {
        long startNs = System.nanoTime();
        r.run();
        if (isEnabled()) {
            json.x(tag, "trace", startNs, System.nanoTime(), null);
        }
    }

    public interface Builder<T> {
        T build();
    }

    static public <T> T trace(String tag, Builder<T> b) {
        long startNs = System.nanoTime();
        T value = b.build();
        if (isEnabled()) {
            json.x(tag, "trace", startNs, System.nanoTime(), null);
        }
        return value;
    }

    public ChromeEventTracer() {

    }

    static public void opencltimes(int localId, long queuedNs, long submitNs, long startNs, long endNs, Map<String, ?> meta) {
        json.x("queued", null, queuedNs, endNs, meta == null ? null : () -> {
            for (String k : meta.keySet()) {
                json.kv(k, (String) meta.get(k));
            }
        });
        json.x("submit", null, submitNs, endNs, null);
        json.x("start", null, startNs, endNs, null);
        // order queue submit start end
    }
}
