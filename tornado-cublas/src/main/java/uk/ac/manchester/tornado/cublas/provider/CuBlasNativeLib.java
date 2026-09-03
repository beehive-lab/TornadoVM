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

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;

/**
 * JNI bindings to libtornado-cublas. All device pointers are raw CUdeviceptr
 * values of TornadoVM-managed buffers; the cuBLAS handle is bound to the
 * TornadoVM execution stream via {@code cublasSetStream}, so calls are ordered
 * with the kernels and transfers of the same task graph.
 */
final class CuBlasNativeLib {

    /** {@code CUBLAS_STATUS_SUCCESS} and {@code cudaSuccess}. */
    private static final int CUBLAS_STATUS_SUCCESS = 0;
    private static final int CUDA_SUCCESS = 0;

    /** cuBLAS handles, streams and device pointers are all opaque to the Java layer. */
    private static final SymbolLookup LIBCUBLAS = FFMSupport.loadLibrary("libcublas.so.12", "libcublas.so.11", "libcublas.so", "cublas64_12.dll", "libcublas.dylib");

    /**
     * The workspace allocation goes through the CUDA runtime rather than the driver API, because
     * that is what cuBLAS itself is built on and {@code cudaMalloc} is what guarantees the
     * 256-byte alignment {@code cublasSetWorkspace} requires.
     */
    private static final SymbolLookup LIBCUDART = FFMSupport.loadLibrary("libcudart.so.12", "libcudart.so.11.0", "libcudart.so", "cudart64_12.dll", "libcudart.dylib");

    /** Scratch for the host-side alpha and beta scalars every GEMM passes by pointer. */
    private static final FFMSupport.Staging SCALARS = new FFMSupport.Staging();

    private static final MethodHandle CUBLAS_CREATE;
    private static final MethodHandle CUBLAS_DESTROY;
    private static final MethodHandle CUBLAS_SET_STREAM;
    private static final MethodHandle CUBLAS_SET_MATH_MODE;
    private static final MethodHandle CUBLAS_SET_WORKSPACE;
    private static final MethodHandle CUBLAS_SGEMV;
    private static final MethodHandle CUBLAS_SGEMM;
    private static final MethodHandle CUBLAS_SGEMM_STRIDED_BATCHED;
    private static final MethodHandle CUBLAS_GEMM_EX;
    private static final MethodHandle CUDA_MALLOC;
    private static final MethodHandle CUDA_FREE;

