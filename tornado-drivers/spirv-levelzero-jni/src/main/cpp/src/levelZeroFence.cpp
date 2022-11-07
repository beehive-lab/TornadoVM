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

#include "levelZeroFence.h"
#include <iostream>
#include "ze_api.h"
#include "ze_log.h"

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroFence
 * Method:    zeFenceCreate_native
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeFenceDescriptor;Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeFenceHandle;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroFence_zeFenceCreate_1native
(JNIEnv * env, jobject, jlong commandQueueHandler, jobject javaFenceDescriptor, jobject javaFenceHandler) {

    ze_command_queue_handle_t commandQueue = reinterpret_cast<ze_command_queue_handle_t>(commandQueueHandler);

    jclass descriptionClass = env->GetObjectClass(javaFenceDescriptor);
    jfieldID fieldDescriptorType = env->GetFieldID(descriptionClass, "stype", "I");
    ze_structure_type_t stype = static_cast<ze_structure_type_t>(env->GetIntField(javaFenceDescriptor, fieldDescriptorType));
    jfieldID fieldFlags = env->GetFieldID(descriptionClass, "flags", "I");
    int flags = static_cast<int>(env->GetIntField(javaFenceDescriptor, fieldFlags));

    ze_fence_desc_t fenceDesc = {};
    fenceDesc.stype = stype;
    fenceDesc.flags = flags;

    jclass klassEvent = env->GetObjectClass(javaFenceHandler);
    jfieldID fieldPointer = env->GetFieldID(klassEvent, "ptrZeFenceHandle", "J");
    jlong ptrCommandListHandler = env->GetLongField(javaFenceHandler, fieldPointer);
    ze_fence_handle_t fenceHandler = nullptr;
    if (ptrCommandListHandler != -1) {
        fenceHandler = reinterpret_cast<ze_fence_handle_t>(ptrCommandListHandler);
    }

    ze_result_t result = zeFenceCreate(commandQueue, &fenceDesc, &fenceHandler);
    LOG_ZE_JNI("zeFenceCreate", result);

    env->SetLongField(javaFenceHandler, fieldPointer, reinterpret_cast<jlong>(fenceHandler));

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroFence
 * Method:    zeFenceHostSynchronize_native
 * Signature: (JJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroFence_zeFenceHostSynchronize_1native
    (JNIEnv *, jobject, jlong javaFenceHandlerPointer, jlong maxValue) {

    ze_fence_handle_t fenceHandle = reinterpret_cast<ze_fence_handle_t>(javaFenceHandlerPointer);

    ze_result_t result = zeFenceHostSynchronize(fenceHandle, maxValue);
    LOG_ZE_JNI("zeFenceHostSynchronize", result);

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroFence
 * Method:    zeFenceReset_native
 * Signature: (J)I
*/
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroFence_zeFenceReset_1native
(JNIEnv *, jobject, jlong javaFenceHandlerPointer) {
    ze_fence_handle_t fenceHandle = reinterpret_cast<ze_fence_handle_t>(javaFenceHandlerPointer);
    ze_result_t result = zeFenceReset(fenceHandle);
    LOG_ZE_JNI("zeFenceReset", result);
    return result;
}

