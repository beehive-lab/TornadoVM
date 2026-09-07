/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
 */
package uk.ac.manchester.tornado.drivers.cuda.ffm;

import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
import java.io.File;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compiles a generated CUDA C kernel to a loadable module image with NVRTC, and loads it into a
 * {@code CUmodule}.
 */
public final class CUDACompiler {

    private static final String LOG_PREFIX = "[TornadoVM-CUDA] ";

    /** Size of the buffer the driver's JIT is given to report why it rejected an image. */
    private static final int JIT_LOG_BYTES = 8192;

    /**
     * In-process cubin cache. A task graph emits the <em>same</em> kernel source once per
     * transformer layer, so identical CUDA C is otherwise NVRTC-compiled dozens of times during
     * warm-up. The key embeds the architecture and the full source rather than a hash, so there is
     * no collision risk.
     */
    private static final Map<String, byte[]> IMAGE_CACHE = new ConcurrentHashMap<>();

    /**
     * How this NVRTC resolves {@code #include <cuda_fp16.h>} and friends. Whether it can do so on
     * its own, needs an explicit toolkit include path, or cannot do it at all is a fixed property
     * of the (NVRTC, toolkit) pair on this machine, not of any individual kernel. It is discovered
     * once, by the first kernel that includes a header, and reused, so later kernels compile in a
     * single attempt instead of repeating the failed built-in probe.
     */
    private enum HeaderMode {
        UNKNOWN, NEEDS_INCLUDE_PATHS, UNRESOLVABLE
    }

    private static final Object HEADER_MODE_LOCK = new Object();
    private static HeaderMode headerMode = HeaderMode.UNKNOWN;
    private static List<String> headerIncludeOptions = List.of();

    /** The PTX-JIT fallback works, so its warning is emitted once per process, not once per kernel. */
    private static volatile boolean archFallbackWarned;

    private CUDACompiler() {
    }

