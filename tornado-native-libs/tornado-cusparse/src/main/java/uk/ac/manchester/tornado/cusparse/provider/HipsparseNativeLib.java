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
package uk.ac.manchester.tornado.cusparse.provider;

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
 * Panama bindings to hipSPARSE's generic API (CSR / FP32), the ROCm counterpart of
 * {@link CusparseNativeLib} for HIP devices. hipSPARSE's generic API takes the same arguments and enum
 * values as cuSPARSE's, and its status codes are cuSPARSE's.
 */
final class HipsparseNativeLib {

    private static boolean loaded = false;

    private HipsparseNativeLib() {
    }

    /** Checks that hipSPARSE is reachable. */
    static synchronized void load() {
        if (LIBHIPSPARSE == null || CREATE == null) {
            throw new TornadoRuntimeException("[ERROR] Unable to load hipSPARSE. Install ROCm with hipSPARSE and set ROCM_PATH (or HIP_PATH) to its root.");
        }
    }

    /** {@code hipsparseStatus_t} values the wrappers below distinguish. */
    private static final int HIPSPARSE_STATUS_SUCCESS = 0;
    private static final int HIPSPARSE_STATUS_INTERNAL_ERROR = 7;

    /** Fixed descriptor properties: 32-bit indices, zero-based, FP32 values, row-major dense. */
    private static final int HIPSPARSE_INDEX_32I = 2;
    private static final int HIPSPARSE_INDEX_BASE_ZERO = 0;
    private static final int HIP_R_32F = 0;
    private static final int HIPSPARSE_ORDER_ROW = 2;
    private static final int HIPSPARSE_OPERATION_NON_TRANSPOSE = 0;
    /**
     * {@code HIPSPARSE_SPMV_CSR_ALG2}: the CSR algorithm without an analysis step. The default one
     * analyses the matrix on first use, which fails inside a stream capture.
     */
    private static final int HIPSPARSE_SPMV_ALG = 3;
    private static final int HIPSPARSE_SPMM_ALG_DEFAULT = 0;

    private static final SymbolLookup LIBHIPSPARSE = ROCmLibraries.load(List.of("libhipsparse.so.4", "libhipsparse.so.1", "libhipsparse.so"), List.of("hipsparse.dll", "libhipsparse.dll"));

    /** The scratch allocation goes through the HIP runtime, as hipSPARSE itself does. */
    private static final SymbolLookup LIBHIP = ROCmLibraries.load(List.of("libamdhip64.so.7", "libamdhip64.so.6", "libamdhip64.so"), List.of("amdhip64_7.dll", "amdhip64_6.dll"));

    private static final MethodHandle CREATE;
    private static final MethodHandle DESTROY;
    private static final MethodHandle SET_STREAM;
    private static final MethodHandle CREATE_CSR;
    private static final MethodHandle DESTROY_SP_MAT;
    private static final MethodHandle CREATE_DN_VEC;
    private static final MethodHandle DESTROY_DN_VEC;
    private static final MethodHandle CREATE_DN_MAT;
    private static final MethodHandle DESTROY_DN_MAT;
    private static final MethodHandle SPMV_BUFFER_SIZE;
    private static final MethodHandle SPMV;
    private static final MethodHandle SPMM_BUFFER_SIZE;
    private static final MethodHandle SPMM;
    private static final MethodHandle HIP_MALLOC;
    private static final MethodHandle HIP_FREE;

