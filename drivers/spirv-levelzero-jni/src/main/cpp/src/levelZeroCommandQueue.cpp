#include "levelZeroCommandQueue.h"

#include <iostream>

#include "ze_api.h"
#include "ze_log.h"

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandQueue
 * Method:    zeCommandQueueExecuteCommandLists_native
 * Signature: (JIJLjava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandQueue_zeCommandQueueExecuteCommandLists_1native
        (JNIEnv *env , jobject object, jlong javaCommandQueueHandler, jint numCommandLists, jlong javaCommandListHandler, jobject javaHFence) {

    ze_command_list_handle_t cmdList = reinterpret_cast<ze_command_list_handle_t>(javaCommandListHandler);
    ze_command_queue_handle_t cmdQueue = reinterpret_cast<ze_command_queue_handle_t>(javaCommandQueueHandler);
    ze_result_t result = zeCommandQueueExecuteCommandLists(cmdQueue, numCommandLists, &cmdList, nullptr);
    LOG_ZE_JNI("zeCommandQueueExecuteCommandLists", result);
    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandQueue
 * Method:    zeCommandQueueSynchronize_native
 * Signature: (JJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandQueue_zeCommandQueueSynchronize_1native
        (JNIEnv *env, jobject object, jlong javaCommandQueueHandler, jlong timeOut) {

    ze_command_queue_handle_t cmdQueue = reinterpret_cast<ze_command_queue_handle_t>(javaCommandQueueHandler);
    ze_result_t result = zeCommandQueueSynchronize(cmdQueue, timeOut);
    LOG_ZE_JNI("zeCommandQueueSynchronize", result);
    return result;
}