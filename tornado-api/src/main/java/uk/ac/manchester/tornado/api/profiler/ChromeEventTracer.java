
/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * GNU Classpath is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * GNU Classpath is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING.  If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module.  An independent module is a module which is not derived from
 * or based on this library.  If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so.  If you do not wish to do so, delete this
 * exception statement from your version.
 *
 * Author Gary Frost
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
