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
 */
package uk.ac.manchester.tornado.runtime.common;

import static uk.ac.manchester.tornado.runtime.common.Tornado.getProperty;

public class TornadoOptions {

    public static final String FALSE = "FALSE";
    public static final String TRUE = "TRUE";
    public static final boolean TIME_IN_NANOSECONDS = Boolean.parseBoolean(System.getProperty("tornado.ns.time", TRUE));
    public static final int DEFAULT_DRIVER_INDEX = Integer.parseInt(Tornado.getProperty("tornado.driver", "0"));
    public static final int DEFAULT_DEVICE_INDEX = Integer.parseInt(Tornado.getProperty("tornado.device", "0"));
    /**
     * Priority of the PTX Backend. The higher the number, the more priority over
     * the rest of the backends.
     */
    public static final int PTX_BACKEND_PRIORITY = Integer.parseInt(Tornado.getProperty("tornado.ptx.priority", "0"));
    /**
     * Priority of the OpenCL Backend. The higher the number, the more priority over
     * the rest of the backends.
     */
    public static final int OPENCL_BACKEND_PRIORITY = Integer.parseInt(Tornado.getProperty("tornado.opencl.priority", "10"));
    /**
     * Priority of the SPIRV Backend. The higher the number, the more priority over
     * the rest of the backends.
     */
    public static final int SPIRV_BACKEND_PRIORITY = Integer.parseInt(Tornado.getProperty("tornado.spirv.priority", "11"));
    /**
     * Check if the FPGA emulation mode has been set.
     */
    public static final boolean FPGA_EMULATION = isFPGAEmulation();
    /**
     * Option to set the device maximum memory usage. It is set to 1GB by default.
     */
    public static final long DEVICE_AVAILABLE_MEMORY = RuntimeUtilities.parseSize(System.getProperty("tornado.device.memory", "1GB"));
    /**
     * Option to enable exceptions for the OpenCL generated code. This is
     * experimental.
     */
    public static final boolean ENABLE_EXCEPTIONS = Boolean.parseBoolean(System.getProperty("tornado.exceptions", FALSE));
    /**
     * Option to print TornadoVM Internal Bytecodes.
     */
    public static final boolean PRINT_BYTECODES = getBooleanValue("tornado.print.bytecodes", FALSE);
    /**
     * Option to debug dynamic reconfiguration policies.
     * <p>
     * Use `-Dtornado.dynamic.verbose=True`.
     */
    public static final boolean DEBUG_POLICY = getBooleanValue("tornado.dynamic.verbose", FALSE);
    /**
     * Option to enable experimental and new option for performing automatic full
     * reductions.
     */
    public static final boolean EXPERIMENTAL_REDUCE = getBooleanValue("tornado.experimental.reduce", TRUE);
    /**
     * Temporal option for disabling null checks for Apache-Flink.
     */
    public static final boolean IGNORE_NULL_CHECKS = getBooleanValue("tornado.ignore.nullchecks", FALSE);
    /**
     * Option to enable profiler-feature extractions.
     */
    public static final boolean FEATURE_EXTRACTION = getBooleanValue("tornado.feature.extraction", FALSE);
    /**
     * Enable/Disable FMA Optimizations. True by default.
     */
    public static final boolean ENABLE_FMA = getBooleanValue("tornado.enable.fma", "True");
    /**
     * Enable/Disable Fix Reads Optimization. True by default.
     */
    public static final boolean ENABLE_FIX_READS = getBooleanValue("tornado.enable.fix.reads", TRUE);
    /**
     * Enable/Disable events dumping on program finish. False by default.
     */
    public static final boolean DUMP_EVENTS = Boolean.parseBoolean(getProperty("tornado.events.dump", FALSE));
    /**
     * Prints the generated code by the TornadoVM compiler. Default is False.
     */
    public static final boolean PRINT_SOURCE = Boolean.parseBoolean(getProperty("tornado.print.kernel", FALSE));
    /**
     * Prints the generated code by the TornadoVM compiler. Default is False.
     */
    public static final String PRINT_SOURCE_DIRECTORY = getProperty("tornado.print.kernel.dir", "");
    /**
     * Once the internal buffers storing events are full, it will start to circulate
     * old events and overwrite them with new ones. Default is True.
     */
    public static final boolean CIRCULAR_EVENTS = Boolean.parseBoolean(getProperty("tornado.circularevents", TRUE));
    /**
     * Sets the array memory alignment for PTX devices. Default is 128 bytes.
     */
    public static final int PTX_ARRAY_ALIGNMENT = Integer.parseInt(getProperty("tornado.ptx.array.align", "128"));
    /**
     * Sets the array memory alignment for OpenCL devices. Default is 128 bytes.
     */
    public static final int OPENCL_ARRAY_ALIGNMENT = Integer.parseInt(getProperty("tornado.opencl.array.align", "128"));
    /**
     * Sets the array memory alignment for SPIRV devices. Default is 128 bytes.
     */
    public static final int SPIRV_ARRAY_ALIGNMENT = Integer.parseInt(getProperty("tornado.spirv.array.align", "128"));
    /**
     * Enables OpenCL code generation based on a virtual device. Default is False.
     */
    public static final boolean VIRTUAL_DEVICE_ENABLED = getBooleanValue("tornado.virtual.device", FALSE);
    /**
     * Specifies the virtual device properties file. Default value is
     * virtual-device.json.
     */
    public static final String VIRTUAL_DEVICE_FILE = Tornado.getProperty("tornado.device.desc", "etc/virtual-device-template.json");
    /**
     * Option to redirect profiler output.
     */
    public static final String PROFILER_DIRECTORY = getProperty("tornado.profiler.dump.dir", "");
    /**
     * Dump the Control-Flow-Graph with IGV for the compiled-graph after the last
     * phase in the Low-Tier.
     */
    public static final boolean DUMP_LOW_TIER_WITH_IGV = getBooleanValue("tornado.debug.lowtier", FALSE);
    /**
     * In the case of a TornadoVM runtime, JIT compiler or driver failure (OpenCL,
     * PTX or SPIRV), this option allows users to automatically execute the code
     * with plain Java if an exception occurs when compiling or running the parallel
     * code. This option is True by default.
     */
    public static final boolean RECOVER_BAILOUT = getBooleanValue("tornado.recover.bailout", TRUE);
    /**
     * Option to log the IP of the current machine on the profiler logs.
     */
    public static final boolean LOG_IP = getBooleanValue("tornado.enable.ip.logging", FALSE);
    /**
     * Option to send the feature extractions and/or profiler logs to a specific
     * port.
     */
    public static final String SOCKET_PORT = getProperty("tornado.dump.to.ip", "");
    /**
     * Sets the number of threads for the Tornado Sketcher. Default is 4.
     */
    public static final int TORNADO_SKETCHER_THREADS = Integer.parseInt(getProperty("tornado.sketcher.threads", "4"));
    /**
     * It enables automatic discovery and parallelization of loops. Please note that
     * this option is experimental and may cause issues if enabled.
     */
    public static final boolean AUTO_PARALLELISATION = getBooleanValue("tornado.parallelise.auto", FALSE);
    /**
     * Full Inlining Policy with the TornadoVM JIT compiler. Default is False.
     */
    public static final boolean FULL_INLINING = getBooleanValue("tornado.compiler.fullInlining", FALSE);
    /**
     * It enables inlining during Java bytecode parsing. Default is False.
     */
    public static final boolean INLINE_DURING_BYTECODE_PARSING = getBooleanValue("tornado.compiler.bytecodeInlining", FALSE);
    /**
     * Use Level Zero as a dispatcher for SPIRV
     */
    public static final boolean USE_LEVELZERO_FOR_SPIRV = getBooleanValue("tornado.spirv.levelzero", TRUE);
    /**
     * Check I/O parameters for every task within a task-graph.
     */
    public static final boolean FORCE_CHECK_PARAMETERS = getBooleanValue("tornado.check.parameters", TRUE);
    /**
     * Select Shared Memory allocator for SPIRV-Level Zero implementation.
     */
    public static final boolean LEVEL_ZERO_SHARED_MEMORY = getBooleanValue("tornado.spirv.levelzero.memoryAlloc.shared", FALSE);
    /**
     * Use return as a common label and insert the instruction before function
     * ending.
     */
    public static final boolean SPIRV_RETURN_LABEL = getBooleanValue("tornado.spirv.returnlabel", TRUE);
    /**
     * Use the heap and frame index for any direct call invocation inside the
     * generated SPIRV kernel.
     */
    public static final boolean SPIRV_DIRECT_CALL_WITH_LOAD_HEAP = getBooleanValue("tornado.spirv.directcall.heap", FALSE);
    /**
     * Trace code generation
     */
    public static final boolean TRACE_CODE_GEN = getBooleanValue("tornado.logger.codegen", FALSE);
    /**
     * Trace code generation
     */
    public static final boolean TRACE_BUILD_LIR = getBooleanValue("tornado.logger.buildlir", FALSE);
    /**
     * It enables native math functions for the code generation.
     */
    public static final boolean ENABLE_NATIVE_FUNCTION = getBooleanValue("tornado.enable.nativeFunctions", TRUE);
    /**
     * - It enables more aggressive math optimizations
     */
    public static final boolean MATH_OPTIMIZATIONS = getBooleanValue("tornado.enable.mathOptimizations", TRUE);
    /**
     * It enables more fast math optimizations
     */
    public static final boolean FAST_MATH_OPTIMIZATIONS = getBooleanValue("tornado.enable.fastMathOptimizations", TRUE);
    /**
     * It optimizes loads and stores for the SPIRV backend. It uses less virtual
     * registers. Experimental Feature.
     */
    public static final boolean OPTIMIZE_LOAD_STORE_SPIRV = getBooleanValue("tornado.spirv.loadstore", TRUE);
    /**
     * Use Level Zero Thread Suggestions for the Thread Dispatcher. True by default.
     */
    public static final boolean USE_LEVELZERO_THREAD_DISPATCHER_SUGGESTIONS = getBooleanValue("tornado.spirv.levelzero.thread.dispatcher", TRUE);
    /**
     * Memory Alignment for the Level Zero buffers (shared memory and or device
     * memory)
     */
    public static final int LEVEL_ZERO_BUFFER_ALIGNMENT = getIntValue("tornado.spirv.levelzero.alignment", "64");
    /**
     * Enable/Disable the extended memory allocation mode for the Level Zero
     * Backend. It is enabled by default.
     */
    public static final boolean LEVEL_ZERO_EXTENDED_MEMORY_MODE = getBooleanValue("tornado.spirv.levelzero.extended.memory", TRUE);
    /**
     * If enabled, the TornadoVM will substitute the last READ (data transfer from
     * the TRANSFER_DEVICE_TO_HOST) using a STREAM_OUT_BLOCKING. This is FALSE by
     * default.
     */
    public static final boolean ENABLE_STREAM_OUT_BLOCKING = getBooleanValue("tornado.enable.streamOut.blocking", FALSE);
    /**
     * Option to run concurrently on multiple device in single or multi-backend
     * configuration. False by default.
     */
    public static final boolean CONCURRENT_INTERPRETERS = Boolean.parseBoolean(System.getProperty("tornado.concurrent.devices", "False"));
    public static String PROFILER_LOG = "tornado.log.profiler";
    public static String PROFILER = "tornado.profiler";
    /**
     * Option to load FPGA pre-compiled binaries.
     */
    public static StringBuilder FPGA_BINARIES = System.getProperty("tornado.precompiled.binary", null) != null ? new StringBuilder(System.getProperty("tornado.precompiled.binary", null)) : null;

