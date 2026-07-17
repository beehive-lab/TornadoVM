/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
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

import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;

import java.lang.management.ManagementFactory;
import java.util.List;

public class TornadoOptions {

    private static final String FALSE = "FALSE";
    private static final String TRUE = "TRUE";

    /**
     * Default OpenCL Compiler Flags.
     */
    public static final String DEFAULT_OPENCL_COMPILER_FLAGS = getProperty("tornado.opencl.compiler.flags", "-cl-mad-enable -cl-fast-relaxed-math -w");

    /**
     * Default PTX Compiler Flags.
     */
    public static final String DEFAULT_METAL_COMPILER_FLAGS = getProperty("tornado.metal.compiler.flags", "");

    /**
     * Default PTX Compiler Flags. Make sure the flags are passed in the following format: <flag><space><flag value><another flag><space><another flag value> and so on.
     * For example, CU_JIT_OPTIMIZATION_LEVEL 4 CU_JIT_TARGET 120
     */
    public static final String DEFAULT_PTX_COMPILER_FLAGS = getProperty("tornado.ptx.compiler.flags", "CU_JIT_OPTIMIZATION_LEVEL 4");

    /**
     * Default CUDA (NVRTC) Compiler Flags. Passed to NVRTC when compiling the generated CUDA C source.
     */
    public static final String DEFAULT_CUDA_COMPILER_FLAGS = getProperty("tornado.cuda.compiler.flags", "");

    /**
     * Use internal timers for profiling in ns if enabled, in ms if disabled. Default is ns (enabled).
     */
    public static final boolean TIME_IN_NANOSECONDS = Boolean.parseBoolean(System.getProperty("tornado.ns.time", TRUE));

    /**
     * Index for the default backend in TornadoVM. Default is 0.
     */
    public static final int DEFAULT_BACKEND_INDEX = Integer.parseInt(Tornado.getProperty("tornado.backend", "0"));

    /**
     * Index for the default device in TornadoVM. Default is 0.
     */
    public static final int DEFAULT_DEVICE_INDEX = Integer.parseInt(Tornado.getProperty("tornado.device", "0"));

    /**
     * Enable/disable loop interchange optimization from the Sketcher compilation. This optimization is enabled
     * by default. The optimization that reverses the ordering of the loops is:
     * {@link uk.ac.manchester.tornado.runtime.graal.phases.sketcher.TornadoApiReplacement}.
     */
    public static final boolean TORNADO_LOOP_INTERCHANGE = getBooleanValue("tornado.loop.interchange", "True");

    /**
     * Enable thread deployment debugging from the TornadoVM runtime and code dispatcher.
     */
    public static final boolean THREAD_INFO = getBooleanValue("tornado.threadInfo", FALSE);

    /**
     * Enable the runtime to dump the generated code (e.g., OpenCL or CUDA PTX) from the TornadoVM JIT Compiler.
     */
    public static final boolean PRINT_KERNEL_SOURCE = getBooleanValue("tornado.printKernel", FALSE);

    /**
     * Priority of the Metal Backend. The higher the number, the more priority over
     * the rest of the backends.
     */
    public static final int METAL_BACKEND_PRIORITY = Integer.parseInt(Tornado.getProperty("tornado.metal.priority", "0"));
    /**
     * Priority of the PTX Backend. The higher the number, the more priority over
     * the rest of the backends.
     */
    public static final int PTX_BACKEND_PRIORITY = Integer.parseInt(Tornado.getProperty("tornado.ptx.priority", "0"));
    /**
     * Priority of the CUDA Backend. The higher the number, the more priority over
     * the rest of the backends.
     */
    public static final int CUDA_BACKEND_PRIORITY = Integer.parseInt(Tornado.getProperty("tornado.cuda.priority", "0"));
    /**
     * Priority of the OpenCL Backend. The higher the number, the more priority over
     * the rest of the backends.
     */
    public static final int OPENCL_BACKEND_PRIORITY = Integer.parseInt(Tornado.getProperty("tornado.opencl.priority", "10"));
    /**
     * Check if the FPGA emulation mode has been set.
     */
    public static final boolean FPGA_EMULATION = isFPGAEmulation();
    /**
     * Option to set the device maximum memory usage. It is set to 4GB by default.
     */
    public static final long DEVICE_AVAILABLE_MEMORY = RuntimeUtilities.parseSize(System.getProperty("tornado.device.memory", "4GB"));
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
     * Experimental (PTX and CUDA backends): route large one-shot host-to-device transfers (e.g. FIRST_EXECUTION
     * weight uploads) through a ring of pinned host staging buffers (the llama.cpp
     * "ring of 4" pattern). Each chunk is memcpy'd into a pinned slot and uploaded with
     * cuMemcpyHtoDAsync while the next chunk is being staged, overlapping the host-side copy
     * (and the page-in it forces) with the PCIe DMA - and removing the need to
     * cuMemHostRegister the whole source segment up front. Default off.
     */
    public static final boolean ENABLE_STAGED_TRANSFERS = getBooleanValue("tornado.staged.transfers", FALSE);

