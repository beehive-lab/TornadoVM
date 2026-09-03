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

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;

/**
 * JNI bindings to libtornado-cufft. Plans are created per (n, batch) shape,
 * bound to the TornadoVM execution stream, and cached by the provider.
 */
final class CuFftNativeLib {

    private static boolean loaded = false;

    private CuFftNativeLib() {
    }

    /**
     * Checks that cuFFT is reachable. There is no TornadoVM library to load any more -- the
     * bindings open cuFFT itself -- so this only reports whether the toolkit is installed.
     */
    static synchronized void load() {
        if (LIBCUFFT == null || CUFFT_PLAN_1D == null) {
            throw new TornadoRuntimeException("[ERROR] Unable to load cuFFT. Install the CUDA Toolkit and make sure libcufft is on the library path.");
        }
    }

    /** Raw {@code cufftType} values. */
    static final int CUFFT_C2C = 0x29;
    static final int CUFFT_R2C = 0x2a;
    static final int CUFFT_C2R = 0x2c;
    static final int CUFFT_Z2Z = 0x69;

    /** {@code CUFFT_SUCCESS}. */
    private static final int CUFFT_SUCCESS = 0;

    private static final SymbolLookup LIBCUFFT = FFMSupport.loadLibrary("libcufft.so.11", "libcufft.so.10", "libcufft.so", "cufft64_11.dll", "libcufft.dylib");

    private static final MethodHandle CUFFT_PLAN_1D;
    private static final MethodHandle CUFFT_PLAN_2D;
    private static final MethodHandle CUFFT_SET_STREAM;
    private static final MethodHandle CUFFT_EXEC_C2C;
    private static final MethodHandle CUFFT_EXEC_R2C;
    private static final MethodHandle CUFFT_EXEC_C2R;
    private static final MethodHandle CUFFT_EXEC_Z2Z;
    private static final MethodHandle CUFFT_GET_VERSION;
    private static final MethodHandle CUFFT_DESTROY;

    static {
        if (LIBCUFFT == null) {
            CUFFT_PLAN_1D = null;
            CUFFT_PLAN_2D = null;
            CUFFT_SET_STREAM = null;
            CUFFT_EXEC_C2C = null;
            CUFFT_EXEC_R2C = null;
            CUFFT_EXEC_C2R = null;
            CUFFT_EXEC_Z2Z = null;
            CUFFT_GET_VERSION = null;
            CUFFT_DESTROY = null;
        } else {
            // A cufftHandle is an int, not a pointer; the Java layer carries it widened to long.
            CUFFT_PLAN_1D = FFMSupport.downcall(LIBCUFFT, FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_INT, C_INT), "cufftPlan1d");
            CUFFT_PLAN_2D = FFMSupport.downcall(LIBCUFFT, FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_INT, C_INT), "cufftPlan2d");
            CUFFT_SET_STREAM = FFMSupport.downcall(LIBCUFFT, FunctionDescriptor.of(C_INT, C_INT, C_LONG), "cufftSetStream");
            CUFFT_EXEC_C2C = FFMSupport.downcall(LIBCUFFT, FunctionDescriptor.of(C_INT, C_INT, C_LONG, C_LONG, C_INT), "cufftExecC2C");
            CUFFT_EXEC_R2C = FFMSupport.downcall(LIBCUFFT, FunctionDescriptor.of(C_INT, C_INT, C_LONG, C_LONG), "cufftExecR2C");
            CUFFT_EXEC_C2R = FFMSupport.downcall(LIBCUFFT, FunctionDescriptor.of(C_INT, C_INT, C_LONG, C_LONG), "cufftExecC2R");
            CUFFT_EXEC_Z2Z = FFMSupport.downcall(LIBCUFFT, FunctionDescriptor.of(C_INT, C_INT, C_LONG, C_LONG, C_INT), "cufftExecZ2Z");
            CUFFT_GET_VERSION = FFMSupport.downcall(LIBCUFFT, FunctionDescriptor.of(C_INT, C_POINTER), "cufftGetVersion");
            CUFFT_DESTROY = FFMSupport.downcall(LIBCUFFT, FunctionDescriptor.of(C_INT, C_INT), "cufftDestroy");
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
        if (CUFFT_PLAN_1D == null) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment plan = FFMSupport.allocateInt(arena);
            if ((int) CUFFT_PLAN_1D.invokeExact(plan, n, type, batch) != CUFFT_SUCCESS) {
                return 0;
            }
            return plan.get(C_INT, 0);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Creates a 1D C2C FP32 plan for {@code batch} transforms of length n; returns 0 on failure. */
    static long cufftPlan1dC2C(int n, int batch) {
        return createPlan1d(n, batch, CUFFT_C2C);
    }

    static long cufftPlan1dOfType(int n, int batch, int type) {
        return createPlan1d(n, batch, type);
    }

    static long cufftPlan2dOfType(int nx, int ny, int type) {
        if (CUFFT_PLAN_2D == null) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment plan = FFMSupport.allocateInt(arena);
            if ((int) CUFFT_PLAN_2D.invokeExact(plan, nx, ny, type) != CUFFT_SUCCESS) {
                return 0;
            }
            return plan.get(C_INT, 0);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int cufftSetStream(long plan, long streamPtr) {
        try {
            return (int) CUFFT_SET_STREAM.invokeExact((int) plan, streamPtr);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int cufftExecC2C(long plan, long dIn, long dOut, int direction) {
        try {
            return (int) CUFFT_EXEC_C2C.invokeExact((int) plan, dIn, dOut, direction);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int cufftExecR2C(long plan, long dIn, long dOut) {
        try {
            return (int) CUFFT_EXEC_R2C.invokeExact((int) plan, dIn, dOut);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int cufftExecC2R(long plan, long dIn, long dOut) {
        try {
            return (int) CUFFT_EXEC_C2R.invokeExact((int) plan, dIn, dOut);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int cufftExecZ2Z(long plan, long dIn, long dOut, int direction) {
        try {
            return (int) CUFFT_EXEC_Z2Z.invokeExact((int) plan, dIn, dOut, direction);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int cufftGetVersion() {
        if (CUFFT_GET_VERSION == null) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment version = FFMSupport.allocateInt(arena);
            if ((int) CUFFT_GET_VERSION.invokeExact(version) != CUFFT_SUCCESS) {
                return 0;
            }
            return version.get(C_INT, 0);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void cufftDestroy(long plan) {
        if (plan == 0 || CUFFT_DESTROY == null) {
            return;
        }
        try {
            int ignored = (int) CUFFT_DESTROY.invokeExact((int) plan);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static String decodeResult(int result) {
        return switch (result) {
            case 0 -> "CUFFT_SUCCESS";
            case 1 -> "CUFFT_INVALID_PLAN";
            case 2 -> "CUFFT_ALLOC_FAILED";
            case 3 -> "CUFFT_INVALID_TYPE";
            case 4 -> "CUFFT_INVALID_VALUE";
            case 5 -> "CUFFT_INTERNAL_ERROR";
            case 6 -> "CUFFT_EXEC_FAILED";
            case 7 -> "CUFFT_SETUP_FAILED";
            case 8 -> "CUFFT_INVALID_SIZE";
            case 9 -> "CUFFT_UNALIGNED_DATA";
            default -> "UNKNOWN_CUFFT_RESULT (" + result + ")";
        };
    }

    static void checkResult(int result, String function) {
        if (result != 0) {
            throw new TornadoRuntimeException("[ERROR] " + function + " failed with result: " + decodeResult(result));
        }
    }
}
