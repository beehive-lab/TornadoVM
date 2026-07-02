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