    /**
     * Compiles {@code program}'s source for {@code device} and loads the result as a module,
     * recording the outcome in the program's build status and log. Nothing is thrown: the caller
     * reads the status the way the OpenCL two-step create-then-build does.
     *
     * @param userOptions
     *     whitespace-separated NVRTC options from {@code tornado.cuda.compiler.flags} or
     *     {@code withCompilerFlags(CUDA, ...)}, appended last so they override the defaults.
     */
    public static void build(CUDAHandles.Program program, int device, String userOptions) {
        List<String> options = splitOptions(userOptions);

        // Hoisted so the module-load failure branch can explain an NVRTC/GPU-arch mismatch. The
        // defaults suit the pre-built-image path, which loads a cubin directly with no PTX JIT.
        int gpuArch = 0;          // device compute capability, e.g. cc 12.0 -> 120
        int maxSupportedArch = 0; // highest arch this NVRTC can emit
        int fallbackArch = 0;     // highest supported arch <= the GPU's (0 if none)
        boolean useCubin = true;
        boolean archKnown = false; // arch values below describe this compile (false for a supplied image)

        if (program.binary.length > 0) {
            // Already have a module image (the createProgramWithBinary path) - just load it below.
            program.buildStatus = CUDAHandles.Program.BUILD_SUCCESS;
        } else {
            int major = deviceAttribute(device, CUDADriverAPI.CU_DEVICE_ATTRIBUTE_COMPUTE_CAPABILITY_MAJOR);
            int minor = deviceAttribute(device, CUDADriverAPI.CU_DEVICE_ATTRIBUTE_COMPUTE_CAPABILITY_MINOR);
            gpuArch = major * 10 + minor;

            // The NVRTC target depends on what *this* toolkit actually supports, which decides
            // between two compilation modes:
            //
            //   * cubin (sm_XX): finished SASS for the device's real arch, loaded with no driver
            //     PTX JIT. Requires the toolkit to know the GPU arch. Avoids the driver rejecting
            //     PTX whose ISA is newer than its JIT supports.
            //
            //   * PTX (compute_XX): when the toolkit is OLDER than the GPU (a CUDA 12.0 NVRTC on a
            //     Blackwell sm_120 device, say), it cannot emit sm_120 at all. Fall back to PTX for
            //     the newest virtual arch it does know and let the newer driver JIT it -- PTX
            //     forward compatibility.
            //
            // This keeps the backend working across the whole (toolkit, driver, GPU) matrix instead
            // of assuming toolkit >= GPU.
            int[] supportedArchs = supportedArchs();
            boolean queriedArchs = supportedArchs != null;
            boolean gpuArchSupported = false;
            int lowestSupportedArch = 0;
            if (queriedArchs) {
                for (int arch : supportedArchs) {
                    if (arch == gpuArch) {
                        gpuArchSupported = true;
                    }
                    if (arch <= gpuArch && arch > fallbackArch) {
                        fallbackArch = arch;
                    }
                    if (lowestSupportedArch == 0 || arch < lowestSupportedArch) {
                        lowestSupportedArch = arch;
                    }
                    if (arch > maxSupportedArch) {
                        maxSupportedArch = arch;
                    }
                }
            }

            // The toolkit is newer than the GPU and has dropped support for its family entirely
            // (CUDA 13.x removed sm_5x/6x/7x, so a Pascal sm_61 device has no arch string this code
            // could construct that NVRTC would accept). Bail here naming the actual mismatch rather
            // than forwarding a rejected -arch and surfacing NVRTC's opaque "invalid value for
            // --gpu-architecture". Only when the arch query succeeded: if it failed, fall through
            // and try the GPU's own arch as a last-resort compute_XX.
            if (!gpuArchSupported && queriedArchs && fallbackArch == 0) {
                program.buildStatus = CUDAHandles.Program.BUILD_ERROR;
                program.log = "CUDA Toolkit does not support the GPU's compute capability (sm_" + gpuArch + ", cc " + major + "." + minor
                        + "). The lowest architecture supported by this NVRTC is sm_" + lowestSupportedArch
                        + ". This typically happens when CUDA 13+ is used with an older GPU: CUDA 13 removed Pascal/Volta and earlier (sm_50..sm_72). "
                        + "Install a CUDA Toolkit old enough to still target sm_" + gpuArch + " (CUDA 12.8 is the last release supporting Pascal), or use a newer GPU (Turing sm_75 or later).";
                System.out.println(LOG_PREFIX + program.log);
                return;
            }

            String arch;
            if (gpuArchSupported) {
                arch = "--gpu-architecture=sm_" + gpuArch;
                useCubin = true;
            } else {
                // The toolkit does not know the GPU's real arch: emit forward-compatible PTX for the
                // best virtual arch it does know (or the GPU's own if the query failed) and let the
                // driver JIT it.
                arch = "--gpu-architecture=compute_" + (fallbackArch > 0 ? fallbackArch : gpuArch);
                useCubin = false;
            }
            archKnown = true;

            // Identical source for the same architecture yields an identical image, so reuse a
            // previously compiled one rather than invoking NVRTC again.
            String cacheKey = arch + "\n" + program.source;
            byte[] cached = IMAGE_CACHE.get(cacheKey);
            if (cached != null) {
                program.binary = cached;
                program.buildStatus = CUDAHandles.Program.BUILD_SUCCESS;
            } else if (!compile(program, arch, options, useCubin)) {
                return;
            } else {
                IMAGE_CACHE.putIfAbsent(cacheKey, program.binary);
            }
        }

        loadModule(program, gpuArch, maxSupportedArch, fallbackArch, useCubin, archKnown);
    }

