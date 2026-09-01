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
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_INT;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_LONG;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_POINTER;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.downcall;

import java.io.File;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
        // Windows bakes the CUDA version into the DLL name and NVIDIA bumps it on every release
        // whose ABI changed: nvrtc64_120_0.dll served every CUDA 12.x release, CUDA 13.0
        // introduced nvrtc64_130_0.dll. A single hardcoded name goes stale the day a newer
        // toolkit ships, so every version this backend has ever been run against is tried here,
        // newest first, before falling through to the toolkit-root scan below (which finds
        // whatever is actually installed by filename, not by a version this code had to guess).
        for (int major = 13; major >= 10; major--) {
            for (int minor = 9; minor >= 0; minor--) {
                candidates.add("nvrtc64_" + major + minor + "_0.dll");
            }
        }
        candidates.add("libnvrtc.dylib");
        // Then every directory that might hold NVRTC under every toolkit root the environment
        // names (or, absent those, the standard install location - see toolkitRoots()), for the
        // common case of a toolkit that is installed but not on the loader path. bin/x64 is
        // where CUDA 13.x moved the Windows runtime DLLs (older toolkits kept them directly in
        // bin); both are checked since the layout varies by toolkit version.
        for (String root : toolkitRoots()) {
            for (String libDir : new String[] { "lib64", "lib", "lib/x64", "bin", "bin/x64" }) {
                File dir = new File(root, libDir);
                File[] files = dir.listFiles();
                if (files == null) {
                    continue;
                }
                for (File file : files) {
                    String name = file.getName();
                    boolean isNvrtc = name.equals("libnvrtc.so") || name.startsWith("libnvrtc.so.") || name.equals("libnvrtc.dylib")
                            || (name.startsWith("nvrtc64_") && name.endsWith(".dll"));
                    if (isNvrtc) {
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
        // Windows has no equivalent of /usr/local/cuda: the installer's default location instead
        // carries the version in the path itself (v13.2, v12.8, ...), and only sets the plain
        // CUDA_PATH to whichever toolkit was installed last - a machine with several toolkits, or
        // one where CUDA_PATH was never exported to this process, would otherwise find none of
        // them. Every version under the standard install root is offered here, newest first, so
        // NVRTC is still found without requiring any environment variable to be set.
        String programFiles = System.getenv("ProgramFiles");
        File cudaRoot = new File(programFiles != null ? programFiles : "C:\\Program Files", "NVIDIA GPU Computing Toolkit" + File.separator + "CUDA");
        File[] versions = cudaRoot.listFiles(File::isDirectory);
        if (versions != null) {
            // Lexicographic order on the directory name would rank "v9.0" above "v13.2" (a
            // character comparison sees '9' > '1'), so a stray old toolkit could shadow a newer
            // one; compare the parsed major/minor numerically instead.
            Arrays.sort(versions, Comparator.comparingInt(NVRTCAPI::cudaVersionKey).reversed());
            for (File version : versions) {
                String path = version.getAbsolutePath();
                if (!roots.contains(path)) {
                    roots.add(path);
                }
            }
        }
        return roots;
    }

    /**
     * Numeric sort key for a toolkit directory named {@code vMAJOR.MINOR} (as the Windows
     * installer names them), highest first. Anything that does not parse sorts last rather than
     * first, so an unrecognised entry cannot masquerade as the newest toolkit.
     */
    private static int cudaVersionKey(File versionDir) {
        String name = versionDir.getName();
        if (!name.isEmpty() && (name.charAt(0) == 'v' || name.charAt(0) == 'V')) {
            name = name.substring(1);
        }
        String[] parts = name.split("\\.", 2);
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return major * 1000 + minor;
        } catch (NumberFormatException e) {
            return -1;
        }
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
