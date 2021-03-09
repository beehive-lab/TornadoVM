#include "levelZeroCommandList.h"

#include <iostream>

#include "ze_api.h"
#include "ze_log.h"


/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandList
 * Method:    zeCommandListAppendLaunchKernel_native
 * Signature: (JJLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeGroupDispatch;Ljava/lang/Object;ILjava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandList_zeCommandListAppendLaunchKernel_1native
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
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandList
 * Method:    zeCommandListClose_native
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandList_zeCommandListClose_1native
        (JNIEnv *, jobject, jlong javaCommandListHandler) {
    ze_command_list_handle_t cmdList = reinterpret_cast<ze_command_list_handle_t>(javaCommandListHandler);
    ze_result_t result = zeCommandListClose(cmdList);
    LOG_ZE_JNI("zeCommandListClose", result);
    return result;
}