    /**
     * Runs NVRTC over the program's source, retrying once with explicit toolkit include paths when
     * the failure is an unresolved header. Returns whether an image was produced; on failure the
     * program's status and log carry the reason.
     */
    private static boolean compile(CUDAHandles.Program program, String arch, List<String> userOptions, boolean useCubin) {
        // Only kernels that actually include a header consult the header-resolution memo.
        boolean needsHeader = program.source.contains("#include");

        HeaderMode mode;
        List<String> includeOptions = new ArrayList<>();
        synchronized (HEADER_MODE_LOCK) {
            mode = headerMode;
            if (needsHeader && mode == HeaderMode.NEEDS_INCLUDE_PATHS) {
                includeOptions.addAll(headerIncludeOptions);
            }
        }

        // A previous probe already proved the header cannot be resolved on this host: skip the
        // doomed compile and report the explanatory failure.
        if (needsHeader && mode == HeaderMode.UNRESOLVABLE) {
            program.buildStatus = CUDAHandles.Program.BUILD_ERROR;
            program.log = headerUnresolvableMessage();
            System.out.println(LOG_PREFIX + program.log);
            return false;
        }

        // Header resolution for #include <cuda_fp16.h> varies by toolkit:
        //
        //   * Some NVRTC builds (observed on 12.x) resolve the standard CUDA headers via RTC
        //     built-ins with an empty include list. For those, adding the on-disk include directory
        //     is actively harmful: their on-disk cuda_fp16.hpp guards <nv/target> with
        //     !defined(__CUDACC_RTC__), so NV_IF_ELSE_TARGET / NV_IS_DEVICE are undefined under
        //     NVRTC and produce 100+ errors.
        //
        //   * Others (11.x, 13.x) have no built-in cuda_fp16.h and fail with "could not open source
        //     file" unless the toolkit include directory is supplied; their on-disk headers are
        //     RTC-safe.
        //
        // A version gate cannot capture this reliably across the toolkit matrix, so the first such
        // kernel probes it -- built-ins first, then the toolkit include directories -- and memoizes
        // the outcome.
        NvrtcResult attempt = compileOnce(program.source, arch, includeOptions, userOptions, useCubin);
        program.log = attempt.log;

        // First kernel whose built-in header resolution failed: probe the toolkit include
        // directories once, retry, then memoize what worked so no other kernel repeats the failure.
        if (needsHeader && !attempt.success && mode == HeaderMode.UNKNOWN && attempt.log.contains("could not open source file")) {
            for (String directory : headerIncludeDirectories()) {
                includeOptions.add("--include-path=" + directory);
            }
            if (!includeOptions.isEmpty()) {
                attempt = compileOnce(program.source, arch, includeOptions, userOptions, useCubin);
                program.log = attempt.log;
            }
            synchronized (HEADER_MODE_LOCK) {
                if (attempt.success && !includeOptions.isEmpty()) {
                    headerMode = HeaderMode.NEEDS_INCLUDE_PATHS;
                    headerIncludeOptions = List.copyOf(includeOptions);
                } else if (attempt.log.contains("could not open source file")) {
                    // Neither the built-ins nor any toolkit include directory has the header.
                    headerMode = HeaderMode.UNRESOLVABLE;
                }
            }
        }

        if (!attempt.success) {
            program.buildStatus = CUDAHandles.Program.BUILD_ERROR;
            // An unresolved cuda_fp16.h reports only "could not open source file", which says
            // neither why nor how to fix it; prepend the root cause and the remedy.
            if (program.log.contains("could not open source file")) {
                program.log = headerUnresolvableMessage() + "\n\nNVRTC log:\n" + program.log;
            }
            System.out.println(LOG_PREFIX + "NVRTC compilation failed:\n" + program.log);
            return false;
        }

        program.binary = attempt.image;
        program.buildStatus = CUDAHandles.Program.BUILD_SUCCESS;
        return true;
    }

    private record NvrtcResult(boolean success, byte[] image, String log) {
    }