    /**
     * Option for enabling saving the profiler into a file.
     */
    public static boolean PROFILER_LOGS_ACCUMULATE() {
        return getBooleanValue(PROFILER_LOG, FALSE);
    }

    /**
     * Option to reuse device buffers every time a task-graph is executed. True by
     * default.
     */
    public static boolean isReusedBuffersEnabled() {
        return getBooleanValue("tornado.reuse.device.buffers", TRUE);
    }

    /**
     * Option to enable profiler. It can be disabled at any point during runtime.
     *
     * @return boolean.
     */
    public static boolean isProfilerEnabled() {
        return getBooleanValue(PROFILER, FALSE);
    }

    /**
     * Option for enabling partial loop unrolling. The unroll factor can be
     * configured to take any integer value of power of 2 and less than 32.
     *
     * @return boolean.
     */
    public static boolean isPartialUnrollEnabled() {
        return getBooleanValue("tornado.experimental.partial.unroll", FALSE);
    }

    private static boolean getBooleanValue(String property, String defaultValue) {
        return Boolean.parseBoolean(System.getProperty(property, defaultValue));
    }

    private static int getIntValue(String property, String defaultValue) {
        return Integer.parseInt(System.getProperty(property, defaultValue));
    }

    private static boolean isFPGAEmulation() {
        String contextEmulatorIntelFPGA = System.getenv("CL_CONTEXT_EMULATOR_DEVICE_INTELFPGA");
        String contextEmulatorXilinxFPGA = System.getenv("XCL_EMULATION_MODE");
        return (contextEmulatorIntelFPGA != null && (contextEmulatorIntelFPGA.equals("1"))) || (contextEmulatorXilinxFPGA != null && (contextEmulatorXilinxFPGA.equals("sw_emu")));
    }

}
