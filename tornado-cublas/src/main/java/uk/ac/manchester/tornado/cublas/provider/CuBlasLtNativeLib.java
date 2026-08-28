/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;

/**
 * cuBLASLt bindings, over {@code java.lang.foreign} rather than a JNI shim.
 *
 * <p>
 * Plan-based design, unchanged from the JNI version it replaces: {@link #ltCreatePlan} builds the
 * cuBLASLt descriptors and matrix layouts and runs the algorithm heuristic once; the Java side
 * caches the resulting plan per problem shape and replays it with {@link #ltExecutePlan}. Scalars
 * are host floats (scale type {@code CUDA_R_32F}), so plans are restricted to the FP32-compute
 * family.
 *
 * <p>
 * The plan used to be a {@code malloc}'d C struct whose address was the handle. Here it is a Java
 * object behind a counter, with one small native segment for the algorithm the heuristic chose --
 * an opaque 64-byte blob that cuBLASLt only ever takes by pointer, so it never has to be understood
 * on this side.
 */
final class CuBlasLtNativeLib {

    /** {@code cublasStatus_t} values the plan path distinguishes. */
    private static final int CUBLAS_STATUS_SUCCESS = 0;
    private static final int CUBLAS_STATUS_NOT_SUPPORTED = 15;

    /** {@code CUBLAS_OP_N}. */
    private static final int CUBLAS_OP_N = 0;

    /** {@code cublasLtMatmulDescAttributes_t} values used here. */
    private static final int CUBLASLT_MATMUL_DESC_TRANSA = 3;
    private static final int CUBLASLT_MATMUL_DESC_TRANSB = 4;
    private static final int CUBLASLT_MATMUL_DESC_EPILOGUE = 7;
    private static final int CUBLASLT_MATMUL_DESC_BIAS_POINTER = 8;

    /** {@code CUBLASLT_MATMUL_PREF_MAX_WORKSPACE_BYTES}. */
    private static final int CUBLASLT_MATMUL_PREF_MAX_WORKSPACE_BYTES = 1;

    /**
     * {@code cublasLtMatmulHeuristicResult_t}: a 64-byte {@code cublasLtMatmulAlgo_t} at offset
     * zero, then the workspace size, state, wave count and reserved words. Only the algorithm is
     * read back, and only ever as a pointer, so the rest is just room for the library to write.
     */
    private static final int HEURISTIC_RESULT_BYTES = 96;
    private static final int MATMUL_ALGO_BYTES = 64;

    private static final SymbolLookup LIBCUBLASLT = FFMSupport.loadLibrary("libcublasLt.so.12", "libcublasLt.so.11", "libcublasLt.so", "cublasLt64_12.dll", "libcublasLt.dylib");

    /** Scratch for the host-side alpha and beta scalars every matmul passes by pointer. */
    private static final FFMSupport.Staging SCALARS = new FFMSupport.Staging();

    private static final MethodHandle LT_CREATE;
    private static final MethodHandle LT_DESTROY;
    private static final MethodHandle MATMUL_DESC_CREATE;
    private static final MethodHandle MATMUL_DESC_DESTROY;
    private static final MethodHandle MATMUL_DESC_SET_ATTRIBUTE;
    private static final MethodHandle MATRIX_LAYOUT_CREATE;
    private static final MethodHandle MATRIX_LAYOUT_DESTROY;
    private static final MethodHandle PREFERENCE_CREATE;
    private static final MethodHandle PREFERENCE_DESTROY;
    private static final MethodHandle PREFERENCE_SET_ATTRIBUTE;
    private static final MethodHandle ALGO_GET_HEURISTIC;
    private static final MethodHandle MATMUL;

