#include "levelZeroCommandQueue.h"

#include <iostream>

#include "ze_api.h"
#include "ze_log.h"


/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandQueue
 * Method:    zeCommandQueueExecuteCommandLists_native
 * Signature: (JILuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeCommandQueueListHandle;Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandQueue_zeCommandQueueExecuteCommandLists_1native
        (JNIEnv *env , jobject , jlong javaCommandQueueHandler, jint numCommandLists, jobject javaCommandListHandler, jobject  javaHFence) {

    ze_command_queue_handle_t commandQueue = reinterpret_cast<ze_command_queue_handle_t>(javaCommandQueueHandler);

    jclass klassEvent = env->GetObjectClass(javaCommandListHandler);
    jfieldID fieldPointer = env->GetFieldID(klassEvent, "ptrZeCommandListHandle", "J");
    jlong ptrCommandListHandler = env->GetLongField(javaCommandListHandler, fieldPointer);
    ze_command_list_handle_t commandList = reinterpret_cast<ze_command_list_handle_t>(ptrCommandListHandler);

    ze_result_t result = zeCommandQueueExecuteCommandLists(commandQueue, numCommandLists, &commandList, nullptr);
    LOG_ZE_JNI("zeCommandQueueExecuteCommandLists", result);

    // Set CommandQueue List handler pointer
    env->SetLongField(javaCommandListHandler, fieldPointer, reinterpret_cast<jlong>(commandList));
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