/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
import uk.ac.manchester.tornado.runtime.ffm.ROCmLibraries;

/**
 * Panama bindings to hipBLASLt, the ROCm counterpart of {@link CuBlasLtNativeLib} for HIP devices: the
 * same matmul plans (descriptors, layouts and a heuristic algorithm, cached per shape) over the hipBLASLt
 * entry points, whose descriptor attribute numbers and result sizes differ from cuBLASLt's.
 */
final class HipBlasLtNativeLib {

    /** {@code hipblasStatus_t} values the plan path distinguishes. */
    private static final int HIPBLAS_STATUS_SUCCESS = 0;
    private static final int HIPBLAS_STATUS_NOT_SUPPORTED = 7;

    /** {@code HIPBLAS_OP_N}. */
    private static final int HIPBLAS_OP_N = 111;

    /** {@code hipblasLtMatmulDescAttributes_t}. */
    private static final int HIPBLASLT_MATMUL_DESC_TRANSA = 0;
    private static final int HIPBLASLT_MATMUL_DESC_TRANSB = 1;
    private static final int HIPBLASLT_MATMUL_DESC_EPILOGUE = 2;
    private static final int HIPBLASLT_MATMUL_DESC_BIAS_POINTER = 3;

    /** {@code HIPBLASLT_MATMUL_PREF_MAX_WORKSPACE_BYTES}. */
    private static final int HIPBLASLT_MATMUL_PREF_MAX_WORKSPACE_BYTES = 1;

    /** {@code hipblasLtMatmulHeuristicResult_t} (56 bytes) and the {@code hipblasLtMatmulAlgo_t} at its start (24). */
    private static final int HEURISTIC_RESULT_BYTES = 56;
    private static final int MATMUL_ALGO_BYTES = 24;

