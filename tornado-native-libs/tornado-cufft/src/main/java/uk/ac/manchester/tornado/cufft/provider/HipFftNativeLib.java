/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
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
package uk.ac.manchester.tornado.cufft.provider;

import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_INT;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_LONG;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_POINTER;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.List;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
import uk.ac.manchester.tornado.runtime.ffm.ROCmLibraries;

/**
 * Panama bindings to hipFFT, the ROCm counterpart of {@link CuFftNativeLib} for HIP devices. hipFFT
 * shares cuFFT's transform types, directions and result codes; its plan handle is a pointer rather than
 * an int.
 */
final class HipFftNativeLib {

    private static boolean loaded = false;

    private HipFftNativeLib() {
    }

    /** Checks that hipFFT is reachable. */
    static synchronized void load() {
        if (LIBHIPFFT == null || HIPFFT_PLAN_1D == null) {
            throw new TornadoRuntimeException("[ERROR] Unable to load hipFFT. Install ROCm with hipFFT and set ROCM_PATH (or HIP_PATH) to its root.");
        }
    }

    /** Raw {@code hipfftType} values (the same as cuFFT's). */
    static final int HIPFFT_C2C = 0x29;
    static final int HIPFFT_R2C = 0x2a;
    static final int HIPFFT_C2R = 0x2c;
    static final int HIPFFT_Z2Z = 0x69;

    /** {@code HIPFFT_SUCCESS}. */
    private static final int HIPFFT_SUCCESS = 0;

    private static final SymbolLookup LIBHIPFFT = ROCmLibraries.load(List.of("libhipfft.so.0", "libhipfft.so"), List.of("hipfft.dll", "libhipfft.dll"));

    private static final MethodHandle HIPFFT_PLAN_1D;
    private static final MethodHandle HIPFFT_PLAN_2D;
    private static final MethodHandle HIPFFT_SET_STREAM;
    private static final MethodHandle HIPFFT_EXEC_C2C;
    private static final MethodHandle HIPFFT_EXEC_R2C;
    private static final MethodHandle HIPFFT_EXEC_C2R;
    private static final MethodHandle HIPFFT_EXEC_Z2Z;
    private static final MethodHandle HIPFFT_GET_VERSION;
    private static final MethodHandle HIPFFT_DESTROY;

