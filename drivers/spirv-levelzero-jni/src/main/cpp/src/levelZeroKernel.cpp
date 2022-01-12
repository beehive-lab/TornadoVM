/*
 * MIT License
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
#include "levelZeroKernel.h"

#include <iostream>

#include "ze_api.h"
#include "ze_log.h"

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel
 * Method:    zeKernelSuggestGroupSize_native
 * Signature: (JIII[I[I[I)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel_zeKernelSuggestGroupSize_1native
        (JNIEnv *env, jobject object, jlong javaKernelHandlerPtr, jint globalSizeX, jint globalSizeY, jint globalSizeZ, jintArray javaArrayGroupSizeX, jintArray javaArrayGroupSizeY, jintArray javaArrayGroupSizeZ) {

    ze_kernel_handle_t kernel = reinterpret_cast<ze_kernel_handle_t>(javaKernelHandlerPtr);
    jint *groupSizeX = static_cast<jint *>(env->GetIntArrayElements(javaArrayGroupSizeX, 0));
    jint *groupSizeY = static_cast<jint *>(env->GetIntArrayElements(javaArrayGroupSizeY, 0));
    jint *groupSizeZ = static_cast<jint *>(env->GetIntArrayElements(javaArrayGroupSizeZ, 0));
    ze_result_t result = zeKernelSuggestGroupSize(kernel, globalSizeX, globalSizeY, globalSizeZ,
                                                  reinterpret_cast<uint32_t *>(groupSizeX),
                                                  reinterpret_cast<uint32_t *>(groupSizeY),
                                                  reinterpret_cast<uint32_t *>(groupSizeZ));
    env->ReleaseIntArrayElements(javaArrayGroupSizeX, groupSizeX, 0);
    env->ReleaseIntArrayElements(javaArrayGroupSizeY, groupSizeY, 0);
    env->ReleaseIntArrayElements(javaArrayGroupSizeZ, groupSizeZ, 0);
    LOG_ZE_JNI("zeKernelSuggestGroupSize", result);
    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel
 * Method:    zeKernelSetGroupSize_native
 * Signature: (JIII)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel_zeKernelSetGroupSize_1native
        (JNIEnv *env, jobject object, jlong javaKernelHandlerPtr, jint groupSizeX, jint groupSizeY, jint groupSizeZ) {

    ze_kernel_handle_t kernel = reinterpret_cast<ze_kernel_handle_t>(javaKernelHandlerPtr);
    ze_result_t result = zeKernelSetGroupSize(kernel, groupSizeX, groupSizeY, groupSizeZ);
    LOG_ZE_JNI("zeKernelSuggestGroupSize", result);
    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel
 * Method:    zeKernelSetArgumentValue_nativePtrArg
 * Signature: (JIIJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel_zeKernelSetArgumentValue_1nativePtrArg
        (JNIEnv *env, jobject object, jlong javaKernelHandlerPtr, jint argIndex, jint argSize, jlong ptrBuffer) {

    ze_kernel_handle_t kernel = reinterpret_cast<ze_kernel_handle_t>(javaKernelHandlerPtr);
    void *buffer = nullptr;
    if (ptrBuffer != -1) {
        buffer = reinterpret_cast<void *>(ptrBuffer);
    }
    ze_result_t result = zeKernelSetArgumentValue(kernel, argIndex, argSize, (buffer == nullptr)? nullptr: &buffer);
    LOG_ZE_JNI("zeKernelSetArgumentValue [PTR]", result);
    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel
 * Method:    zeKernelSetCacheConfig_native
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel_zeKernelSetCacheConfig_1native
    (JNIEnv *, jobject, jlong javaKernelHandlerPtr, jint flag) {
    ze_kernel_handle_t kernel = reinterpret_cast<ze_kernel_handle_t>(javaKernelHandlerPtr);
    ze_result_t result = zeKernelSetCacheConfig(kernel, flag);
    LOG_ZE_JNI("zeKernelSetCacheConfig", result);
    return result;
}