    static {
        if (LIBCUBLASLT == null) {
            LT_CREATE = null;
            LT_DESTROY = null;
            MATMUL_DESC_CREATE = null;
            MATMUL_DESC_DESTROY = null;
            MATMUL_DESC_SET_ATTRIBUTE = null;
            MATRIX_LAYOUT_CREATE = null;
            MATRIX_LAYOUT_DESTROY = null;
            PREFERENCE_CREATE = null;
            PREFERENCE_DESTROY = null;
            PREFERENCE_SET_ATTRIBUTE = null;
            ALGO_GET_HEURISTIC = null;
            MATMUL = null;
        } else {
            LT_CREATE = FFMSupport.downcall(LIBCUBLASLT, FunctionDescriptor.of(C_INT, C_POINTER), "cublasLtCreate");
            LT_DESTROY = FFMSupport.downcall(LIBCUBLASLT, FunctionDescriptor.of(C_INT, C_LONG), "cublasLtDestroy");
            MATMUL_DESC_CREATE = FFMSupport.downcall(LIBCUBLASLT, FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_INT), "cublasLtMatmulDescCreate");
            MATMUL_DESC_DESTROY = FFMSupport.downcall(LIBCUBLASLT, FunctionDescriptor.of(C_INT, C_LONG), "cublasLtMatmulDescDestroy");
            MATMUL_DESC_SET_ATTRIBUTE = FFMSupport.downcall(LIBCUBLASLT, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_POINTER, C_LONG), "cublasLtMatmulDescSetAttribute");
            MATRIX_LAYOUT_CREATE = FFMSupport.downcall(LIBCUBLASLT, FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_LONG, C_LONG, C_LONG), "cublasLtMatrixLayoutCreate");
            MATRIX_LAYOUT_DESTROY = FFMSupport.downcall(LIBCUBLASLT, FunctionDescriptor.of(C_INT, C_LONG), "cublasLtMatrixLayoutDestroy");
            PREFERENCE_CREATE = FFMSupport.downcall(LIBCUBLASLT, FunctionDescriptor.of(C_INT, C_POINTER), "cublasLtMatmulPreferenceCreate");
            PREFERENCE_DESTROY = FFMSupport.downcall(LIBCUBLASLT, FunctionDescriptor.of(C_INT, C_LONG), "cublasLtMatmulPreferenceDestroy");
            PREFERENCE_SET_ATTRIBUTE = FFMSupport.downcall(LIBCUBLASLT, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_POINTER, C_LONG), "cublasLtMatmulPreferenceSetAttribute");
            ALGO_GET_HEURISTIC = FFMSupport.downcall(LIBCUBLASLT,
                    FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_INT, C_POINTER, C_POINTER), "cublasLtMatmulAlgoGetHeuristic");
            MATMUL = FFMSupport.downcall(LIBCUBLASLT,
                    FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG, C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG, C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG, C_LONG), "cublasLtMatmul");
        }
    }

    /** The descriptors one plan owns, plus the algorithm the heuristic picked for it. */
    private static final class Plan {

        private final long matmulDesc;
        private final long aLayout;
        private final long bLayout;
        private final long cLayout;
        /** The chosen {@code cublasLtMatmulAlgo_t}, or NULL to let cuBLASLt choose per call. */
        private final MemorySegment algo;
        private final Arena arena;

        private Plan(long matmulDesc, long aLayout, long bLayout, long cLayout, MemorySegment algo, Arena arena) {
            this.matmulDesc = matmulDesc;
            this.aLayout = aLayout;
            this.bLayout = bLayout;
            this.cLayout = cLayout;
            this.algo = algo;
            this.arena = arena;
        }
    }

    private static final AtomicLong NEXT_PLAN = new AtomicLong(1);

    private static final ConcurrentHashMap<Long, Plan> PLANS = new ConcurrentHashMap<>();

    private CuBlasLtNativeLib() {
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

    static long ltCreate() {
        if (LT_CREATE == null) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handle = FFMSupport.allocatePointer(arena);
            if ((int) LT_CREATE.invokeExact(handle) != CUBLAS_STATUS_SUCCESS) {
                return 0;
            }
            return handle.get(C_POINTER, 0).address();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void ltDestroy(long handle) {
        if (handle == 0 || LT_DESTROY == null) {
            return;
        }
        try {
            int ignored = (int) LT_DESTROY.invokeExact(handle);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /**
     * Creates a matmul plan (descriptors plus the heuristic's algorithm). Returns 0 on failure.
     * Scalars are host floats: FP32-family compute types only.
     */
    static long ltCreatePlan(long handle, int transa, int transb, int m, int n, int k, int lda, int ldb, int ldc, int aType, int bType, int cType, int computeType, int scaleType, int epilogue,
            long workspaceBytes) {
        if (MATMUL_DESC_CREATE == null) {
            return 0;
        }
        // Shared, because a plan outlives this call and is replayed until the Java side drops it.
        Arena arena = Arena.ofShared();
        try {
            MemorySegment out = FFMSupport.allocatePointer(arena);
            if ((int) MATMUL_DESC_CREATE.invokeExact(out, computeType, scaleType) != CUBLAS_STATUS_SUCCESS) {
                arena.close();
                return 0;
            }
            long matmulDesc = out.get(C_POINTER, 0).address();

            setDescriptorInt(arena, matmulDesc, CUBLASLT_MATMUL_DESC_TRANSA, transa);
            setDescriptorInt(arena, matmulDesc, CUBLASLT_MATMUL_DESC_TRANSB, transb);
            setDescriptorInt(arena, matmulDesc, CUBLASLT_MATMUL_DESC_EPILOGUE, epilogue);

            // Column-major layouts: A is (m x k) for OP_N or (k x m) for OP_T, and so on.
            int aRows = transa == CUBLAS_OP_N ? m : k;
            int aCols = transa == CUBLAS_OP_N ? k : m;
            int bRows = transb == CUBLAS_OP_N ? k : n;
            int bCols = transb == CUBLAS_OP_N ? n : k;
            long aLayout = createLayout(arena, aType, aRows, aCols, lda);
            long bLayout = createLayout(arena, bType, bRows, bCols, ldb);
            long cLayout = createLayout(arena, cType, m, n, ldc);

            MemorySegment heuristic = arena.allocate(HEURISTIC_RESULT_BYTES, 8);
            heuristic.fill((byte) 0);
            MemorySegment returned = FFMSupport.allocateInt(arena);
            int status;
            long preference = 0;
            MemorySegment preferenceOut = FFMSupport.allocatePointer(arena);
            if ((int) PREFERENCE_CREATE.invokeExact(preferenceOut) == CUBLAS_STATUS_SUCCESS) {
                preference = preferenceOut.get(C_POINTER, 0).address();
                MemorySegment maxWorkspace = arena.allocate(Long.BYTES, Long.BYTES);
                maxWorkspace.set(C_LONG, 0, workspaceBytes);
                int ignored = (int) PREFERENCE_SET_ATTRIBUTE.invokeExact(preference, CUBLASLT_MATMUL_PREF_MAX_WORKSPACE_BYTES, maxWorkspace, (long) Long.BYTES);
            }
            status = (int) ALGO_GET_HEURISTIC.invokeExact(handle, matmulDesc, aLayout, bLayout, cLayout, cLayout, preference, 1, heuristic, returned);
            if (preference != 0) {
                int ignored = (int) PREFERENCE_DESTROY.invokeExact(preference);
            }

            int returnedResults = returned.get(C_INT, 0);
            if (status != CUBLAS_STATUS_SUCCESS || returnedResults == 0) {
                // The caller only sees a null plan, so report why here: status 15
                // (CUBLAS_STATUS_NOT_SUPPORTED) or zero results means this cuBLAS has no kernel for
                // the requested types on this GPU (FP8 on an arch the library predates, say); other
                // statuses point at the descriptor or the layouts.
                System.err.println("[TornadoVM-cuBLASLt] plan creation failed: cublasLtMatmulAlgoGetHeuristic status=" + status + " returnedResults=" + returnedResults + " (aType=" + aType
                        + " bType=" + bType + " cType=" + cType + " m=" + m + " n=" + n + " k=" + k + ")");
                destroyDescriptors(matmulDesc, aLayout, bLayout, cLayout);
                arena.close();
                return 0;
            }

            // The algorithm sits at offset zero of the heuristic result and is only ever handed
            // back to cuBLASLt by pointer, so it is kept as the opaque blob it is.
            MemorySegment algo = heuristic.asSlice(0, MATMUL_ALGO_BYTES);
            long planHandle = NEXT_PLAN.getAndIncrement();
            PLANS.put(planHandle, new Plan(matmulDesc, aLayout, bLayout, cLayout, algo, arena));
            return planHandle;
        } catch (Throwable t) {
            arena.close();
            throw rethrow(t);
        }
    }

    private static void setDescriptorInt(Arena arena, long descriptor, int attribute, int value) throws Throwable {
        MemorySegment slot = arena.allocate(Integer.BYTES, Integer.BYTES);
        slot.set(C_INT, 0, value);
        int ignored = (int) MATMUL_DESC_SET_ATTRIBUTE.invokeExact(descriptor, attribute, slot, (long) Integer.BYTES);
    }

    private static long createLayout(Arena arena, int type, int rows, int cols, int ld) throws Throwable {
        MemorySegment out = FFMSupport.allocatePointer(arena);
        if ((int) MATRIX_LAYOUT_CREATE.invokeExact(out, type, (long) rows, (long) cols, (long) ld) != CUBLAS_STATUS_SUCCESS) {
            return 0;
        }
        return out.get(C_POINTER, 0).address();
    }

    private static void destroyDescriptors(long matmulDesc, long aLayout, long bLayout, long cLayout) {
        try {
            if (matmulDesc != 0) {
                int ignored = (int) MATMUL_DESC_DESTROY.invokeExact(matmulDesc);
            }
            for (long layout : new long[] { aLayout, bLayout, cLayout }) {
                if (layout != 0) {
                    int ignored = (int) MATRIX_LAYOUT_DESTROY.invokeExact(layout);
                }
            }
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void ltDestroyPlan(long planPtr) {
        Plan plan = PLANS.remove(planPtr);
        if (plan == null) {
            return;
        }
        destroyDescriptors(plan.matmulDesc, plan.aLayout, plan.bLayout, plan.cLayout);
        plan.arena.close();
    }

    /**
     * Executes a previously created plan on the given stream. {@code biasPtr} may be 0 for plans
     * without a bias epilogue; when non-zero it is re-attached to the matmul descriptor before the
     * call, the bias buffer being stable per task.
     */
    static int ltExecutePlan(long handle, long planPtr, long streamPtr, float alpha, long dA, long dB, float beta, long dC, long biasPtr, long workspacePtr, long workspaceBytes) {
        Plan plan = PLANS.get(planPtr);
        if (plan == null || MATMUL == null) {
            return CUBLAS_STATUS_NOT_SUPPORTED;
        }
        MemorySegment scalars = SCALARS.forBytes(2L * Float.BYTES);
        scalars.set(FFMSupport.C_FLOAT, 0, alpha);
        scalars.set(FFMSupport.C_FLOAT, Float.BYTES, beta);
        MemorySegment alphaSlot = scalars.asSlice(0, Float.BYTES);
        MemorySegment betaSlot = scalars.asSlice(Float.BYTES, Float.BYTES);
        try (Arena arena = Arena.ofConfined()) {
            if (biasPtr != 0) {
                MemorySegment bias = arena.allocate(Long.BYTES, Long.BYTES);
                bias.set(C_LONG, 0, biasPtr);
                int status = (int) MATMUL_DESC_SET_ATTRIBUTE.invokeExact(plan.matmulDesc, CUBLASLT_MATMUL_DESC_BIAS_POINTER, bias, (long) Long.BYTES);
                if (status != CUBLAS_STATUS_SUCCESS) {
                    return status;
                }
            }
            int status = (int) MATMUL.invokeExact(handle, plan.matmulDesc, alphaSlot, dA, plan.aLayout, dB, plan.bLayout, betaSlot, dC, plan.cLayout, dC, plan.cLayout, plan.algo, workspacePtr,
                    workspaceBytes, streamPtr);
            // The heuristic can hand back an algorithm that cublasLtMatmul then rejects for the same
            // descriptors (seen for FP16 matmul on some GPU/driver combinations). Retry once with no
            // preselected algorithm, letting cuBLASLt choose internally.
            if (status == CUBLAS_STATUS_NOT_SUPPORTED) {
                status = (int) MATMUL.invokeExact(handle, plan.matmulDesc, alphaSlot, dA, plan.aLayout, dB, plan.bLayout, betaSlot, dC, plan.cLayout, dC, plan.cLayout, MemorySegment.NULL,
                        workspacePtr, workspaceBytes, streamPtr);
            }
            return status;
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }
}
