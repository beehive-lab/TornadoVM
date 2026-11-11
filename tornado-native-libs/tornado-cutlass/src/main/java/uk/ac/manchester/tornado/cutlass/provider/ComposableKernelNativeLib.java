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
package uk.ac.manchester.tornado.cutlass.provider;

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
 * Panama bindings to the GEMM kernels of libtornado-ck, which TornadoVM builds from AMD Composable
 * Kernel (tornado-native-libs/ck-hip): the counterpart of {@link CutlassNativeLib} for HIP devices.
 * Each entry point takes row-major device operands and the stream to launch on; called with a
 * non-null query it launches nothing and returns the workspace the problem needs.
 */
final class ComposableKernelNativeLib {

    /** Element types of {@code tornado_ck_gemm}. */
    static final int F32 = 0;
    static final int F16 = 1;
    static final int BF16 = 2;

    /** Activations of {@code tornado_ck_gemm_bias_act}. */
    static final int RELU = 0;
    static final int GELU = 1;
    static final int SILU = 2;
    static final int SIGMOID = 3;
    static final int TANH = 4;
    static final int HARDSWISH = 5;

    /** Return codes. */
    static final int SUCCESS = 0;
    static final int NO_KERNEL = 1;

    private static final SymbolLookup LIBTORNADO_CK = FFMSupport.loadBundledLibrary("libtornado-ck.so", "tornado-ck.dll");

    /** The workspace comes from {@code hipMalloc}. */
    private static final SymbolLookup LIBHIP = ROCmLibraries.load(List.of("libamdhip64.so.7", "libamdhip64.so.6", "libamdhip64.so"), List.of("amdhip64_7.dll", "amdhip64_6.dll"));

    private static final MethodHandle GEMM;
    private static final MethodHandle GEMM_BIAS_ACT;
    private static final MethodHandle HGEMM_BATCHED;
    private static final MethodHandle HIP_MALLOC;
    private static final MethodHandle HIP_FREE;

    static {
        if (LIBTORNADO_CK == null) {
            GEMM = null;
            GEMM_BIAS_ACT = null;
            HGEMM_BATCHED = null;
        } else {
            GEMM = FFMSupport.downcall(LIBTORNADO_CK, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_INT, C_INT, C_FLOAT, C_LONG, C_LONG, C_FLOAT, C_LONG, C_LONG, C_POINTER),
                    "tornado_ck_gemm");
            GEMM_BIAS_ACT = FFMSupport.downcall(LIBTORNADO_CK, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_INT, C_INT, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_POINTER),
                    "tornado_ck_gemm_bias_act");
            HGEMM_BATCHED = FFMSupport.downcall(LIBTORNADO_CK,
                    FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_INT, C_INT, C_FLOAT, C_LONG, C_LONG, C_FLOAT, C_LONG, C_INT, C_LONG, C_POINTER), "tornado_ck_hgemm_batched");
        }
        if (LIBHIP == null) {
            HIP_MALLOC = null;
            HIP_FREE = null;
        } else {
            HIP_MALLOC = FFMSupport.downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_POINTER, C_LONG), "hipMalloc");
            HIP_FREE = FFMSupport.downcall(LIBHIP, FunctionDescriptor.of(C_INT, C_LONG), "hipFree");
        }
    }

    private ComposableKernelNativeLib() {
    }

    static boolean isAvailable() {
        return GEMM != null && HIP_MALLOC != null;
    }

    static void load() {
        if (!isAvailable()) {
            throw new TornadoRuntimeException("[ERROR] Unable to load libtornado-ck, the GEMM kernels TornadoVM builds from Composable Kernel for HIP devices. "
                    + "Build TornadoVM with the HIP backend against ROCm.");
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

    /** The queried workspace size, or the negated return code when the query failed. */
    private static long workspace(int status, MemorySegment query) {
        return status == SUCCESS ? query.get(C_LONG, 0) : -status;
    }

    /** Workspace bytes of {@link #gemm} for this problem on the current device; negative on failure. */
    static long gemmWorkspace(int type, int m, int n, int k, float beta) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment query = FFMSupport.allocateLong(arena);
            return workspace((int) GEMM.invokeExact(0L, type, m, n, k, 1.0f, 0L, 0L, beta, 0L, 0L, query), query);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Row-major C = alpha A B + beta C; beta == 0 does not read C. */
    static int gemm(long stream, int type, int m, int n, int k, float alpha, long a, long b, float beta, long c, long workspace) {
        try {
            return (int) GEMM.invokeExact(stream, type, m, n, k, alpha, a, b, beta, c, workspace, MemorySegment.NULL);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static long gemmBiasActWorkspace(int activation, int m, int n, int k) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment query = FFMSupport.allocateLong(arena);
            return workspace((int) GEMM_BIAS_ACT.invokeExact(0L, activation, m, n, k, 0L, 0L, 0L, 0L, 0L, query), query);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Row-major FP16 D = act(A B + bias), the length-n bias broadcast over the rows. */
    static int gemmBiasAct(long stream, int activation, int m, int n, int k, long a, long b, long bias, long d, long workspace) {
        try {
            return (int) GEMM_BIAS_ACT.invokeExact(stream, activation, m, n, k, a, b, bias, d, workspace, MemorySegment.NULL);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static long hgemmBatchedWorkspace(int m, int n, int k, float beta, int batchCount) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment query = FFMSupport.allocateLong(arena);
            return workspace((int) HGEMM_BATCHED.invokeExact(0L, m, n, k, 1.0f, 0L, 0L, beta, 0L, batchCount, 0L, query), query);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** batchCount packed FP16 GEMMs C_i = alpha A_i B_i + beta C_i (batch strides m k, k n, m n). */
    static int hgemmBatched(long stream, int m, int n, int k, float alpha, long a, long b, float beta, long c, int batchCount, long workspace) {
        try {
            return (int) HGEMM_BATCHED.invokeExact(stream, m, n, k, alpha, a, b, beta, c, batchCount, workspace, MemorySegment.NULL);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static long allocateDeviceMemory(long bytes) {
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

    static void freeDeviceMemory(long ptr) {
        if (ptr == 0) {
            return;
        }
        try {
            int ignored = (int) HIP_FREE.invokeExact(ptr);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static String decodeStatus(int status) {
        return switch (status) {
            case 0 -> "success";
            case 1 -> "no kernel for this problem on this GPU";
            case 2 -> "invalid argument";
            case 3 -> "HIP error";
            default -> "unknown status " + status;
        };
    }
}