    /**
     * Chunk size in bytes for {@link #ENABLE_STAGED_TRANSFERS} (size of each pinned staging slot).
     */
    public static final long STAGED_TRANSFER_CHUNK_SIZE = Long.parseLong(getProperty("tornado.staged.chunk.size", Integer.toString(16 * 1024 * 1024)));

    /**
     * Number of pinned staging slots cycled by {@link #ENABLE_STAGED_TRANSFERS}. Depth 2 already
     * overlaps staging with DMA; llama.cpp uses 4.
     */
    public static final int STAGED_TRANSFER_RING_DEPTH = Integer.parseInt(getProperty("tornado.staged.ring.depth", "4"));

    /**
     * Minimum transfer size in bytes for {@link #ENABLE_STAGED_TRANSFERS} to engage; smaller
     * transfers keep the direct path (staging overhead would dominate).
     */
    public static final long STAGED_TRANSFER_MIN_SIZE = Long.parseLong(getProperty("tornado.staged.min.size", Integer.toString(16 * 1024 * 1024)));

    /**
     * Threads used to fill a pinned staging slot for {@link #ENABLE_STAGED_TRANSFERS}. A single
     * thread's memcpy (~4-5 GB/s) is slower than the PCIe DMA it feeds, so the fill is split
     * across threads. Default: half the available cores, capped at 8.
     */
    public static final int STAGED_TRANSFER_FILL_THREADS = Integer.parseInt(getProperty("tornado.staged.fill.threads", Integer.toString(Math.min(8, Math.max(1, Runtime.getRuntime()
            .availableProcessors() / 2)))));

    /**
     * Option to dump TornadoVM Internal Bytecodes into a file.
     */
    public static final String DUMP_BYTECODES = getProperty("tornado.dump.bytecodes.dir", "");

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
    public static final boolean ENABLE_FMA = getBooleanValue("tornado.enable.fma", TRUE);

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
     * In the case of a TornadoVM runtime, JIT compiler or driver failure (OpenCL
     * or PTX), this option allows users to automatically execute the code
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
     * Check I/O parameters for every task within a task-graph.
     */
    public static final boolean FORCE_CHECK_PARAMETERS = getBooleanValue("tornado.check.parameters", TRUE);
    /**
     * Trace code generation.
     */
    public static final boolean TRACE_CODE_GEN = getBooleanValue("tornado.logger.codegen", FALSE);
    /**
     * Trace code generation.
     */
    public static final boolean TRACE_BUILD_LIR = getBooleanValue("tornado.logger.buildlir", FALSE);
    /**
     * It enables native math functions for the code generation.
     */
    public static final boolean ENABLE_NATIVE_FUNCTION = getBooleanValue("tornado.enable.nativeFunctions", TRUE);
    /**
     * - It enables more aggressive math optimizations.
     */
    public static final boolean MATH_OPTIMIZATIONS = getBooleanValue("tornado.enable.mathOptimizations", TRUE);
    /**
     * It enables more fast math optimizations.
     */
    public static final boolean FAST_MATH_OPTIMIZATIONS = getBooleanValue("tornado.enable.fastMathOptimizations", TRUE);
    /**
     * Opt-in: compile Metal kernels with fast/relaxed math ({@code MTLMathModeFast}),
     * the Metal analogue of OpenCL's {@code -cl-fast-relaxed-math}. Trades a small
     * amount of FP precision for speed. Default OFF to preserve strict precision.
     */
    public static final boolean METAL_FAST_MATH = getBooleanValue("tornado.metal.fastmath", FALSE);
    /**
     * Opt-in: emit a {@code [[max_total_threads_per_threadgroup(N)]]} attribute on
     * Metal kernels when the local work-group size is statically known (a worker
     * grid is attached at compile time). Lets the Metal compiler tune register
     * allocation/occupancy. Default OFF because the bound is baked into the cached
     * kernel - re-dispatching the same kernel with a larger threadgroup would fail.
     */
    public static final boolean METAL_THREADGROUP_HINT = getBooleanValue("tornado.metal.threadgroupHint", FALSE);
    /**
     * If enabled, the TornadoVM will substitute the last READ (data transfer from
     * the device to the host) using a STREAM_OUT_BLOCKING. This is FALSE by
     * default.
     */
    public static final boolean ENABLE_STREAM_OUT_BLOCKING = getBooleanValue("tornado.enable.streamOut.blocking", TRUE);

