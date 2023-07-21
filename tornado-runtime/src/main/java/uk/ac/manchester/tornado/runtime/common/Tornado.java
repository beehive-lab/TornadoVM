/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2022, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import uk.ac.manchester.tornado.api.TornadoCI;

public final class Tornado implements TornadoCI {

    public static final TornadoLogger log = new TornadoLogger(TornadoLogger.class);
    private static final Properties settings = System.getProperties();
    public static final boolean VALIDATE_ARRAY_HEADERS = Boolean.parseBoolean(settings.getProperty("tornado.opencl.array.validate", "False"));
    public static final boolean TORNADO_LOOPS_REVERSE = Boolean.parseBoolean(settings.getProperty("tornado.loops.reverse", "True"));
    public static final boolean MARKER_USE_BARRIER = Boolean.parseBoolean(settings.getProperty("tornado.opencl.marker.asbarrier", "False"));
    public static final boolean DEBUG_KERNEL_ARGS = Boolean.parseBoolean(settings.getProperty("tornado.debug.kernelargs", "False"));
    public static final boolean FORCE_ALL_TO_GPU = Boolean.parseBoolean(settings.getProperty("tornado.opencl.forcegpu", "False"));
    public static final boolean USE_SYNC_FLUSH = Boolean.parseBoolean(settings.getProperty("tornado.opencl.syncflush", "False"));
    public static final boolean USE_VM_FLUSH = Boolean.parseBoolean(settings.getProperty("tornado.opencl.vmflush", "True"));
    public static final int EVENT_WINDOW = Integer.parseInt(getProperty("tornado.eventpool.size", "1024"));
    public static final int MAX_WAIT_EVENTS = Integer.parseInt(getProperty("tornado.eventpool.maxwaitevents", "32"));
    public static final boolean OPENCL_USE_RELATIVE_ADDRESSES = Boolean.parseBoolean(settings.getProperty("tornado.opencl.userelative", "False"));
    public static final boolean DUMP_COMPILED_METHODS = Boolean.parseBoolean(getProperty("tornado.compiled.dump", "False"));
    public static final boolean ENABLE_PROFILING = Boolean.parseBoolean(settings.getProperty("tornado.profiling.enable", "True"));
    public static final boolean ENABLE_OOO_EXECUTION = Boolean.parseBoolean(settings.getProperty("tornado.ooo-execution.enable", "False"));
    public static final boolean VM_USE_DEPS = Boolean.parseBoolean(Tornado.getProperty("tornado.vm.deps", "False"));
    public static final int UNROLL_FACTOR = Integer.parseInt(getProperty("tornado.unroll.factor", "2"));

    public static final boolean ENABLE_VECTORS = Boolean.parseBoolean(settings.getProperty("tornado.vectors.enable", "True"));
    public static final boolean TORNADO_ENABLE_BIFS = Boolean.parseBoolean(settings.getProperty("tornado.bifs.enable", "False"));
    public static final boolean DEBUG = Boolean.parseBoolean(settings.getProperty("tornado.debug", "False"));
    public static final boolean FPGA_DUMP_LOG = Boolean.parseBoolean(settings.getProperty("tornado.fpgaDumpLog", "False"));

    public static final boolean FULL_DEBUG = Boolean.parseBoolean(settings.getProperty("tornado.fullDebug", "False"));
    public static final boolean SHOULD_LOAD_RMI = Boolean.parseBoolean(settings.getProperty("tornado.rmi.enable", "false"));
    private static final String TORNADO_SDK_VARIABLE = "TORNADO_SDK";
    public static boolean FORCE_BLOCKING_API_CALLS = false;

    static {
        tryLoadSettings();
    }

    private static void setProperty(String key, String value) {
        settings.setProperty(key, value);
    }

    public static String getProperty(String key) {
        return settings.getProperty(key);
    }

    public static String getProperty(String key, String defaultValue) {
        return settings.getProperty(key, defaultValue);
    }

    public static void debug(final String msg) {
        log.debug(msg);
    }

    private static void loadSettings(String filename) {
        final File localSettings = new File(filename);
        Properties loadProperties = new Properties();
        if (localSettings.exists()) {
            try (FileInputStream fileInputStream = new FileInputStream(localSettings)) {
                loadProperties.load(fileInputStream);
            } catch (IOException e) {
                warn("Unable to load settings from %s", localSettings.getAbsolutePath());
            }
        }
        /*
         * merge local and system properties, note that command line arguments override
         * saved properties
         */
        Set<String> localKeys = loadProperties.stringPropertyNames();
        Set<String> systemKeys = settings.stringPropertyNames();
        Set<String> diff = new HashSet<>(localKeys);
        diff.removeAll(systemKeys);

        for (String key : diff) {
            settings.setProperty(key, loadProperties.getProperty(key));
        }
        FORCE_BLOCKING_API_CALLS = Boolean.parseBoolean(settings.getProperty("tornado.opencl.blocking", "False"));
    }

    private static void tryLoadSettings() {
        final String tornadoRoot = System.getenv(TORNADO_SDK_VARIABLE);
        loadSettings(tornadoRoot + "/etc/tornado.properties");
    }

    public static void debug(final String pattern, final Object... args) {
        debug(String.format(pattern, args));
    }

    public static void error(final String msg) {
        log.error(msg);
    }

    public static void error(final String pattern, final Object... args) {
        error(String.format(pattern, args));
    }

    public static void fatal(final String msg) {
        log.fatal(msg);
    }

    public static void fatal(final String pattern, final Object... args) {
        fatal(String.format(pattern, args));
    }

    public static void info(final String msg) {
        log.info(msg);
    }

    public static void info(final String pattern, final Object... args) {
        info(String.format(pattern, args));
    }

    public static void trace(final String msg) {
        log.trace(msg);
    }

    public static void trace(final String pattern, final Object... args) {
        trace(String.format(pattern, args));
    }

    public static void warn(final String msg) {
        log.warn(msg);
    }

    public static void warn(final String pattern, final Object... args) {
        warn(String.format(pattern, args));
    }

    @Override
    public void setTornadoProperty(String key, String value) {
        Tornado.setProperty(key, value);
    }

    @Override
    public String getTornadoProperty(String key) {
        return Tornado.getProperty(key);
    }

    @Override
    public String getTornadoProperty(String key, String defaultValue) {
        return Tornado.getProperty(key, defaultValue);
    }

    @Override
    public void loadTornadoSettings(String filename) {
        loadSettings(filename);
    }

}
