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
#include "levelZeroCommandQueue.h"

#include <iostream>

#include "ze_api.h"
#include "ze_log.h"


/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandQueue
 * Method:    zeCommandQueueExecuteCommandLists_native
 * Signature: (JILuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeCommandListHandle;J)I
*/
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandQueue_zeCommandQueueExecuteCommandLists_1native
    (JNIEnv *env , jobject , jlong javaCommandQueueHandler, jint numCommandLists, jobject javaCommandListHandler, jlong fencePointer) {

    ze_command_queue_handle_t commandQueue = reinterpret_cast<ze_command_queue_handle_t>(javaCommandQueueHandler);

    jclass klassEvent = env->GetObjectClass(javaCommandListHandler);
    jfieldID fieldPointer = env->GetFieldID(klassEvent, "ptrZeCommandListHandle", "J");
    jlong ptrCommandListHandler = env->GetLongField(javaCommandListHandler, fieldPointer);
    ze_command_list_handle_t commandList = reinterpret_cast<ze_command_list_handle_t>(ptrCommandListHandler);

    ze_fence_handle_t fenceHandle = nullptr;
    if (fencePointer != -1) {
        fenceHandle = reinterpret_cast<ze_fence_handle_t>(fencePointer);
    }

    ze_result_t result = zeCommandQueueExecuteCommandLists(commandQueue, numCommandLists, &commandList, fenceHandle);
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