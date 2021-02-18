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

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel
 * Method:    zeCommandListAppendLaunchKernel_native
 * Signature: (JJLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeGroupDispatch;Ljava/lang/Object;ILjava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel_zeCommandListAppendLaunchKernel_1native
        (JNIEnv *env, jobject object, jlong javaCommandListHandler, jlong javaKernelHandlerPtr, jobject javaDispatch, jobject javaSignalEvent, jint numWaits, jobject javaEventHandler) {

    ze_kernel_handle_t kernel = reinterpret_cast<ze_kernel_handle_t>(javaKernelHandlerPtr);

    // Get the command list
    ze_command_list_handle_t cmdList = reinterpret_cast<ze_command_list_handle_t>(javaCommandListHandler);

    // Get the dispatch
    jclass klass = env->GetObjectClass(javaDispatch);
    jfieldID field = env->GetFieldID(klass, "groupCountX", "J");
    jlong groupCountX = env->GetLongField(javaDispatch, field);
    field = env->GetFieldID(klass, "groupCountY", "J");
    jlong groupCountY = env->GetLongField(javaDispatch, field);
    field = env->GetFieldID(klass, "groupCountZ", "J");
    jlong groupCountZ = env->GetLongField(javaDispatch, field);

    ze_group_count_t dispatch;
    dispatch.groupCountX = groupCountX;
    dispatch.groupCountY = groupCountY;
    dispatch.groupCountZ = groupCountZ;

    // XXX: Fix the rest of the parameters under demand
    ze_result_t result = zeCommandListAppendLaunchKernel(cmdList, kernel, &dispatch, nullptr, numWaits, nullptr);
    LOG_ZE_JNI("zeCommandListAppendLaunchKernel", result);
    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel
 * Method:    zeCommandListClose_native
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel_zeCommandListClose_1native
        (JNIEnv *, jobject, jlong javaCommandListHandler) {
    ze_command_list_handle_t cmdList = reinterpret_cast<ze_command_list_handle_t>(javaCommandListHandler);
    ze_result_t result = zeCommandListClose(cmdList);
    LOG_ZE_JNI("zeCommandListClose", result);
    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel
 * Method:    zeCommandQueueExecuteCommandLists_native
 * Signature: (JIJLjava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel_zeCommandQueueExecuteCommandLists_1native
        (JNIEnv *env , jobject object, jlong javaCommandQueueHandler, jint numCommandLists, jlong javaCommandListHandler, jobject javaHFence) {

    ze_command_list_handle_t cmdList = reinterpret_cast<ze_command_list_handle_t>(javaCommandListHandler);
    ze_command_queue_handle_t cmdQueue = reinterpret_cast<ze_command_queue_handle_t>(javaCommandQueueHandler);
    ze_result_t result = zeCommandQueueExecuteCommandLists(cmdQueue, numCommandLists, &cmdList, nullptr);
    LOG_ZE_JNI("zeCommandQueueExecuteCommandLists", result);
    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel
 * Method:    zeCommandQueueSynchronize_native
 * Signature: (JJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel_zeCommandQueueSynchronize_1native
        (JNIEnv *env, jobject object, jlong javaCommandQueueHandler, jlong timeOut) {
    ze_command_queue_handle_t cmdQueue = reinterpret_cast<ze_command_queue_handle_t>(javaCommandQueueHandler);
    ze_result_t result = zeCommandQueueSynchronize(cmdQueue, timeOut);
    LOG_ZE_JNI("zeCommandQueueSynchronize", result);
    return result;

}