    static {
        if (LIBCUBLAS == null) {
            CUBLAS_CREATE = null;
            CUBLAS_DESTROY = null;
            CUBLAS_SET_STREAM = null;
            CUBLAS_SET_MATH_MODE = null;
            CUBLAS_SET_WORKSPACE = null;
            CUBLAS_SGEMV = null;
            CUBLAS_SGEMM = null;
            CUBLAS_SGEMM_STRIDED_BATCHED = null;
            CUBLAS_GEMM_EX = null;
        } else {
            // The cublas_v2.h header renames most entry points to a versioned symbol; the plain
            // names are the v1 ABI and must not be bound instead.
            CUBLAS_CREATE = FFMSupport.downcall(LIBCUBLAS, FunctionDescriptor.of(C_INT, C_POINTER), "cublasCreate_v2");
            CUBLAS_DESTROY = FFMSupport.downcall(LIBCUBLAS, FunctionDescriptor.of(C_INT, C_LONG), "cublasDestroy_v2");
            CUBLAS_SET_STREAM = FFMSupport.downcall(LIBCUBLAS, FunctionDescriptor.of(C_INT, C_LONG, C_LONG), "cublasSetStream_v2");
            CUBLAS_SET_MATH_MODE = FFMSupport.downcall(LIBCUBLAS, FunctionDescriptor.of(C_INT, C_LONG, C_INT), "cublasSetMathMode");
            CUBLAS_SET_WORKSPACE = FFMSupport.downcall(LIBCUBLAS, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG), "cublasSetWorkspace_v2");
            CUBLAS_SGEMV = FFMSupport.downcall(LIBCUBLAS, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_INT, C_POINTER, C_LONG, C_INT, C_LONG, C_INT, C_POINTER, C_LONG, C_INT),
                    "cublasSgemv_v2");
            CUBLAS_SGEMM = FFMSupport.downcall(LIBCUBLAS,
                    FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_INT, C_INT, C_INT, C_POINTER, C_LONG, C_INT, C_LONG, C_INT, C_POINTER, C_LONG, C_INT), "cublasSgemm_v2");
            CUBLAS_SGEMM_STRIDED_BATCHED = FFMSupport.downcall(LIBCUBLAS,
                    FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_INT, C_INT, C_INT, C_POINTER, C_LONG, C_INT, C_LONG, C_LONG, C_INT, C_LONG, C_POINTER, C_LONG, C_INT, C_LONG, C_INT),
                    "cublasSgemmStridedBatched");
            CUBLAS_GEMM_EX = FFMSupport.downcall(LIBCUBLAS,
                    FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_INT, C_INT, C_INT, C_POINTER, C_LONG, C_INT, C_INT, C_LONG, C_INT, C_INT, C_POINTER, C_LONG, C_INT, C_INT, C_INT, C_INT),
                    "cublasGemmEx");
        }
        if (LIBCUDART == null) {
            CUDA_MALLOC = null;
            CUDA_FREE = null;
        } else {
            CUDA_MALLOC = FFMSupport.downcall(LIBCUDART, FunctionDescriptor.of(C_INT, C_POINTER, C_LONG), "cudaMalloc");
            CUDA_FREE = FFMSupport.downcall(LIBCUDART, FunctionDescriptor.of(C_INT, C_LONG), "cudaFree");
        }
    }

    private CuBlasNativeLib() {
    }

    /**
     * Checks that cuBLAS is reachable. There is no TornadoVM library to load any more -- the
     * bindings above open cuBLAS itself -- so this only reports whether the toolkit is installed.
     */
    static synchronized void load() {
        if (LIBCUBLAS == null || CUBLAS_CREATE == null) {
            throw new TornadoRuntimeException("[ERROR] Unable to load cuBLAS. Install the CUDA Toolkit and make sure libcublas is on the library path.");
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

    static long cublasCreate() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handle = FFMSupport.allocatePointer(arena);
            if ((int) CUBLAS_CREATE.invokeExact(handle) != CUBLAS_STATUS_SUCCESS) {
                return 0;
            }
            return handle.get(FFMSupport.C_POINTER, 0).address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int cublasSetStream(long handle, long streamPtr) {
        try {
            return (int) CUBLAS_SET_STREAM.invokeExact(handle, streamPtr);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void cublasDestroy(long handle) {
        if (handle == 0) {
            return;
        }
        try {
            int ignored = (int) CUBLAS_DESTROY.invokeExact(handle);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int cublasSetMathMode(long handle, int mathMode) {
        try {
            return (int) CUBLAS_SET_MATH_MODE.invokeExact(handle, mathMode);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int cublasSetWorkspace(long handle, long workspacePtr, long workspaceBytes) {
        try {
            return (int) CUBLAS_SET_WORKSPACE.invokeExact(handle, workspacePtr, workspaceBytes);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Returns the device pointer, or 0 on allocation failure. */
    static long allocateDeviceMemory(long bytes) {
        if (CUDA_MALLOC == null) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pointer = FFMSupport.allocatePointer(arena);
            if ((int) CUDA_MALLOC.invokeExact(pointer, bytes) != CUDA_SUCCESS) {
                return 0;
            }
            return pointer.get(FFMSupport.C_POINTER, 0).address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void freeDeviceMemory(long ptr) {
        if (ptr == 0 || CUDA_FREE == null) {
            return;
        }
        try {
            int ignored = (int) CUDA_FREE.invokeExact(ptr);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int cublasSgemv(long handle, int trans, int m, int n, float alpha, long dA, int lda, long dX, int incx, float beta, long dY, int incy) {
        MemorySegment scalars = scalars(alpha, beta);
        try {
            return (int) CUBLAS_SGEMV.invokeExact(handle, trans, m, n, scalars.asSlice(0, Float.BYTES), dA, lda, dX, incx, scalars.asSlice(Float.BYTES, Float.BYTES), dY, incy);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int cublasSgemm(long handle, int transa, int transb, int m, int n, int k, float alpha, long dA, int lda, long dB, int ldb, float beta, long dC, int ldc) {
        MemorySegment scalars = scalars(alpha, beta);
        try {
            return (int) CUBLAS_SGEMM.invokeExact(handle, transa, transb, m, n, k, scalars.asSlice(0, Float.BYTES), dA, lda, dB, ldb, scalars.asSlice(Float.BYTES, Float.BYTES), dC, ldc);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int cublasSgemmStridedBatched(long handle, int transa, int transb, int m, int n, int k, float alpha, long dA, int lda, long strideA, long dB, int ldb, long strideB, float beta, long dC,
            int ldc, long strideC, int batchCount) {
        MemorySegment scalars = scalars(alpha, beta);
        try {
            return (int) CUBLAS_SGEMM_STRIDED_BATCHED.invokeExact(handle, transa, transb, m, n, k, scalars.asSlice(0, Float.BYTES), dA, lda, strideA, dB, ldb, strideB,
                    scalars.asSlice(Float.BYTES, Float.BYTES), dC, ldc, strideC, batchCount);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /**
     * Scalars are host floats, which is valid for the {@code CUBLAS_COMPUTE_32F*} compute types
     * only: the scale type of the FP32 compute family is {@code CUDA_R_32F}.
     */
    static int cublasGemmEx(long handle, int transa, int transb, int m, int n, int k, float alpha, long dA, int aType, int lda, long dB, int bType, int ldb, float beta, long dC, int cType, int ldc,
            int computeType, int algo) {
        MemorySegment scalars = scalars(alpha, beta);
        try {
            return (int) CUBLAS_GEMM_EX.invokeExact(handle, transa, transb, m, n, k, scalars.asSlice(0, Float.BYTES), dA, aType, lda, dB, bType, ldb, scalars.asSlice(Float.BYTES, Float.BYTES), dC,
                    cType, ldc, computeType, algo);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /**
     * Decodes a {@code cublasStatus_t} value.
     */
    static String decodeStatus(int status) {
        return switch (status) {
            case 0 -> "CUBLAS_STATUS_SUCCESS";
            case 1 -> "CUBLAS_STATUS_NOT_INITIALIZED";
            case 3 -> "CUBLAS_STATUS_ALLOC_FAILED";
            case 7 -> "CUBLAS_STATUS_INVALID_VALUE";
            case 8 -> "CUBLAS_STATUS_ARCH_MISMATCH";
            case 11 -> "CUBLAS_STATUS_MAPPING_ERROR";
            case 13 -> "CUBLAS_STATUS_EXECUTION_FAILED";
            case 14 -> "CUBLAS_STATUS_INTERNAL_ERROR";
            case 15 -> "CUBLAS_STATUS_NOT_SUPPORTED";
            case 16 -> "CUBLAS_STATUS_LICENSE_ERROR";
            default -> "UNKNOWN_CUBLAS_STATUS (" + status + ")";
        };
    }

    static void checkStatus(int status, String function) {
        if (status != 0) {
            throw new TornadoRuntimeException("[ERROR] " + function + " failed with status: " + decodeStatus(status));
        }
    }
}
