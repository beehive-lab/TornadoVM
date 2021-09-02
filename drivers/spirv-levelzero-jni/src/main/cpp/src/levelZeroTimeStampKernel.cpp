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

#include "levelZeroTimeStampKernel.h"

#include "ze_api.h"

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_ZeKernelTimeStampResult
 * Method:    resolve_native
 * Signature: (Luk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroByteBuffer;Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeKernelTimeStampResult;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_ZeKernelTimeStampResult_resolve_1native
    (JNIEnv *env, jobject, jobject javaLevelZeroByteBuffer, jobject javaZeTimeKernelStampResult) {

    void *timestampBuffer = nullptr;
    if (javaLevelZeroByteBuffer != nullptr) {
        jclass klass = env->GetObjectClass(javaLevelZeroByteBuffer);
        jfieldID fieldPointer = env->GetFieldID(klass, "ptrBuffer", "J");
        jlong ptr = env->GetLongField(javaLevelZeroByteBuffer, fieldPointer);
        if (ptr != -1) {
            timestampBuffer = reinterpret_cast<void *>(ptr);
        }
    }

    ze_kernel_timestamp_result_t *kernelTsResults = reinterpret_cast<ze_kernel_timestamp_result_t *>(timestampBuffer);

    // Set fields for Java Event Handle
    jclass eventHandleClass = env->GetObjectClass(javaZeTimeKernelStampResult);
    jfieldID field = env->GetFieldID(eventHandleClass, "globalKernelStart", "J");
    env->SetLongField(javaZeTimeKernelStampResult, field, reinterpret_cast<uint64_t>(kernelTsResults->global.kernelStart));

    field = env->GetFieldID(eventHandleClass, "globalKernelEnd", "J");
    env->SetLongField(javaZeTimeKernelStampResult, field, reinterpret_cast<uint64_t>(kernelTsResults->global.kernelEnd));

    field = env->GetFieldID(eventHandleClass, "contextKernelStart", "J");
    env->SetLongField(javaZeTimeKernelStampResult, field, reinterpret_cast<uint64_t>(kernelTsResults->context.kernelStart));

    field = env->GetFieldID(eventHandleClass, "contextKernelEnd", "J");
    env->SetLongField(javaZeTimeKernelStampResult, field, reinterpret_cast<uint64_t>(kernelTsResults->context.kernelEnd));

    return 1;
}