    /**
     * One NVRTC create-compile-fetch-destroy cycle. A failed nvrtcProgram cannot be recompiled, so
     * each attempt builds its own.
     */
    private static NvrtcResult compileOnce(String source, String arch, List<String> includeOptions, List<String> userOptions, boolean useCubin) {
        if (!NVRTCAPI.isAvailable()) {
            return new NvrtcResult(false, null, "NVRTC is not available: no CUDA toolkit was found on this host.");
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment programOut = FFMSupport.allocatePointer(arena);
            int status = NVRTCAPI.nvrtcCreateProgram(programOut, FFMSupport.allocateCString(arena, source), FFMSupport.allocateCString(arena, "tornado_kernel.cu"), 0, MemorySegment.NULL,
                    MemorySegment.NULL);
            if (status != NVRTCAPI.NVRTC_SUCCESS) {
                return new NvrtcResult(false, null, "nvrtcCreateProgram failed: " + NVRTCAPI.nvrtcGetErrorString(status));
            }
            long program = programOut.get(FFMSupport.C_POINTER, 0).address();
            try {
                List<String> all = new ArrayList<>();
                all.add(arch);
                all.addAll(includeOptions);
                // User options last so they can override the defaults above.
                all.addAll(userOptions);

                MemorySegment options = FFMSupport.allocateArray(arena, FFMSupport.C_POINTER, all.size());
                for (int i = 0; i < all.size(); i++) {
                    options.set(FFMSupport.C_POINTER, i * FFMSupport.C_POINTER.byteSize(), FFMSupport.allocateCString(arena, all.get(i)));
                }
                status = NVRTCAPI.nvrtcCompileProgram(program, all.size(), options);
                String log = programLog(arena, program);
                if (status != NVRTCAPI.NVRTC_SUCCESS) {
                    // Deliberately not logged here: on toolkits whose NVRTC has no built-in
                    // cuda_fp16.h this first attempt is *expected* to fail and then succeed on the
                    // include-path retry. A failure that survives the retry is reported by the
                    // caller, with the complete log.
                    return new NvrtcResult(false, null, log);
                }
                // A compile that succeeded but yielded nothing to load is a failure here, not a
                // success with an empty image: reporting it as success sets BUILD_SUCCESS and
                // defers the problem to cuModuleLoadDataEx, which sees a zero-length image and
                // reports only CUDA_ERROR_INVALID_PTX - naming neither the retrieval that failed
                // nor the kernel it belonged to. Retrieval fails when the requested image format
                // does not match what was compiled (asking for a cubin from a compute_XX virtual
                // target, say), so say which format was asked for.
                byte[] compiled = image(arena, program, useCubin);
                if (compiled.length == 0) {
                    String format = useCubin ? "cubin" : "PTX";
                    return new NvrtcResult(false, null, "NVRTC reported success but produced no " + format + " image for this kernel." + (log.isEmpty() ? "" : "\n\nNVRTC log:\n" + log));
                }
                return new NvrtcResult(true, compiled, log);
            } finally {
                MemorySegment handle = FFMSupport.allocatePointer(arena);
                handle.set(FFMSupport.C_POINTER, 0, MemorySegment.ofAddress(program));
                NVRTCAPI.nvrtcDestroyProgram(handle);
            }
        }
    }

    /**
     * Retrieves the compiled image. {@code cuModuleLoadDataEx} detects the format from its header,
     * so the load path handles a cubin and raw PTX identically: a cubin (sm_XX) loads directly,
     * PTX (compute_XX) is JIT'd by the driver.
     */
    private static byte[] image(Arena arena, long program, boolean useCubin) {
        MemorySegment size = FFMSupport.allocateLong(arena);
        int status = useCubin ? NVRTCAPI.nvrtcGetCUBINSize(program, size) : NVRTCAPI.nvrtcGetPTXSize(program, size);
        if (status != NVRTCAPI.NVRTC_SUCCESS) {
            return new byte[0];
        }
        long byteCount = size.get(FFMSupport.C_LONG, 0);
        if (byteCount <= 0) {
            return new byte[0];
        }
        MemorySegment buffer = arena.allocate(byteCount, 1);
        status = useCubin ? NVRTCAPI.nvrtcGetCUBIN(program, buffer) : NVRTCAPI.nvrtcGetPTX(program, buffer);
        if (status != NVRTCAPI.NVRTC_SUCCESS) {
            return new byte[0];
        }
        byte[] image = new byte[(int) byteCount];
        MemorySegment.copy(buffer, FFMSupport.C_CHAR, 0, image, 0, image.length);
        return image;
    }

    /** The NVRTC build log (errors and warnings) for a program, empty when it produced none. */
    private static String programLog(Arena arena, long program) {
        MemorySegment size = FFMSupport.allocateLong(arena);
        if (NVRTCAPI.nvrtcGetProgramLogSize(program, size) != NVRTCAPI.NVRTC_SUCCESS) {
            return "";
        }
        long byteCount = size.get(FFMSupport.C_LONG, 0);
        if (byteCount <= 1) {
            return "";
        }
        MemorySegment buffer = arena.allocate(byteCount, 1);
        if (NVRTCAPI.nvrtcGetProgramLog(program, buffer) != NVRTCAPI.NVRTC_SUCCESS) {
            return "";
        }
        return FFMSupport.readCString(buffer, byteCount);
    }

