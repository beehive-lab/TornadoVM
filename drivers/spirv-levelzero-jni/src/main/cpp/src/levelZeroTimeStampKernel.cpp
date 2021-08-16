//
// Created by juan on 02/08/2021.
//

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