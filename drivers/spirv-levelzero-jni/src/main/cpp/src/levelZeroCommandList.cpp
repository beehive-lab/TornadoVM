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

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandList
 * Method:    zeCommandListAppendMemoryCopy_native
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroByteBuffer;[BJJJLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeEventHandle;ILuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeEventHandle;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandList_zeCommandListAppendMemoryCopy_1native
        (JNIEnv * env, jobject, jlong javaCommandListHandler, jobject javaLevelZeroBuffer, jbyteArray array, jlong size, jlong dstOffset, jlong srcOffset, jobject javaEvenHandle, jint numWaitEvents, jobject javaWaitEvents) {

    ze_command_list_handle_t cmdList = reinterpret_cast<ze_command_list_handle_t>(javaCommandListHandler);

    jclass klass = env->GetObjectClass(javaLevelZeroBuffer);
    jfieldID fieldPointer = env->GetFieldID(klass, "ptrBuffer", "J");
    jlong ptr = env->GetLongField(javaLevelZeroBuffer, fieldPointer);

    jbyte *sourceBuffer = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(array, NULL));
    jbyte *dstBuffer = reinterpret_cast<jbyte *>(ptr);

    ze_event_handle_t hSignalEvent = nullptr;
    if (javaEvenHandle != nullptr) {
        jclass klassEvent = env->GetObjectClass(javaEvenHandle);
        fieldPointer = env->GetFieldID(klassEvent, "ptrZeEventHandle", "J");
        jlong ptrHSignalEvent = env->GetLongField(javaEvenHandle, fieldPointer);
        hSignalEvent = reinterpret_cast<ze_event_handle_t>(ptrHSignalEvent);
    }

    ze_event_handle_t phWaitEvents = nullptr;
    if (javaWaitEvents != nullptr) {
        jclass klassEvent = env->GetObjectClass(javaWaitEvents);
        fieldPointer = env->GetFieldID(klassEvent, "ptrZeEventHandle", "J");
        jlong ptrHSignalEvent = env->GetLongField(javaWaitEvents, fieldPointer);
        phWaitEvents = reinterpret_cast<ze_event_handle_t>(ptrHSignalEvent);
    }

    ze_result_t result = zeCommandListAppendMemoryCopy(cmdList, &dstBuffer[dstOffset], &sourceBuffer[srcOffset], size, hSignalEvent, numWaitEvents, &phWaitEvents);
    LOG_ZE_JNI("zeCommandListAppendMemoryCopy", result);
    env->ReleasePrimitiveArrayCritical(array, sourceBuffer, JNI_ABORT);

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandList
 * Method:    zeCommandListAppendMemoryCopy_nativeInt
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroByteBuffer;[IJJJLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeEventHandle;ILuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeEventHandle;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandList_zeCommandListAppendMemoryCopy_1nativeInt
        (JNIEnv * env, jobject, jlong javaCommandListHandler, jobject javaLevelZeroBuffer, jintArray array, jlong size, jlong dstOffset, jlong srcOffset, jobject javaEvenHandle, jint numWaitEvents, jobject javaWaitEvents) {
    ze_command_list_handle_t cmdList = reinterpret_cast<ze_command_list_handle_t>(javaCommandListHandler);

    jclass klass = env->GetObjectClass(javaLevelZeroBuffer);
    jfieldID fieldPointer = env->GetFieldID(klass, "ptrBuffer", "J");
    jlong ptr = env->GetLongField(javaLevelZeroBuffer, fieldPointer);

    jbyte *sourceBuffer = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(array, NULL));
    jbyte *dstBuffer = reinterpret_cast<jbyte *>(ptr);

    ze_event_handle_t hSignalEvent = nullptr;
    if (javaEvenHandle != nullptr) {
        jclass klassEvent = env->GetObjectClass(javaEvenHandle);
        fieldPointer = env->GetFieldID(klassEvent, "ptrZeEventHandle", "J");
        jlong ptrHSignalEvent = env->GetLongField(javaEvenHandle, fieldPointer);
        hSignalEvent = reinterpret_cast<ze_event_handle_t>(ptrHSignalEvent);
    }

    ze_event_handle_t phWaitEvents = nullptr;
    if (javaWaitEvents != nullptr) {
        jclass klassEvent = env->GetObjectClass(javaWaitEvents);
        fieldPointer = env->GetFieldID(klassEvent, "ptrZeEventHandle", "J");
        jlong ptrHSignalEvent = env->GetLongField(javaWaitEvents, fieldPointer);
        phWaitEvents = reinterpret_cast<ze_event_handle_t>(ptrHSignalEvent);
    }

    ze_result_t result = zeCommandListAppendMemoryCopy(cmdList, &dstBuffer[dstOffset], &sourceBuffer[srcOffset], size, hSignalEvent, numWaitEvents, &phWaitEvents);
    LOG_ZE_JNI("zeCommandListAppendMemoryCopy-[INTEGER]", result);
    env->ReleasePrimitiveArrayCritical(array, sourceBuffer, JNI_ABORT);
    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandList
 * Method:    zeCommandListAppendMemoryCopy_nativeBack
 * Signature: (JJ[BLuk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroByteBuffer;JJJLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeEventHandle;ILuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeEventHandle;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandList_zeCommandListAppendMemoryCopy_1nativeBack
        (JNIEnv *env, jobject , jlong javaCommandListHandler, jbyteArray array, jobject javaLevelZeroBuffer, jlong size, jlong dstOffset, jlong srcOffset, jobject javaEvenHandle, jint numWaitEvents, jobject javaWaitEvents) {

    ze_command_list_handle_t cmdList = reinterpret_cast<ze_command_list_handle_t>(javaCommandListHandler);

    jclass klass = env->GetObjectClass(javaLevelZeroBuffer);
    jfieldID fieldPointer = env->GetFieldID(klass, "ptrBuffer", "J");
    jlong ptr = env->GetLongField(javaLevelZeroBuffer, fieldPointer);

    jbyte *dstBuffer = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(array, NULL));
    jbyte *sourceBuffer = reinterpret_cast<jbyte *>(ptr);

    ze_event_handle_t hSignalEvent = nullptr;
    if (javaEvenHandle != nullptr) {
        jclass klassEvent = env->GetObjectClass(javaEvenHandle);
        fieldPointer = env->GetFieldID(klassEvent, "ptrZeEventHandle", "J");
        jlong ptrHSignalEvent = env->GetLongField(javaEvenHandle, fieldPointer);
        hSignalEvent = reinterpret_cast<ze_event_handle_t>(ptrHSignalEvent);
    }

    ze_event_handle_t phWaitEvents = nullptr;
    if (javaWaitEvents != nullptr) {
        jclass klassEvent = env->GetObjectClass(javaWaitEvents);
        fieldPointer = env->GetFieldID(klassEvent, "ptrZeEventHandle", "J");
        jlong ptrHSignalEvent = env->GetLongField(javaWaitEvents, fieldPointer);
        phWaitEvents = reinterpret_cast<ze_event_handle_t>(ptrHSignalEvent);
    }

    ze_result_t result = zeCommandListAppendMemoryCopy(cmdList, &dstBuffer[dstOffset], &sourceBuffer[srcOffset], size, hSignalEvent, numWaitEvents, &phWaitEvents);
    LOG_ZE_JNI("zeCommandListAppendMemoryCopy", result);

    env->ReleasePrimitiveArrayCritical(array, dstBuffer, JNI_ABORT);
    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandList
 * Method:    zeCommandListAppendMemoryCopy_nativeBackInt
 * Signature: (J[ILuk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroByteBuffer;JJJLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeEventHandle;ILuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeEventHandle;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandList_zeCommandListAppendMemoryCopy_1nativeBackInt
        (JNIEnv *env, jobject , jlong javaCommandListHandler, jintArray array, jobject javaLevelZeroBuffer, jlong size, jlong dstOffset, jlong srcOffset, jobject javaEvenHandle, jint numWaitEvents, jobject javaWaitEvents) {

    ze_command_list_handle_t cmdList = reinterpret_cast<ze_command_list_handle_t>(javaCommandListHandler);

    jclass klass = env->GetObjectClass(javaLevelZeroBuffer);
    jfieldID fieldPointer = env->GetFieldID(klass, "ptrBuffer", "J");
    jlong ptr = env->GetLongField(javaLevelZeroBuffer, fieldPointer);

    jbyte *dstBuffer = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(array, NULL));
    jbyte *sourceBuffer = reinterpret_cast<jbyte *>(ptr);

    ze_event_handle_t hSignalEvent = nullptr;
    if (javaEvenHandle != nullptr) {
        jclass klassEvent = env->GetObjectClass(javaEvenHandle);
        fieldPointer = env->GetFieldID(klassEvent, "ptrZeEventHandle", "J");
        jlong ptrHSignalEvent = env->GetLongField(javaEvenHandle, fieldPointer);
        hSignalEvent = reinterpret_cast<ze_event_handle_t>(ptrHSignalEvent);
    }

    ze_event_handle_t phWaitEvents = nullptr;
    if (javaWaitEvents != nullptr) {
        jclass klassEvent = env->GetObjectClass(javaWaitEvents);
        fieldPointer = env->GetFieldID(klassEvent, "ptrZeEventHandle", "J");
        jlong ptrHSignalEvent = env->GetLongField(javaWaitEvents, fieldPointer);
        phWaitEvents = reinterpret_cast<ze_event_handle_t>(ptrHSignalEvent);
    }

    if (LOG_JNI) {
        std::cout << "DEST offset: " << dstOffset << std::endl;
        std::cout << "SOURCE offset: " << srcOffset << std::endl;
    }
    ze_result_t result = zeCommandListAppendMemoryCopy(cmdList, &dstBuffer[dstOffset], &sourceBuffer[srcOffset], size, hSignalEvent, numWaitEvents, &phWaitEvents);
    LOG_ZE_JNI("zeCommandListAppendMemoryCopy-[INTEGER]", result);

    env->ReleasePrimitiveArrayCritical(array, dstBuffer, JNI_ABORT);
    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandList
 * Method:    zeCommandListAppendBarrier_native
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeEventHandle;ILjava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandList_zeCommandListAppendBarrier_1native
        (JNIEnv *env, jobject, jlong javaCommandListHandler, jobject javaEvenHandle, jint numWaitEvents, jobject javaWaitEvents) {

    ze_command_list_handle_t cmdList = reinterpret_cast<ze_command_list_handle_t>(javaCommandListHandler);

    ze_event_handle_t hSignalEvent = nullptr;
    if (javaEvenHandle != nullptr) {
        jclass klassEvent = env->GetObjectClass(javaEvenHandle);
        jfieldID fieldPointer = env->GetFieldID(klassEvent, "ptrZeEventHandle", "J");
        jlong ptrHSignalEvent = env->GetLongField(javaEvenHandle, fieldPointer);
        hSignalEvent = reinterpret_cast<ze_event_handle_t>(ptrHSignalEvent);
    }

    ze_event_handle_t phWaitEvents = nullptr;
    if (javaWaitEvents != nullptr) {
        jclass klassEvent = env->GetObjectClass(javaWaitEvents);
        jfieldID fieldPointer = env->GetFieldID(klassEvent, "ptrZeEventHandle", "J");
        jlong ptrHSignalEvent = env->GetLongField(javaWaitEvents, fieldPointer);
        phWaitEvents = reinterpret_cast<ze_event_handle_t>(ptrHSignalEvent);
    }

    ze_result_t result = zeCommandListAppendBarrier(cmdList, hSignalEvent, numWaitEvents, &phWaitEvents);
    LOG_ZE_JNI("zeCommandListAppendBarrier", result);
    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandList
 * Method:    zeCommandListAppendMemoryCopy_nativeBuffers
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroByteBuffer;Luk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroByteBuffer;ILuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeEventHandle;ILuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeEventHandle;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandList_zeCommandListAppendMemoryCopy_1nativeBuffers
        (JNIEnv *env, jobject, jlong javaCommandListHandler, jobject javaDestBuffer, jobject javaSourceBuffer, jint size, jobject javaEvenHandle, jint numWaitEvents, jobject javaWaitEvents) {

    ze_command_list_handle_t cmdList = reinterpret_cast<ze_command_list_handle_t>(javaCommandListHandler);

    jclass klassDest = env->GetObjectClass(javaDestBuffer);
    jfieldID fieldPointerDest = env->GetFieldID(klassDest, "ptrBuffer", "J");
    jlong ptrDest = env->GetLongField(javaDestBuffer, fieldPointerDest);


    jclass klassSource = env->GetObjectClass(javaSourceBuffer);
    jfieldID fieldPointerSource = env->GetFieldID(klassSource, "ptrBuffer", "J");
    jlong ptrSource = env->GetLongField(javaSourceBuffer, fieldPointerSource);

    jbyte *dstBuffer = reinterpret_cast<jbyte *>(ptrDest);
    jbyte *sourceBuffer = reinterpret_cast<jbyte *>(ptrSource);

    ze_event_handle_t hSignalEvent = nullptr;
    if (javaEvenHandle != nullptr) {
        jclass klassEvent = env->GetObjectClass(javaEvenHandle);
        jfieldID fieldPointer = env->GetFieldID(klassEvent, "ptrZeEventHandle", "J");
        jlong ptrHSignalEvent = env->GetLongField(javaEvenHandle, fieldPointer);
        hSignalEvent = reinterpret_cast<ze_event_handle_t>(ptrHSignalEvent);
    }

    ze_event_handle_t phWaitEvents = nullptr;
    if (javaWaitEvents != nullptr) {
        jclass klassEvent = env->GetObjectClass(javaWaitEvents);
        jfieldID fieldPointer = env->GetFieldID(klassEvent, "ptrZeEventHandle", "J");
        jlong ptrHSignalEvent = env->GetLongField(javaWaitEvents, fieldPointer);
        phWaitEvents = reinterpret_cast<ze_event_handle_t>(ptrHSignalEvent);
    }

    ze_result_t result = zeCommandListAppendMemoryCopy(cmdList, dstBuffer, sourceBuffer, size, hSignalEvent, numWaitEvents, &phWaitEvents);
    LOG_ZE_JNI("zeCommandListAppendMemoryCopy", result);

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandList
 * Method:    zeCommandListReset_native
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroCommandList_zeCommandListReset_1native
        (JNIEnv *env, jobject, jlong javaCommandListHandlePtr) {
    ze_command_list_handle_t commandList = reinterpret_cast<ze_command_list_handle_t>(javaCommandListHandlePtr);
    ze_result_t result = zeCommandListReset(commandList);
    LOG_ZE_JNI("zeCommandListReset", result);
    return result;
}