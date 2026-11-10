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
import java.util.List;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
import uk.ac.manchester.tornado.runtime.ffm.ROCmLibraries;

/**
 * hipRAND bindings over the Java FFM API, the ROCm counterpart of {@link CuRandNativeLib} for HIP
 * devices. hipRAND's host API mirrors cuRAND's; its generator types are cuRAND's plus 300, and its
 * status codes are cuRAND's.
 */
final class HipRandNativeLib {

    private static final SymbolLookup LIBHIPRAND = ROCmLibraries.load(List.of("libhiprand.so.1", "libhiprand.so"), List.of("hiprand.dll", "libhiprand.dll"));

    private static final MethodHandle HIPRAND_CREATE_GENERATOR;
    private static final MethodHandle HIPRAND_DESTROY_GENERATOR;
    private static final MethodHandle HIPRAND_SET_STREAM;
    private static final MethodHandle HIPRAND_SET_SEED;
    private static final MethodHandle HIPRAND_SET_OFFSET;
    private static final MethodHandle HIPRAND_GENERATE_NORMAL;
    private static final MethodHandle HIPRAND_GENERATE_NORMAL_DOUBLE;
    private static final MethodHandle HIPRAND_GENERATE_UNIFORM;
    private static final MethodHandle HIPRAND_GENERATE_UNIFORM_DOUBLE;

