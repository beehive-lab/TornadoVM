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
package uk.ac.manchester.tornado.cublas.provider;

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
 * Panama bindings to hipBLAS, the ROCm counterpart of {@link CuBlasNativeLib} for HIP devices. All
 * device pointers are raw {@code hipDeviceptr_t} values of TornadoVM-managed buffers; the hipBLAS
 * handle is bound to the TornadoVM execution stream via {@code hipblasSetStream}, so calls are ordered
 * with the kernels and transfers of the same task graph.
 */
final class HipBlasNativeLib {

    /** {@code HIPBLAS_STATUS_SUCCESS} and {@code hipSuccess}. */
    private static final int HIPBLAS_STATUS_SUCCESS = 0;
    private static final int HIP_SUCCESS = 0;
    /** {@code HIPBLAS_STATUS_NOT_SUPPORTED}. */
    static final int HIPBLAS_STATUS_NOT_SUPPORTED = 7;

    /** hipBLAS handles, streams and device pointers are all opaque to the Java layer. */
    private static final SymbolLookup LIBHIPBLAS = ROCmLibraries.load(List.of("libhipblas.so.3", "libhipblas.so.2", "libhipblas.so"), List.of("hipblas.dll", "libhipblas.dll"));

    /** The workspace comes from {@code hipMalloc}, which gives the alignment {@code hipblasSetWorkspace} requires. */
    private static final SymbolLookup LIBHIP = ROCmLibraries.load(List.of("libamdhip64.so.7", "libamdhip64.so.6", "libamdhip64.so"), List.of("amdhip64_7.dll", "amdhip64_6.dll"));

    /** Scratch for the host-side alpha and beta scalars every GEMM passes by pointer. */
    private static final FFMSupport.Staging SCALARS = new FFMSupport.Staging();

    private static final MethodHandle HIPBLAS_CREATE;
    private static final MethodHandle HIPBLAS_DESTROY;
    private static final MethodHandle HIPBLAS_SET_STREAM;
    private static final MethodHandle HIPBLAS_SET_MATH_MODE;
    private static final MethodHandle HIPBLAS_SET_WORKSPACE;
    private static final MethodHandle HIPBLAS_SET_POINTER_MODE;
    private static final MethodHandle HIPBLAS_SDOT;
    private static final MethodHandle HIPBLAS_SNRM2;
    private static final MethodHandle HIPBLAS_SASUM;
    private static final MethodHandle HIPBLAS_ISAMAX;
    private static final MethodHandle HIPBLAS_ISAMIN;
    private static final MethodHandle HIPBLAS_SGEMV;
    private static final MethodHandle HIPBLAS_SGEMM;
    private static final MethodHandle HIPBLAS_DGEMM;
    private static final MethodHandle HIPBLAS_SGEMM_STRIDED_BATCHED;
    private static final MethodHandle HIPBLAS_GEMM_EX;
    private static final MethodHandle HIP_MALLOC;
    private static final MethodHandle HIP_FREE;