    /** Loads the module image via the driver API, capturing the JIT's diagnostics on failure. */
    private static void loadModule(CUDAHandles.Program program, int gpuArch, int maxSupportedArch, int fallbackArch, boolean useCubin, boolean archKnown) {
        if (program.context != 0) {
            CUDADriverAPI.cuCtxSetCurrent(program.context);
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment image = arena.allocate(Math.max(program.binary.length, 1), 1);
            MemorySegment.copy(program.binary, 0, image, FFMSupport.C_CHAR, 0, program.binary.length);

            // Capture the driver's JIT error log so that an invalid image reports a diagnostic
            // rather than an opaque CUDA_ERROR_INVALID_PTX / unsupported-version error.
            MemorySegment jitLog = arena.allocate(JIT_LOG_BYTES, 1);
            jitLog.set(FFMSupport.C_CHAR, 0, (byte) 0);
            MemorySegment jitOptions = FFMSupport.allocateArray(arena, FFMSupport.C_INT, 2);
            jitOptions.set(FFMSupport.C_INT, 0, CUDADriverAPI.CU_JIT_ERROR_LOG_BUFFER);
            jitOptions.set(FFMSupport.C_INT, 4, CUDADriverAPI.CU_JIT_ERROR_LOG_BUFFER_SIZE_BYTES);
            MemorySegment jitValues = FFMSupport.allocateArray(arena, FFMSupport.C_POINTER, 2);
            jitValues.set(FFMSupport.C_POINTER, 0, jitLog);
            jitValues.set(FFMSupport.C_POINTER, FFMSupport.C_POINTER.byteSize(), MemorySegment.ofAddress(JIT_LOG_BYTES));

            MemorySegment moduleOut = FFMSupport.allocatePointer(arena);
            int result = CUDADriverAPI.cuModuleLoadDataEx(moduleOut, image, 2, jitOptions, jitValues);
            if (result == CUDADriverAPI.CUDA_SUCCESS) {
                program.module = moduleOut.get(FFMSupport.C_POINTER, 0).address();
                program.moduleLoaded = true;
                warnAboutPtxFallbackOnce(gpuArch, maxSupportedArch, fallbackArch, useCubin, archKnown);
                return;
            }

            program.buildStatus = CUDAHandles.Program.BUILD_ERROR;
            program.moduleLoaded = false;
            String driverMessage = CUDADriverAPI.errorString(result);
            StringBuilder log = new StringBuilder("cuModuleLoadDataEx failed: ").append(driverMessage == null ? "unknown" : driverMessage);
            String jitText = FFMSupport.readCString(jitLog, JIT_LOG_BYTES);
            if (!jitText.isEmpty()) {
                log.append("\nJIT log:\n").append(jitText);
            }
            program.log = explainLoadFailure(log.toString(), jitText, gpuArch, fallbackArch, useCubin, archKnown);
            System.out.println(LOG_PREFIX + program.log);
        }
    }

    /**
     * Names the component that is actually stale when the driver rejects a freshly compiled image.
     * The raw error is only INVALID_PTX or an unsupported-version code, which sends people chasing
     * the wrong upgrade.
     */
    private static String explainLoadFailure(String log, String jitLog, int gpuArch, int fallbackArch, boolean useCubin, boolean archKnown) {
        if (!archKnown) {
            return log;
        }
        if (useCubin) {
            // Native cubin (the toolkit knew the GPU arch) rejected at load: the driver is older
            // than the toolkit that produced it.
            return "Driver rejected the native sm_" + gpuArch + " cubin: the GPU driver is older than the CUDA toolkit that produced it. "
                    + "Update the GPU driver, or use a CUDA toolkit matching the installed driver.\n\n" + log;
        }
        // PTX fallback (toolkit older than the GPU). Two opposite causes land here and the JIT log
        // is what separates them:
        //
        //   * "requires PTX ISA .version X or later" - the TOOLKIT is behind. NVRTC stamped an older
        //     .version onto PTX using a feature that needs a newer one (FP8 mma.sync needs ISA 8.4 /
        //     CUDA 12.4). Updating the driver does nothing; the PTX itself is the problem.
        //   * anything else - the DRIVER's ptxas is older than this toolkit's PTX ISA.
        int target = fallbackArch > 0 ? fallbackArch : gpuArch;
        if (jitLog.contains("requires PTX ISA")) {
            return "Driver could not JIT compute_" + target + " PTX for this GPU (sm_" + gpuArch
                    + "): the CUDA toolkit is too old to encode an instruction this kernel uses (see the PTX ISA version named below). "
                    + "Install a newer CUDA toolkit; updating the GPU driver will not help.\n\n" + log;
        }
        return "Driver could not JIT compute_" + target + " PTX for this GPU (sm_" + gpuArch + "): the GPU driver is too old for this toolkit's PTX ISA. "
                + "Update the GPU driver, or use a CUDA toolkit whose NVRTC natively supports sm_" + gpuArch + " (native cubin avoids PTX JIT entirely).\n\n" + log;
    }

