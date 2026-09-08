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
package uk.ac.manchester.tornado.curand.provider;

import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_DOUBLE;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_FLOAT;
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
 * cuRAND bindings over the Java FFM API.
 *
 * <p>
 * cuRAND's host API is plain C, so no native shim is needed: the entry points are resolved with a
 * {@link SymbolLookup} at class initialisation and called through downcall handles. Device pointers
 * cross as {@code C_LONG} and the generator handle is an opaque pointer, likewise a {@code long}.
 * </p>
 *
 * <p>
 * If libcurand is not present the lookup comes back empty and every handle is null; {@link #load()}
 * then reports the provider as unavailable rather than failing the build.
 * </p>
 */
final class CuRandNativeLib {

    private static final SymbolLookup LIBCURAND = FFMSupport.loadLibrary("libcurand.so.10", "libcurand.so", "curand64_10.dll", "libcurand.dylib");

    private static final MethodHandle CURAND_CREATE_GENERATOR;
    private static final MethodHandle CURAND_DESTROY_GENERATOR;
    private static final MethodHandle CURAND_SET_STREAM;
    private static final MethodHandle CURAND_SET_SEED;
    private static final MethodHandle CURAND_SET_OFFSET;
    private static final MethodHandle CURAND_GENERATE_NORMAL;
    private static final MethodHandle CURAND_GENERATE_NORMAL_DOUBLE;
    private static final MethodHandle CURAND_GENERATE_UNIFORM;
    private static final MethodHandle CURAND_GENERATE_UNIFORM_DOUBLE;

    static {
        if (LIBCURAND == null) {
            CURAND_CREATE_GENERATOR = null;
            CURAND_DESTROY_GENERATOR = null;
            CURAND_SET_STREAM = null;
            CURAND_SET_SEED = null;
            CURAND_SET_OFFSET = null;
            CURAND_GENERATE_NORMAL = null;
            CURAND_GENERATE_NORMAL_DOUBLE = null;
            CURAND_GENERATE_UNIFORM = null;
            CURAND_GENERATE_UNIFORM_DOUBLE = null;
        } else {
            // curandCreateGenerator writes the handle through an out-parameter and returns a status.
            CURAND_CREATE_GENERATOR = FFMSupport.downcall(LIBCURAND, FunctionDescriptor.of(C_INT, C_POINTER, C_INT), "curandCreateGenerator");
            CURAND_DESTROY_GENERATOR = FFMSupport.downcall(LIBCURAND, FunctionDescriptor.of(C_INT, C_LONG), "curandDestroyGenerator");
            CURAND_SET_STREAM = FFMSupport.downcall(LIBCURAND, FunctionDescriptor.of(C_INT, C_LONG, C_LONG), "curandSetStream");
            CURAND_SET_SEED = FFMSupport.downcall(LIBCURAND, FunctionDescriptor.of(C_INT, C_LONG, C_LONG), "curandSetPseudoRandomGeneratorSeed");
            CURAND_SET_OFFSET = FFMSupport.downcall(LIBCURAND, FunctionDescriptor.of(C_INT, C_LONG, C_LONG), "curandSetGeneratorOffset");
            CURAND_GENERATE_NORMAL = FFMSupport.downcall(LIBCURAND, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_FLOAT, C_FLOAT), "curandGenerateNormal");
            CURAND_GENERATE_NORMAL_DOUBLE = FFMSupport.downcall(LIBCURAND, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_DOUBLE, C_DOUBLE), "curandGenerateNormalDouble");
            CURAND_GENERATE_UNIFORM = FFMSupport.downcall(LIBCURAND, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG), "curandGenerateUniform");
            CURAND_GENERATE_UNIFORM_DOUBLE = FFMSupport.downcall(LIBCURAND, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG), "curandGenerateUniformDouble");
        }
    }

    private CuRandNativeLib() {
    }

    /**
     * Verifies that libcurand resolved. Nothing is loaded lazily -- the symbol lookup happens at
     * class initialisation -- so this only reports whether the library is usable.
     */
    static synchronized void load() {
        if (LIBCURAND == null || CURAND_CREATE_GENERATOR == null) {
            throw new TornadoRuntimeException("[ERROR] Unable to load cuRAND. Install the CUDA Toolkit and make sure libcurand is on the library path.");
        }
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new IllegalStateException(t);
    }

    /** Returns the generator handle, or 0 on failure. */
    static long curandCreateGenerator(int rngType) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handle = arena.allocate(C_POINTER);
            if ((int) CURAND_CREATE_GENERATOR.invokeExact(handle, rngType) != CURAND_STATUS_SUCCESS) {
                return 0;
            }
            return handle.get(C_POINTER, 0).address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void curandDestroyGenerator(long generator) {
        if (generator == 0) {
            return;
        }
        try {
            int ignored = (int) CURAND_DESTROY_GENERATOR.invokeExact(generator);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int curandSetStream(long generator, long streamPtr) {
        try {
            return (int) CURAND_SET_STREAM.invokeExact(generator, streamPtr);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int curandSetPseudoRandomGeneratorSeed(long generator, long seed) {
        try {
            return (int) CURAND_SET_SEED.invokeExact(generator, seed);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int curandSetGeneratorOffset(long generator, long offset) {
        try {
            return (int) CURAND_SET_OFFSET.invokeExact(generator, offset);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int curandGenerateNormal(long generator, long devicePtr, long elementOffset, long n, float mean, float stddev) {
        try {
            return (int) CURAND_GENERATE_NORMAL.invokeExact(generator, devicePtr + elementOffset * Float.BYTES, n, mean, stddev);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int curandGenerateNormalDouble(long generator, long devicePtr, long elementOffset, long n, double mean, double stddev) {
        try {
            return (int) CURAND_GENERATE_NORMAL_DOUBLE.invokeExact(generator, devicePtr + elementOffset * Double.BYTES, n, mean, stddev);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int curandGenerateUniform(long generator, long devicePtr, long elementOffset, long n) {
        try {
            return (int) CURAND_GENERATE_UNIFORM.invokeExact(generator, devicePtr + elementOffset * Float.BYTES, n);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int curandGenerateUniformDouble(long generator, long devicePtr, long elementOffset, long n) {
        try {
            return (int) CURAND_GENERATE_UNIFORM_DOUBLE.invokeExact(generator, devicePtr + elementOffset * Double.BYTES, n);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** {@code curandStatus_t} success value. */
    private static final int CURAND_STATUS_SUCCESS = 0;

    static String decodeStatus(int status) {
        return switch (status) {
            case 0 -> "CURAND_STATUS_SUCCESS";
            case 100 -> "CURAND_STATUS_VERSION_MISMATCH";
            case 101 -> "CURAND_STATUS_NOT_INITIALIZED";
            case 102 -> "CURAND_STATUS_ALLOCATION_FAILED";
            case 103 -> "CURAND_STATUS_TYPE_ERROR";
            case 104 -> "CURAND_STATUS_OUT_OF_RANGE";
            case 105 -> "CURAND_STATUS_LENGTH_NOT_MULTIPLE";
            case 106 -> "CURAND_STATUS_DOUBLE_PRECISION_REQUIRED";
            case 201 -> "CURAND_STATUS_LAUNCH_FAILURE";
            case 202 -> "CURAND_STATUS_PREEXISTING_FAILURE";
            case 203 -> "CURAND_STATUS_INITIALIZATION_FAILED";
            case 204 -> "CURAND_STATUS_ARCH_MISMATCH";
            case 999 -> "CURAND_STATUS_INTERNAL_ERROR";
            default -> "CURAND_STATUS_" + status;
        };
    }

    static void checkStatus(int status, String what) {
        if (status != CURAND_STATUS_SUCCESS) {
            throw new TornadoRuntimeException("[ERROR] " + what + " failed: " + decodeStatus(status));
        }
    }
}
