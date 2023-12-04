/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.api.profiler;

import java.io.File;
import java.util.Map;

public class ChromeEventTracer {
    /**
     * The filename for ChromeEventTracer to write json file.
     */
    public static final String CHROME_EVENT_TRACER_FILENAME_KEY = "tornado.chrome.event.tracer.filename";
    public static final String CHROME_EVENT_TRACER_FILENAME = System.getProperties().getProperty(CHROME_EVENT_TRACER_FILENAME_KEY, "chrome.json");
    public static final String CHROME_EVENT_TRACER_ENABLED_KEY = "tornado.chrome.event.tracer.enabled";
    public static final ChromeEventJSonWriter json = new ChromeEventJSonWriter();

    static {
        if (isEnabled()) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> json.write(new File(getChromeEventTracerFileName()))));
        }
    }

    public ChromeEventTracer() {

    }

    public static String getChromeEventTracerFileName() {
        return System.getProperties().getProperty(CHROME_EVENT_TRACER_FILENAME_KEY, "chrome.json");
    }

    /**
     * Option to enable chrome event format for profiler. It can be disabled at any
     * point during runtime.
     *
     * @return boolean.
     */
    public static boolean isChromeEventTracerEnabled() {
        return Boolean.getBoolean(CHROME_EVENT_TRACER_ENABLED_KEY);
    }

    public static ChromeEventTracer create() {
        return new ChromeEventTracer();
    }

    public static boolean isEnabled() {
        return isChromeEventTracerEnabled();
    }

    public static void enqueueWriteIfEnabled(String tag, long bytes, long startNs, long endNs) {
        if (isEnabled()) {
            json.x(tag, "write", startNs, endNs, () -> json.kv("bytes", bytes));
        }
    }

    public static void enqueueReadIfEnabled(String tag, long bytes, long startNs, long endNs) {
        if (isEnabled()) {
            json.x(tag, "read", startNs, endNs, () -> json.kv("bytes", bytes));
        }
    }

    public static void enqueueNDRangeKernelIfEnabled(String tag, long startNs, long endNs) {
        if (isEnabled()) {
            json.x(tag, "NDRangeKernel", startNs, endNs, null);
        }
    }

    public static void enqueueTaskIfEnabled(String tag, long startNs, long endNs) {
        if (isEnabled()) {
            json.x(tag, "exec", startNs, endNs, null);
        }
    }

    public static void trace(String tag, Runnable r) {
        long startNs = System.nanoTime();
        r.run();
        if (isEnabled()) {
            json.x(tag, "trace", startNs, System.nanoTime(), null);
        }
    }

    public static <T> T trace(String tag, Builder<T> b) {
        long startNs = System.nanoTime();
        T value = b.build();
        if (isEnabled()) {
            json.x(tag, "trace", startNs, System.nanoTime(), null);
        }
        return value;
    }

    public static void opencltimes(int localId, long queuedNs, long submitNs, long startNs, long endNs, Map<String, ?> meta) {
        json.x("queued", null, queuedNs, endNs, meta == null ? null : () -> {
            for (String k : meta.keySet()) {
                json.kv(k, (String) meta.get(k));
            }
        });
        json.x("submit", null, submitNs, endNs, null);
        json.x("start", null, startNs, endNs, null);
        // order queue submit start end
    }

    public interface Builder<T> {
        T build();
    }
}
