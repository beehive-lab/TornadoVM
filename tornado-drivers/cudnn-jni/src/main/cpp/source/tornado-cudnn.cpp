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
 * JNI bindings for uk.ac.manchester.tornado.cudnn.provider.CuDnnNativeLib.
 *
 * FP32/NCHW wrappers around the cuDNN legacy API. Simple per-element ops
 * (softmax, activation, pooling) create their descriptors inline (host-side,
 * cheap); convolution uses an opaque plan (descriptors + algorithm + workspace
 * size) created once per shape and cached on the Java side, so the workspace
 * can be allocated before CUDA graph capture starts.
 */

#include <jni.h>
#include <cudnn.h>
#include <cuda_runtime_api.h>

extern "C" {

typedef struct cudnn_conv_plan_s {
    cudnnTensorDescriptor_t inputDesc;
    cudnnFilterDescriptor_t filterDesc;
    cudnnConvolutionDescriptor_t convDesc;
    cudnnTensorDescriptor_t outputDesc;
    cudnnConvolutionFwdAlgo_t algo;
    size_t workspaceBytes;
} cudnn_conv_plan_t;

/*
 * Class:     uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib
 * Method:    cudnnCreateHandle
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_cudnnCreateHandle
        (JNIEnv *env, jclass clazz) {
    cudnnHandle_t handle;
    if (cudnnCreate(&handle) != CUDNN_STATUS_SUCCESS) {
        return 0;
    }
    return (jlong) handle;
}

/*
 * Class:     uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib
 * Method:    cudnnSetStream
 * Signature: (JJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_cudnnSetStream
        (JNIEnv *env, jclass clazz, jlong handle, jlong stream_ptr) {
    return (jint) cudnnSetStream((cudnnHandle_t) handle, (cudaStream_t) stream_ptr);
}

/*
 * Class:     uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib
 * Method:    cudnnDestroyHandle
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_cudnnDestroyHandle
        (JNIEnv *env, jclass clazz, jlong handle) {
    if (handle != 0) {
        cudnnDestroy((cudnnHandle_t) handle);
    }
}

/*
 * Class:     uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib
 * Method:    softmaxForward
 * Signature: (JIIIIJJ)I
 *
 * CUDNN_SOFTMAX_ACCURATE with MODE_INSTANCE: softmax over C*H*W per N — with
 * (n=rows, c=cols, h=w=1) this is a per-row softmax.
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_softmaxForward
        (JNIEnv *env, jclass clazz, jlong handle, jint n, jint c, jint h, jint w, jlong d_in, jlong d_out) {
    cudnnTensorDescriptor_t desc;
    cudnnStatus_t status = cudnnCreateTensorDescriptor(&desc);
    if (status != CUDNN_STATUS_SUCCESS) {
        return (jint) status;
    }
    status = cudnnSetTensor4dDescriptor(desc, CUDNN_TENSOR_NCHW, CUDNN_DATA_FLOAT, n, c, h, w);
    if (status == CUDNN_STATUS_SUCCESS) {
        float alpha = 1.0f;
        float beta = 0.0f;
        status = cudnnSoftmaxForward((cudnnHandle_t) handle, CUDNN_SOFTMAX_ACCURATE, CUDNN_SOFTMAX_MODE_INSTANCE,
                                     &alpha, desc, (const void *) d_in, &beta, desc, (void *) d_out);
    }
    cudnnDestroyTensorDescriptor(desc);
    return (jint) status;
}

/*
 * Class:     uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib
 * Method:    activationForward
 * Signature: (JIJJJ)I
 *
 * mode maps onto cudnnActivationMode_t (SIGMOID=0, RELU=1, TANH=2, ...);
 * the data is treated as a flat (1,1,1,size) tensor.
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_activationForward
        (JNIEnv *env, jclass clazz, jlong handle, jint mode, jlong size, jlong d_in, jlong d_out) {
    cudnnTensorDescriptor_t desc;
    cudnnActivationDescriptor_t activationDesc;
    cudnnStatus_t status = cudnnCreateTensorDescriptor(&desc);
    if (status != CUDNN_STATUS_SUCCESS) {
        return (jint) status;
    }
    status = cudnnCreateActivationDescriptor(&activationDesc);
    if (status != CUDNN_STATUS_SUCCESS) {
        cudnnDestroyTensorDescriptor(desc);
        return (jint) status;
    }
    status = cudnnSetTensor4dDescriptor(desc, CUDNN_TENSOR_NCHW, CUDNN_DATA_FLOAT, 1, 1, 1, (int) size);
    if (status == CUDNN_STATUS_SUCCESS) {
        status = cudnnSetActivationDescriptor(activationDesc, (cudnnActivationMode_t) mode, CUDNN_NOT_PROPAGATE_NAN, 0.0);
    }
    if (status == CUDNN_STATUS_SUCCESS) {
        float alpha = 1.0f;
        float beta = 0.0f;
        status = cudnnActivationForward((cudnnHandle_t) handle, activationDesc, &alpha, desc, (const void *) d_in, &beta, desc, (void *) d_out);
    }
    cudnnDestroyActivationDescriptor(activationDesc);
    cudnnDestroyTensorDescriptor(desc);
    return (jint) status;
}

/*
 * Class:     uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib
 * Method:    poolingMaxForward
 * Signature: (JIIIIIIJJ)I
 *
 * Square window/stride max pooling, no padding. Output dims:
 * (h - window) / stride + 1, (w - window) / stride + 1.
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_poolingMaxForward
        (JNIEnv *env, jclass clazz, jlong handle, jint n, jint c, jint h, jint w, jint window, jint stride, jlong d_in, jlong d_out) {
    cudnnTensorDescriptor_t inDesc;
    cudnnTensorDescriptor_t outDesc;
    cudnnPoolingDescriptor_t poolDesc;
    cudnnCreateTensorDescriptor(&inDesc);
    cudnnCreateTensorDescriptor(&outDesc);
    cudnnCreatePoolingDescriptor(&poolDesc);

    int outH = (h - window) / stride + 1;
    int outW = (w - window) / stride + 1;

    cudnnStatus_t status = cudnnSetTensor4dDescriptor(inDesc, CUDNN_TENSOR_NCHW, CUDNN_DATA_FLOAT, n, c, h, w);
    if (status == CUDNN_STATUS_SUCCESS) {
        status = cudnnSetTensor4dDescriptor(outDesc, CUDNN_TENSOR_NCHW, CUDNN_DATA_FLOAT, n, c, outH, outW);
    }
    if (status == CUDNN_STATUS_SUCCESS) {
        status = cudnnSetPooling2dDescriptor(poolDesc, CUDNN_POOLING_MAX, CUDNN_NOT_PROPAGATE_NAN, window, window, 0, 0, stride, stride);
    }
    if (status == CUDNN_STATUS_SUCCESS) {
        float alpha = 1.0f;
        float beta = 0.0f;
        status = cudnnPoolingForward((cudnnHandle_t) handle, poolDesc, &alpha, inDesc, (const void *) d_in, &beta, outDesc, (void *) d_out);
    }
    cudnnDestroyPoolingDescriptor(poolDesc);
    cudnnDestroyTensorDescriptor(outDesc);
    cudnnDestroyTensorDescriptor(inDesc);
    return (jint) status;
}

/*
 * Class:     uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib
 * Method:    createConvPlan
 * Signature: (JIIIIIIIII)J
 *
 * Builds descriptors for a 2D cross-correlation (the DL convention): input
 * NCHW (n,c,h,w), filter KCRS (k,c,r,s), square pad/stride, FP32, with the
 * IMPLICIT_PRECOMP_GEMM algorithm, and queries the workspace size. Returns an
 * opaque plan pointer, or 0 on failure.
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_createConvPlan
        (JNIEnv *env, jclass clazz, jlong handle, jint n, jint c, jint h, jint w, jint k, jint r, jint s, jint pad, jint stride) {
    cudnn_conv_plan_t *plan = new cudnn_conv_plan_t();
    cudnnCreateTensorDescriptor(&plan->inputDesc);
    cudnnCreateFilterDescriptor(&plan->filterDesc);
    cudnnCreateConvolutionDescriptor(&plan->convDesc);
    cudnnCreateTensorDescriptor(&plan->outputDesc);

    cudnnStatus_t status = cudnnSetTensor4dDescriptor(plan->inputDesc, CUDNN_TENSOR_NCHW, CUDNN_DATA_FLOAT, n, c, h, w);
    if (status == CUDNN_STATUS_SUCCESS) {
        status = cudnnSetFilter4dDescriptor(plan->filterDesc, CUDNN_DATA_FLOAT, CUDNN_TENSOR_NCHW, k, c, r, s);
    }
    if (status == CUDNN_STATUS_SUCCESS) {
        status = cudnnSetConvolution2dDescriptor(plan->convDesc, pad, pad, stride, stride, 1, 1, CUDNN_CROSS_CORRELATION, CUDNN_DATA_FLOAT);
    }
    int outN, outC, outH, outW;
    if (status == CUDNN_STATUS_SUCCESS) {
        status = cudnnGetConvolution2dForwardOutputDim(plan->convDesc, plan->inputDesc, plan->filterDesc, &outN, &outC, &outH, &outW);
    }
    if (status == CUDNN_STATUS_SUCCESS) {
        status = cudnnSetTensor4dDescriptor(plan->outputDesc, CUDNN_TENSOR_NCHW, CUDNN_DATA_FLOAT, outN, outC, outH, outW);
    }
    if (status == CUDNN_STATUS_SUCCESS) {
        plan->algo = CUDNN_CONVOLUTION_FWD_ALGO_IMPLICIT_PRECOMP_GEMM;
        status = cudnnGetConvolutionForwardWorkspaceSize((cudnnHandle_t) handle, plan->inputDesc, plan->filterDesc,
                                                         plan->convDesc, plan->outputDesc, plan->algo, &plan->workspaceBytes);
    }
    if (status != CUDNN_STATUS_SUCCESS) {
        cudnnDestroyTensorDescriptor(plan->outputDesc);
        cudnnDestroyConvolutionDescriptor(plan->convDesc);
        cudnnDestroyFilterDescriptor(plan->filterDesc);
        cudnnDestroyTensorDescriptor(plan->inputDesc);
        delete plan;
        return 0;
    }
    return (jlong) plan;
}

/*
 * Class:     uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib
 * Method:    convPlanWorkspaceBytes
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_convPlanWorkspaceBytes
        (JNIEnv *env, jclass clazz, jlong plan_ptr) {
    return (jlong) ((cudnn_conv_plan_t *) plan_ptr)->workspaceBytes;
}

/*
 * Class:     uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib
 * Method:    convForward
 * Signature: (JJJJJJJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_convForward
        (JNIEnv *env, jclass clazz, jlong handle, jlong plan_ptr, jlong d_in, jlong d_filter, jlong d_out, jlong workspace_ptr, jlong workspace_bytes) {
    cudnn_conv_plan_t *plan = (cudnn_conv_plan_t *) plan_ptr;
    float alpha = 1.0f;
    float beta = 0.0f;
    return (jint) cudnnConvolutionForward((cudnnHandle_t) handle, &alpha,
                                          plan->inputDesc, (const void *) d_in,
                                          plan->filterDesc, (const void *) d_filter,
                                          plan->convDesc, plan->algo,
                                          (void *) workspace_ptr, (size_t) workspace_bytes,
                                          &beta, plan->outputDesc, (void *) d_out);
}

/*
 * Class:     uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib
 * Method:    destroyConvPlan
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_destroyConvPlan
        (JNIEnv *env, jclass clazz, jlong plan_ptr) {
    cudnn_conv_plan_t *plan = (cudnn_conv_plan_t *) plan_ptr;
    if (plan == nullptr) {
        return;
    }
    cudnnDestroyTensorDescriptor(plan->outputDesc);
    cudnnDestroyConvolutionDescriptor(plan->convDesc);
    cudnnDestroyFilterDescriptor(plan->filterDesc);
    cudnnDestroyTensorDescriptor(plan->inputDesc);
    delete plan;
}

/*
 * Class:     uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib
 * Method:    allocateDeviceMemory
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_allocateDeviceMemory
        (JNIEnv *env, jclass clazz, jlong bytes) {
    void *ptr = nullptr;
    if (cudaMalloc(&ptr, (size_t) bytes) != cudaSuccess) {
        return 0;
    }
    return (jlong) ptr;
}

/*
 * Class:     uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib
 * Method:    freeDeviceMemory
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_freeDeviceMemory
        (JNIEnv *env, jclass clazz, jlong ptr) {
    if (ptr != 0) {
        cudaFree((void *) ptr);
    }
}

/*
 * Class:     uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib
 * Method:    cudnnGetVersionNative
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_cudnnGetVersionNative
        (JNIEnv *env, jclass clazz) {
    return (jlong) cudnnGetVersion();
}

} // extern "C"
