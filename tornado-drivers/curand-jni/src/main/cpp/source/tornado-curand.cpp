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
 * JNI bindings for uk.ac.manchester.tornado.curand.provider.CuRandNativeLib.
 *
 * Thin, stateless wrappers around cuRAND. Device pointers arrive as raw
 * CUdeviceptr longs already pointing at the first data element of a
 * TornadoVM-managed buffer; the generator is bound (via curandSetStream) to the
 * CUstream of the TornadoVM execution plan, so generation is ordered with the
 * kernels and transfers of the same task graph.
 *
 * Numbers are written straight into the destination buffer, so a simulation
 * never moves its random numbers across the bus.
 */

#include <jni.h>
#include <curand.h>

extern "C" {

JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_curand_provider_CuRandNativeLib_curandCreateGenerator
        (JNIEnv *, jclass, jint rngType) {
    curandGenerator_t generator = nullptr;
    curandStatus_t status = curandCreateGenerator(&generator, static_cast<curandRngType_t>(rngType));
    if (status != CURAND_STATUS_SUCCESS) {
        return 0;
    }
    return reinterpret_cast<jlong>(generator);
}

JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_curand_provider_CuRandNativeLib_curandDestroyGenerator
        (JNIEnv *, jclass, jlong generator) {
    if (generator != 0) {
        curandDestroyGenerator(reinterpret_cast<curandGenerator_t>(generator));
    }
}

JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_curand_provider_CuRandNativeLib_curandSetStream
        (JNIEnv *, jclass, jlong generator, jlong stream) {
    return curandSetStream(reinterpret_cast<curandGenerator_t>(generator), reinterpret_cast<cudaStream_t>(stream));
}

JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_curand_provider_CuRandNativeLib_curandSetPseudoRandomGeneratorSeed
        (JNIEnv *, jclass, jlong generator, jlong seed) {
    return curandSetPseudoRandomGeneratorSeed(reinterpret_cast<curandGenerator_t>(generator),
                                              static_cast<unsigned long long>(seed));
}

JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_curand_provider_CuRandNativeLib_curandSetGeneratorOffset
        (JNIEnv *, jclass, jlong generator, jlong offset) {
    return curandSetGeneratorOffset(reinterpret_cast<curandGenerator_t>(generator),
                                    static_cast<unsigned long long>(offset));
}

/*
 * elementOffset is in elements, not bytes: callers address a slot of a larger
 * buffer, and doing the arithmetic here keeps the Java side free of the element
 * size.
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_curand_provider_CuRandNativeLib_curandGenerateNormal
        (JNIEnv *, jclass, jlong generator, jlong devicePtr, jlong elementOffset, jlong n, jfloat mean, jfloat stddev) {
    float *output = reinterpret_cast<float *>(devicePtr) + elementOffset;
    return curandGenerateNormal(reinterpret_cast<curandGenerator_t>(generator), output,
                                static_cast<size_t>(n), mean, stddev);
}

JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_curand_provider_CuRandNativeLib_curandGenerateNormalDouble
        (JNIEnv *, jclass, jlong generator, jlong devicePtr, jlong elementOffset, jlong n, jdouble mean, jdouble stddev) {
    double *output = reinterpret_cast<double *>(devicePtr) + elementOffset;
    return curandGenerateNormalDouble(reinterpret_cast<curandGenerator_t>(generator), output,
                                      static_cast<size_t>(n), mean, stddev);
}

JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_curand_provider_CuRandNativeLib_curandGenerateUniform
        (JNIEnv *, jclass, jlong generator, jlong devicePtr, jlong elementOffset, jlong n) {
    float *output = reinterpret_cast<float *>(devicePtr) + elementOffset;
    return curandGenerateUniform(reinterpret_cast<curandGenerator_t>(generator), output, static_cast<size_t>(n));
}

JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_curand_provider_CuRandNativeLib_curandGenerateUniformDouble
        (JNIEnv *, jclass, jlong generator, jlong devicePtr, jlong elementOffset, jlong n) {
    double *output = reinterpret_cast<double *>(devicePtr) + elementOffset;
    return curandGenerateUniformDouble(reinterpret_cast<curandGenerator_t>(generator), output, static_cast<size_t>(n));
}

}