    /**
     * Option to run concurrently on multiple device in single or multi-backend
     * configuration. False by default.
     */
    public static final boolean CONCURRENT_INTERPRETERS = Boolean.parseBoolean(System.getProperty("tornado.concurrent.devices", FALSE));

    /**
     * Panama Object Header in TornadoVM.
     */
    public static final long PANAMA_OBJECT_HEADER_SIZE = TornadoNativeArray.ARRAY_HEADER;

    /**
     * Option to define the maximum number of internal events related to OpenCL/PTX events to keep alive.
     * This is application specific. In a large application, there are probably many events that need to keep alive
     * to perform the sync.
     */
    public static final int MAX_EVENTS = getIntValue("tornado.max.events", "32768");

    /**
     * Partitions the iteration space into blocks. When running on CPUs, the number of blocks is equal to the
     * number of CPU visible cores at runtime. False by default.
     */
    public static boolean USE_BLOCK_SCHEDULER = getBooleanValue("tornado.scheduler.block", FALSE);

    public static boolean TORNADO_PROFILER_LOG = false;

    public static boolean TORNADO_PROFILER = false;

    /**
     * Option to load FPGA pre-compiled binaries.
     */
    public static StringBuilder FPGA_BINARIES = System.getProperty("tornado.precompiled.binary", null) != null ? new StringBuilder(System.getProperty("tornado.precompiled.binary", null)) : null;
    private static String PROFILER_LOG = "tornado.log.profiler";
    private static String PROFILER = "tornado.profiler";

    /**
     * Option for enabling saving the profiler into a file.
     */
    public static boolean PROFILER_LOGS_ACCUMULATE() {
        return TornadoOptions.TORNADO_PROFILER_LOG || getBooleanValue(PROFILER_LOG, FALSE);
    }

    /**
     * Option for logging the TornadoVM Bytecodes when printing in console is enabled or dumping them into a file.
     */
    public static boolean LOG_BYTECODES() {
        return TornadoOptions.PRINT_BYTECODES || !TornadoOptions.DUMP_BYTECODES.isBlank();
    }

    /**
     * Option to reuse device buffers every time a task-graph is executed. True by
     * default.
     */
    public static boolean isReusedBuffersEnabled() {
        return getBooleanValue("tornado.reuse.device.buffers", TRUE);
    }

    /**
     * Option to deallocate after the execution plan finishes. It frees all
     * resources consumed by the execution plan, which can involved multiple
     * task graphs.
     */
    public static boolean isDeallocateBufferEnabled() {
        return getBooleanValue("tornado.deallocate.buffers", TRUE);
    }

    /**
     * Option to enable profiler. It can be disabled at any point during runtime.
     *
     * @return boolean.
     */
    public static boolean isProfilerEnabled() {
        return TORNADO_PROFILER || getBooleanValue(PROFILER, FALSE);
    }

    public static boolean isUpsReaderEnabled() {
        return UPS_IP_ADDRESS != null;
    }

