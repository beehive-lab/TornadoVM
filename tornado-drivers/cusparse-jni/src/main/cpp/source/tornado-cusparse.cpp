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
 * JNI bindings for uk.ac.manchester.tornado.cusparse.provider.CusparseNativeLib.
 *
 * cuSPARSE generic-API sparse BLAS: SpMV (y = alpha*A*x + beta*y) and SpMM
 * (C = alpha*A*B + beta*C), with A in CSR (FP32, 32-bit indices, zero-based).
 * Dense operands are row-major, matching TornadoVM's native array layout. The
 * cuSPARSE descriptors reference device pointers, so they are (cheaply) built
 * per call; the small external workspace is allocated Java-side and passed in.
 */

#include <jni.h>
#include <cuda_runtime_api.h>
#include <cusparse.h>

extern "C" {

/*
 * Class:     uk_ac_manchester_tornado_cusparse_provider_CusparseNativeLib
 * Method:    cusparseCreateHandle
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cusparse_provider_CusparseNativeLib_cusparseCreateHandle
        (JNIEnv *env, jclass clazz) {
    cusparseHandle_t handle;
    if (cusparseCreate(&handle) != CUSPARSE_STATUS_SUCCESS) {
        return 0;
    }
    return (jlong) handle;
}

/*
 * Class:     uk_ac_manchester_tornado_cusparse_provider_CusparseNativeLib
 * Method:    cusparseSetStreamNative
 * Signature: (JJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cusparse_provider_CusparseNativeLib_cusparseSetStreamNative
        (JNIEnv *env, jclass clazz, jlong handle, jlong stream) {
    return (jint) cusparseSetStream((cusparseHandle_t) handle, (cudaStream_t) stream);
}

/*
 * Class:     uk_ac_manchester_tornado_cusparse_provider_CusparseNativeLib
 * Method:    cusparseDestroyHandle
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_cusparse_provider_CusparseNativeLib_cusparseDestroyHandle
        (JNIEnv *env, jclass clazz, jlong handle) {
    if (handle != 0) {
        cusparseDestroy((cusparseHandle_t) handle);
    }
}

static bool makeCsr(cusparseSpMatDescr_t *A, jint rows, jint cols, jint nnz, jlong dRow, jlong dCol, jlong dVal) {
    return cusparseCreateCsr(A, rows, cols, nnz,
            (void *) dRow, (void *) dCol, (void *) dVal,
            CUSPARSE_INDEX_32I, CUSPARSE_INDEX_32I, CUSPARSE_INDEX_BASE_ZERO, CUDA_R_32F) == CUSPARSE_STATUS_SUCCESS;
}

// -----------------------------------------------------------------------------
// SpMV: y = alpha * A * x + beta * y
// -----------------------------------------------------------------------------

/*
 * Class:     uk_ac_manchester_tornado_cusparse_provider_CusparseNativeLib
 * Method:    spmvBufferSize
 * Signature: (JIIIJJJJJFF)J
 * Returns the external buffer size in bytes, or -1 on error.
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cusparse_provider_CusparseNativeLib_spmvBufferSize
        (JNIEnv *env, jclass clazz, jlong handle, jint rows, jint cols, jint nnz,
         jlong dRow, jlong dCol, jlong dVal, jlong dX, jlong dY, jfloat alpha, jfloat beta) {
    cusparseSpMatDescr_t A = nullptr; cusparseDnVecDescr_t X = nullptr, Y = nullptr;
    float a = alpha, b = beta; size_t bufferSize = 0; jlong result = -1;
    if (makeCsr(&A, rows, cols, nnz, dRow, dCol, dVal)
            && cusparseCreateDnVec(&X, cols, (void *) dX, CUDA_R_32F) == CUSPARSE_STATUS_SUCCESS
            && cusparseCreateDnVec(&Y, rows, (void *) dY, CUDA_R_32F) == CUSPARSE_STATUS_SUCCESS
            && cusparseSpMV_bufferSize((cusparseHandle_t) handle, CUSPARSE_OPERATION_NON_TRANSPOSE,
                    &a, A, X, &b, Y, CUDA_R_32F, CUSPARSE_SPMV_ALG_DEFAULT, &bufferSize) == CUSPARSE_STATUS_SUCCESS) {
        result = (jlong) bufferSize;
    }
    if (Y) cusparseDestroyDnVec(Y);
    if (X) cusparseDestroyDnVec(X);
    if (A) cusparseDestroySpMat(A);
    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_cusparse_provider_CusparseNativeLib
 * Method:    spmv
 * Signature: (JIIIJJJJJFFJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cusparse_provider_CusparseNativeLib_spmv
        (JNIEnv *env, jclass clazz, jlong handle, jint rows, jint cols, jint nnz,
         jlong dRow, jlong dCol, jlong dVal, jlong dX, jlong dY, jfloat alpha, jfloat beta, jlong workspace) {
    cusparseSpMatDescr_t A = nullptr; cusparseDnVecDescr_t X = nullptr, Y = nullptr;
    float a = alpha, b = beta; cusparseStatus_t status = CUSPARSE_STATUS_INTERNAL_ERROR;
    if (makeCsr(&A, rows, cols, nnz, dRow, dCol, dVal)
            && cusparseCreateDnVec(&X, cols, (void *) dX, CUDA_R_32F) == CUSPARSE_STATUS_SUCCESS
            && cusparseCreateDnVec(&Y, rows, (void *) dY, CUDA_R_32F) == CUSPARSE_STATUS_SUCCESS) {
        status = cusparseSpMV((cusparseHandle_t) handle, CUSPARSE_OPERATION_NON_TRANSPOSE,
                &a, A, X, &b, Y, CUDA_R_32F, CUSPARSE_SPMV_ALG_DEFAULT, (void *) workspace);
    }
    if (Y) cusparseDestroyDnVec(Y);
    if (X) cusparseDestroyDnVec(X);
    if (A) cusparseDestroySpMat(A);
    return (jint) status;
}

// -----------------------------------------------------------------------------
// SpMM: C = alpha * A * B + beta * C  (A: rows x k CSR; B,C row-major dense)
// -----------------------------------------------------------------------------

/*
 * Class:     uk_ac_manchester_tornado_cusparse_provider_CusparseNativeLib
 * Method:    spmmBufferSize
 * Signature: (JIIIIJJJJJFF)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cusparse_provider_CusparseNativeLib_spmmBufferSize
        (JNIEnv *env, jclass clazz, jlong handle, jint rows, jint k, jint n, jint nnz,
         jlong dRow, jlong dCol, jlong dVal, jlong dB, jlong dC, jfloat alpha, jfloat beta) {
    cusparseSpMatDescr_t A = nullptr; cusparseDnMatDescr_t B = nullptr, C = nullptr;
    float a = alpha, b = beta; size_t bufferSize = 0; jlong result = -1;
    if (makeCsr(&A, rows, k, nnz, dRow, dCol, dVal)
            && cusparseCreateDnMat(&B, k, n, n, (void *) dB, CUDA_R_32F, CUSPARSE_ORDER_ROW) == CUSPARSE_STATUS_SUCCESS
            && cusparseCreateDnMat(&C, rows, n, n, (void *) dC, CUDA_R_32F, CUSPARSE_ORDER_ROW) == CUSPARSE_STATUS_SUCCESS
            && cusparseSpMM_bufferSize((cusparseHandle_t) handle, CUSPARSE_OPERATION_NON_TRANSPOSE, CUSPARSE_OPERATION_NON_TRANSPOSE,
                    &a, A, B, &b, C, CUDA_R_32F, CUSPARSE_SPMM_ALG_DEFAULT, &bufferSize) == CUSPARSE_STATUS_SUCCESS) {
        result = (jlong) bufferSize;
    }
    if (C) cusparseDestroyDnMat(C);
    if (B) cusparseDestroyDnMat(B);
    if (A) cusparseDestroySpMat(A);
    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_cusparse_provider_CusparseNativeLib
 * Method:    spmm
 * Signature: (JIIIIJJJJJFFJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cusparse_provider_CusparseNativeLib_spmm
        (JNIEnv *env, jclass clazz, jlong handle, jint rows, jint k, jint n, jint nnz,
         jlong dRow, jlong dCol, jlong dVal, jlong dB, jlong dC, jfloat alpha, jfloat beta, jlong workspace) {
    cusparseSpMatDescr_t A = nullptr; cusparseDnMatDescr_t B = nullptr, C = nullptr;
    float a = alpha, b = beta; cusparseStatus_t status = CUSPARSE_STATUS_INTERNAL_ERROR;
    if (makeCsr(&A, rows, k, nnz, dRow, dCol, dVal)
            && cusparseCreateDnMat(&B, k, n, n, (void *) dB, CUDA_R_32F, CUSPARSE_ORDER_ROW) == CUSPARSE_STATUS_SUCCESS
            && cusparseCreateDnMat(&C, rows, n, n, (void *) dC, CUDA_R_32F, CUSPARSE_ORDER_ROW) == CUSPARSE_STATUS_SUCCESS) {
        status = cusparseSpMM((cusparseHandle_t) handle, CUSPARSE_OPERATION_NON_TRANSPOSE, CUSPARSE_OPERATION_NON_TRANSPOSE,
                &a, A, B, &b, C, CUDA_R_32F, CUSPARSE_SPMM_ALG_DEFAULT, (void *) workspace);
    }
    if (C) cusparseDestroyDnMat(C);
    if (B) cusparseDestroyDnMat(B);
    if (A) cusparseDestroySpMat(A);
    return (jint) status;
}

// -----------------------------------------------------------------------------
// Device-memory helpers + status decode.
// -----------------------------------------------------------------------------

JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cusparse_provider_CusparseNativeLib_allocateDeviceMemory
        (JNIEnv *env, jclass clazz, jlong bytes) {
    if (bytes <= 0) return 0;
    void *ptr = nullptr;
    if (cudaMalloc(&ptr, (size_t) bytes) != cudaSuccess) return 0;
    return (jlong) ptr;
}

JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cusparse_provider_CusparseNativeLib_freeDeviceMemory
        (JNIEnv *env, jclass clazz, jlong ptr) {
    if (ptr == 0) return (jint) cudaSuccess;
    return (jint) cudaFree((void *) ptr);
}

JNIEXPORT jstring JNICALL Java_uk_ac_manchester_tornado_cusparse_provider_CusparseNativeLib_statusString
        (JNIEnv *env, jclass clazz, jint status) {
    return env->NewStringUTF(cusparseGetErrorString((cusparseStatus_t) status));
}

} // extern "C"