    static {
        if (LIBHIPRAND == null) {
            HIPRAND_CREATE_GENERATOR = null;
            HIPRAND_DESTROY_GENERATOR = null;
            HIPRAND_SET_STREAM = null;
            HIPRAND_SET_SEED = null;
            HIPRAND_SET_OFFSET = null;
            HIPRAND_GENERATE_NORMAL = null;
            HIPRAND_GENERATE_NORMAL_DOUBLE = null;
            HIPRAND_GENERATE_UNIFORM = null;
            HIPRAND_GENERATE_UNIFORM_DOUBLE = null;
        } else {
            // curandCreateGenerator writes the handle through an out-parameter and returns a status.
            HIPRAND_CREATE_GENERATOR = FFMSupport.downcall(LIBHIPRAND, FunctionDescriptor.of(C_INT, C_POINTER, C_INT), "hiprandCreateGenerator");
            HIPRAND_DESTROY_GENERATOR = FFMSupport.downcall(LIBHIPRAND, FunctionDescriptor.of(C_INT, C_LONG), "hiprandDestroyGenerator");
            HIPRAND_SET_STREAM = FFMSupport.downcall(LIBHIPRAND, FunctionDescriptor.of(C_INT, C_LONG, C_LONG), "hiprandSetStream");
            HIPRAND_SET_SEED = FFMSupport.downcall(LIBHIPRAND, FunctionDescriptor.of(C_INT, C_LONG, C_LONG), "hiprandSetPseudoRandomGeneratorSeed");
            HIPRAND_SET_OFFSET = FFMSupport.downcall(LIBHIPRAND, FunctionDescriptor.of(C_INT, C_LONG, C_LONG), "hiprandSetGeneratorOffset");
            HIPRAND_GENERATE_NORMAL = FFMSupport.downcall(LIBHIPRAND, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_FLOAT, C_FLOAT), "hiprandGenerateNormal");
            HIPRAND_GENERATE_NORMAL_DOUBLE = FFMSupport.downcall(LIBHIPRAND, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_DOUBLE, C_DOUBLE), "hiprandGenerateNormalDouble");
            HIPRAND_GENERATE_UNIFORM = FFMSupport.downcall(LIBHIPRAND, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG), "hiprandGenerateUniform");
            HIPRAND_GENERATE_UNIFORM_DOUBLE = FFMSupport.downcall(LIBHIPRAND, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG), "hiprandGenerateUniformDouble");
        }
    }

    private HipRandNativeLib() {
    }

    /** Verifies that libhiprand resolved. */
    static synchronized void load() {
        if (LIBHIPRAND == null || HIPRAND_CREATE_GENERATOR == null) {
            throw new TornadoRuntimeException("[ERROR] Unable to load hipRAND. Install ROCm with hipRAND and set ROCM_PATH (or HIP_PATH) to its root.");
        }
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new IllegalStateException(t);
    }

    /** Returns the generator handle, or 0 on failure. */
    static long hiprandCreateGenerator(int rngType) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handle = arena.allocate(C_POINTER);
            if ((int) HIPRAND_CREATE_GENERATOR.invokeExact(handle, rngType) != HIPRAND_STATUS_SUCCESS) {
                return 0;
            }
            return handle.get(C_POINTER, 0).address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void hiprandDestroyGenerator(long generator) {
        if (generator == 0) {
            return;
        }
        try {
            int ignored = (int) HIPRAND_DESTROY_GENERATOR.invokeExact(generator);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int hiprandSetStream(long generator, long streamPtr) {
        try {
            return (int) HIPRAND_SET_STREAM.invokeExact(generator, streamPtr);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int hiprandSetPseudoRandomGeneratorSeed(long generator, long seed) {
        try {
            return (int) HIPRAND_SET_SEED.invokeExact(generator, seed);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int hiprandSetGeneratorOffset(long generator, long offset) {
        try {
            return (int) HIPRAND_SET_OFFSET.invokeExact(generator, offset);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int hiprandGenerateNormal(long generator, long devicePtr, long elementOffset, long n, float mean, float stddev) {
        try {
            return (int) HIPRAND_GENERATE_NORMAL.invokeExact(generator, devicePtr + elementOffset * Float.BYTES, n, mean, stddev);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int hiprandGenerateNormalDouble(long generator, long devicePtr, long elementOffset, long n, double mean, double stddev) {
        try {
            return (int) HIPRAND_GENERATE_NORMAL_DOUBLE.invokeExact(generator, devicePtr + elementOffset * Double.BYTES, n, mean, stddev);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int hiprandGenerateUniform(long generator, long devicePtr, long elementOffset, long n) {
        try {
            return (int) HIPRAND_GENERATE_UNIFORM.invokeExact(generator, devicePtr + elementOffset * Float.BYTES, n);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int hiprandGenerateUniformDouble(long generator, long devicePtr, long elementOffset, long n) {
        try {
            return (int) HIPRAND_GENERATE_UNIFORM_DOUBLE.invokeExact(generator, devicePtr + elementOffset * Double.BYTES, n);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** {@code curandStatus_t} success value. */
    private static final int HIPRAND_STATUS_SUCCESS = 0;

    static String decodeStatus(int status) {
        return switch (status) {
            case 0 -> "HIPRAND_STATUS_SUCCESS";
            case 100 -> "HIPRAND_STATUS_VERSION_MISMATCH";
            case 101 -> "HIPRAND_STATUS_NOT_INITIALIZED";
            case 102 -> "HIPRAND_STATUS_ALLOCATION_FAILED";
            case 103 -> "HIPRAND_STATUS_TYPE_ERROR";
            case 104 -> "HIPRAND_STATUS_OUT_OF_RANGE";
            case 105 -> "HIPRAND_STATUS_LENGTH_NOT_MULTIPLE";
            case 106 -> "HIPRAND_STATUS_DOUBLE_PRECISION_REQUIRED";
            case 201 -> "HIPRAND_STATUS_LAUNCH_FAILURE";
            case 202 -> "HIPRAND_STATUS_PREEXISTING_FAILURE";
            case 203 -> "HIPRAND_STATUS_INITIALIZATION_FAILED";
            case 204 -> "HIPRAND_STATUS_ARCH_MISMATCH";
            case 999 -> "HIPRAND_STATUS_INTERNAL_ERROR";
            default -> "HIPRAND_STATUS_" + status;
        };
    }

    static void checkStatus(int status, String what) {
        if (status != HIPRAND_STATUS_SUCCESS) {
            throw new TornadoRuntimeException("[ERROR] " + what + " failed: " + decodeStatus(status));
        }
    }
}
