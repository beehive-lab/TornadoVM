/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.hip.ffm;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;

/**
 * Compiles a generated HIP C++ kernel to an AMD GPU code object with hipRTC, for the device's own
 * architecture, and loads it into a {@code hipModule_t}.
 */
public final class HIPCompiler {

    private static final String LOG_PREFIX = "[TornadoVM-HIP] ";

    /**
     * In-process code-object cache. A task graph emits the <em>same</em> kernel source once per
     * transformer layer, so identical HIP C++ is otherwise compiled dozens of times during warm-up. The
     * key embeds the architecture, the options and the full source rather than a hash, so there is no
     * collision risk.
     */
    private static final Map<String, byte[]> IMAGE_CACHE = new ConcurrentHashMap<>();

    /** Each device's architecture as hipRTC names it, queried once. */
    private static final Map<Integer, String> ARCHITECTURES = new ConcurrentHashMap<>();

    private HIPCompiler() {
    }

    /** The architecture of {@code device} without feature suffixes ({@code gfx942}); empty when unknown. */
    public static String architecture(int device) {
        return ARCHITECTURES.computeIfAbsent(device, HIPDriverAPI::gcnArchName);
    }

    /**
     * Compiles {@code program}'s source for {@code device} and loads the result as a module, recording
     * the outcome in the program's build status and log. Nothing is thrown: the caller reads the status
     * the way the OpenCL two-step create-then-build does.
     *
     * @param userOptions
     *     whitespace-separated hipRTC options from {@code tornado.hip.compiler.flags} or
     *     {@code withCompilerFlags(HIP, ...)}, appended last so they override the defaults.
     */
    public static void build(HIPHandles.Program program, int device, String userOptions) {
        if (program.binary.length > 0) {
            // Already have a code object (the createProgramWithBinary path) - just load it below.
            program.buildStatus = HIPHandles.Program.BUILD_SUCCESS;
        } else {
            String arch = architecture(device);
            List<String> options = new ArrayList<>();
            if (!arch.isEmpty()) {
                options.add("--offload-arch=" + arch);
            }
            options.addAll(splitOptions(userOptions));
            String cacheKey = String.join(" ", options) + "\n" + program.source;
            byte[] cached = IMAGE_CACHE.get(cacheKey);
            if (cached != null) {
                program.binary = cached;
                program.buildStatus = HIPHandles.Program.BUILD_SUCCESS;
            } else if (!compile(program, options)) {
                return;
            } else {
                IMAGE_CACHE.putIfAbsent(cacheKey, program.binary);
            }
        }
        loadModule(program);
    }

    /** Runs hipRTC over the program's source. Returns whether a code object was produced; on failure the program's status and log carry the reason. */
    private static boolean compile(HIPHandles.Program program, List<String> options) {
        if (!HIPRTCAPI.isAvailable()) {
            program.buildStatus = HIPHandles.Program.BUILD_ERROR;
            program.log = "hipRTC is not available: no ROCm installation with hipRTC was found (set ROCM_PATH or HIP_PATH to the ROCm root).";
            System.out.println(LOG_PREFIX + program.log);
            return false;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment programOut = FFMSupport.allocatePointer(arena);
            int status = HIPRTCAPI.hiprtcCreateProgram(programOut, FFMSupport.allocateCString(arena, program.source), FFMSupport.allocateCString(arena, "tornado_kernel.hip"), 0,
                    MemorySegment.NULL, MemorySegment.NULL);
            if (status != HIPRTCAPI.HIPRTC_SUCCESS) {
                program.buildStatus = HIPHandles.Program.BUILD_ERROR;
                program.log = "hiprtcCreateProgram failed: " + HIPRTCAPI.hiprtcGetErrorString(status);
                System.out.println(LOG_PREFIX + program.log);
                return false;
            }
            long handle = programOut.get(FFMSupport.C_POINTER, 0).address();
            try {
                MemorySegment optionArray = FFMSupport.allocateArray(arena, FFMSupport.C_POINTER, Math.max(1, options.size()));
                for (int i = 0; i < options.size(); i++) {
                    optionArray.set(FFMSupport.C_POINTER, i * FFMSupport.C_POINTER.byteSize(), FFMSupport.allocateCString(arena, options.get(i)));
                }
                status = HIPRTCAPI.hiprtcCompileProgram(handle, options.size(), optionArray);
                program.log = programLog(arena, handle);
                if (status != HIPRTCAPI.HIPRTC_SUCCESS) {
                    program.buildStatus = HIPHandles.Program.BUILD_ERROR;
                    System.out.println(LOG_PREFIX + "hipRTC compilation failed (" + HIPRTCAPI.hiprtcGetErrorString(status) + "):\n" + program.log);
                    return false;
                }
                byte[] image = code(arena, handle);
                if (image.length == 0) {
                    program.buildStatus = HIPHandles.Program.BUILD_ERROR;
                    program.log = "hipRTC reported success but produced no code object for this kernel." + (program.log.isEmpty() ? "" : "\n\nhipRTC log:\n" + program.log);
                    System.out.println(LOG_PREFIX + program.log);
                    return false;
                }
                program.binary = image;
                program.buildStatus = HIPHandles.Program.BUILD_SUCCESS;
                return true;
            } finally {
                MemorySegment holder = FFMSupport.allocatePointer(arena);
                holder.set(FFMSupport.C_POINTER, 0, MemorySegment.ofAddress(handle));
                HIPRTCAPI.hiprtcDestroyProgram(holder);
            }
        }
    }

