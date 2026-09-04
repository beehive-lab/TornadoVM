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
import java.io.IOException;
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

    private static final String LIBNVRTC_SO_PREFIX = "libnvrtc.so.";

    /**
     * Directories under a toolkit root that may hold NVRTC. {@code bin/x64} is where CUDA 13.x
     * moved the Windows runtime DLLs (older toolkits kept them directly in {@code bin}), and the
     * multiarch directories are where Debian and Ubuntu package the distribution's own CUDA
     * runtime - a toolkit root ({@code /usr}) whose libraries live in none of the others, so
     * without them the packaged NVRTC was reachable only by soname and never version-ranked
     * against the toolkits installed under {@code /usr/local}.
     */
    private static final String[] LIB_DIRS = { "lib64", "lib", "lib/x64", "bin", "bin/x64", "lib/x86_64-linux-gnu", "lib/aarch64-linux-gnu" };

    /**
     * The candidate {@link #loadNvrtc()} actually loaded. Assigned before {@link #LIBNVRTC} is,
     * since it is set from that field's own initializer. A host can hold several toolkits and the
     * one in use decides which architectures can be targeted, so measurements have to be able to
     * state which NVRTC produced them rather than inferring it from what is installed.
     */
    private static String loadedLibrary;

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

        // An explicit choice outranks every heuristic below: a host with several toolkits can pin
        // the exact NVRTC it means to measure with, which the version ranking would otherwise
        // decide for it.
        String explicit = explicitNvrtcLibrary();
        if (explicit != null) {
            candidates.add(explicit);
        }

        // Toolkit roots BEFORE the bare soname. The unversioned libnvrtc.so on the loader path
        // belongs to whichever toolkit the distribution packaged, and that is routinely OLDER than
        // one installed later under /usr/local: on a Blackwell host carrying Ubuntu's CUDA 12.0
        // nvidia-cuda-toolkit alongside a hand-installed CUDA 13.0, taking the soname first pins
        // the backend to the 12.0 NVRTC, which cannot emit sm_120 at all and so forces the
        // compute_90 PTX fallback - with the newer toolkit sitting unused on disk and nothing in
        // the log naming the older one as the reason. Ranking the toolkits actually present by
        // version and trying them first makes the newest installed NVRTC win, which is the
        // assumption the arch selection in CUDACompiler is written against.
        candidates.addAll(toolkitNvrtcFiles());

        // Versioned sonames next, newest major first, so a toolkit that IS on the loader path but
        // installed somewhere this scan does not know about is still found.
        for (int major = 13; major >= 10; major--) {
            candidates.add("libnvrtc.so." + major);
        }
        // Windows bakes the CUDA version into the DLL name and NVIDIA bumps it on every release
        // whose ABI changed: nvrtc64_120_0.dll served every CUDA 12.x release, CUDA 13.0
        // introduced nvrtc64_130_0.dll. A single hardcoded name goes stale the day a newer
        // toolkit ships, so every version this backend has ever been run against is tried here,
        // newest first.
        for (int major = 13; major >= 10; major--) {
            for (int minor = 9; minor >= 0; minor--) {
                candidates.add("nvrtc64_" + major + minor + "_0.dll");
            }
        }
        // Unversioned names last. They name no version, so they are the weakest evidence of which
        // toolkit is being loaded, and serve only as the fallback for an install neither the
        // environment nor the root scan located.
        candidates.add("libnvrtc.so");
        candidates.add("libnvrtc.dylib");

        // Tried one at a time rather than in a single call so the winning candidate can be
        // recorded; see loadedLibrary().
        for (String candidate : candidates) {
            SymbolLookup lookup = FFMSupport.loadLibrary(candidate);
            if (lookup != null) {
                loadedLibrary = candidate;
                return lookup;
            }
        }
        return null;
    }

    /**
     * The NVRTC that was loaded - an absolute path when it was found under a toolkit root, or the
     * soname the loader resolved otherwise - or {@code null} when none could be loaded.
     */
    public static String loadedLibrary() {
        return loadedLibrary;
    }

    /**
     * Absolute path of an NVRTC named explicitly by the {@code tornado.cuda.nvrtc.library} system
     * property or the {@code TORNADO_NVRTC_LIBRARY} environment variable, or {@code null} when
     * neither is set. Provides a deterministic override on hosts holding several toolkits, where
     * measurements have to state which one produced them.
     */
    private static String explicitNvrtcLibrary() {
        String property = System.getProperty("tornado.cuda.nvrtc.library");
        if (property != null && !property.isEmpty()) {
            return property;
        }
        String variable = System.getenv("TORNADO_NVRTC_LIBRARY");
        return variable != null && !variable.isEmpty() ? variable : null;
    }

    /**
     * Every NVRTC found under the toolkit roots, newest first. Roots the environment names keep
     * their stated priority ahead of the discovered ones - naming a toolkit is a deliberate act, so
     * it outranks a merely newer one found on disk. Within each group the files are ordered by the
     * CUDA version their soname carries, so an older toolkit cannot shadow a newer one just by
     * sorting earlier in a directory listing.
     */
    private static List<String> toolkitNvrtcFiles() {
        List<String> named = environmentRoots();
        List<File> fromNamedRoots = new ArrayList<>();
        List<File> fromDiscoveredRoots = new ArrayList<>();
        for (String root : toolkitRoots()) {
            List<File> bucket = named.contains(root) ? fromNamedRoots : fromDiscoveredRoots;
            for (String libDir : LIB_DIRS) {
                File[] files = new File(root, libDir).listFiles();
                if (files == null) {
                    continue;
                }
                for (File file : files) {
                    if (isNvrtcLibrary(file.getName())) {
                        bucket.add(file);
                    }
                }
            }
        }
        List<String> ordered = new ArrayList<>();
        for (List<File> bucket : List.of(fromNamedRoots, fromDiscoveredRoots)) {
            bucket.sort(Comparator.comparingInt(NVRTCAPI::nvrtcFileVersionKey).reversed());
            for (File file : bucket) {
                String path = file.getAbsolutePath();
                if (!ordered.contains(path)) {
                    ordered.add(path);
                }
            }
        }
        return ordered;
    }

    private static boolean isNvrtcLibrary(String name) {
        return name.equals("libnvrtc.so") || name.startsWith("libnvrtc.so.") || name.equals("libnvrtc.dylib") || (name.startsWith("nvrtc64_") && name.endsWith(".dll"));
    }

    /**
     * CUDA version of an NVRTC file as {@code major * 1000 + minor}. The soname normally carries it
     * (libnvrtc.so.13.0.88, nvrtc64_130_0.dll); an unversioned libnvrtc.so is usually a symlink, so
     * it is resolved before falling back to the version named by the toolkit directory holding it.
     * Anything that identifies no version sorts last rather than first, so an unrecognised file
     * cannot masquerade as the newest toolkit.
     */
    private static int nvrtcFileVersionKey(File file) {
        int key = sonameVersionKey(file.getName());
        if (key < 0) {
            try {
                key = sonameVersionKey(file.getCanonicalFile().getName());
            } catch (IOException e) {
                // Unreadable link: fall through to the directory-derived version below.
            }
        }
        if (key < 0) {
            for (File directory = file.getParentFile(); directory != null; directory = directory.getParentFile()) {
                int fromDirectory = cudaVersionKey(directory);
                if (fromDirectory >= 0) {
                    return fromDirectory;
                }
            }
        }
        return key;
    }

    /** Version encoded in an NVRTC library filename, or {@code -1} when it carries none. */
    private static int sonameVersionKey(String name) {
        if (name.startsWith(LIBNVRTC_SO_PREFIX)) {
            String[] parts = name.substring(LIBNVRTC_SO_PREFIX.length()).split("\\.");
            try {
                int major = Integer.parseInt(parts[0]);
                int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                return major * 1000 + minor;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        if (name.startsWith("nvrtc64_") && name.endsWith(".dll")) {
            int end = name.indexOf('_', "nvrtc64_".length());
            if (end < 0) {
                return -1;
            }
            try {
                // nvrtc64_130_0.dll: the "130" field packs major 13 and minor 0 together.
                int packed = Integer.parseInt(name.substring("nvrtc64_".length(), end));
                return (packed / 10) * 1000 + packed % 10;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * CUDA toolkit roots to probe, in priority order. Shared with the header search so that the
     * NVRTC that gets loaded and the headers that get offered to it come from the same toolkit.
     */
    public static List<String> toolkitRoots() {
        List<String> roots = new ArrayList<>(environmentRoots());
        for (String root : localCudaRoots()) {
            if (!roots.contains(root)) {
                roots.add(root);
            }
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

    /** Toolkit roots the environment names, in the order the variables are consulted. */
    private static List<String> environmentRoots() {
        List<String> roots = new ArrayList<>();
        for (String var : new String[] { "CUDA_PATH", "CUDA_HOME", "CUDA_ROOT" }) {
            String value = System.getenv(var);
            if (value != null && !value.isEmpty() && !roots.contains(value)) {
                roots.add(value);
            }
        }
        return roots;
    }

    /**
     * CUDA toolkits installed under {@code /usr/local}, newest first. Linux installs one versioned
     * directory per toolkit (cuda-13.0, cuda-12.8) plus an optional {@code cuda} symlink naming a
     * default, so a host readily holds several at once. Only the symlink used to be consulted,
     * which left a toolkit installed beside it undiscoverable - the usual shape of adding CUDA 13
     * to a machine whose distribution already packaged an older one. The versioned directories are
     * enumerated and ranked here for the same reason the Windows branch ranks its own: the newest
     * toolkit present should win, not whichever one a symlink or a directory listing happens to
     * name first.
     */
    private static List<String> localCudaRoots() {
        File[] entries = new File("/usr/local").listFiles(f -> f.isDirectory() && (f.getName().equals("cuda") || f.getName().startsWith("cuda-")));
        if (entries == null) {
            // No /usr/local/cuda* at all; still offer the conventional path in case it appears as
            // something this filter cannot see (a symlink to an unreadable directory, say).
            return List.of("/usr/local/cuda");
        }
        Arrays.sort(entries, Comparator.comparingInt(NVRTCAPI::localCudaVersionKey).reversed());
        List<String> roots = new ArrayList<>();
        for (File entry : entries) {
            roots.add(entry.getAbsolutePath());
        }
        return roots;
    }

    /**
     * Version of a {@code /usr/local} CUDA directory, resolving the unversioned {@code cuda}
     * symlink to the versioned directory it points at so it ranks alongside its siblings instead
     * of sorting last.
     */
    private static int localCudaVersionKey(File directory) {
        int key = cudaVersionKey(directory);
        if (key < 0) {
            try {
                key = cudaVersionKey(directory.getCanonicalFile());
            } catch (IOException e) {
                // Unreadable link: leave it unversioned so it sorts last.
            }
        }
        return key;
    }

    /**
     * Numeric sort key for a toolkit directory named {@code vMAJOR.MINOR} (as the Windows
     * installer names them) or {@code cuda-MAJOR.MINOR} (as Linux installs them), highest first.
     * Anything that does not parse sorts last rather than first, so an unrecognised entry cannot
     * masquerade as the newest toolkit.
     */
    private static int cudaVersionKey(File versionDir) {
        String name = versionDir.getName();
        if (name.startsWith("cuda-")) {
            name = name.substring("cuda-".length());
        } else if (!name.isEmpty() && (name.charAt(0) == 'v' || name.charAt(0) == 'V')) {
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