    /**
     * Working, but suboptimal: the toolkit did not know the GPU's arch, so compute_&lt;fallback&gt;
     * PTX was loaded and JIT'd by the newer driver. It runs, but every load pays a driver JIT and
     * codegen is capped at that virtual ISA -- no sm_&lt;gpuArch&gt;-native instructions such as
     * newer tensor-core MMA shapes. Warned once so the degraded path is visible; native codegen
     * needs a newer toolkit, not a driver change, since the driver is already ahead.
     */
    private static void warnAboutPtxFallbackOnce(int gpuArch, int maxSupportedArch, int fallbackArch, boolean useCubin, boolean archKnown) {
        if (!archKnown || useCubin || archFallbackWarned) {
            return;
        }
        synchronized (CUDACompiler.class) {
            if (archFallbackWarned) {
                return;
            }
            archFallbackWarned = true;
        }
        int target = fallbackArch > 0 ? fallbackArch : gpuArch;
        System.out.println(LOG_PREFIX + "WARNING: CUDA toolkit (NVRTC max sm_" + maxSupportedArch + ") predates this GPU (sm_" + gpuArch + "). Using compute_" + target
                + " PTX JIT'd by the driver - functional but not optimal (extra load-time JIT; codegen limited to the compute_" + target + " ISA). Upgrade the CUDA toolkit to one supporting sm_"
                + gpuArch + " for native codegen.");
    }

    /**
     * Whether this NVRTC can compile a kernel that includes {@code headerName}, using the same
     * resolution order as real kernels: built-in headers first, then the toolkit include
     * directories. Codegen consults this to decide whether a native conversion that needs a header
     * can be emitted or the Java software codec must be inlined instead, so a missing or
     * mismatched header costs performance, not correctness.
     */
    public static boolean canCompileHeader(String headerName) {
        if (!NVRTCAPI.isAvailable()) {
            return false;
        }
        String source = "#include <" + headerName + ">\nextern \"C\" __global__ void tornado_header_probe() {}\n";
        if (probeCompile(source, List.of())) {
            return true;
        }
        List<String> includeOptions = new ArrayList<>();
        for (String directory : headerIncludeDirectories()) {
            includeOptions.add("--include-path=" + directory);
        }
        return !includeOptions.isEmpty() && probeCompile(source, includeOptions);
    }