    static {
        if (LIBHIPFFT == null) {
            HIPFFT_PLAN_1D = null;
            HIPFFT_PLAN_2D = null;
            HIPFFT_SET_STREAM = null;
            HIPFFT_EXEC_C2C = null;
            HIPFFT_EXEC_R2C = null;
            HIPFFT_EXEC_C2R = null;
            HIPFFT_EXEC_Z2Z = null;
            HIPFFT_GET_VERSION = null;
            HIPFFT_DESTROY = null;
        } else {
            // A hipfftHandle is a pointer (cuFFT's is an int); the Java layer carries it as a long.
            HIPFFT_PLAN_1D = FFMSupport.downcall(LIBHIPFFT, FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_INT, C_INT), "hipfftPlan1d");
            HIPFFT_PLAN_2D = FFMSupport.downcall(LIBHIPFFT, FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_INT, C_INT), "hipfftPlan2d");
            HIPFFT_SET_STREAM = FFMSupport.downcall(LIBHIPFFT, FunctionDescriptor.of(C_INT, C_LONG, C_LONG), "hipfftSetStream");
            HIPFFT_EXEC_C2C = FFMSupport.downcall(LIBHIPFFT, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_INT), "hipfftExecC2C");
            HIPFFT_EXEC_R2C = FFMSupport.downcall(LIBHIPFFT, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG), "hipfftExecR2C");
            HIPFFT_EXEC_C2R = FFMSupport.downcall(LIBHIPFFT, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG), "hipfftExecC2R");
            HIPFFT_EXEC_Z2Z = FFMSupport.downcall(LIBHIPFFT, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_INT), "hipfftExecZ2Z");
            HIPFFT_GET_VERSION = FFMSupport.downcall(LIBHIPFFT, FunctionDescriptor.of(C_INT, C_POINTER), "hipfftGetVersion");
            HIPFFT_DESTROY = FFMSupport.downcall(LIBHIPFFT, FunctionDescriptor.of(C_INT, C_LONG), "hipfftDestroy");
        }
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

    private static long createPlan1d(int n, int batch, int type) {
        if (HIPFFT_PLAN_1D == null) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment plan = FFMSupport.allocatePointer(arena);
            if ((int) HIPFFT_PLAN_1D.invokeExact(plan, n, type, batch) != HIPFFT_SUCCESS) {
                return 0;
            }
            return plan.get(C_POINTER, 0).address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Creates a 1D C2C FP32 plan for {@code batch} transforms of length n; returns 0 on failure. */
    static long hipfftPlan1dC2C(int n, int batch) {
        return createPlan1d(n, batch, HIPFFT_C2C);
    }

    static long hipfftPlan1dOfType(int n, int batch, int type) {
        return createPlan1d(n, batch, type);
    }

    static long hipfftPlan2dOfType(int nx, int ny, int type) {
        if (HIPFFT_PLAN_2D == null) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment plan = FFMSupport.allocatePointer(arena);
            if ((int) HIPFFT_PLAN_2D.invokeExact(plan, nx, ny, type) != HIPFFT_SUCCESS) {
                return 0;
            }
            return plan.get(C_POINTER, 0).address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int hipfftSetStream(long plan, long streamPtr) {
        try {
            return (int) HIPFFT_SET_STREAM.invokeExact(plan, streamPtr);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int hipfftExecC2C(long plan, long dIn, long dOut, int direction) {
        try {
            return (int) HIPFFT_EXEC_C2C.invokeExact(plan, dIn, dOut, direction);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int hipfftExecR2C(long plan, long dIn, long dOut) {
        try {
            return (int) HIPFFT_EXEC_R2C.invokeExact(plan, dIn, dOut);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int hipfftExecC2R(long plan, long dIn, long dOut) {
        try {
            return (int) HIPFFT_EXEC_C2R.invokeExact(plan, dIn, dOut);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int hipfftExecZ2Z(long plan, long dIn, long dOut, int direction) {
        try {
            return (int) HIPFFT_EXEC_Z2Z.invokeExact(plan, dIn, dOut, direction);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int hipfftGetVersion() {
        if (HIPFFT_GET_VERSION == null) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment version = FFMSupport.allocateInt(arena);
            if ((int) HIPFFT_GET_VERSION.invokeExact(version) != HIPFFT_SUCCESS) {
                return 0;
            }
            return version.get(C_INT, 0);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void hipfftDestroy(long plan) {
        if (plan == 0 || HIPFFT_DESTROY == null) {
            return;
        }
        try {
            int ignored = (int) HIPFFT_DESTROY.invokeExact(plan);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static String decodeResult(int result) {
        return switch (result) {
            case 0 -> "HIPFFT_SUCCESS";
            case 1 -> "HIPFFT_INVALID_PLAN";
            case 2 -> "HIPFFT_ALLOC_FAILED";
            case 3 -> "HIPFFT_INVALID_TYPE";
            case 4 -> "HIPFFT_INVALID_VALUE";
            case 5 -> "HIPFFT_INTERNAL_ERROR";
            case 6 -> "HIPFFT_EXEC_FAILED";
            case 7 -> "HIPFFT_SETUP_FAILED";
            case 8 -> "HIPFFT_INVALID_SIZE";
            case 9 -> "HIPFFT_UNALIGNED_DATA";
            case 10 -> "HIPFFT_INCOMPLETE_PARAMETER_LIST";
            case 11 -> "HIPFFT_INVALID_DEVICE";
            case 12 -> "HIPFFT_PARSE_ERROR";
            case 13 -> "HIPFFT_NO_WORKSPACE";
            case 14 -> "HIPFFT_NOT_IMPLEMENTED";
            case 16 -> "HIPFFT_NOT_SUPPORTED";
            default -> "UNKNOWN_HIPFFT_RESULT (" + result + ")";
        };
    }

    static void checkResult(int result, String function) {
        if (result != 0) {
            throw new TornadoRuntimeException("[ERROR] " + function + " failed with result: " + decodeResult(result));
        }
    }
}
