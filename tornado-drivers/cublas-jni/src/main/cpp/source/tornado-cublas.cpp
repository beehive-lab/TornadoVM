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
 * JNI bindings for uk.ac.manchester.tornado.cublas.provider.CuBlasNativeLib.
 *
 * Thin, stateless wrappers around cuBLAS. Device pointers arrive as raw
 * CUdeviceptr longs of TornadoVM-managed buffers; the cuBLAS handle is bound
 * (via cublasSetStream) to the CUstream of the TornadoVM execution plan, so
 * calls are ordered with the kernels and transfers of the same task graph.
 * Scalars use the default CUBLAS_POINTER_MODE_HOST.
 */

#include <jni.h>
#include <cublas_v2.h>

extern "C" {

/*
 * Class:     uk_ac_manchester_tornado_cublas_provider_CuBlasNativeLib
 * Method:    cublasCreate
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cublas_provider_CuBlasNativeLib_cublasCreate
        (JNIEnv *env, jclass clazz) {
    cublasHandle_t handle;
    cublasStatus_t status = cublasCreate(&handle);
    if (status != CUBLAS_STATUS_SUCCESS) {
        return 0;
    }
    return (jlong) handle;
}

/*
 * Class:     uk_ac_manchester_tornado_cublas_provider_CuBlasNativeLib
 * Method:    cublasSetStream
 * Signature: (JJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cublas_provider_CuBlasNativeLib_cublasSetStream
        (JNIEnv *env, jclass clazz, jlong handle, jlong stream_ptr) {
    return (jint) cublasSetStream((cublasHandle_t) handle, (cudaStream_t) stream_ptr);
}

/*
 * Class:     uk_ac_manchester_tornado_cublas_provider_CuBlasNativeLib
 * Method:    cublasDestroy
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_cublas_provider_CuBlasNativeLib_cublasDestroy
        (JNIEnv *env, jclass clazz, jlong handle) {
    if (handle != 0) {
        cublasDestroy((cublasHandle_t) handle);
    }
}

/*
 * Class:     uk_ac_manchester_tornado_cublas_provider_CuBlasNativeLib
 * Method:    cublasSgemv
 * Signature: (JIIIFJIJIFJI)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cublas_provider_CuBlasNativeLib_cublasSgemv
        (JNIEnv *env, jclass clazz, jlong handle, jint trans, jint m, jint n, jfloat alpha, jlong d_a, jint lda,
         jlong d_x, jint incx, jfloat beta, jlong d_y, jint incy) {
    float host_alpha = alpha;
    float host_beta = beta;
    return (jint) cublasSgemv((cublasHandle_t) handle, (cublasOperation_t) trans, m, n,
                              &host_alpha, (const float *) d_a, lda,
                              (const float *) d_x, incx,
                              &host_beta, (float *) d_y, incy);
}

/*
 * Class:     uk_ac_manchester_tornado_cublas_provider_CuBlasNativeLib
 * Method:    cublasSgemm
 * Signature: (JIIIIIFJIJIFJI)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cublas_provider_CuBlasNativeLib_cublasSgemm
        (JNIEnv *env, jclass clazz, jlong handle, jint transa, jint transb, jint m, jint n, jint k, jfloat alpha,
         jlong d_a, jint lda, jlong d_b, jint ldb, jfloat beta, jlong d_c, jint ldc) {
    float host_alpha = alpha;
    float host_beta = beta;
    return (jint) cublasSgemm((cublasHandle_t) handle, (cublasOperation_t) transa, (cublasOperation_t) transb,
                              m, n, k,
                              &host_alpha, (const float *) d_a, lda,
                              (const float *) d_b, ldb,
                              &host_beta, (float *) d_c, ldc);
}

} // extern "C"
