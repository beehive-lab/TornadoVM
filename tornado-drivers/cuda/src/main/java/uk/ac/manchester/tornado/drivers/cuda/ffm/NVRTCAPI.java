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

import uk.ac.manchester.tornado.drivers.common.ffm.FFMSupport;
import static uk.ac.manchester.tornado.drivers.common.ffm.FFMSupport.C_INT;
import static uk.ac.manchester.tornado.drivers.common.ffm.FFMSupport.C_LONG;
import static uk.ac.manchester.tornado.drivers.common.ffm.FFMSupport.C_POINTER;
import static uk.ac.manchester.tornado.drivers.common.ffm.FFMSupport.downcall;

import java.io.File;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

/**
 * Panama bindings for NVRTC, the runtime CUDA C compiler the backend uses to turn generated kernel
 * sources into a loadable module image.
 *
 * <p>
 * Unlike libcuda, NVRTC ships with the CUDA toolkit rather than the driver, so it is not guaranteed
 * to be on the loader path. The lookup therefore tries the sonames first and then the {@code lib64}
 * directory of every toolkit root the environment names, which is the same search the backend's
 * header probing already performs.
 */
public final class NVRTCAPI {

    /** nvrtcResult success code. */
    public static final int NVRTC_SUCCESS = 0;

    private static final SymbolLookup LIBNVRTC = loadNvrtc();

    private static final MethodHandle NVRTC_VERSION;
    private static final MethodHandle NVRTC_GET_ERROR_STRING;
    private static final MethodHandle NVRTC_CREATE_PROGRAM;
    private static final MethodHandle NVRTC_DESTROY_PROGRAM;
    private static final MethodHandle NVRTC_COMPILE_PROGRAM;
    private static final MethodHandle NVRTC_GET_PROGRAM_LOG_SIZE;
    private static final MethodHandle NVRTC_GET_PROGRAM_LOG;
    private static final MethodHandle NVRTC_GET_PTX_SIZE;
    private static final MethodHandle NVRTC_GET_PTX;
    private static final MethodHandle NVRTC_GET_CUBIN_SIZE;
    private static final MethodHandle NVRTC_GET_CUBIN;
    private static final MethodHandle NVRTC_GET_NUM_SUPPORTED_ARCHS;
    private static final MethodHandle NVRTC_GET_SUPPORTED_ARCHS;

