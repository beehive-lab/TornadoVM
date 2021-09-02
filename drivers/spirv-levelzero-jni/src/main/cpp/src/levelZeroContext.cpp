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
#include "levelZeroContext.h"

#include <iostream>
#include <fstream>
#include <memory>
#include "ze_api.h"
#include "ze_log.h"

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeContextCreate
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeContextDesc;[J)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeContextCreate
    (JNIEnv *env, jobject object, jlong javaDriverHandler, jobject descriptionObject, jlongArray contextArray) {

    jclass descriptionClass = env->GetObjectClass(descriptionObject);
    jfieldID fieldDescriptionType = env->GetFieldID(descriptionClass, "type", "I");
    ze_structure_type_t type = static_cast<ze_structure_type_t>(env->GetIntField(descriptionObject, fieldDescriptionType));

    ze_driver_handle_t driverHandle = reinterpret_cast<ze_driver_handle_t>(javaDriverHandler);

    jlong *contextJavaArray = static_cast<jlong *>(env->GetLongArrayElements(contextArray, 0));
    ze_context_handle_t context;
    if (contextJavaArray[0] != 0) {
        context = reinterpret_cast<ze_context_handle_t>(contextJavaArray[0]);
    }

    jfieldID fieldDescriptionPointer = env->GetFieldID(descriptionClass, "nativePointer", "J");
    long valuePointerDescription = env->GetLongField(descriptionObject, fieldDescriptionPointer);

    ze_context_desc_t contextDesc = {};
    ze_context_desc_t *contextDescPtr;
    if (valuePointerDescription != -1) {
        contextDescPtr = reinterpret_cast<ze_context_desc_t *>(valuePointerDescription);
        contextDesc = *(contextDescPtr);
    }

    contextDesc.stype = type;
    ze_result_t result = zeContextCreate(driverHandle, &contextDesc, &context);
    LOG_ZE_JNI("zeContextCreate", result);

    contextJavaArray[0] = reinterpret_cast<jlong>(context);
    env->ReleaseLongArrayElements(contextArray, contextJavaArray, 0);

    valuePointerDescription = reinterpret_cast<long>(&(contextDesc));
    env->SetLongField(descriptionObject, fieldDescriptionPointer, valuePointerDescription);

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeCommandQueueCreate_native
 * Signature: (JJLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeCommandQueueDescription;Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeCommandQueueHandle;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeCommandQueueCreate_1native
        (JNIEnv *env, jobject object, jlong javaContextPtr , jlong javaDeviceHandler, jobject javaCommandQueueDescription, jobject javaCommandQueue) {

    ze_context_handle_t context = reinterpret_cast<ze_context_handle_t>(javaContextPtr);
    ze_device_handle_t device = reinterpret_cast<ze_device_handle_t>(javaDeviceHandler);

    // Get command Queue
    jclass commandQueueClass = env->GetObjectClass(javaCommandQueue);
    jfieldID field = env->GetFieldID(commandQueueClass, "commandQueueHandlerPointer", "J");
    jlong commandQueuePointer = env->GetLongField(javaCommandQueue, field);

    ze_command_queue_handle_t commandQueue;
    if (commandQueuePointer != -1) {
        commandQueue = reinterpret_cast<ze_command_queue_handle_t>(commandQueuePointer);
    }

    // Reconstruct commandQueueDescription
    ze_command_queue_desc_t cmdQueueDesc = {};
    jclass commandDescriptionClass = env->GetObjectClass(javaCommandQueueDescription);
    field = env->GetFieldID(commandDescriptionClass, "ptrZeCommandDescription", "J");
    long ptrZeCommandDescription = env->GetLongField(javaCommandQueueDescription, field);
    if (ptrZeCommandDescription != -1) {
        ze_command_queue_desc_t *cmdQueueDescPtr = reinterpret_cast<ze_command_queue_desc_t*>(ptrZeCommandDescription);
        cmdQueueDesc = *cmdQueueDescPtr;
    }

    field = env->GetFieldID(commandDescriptionClass, "stype", "I");
    int type = env->GetIntField(javaCommandQueueDescription, field);

    field = env->GetFieldID(commandDescriptionClass, "ordinal", "J");
    long ordinal = env->GetLongField(javaCommandQueueDescription, field);

    field = env->GetFieldID(commandDescriptionClass, "index", "J");
    int index = env->GetLongField(javaCommandQueueDescription, field);

    field = env->GetFieldID(commandDescriptionClass, "mode", "I");
    int mode = env->GetIntField(javaCommandQueueDescription, field);

    field = env->GetFieldID(commandDescriptionClass, "priority", "I");
    int priority = env->GetIntField(javaCommandQueueDescription, field);

    cmdQueueDesc.stype = static_cast<ze_structure_type_t>(type);
    cmdQueueDesc.ordinal = ordinal;
    cmdQueueDesc.index = index;
    cmdQueueDesc.mode = static_cast<ze_command_queue_mode_t>(mode);
    cmdQueueDesc.priority = static_cast<ze_command_queue_priority_t>(priority);

    ze_result_t result = zeCommandQueueCreate(context, device, &cmdQueueDesc, &commandQueue);
    LOG_ZE_JNI("zeCommandQueueCreate", result);

    // Set command queue into to the Java Command Queue Object
    field = env->GetFieldID(commandQueueClass, "commandQueueHandlerPointer", "J");
    env->SetLongField(javaCommandQueue, field, reinterpret_cast<jlong>(commandQueue));

    // Set command queue description
    field = env->GetFieldID(commandDescriptionClass, "ptrZeCommandDescription", "J");
    env->SetLongField(javaCommandQueueDescription, field, reinterpret_cast<jlong>(&cmdQueueDesc));

    field = env->GetFieldID(commandDescriptionClass, "stype", "I");
    env->SetIntField(javaCommandQueueDescription, field, cmdQueueDesc.stype);

    field = env->GetFieldID(commandDescriptionClass, "pNext", "J");
    env->SetLongField(javaCommandQueueDescription, field, reinterpret_cast<jlong>(cmdQueueDesc.pNext));

    field = env->GetFieldID(commandDescriptionClass, "ordinal", "J");
    env->SetLongField(javaCommandQueueDescription, field, cmdQueueDesc.ordinal);

    field = env->GetFieldID(commandDescriptionClass, "index", "J");
    env->SetLongField(javaCommandQueueDescription, field, cmdQueueDesc.index);

    field = env->GetFieldID(commandDescriptionClass, "flags", "I");
    env->SetIntField(javaCommandQueueDescription, field, cmdQueueDesc.flags);

    field = env->GetFieldID(commandDescriptionClass, "mode", "I");
    env->SetIntField(javaCommandQueueDescription, field, cmdQueueDesc.mode);

    field = env->GetFieldID(commandDescriptionClass, "priority", "I");
    env->SetIntField(javaCommandQueueDescription, field, cmdQueueDesc.priority);

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeCommandListCreate_native
 * Signature: (JJLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeCommandListDescription;Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeCommandQueueListHandle;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeCommandListCreate_1native
        (JNIEnv *env, jobject object, jlong javaContextPtr, jlong javaDeviceHandler, jobject javaCommandListDescription, jobject javaCommandList) {

    ze_context_handle_t context = reinterpret_cast<ze_context_handle_t>(javaContextPtr);
    ze_device_handle_t device = reinterpret_cast<ze_device_handle_t>(javaDeviceHandler);

    // Get command list
    jclass commandListClass = env->GetObjectClass(javaCommandList);
    //jfieldID field = env->GetFieldID(commandListClass, "ptrZeCommandListHandle", "J");
//    jlong commandListPointer = env->GetLongField(commandListClass, field);

    ze_command_list_handle_t commandList = nullptr;
//    if (commandListPointer != -1) {
//        commandList = reinterpret_cast<ze_command_list_handle_t>(commandListPointer);
//    }

    // Reconstruct command list description
    ze_command_list_desc_t cmdListDesc = {};
    jclass commandDescriptionClass = env->GetObjectClass(javaCommandListDescription);
    jfieldID field = env->GetFieldID(commandDescriptionClass, "ptrZeCommandListDescription", "J");
    long ptrZeCommandDescription = env->GetLongField(javaCommandListDescription, field);
    if (ptrZeCommandDescription != -1) {
        ze_command_list_desc_t *cmdListDescPtr = reinterpret_cast<ze_command_list_desc_t*>(ptrZeCommandDescription);
        cmdListDesc = *cmdListDescPtr;
    }

    field = env->GetFieldID(commandDescriptionClass, "stype", "I");
    int type = env->GetIntField(javaCommandListDescription, field);

    field = env->GetFieldID(commandDescriptionClass, "commandQueueGroupOrdinal", "J");
    long ordinal = env->GetLongField(javaCommandListDescription, field);

    cmdListDesc.stype = static_cast<ze_structure_type_t>(type);
    cmdListDesc.commandQueueGroupOrdinal = ordinal;

    ze_result_t result = zeCommandListCreate(context, device, &cmdListDesc, &commandList);
    LOG_ZE_JNI("zeCommandListCreate", result);

    // Set command queue into to the Java Command Queue Object
    field = env->GetFieldID(commandListClass, "ptrZeCommandListHandle", "J");
    env->SetLongField(javaCommandList, field, reinterpret_cast<jlong>(commandList));

    // Set command queue description
    field = env->GetFieldID(commandDescriptionClass, "ptrZeCommandListDescription", "J");
    env->SetLongField(javaCommandListDescription, field, reinterpret_cast<jlong>(&cmdListDesc));

    field = env->GetFieldID(commandDescriptionClass, "stype", "I");
    env->SetIntField(javaCommandListDescription, field, cmdListDesc.stype);

    field = env->GetFieldID(commandDescriptionClass, "pNext", "J");
    env->SetLongField(javaCommandListDescription, field, reinterpret_cast<jlong>(cmdListDesc.pNext));

    field = env->GetFieldID(commandDescriptionClass, "commandQueueGroupOrdinal", "J");
    env->SetLongField(javaCommandListDescription, field, cmdListDesc.commandQueueGroupOrdinal);

    field = env->GetFieldID(commandDescriptionClass, "flags", "I");
    env->SetIntField(javaCommandListDescription, field, cmdListDesc.flags);

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeCommandListCreateImmediate_native
 * Signature: (JJLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeCommandQueueDescription;Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeCommandQueueListHandle;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeCommandListCreateImmediate_1native
        (JNIEnv * env, jobject object, jlong javaContextPtr, jlong javaDeviceHandler, jobject javaCommandQueueDescription, jobject javaCommandList) {

    ze_context_handle_t context = reinterpret_cast<ze_context_handle_t>(javaContextPtr);
    ze_device_handle_t device = reinterpret_cast<ze_device_handle_t>(javaDeviceHandler);

    // Get command list
    jclass commandListClass = env->GetObjectClass(javaCommandList);
    jfieldID field = env->GetFieldID(commandListClass, "ptrZeCommandListHandle", "J");
    jlong commandListPointer = env->GetLongField(commandListClass, field);
    ze_command_list_handle_t commandList = {};
    if (commandListPointer != -1) {
        commandList = reinterpret_cast<ze_command_list_handle_t>(commandListPointer);
    }

    // Reconstruct command queue description
    ze_command_queue_desc_t commandQueueDesc = {};
    jclass commandDescriptionClass = env->GetObjectClass(javaCommandQueueDescription);
    field = env->GetFieldID(commandDescriptionClass, "ptrZeCommandDescription", "J");
    long ptrZeCommandDescription = env->GetLongField(javaCommandQueueDescription, field);
    if (ptrZeCommandDescription != -1) {
        ze_command_queue_desc_t *cmdQueueDescPtr = reinterpret_cast<ze_command_queue_desc_t*>(ptrZeCommandDescription);
        commandQueueDesc = *cmdQueueDescPtr;
    }

    field = env->GetFieldID(commandDescriptionClass, "stype", "I");
    int type = env->GetIntField(javaCommandQueueDescription, field);

    field = env->GetFieldID(commandDescriptionClass, "ordinal", "J");
    long ordinal = env->GetLongField(javaCommandQueueDescription, field);

    field = env->GetFieldID(commandDescriptionClass, "index", "J");
    int index = env->GetIntField(javaCommandQueueDescription, field);

    field = env->GetFieldID(commandDescriptionClass, "flags", "I");
    int flags = env->GetIntField(javaCommandQueueDescription, field);

    field = env->GetFieldID(commandDescriptionClass, "priority", "I");
    int priority = env->GetIntField(javaCommandQueueDescription, field);

    commandQueueDesc.stype = static_cast<ze_structure_type_t>(type);
    commandQueueDesc.ordinal = ordinal;
    commandQueueDesc.index = index;
    commandQueueDesc.flags = flags;
    commandQueueDesc.priority = static_cast<ze_command_queue_priority_t>(priority);

    int result = zeCommandListCreateImmediate(context, device, &commandQueueDesc, &commandList);
    LOG_ZE_JNI("zeCommandListCreateImmediate", result);

    // Store the pointer in the command list Java object
    field = env->GetFieldID(commandListClass, "ptrZeCommandListHandle", "J");
    env->SetLongField(javaCommandList, field, reinterpret_cast<jlong>(commandList));

    // Set command queue description
    field = env->GetFieldID(commandDescriptionClass, "ptrZeCommandDescription", "J");
    env->SetLongField(javaCommandQueueDescription, field, reinterpret_cast<jlong>(&commandQueueDesc));

    field = env->GetFieldID(commandDescriptionClass, "stype", "I");
    env->SetIntField(javaCommandQueueDescription, field, commandQueueDesc.stype);

    field = env->GetFieldID(commandDescriptionClass, "pNext", "J");
    env->SetLongField(javaCommandQueueDescription, field, reinterpret_cast<jlong>(commandQueueDesc.pNext));

    field = env->GetFieldID(commandDescriptionClass, "ordinal", "J");
    env->SetLongField(javaCommandQueueDescription, field, commandQueueDesc.ordinal);

    field = env->GetFieldID(commandDescriptionClass, "index", "J");
    env->SetIntField(javaCommandQueueDescription, field, commandQueueDesc.index);

    field = env->GetFieldID(commandDescriptionClass, "flags", "I");
    env->SetIntField(javaCommandQueueDescription, field, commandQueueDesc.flags);

    field = env->GetFieldID(commandDescriptionClass, "mode", "I");
    env->SetIntField(javaCommandQueueDescription, field, commandQueueDesc.mode);

    field = env->GetFieldID(commandDescriptionClass, "priority", "I");
    env->SetIntField(javaCommandQueueDescription, field, commandQueueDesc.priority);

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeMemAllocShared_native
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeDeviceMemAllocDesc;Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeHostMemAllocDesc;IIJLuk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroBufferInteger;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeMemAllocShared_1native
        (JNIEnv *env, jobject object, jlong javaContextPtr, jobject javaDeviceMemAllocDesc, jobject javaHostMemAllocDesc, jint bufferSize, jint aligmnent, jlong javaDeviceHandler, jobject javaLevelZeroBuffer) {

    ze_context_handle_t context = reinterpret_cast<ze_context_handle_t>(javaContextPtr);
    ze_device_handle_t device = reinterpret_cast<ze_device_handle_t>(javaDeviceHandler);

    jclass javaBufferClass = env->GetObjectClass(javaLevelZeroBuffer);
    jfieldID fieldBuffer = env->GetFieldID(javaBufferClass, "ptrBuffer", "J");
    void* buffer = nullptr;

    jclass javaDeviceMemAllocDescClass = env->GetObjectClass(javaDeviceMemAllocDesc);
    jfieldID fieldTypeDeviceDesc = env->GetFieldID(javaDeviceMemAllocDescClass, "stype", "I");
    int typeDeviceDesc = env->GetIntField(javaDeviceMemAllocDesc, fieldTypeDeviceDesc);
    jfieldID fieldFlagsDeviceDesc = env->GetFieldID(javaDeviceMemAllocDescClass, "flags", "J");
    long flagDeviceDesc = env->GetLongField(javaDeviceMemAllocDesc, fieldFlagsDeviceDesc);
    jfieldID fieldOrdinalDeviceDesc = env->GetFieldID(javaDeviceMemAllocDescClass, "ordinal", "J");
    long ordinalDeviceDesc = env->GetLongField(javaDeviceMemAllocDesc, fieldOrdinalDeviceDesc);

    ze_device_mem_alloc_desc_t deviceDesc = {};
    deviceDesc.stype = static_cast<ze_structure_type_t>(typeDeviceDesc);
    deviceDesc.ordinal = ordinalDeviceDesc;
    deviceDesc.flags = flagDeviceDesc;

    jclass javaHostMemAllocDescClass = env->GetObjectClass(javaHostMemAllocDesc);
    jfieldID fieldTypeHostDesc = env->GetFieldID(javaHostMemAllocDescClass, "stype", "I");
    int typeHostDesc = env->GetIntField(javaHostMemAllocDesc, fieldTypeHostDesc);
    jfieldID fieldFlagsHostDesc = env->GetFieldID(javaHostMemAllocDescClass, "flags", "J");
    long flagsHostDesc = env->GetLongField(javaHostMemAllocDesc, fieldFlagsHostDesc);

    ze_host_mem_alloc_desc_t hostDesc;
    hostDesc.stype = static_cast<ze_structure_type_t>(typeHostDesc);
    hostDesc.flags = flagsHostDesc;

    ze_result_t result = zeMemAllocShared(context, &deviceDesc, &hostDesc, bufferSize, aligmnent, device, &buffer);
    LOG_ZE_JNI("zeMemAllocShared", result);

    // Set Buffer Pointer
    env->SetLongField(javaLevelZeroBuffer, fieldBuffer, reinterpret_cast<jlong>(buffer));

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeMemAllocShared_nativeByte
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeDeviceMemAllocDesc;Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeHostMemAllocDesc;IIJLuk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroByteBuffer;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeMemAllocShared_1nativeByte
        (JNIEnv *env, jobject object, jlong javaContextPtr, jobject javaDeviceMemAllocDesc, jobject javaHostMemAllocDesc, jint bufferSize, jint aligmnent, jlong javaDeviceHandler, jobject javaLevelZeroBuffer) {

    ze_context_handle_t context = reinterpret_cast<ze_context_handle_t>(javaContextPtr);
    ze_device_handle_t device = reinterpret_cast<ze_device_handle_t>(javaDeviceHandler);

    jclass javaBufferClass = env->GetObjectClass(javaLevelZeroBuffer);
    jfieldID fieldBuffer = env->GetFieldID(javaBufferClass, "ptrBuffer", "J");
    void* buffer = nullptr;

    jclass javaDeviceMemAllocDescClass = env->GetObjectClass(javaDeviceMemAllocDesc);
    jfieldID fieldTypeDeviceDesc = env->GetFieldID(javaDeviceMemAllocDescClass, "stype", "I");
    int typeDeviceDesc = env->GetIntField(javaDeviceMemAllocDesc, fieldTypeDeviceDesc);
    jfieldID fieldFlagsDeviceDesc = env->GetFieldID(javaDeviceMemAllocDescClass, "flags", "J");
    long flagDeviceDesc = env->GetLongField(javaDeviceMemAllocDesc, fieldFlagsDeviceDesc);
    jfieldID fieldOrdinalDeviceDesc = env->GetFieldID(javaDeviceMemAllocDescClass, "ordinal", "J");
    long ordinalDeviceDesc = env->GetLongField(javaDeviceMemAllocDesc, fieldOrdinalDeviceDesc);

    ze_device_mem_alloc_desc_t deviceDesc = {};
    deviceDesc.stype = static_cast<ze_structure_type_t>(typeDeviceDesc);
    deviceDesc.ordinal = ordinalDeviceDesc;
    deviceDesc.flags = flagDeviceDesc;

    jclass javaHostMemAllocDescClass = env->GetObjectClass(javaHostMemAllocDesc);
    jfieldID fieldTypeHostDesc = env->GetFieldID(javaHostMemAllocDescClass, "stype", "I");
    int typeHostDesc = env->GetIntField(javaHostMemAllocDesc, fieldTypeHostDesc);
    jfieldID fieldFlagsHostDesc = env->GetFieldID(javaHostMemAllocDescClass, "flags", "J");
    long flagsHostDesc = env->GetLongField(javaHostMemAllocDesc, fieldFlagsHostDesc);

    ze_host_mem_alloc_desc_t hostDesc;
    hostDesc.stype = static_cast<ze_structure_type_t>(typeHostDesc);
    hostDesc.flags = flagsHostDesc;

    ze_result_t result = zeMemAllocShared(context, &deviceDesc, &hostDesc, bufferSize, aligmnent, device, &buffer);
    LOG_ZE_JNI("zeMemAllocShared - [ByteBuffer]", result);

    // Set Buffer Pointer
    env->SetLongField(javaLevelZeroBuffer, fieldBuffer, reinterpret_cast<jlong>(buffer));

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeMemAllocDevice_native
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeDeviceMemAllocDesc;IIJLuk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroByteBuffer;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeMemAllocDevice_1native
        (JNIEnv *env, jobject object, jlong javaContextPtr, jobject javaDeviceMemAllocDesc, jint allocSize, jint alignment, jlong javaDeviceHandler, jobject javaLevelZeroBuffer) {

    ze_context_handle_t context = reinterpret_cast<ze_context_handle_t>(javaContextPtr);
    ze_device_handle_t device = reinterpret_cast<ze_device_handle_t>(javaDeviceHandler);

    jclass javaBufferClass = env->GetObjectClass(javaLevelZeroBuffer);
    jfieldID fieldBuffer = env->GetFieldID(javaBufferClass, "ptrBuffer", "J");
    void* buffer = nullptr;

    jclass javaDeviceMemAllocDescClass = env->GetObjectClass(javaDeviceMemAllocDesc);
    jfieldID fieldTypeDeviceDesc = env->GetFieldID(javaDeviceMemAllocDescClass, "stype", "I");
    int typeDeviceDesc = env->GetIntField(javaDeviceMemAllocDesc, fieldTypeDeviceDesc);
    jfieldID fieldFlagsDeviceDesc = env->GetFieldID(javaDeviceMemAllocDescClass, "flags", "J");
    long flagDeviceDesc = env->GetLongField(javaDeviceMemAllocDesc, fieldFlagsDeviceDesc);
    jfieldID fieldOrdinalDeviceDesc = env->GetFieldID(javaDeviceMemAllocDescClass, "ordinal", "J");
    long ordinalDeviceDesc = env->GetLongField(javaDeviceMemAllocDesc, fieldOrdinalDeviceDesc);

    ze_device_mem_alloc_desc_t deviceDesc = {};
    deviceDesc.stype = static_cast<ze_structure_type_t>(typeDeviceDesc);
    deviceDesc.ordinal = ordinalDeviceDesc;
    deviceDesc.flags = flagDeviceDesc;

    ze_result_t result = zeMemAllocDevice(context, &deviceDesc, allocSize, alignment, device, &buffer);
    LOG_ZE_JNI("zeMemAllocDevice", result)

    // Set Buffer Pointer and attributes
    jfieldID fieldBufferSize = env->GetFieldID(javaBufferClass, "size", "I");
    jfieldID alignmentField = env->GetFieldID(javaBufferClass, "alignment", "I");
    env->SetLongField(javaLevelZeroBuffer, fieldBuffer, reinterpret_cast<ulong>(buffer));
    env->SetIntField(javaLevelZeroBuffer, fieldBufferSize, allocSize);
    env->SetIntField(javaLevelZeroBuffer, alignmentField, alignment);

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeMemAllocDevice_nativeLong
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeDeviceMemAllocDesc;IIJLuk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroBufferLong;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeMemAllocDevice_1nativeLong
        (JNIEnv * env, jobject object, jlong javaContextPtr, jobject javaDeviceMemAllocDesc, jint allocSize, jint alignment, jlong javaDeviceHandler, jobject javaLevelZeroBuffer) {
    ze_context_handle_t context = reinterpret_cast<ze_context_handle_t>(javaContextPtr);
    ze_device_handle_t device = reinterpret_cast<ze_device_handle_t>(javaDeviceHandler);

    jclass javaBufferClass = env->GetObjectClass(javaLevelZeroBuffer);
    jfieldID fieldBuffer = env->GetFieldID(javaBufferClass, "ptrBuffer", "J");
    void* buffer = nullptr;

    jclass javaDeviceMemAllocDescClass = env->GetObjectClass(javaDeviceMemAllocDesc);
    jfieldID fieldTypeDeviceDesc = env->GetFieldID(javaDeviceMemAllocDescClass, "stype", "I");
    int typeDeviceDesc = env->GetIntField(javaDeviceMemAllocDesc, fieldTypeDeviceDesc);
    jfieldID fieldFlagsDeviceDesc = env->GetFieldID(javaDeviceMemAllocDescClass, "flags", "J");
    long flagDeviceDesc = env->GetLongField(javaDeviceMemAllocDesc, fieldFlagsDeviceDesc);
    jfieldID fieldOrdinalDeviceDesc = env->GetFieldID(javaDeviceMemAllocDescClass, "ordinal", "J");
    long ordinalDeviceDesc = env->GetLongField(javaDeviceMemAllocDesc, fieldOrdinalDeviceDesc);

    ze_device_mem_alloc_desc_t deviceDesc = {};
    deviceDesc.stype = static_cast<ze_structure_type_t>(typeDeviceDesc);
    deviceDesc.ordinal = ordinalDeviceDesc;
    deviceDesc.flags = flagDeviceDesc;

    ze_result_t result = zeMemAllocDevice(context, &deviceDesc, allocSize, alignment, device, &buffer);
    LOG_ZE_JNI("zeMemAllocDevice - [LONG]", result)

    // Set Buffer Pointer and attributes
    jfieldID fieldBufferSize = env->GetFieldID(javaBufferClass, "size", "I");
    jfieldID alignmentField = env->GetFieldID(javaBufferClass, "alignment", "I");
    env->SetLongField(javaLevelZeroBuffer, fieldBuffer, reinterpret_cast<ulong>(buffer));
    env->SetIntField(javaLevelZeroBuffer, fieldBufferSize, allocSize);
    env->SetIntField(javaLevelZeroBuffer, alignmentField, alignment);

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeModuleCreate_nativeWithPath
 * Signature: (JJLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeModuleDesc;Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeModuleHandle;Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeBuildLogHandle;Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeModuleCreate_1nativeWithPath
        (JNIEnv *env, jobject object, jlong javaContextPtr, jlong javaDeviceHandler, jobject javaModuleDesc, jobject javaModuleHandle, jobject javaBuildLog, jstring pathToBinary) {

    ze_context_handle_t context = reinterpret_cast<ze_context_handle_t>(javaContextPtr);
    ze_device_handle_t device = reinterpret_cast<ze_device_handle_t>(javaDeviceHandler);

    jclass javaModuleDescClass = env->GetObjectClass(javaModuleDesc);
    jfieldID fieldPtrModuleDesc = env->GetFieldID(javaModuleDescClass, "ptrZeModuleDesc", "J");
    jlong ptrModuleDesc = env->GetLongField(javaModuleDesc, fieldPtrModuleDesc);

    ze_module_desc_t moduleDesc = {};
    if (ptrModuleDesc != -1) {
        ze_module_desc_t *moduleDescPtr = reinterpret_cast<ze_module_desc_t *>(ptrModuleDesc);
        moduleDesc = *moduleDescPtr;
    }

    jfieldID modDesc_field_stype = env->GetFieldID(javaModuleDescClass, "stype", "I");
    jint stype = env->GetIntField(javaModuleDesc, modDesc_field_stype);
    jfieldID modDesc_field_format = env->GetFieldID(javaModuleDescClass, "format", "I");
    jint format = env->GetIntField(javaModuleDesc, modDesc_field_format);

    jfieldID buildFlagsField = env->GetFieldID(javaModuleDescClass, "pBuildFlags", "Ljava/lang/String;");
    jstring objectString = static_cast<jstring>(env->GetObjectField(javaModuleDesc, buildFlagsField));
    const char* buildFlags = env->GetStringUTFChars(objectString, 0);

    const char* fileName = env->GetStringUTFChars(pathToBinary, 0);
    std::string f(fileName);

    std::ifstream file(f, std::ios::binary);

    if (file.is_open()) {
        file.seekg(0, file.end);
        int length = file.tellg();
        file.seekg(0, file.beg);

        std::unique_ptr<char[]> spirvInput(new char[length]);
        file.read(spirvInput.get(), length);

        ze_module_build_log_handle_t buildLog;
        moduleDesc.stype = static_cast<ze_structure_type_t>(stype);
        moduleDesc.format = static_cast<ze_module_format_t>(format);
        moduleDesc.pInputModule = reinterpret_cast<const uint8_t *>(spirvInput.get());
        moduleDesc.inputSize = length;
        moduleDesc.pBuildFlags = buildFlags;

        jclass javaModuleClass = env->GetObjectClass(javaModuleHandle);
        jfieldID fieldPtr = env->GetFieldID(javaModuleClass, "ptrZeModuleHandle", "J");
        jlong ptrModule = env->GetLongField(javaModuleClass, fieldPtr);

        ze_module_handle_t module = nullptr;
        if (ptrModule != -1) {
            module = reinterpret_cast<ze_module_handle_t>(ptrModule);
        }

        ze_result_t result = zeModuleCreate(context, device, &moduleDesc, &module, &buildLog);
        LOG_ZE_JNI("zeModuleCreate", result);

        // update module pointer
        env->SetLongField(javaModuleHandle, fieldPtr, reinterpret_cast<jlong>(module));

        // update module Description object
        jfieldID field = env->GetFieldID(javaModuleDescClass, "pNext", "J");
        env->SetLongField(javaModuleDesc, field, (jlong) moduleDesc.pNext);

        if (moduleDesc.pConstants != nullptr) {
            field = env->GetFieldID(javaModuleDescClass, "numConstants", "I");
            env->SetLongField(javaModuleDesc, field, (jlong) moduleDesc.pConstants->numConstants);
            field = env->GetFieldID(javaModuleDescClass, "pConstantsIds", "J");
            env->SetLongField(javaModuleDesc, field, (jlong) moduleDesc.pConstants->pConstantIds);
            field = env->GetFieldID(javaModuleDescClass, "pConstantValues", "J");
            env->SetLongField(javaModuleDesc, field, (jlong) moduleDesc.pConstants->pConstantValues);
        }

        // update build log object
        jclass javaBuildLogClass = env->GetObjectClass(javaBuildLog);
        jfieldID fieldPtrLog = env->GetFieldID(javaBuildLogClass, "ptrZeBuildLogHandle", "J");
        env->SetLongField(javaBuildLog, fieldPtrLog, reinterpret_cast<jlong>(buildLog));

        file.close();
        return result;
    } else {
        return -1;
    }
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeMemFree_native
 * Signature: (JJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeMemFree_1native
        (JNIEnv *env, jobject object, jlong javaContextHandler, jlong bufferPtr) {

    ze_context_handle_t context = reinterpret_cast<ze_context_handle_t>(javaContextHandler);
    void *buffer = reinterpret_cast<void *>(bufferPtr);
    ze_result_t result = zeMemFree(context, buffer);
    LOG_ZE_JNI("zeMemFree", result);
    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeCommandListDestroy_native
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeCommandListDestroy_1native
        (JNIEnv *env, jobject object, jlong javaCommandListHandle) {
    auto commandListHandle = reinterpret_cast<ze_command_list_handle_t>(javaCommandListHandle);
    ze_result_t result = zeCommandListDestroy(commandListHandle);
    LOG_ZE_JNI("zeCommandListDestroy", result);
    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeCommandQueueDestroy_native
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeCommandQueueDestroy_1native
        (JNIEnv *env, jobject object, jlong javaCommandQueueHandler) {
    auto commandListHandle = reinterpret_cast<ze_command_queue_handle_t>(javaCommandQueueHandler);
    ze_result_t result = zeCommandQueueDestroy(commandListHandle);
    LOG_ZE_JNI("zeCommandQueueDestroy", result);
    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeModuleBuildLogGetString_native
 * Signature: (Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeBuildLogHandle;[I[Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeModuleBuildLogGetString_1native
        (JNIEnv *env, jobject object, jobject javaBuildLogHandle, jintArray javaSizeArray, jobjectArray javaStringMessage) {

    jclass javaBuildLogClass = env->GetObjectClass(javaBuildLogHandle);
    jfieldID fieldPtrLog = env->GetFieldID(javaBuildLogClass, "ptrZeBuildLogHandle", "J");
    jlong ptrLog = env->GetLongField(javaBuildLogHandle, fieldPtrLog);

    auto logHandle = reinterpret_cast<ze_module_build_log_handle_t>(ptrLog);

    size_t szLog = 0;
    ze_result_t result = zeModuleBuildLogGetString(logHandle, &szLog, nullptr);
    LOG_ZE_JNI("zeModuleBuildLogGetString", result);

    char* stringLog = (char*)malloc(szLog);
    result = zeModuleBuildLogGetString(logHandle, &szLog, stringLog);
    LOG_ZE_JNI("zeModuleBuildLogGetString", result);

    jstring str = env->NewStringUTF(stringLog);
    env->SetObjectArrayElement(javaStringMessage, 0, str);

    jint* sizeArray = env->GetIntArrayElements(javaSizeArray, 0);
    sizeArray[0] = szLog;
    env->ReleaseIntArrayElements(javaSizeArray, sizeArray, 0);

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeEventPoolCreate_native
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeEventPoolDescription;IJLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeEventPoolHandle;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeEventPoolCreate_1native
        (JNIEnv *env, jobject object, jlong javaContextPtr, jobject javaEventPoolDescription, jint numDevices, jlong javaDeviceHandler, jobject javaEventPool) {

    ze_context_handle_t context = reinterpret_cast<ze_context_handle_t>(javaContextPtr);
    ze_device_handle_t  device = reinterpret_cast<ze_device_handle_t>(javaDeviceHandler);

    jclass klassEventPoolDescription = env->GetObjectClass(javaEventPoolDescription);
    jfieldID field = env->GetFieldID(klassEventPoolDescription, "stype", "I");
    int stype = env->GetIntField(javaEventPoolDescription, field);
    field = env->GetFieldID(klassEventPoolDescription, "count", "I");
    jint count = env->GetIntField(javaEventPoolDescription, field);
    field = env->GetFieldID(klassEventPoolDescription, "flags", "I");
    jint flags = env->GetIntField(javaEventPoolDescription, field);
    field = env->GetFieldID(klassEventPoolDescription, "pNext", "J");
    jint pNext = env->GetLongField(javaEventPoolDescription, field);

    ze_event_pool_desc_t eventPoolDescription = {};
    eventPoolDescription.stype = static_cast<ze_structure_type_t>(stype);
    eventPoolDescription.pNext = reinterpret_cast<const void *>(pNext);
    eventPoolDescription.count = count;
    eventPoolDescription.flags = flags;

    ze_event_pool_handle_t eventPool = nullptr;

    ze_result_t result = zeEventPoolCreate(context, &eventPoolDescription, numDevices, &device, &eventPool);
    LOG_ZE_JNI("zeEventPoolCreate", result);

    // Set fields for Java Event Pool Handle
    jclass eventPoolHandlerClass = env->GetObjectClass(javaEventPool);
    field = env->GetFieldID(eventPoolHandlerClass, "ptrZeEventPoolHandle", "J");
    env->SetLongField(javaEventPool, field, reinterpret_cast<jlong>(eventPool));

    // Set Event Pool Description
    field = env->GetFieldID(klassEventPoolDescription, "ptrZeEventPoolDescription", "J");
    env->SetLongField(javaEventPoolDescription, field, reinterpret_cast<jlong>(&eventPoolDescription));

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeEventCreate_native
 * Signature: (Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeEventPoolHandle;Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeEventDescription;Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeEventHandle;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeEventCreate_1native
        (JNIEnv *env, jobject object, jobject javaEventPoolHandler, jobject javaEventDescription, jobject javaEventHandler) {

    // The pointer in JavaEventPoolHandler cannot be null (-1)
    ze_event_pool_handle_t eventPool;
    jclass eventPoolClass = env->GetObjectClass(javaEventPoolHandler);
    jfieldID field = env->GetFieldID(eventPoolClass, "ptrZeEventPoolHandle", "J");
    long ptrEventPool = env->GetLongField(javaEventPoolHandler, field);
    if (ptrEventPool != -1) {
        eventPool = reinterpret_cast<ze_event_pool_handle_t>(ptrEventPool);
    } else {
        std::cout << "[TornadoVM-JNI] Error, Java Event Pool Handler native pointer is null - Invoke zeEventPoolCreate before calling this function " << std::endl;
        return -1;
    }

    jclass klassEventDesc = env->GetObjectClass(javaEventDescription);
    field = env->GetFieldID(klassEventDesc, "stype", "I");
    int stype = env->GetLongField(javaEventDescription, field);
    field = env->GetFieldID(klassEventDesc, "index", "J");
    jint index = env->GetLongField(javaEventDescription, field);
    field = env->GetFieldID(klassEventDesc, "signal", "I");
    jint signal = env->GetLongField(javaEventDescription, field);
    field = env->GetFieldID(klassEventDesc, "wait", "I");
    jint wait = env->GetLongField(javaEventDescription, field);

    ze_event_desc_t eventDesc = {};
    eventDesc.stype = static_cast<ze_structure_type_t>(stype);
    eventDesc.index = index;
    eventDesc.signal = signal;
    eventDesc.wait = wait;

    ze_event_handle_t event;

    ze_result_t result = zeEventCreate(eventPool, &eventDesc, &event);
    LOG_ZE_JNI("zeEventCreate", result);

    // Set fields for Java Event Handle
    jclass eventHandleClass = env->GetObjectClass(javaEventHandler);
    field = env->GetFieldID(eventHandleClass, "ptrZeEventHandle", "J");
    env->SetLongField(javaEventHandler, field, reinterpret_cast<jlong>(event));

    // Set Event Pool Description
    field = env->GetFieldID(klassEventDesc, "ptrZeEventDescription", "J");
    env->SetLongField(javaEventDescription, field, reinterpret_cast<jlong>(&eventDesc));

    field = env->GetFieldID(klassEventDesc, "stype", "I");
    env->SetIntField(javaEventDescription, field, eventDesc.stype);

    field = env->GetFieldID(klassEventDesc, "pNext", "J");
    env->SetLongField(javaEventDescription, field, reinterpret_cast<jlong>(eventDesc.pNext));

    field = env->GetFieldID(klassEventDesc, "index", "J");
    env->SetIntField(javaEventDescription, field, eventDesc.index);

    field = env->GetFieldID(klassEventDesc, "signal", "I");
    env->SetIntField(javaEventDescription, field, eventDesc.signal);

    field = env->GetFieldID(klassEventDesc, "wait", "I");
    env->SetIntField(javaEventDescription, field, eventDesc.wait);

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeEventPoolDestroy_native
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeEventPoolDestroy_1native
        (JNIEnv * env, jobject object, jlong eventHandlePoolPtr) {
    if (eventHandlePoolPtr) {
        ze_event_pool_handle_t eventPool = reinterpret_cast<ze_event_pool_handle_t>(eventHandlePoolPtr);
        ze_result_t result = zeEventPoolDestroy(eventPool);
        LOG_ZE_JNI("zeEventPoolDestroy", result);
        return result;
    }
    return 0;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeEventDestroy_native
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeEventDestroy_1native
        (JNIEnv *env, jobject object, jlong javaEventPtr) {
    if (javaEventPtr != -1) {
        ze_event_handle_t event = reinterpret_cast<ze_event_handle_t>(javaEventPtr);
        ze_result_t result = zeEventDestroy(event);
        LOG_ZE_JNI("zeEventDestroy", result);
        return result;
    }
    return 0;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeMemAllocHost_native
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeHostMemAllocDesc;IILuk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroByteBuffer;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeMemAllocHost_1native
        (JNIEnv *env, jobject, jlong javaContextPtr, jobject javaHostMemAllocDesc, jint allocSize, jint alignment, jobject javaLevelZeroBuffer) {


    ze_context_handle_t context = reinterpret_cast<ze_context_handle_t>(javaContextPtr);

    jclass javaBufferClass = env->GetObjectClass(javaLevelZeroBuffer);
    jfieldID fieldBuffer = env->GetFieldID(javaBufferClass, "ptrBuffer", "J");
    jlong ptrBuffer = env->GetLongField(javaBufferClass, fieldBuffer);

    void* buffer = nullptr;
    if (ptrBuffer != -1) {
        buffer = reinterpret_cast<void *>(ptrBuffer);
    }

    jclass javaHostDescClass = env->GetObjectClass(javaHostMemAllocDesc);
    jfieldID fieldTypeDeviceDesc = env->GetFieldID(javaHostDescClass, "stype", "I");
    int typeDeviceDesc = env->GetIntField(javaHostMemAllocDesc, fieldTypeDeviceDesc);
    jfieldID fieldFlagsDeviceDesc = env->GetFieldID(javaHostDescClass, "flags", "J");
    long flagDeviceDesc = env->GetLongField(javaHostMemAllocDesc, fieldFlagsDeviceDesc);

    ze_host_mem_alloc_desc_t deviceDesc = {};
    deviceDesc.stype = static_cast<ze_structure_type_t>(typeDeviceDesc);
    deviceDesc.flags = flagDeviceDesc;
    deviceDesc.pNext = nullptr;

    ze_result_t result = zeMemAllocHost(context, &deviceDesc, allocSize, alignment, (void**) &buffer);
    LOG_ZE_JNI("zeMemAllocHost", result);
    
    // Set Buffer Pointer and attributes
    jfieldID fieldBufferSize = env->GetFieldID(javaBufferClass, "size", "I");
    jfieldID alignmentField = env->GetFieldID(javaBufferClass, "alignment", "I");
    env->SetLongField(javaLevelZeroBuffer, fieldBuffer, reinterpret_cast<jlong>(buffer));
    env->SetIntField(javaLevelZeroBuffer, fieldBufferSize, allocSize);
    env->SetIntField(javaLevelZeroBuffer, alignmentField, alignment);

    return result;
}