    /**
     * Set Loop unrolling factor. Default is set to 4.
     */
    public static final int UNROLL_FACTOR = Integer.parseInt(getProperty("tornado.unroll.factor", "4"));

    /**
     * Enable basic debug information. Disabled by default.
     */
    public static final boolean DEBUG = getBooleanValue("tornado.debug", FALSE);

    /**
     * Enable Full Debug Mode. Disabled by default.
     */
    public static final boolean FULL_DEBUG = getBooleanValue("tornado.fullDebug", FALSE);

    /**
     * Enable debugging of the kernel parameters. Disable by default.
     */
    public static final boolean DEBUG_KERNEL_ARGS = getBooleanValue("tornado.debug.kernelargs", FALSE);

    /**
     * Use flush in OpenCL to sync all pending commands from the command queue. Disabled by default.
     */
    public static final boolean USE_SYNC_FLUSH = getBooleanValue("tornado.opencl.syncflush", FALSE);

    /**
     * Run VM Flush when TornadoVM finishes the execution of the TornadoVM interpreter.
     */
    public static final boolean USE_VM_FLUSH = getBooleanValue("tornado.vmflush", TRUE);

    /**
     * Define the maximum number of events to wait for in the OpenCL Event Pool. Default is 64.
     */
    public static final int MAX_WAIT_EVENTS = getIntValue("tornado.eventpool.maxwaitevents", "64");

    /**
     * Define the event windows for the Internal Event Pool. Default is 1024.
     */
    public static final int EVENT_WINDOW = getIntValue("tornado.eventpool.size", "1024");

    /**
     * Enable BIFS Math operations. Disabled by default.
     */
    public static final boolean TORNADO_ENABLE_BIFS = getBooleanValue("tornado.bifs.enable", FALSE);

    /**
     * Enable VM Dependency Path. Disabled by default. This option is only for testing.
     */
    public static boolean VM_USE_DEPS = getBooleanValue("tornado.vm.deps", FALSE);

    /**
     * Enable OpenCL Profiling. Enabled by default.
     */
    public static final boolean ENABLE_OPENCL_PROFILING = getBooleanValue("tornado.opencl.profiling.enable", TRUE);

    /**
     * Enable Metal Profiling. Enabled by default.
     */
    public static final boolean ENABLE_METAL_PROFILING = getBooleanValue("tornado.metal.profiling.enable", TRUE);

    /**
     * Enable to dump the generated methods to a file for debugging purposes. Disabled by default.
     */
    public static final boolean DUMP_COMPILED_METHODS = getBooleanValue("tornado.compiled.dump", FALSE);

    /**
     * Enable out-of-order execution. False by default.
     */
    public static final boolean ENABLE_OOO_EXECUTION = getBooleanValue("tornado.ooo-execution.enable", FALSE);

    public static final String UPS_IP_ADDRESS = getProperty("tornado.ups.ip", null);

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

    private static float getFloatValue(String property, String defaultValue) {
        return Float.parseFloat(System.getProperty(property, defaultValue));
    }

    private static boolean isFPGAEmulation() {
        String contextEmulatorIntelFPGA = System.getenv("CL_CONTEXT_EMULATOR_DEVICE_INTELFPGA");
        String contextEmulatorXilinxFPGA = System.getenv("XCL_EMULATION_MODE");
        return (contextEmulatorIntelFPGA != null && (contextEmulatorIntelFPGA.equals("1"))) || (contextEmulatorXilinxFPGA != null && (contextEmulatorXilinxFPGA.equals("sw_emu")));
    }

    /**
     * Flag to signal to clean up the atomics area (as in accelerator's global memory) when the Execution Plan
     * resource is closed. This is False by default, since this area is global for all kernels. In near future,
     * we will change this to use a unique area per execution plan, and have the option to turn on and off
     * this flag as needed.
     *
     * @return boolean
     */
    public static boolean cleanUpAtomicsSpace() {
        return getBooleanValue("tornado.clean.atomics.space", FALSE);
    }

    public static boolean coopsUsed() {
        List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
        boolean isUncompressed = jvmArgs.contains("-XX:-UseCompressedOops") ||
                jvmArgs.contains("-XX:-UseCompressedClassPointers");

        return isUncompressed ? false : true;
    }
}