    private static byte[] code(Arena arena, long program) {
        MemorySegment size = FFMSupport.allocateLong(arena);
        if (HIPRTCAPI.hiprtcGetCodeSize(program, size) != HIPRTCAPI.HIPRTC_SUCCESS) {
            return new byte[0];
        }
        long byteCount = size.get(FFMSupport.C_LONG, 0);
        if (byteCount <= 0) {
            return new byte[0];
        }
        MemorySegment buffer = arena.allocate(byteCount, 1);
        if (HIPRTCAPI.hiprtcGetCode(program, buffer) != HIPRTCAPI.HIPRTC_SUCCESS) {
            return new byte[0];
        }
        return buffer.toArray(FFMSupport.C_CHAR);
    }

    /** The hipRTC build log (errors and warnings) for a program, empty when it produced none. */
    private static String programLog(Arena arena, long program) {
        MemorySegment size = FFMSupport.allocateLong(arena);
        if (HIPRTCAPI.hiprtcGetProgramLogSize(program, size) != HIPRTCAPI.HIPRTC_SUCCESS) {
            return "";
        }
        long byteCount = size.get(FFMSupport.C_LONG, 0);
        if (byteCount <= 1) {
            return "";
        }
        MemorySegment buffer = arena.allocate(byteCount, 1);
        if (HIPRTCAPI.hiprtcGetProgramLog(program, buffer) != HIPRTCAPI.HIPRTC_SUCCESS) {
            return "";
        }
        return FFMSupport.readCString(buffer, byteCount);
    }

    /** Loads the code object as a module on the program's device. */
    private static void loadModule(HIPHandles.Program program) {
        if (program.context != 0) {
            HIPDriverAPI.hipCtxSetCurrent(program.context);
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment image = arena.allocate(Math.max(program.binary.length, 1), 8);
            MemorySegment.copy(program.binary, 0, image, FFMSupport.C_CHAR, 0, program.binary.length);
            MemorySegment moduleOut = FFMSupport.allocatePointer(arena);
            // HIP ignores the JIT options (an error log buffer among them), so the error code is all there is
            int result = HIPDriverAPI.hipModuleLoadDataEx(moduleOut, image, 0, MemorySegment.NULL, MemorySegment.NULL);
            if (result == HIPDriverAPI.HIP_SUCCESS) {
                program.module = moduleOut.get(FFMSupport.C_POINTER, 0).address();
                program.moduleLoaded = true;
                return;
            }
            program.buildStatus = HIPHandles.Program.BUILD_ERROR;
            program.moduleLoaded = false;
            program.log = HIPDriverAPI.describe("hipModuleLoadDataEx", result)
                    + (result == HIPDriverAPI.HIP_ERROR_INVALID_IMAGE || result == HIPDriverAPI.HIP_ERROR_NO_BINARY_FOR_GPU
                            ? ". The code object was not built for this GPU: a pre-built binary must target its architecture (" + architectureOfCurrentProgram(program) + ")."
                            : "");
            System.out.println(LOG_PREFIX + program.log);
        }
    }

    private static String architectureOfCurrentProgram(HIPHandles.Program program) {
        return program.context > 0 ? architecture((int) (program.context - 1)) : "unknown";
    }

    /** hipRTC version as {@code major * 1000 + minor} (HIP 7.2 gives 7002), or {@code -1} when the query fails. */
    public static int version() {
        if (!HIPRTCAPI.isAvailable()) {
            return -1;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment major = FFMSupport.allocateInt(arena);
            MemorySegment minor = FFMSupport.allocateInt(arena);
            if (HIPRTCAPI.hiprtcVersion(major, minor) != HIPRTCAPI.HIPRTC_SUCCESS) {
                return -1;
            }
            return major.get(FFMSupport.C_INT, 0) * 1000 + minor.get(FFMSupport.C_INT, 0);
        }
    }

    /** Splits a whitespace-separated hipRTC option string into individual arguments. */
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