    private static boolean probeCompile(String source, List<String> includeOptions) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment programOut = FFMSupport.allocatePointer(arena);
            if (NVRTCAPI.nvrtcCreateProgram(programOut, FFMSupport.allocateCString(arena, source), FFMSupport.allocateCString(arena, "tornado_header_probe.cu"), 0, MemorySegment.NULL,
                    MemorySegment.NULL) != NVRTCAPI.NVRTC_SUCCESS) {
                return false;
            }
            long program = programOut.get(FFMSupport.C_POINTER, 0).address();
            try {
                MemorySegment options = MemorySegment.NULL;
                if (!includeOptions.isEmpty()) {
                    options = FFMSupport.allocateArray(arena, FFMSupport.C_POINTER, includeOptions.size());
                    for (int i = 0; i < includeOptions.size(); i++) {
                        options.set(FFMSupport.C_POINTER, i * FFMSupport.C_POINTER.byteSize(), FFMSupport.allocateCString(arena, includeOptions.get(i)));
                    }
                }
                return NVRTCAPI.nvrtcCompileProgram(program, includeOptions.size(), options) == NVRTCAPI.NVRTC_SUCCESS;
            } finally {
                MemorySegment handle = FFMSupport.allocatePointer(arena);
                handle.set(FFMSupport.C_POINTER, 0, MemorySegment.ofAddress(program));
                NVRTCAPI.nvrtcDestroyProgram(handle);
            }
        }
    }

    /**
     * NVRTC version as {@code major * 1000 + minor} (CUDA 12.4 gives 12004), or {@code -1} when the
     * query fails.
     */
    public static int version() {
        if (!NVRTCAPI.isAvailable()) {
            return -1;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment major = FFMSupport.allocateInt(arena);
            MemorySegment minor = FFMSupport.allocateInt(arena);
            if (NVRTCAPI.nvrtcVersion(major, minor) != NVRTCAPI.NVRTC_SUCCESS) {
                return -1;
            }
            return major.get(FFMSupport.C_INT, 0) * 1000 + minor.get(FFMSupport.C_INT, 0);
        }
    }

    /**
     * The architectures this NVRTC can emit, or {@code null} when the query is unavailable (before
     * CUDA 11.2) or fails -- which the caller treats as "unknown" rather than "none".
     */
    private static int[] supportedArchs() {
        if (!NVRTCAPI.isAvailable() || !NVRTCAPI.hasSupportedArchsQuery()) {
            return null;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment count = FFMSupport.allocateInt(arena);
            if (NVRTCAPI.nvrtcGetNumSupportedArchs(count) != NVRTCAPI.NVRTC_SUCCESS) {
                return null;
            }
            int numArchs = count.get(FFMSupport.C_INT, 0);
            if (numArchs <= 0) {
                return null;
            }
            MemorySegment archs = FFMSupport.allocateArray(arena, FFMSupport.C_INT, numArchs);
            if (NVRTCAPI.nvrtcGetSupportedArchs(archs) != NVRTCAPI.NVRTC_SUCCESS) {
                return null;
            }
            int[] supported = new int[numArchs];
            MemorySegment.copy(archs, FFMSupport.C_INT, 0, supported, 0, numArchs);
            return supported;
        }
    }

    private static int deviceAttribute(int device, int attribute) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = FFMSupport.allocateInt(arena);
            if (CUDADriverAPI.cuDeviceGetAttribute(value, attribute, device) != CUDADriverAPI.CUDA_SUCCESS) {
                return 0;
            }
            return value.get(FFMSupport.C_INT, 0);
        }
    }

    /**
     * Directories that hold the toolkit's {@code cuda_fp16.h}, so an explicit NVRTC include path
     * can be supplied on toolkits whose NVRTC cannot resolve the standard CUDA headers itself.
     * Candidate roots are probed in priority order and only those that actually hold the header are
     * returned.
     */
    private static List<String> headerIncludeDirectories() {
        List<String> found = new ArrayList<>();
        for (String root : NVRTCAPI.toolkitRoots()) {
            String directory = root + File.separator + "include";
            if (new File(directory, "cuda_fp16.h").isFile() && !found.contains(directory)) {
                found.add(directory);
            }
        }
        return found;
    }

    /**
     * Root cause and remedy for an unresolved {@code <cuda_fp16.h>}. The raw NVRTC diagnostic is
     * only "could not open source file", which says neither why nor how to fix it.
     */
    private static String headerUnresolvableMessage() {
        return "NVRTC cannot resolve <cuda_fp16.h>: this NVRTC provides no built-in CUDA headers and no CUDA toolkit include directory containing cuda_fp16.h was found "
                + "(checked $CUDA_PATH/$CUDA_HOME/$CUDA_ROOT/include, /usr/local/cuda*/include, /usr/include). "
                + "Install a CUDA toolkit or set CUDA_PATH to one so the CUDA backend can compile FP16 kernels.";
    }

    /** Splits a whitespace-separated NVRTC option string into individual arguments. */
    private static List<String> splitOptions(String options) {
        List<String> result = new ArrayList<>();
        if (options == null) {
            return result;
        }
        for (String token : options.trim().split("\\s+")) {
            if (!token.isEmpty()) {
                result.add(token);
            }
        }
        return result;
    }
}
