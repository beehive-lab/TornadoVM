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

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;

/**
 * JNI bindings to libtornado-cusparse (NVIDIA cuSPARSE generic-API sparse BLAS,
 * CSR / FP32). Descriptors reference device pointers so they are built per call;
 * the small external workspace is allocated on the Java side. Status codes are
 * {@code cusparseStatus_t} ordinals ({@code 0 == CUSPARSE_STATUS_SUCCESS}).
 */
final class CusparseNativeLib {

    private static boolean loaded = false;

    private CusparseNativeLib() {
    }

    /**
     * Checks that cuSPARSE is reachable. There is no TornadoVM library to load any more -- the
     * bindings open cuSPARSE itself -- so this only reports whether the toolkit is installed.
     */
    static synchronized void load() {
        if (LIBCUSPARSE == null || CREATE == null) {
            throw new TornadoRuntimeException("[ERROR] Unable to load cuSPARSE. Install the CUDA Toolkit and make sure libcusparse is on the library path.");
        }
    }

    /** {@code cusparseStatus_t} values the wrappers below distinguish. */
    private static final int CUSPARSE_STATUS_SUCCESS = 0;
    private static final int CUSPARSE_STATUS_INTERNAL_ERROR = 7;

    /** Fixed descriptor properties: 32-bit indices, zero-based, FP32 values, row-major dense. */
    private static final int CUSPARSE_INDEX_32I = 2;
    private static final int CUSPARSE_INDEX_BASE_ZERO = 0;
    private static final int CUDA_R_32F = 0;
    private static final int CUSPARSE_ORDER_ROW = 2;
    private static final int CUSPARSE_OPERATION_NON_TRANSPOSE = 0;
    private static final int CUSPARSE_SPMV_ALG_DEFAULT = 0;
    private static final int CUSPARSE_SPMM_ALG_DEFAULT = 0;

    private static final SymbolLookup LIBCUSPARSE = FFMSupport.loadLibrary("libcusparse.so.12", "libcusparse.so.11", "libcusparse.so", "cusparse64_12.dll", "libcusparse.dylib");

    /** The scratch allocation goes through the CUDA runtime, as cuSPARSE itself does. */
    private static final SymbolLookup LIBCUDART = FFMSupport.loadLibrary("libcudart.so.12", "libcudart.so.11.0", "libcudart.so", "cudart64_12.dll", "libcudart.dylib");

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
    private static final MethodHandle CUDA_MALLOC;
    private static final MethodHandle CUDA_FREE;

