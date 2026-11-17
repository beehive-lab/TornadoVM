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

import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
import uk.ac.manchester.tornado.runtime.ffm.ROCmLibraries;

/**
 * Panama bindings for hipRTC, which compiles the generated HIP C++ to an AMD GPU code object at run
 * time. hipRTC is versioned with the HIP runtime: {@code libhiprtc.so.<major>} on Linux, and on Windows a
 * DLL whose name carries the version ({@code hiprtc0702.dll} for 7.2), found by scanning the ROCm
 * {@code bin} directory.
 */
public final class HIPRTCAPI {

    public static final int HIPRTC_SUCCESS = 0;

    private static final SymbolLookup LIBHIPRTC = ROCmLibraries.load(List.of("libhiprtc.so.7", "libhiprtc.so.6", "libhiprtc.so"), windowsNames());

    private static final MethodHandle HIPRTC_GET_ERROR_STRING;
    private static final MethodHandle HIPRTC_VERSION;
    private static final MethodHandle HIPRTC_CREATE_PROGRAM;
    private static final MethodHandle HIPRTC_DESTROY_PROGRAM;
    private static final MethodHandle HIPRTC_COMPILE_PROGRAM;
    private static final MethodHandle HIPRTC_GET_PROGRAM_LOG_SIZE;
    private static final MethodHandle HIPRTC_GET_PROGRAM_LOG;
    private static final MethodHandle HIPRTC_GET_CODE_SIZE;
    private static final MethodHandle HIPRTC_GET_CODE;

    static {
        if (LIBHIPRTC == null) {
            HIPRTC_GET_ERROR_STRING = null;
            HIPRTC_VERSION = null;
            HIPRTC_CREATE_PROGRAM = null;
            HIPRTC_DESTROY_PROGRAM = null;
            HIPRTC_COMPILE_PROGRAM = null;
            HIPRTC_GET_PROGRAM_LOG_SIZE = null;
            HIPRTC_GET_PROGRAM_LOG = null;
            HIPRTC_GET_CODE_SIZE = null;
            HIPRTC_GET_CODE = null;
        } else {
            HIPRTC_GET_ERROR_STRING = downcall(LIBHIPRTC, FunctionDescriptor.of(C_POINTER, C_INT), "hiprtcGetErrorString");
            HIPRTC_VERSION = downcall(LIBHIPRTC, FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER), "hiprtcVersion");
            HIPRTC_CREATE_PROGRAM = downcall(LIBHIPRTC, FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_INT, C_POINTER, C_POINTER), "hiprtcCreateProgram");
            HIPRTC_DESTROY_PROGRAM = downcall(LIBHIPRTC, FunctionDescriptor.of(C_INT, C_POINTER), "hiprtcDestroyProgram");
            HIPRTC_COMPILE_PROGRAM = downcall(LIBHIPRTC, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_POINTER), "hiprtcCompileProgram");
            HIPRTC_GET_PROGRAM_LOG_SIZE = downcall(LIBHIPRTC, FunctionDescriptor.of(C_INT, C_LONG, C_POINTER), "hiprtcGetProgramLogSize");
            HIPRTC_GET_PROGRAM_LOG = downcall(LIBHIPRTC, FunctionDescriptor.of(C_INT, C_LONG, C_POINTER), "hiprtcGetProgramLog");
            HIPRTC_GET_CODE_SIZE = downcall(LIBHIPRTC, FunctionDescriptor.of(C_INT, C_LONG, C_POINTER), "hiprtcGetCodeSize");
            HIPRTC_GET_CODE = downcall(LIBHIPRTC, FunctionDescriptor.of(C_INT, C_LONG, C_POINTER), "hiprtcGetCode");
        }
    }

    private HIPRTCAPI() {
    }

    /** The hipRTC DLL names under the ROCm roots, newest version first, then the unversioned name. */
    private static List<String> windowsNames() {
        List<String> names = new ArrayList<>();
        for (String root : ROCmLibraries.roots()) {
            File[] files = new File(root, "bin").listFiles((directory, name) -> name.startsWith("hiprtc") && name.endsWith(".dll") && !name.contains("builtins"));
            if (files != null) {
                Arrays.sort(files, Comparator.comparing(File::getName).reversed());
                for (File file : files) {
                    if (!names.contains(file.getName())) {
                        names.add(file.getName());
                    }
                }
            }
        }
        names.add("hiprtc.dll");
        return names;
    }

    /** Whether hipRTC could be opened at all. */
    public static boolean isAvailable() {
        return LIBHIPRTC != null && HIPRTC_COMPILE_PROGRAM != null;
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

    public static String hiprtcGetErrorString(int result) {
        try {
            MemorySegment text = (MemorySegment) HIPRTC_GET_ERROR_STRING.invokeExact(result);
            String message = FFMSupport.readCString(text);
            return message == null ? Integer.toString(result) : message;
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hiprtcVersion(MemorySegment major, MemorySegment minor) {
        try {
            return (int) HIPRTC_VERSION.invokeExact(major, minor);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hiprtcCreateProgram(MemorySegment program, MemorySegment source, MemorySegment name, int numHeaders, MemorySegment headers, MemorySegment includeNames) {
        try {
            return (int) HIPRTC_CREATE_PROGRAM.invokeExact(program, source, name, numHeaders, headers, includeNames);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hiprtcDestroyProgram(MemorySegment program) {
        try {
            return (int) HIPRTC_DESTROY_PROGRAM.invokeExact(program);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hiprtcCompileProgram(long program, int numOptions, MemorySegment options) {
        try {
            return (int) HIPRTC_COMPILE_PROGRAM.invokeExact(program, numOptions, options);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hiprtcGetProgramLogSize(long program, MemorySegment logSize) {
        try {
            return (int) HIPRTC_GET_PROGRAM_LOG_SIZE.invokeExact(program, logSize);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hiprtcGetProgramLog(long program, MemorySegment log) {
        try {
            return (int) HIPRTC_GET_PROGRAM_LOG.invokeExact(program, log);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hiprtcGetCodeSize(long program, MemorySegment codeSize) {
        try {
            return (int) HIPRTC_GET_CODE_SIZE.invokeExact(program, codeSize);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int hiprtcGetCode(long program, MemorySegment code) {
        try {
            return (int) HIPRTC_GET_CODE.invokeExact(program, code);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }
}
