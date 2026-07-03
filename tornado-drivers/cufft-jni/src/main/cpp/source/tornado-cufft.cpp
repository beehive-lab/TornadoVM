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
 * JNI bindings for uk.ac.manchester.tornado.cufft.provider.CuFftNativeLib.
 *
 * Thin wrappers around cuFFT. Device pointers arrive as raw CUdeviceptr longs
 * of TornadoVM-managed buffers (interleaved complex FP32); plans are bound to
 * the CUstream of the TornadoVM execution plan via cufftSetStream.
 */

#include <jni.h>
#include <cufft.h>

extern "C" {

/*
 * Class:     uk_ac_manchester_tornado_cufft_provider_CuFftNativeLib
 * Method:    cufftPlan1dC2C
 * Signature: (II)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cufft_provider_CuFftNativeLib_cufftPlan1dC2C
        (JNIEnv *env, jclass clazz, jint n, jint batch) {
    cufftHandle plan;
    cufftResult result = cufftPlan1d(&plan, n, CUFFT_C2C, batch);
    if (result != CUFFT_SUCCESS) {
        return 0;
    }
    return (jlong) plan;
}

/*
 * Class:     uk_ac_manchester_tornado_cufft_provider_CuFftNativeLib
 * Method:    cufftPlan1dOfType
 * Signature: (III)J
 *
 * type is the raw cufftType value (CUFFT_C2C=0x29, CUFFT_R2C=0x2a,
 * CUFFT_C2R=0x2c, CUFFT_Z2Z=0x69, ...).
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cufft_provider_CuFftNativeLib_cufftPlan1dOfType
        (JNIEnv *env, jclass clazz, jint n, jint batch, jint type) {
    cufftHandle plan;
    cufftResult result = cufftPlan1d(&plan, n, (cufftType) type, batch);
    if (result != CUFFT_SUCCESS) {
        return 0;
    }
    return (jlong) plan;
}

/*
 * Class:     uk_ac_manchester_tornado_cufft_provider_CuFftNativeLib
 * Method:    cufftPlan2dOfType
 * Signature: (III)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cufft_provider_CuFftNativeLib_cufftPlan2dOfType
        (JNIEnv *env, jclass clazz, jint nx, jint ny, jint type) {
    cufftHandle plan;
    cufftResult result = cufftPlan2d(&plan, nx, ny, (cufftType) type);
    if (result != CUFFT_SUCCESS) {
        return 0;
    }
    return (jlong) plan;
}

/*
 * Class:     uk_ac_manchester_tornado_cufft_provider_CuFftNativeLib
 * Method:    cufftExecR2C
 * Signature: (JJJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cufft_provider_CuFftNativeLib_cufftExecR2C
        (JNIEnv *env, jclass clazz, jlong plan, jlong d_in, jlong d_out) {
    return (jint) cufftExecR2C((cufftHandle) plan, (cufftReal *) d_in, (cufftComplex *) d_out);
}

/*
 * Class:     uk_ac_manchester_tornado_cufft_provider_CuFftNativeLib
 * Method:    cufftExecC2R
 * Signature: (JJJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cufft_provider_CuFftNativeLib_cufftExecC2R
        (JNIEnv *env, jclass clazz, jlong plan, jlong d_in, jlong d_out) {
    return (jint) cufftExecC2R((cufftHandle) plan, (cufftComplex *) d_in, (cufftReal *) d_out);
}

/*
 * Class:     uk_ac_manchester_tornado_cufft_provider_CuFftNativeLib
 * Method:    cufftExecZ2Z
 * Signature: (JJJI)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cufft_provider_CuFftNativeLib_cufftExecZ2Z
        (JNIEnv *env, jclass clazz, jlong plan, jlong d_in, jlong d_out, jint direction) {
    return (jint) cufftExecZ2Z((cufftHandle) plan, (cufftDoubleComplex *) d_in, (cufftDoubleComplex *) d_out, direction);
}

/*
 * Class:     uk_ac_manchester_tornado_cufft_provider_CuFftNativeLib
 * Method:    cufftGetVersion
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cufft_provider_CuFftNativeLib_cufftGetVersion
        (JNIEnv *env, jclass clazz) {
    int version = 0;
    cufftGetVersion(&version);
    return (jint) version;
}

/*
 * Class:     uk_ac_manchester_tornado_cufft_provider_CuFftNativeLib
 * Method:    cufftSetStream
 * Signature: (JJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cufft_provider_CuFftNativeLib_cufftSetStream
        (JNIEnv *env, jclass clazz, jlong plan, jlong stream_ptr) {
    return (jint) cufftSetStream((cufftHandle) plan, (cudaStream_t) stream_ptr);
}

/*
 * Class:     uk_ac_manchester_tornado_cufft_provider_CuFftNativeLib
 * Method:    cufftExecC2C
 * Signature: (JJJI)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cufft_provider_CuFftNativeLib_cufftExecC2C
        (JNIEnv *env, jclass clazz, jlong plan, jlong d_in, jlong d_out, jint direction) {
    return (jint) cufftExecC2C((cufftHandle) plan, (cufftComplex *) d_in, (cufftComplex *) d_out, direction);
}

/*
 * Class:     uk_ac_manchester_tornado_cufft_provider_CuFftNativeLib
 * Method:    cufftDestroy
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_cufft_provider_CuFftNativeLib_cufftDestroy
        (JNIEnv *env, jclass clazz, jlong plan) {
    if (plan != 0) {
        cufftDestroy((cufftHandle) plan);
    }
}

} // extern "C"
