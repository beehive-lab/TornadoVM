//
// Created by juan on 30/07/2021.
//

#include "levelZeroFence.h"
#include <iostream>
#include "ze_api.h"
#include "ze_log.h"

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroFence
 * Method:    zeFenceCreate_native
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeFenceDesc;Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeFenceHandle;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroFence_zeFenceCreate_1native
(JNIEnv * env, jobject, jlong commandQueueHandler, jobject javaFenceDescriptor, jobject javaFenceHandler) {

    ze_command_queue_handle_t commandQueue = reinterpret_cast<ze_command_queue_handle_t>(commandQueueHandler);

    jclass descriptionClass = env->GetObjectClass(javaFenceDescriptor);
    jfieldID fieldDescriptionType = env->GetFieldID(descriptionClass, "stype", "I");
    ze_structure_type_t stype = static_cast<ze_structure_type_t>(env->GetIntField(javaFenceDescriptor, fieldDescriptionType));
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