    static {
        if (LIBHIPBLAS == null) {
            HIPBLAS_CREATE = null;
            HIPBLAS_DESTROY = null;
            HIPBLAS_SET_STREAM = null;
            HIPBLAS_SET_MATH_MODE = null;
            HIPBLAS_SET_WORKSPACE = null;
            HIPBLAS_SET_POINTER_MODE = null;
            HIPBLAS_SDOT = null;
            HIPBLAS_SNRM2 = null;
            HIPBLAS_SASUM = null;
            HIPBLAS_ISAMAX = null;
            HIPBLAS_ISAMIN = null;
            HIPBLAS_SGEMV = null;
            HIPBLAS_SGEMM = null;
            HIPBLAS_DGEMM = null;
            HIPBLAS_SGEMM_STRIDED_BATCHED = null;
            HIPBLAS_GEMM_EX = null;
        } else {
            HIPBLAS_CREATE = FFMSupport.downcall(LIBHIPBLAS, FunctionDescriptor.of(C_INT, C_POINTER), "hipblasCreate");
            HIPBLAS_DESTROY = FFMSupport.downcall(LIBHIPBLAS, FunctionDescriptor.of(C_INT, C_LONG), "hipblasDestroy");
            HIPBLAS_SET_STREAM = FFMSupport.downcall(LIBHIPBLAS, FunctionDescriptor.of(C_INT, C_LONG, C_LONG), "hipblasSetStream");
            HIPBLAS_SET_MATH_MODE = FFMSupport.downcall(LIBHIPBLAS, FunctionDescriptor.of(C_INT, C_LONG, C_INT), "hipblasSetMathMode");
            HIPBLAS_SET_WORKSPACE = FFMSupport.downcall(LIBHIPBLAS, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG), "hipblasSetWorkspace");
            HIPBLAS_SET_POINTER_MODE = FFMSupport.downcall(LIBHIPBLAS, FunctionDescriptor.of(C_INT, C_LONG, C_INT), "hipblasSetPointerMode");
            // The scalar-output level-1 routines write their result through a pointer. Under
            // HIPBLAS_POINTER_MODE_DEVICE that pointer is device memory, so the result stays on the
            // GPU and hipBLAS does not have to synchronise the stream to deliver it.
            HIPBLAS_SDOT = FFMSupport.downcall(LIBHIPBLAS, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_LONG, C_INT, C_LONG, C_INT, C_LONG), "hipblasSdot");
            HIPBLAS_SNRM2 = FFMSupport.downcall(LIBHIPBLAS, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_LONG, C_INT, C_LONG), "hipblasSnrm2");
            HIPBLAS_SASUM = FFMSupport.downcall(LIBHIPBLAS, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_LONG, C_INT, C_LONG), "hipblasSasum");
            // The index-returning routines write an int rather than a float, but are otherwise the
            // same shape and have the same host-pointer-mode synchronisation problem.
            HIPBLAS_ISAMAX = FFMSupport.downcall(LIBHIPBLAS, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_LONG, C_INT, C_LONG), "hipblasIsamax");
            HIPBLAS_ISAMIN = FFMSupport.downcall(LIBHIPBLAS, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_LONG, C_INT, C_LONG), "hipblasIsamin");
            HIPBLAS_SGEMV = FFMSupport.downcall(LIBHIPBLAS, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_INT, C_POINTER, C_LONG, C_INT, C_LONG, C_INT, C_POINTER, C_LONG, C_INT),
                    "hipblasSgemv");
            HIPBLAS_SGEMM = FFMSupport.downcall(LIBHIPBLAS,
                    FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_INT, C_INT, C_INT, C_POINTER, C_LONG, C_INT, C_LONG, C_INT, C_POINTER, C_LONG, C_INT), "hipblasSgemm");
            HIPBLAS_DGEMM = FFMSupport.downcall(LIBHIPBLAS,
                    FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_INT, C_INT, C_INT, C_POINTER, C_LONG, C_INT, C_LONG, C_INT, C_POINTER, C_LONG, C_INT), "hipblasDgemm");
            HIPBLAS_SGEMM_STRIDED_BATCHED = FFMSupport.downcall(LIBHIPBLAS,
                    FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_INT, C_INT, C_INT, C_POINTER, C_LONG, C_INT, C_LONG, C_LONG, C_INT, C_LONG, C_POINTER, C_LONG, C_INT, C_LONG, C_INT),
                    "hipblasSgemmStridedBatched");
            HIPBLAS_GEMM_EX = FFMSupport.downcall(LIBHIPBLAS,
                    FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_INT, C_INT, C_INT, C_POINTER, C_LONG, C_INT, C_INT, C_LONG, C_INT, C_INT, C_POINTER, C_LONG, C_INT, C_INT, C_INT, C_INT),
                    // ROCm 6 names the hipDataType form hipblasGemmEx_v2; from ROCm 7 it is hipblasGemmEx.
                    "hipblasGemmEx_v2", "hipblasGemmEx");
        }
        if (LIBHIP == null) {
            HIP_MALLOC = null;
            HIP_FREE = null;
        } else {
            HIP_MALLOC = FFMSupport.downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_POINTER, C_LONG), "hipMalloc");
            HIP_FREE = FFMSupport.downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG), "hipFree");
        }
    }

    private HipBlasNativeLib() {
    }

    /** Checks that hipBLAS is reachable. */
    static synchronized void load() {
        if (LIBHIPBLAS == null || HIPBLAS_CREATE == null) {
            throw new TornadoRuntimeException("[ERROR] Unable to load hipBLAS. Install ROCm with hipBLAS and set ROCM_PATH (or HIP_PATH) to its root.");
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

    /** Writes alpha and beta into the per-thread scalar scratch and returns it. */
    private static MemorySegment scalars(float alpha, float beta) {
        MemorySegment segment = SCALARS.forBytes(2L * Float.BYTES);
        segment.set(FFMSupport.C_FLOAT, 0, alpha);
        segment.set(FFMSupport.C_FLOAT, Float.BYTES, beta);
        return segment;
    }

    /** Writes a double alpha and beta into the per-thread scalar scratch and returns it. */
    private static MemorySegment scalars(double alpha, double beta) {
        MemorySegment segment = SCALARS.forBytes(2L * Double.BYTES);
        segment.set(FFMSupport.C_DOUBLE, 0, alpha);
        segment.set(FFMSupport.C_DOUBLE, Double.BYTES, beta);
        return segment;
    }

    static long hipblasCreate() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handle = FFMSupport.allocatePointer(arena);
            if ((int) HIPBLAS_CREATE.invokeExact(handle) != HIPBLAS_STATUS_SUCCESS) {
                return 0;
            }
            return handle.get(FFMSupport.C_POINTER, 0).address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int hipblasSetStream(long handle, long streamPtr) {
        try {
            return (int) HIPBLAS_SET_STREAM.invokeExact(handle, streamPtr);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void hipblasDestroy(long handle) {
        if (handle == 0) {
            return;
        }
        try {
            int ignored = (int) HIPBLAS_DESTROY.invokeExact(handle);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** {@code HIPBLAS_STATUS_NOT_SUPPORTED} when this hipBLAS predates math modes. */
    static int hipblasSetMathMode(long handle, int mathMode) {
        if (HIPBLAS_SET_MATH_MODE == null) {
            return HIPBLAS_STATUS_NOT_SUPPORTED;
        }
        try {
            return (int) HIPBLAS_SET_MATH_MODE.invokeExact(handle, mathMode);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** {@code HIPBLAS_STATUS_NOT_SUPPORTED} when this hipBLAS cannot take a user workspace. */
    static int hipblasSetWorkspace(long handle, long workspacePtr, long workspaceBytes) {
        if (HIPBLAS_SET_WORKSPACE == null) {
            return HIPBLAS_STATUS_NOT_SUPPORTED;
        }
        try {
            return (int) HIPBLAS_SET_WORKSPACE.invokeExact(handle, workspacePtr, workspaceBytes);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Returns the device pointer, or 0 on allocation failure. */
    static long allocateDeviceMemory(long bytes) {
        if (HIP_MALLOC == null) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pointer = FFMSupport.allocatePointer(arena);
            if ((int) HIP_MALLOC.invokeExact(pointer, bytes) != HIP_SUCCESS) {
                return 0;
            }
            return pointer.get(FFMSupport.C_POINTER, 0).address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void freeDeviceMemory(long ptr) {
        if (ptr == 0 || HIP_FREE == null) {
            return;
        }
        try {
            int ignored = (int) HIP_FREE.invokeExact(ptr);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** {@code hipblasPointerMode_t}. */
    private static final int HIPBLAS_POINTER_MODE_HOST = 0;
    private static final int HIPBLAS_POINTER_MODE_DEVICE = 1;

    private static int setPointerMode(long handle, int mode) {
        try {
            return (int) HIPBLAS_SET_POINTER_MODE.invokeExact(handle, mode);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /**
     * Runs a scalar-output level-1 routine with the handle in device pointer mode, restoring host
     * mode afterwards because the handle is shared by every call of the execution plan.
     */
    private static int inDevicePointerMode(long handle, java.util.function.LongToIntFunction body) {
        int status = setPointerMode(handle, HIPBLAS_POINTER_MODE_DEVICE);
        if (status != HIPBLAS_STATUS_SUCCESS) {
            return status;
        }
        try {
            return body.applyAsInt(handle);
        } finally {
            setPointerMode(handle, HIPBLAS_POINTER_MODE_HOST);
        }
    }

    static int hipblasSdot(long handle, int n, long dX, int incx, long dY, int incy, long dResult) {
        return inDevicePointerMode(handle, h -> {
            try {
                return (int) HIPBLAS_SDOT.invokeExact(h, n, dX, incx, dY, incy, dResult);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        });
    }

    static int hipblasSnrm2(long handle, int n, long dX, int incx, long dResult) {
        return inDevicePointerMode(handle, h -> {
            try {
                return (int) HIPBLAS_SNRM2.invokeExact(h, n, dX, incx, dResult);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        });
    }

    static int hipblasSasum(long handle, int n, long dX, int incx, long dResult) {
        return inDevicePointerMode(handle, h -> {
            try {
                return (int) HIPBLAS_SASUM.invokeExact(h, n, dX, incx, dResult);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        });
    }

    static int hipblasIsamax(long handle, int n, long dX, int incx, long dResult) {
        return inDevicePointerMode(handle, h -> {
            try {
                return (int) HIPBLAS_ISAMAX.invokeExact(h, n, dX, incx, dResult);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        });
    }

    static int hipblasIsamin(long handle, int n, long dX, int incx, long dResult) {
        return inDevicePointerMode(handle, h -> {
            try {
                return (int) HIPBLAS_ISAMIN.invokeExact(h, n, dX, incx, dResult);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        });
    }

    static int hipblasSgemv(long handle, int trans, int m, int n, float alpha, long dA, int lda, long dX, int incx, float beta, long dY, int incy) {
        MemorySegment scalars = scalars(alpha, beta);
        try {
            return (int) HIPBLAS_SGEMV.invokeExact(handle, trans, m, n, scalars.asSlice(0, Float.BYTES), dA, lda, dX, incx, scalars.asSlice(Float.BYTES, Float.BYTES), dY, incy);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int hipblasSgemm(long handle, int transa, int transb, int m, int n, int k, float alpha, long dA, int lda, long dB, int ldb, float beta, long dC, int ldc) {
        MemorySegment scalars = scalars(alpha, beta);
        try {
            return (int) HIPBLAS_SGEMM.invokeExact(handle, transa, transb, m, n, k, scalars.asSlice(0, Float.BYTES), dA, lda, dB, ldb, scalars.asSlice(Float.BYTES, Float.BYTES), dC, ldc);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int hipblasDgemm(long handle, int transa, int transb, int m, int n, int k, double alpha, long dA, int lda, long dB, int ldb, double beta, long dC, int ldc) {
        MemorySegment scalars = scalars(alpha, beta);
        try {
            return (int) HIPBLAS_DGEMM.invokeExact(handle, transa, transb, m, n, k, scalars.asSlice(0, Double.BYTES), dA, lda, dB, ldb, scalars.asSlice(Double.BYTES, Double.BYTES), dC, ldc);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int hipblasSgemmStridedBatched(long handle, int transa, int transb, int m, int n, int k, float alpha, long dA, int lda, long strideA, long dB, int ldb, long strideB, float beta, long dC,
            int ldc, long strideC, int batchCount) {
        MemorySegment scalars = scalars(alpha, beta);
        try {
            return (int) HIPBLAS_SGEMM_STRIDED_BATCHED.invokeExact(handle, transa, transb, m, n, k, scalars.asSlice(0, Float.BYTES), dA, lda, strideA, dB, ldb, strideB,
                    scalars.asSlice(Float.BYTES, Float.BYTES), dC, ldc, strideC, batchCount);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /**
     * Scalars are host floats, which is valid for the {@code HIPBLAS_COMPUTE_32F*} compute types
     * only: the scale type of the FP32 compute family is {@code HIP_R_32F}.
     */
    static int hipblasGemmEx(long handle, int transa, int transb, int m, int n, int k, float alpha, long dA, int aType, int lda, long dB, int bType, int ldb, float beta, long dC, int cType, int ldc,
            int computeType, int algo) {
        MemorySegment scalars = scalars(alpha, beta);
        try {
            return (int) HIPBLAS_GEMM_EX.invokeExact(handle, transa, transb, m, n, k, scalars.asSlice(0, Float.BYTES), dA, aType, lda, dB, bType, ldb, scalars.asSlice(Float.BYTES, Float.BYTES), dC,
                    cType, ldc, computeType, algo);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Decodes a {@code hipblasStatus_t} value. */
    static String decodeStatus(int status) {
        return switch (status) {
            case 0 -> "HIPBLAS_STATUS_SUCCESS";
            case 1 -> "HIPBLAS_STATUS_NOT_INITIALIZED";
            case 2 -> "HIPBLAS_STATUS_ALLOC_FAILED";
            case 3 -> "HIPBLAS_STATUS_INVALID_VALUE";
            case 4 -> "HIPBLAS_STATUS_MAPPING_ERROR";
            case 5 -> "HIPBLAS_STATUS_EXECUTION_FAILED";
            case 6 -> "HIPBLAS_STATUS_INTERNAL_ERROR";
            case 7 -> "HIPBLAS_STATUS_NOT_SUPPORTED";
            case 8 -> "HIPBLAS_STATUS_ARCH_MISMATCH";
            case 9 -> "HIPBLAS_STATUS_HANDLE_IS_NULLPTR";
            case 10 -> "HIPBLAS_STATUS_INVALID_ENUM";
            default -> "UNKNOWN_HIPBLAS_STATUS (" + status + ")";
        };
    }

    static void checkStatus(int status, String function) {
        if (status != 0) {
            throw new TornadoRuntimeException("[ERROR] " + function + " failed with status: " + decodeStatus(status));
        }
    }
}