    static {
        if (LIBCUSPARSE == null) {
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
            CREATE = FFMSupport.downcall(LIBCUSPARSE, FunctionDescriptor.of(C_INT, C_POINTER), "cusparseCreate");
            DESTROY = FFMSupport.downcall(LIBCUSPARSE, FunctionDescriptor.of(C_INT, C_LONG), "cusparseDestroy");
            SET_STREAM = FFMSupport.downcall(LIBCUSPARSE, FunctionDescriptor.of(C_INT, C_LONG, C_LONG), "cusparseSetStream");
            CREATE_CSR = FFMSupport.downcall(LIBCUSPARSE, FunctionDescriptor.of(C_INT, C_POINTER, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_INT, C_INT, C_INT, C_INT), "cusparseCreateCsr");
            DESTROY_SP_MAT = FFMSupport.downcall(LIBCUSPARSE, FunctionDescriptor.of(C_INT, C_LONG), "cusparseDestroySpMat");
            CREATE_DN_VEC = FFMSupport.downcall(LIBCUSPARSE, FunctionDescriptor.of(C_INT, C_POINTER, C_LONG, C_LONG, C_INT), "cusparseCreateDnVec");
            DESTROY_DN_VEC = FFMSupport.downcall(LIBCUSPARSE, FunctionDescriptor.of(C_INT, C_LONG), "cusparseDestroyDnVec");
            CREATE_DN_MAT = FFMSupport.downcall(LIBCUSPARSE, FunctionDescriptor.of(C_INT, C_POINTER, C_LONG, C_LONG, C_LONG, C_LONG, C_INT, C_INT), "cusparseCreateDnMat");
            DESTROY_DN_MAT = FFMSupport.downcall(LIBCUSPARSE, FunctionDescriptor.of(C_INT, C_LONG), "cusparseDestroyDnMat");
            SPMV_BUFFER_SIZE = FFMSupport.downcall(LIBCUSPARSE, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_POINTER, C_LONG, C_LONG, C_POINTER, C_LONG, C_INT, C_INT, C_POINTER),
                    "cusparseSpMV_bufferSize");
            SPMV = FFMSupport.downcall(LIBCUSPARSE, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_POINTER, C_LONG, C_LONG, C_POINTER, C_LONG, C_INT, C_INT, C_LONG), "cusparseSpMV");
            SPMM_BUFFER_SIZE = FFMSupport.downcall(LIBCUSPARSE, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_POINTER, C_LONG, C_LONG, C_POINTER, C_LONG, C_INT, C_INT, C_POINTER),
                    "cusparseSpMM_bufferSize");
            SPMM = FFMSupport.downcall(LIBCUSPARSE, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_POINTER, C_LONG, C_LONG, C_POINTER, C_LONG, C_INT, C_INT, C_LONG), "cusparseSpMM");
        }
        if (LIBCUDART == null) {
            CUDA_MALLOC = null;
            CUDA_FREE = null;
        } else {
            CUDA_MALLOC = FFMSupport.downcall(LIBCUDART, FunctionDescriptor.of(C_INT, C_POINTER, C_LONG), "cudaMalloc");
            CUDA_FREE = FFMSupport.downcall(LIBCUDART, FunctionDescriptor.of(C_INT, C_LONG), "cudaFree");
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

    static long cusparseCreateHandle() {
        if (CREATE == null) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handle = FFMSupport.allocatePointer(arena);
            if ((int) CREATE.invokeExact(handle) != CUSPARSE_STATUS_SUCCESS) {
                return 0;
            }
            return handle.get(C_POINTER, 0).address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int cusparseSetStreamNative(long handle, long stream) {
        try {
            return (int) SET_STREAM.invokeExact(handle, stream);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void cusparseDestroyHandle(long handle) {
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
        int status = (int) CREATE_CSR.invokeExact(out, (long) rows, (long) cols, (long) nnz, dRow, dCol, dVal, CUSPARSE_INDEX_32I, CUSPARSE_INDEX_32I, CUSPARSE_INDEX_BASE_ZERO, CUDA_R_32F);
        return status == CUSPARSE_STATUS_SUCCESS ? out.get(C_POINTER, 0).address() : 0;
    }

    private static long createDnVec(Arena arena, int size, long values) throws Throwable {
        MemorySegment out = FFMSupport.allocatePointer(arena);
        int status = (int) CREATE_DN_VEC.invokeExact(out, (long) size, values, CUDA_R_32F);
        return status == CUSPARSE_STATUS_SUCCESS ? out.get(C_POINTER, 0).address() : 0;
    }

    private static long createDnMat(Arena arena, int rows, int cols, int ld, long values) throws Throwable {
        MemorySegment out = FFMSupport.allocatePointer(arena);
        int status = (int) CREATE_DN_MAT.invokeExact(out, (long) rows, (long) cols, (long) ld, values, CUDA_R_32F, CUSPARSE_ORDER_ROW);
        return status == CUSPARSE_STATUS_SUCCESS ? out.get(C_POINTER, 0).address() : 0;
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

    /** Returns the external buffer size in bytes, or -1 on error. */
    static long spmvBufferSize(long handle, int rows, int cols, int nnz, long dRow, long dCol, long dVal, long dX, long dY, float alpha, float beta) {
        if (CREATE_CSR == null) {
            return -1;
        }
        long a = 0;
        long x = 0;
        long y = 0;
        try (Arena arena = Arena.ofConfined()) {
            a = createCsr(arena, rows, cols, nnz, dRow, dCol, dVal);
            x = a == 0 ? 0 : createDnVec(arena, cols, dX);
            y = x == 0 ? 0 : createDnVec(arena, rows, dY);
            if (y == 0) {
                return -1;
            }
            MemorySegment bufferSize = FFMSupport.allocateLong(arena);
            int status = (int) SPMV_BUFFER_SIZE.invokeExact(handle, CUSPARSE_OPERATION_NON_TRANSPOSE, scalar(arena, alpha), a, x, scalar(arena, beta), y, CUDA_R_32F, CUSPARSE_SPMV_ALG_DEFAULT,
                    bufferSize);
            return status == CUSPARSE_STATUS_SUCCESS ? bufferSize.get(C_LONG, 0) : -1;
        } catch (Throwable t) {
            throw rethrow(t);
        } finally {
            destroy(DESTROY_DN_VEC, y);
            destroy(DESTROY_DN_VEC, x);
            destroy(DESTROY_SP_MAT, a);
        }
    }

    static int spmv(long handle, int rows, int cols, int nnz, long dRow, long dCol, long dVal, long dX, long dY, float alpha, float beta, long workspace) {
        if (CREATE_CSR == null) {
            return CUSPARSE_STATUS_INTERNAL_ERROR;
        }
        long a = 0;
        long x = 0;
        long y = 0;
        try (Arena arena = Arena.ofConfined()) {
            a = createCsr(arena, rows, cols, nnz, dRow, dCol, dVal);
            x = a == 0 ? 0 : createDnVec(arena, cols, dX);
            y = x == 0 ? 0 : createDnVec(arena, rows, dY);
            if (y == 0) {
                return CUSPARSE_STATUS_INTERNAL_ERROR;
            }
            return (int) SPMV.invokeExact(handle, CUSPARSE_OPERATION_NON_TRANSPOSE, scalar(arena, alpha), a, x, scalar(arena, beta), y, CUDA_R_32F, CUSPARSE_SPMV_ALG_DEFAULT, workspace);
        } catch (Throwable t) {
            throw rethrow(t);
        } finally {
            destroy(DESTROY_DN_VEC, y);
            destroy(DESTROY_DN_VEC, x);
            destroy(DESTROY_SP_MAT, a);
        }
    }

    /** Returns the external buffer size in bytes, or -1 on error. */
    static long spmmBufferSize(long handle, int rows, int k, int n, int nnz, long dRow, long dCol, long dVal, long dB, long dC, float alpha, float beta) {
        if (CREATE_CSR == null) {
            return -1;
        }
        long a = 0;
        long b = 0;
        long c = 0;
        try (Arena arena = Arena.ofConfined()) {
            a = createCsr(arena, rows, k, nnz, dRow, dCol, dVal);
            b = a == 0 ? 0 : createDnMat(arena, k, n, n, dB);
            c = b == 0 ? 0 : createDnMat(arena, rows, n, n, dC);
            if (c == 0) {
                return -1;
            }
            MemorySegment bufferSize = FFMSupport.allocateLong(arena);
            int status = (int) SPMM_BUFFER_SIZE.invokeExact(handle, CUSPARSE_OPERATION_NON_TRANSPOSE, CUSPARSE_OPERATION_NON_TRANSPOSE, scalar(arena, alpha), a, b, scalar(arena, beta), c, CUDA_R_32F,
                    CUSPARSE_SPMM_ALG_DEFAULT, bufferSize);
            return status == CUSPARSE_STATUS_SUCCESS ? bufferSize.get(C_LONG, 0) : -1;
        } catch (Throwable t) {
            throw rethrow(t);
        } finally {
            destroy(DESTROY_DN_MAT, c);
            destroy(DESTROY_DN_MAT, b);
            destroy(DESTROY_SP_MAT, a);
        }
    }

    static int spmm(long handle, int rows, int k, int n, int nnz, long dRow, long dCol, long dVal, long dB, long dC, float alpha, float beta, long workspace) {
        if (CREATE_CSR == null) {
            return CUSPARSE_STATUS_INTERNAL_ERROR;
        }
        long a = 0;
        long b = 0;
        long c = 0;
        try (Arena arena = Arena.ofConfined()) {
            a = createCsr(arena, rows, k, nnz, dRow, dCol, dVal);
            b = a == 0 ? 0 : createDnMat(arena, k, n, n, dB);
            c = b == 0 ? 0 : createDnMat(arena, rows, n, n, dC);
            if (c == 0) {
                return CUSPARSE_STATUS_INTERNAL_ERROR;
            }
            return (int) SPMM.invokeExact(handle, CUSPARSE_OPERATION_NON_TRANSPOSE, CUSPARSE_OPERATION_NON_TRANSPOSE, scalar(arena, alpha), a, b, scalar(arena, beta), c, CUDA_R_32F,
                    CUSPARSE_SPMM_ALG_DEFAULT, workspace);
        } catch (Throwable t) {
            throw rethrow(t);
        } finally {
            destroy(DESTROY_DN_MAT, c);
            destroy(DESTROY_DN_MAT, b);
            destroy(DESTROY_SP_MAT, a);
        }
    }

    static long allocateDeviceMemory(long bytes) {
        if (CUDA_MALLOC == null) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pointer = FFMSupport.allocatePointer(arena);
            if ((int) CUDA_MALLOC.invokeExact(pointer, bytes) != 0) {
                return 0;
            }
            return pointer.get(C_POINTER, 0).address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static int freeDeviceMemory(long ptr) {
        if (ptr == 0 || CUDA_FREE == null) {
            return 0;
        }
        try {
            return (int) CUDA_FREE.invokeExact(ptr);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** cuSPARSE has no {@code cusparseGetErrorString} before CUDA 11.4, so the names are inline. */
    static String statusString(int status) {
        return switch (status) {
            case 0 -> "CUSPARSE_STATUS_SUCCESS";
            case 1 -> "CUSPARSE_STATUS_NOT_INITIALIZED";
            case 2 -> "CUSPARSE_STATUS_ALLOC_FAILED";
            case 3 -> "CUSPARSE_STATUS_INVALID_VALUE";
            case 4 -> "CUSPARSE_STATUS_ARCH_MISMATCH";
            case 5 -> "CUSPARSE_STATUS_MAPPING_ERROR";
            case 6 -> "CUSPARSE_STATUS_EXECUTION_FAILED";
            case 7 -> "CUSPARSE_STATUS_INTERNAL_ERROR";
            case 8 -> "CUSPARSE_STATUS_MATRIX_TYPE_NOT_SUPPORTED";
            case 9 -> "CUSPARSE_STATUS_ZERO_PIVOT";
            case 10 -> "CUSPARSE_STATUS_NOT_SUPPORTED";
            case 11 -> "CUSPARSE_STATUS_INSUFFICIENT_RESOURCES";
            default -> "CUSPARSE_STATUS_UNKNOWN(" + status + ")";
        };
    }

    static void checkStatus(int status, String function) {
        if (status != 0) {
            throw new TornadoRuntimeException("[ERROR] " + function + " failed with cuSPARSE status: " + statusString(status));
        }
    }
}