    static {
        if (LIBNVRTC == null) {
            NVRTC_VERSION = null;
            NVRTC_GET_ERROR_STRING = null;
            NVRTC_CREATE_PROGRAM = null;
            NVRTC_DESTROY_PROGRAM = null;
            NVRTC_COMPILE_PROGRAM = null;
            NVRTC_GET_PROGRAM_LOG_SIZE = null;
            NVRTC_GET_PROGRAM_LOG = null;
            NVRTC_GET_PTX_SIZE = null;
            NVRTC_GET_PTX = null;
            NVRTC_GET_CUBIN_SIZE = null;
            NVRTC_GET_CUBIN = null;
            NVRTC_GET_NUM_SUPPORTED_ARCHS = null;
            NVRTC_GET_SUPPORTED_ARCHS = null;
        } else {
            NVRTC_VERSION = downcall(LIBNVRTC, FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER), "nvrtcVersion");
            NVRTC_GET_ERROR_STRING = downcall(LIBNVRTC, FunctionDescriptor.of(C_POINTER, C_INT), "nvrtcGetErrorString");
            NVRTC_CREATE_PROGRAM = downcall(LIBNVRTC, FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_INT, C_POINTER, C_POINTER), "nvrtcCreateProgram");
            NVRTC_DESTROY_PROGRAM = downcall(LIBNVRTC, FunctionDescriptor.of(C_INT, C_POINTER), "nvrtcDestroyProgram");
            NVRTC_COMPILE_PROGRAM = downcall(LIBNVRTC, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_POINTER), "nvrtcCompileProgram");
            NVRTC_GET_PROGRAM_LOG_SIZE = downcall(LIBNVRTC, FunctionDescriptor.of(C_INT, C_LONG, C_POINTER), "nvrtcGetProgramLogSize");
            NVRTC_GET_PROGRAM_LOG = downcall(LIBNVRTC, FunctionDescriptor.of(C_INT, C_LONG, C_POINTER), "nvrtcGetProgramLog");
            NVRTC_GET_PTX_SIZE = downcall(LIBNVRTC, FunctionDescriptor.of(C_INT, C_LONG, C_POINTER), "nvrtcGetPTXSize");
            NVRTC_GET_PTX = downcall(LIBNVRTC, FunctionDescriptor.of(C_INT, C_LONG, C_POINTER), "nvrtcGetPTX");
            NVRTC_GET_CUBIN_SIZE = downcall(LIBNVRTC, FunctionDescriptor.of(C_INT, C_LONG, C_POINTER), "nvrtcGetCUBINSize");
            NVRTC_GET_CUBIN = downcall(LIBNVRTC, FunctionDescriptor.of(C_INT, C_LONG, C_POINTER), "nvrtcGetCUBIN");
            NVRTC_GET_NUM_SUPPORTED_ARCHS = downcall(LIBNVRTC, FunctionDescriptor.of(C_INT, C_POINTER), "nvrtcGetNumSupportedArchs");
            NVRTC_GET_SUPPORTED_ARCHS = downcall(LIBNVRTC, FunctionDescriptor.of(C_INT, C_POINTER), "nvrtcGetSupportedArchs");
        }
    }

    private NVRTCAPI() {
    }

    private static SymbolLookup loadNvrtc() {
        List<String> candidates = new ArrayList<>();
        // Sonames first: these resolve through LD_LIBRARY_PATH / the ldconfig cache, which is how
        // a toolkit that has been put on the loader path is found.
        candidates.add("libnvrtc.so");
        for (int major = 13; major >= 10; major--) {
            candidates.add("libnvrtc.so." + major);
        }
        candidates.add("nvrtc64_120_0.dll");
        candidates.add("libnvrtc.dylib");
        // Then the lib64 directory of every toolkit root the environment names, for the common case
        // of a toolkit that is installed but not on the loader path.
        for (String root : toolkitRoots()) {
            for (String libDir : new String[] { "lib64", "lib", "lib/x64", "bin" }) {
                File dir = new File(root, libDir);
                if (!dir.isDirectory()) {
                    continue;
                }
                for (String name : new String[] { "libnvrtc.so", "libnvrtc.so.13", "libnvrtc.so.12", "libnvrtc.so.11.2", "libnvrtc.dylib" }) {
                    File file = new File(dir, name);
                    if (file.isFile()) {
                        candidates.add(file.getAbsolutePath());
                    }
                }
            }
        }
        return FFMSupport.loadLibrary(candidates.toArray(new String[0]));
    }

    /**
     * CUDA toolkit roots to probe, in priority order. Shared with the header search so that the
     * NVRTC that gets loaded and the headers that get offered to it come from the same toolkit.
     */
    public static List<String> toolkitRoots() {
        List<String> roots = new ArrayList<>();
        for (String var : new String[] { "CUDA_PATH", "CUDA_HOME", "CUDA_ROOT" }) {
            String value = System.getenv(var);
            if (value != null && !value.isEmpty() && !roots.contains(value)) {
                roots.add(value);
            }
        }
        if (!roots.contains("/usr/local/cuda")) {
            roots.add("/usr/local/cuda");
        }
        if (!roots.contains("/usr")) {
            roots.add("/usr");
        }
        return roots;
    }

    /** Whether an NVRTC could be loaded; false when no CUDA toolkit is reachable. */
    public static boolean isAvailable() {
        return LIBNVRTC != null && NVRTC_CREATE_PROGRAM != null;
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException e) {
            throw e;
        }
        if (t instanceof Error e) {
            throw e;
        }
        throw new IllegalStateException(t);
    }

    public static int nvrtcVersion(MemorySegment major, MemorySegment minor) {
        try {
            return (int) NVRTC_VERSION.invokeExact(major, minor);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static String nvrtcGetErrorString(int result) {
        try {
            MemorySegment text = (MemorySegment) NVRTC_GET_ERROR_STRING.invokeExact(result);
            String value = FFMSupport.readCString(text);
            return value == null ? Integer.toString(result) : value;
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int nvrtcCreateProgram(MemorySegment program, MemorySegment source, MemorySegment name, int numHeaders, MemorySegment headers, MemorySegment includeNames) {
        try {
            return (int) NVRTC_CREATE_PROGRAM.invokeExact(program, source, name, numHeaders, headers, includeNames);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int nvrtcDestroyProgram(MemorySegment program) {
        try {
            return (int) NVRTC_DESTROY_PROGRAM.invokeExact(program);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int nvrtcCompileProgram(long program, int numOptions, MemorySegment options) {
        try {
            return (int) NVRTC_COMPILE_PROGRAM.invokeExact(program, numOptions, options);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int nvrtcGetProgramLogSize(long program, MemorySegment logSize) {
        try {
            return (int) NVRTC_GET_PROGRAM_LOG_SIZE.invokeExact(program, logSize);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int nvrtcGetProgramLog(long program, MemorySegment log) {
        try {
            return (int) NVRTC_GET_PROGRAM_LOG.invokeExact(program, log);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int nvrtcGetPTXSize(long program, MemorySegment size) {
        try {
            return (int) NVRTC_GET_PTX_SIZE.invokeExact(program, size);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int nvrtcGetPTX(long program, MemorySegment ptx) {
        try {
            return (int) NVRTC_GET_PTX.invokeExact(program, ptx);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int nvrtcGetCUBINSize(long program, MemorySegment size) {
        try {
            return (int) NVRTC_GET_CUBIN_SIZE.invokeExact(program, size);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int nvrtcGetCUBIN(long program, MemorySegment cubin) {
        try {
            return (int) NVRTC_GET_CUBIN.invokeExact(program, cubin);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Not present before CUDA 11.2; callers fall back to the GPU's own arch when it is missing. */
    public static boolean hasSupportedArchsQuery() {
        return NVRTC_GET_NUM_SUPPORTED_ARCHS != null && NVRTC_GET_SUPPORTED_ARCHS != null;
    }

    public static int nvrtcGetNumSupportedArchs(MemorySegment numArchs) {
        try {
            return (int) NVRTC_GET_NUM_SUPPORTED_ARCHS.invokeExact(numArchs);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int nvrtcGetSupportedArchs(MemorySegment supportedArchs) {
        try {
            return (int) NVRTC_GET_SUPPORTED_ARCHS.invokeExact(supportedArchs);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }
}
