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

/*
 * JNI bindings for uk.ac.manchester.tornado.cublas.provider.CuBlasLtNativeLib.
 *
 * Plan-based design: ltCreatePlan builds the cublasLt descriptors (matmul desc,
 * matrix layouts) and runs the algorithm heuristic once; the opaque plan
 * pointer is cached on the Java side per problem shape and replayed with
 * ltExecutePlan. Scalars are host floats (scale type CUDA_R_32F), so plans are
 * restricted to the FP32-compute family.
 */

#include <jni.h>
#include <cublasLt.h>
#include <iostream>

extern "C" {

typedef struct lt_plan_s {
    cublasLtMatmulDesc_t matmulDesc;
    cublasLtMatrixLayout_t aLayout;
    cublasLtMatrixLayout_t bLayout;
    cublasLtMatrixLayout_t cLayout;
    cublasLtMatmulAlgo_t algo;
    bool hasAlgo;
} lt_plan_t;

/*
 * Class:     uk_ac_manchester_tornado_cublas_provider_CuBlasLtNativeLib
 * Method:    ltCreate
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cublas_provider_CuBlasLtNativeLib_ltCreate
        (JNIEnv *env, jclass clazz) {
    cublasLtHandle_t handle;
    if (cublasLtCreate(&handle) != CUBLAS_STATUS_SUCCESS) {
        return 0;
    }
    return (jlong) handle;
}

/*
 * Class:     uk_ac_manchester_tornado_cublas_provider_CuBlasLtNativeLib
 * Method:    ltDestroy
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_cublas_provider_CuBlasLtNativeLib_ltDestroy
        (JNIEnv *env, jclass clazz, jlong handle) {
    if (handle != 0) {
        cublasLtDestroy((cublasLtHandle_t) handle);
    }
}

/*
 * Class:     uk_ac_manchester_tornado_cublas_provider_CuBlasLtNativeLib
 * Method:    ltCreatePlan
 * Signature: (JIIIIIIIIIIIIIIJ)J
 *
 * Builds the matmul descriptor (with epilogue), the matrix layouts, and runs
 * the algorithm heuristic bounded by the given workspace size. Returns an
 * opaque plan pointer, or 0 on failure.
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cublas_provider_CuBlasLtNativeLib_ltCreatePlan
        (JNIEnv *env, jclass clazz, jlong handle, jint transa, jint transb, jint m, jint n, jint k,
         jint lda, jint ldb, jint ldc, jint a_type, jint b_type, jint c_type, jint compute_type,
         jint scale_type, jint epilogue, jlong workspace_bytes) {

    lt_plan_t *plan = new lt_plan_t();
    plan->hasAlgo = false;

    cublasStatus_t status = cublasLtMatmulDescCreate(&plan->matmulDesc, (cublasComputeType_t) compute_type, (cudaDataType_t) scale_type);
    if (status != CUBLAS_STATUS_SUCCESS) {
        delete plan;
        return 0;
    }

    cublasOperation_t opA = (cublasOperation_t) transa;
    cublasOperation_t opB = (cublasOperation_t) transb;
    cublasLtMatmulDescSetAttribute(plan->matmulDesc, CUBLASLT_MATMUL_DESC_TRANSA, &opA, sizeof(opA));
    cublasLtMatmulDescSetAttribute(plan->matmulDesc, CUBLASLT_MATMUL_DESC_TRANSB, &opB, sizeof(opB));

    cublasLtEpilogue_t epi = (cublasLtEpilogue_t) epilogue;
    cublasLtMatmulDescSetAttribute(plan->matmulDesc, CUBLASLT_MATMUL_DESC_EPILOGUE, &epi, sizeof(epi));

    // Column-major layouts: A is (m x k) for OP_N or (k x m) for OP_T, etc.
    int aRows = (opA == CUBLAS_OP_N) ? m : k;
    int aCols = (opA == CUBLAS_OP_N) ? k : m;
    int bRows = (opB == CUBLAS_OP_N) ? k : n;
    int bCols = (opB == CUBLAS_OP_N) ? n : k;

    cublasLtMatrixLayoutCreate(&plan->aLayout, (cudaDataType_t) a_type, aRows, aCols, lda);
    cublasLtMatrixLayoutCreate(&plan->bLayout, (cudaDataType_t) b_type, bRows, bCols, ldb);
    cublasLtMatrixLayoutCreate(&plan->cLayout, (cudaDataType_t) c_type, m, n, ldc);

    cublasLtMatmulPreference_t preference;
    cublasLtMatmulPreferenceCreate(&preference);
    size_t maxWorkspace = (size_t) workspace_bytes;
    cublasLtMatmulPreferenceSetAttribute(preference, CUBLASLT_MATMUL_PREF_MAX_WORKSPACE_BYTES, &maxWorkspace, sizeof(maxWorkspace));

    cublasLtMatmulHeuristicResult_t heuristic;
    int returnedResults = 0;
    status = cublasLtMatmulAlgoGetHeuristic((cublasLtHandle_t) handle, plan->matmulDesc,
                                            plan->aLayout, plan->bLayout, plan->cLayout, plan->cLayout,
                                            preference, 1, &heuristic, &returnedResults);
    cublasLtMatmulPreferenceDestroy(preference);

    if (status != CUBLAS_STATUS_SUCCESS || returnedResults == 0) {
        // The caller only sees a null plan, so report why here: status 15
        // (CUBLAS_STATUS_NOT_SUPPORTED) or 0 results means this cuBLAS has no
        // kernel for the requested types on this GPU (e.g. FP8 on an arch the
        // library predates); other statuses point at the descriptor/layouts.
        std::cerr << "[TornadoVM-cuBLASLt] plan creation failed: cublasLtMatmulAlgoGetHeuristic"
                  << " status=" << (int) status << " returnedResults=" << returnedResults
                  << " (aType=" << a_type << " bType=" << b_type << " cType=" << c_type
                  << " m=" << m << " n=" << n << " k=" << k << ")" << std::endl;
        cublasLtMatmulDescDestroy(plan->matmulDesc);
        cublasLtMatrixLayoutDestroy(plan->aLayout);
        cublasLtMatrixLayoutDestroy(plan->bLayout);
        cublasLtMatrixLayoutDestroy(plan->cLayout);
        delete plan;
        return 0;
    }

    plan->algo = heuristic.algo;
    plan->hasAlgo = true;
    return (jlong) plan;
}

/*
 * Class:     uk_ac_manchester_tornado_cublas_provider_CuBlasLtNativeLib
 * Method:    ltDestroyPlan
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_cublas_provider_CuBlasLtNativeLib_ltDestroyPlan
        (JNIEnv *env, jclass clazz, jlong plan_ptr) {
    lt_plan_t *plan = (lt_plan_t *) plan_ptr;
    if (plan == nullptr) {
        return;
    }
    cublasLtMatmulDescDestroy(plan->matmulDesc);
    cublasLtMatrixLayoutDestroy(plan->aLayout);
    cublasLtMatrixLayoutDestroy(plan->bLayout);
    cublasLtMatrixLayoutDestroy(plan->cLayout);
    delete plan;
}

/*
 * Class:     uk_ac_manchester_tornado_cublas_provider_CuBlasLtNativeLib
 * Method:    ltExecutePlan
 * Signature: (JJJFJJFJJJJ)I
 *
 * Executes a previously created plan on the given stream. bias_ptr may be 0
 * for plans without a bias epilogue; when non-zero it is (re)attached to the
 * matmul descriptor before the call (the bias buffer is stable per task).
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cublas_provider_CuBlasLtNativeLib_ltExecutePlan
        (JNIEnv *env, jclass clazz, jlong handle, jlong plan_ptr, jlong stream_ptr, jfloat alpha,
         jlong d_a, jlong d_b, jfloat beta, jlong d_c, jlong bias_ptr, jlong workspace_ptr, jlong workspace_bytes) {

    lt_plan_t *plan = (lt_plan_t *) plan_ptr;
    float host_alpha = alpha;
    float host_beta = beta;

    if (bias_ptr != 0) {
        const void *bias = (const void *) bias_ptr;
        cublasStatus_t status = cublasLtMatmulDescSetAttribute(plan->matmulDesc, CUBLASLT_MATMUL_DESC_BIAS_POINTER, &bias, sizeof(bias));
        if (status != CUBLAS_STATUS_SUCCESS) {
            return (jint) status;
        }
    }

    cublasStatus_t status = cublasLtMatmul((cublasLtHandle_t) handle, plan->matmulDesc,
                                 &host_alpha, (const void *) d_a, plan->aLayout,
                                 (const void *) d_b, plan->bLayout,
                                 &host_beta, (const void *) d_c, plan->cLayout,
                                 (void *) d_c, plan->cLayout,
                                 plan->hasAlgo ? &plan->algo : nullptr,
                                 (void *) workspace_ptr, (size_t) workspace_bytes,
                                 (cudaStream_t) stream_ptr);

    // The algorithm heuristic (cublasLtMatmulAlgoGetHeuristic) can hand back an
    // algorithm that cublasLtMatmul itself then rejects for the same descriptors
    // (observed for FP16 matmul on some GPU/driver combinations). Retry once
    // without a preselected algorithm, letting cuBLASLt choose internally.
    if (status == CUBLAS_STATUS_NOT_SUPPORTED && plan->hasAlgo) {
        status = cublasLtMatmul((cublasLtHandle_t) handle, plan->matmulDesc,
                                 &host_alpha, (const void *) d_a, plan->aLayout,
                                 (const void *) d_b, plan->bLayout,
                                 &host_beta, (const void *) d_c, plan->cLayout,
                                 (void *) d_c, plan->cLayout,
                                 nullptr,
                                 (void *) workspace_ptr, (size_t) workspace_bytes,
                                 (cudaStream_t) stream_ptr);
    }

    return (jint) status;
}

} // extern "C"
