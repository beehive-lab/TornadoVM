/* 
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public final class Tornado {

    private final static Properties settings = System.getProperties();

    static {
        tryLoadSettings();
    }

    public static void setProperty(String key, String value) {
        settings.setProperty(key, value);
    }

    public static String getProperty(String key) {
        return settings.getProperty(key);
    }

    public static String getProperty(String key, String defaultValue) {
        return settings.getProperty(key, defaultValue);
    }

    /*
     * Forces the executing kernel to output its arguements before execution
     */
    public static final boolean DUMP_TASK_SCHEDULE = Boolean.parseBoolean(settings.getProperty("tornado.schedule.dump", "False"));
    public static final boolean DEBUG_KERNEL_ARGS = Boolean.parseBoolean(settings.getProperty("tornado.debug.kernelargs", "False"));

    public static final boolean PRINT_COMPILE_TIMES = Boolean.parseBoolean(settings.getProperty("tornado.debug.compiletimes", "False"));

    public static final boolean FORCE_ALL_TO_GPU = Boolean.parseBoolean(settings.getProperty("tornado.opencl.forcegpu", "False"));

    public static final boolean OPENCL_USE_RELATIVE_ADDRESSES = Boolean.parseBoolean(settings.getProperty("tornado.opencl.userelative", "False"));
    public static final boolean OPENCL_WAIT_ACTIVE = Boolean.parseBoolean(settings.getProperty("tornado.opencl.wait.active", "False"));

    /*
     * Allows the OpenCL driver to select the size of local work groups
     */
    public static final boolean USE_OPENCL_SCHEDULING = Boolean.parseBoolean(settings.getProperty("tornado.opencl.schedule", "False"));

    public static final boolean VM_WAIT_EVENT = Boolean.parseBoolean(settings.getProperty("tornado.vm.waitevent", "False"));

    public static final boolean ENABLE_EXCEPTIONS = Boolean
            .parseBoolean(settings.getProperty("tornado.exceptions.enable",
                    "False"));

    public static final boolean ENABLE_PROFILING = Boolean
            .parseBoolean(settings.getProperty("tornado.profiling.enable",
                    "False"));

    public static final boolean ENABLE_OOO_EXECUTION = Boolean
            .parseBoolean(settings.getProperty("tornado.ooo-execution.enable",
                    "False"));
    public static final boolean VM_USE_DEPS = Boolean.parseBoolean(Tornado.getProperty("tornado.vm.deps", "False"));
    public static final boolean FORCE_BLOCKING_API_CALLS = Boolean
            .parseBoolean(settings.getProperty("tornado.opencl.blocking",
                    "False"));

    public static final boolean ENABLE_PARALLELIZATION = Boolean.parseBoolean(Tornado.getProperty("tornado.kernels.parallelize", "True"));
    public static final boolean USE_THREAD_COARSENING = Boolean.parseBoolean(Tornado.getProperty("tornado.kernels.coarsener", "True"));

    public static final boolean ENABLE_VECTORS = Boolean.parseBoolean(settings
            .getProperty("tornado.vectors.enable", "True"));
    public static final boolean TORNADO_ENABLE_BIFS = Boolean
            .parseBoolean(settings.getProperty("tornado.bifs.enable", "False"));

    public static final boolean DEBUG = Boolean.parseBoolean(settings
            .getProperty("tornado.debug", "False"));

    public static final boolean ENABLE_MEM_CHECKS = Boolean
            .parseBoolean(settings.getProperty("tornado.memory.check", "False"));

    public static final boolean LOG_EVENTS = Boolean.parseBoolean(settings
            .getProperty("tornado.events.log", "False"));

    public static final boolean DUMP_PROFILES = Boolean.parseBoolean(settings.getProperty("tornado.profiles.print", "false"));

    public static final String OPENCL_CFLAGS = settings.getProperty(
            "tornado.opencl.cflags", "-w");

    public static final int OPENCL_GPU_BLOCK_X = Integer.parseInt(settings
            .getProperty("tornado.opencl.gpu.block.x", "256"));

    public static final int OPENCL_GPU_BLOCK_2D_X = Integer.parseInt(settings
            .getProperty("tornado.opencl.gpu.block2d.x", "4"));
    public static final int OPENCL_GPU_BLOCK_2D_Y = Integer.parseInt(settings
            .getProperty("tornado.opencl.gpu.block2d.y", "4"));

    public static final boolean SHOULD_LOAD_RMI = Boolean.parseBoolean(settings.getProperty("tornado.rmi.enable", "false"));

    public static final TornadoLogger log = new TornadoLogger(Tornado.class);

    public static final void debug(final String msg) {
        log.debug(msg);
    }

    private static void tryLoadSettings() {
        final String tornadoRoot = System.getenv("TORNADO_ROOT");
        final File localSettings = new File(tornadoRoot + "/etc/tornado.properties");
        Properties loadProperties = new Properties();
        if (localSettings.exists()) {
            try {
                loadProperties.load(new FileInputStream(localSettings));
            } catch (IOException e) {
                warn("Unable to load settings from %s",
                        localSettings.getAbsolutePath());
            }
        }

        // merge local and system properties
        // note that command line args override saved properties
        Set<String> localKeys = loadProperties.stringPropertyNames();
        Set<String> systemKeys = settings.stringPropertyNames();
        Set<String> diff = new HashSet<>();
        diff.addAll(localKeys);
        diff.removeAll(systemKeys);

        for (String key : diff) {
            settings.setProperty(key, loadProperties.getProperty(key));
        }
    }

    public static final void debug(final String pattern, final Object... args) {
        debug(String.format(pattern, args));
    }

    public static final void error(final String msg) {
        log.error(msg);
    }

    public static final void error(final String pattern, final Object... args) {
        error(String.format(pattern, args));
    }

    public static final void fatal(final String msg) {
        log.fatal(msg);
    }

    public static final void fatal(final String pattern, final Object... args) {
        fatal(String.format(pattern, args));
    }

    public static final void info(final String msg) {
        log.info(msg);
    }

    public static final void info(final String pattern, final Object... args) {
        info(String.format(pattern, args));
    }

    public static final void trace(final String msg) {
        log.trace(msg);
    }

    public static final void trace(final String pattern, final Object... args) {
        trace(String.format(pattern, args));
    }

    public static final void warn(final String msg) {
        log.warn(msg);
    }

    public static final void warn(final String pattern, final Object... args) {
        warn(String.format(pattern, args));
    }

}