    static {
        if (LIBHIPSPARSE == null) {
            CREATE = null;
            DESTROY = null;
            SET_STREAM = null;
            CREATE_CSR = null;
            DESTROY_SP_MAT = null;
            CREATE_DN_VEC = null;
            DESTROY_DN_VEC = null;
            CREATE_DN_MAT = null;
            DESTROY_DN_MAT = null;
            SPMV_BUFFER_SIZE = null;
            SPMV = null;
            SPMM_BUFFER_SIZE = null;
            SPMM = null;
        } else {
            CREATE = FFMSupport.downcall(LIBHIPSPARSE, FunctionDescriptor.of(C_INT, C_POINTER), "hipsparseCreate");
            DESTROY = FFMSupport.downcall(LIBHIPSPARSE, FunctionDescriptor.of(C_INT, C_LONG), "hipsparseDestroy");
            SET_STREAM = FFMSupport.downcall(LIBHIPSPARSE, FunctionDescriptor.of(C_INT, C_LONG, C_LONG), "hipsparseSetStream");
            CREATE_CSR = FFMSupport.downcall(LIBHIPSPARSE, FunctionDescriptor.of(C_INT, C_POINTER, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_INT, C_INT, C_INT, C_INT), "hipsparseCreateCsr");
            DESTROY_SP_MAT = FFMSupport.downcall(LIBHIPSPARSE, FunctionDescriptor.of(C_INT, C_LONG), "hipsparseDestroySpMat");
            CREATE_DN_VEC = FFMSupport.downcall(LIBHIPSPARSE, FunctionDescriptor.of(C_INT, C_POINTER, C_LONG, C_LONG, C_INT), "hipsparseCreateDnVec");
            DESTROY_DN_VEC = FFMSupport.downcall(LIBHIPSPARSE, FunctionDescriptor.of(C_INT, C_LONG), "hipsparseDestroyDnVec");
            CREATE_DN_MAT = FFMSupport.downcall(LIBHIPSPARSE, FunctionDescriptor.of(C_INT, C_POINTER, C_LONG, C_LONG, C_LONG, C_LONG, C_INT, C_INT), "hipsparseCreateDnMat");
            DESTROY_DN_MAT = FFMSupport.downcall(LIBHIPSPARSE, FunctionDescriptor.of(C_INT, C_LONG), "hipsparseDestroyDnMat");
            SPMV_BUFFER_SIZE = FFMSupport.downcall(LIBHIPSPARSE, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_POINTER, C_LONG, C_LONG, C_POINTER, C_LONG, C_INT, C_INT, C_POINTER),
                    "hipsparseSpMV_bufferSize");
            SPMV = FFMSupport.downcall(LIBHIPSPARSE, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_POINTER, C_LONG, C_LONG, C_POINTER, C_LONG, C_INT, C_INT, C_LONG), "hipsparseSpMV");
            SPMM_BUFFER_SIZE = FFMSupport.downcall(LIBHIPSPARSE, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_POINTER, C_LONG, C_LONG, C_POINTER, C_LONG, C_INT, C_INT, C_POINTER),
                    "hipsparseSpMM_bufferSize");
            SPMM = FFMSupport.downcall(LIBHIPSPARSE, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_POINTER, C_LONG, C_LONG, C_POINTER, C_LONG, C_INT, C_INT, C_LONG), "hipsparseSpMM");
        }
        if (LIBHIP == null) {
            HIP_MALLOC = null;
            HIP_FREE = null;
        } else {
            HIP_MALLOC = FFMSupport.downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_POINTER, C_LONG), "hipMalloc");
            HIP_FREE = FFMSupport.downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG), "hipFree");
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

    static long hipsparseCreateHandle() {
        if (CREATE == null) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handle = FFMSupport.allocatePointer(arena);
            if ((int) CREATE.invokeExact(handle) != HIPSPARSE_STATUS_SUCCESS) {
                return 0;
            }
            return handle.get(C_POINTER, 0).address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int hipsparseSetStreamNative(long handle, long stream) {
        try {
            return (int) SET_STREAM.invokeExact(handle, stream);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void hipsparseDestroyHandle(long handle) {
        if (handle == 0 || DESTROY == null) {
            return;
        }
        try {
            int ignored = (int) DESTROY.invokeExact(handle);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Builds the CSR descriptor the SpMV and SpMM paths share; 0 when the library refuses it. */
    private static long createCsr(Arena arena, int rows, int cols, int nnz, long dRow, long dCol, long dVal) throws Throwable {
        MemorySegment out = FFMSupport.allocatePointer(arena);
        int status = (int) CREATE_CSR.invokeExact(out, (long) rows, (long) cols, (long) nnz, dRow, dCol, dVal, HIPSPARSE_INDEX_32I, HIPSPARSE_INDEX_32I, HIPSPARSE_INDEX_BASE_ZERO, HIP_R_32F);
        return status == HIPSPARSE_STATUS_SUCCESS ? out.get(C_POINTER, 0).address() : 0;
    }

    private static long createDnVec(Arena arena, int size, long values) throws Throwable {
        MemorySegment out = FFMSupport.allocatePointer(arena);
        int status = (int) CREATE_DN_VEC.invokeExact(out, (long) size, values, HIP_R_32F);
        return status == HIPSPARSE_STATUS_SUCCESS ? out.get(C_POINTER, 0).address() : 0;
    }

    private static long createDnMat(Arena arena, int rows, int cols, int ld, long values) throws Throwable {
        MemorySegment out = FFMSupport.allocatePointer(arena);
        int status = (int) CREATE_DN_MAT.invokeExact(out, (long) rows, (long) cols, (long) ld, values, HIP_R_32F, HIPSPARSE_ORDER_ROW);
        return status == HIPSPARSE_STATUS_SUCCESS ? out.get(C_POINTER, 0).address() : 0;
    }

    private static void destroy(MethodHandle destructor, long descriptor) {
        if (descriptor == 0) {
            return;
        }
        try {
            int ignored = (int) destructor.invokeExact(descriptor);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static MemorySegment scalar(Arena arena, float value) {
        MemorySegment segment = arena.allocate(Float.BYTES, Float.BYTES);
        segment.set(FFMSupport.C_FLOAT, 0, value);
        return segment;
    }

    /**
     * The descriptors of one SpMV ({@code {A, x, y}}) or SpMM ({@code {A, B, C}}) operand set, or null
     * when the library refuses one. rocSPARSE synchronizes the device when a sparse-matrix descriptor
     * is destroyed, which is not allowed while a stream is being captured into a graph, so the
     * provider keeps them for the life of its context instead of rebuilding them on every call as the
     * cuSPARSE path does.
     */
    static long[] spmvDescriptors(int rows, int cols, int nnz, long dRow, long dCol, long dVal, long dX, long dY) {
        if (CREATE_CSR == null) {
            return null;
        }
        try (Arena arena = Arena.ofConfined()) {
            long a = createCsr(arena, rows, cols, nnz, dRow, dCol, dVal);
            long x = a == 0 ? 0 : createDnVec(arena, cols, dX);
            long y = x == 0 ? 0 : createDnVec(arena, rows, dY);
            long[] descriptors = { a, x, y };
            if (y == 0) {
                destroyDescriptors(descriptors, false);
                return null;
            }
            return descriptors;
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static long[] spmmDescriptors(int rows, int k, int n, int nnz, long dRow, long dCol, long dVal, long dB, long dC) {
        if (CREATE_CSR == null) {
            return null;
        }
        try (Arena arena = Arena.ofConfined()) {
            long a = createCsr(arena, rows, k, nnz, dRow, dCol, dVal);
            long b = a == 0 ? 0 : createDnMat(arena, k, n, n, dB);
            long c = b == 0 ? 0 : createDnMat(arena, rows, n, n, dC);
            long[] descriptors = { a, b, c };
            if (c == 0) {
                destroyDescriptors(descriptors, true);
                return null;
            }
            return descriptors;
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Destroys an operand set: dense vectors for SpMV, dense matrices for SpMM. */
    static void destroyDescriptors(long[] descriptors, boolean denseMatrices) {
        MethodHandle dense = denseMatrices ? DESTROY_DN_MAT : DESTROY_DN_VEC;
        destroy(dense, descriptors[2]);
        destroy(dense, descriptors[1]);
        destroy(DESTROY_SP_MAT, descriptors[0]);
    }

    /** Returns the external buffer size in bytes, or -1 on error. */
    static long spmvBufferSize(long handle, long[] d, float alpha, float beta) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment bufferSize = FFMSupport.allocateLong(arena);
            int status = (int) SPMV_BUFFER_SIZE.invokeExact(handle, HIPSPARSE_OPERATION_NON_TRANSPOSE, scalar(arena, alpha), d[0], d[1], scalar(arena, beta), d[2], HIP_R_32F, HIPSPARSE_SPMV_ALG,
                    bufferSize);
            return status == HIPSPARSE_STATUS_SUCCESS ? bufferSize.get(C_LONG, 0) : -1;
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int spmv(long handle, long[] d, float alpha, float beta, long workspace) {
        try (Arena arena = Arena.ofConfined()) {
            return (int) SPMV.invokeExact(handle, HIPSPARSE_OPERATION_NON_TRANSPOSE, scalar(arena, alpha), d[0], d[1], scalar(arena, beta), d[2], HIP_R_32F, HIPSPARSE_SPMV_ALG, workspace);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Returns the external buffer size in bytes, or -1 on error. */
    static long spmmBufferSize(long handle, long[] d, float alpha, float beta) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment bufferSize = FFMSupport.allocateLong(arena);
            int status = (int) SPMM_BUFFER_SIZE.invokeExact(handle, HIPSPARSE_OPERATION_NON_TRANSPOSE, HIPSPARSE_OPERATION_NON_TRANSPOSE, scalar(arena, alpha), d[0], d[1], scalar(arena, beta), d[2],
                    HIP_R_32F, HIPSPARSE_SPMM_ALG_DEFAULT, bufferSize);
            return status == HIPSPARSE_STATUS_SUCCESS ? bufferSize.get(C_LONG, 0) : -1;
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int spmm(long handle, long[] d, float alpha, float beta, long workspace) {
        try (Arena arena = Arena.ofConfined()) {
            return (int) SPMM.invokeExact(handle, HIPSPARSE_OPERATION_NON_TRANSPOSE, HIPSPARSE_OPERATION_NON_TRANSPOSE, scalar(arena, alpha), d[0], d[1], scalar(arena, beta), d[2], HIP_R_32F,
                    HIPSPARSE_SPMM_ALG_DEFAULT, workspace);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static long allocateDeviceMemory(long bytes) {
        if (HIP_MALLOC == null) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pointer = FFMSupport.allocatePointer(arena);
            if ((int) HIP_MALLOC.invokeExact(pointer, bytes) != 0) {
                return 0;
            }
            return pointer.get(C_POINTER, 0).address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int freeDeviceMemory(long ptr) {
        if (ptr == 0 || HIP_FREE == null) {
            return 0;
        }
        try {
            return (int) HIP_FREE.invokeExact(ptr);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** The {@code hipsparseStatus_t} names. */
    static String statusString(int status) {
        return switch (status) {
            case 0 -> "HIPSPARSE_STATUS_SUCCESS";
            case 1 -> "HIPSPARSE_STATUS_NOT_INITIALIZED";
            case 2 -> "HIPSPARSE_STATUS_ALLOC_FAILED";
            case 3 -> "HIPSPARSE_STATUS_INVALID_VALUE";
            case 4 -> "HIPSPARSE_STATUS_ARCH_MISMATCH";
            case 5 -> "HIPSPARSE_STATUS_MAPPING_ERROR";
            case 6 -> "HIPSPARSE_STATUS_EXECUTION_FAILED";
            case 7 -> "HIPSPARSE_STATUS_INTERNAL_ERROR";
            case 8 -> "HIPSPARSE_STATUS_MATRIX_TYPE_NOT_SUPPORTED";
            case 9 -> "HIPSPARSE_STATUS_ZERO_PIVOT";
            case 10 -> "HIPSPARSE_STATUS_NOT_SUPPORTED";
            case 11 -> "HIPSPARSE_STATUS_INSUFFICIENT_RESOURCES";
            default -> "HIPSPARSE_STATUS_UNKNOWN(" + status + ")";
        };
    }

    static void checkStatus(int status, String function) {
        if (status != 0) {
            throw new TornadoRuntimeException("[ERROR] " + function + " failed with hipSPARSE status: " + statusString(status));
        }
    }
}