    private static final SymbolLookup LIBHIPBLASLT = ROCmLibraries.load(List.of("libhipblaslt.so.1", "libhipblaslt.so.0", "libhipblaslt.so"), List.of("hipblaslt.dll", "libhipblaslt.dll"));

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
        if (LIBHIPBLASLT == null) {
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
            LT_CREATE = FFMSupport.downcall(LIBHIPBLASLT, FunctionDescriptor.of(C_INT, C_POINTER), "hipblasLtCreate");
            LT_DESTROY = FFMSupport.downcall(LIBHIPBLASLT, FunctionDescriptor.of(C_INT, C_LONG), "hipblasLtDestroy");
            MATMUL_DESC_CREATE = FFMSupport.downcall(LIBHIPBLASLT, FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_INT), "hipblasLtMatmulDescCreate");
            MATMUL_DESC_DESTROY = FFMSupport.downcall(LIBHIPBLASLT, FunctionDescriptor.of(C_INT, C_LONG), "hipblasLtMatmulDescDestroy");
            MATMUL_DESC_SET_ATTRIBUTE = FFMSupport.downcall(LIBHIPBLASLT, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_POINTER, C_LONG), "hipblasLtMatmulDescSetAttribute");
            MATRIX_LAYOUT_CREATE = FFMSupport.downcall(LIBHIPBLASLT, FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_LONG, C_LONG, C_LONG), "hipblasLtMatrixLayoutCreate");
            MATRIX_LAYOUT_DESTROY = FFMSupport.downcall(LIBHIPBLASLT, FunctionDescriptor.of(C_INT, C_LONG), "hipblasLtMatrixLayoutDestroy");
            PREFERENCE_CREATE = FFMSupport.downcall(LIBHIPBLASLT, FunctionDescriptor.of(C_INT, C_POINTER), "hipblasLtMatmulPreferenceCreate");
            PREFERENCE_DESTROY = FFMSupport.downcall(LIBHIPBLASLT, FunctionDescriptor.of(C_INT, C_LONG), "hipblasLtMatmulPreferenceDestroy");
            PREFERENCE_SET_ATTRIBUTE = FFMSupport.downcall(LIBHIPBLASLT, FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_POINTER, C_LONG), "hipblasLtMatmulPreferenceSetAttribute");
            ALGO_GET_HEURISTIC = FFMSupport.downcall(LIBHIPBLASLT,
                    FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_INT, C_POINTER, C_POINTER), "hipblasLtMatmulAlgoGetHeuristic");
            MATMUL = FFMSupport.downcall(LIBHIPBLASLT, FunctionDescriptor.of(C_INT, C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG, C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG, C_LONG, C_LONG, C_POINTER,
                    C_LONG, C_LONG, C_LONG), "hipblasLtMatmul");
        }
    }

    /** The descriptors one plan owns, plus the algorithm the heuristic picked for it. */
    private static final class Plan {

        private final long matmulDesc;
        private final long aLayout;
        private final long bLayout;
        private final long cLayout;
        /** The chosen {@code hipblasLtMatmulAlgo_t}. */
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

    private HipBlasLtNativeLib() {
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

    /** Whether hipBLASLt is installed: it ships separately from hipBLAS. */
    static boolean isAvailable() {
        return LT_CREATE != null && MATMUL != null;
    }

    /** Fails with a clear message when hipBLASLt cannot be reached. */
    static void load() {
        if (!isAvailable()) {
            throw new TornadoRuntimeException("[ERROR] Unable to load hipBLASLt. Install ROCm with hipBLASLt and set ROCM_PATH (or HIP_PATH) to its root.");
        }
    }

    static long ltCreate() {
        if (LT_CREATE == null) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handle = FFMSupport.allocatePointer(arena);
            if ((int) LT_CREATE.invokeExact(handle) != HIPBLAS_STATUS_SUCCESS) {
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
            if ((int) MATMUL_DESC_CREATE.invokeExact(out, computeType, scaleType) != HIPBLAS_STATUS_SUCCESS) {
                arena.close();
                return 0;
            }
            long matmulDesc = out.get(C_POINTER, 0).address();

            setDescriptorInt(arena, matmulDesc, HIPBLASLT_MATMUL_DESC_TRANSA, transa);
            setDescriptorInt(arena, matmulDesc, HIPBLASLT_MATMUL_DESC_TRANSB, transb);
            setDescriptorInt(arena, matmulDesc, HIPBLASLT_MATMUL_DESC_EPILOGUE, epilogue);

            // Column-major layouts: A is (m x k) for OP_N or (k x m) for OP_T, and so on.
            int aRows = transa == HIPBLAS_OP_N ? m : k;
            int aCols = transa == HIPBLAS_OP_N ? k : m;
            int bRows = transb == HIPBLAS_OP_N ? k : n;
            int bCols = transb == HIPBLAS_OP_N ? n : k;
            long aLayout = createLayout(arena, aType, aRows, aCols, lda);
            long bLayout = createLayout(arena, bType, bRows, bCols, ldb);
            long cLayout = createLayout(arena, cType, m, n, ldc);

            MemorySegment heuristic = arena.allocate(HEURISTIC_RESULT_BYTES, 8);
            heuristic.fill((byte) 0);
            MemorySegment returned = FFMSupport.allocateInt(arena);
            int status;
            long preference = 0;
            MemorySegment preferenceOut = FFMSupport.allocatePointer(arena);
            if ((int) PREFERENCE_CREATE.invokeExact(preferenceOut) == HIPBLAS_STATUS_SUCCESS) {
                preference = preferenceOut.get(C_POINTER, 0).address();
                MemorySegment maxWorkspace = arena.allocate(Long.BYTES, Long.BYTES);
                maxWorkspace.set(C_LONG, 0, workspaceBytes);
                int ignored = (int) PREFERENCE_SET_ATTRIBUTE.invokeExact(preference, HIPBLASLT_MATMUL_PREF_MAX_WORKSPACE_BYTES, maxWorkspace, (long) Long.BYTES);
            }
            status = (int) ALGO_GET_HEURISTIC.invokeExact(handle, matmulDesc, aLayout, bLayout, cLayout, cLayout, preference, 1, heuristic, returned);
            if (preference != 0) {
                int ignored = (int) PREFERENCE_DESTROY.invokeExact(preference);
            }

            int returnedResults = returned.get(C_INT, 0);
            if (status != HIPBLAS_STATUS_SUCCESS || returnedResults == 0) {
                // The caller only sees a null plan, so report why here: status 7
                // (HIPBLAS_STATUS_NOT_SUPPORTED) or zero results means this hipBLASLt has no kernel for
                // the requested types on this GPU (OCP FP8 on gfx942, say); other statuses point at the
                // descriptor or the layouts.
                System.err.println("[TornadoVM-hipBLASLt] plan creation failed: hipblasLtMatmulAlgoGetHeuristic status=" + status + " returnedResults=" + returnedResults + " (aType=" + aType
                        + " bType=" + bType + " cType=" + cType + " m=" + m + " n=" + n + " k=" + k + ")");
                destroyDescriptors(matmulDesc, aLayout, bLayout, cLayout);
                arena.close();
                return 0;
            }

            // The algorithm sits at offset zero of the heuristic result and is only ever handed
            // back to hipBLASLt by pointer, so it is kept as the opaque blob it is.
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
        if ((int) MATRIX_LAYOUT_CREATE.invokeExact(out, type, (long) rows, (long) cols, (long) ld) != HIPBLAS_STATUS_SUCCESS) {
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
            return HIPBLAS_STATUS_NOT_SUPPORTED;
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
                int status = (int) MATMUL_DESC_SET_ATTRIBUTE.invokeExact(plan.matmulDesc, HIPBLASLT_MATMUL_DESC_BIAS_POINTER, bias, (long) Long.BYTES);
                if (status != HIPBLAS_STATUS_SUCCESS) {
                    return status;
                }
            }
            int status = (int) MATMUL.invokeExact(handle, plan.matmulDesc, alphaSlot, dA, plan.aLayout, dB, plan.bLayout, betaSlot, dC, plan.cLayout, dC, plan.cLayout, plan.algo, workspacePtr,
                    workspaceBytes, streamPtr);
            // As with cuBLASLt, retry once with no preselected algorithm if the heuristic's choice is
            // rejected for the same descriptors.
            if (status == HIPBLAS_STATUS_NOT_SUPPORTED) {
                status = (int) MATMUL.invokeExact(handle, plan.matmulDesc, alphaSlot, dA, plan.aLayout, dB, plan.bLayout, betaSlot, dC, plan.cLayout, dC, plan.cLayout, MemorySegment.NULL,
                        workspacePtr, workspaceBytes, streamPtr);
            }
            return status;
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }
}
