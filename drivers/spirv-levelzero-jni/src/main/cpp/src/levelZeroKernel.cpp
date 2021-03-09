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
 * Method:    zeKernelSetArgumentValue_native
 * Signature: (JIILuk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroBufferInteger;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel_zeKernelSetArgumentValue_1native
        (JNIEnv *env, jobject object, jlong javaKernelHandlerPtr, jint argIndex, jint argSize, jobject javaBufferObject) {

    ze_kernel_handle_t kernel = reinterpret_cast<ze_kernel_handle_t>(javaKernelHandlerPtr);

    jclass klass = env->GetObjectClass(javaBufferObject);
    jfieldID fieldPointer = env->GetFieldID(klass, "ptrBuffer", "J");
    jlong ptr = env->GetLongField(javaBufferObject, fieldPointer);
    void *buffer = reinterpret_cast<void *>(ptr);
    ze_result_t result = zeKernelSetArgumentValue(kernel, argIndex, argSize, &buffer);
    LOG_ZE_JNI("zeKernelSetArgumentValue", result);
    return result;
}
