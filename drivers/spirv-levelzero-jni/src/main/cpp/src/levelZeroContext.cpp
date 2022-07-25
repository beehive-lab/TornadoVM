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
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeContextDescriptor;[J)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeContextCreate
    (JNIEnv *env, jobject object, jlong javaDriverHandler, jobject descriptorObject, jlongArray contextArray) {

    jclass descriptorClass = env->GetObjectClass(descriptorObject);
    jfieldID fieldDescriptorType = env->GetFieldID(descriptorClass, "stype", "I");
    ze_structure_type_t type = static_cast<ze_structure_type_t>(env->GetIntField(descriptorObject, fieldDescriptorType));

    ze_driver_handle_t driverHandle = reinterpret_cast<ze_driver_handle_t>(javaDriverHandler);

    jlong *contextJavaArray = static_cast<jlong *>(env->GetLongArrayElements(contextArray, 0));
    ze_context_handle_t context;
    if (contextJavaArray[0] != 0) {
        context = reinterpret_cast<ze_context_handle_t>(contextJavaArray[0]);
    }

    jfieldID fieldDescriptorPointer = env->GetFieldID(descriptorClass, "nativePointer", "J");
    long valuePointerDescriptor = env->GetLongField(descriptorObject, fieldDescriptorPointer);

    ze_context_desc_t contextDesc = {};
    ze_context_desc_t *contextDescPtr;
    if (valuePointerDescriptor != -1) {
        contextDescPtr = reinterpret_cast<ze_context_desc_t *>(valuePointerDescriptor);
        contextDesc = *(contextDescPtr);
    }

    contextDesc.stype = type;
    ze_result_t result = zeContextCreate(driverHandle, &contextDesc, &context);
    LOG_ZE_JNI("zeContextCreate", result);

    contextJavaArray[0] = reinterpret_cast<jlong>(context);
    env->ReleaseLongArrayElements(contextArray, contextJavaArray, 0);

    valuePointerDescriptor = reinterpret_cast<long>(&(contextDesc));
    env->SetLongField(descriptorObject, fieldDescriptorPointer, valuePointerDescriptor);

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeCommandQueueCreate_native
 * Signature: (JJLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeCommandQueueDescriptor;Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeCommandQueueHandle;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeCommandQueueCreate_1native
        (JNIEnv *env, jobject object, jlong javaContextPtr , jlong javaDeviceHandler, jobject javaCommandQueueDescriptor, jobject javaCommandQueue) {

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

    // Reconstruct commandQueueDescriptor
    ze_command_queue_desc_t cmdQueueDesc = {};
    jclass commandDescriptorClass = env->GetObjectClass(javaCommandQueueDescriptor);
    field = env->GetFieldID(commandDescriptorClass, "ptrZeCommandDescriptor", "J");
    long ptrZeCommandDescriptor = env->GetLongField(javaCommandQueueDescriptor, field);
    if (ptrZeCommandDescriptor != -1) {
        ze_command_queue_desc_t *cmdQueueDescPtr = reinterpret_cast<ze_command_queue_desc_t*>(ptrZeCommandDescriptor);
        cmdQueueDesc = *cmdQueueDescPtr;
    }

    field = env->GetFieldID(commandDescriptorClass, "stype", "I");
    int type = env->GetIntField(javaCommandQueueDescriptor, field);

    field = env->GetFieldID(commandDescriptorClass, "ordinal", "J");
    long ordinal = env->GetLongField(javaCommandQueueDescriptor, field);

    field = env->GetFieldID(commandDescriptorClass, "index", "J");
    int index = env->GetLongField(javaCommandQueueDescriptor, field);

    field = env->GetFieldID(commandDescriptorClass, "mode", "I");
    int mode = env->GetIntField(javaCommandQueueDescriptor, field);

    field = env->GetFieldID(commandDescriptorClass, "priority", "I");
    int priority = env->GetIntField(javaCommandQueueDescriptor, field);

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

    // Set command queue Descriptor
    field = env->GetFieldID(commandDescriptorClass, "ptrZeCommandDescriptor", "J");
    env->SetLongField(javaCommandQueueDescriptor, field, reinterpret_cast<jlong>(&cmdQueueDesc));

    field = env->GetFieldID(commandDescriptorClass, "stype", "I");
    env->SetIntField(javaCommandQueueDescriptor, field, cmdQueueDesc.stype);

    field = env->GetFieldID(commandDescriptorClass, "pNext", "J");
    env->SetLongField(javaCommandQueueDescriptor, field, reinterpret_cast<jlong>(cmdQueueDesc.pNext));

    field = env->GetFieldID(commandDescriptorClass, "ordinal", "J");
    env->SetLongField(javaCommandQueueDescriptor, field, cmdQueueDesc.ordinal);

    field = env->GetFieldID(commandDescriptorClass, "index", "J");
    env->SetLongField(javaCommandQueueDescriptor, field, cmdQueueDesc.index);

    field = env->GetFieldID(commandDescriptorClass, "flags", "I");
    env->SetIntField(javaCommandQueueDescriptor, field, cmdQueueDesc.flags);

    field = env->GetFieldID(commandDescriptorClass, "mode", "I");
    env->SetIntField(javaCommandQueueDescriptor, field, cmdQueueDesc.mode);

    field = env->GetFieldID(commandDescriptorClass, "priority", "I");
    env->SetIntField(javaCommandQueueDescriptor, field, cmdQueueDesc.priority);

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeCommandListCreate_native
 * Signature: (JJLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeCommandListDescriptor;Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeCommandQueueListHandle;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeCommandListCreate_1native
        (JNIEnv *env, jobject object, jlong javaContextPtr, jlong javaDeviceHandler, jobject javaCommandListDescriptor, jobject javaCommandList) {

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

    // Reconstruct command list Descriptor
    ze_command_list_desc_t cmdListDesc = {};
    jclass commanddescriptorClass = env->GetObjectClass(javaCommandListDescriptor);
    jfieldID field = env->GetFieldID(commanddescriptorClass, "ptrZeCommandListDescriptor", "J");
    long ptrZeCommandDescriptor = env->GetLongField(javaCommandListDescriptor, field);
    if (ptrZeCommandDescriptor != -1) {
        ze_command_list_desc_t *cmdListDescPtr = reinterpret_cast<ze_command_list_desc_t*>(ptrZeCommandDescriptor);
        cmdListDesc = *cmdListDescPtr;
    }

    field = env->GetFieldID(commanddescriptorClass, "stype", "I");
    int type = env->GetIntField(javaCommandListDescriptor, field);

    field = env->GetFieldID(commanddescriptorClass, "commandQueueGroupOrdinal", "J");
    long ordinal = env->GetLongField(javaCommandListDescriptor, field);

    cmdListDesc.stype = static_cast<ze_structure_type_t>(type);
    cmdListDesc.commandQueueGroupOrdinal = ordinal;

    ze_result_t result = zeCommandListCreate(context, device, &cmdListDesc, &commandList);
    LOG_ZE_JNI("zeCommandListCreate", result);

    // Set command queue into to the Java Command Queue Object
    field = env->GetFieldID(commandListClass, "ptrZeCommandListHandle", "J");
    env->SetLongField(javaCommandList, field, reinterpret_cast<jlong>(commandList));

    // Set command queue Descriptor
    field = env->GetFieldID(commanddescriptorClass, "ptrZeCommandListDescriptor", "J");
    env->SetLongField(javaCommandListDescriptor, field, reinterpret_cast<jlong>(&cmdListDesc));

    field = env->GetFieldID(commanddescriptorClass, "stype", "I");
    env->SetIntField(javaCommandListDescriptor, field, cmdListDesc.stype);

    field = env->GetFieldID(commanddescriptorClass, "pNext", "J");
    env->SetLongField(javaCommandListDescriptor, field, reinterpret_cast<jlong>(cmdListDesc.pNext));

    field = env->GetFieldID(commanddescriptorClass, "commandQueueGroupOrdinal", "J");
    env->SetLongField(javaCommandListDescriptor, field, cmdListDesc.commandQueueGroupOrdinal);

    field = env->GetFieldID(commanddescriptorClass, "flags", "I");
    env->SetIntField(javaCommandListDescriptor, field, cmdListDesc.flags);

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeCommandListCreateImmediate_native
 * Signature: (JJLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeCommandQueueDescriptor;Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeCommandQueueListHandle;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeCommandListCreateImmediate_1native
        (JNIEnv * env, jobject object, jlong javaContextPtr, jlong javaDeviceHandler, jobject javaCommandQueueDescriptor, jobject javaCommandList) {

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

    // Reconstruct command queue Descriptor
    ze_command_queue_desc_t commandQueueDesc = {};
    jclass commanddescriptorClass = env->GetObjectClass(javaCommandQueueDescriptor);
    field = env->GetFieldID(commanddescriptorClass, "ptrZeCommandDescriptor", "J");
    long ptrZeCommandDescriptor = env->GetLongField(javaCommandQueueDescriptor, field);
    if (ptrZeCommandDescriptor != -1) {
        ze_command_queue_desc_t *cmdQueueDescPtr = reinterpret_cast<ze_command_queue_desc_t*>(ptrZeCommandDescriptor);
        commandQueueDesc = *cmdQueueDescPtr;
    }

    field = env->GetFieldID(commanddescriptorClass, "stype", "I");
    int type = env->GetIntField(javaCommandQueueDescriptor, field);

    field = env->GetFieldID(commanddescriptorClass, "ordinal", "J");
    long ordinal = env->GetLongField(javaCommandQueueDescriptor, field);

    field = env->GetFieldID(commanddescriptorClass, "index", "J");
    int index = env->GetIntField(javaCommandQueueDescriptor, field);

    field = env->GetFieldID(commanddescriptorClass, "flags", "I");
    int flags = env->GetIntField(javaCommandQueueDescriptor, field);

    field = env->GetFieldID(commanddescriptorClass, "priority", "I");
    int priority = env->GetIntField(javaCommandQueueDescriptor, field);

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

    // Set command queue Descriptor
    field = env->GetFieldID(commanddescriptorClass, "ptrZeCommandDescriptor", "J");
    env->SetLongField(javaCommandQueueDescriptor, field, reinterpret_cast<jlong>(&commandQueueDesc));

    field = env->GetFieldID(commanddescriptorClass, "stype", "I");
    env->SetIntField(javaCommandQueueDescriptor, field, commandQueueDesc.stype);

    field = env->GetFieldID(commanddescriptorClass, "pNext", "J");
    env->SetLongField(javaCommandQueueDescriptor, field, reinterpret_cast<jlong>(commandQueueDesc.pNext));

    field = env->GetFieldID(commanddescriptorClass, "ordinal", "J");
    env->SetLongField(javaCommandQueueDescriptor, field, commandQueueDesc.ordinal);

    field = env->GetFieldID(commanddescriptorClass, "index", "J");
    env->SetIntField(javaCommandQueueDescriptor, field, commandQueueDesc.index);

    field = env->GetFieldID(commanddescriptorClass, "flags", "I");
    env->SetIntField(javaCommandQueueDescriptor, field, commandQueueDesc.flags);

    field = env->GetFieldID(commanddescriptorClass, "mode", "I");
    env->SetIntField(javaCommandQueueDescriptor, field, commandQueueDesc.mode);

    field = env->GetFieldID(commanddescriptorClass, "priority", "I");
    env->SetIntField(javaCommandQueueDescriptor, field, commandQueueDesc.priority);

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeMemAllocShared_native
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeDeviceMemAllocDescriptor;Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeHostMemAllocDescriptor;JJJLuk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroBufferInteger;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeMemAllocShared_1native
        (JNIEnv *env, jobject object, jlong javaContextPtr, jobject javaDeviceMemAllocDesc, jobject javaHostMemAllocDesc, jlong bufferSize, jlong aligmnent, jlong javaDeviceHandler, jobject javaLevelZeroBuffer) {

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

    jfieldID fieldPNextMemAlloc = env->GetFieldID(javaDeviceMemAllocDescClass, "pNext", "J");
    ulong pnextDeviceAlloc = env->GetLongField(javaDeviceMemAllocDesc, fieldPNextMemAlloc);


    ze_device_mem_alloc_desc_t deviceDesc = {};
    deviceDesc.stype = static_cast<ze_structure_type_t>(typeDeviceDesc);
    deviceDesc.ordinal = ordinalDeviceDesc;
    deviceDesc.flags = flagDeviceDesc;
    if (pnextDeviceAlloc != -1) {
        deviceDesc.pNext = reinterpret_cast<void *>(pnextDeviceAlloc);
    }

    jclass javaHostMemAllocDescClass = env->GetObjectClass(javaHostMemAllocDesc);
    jfieldID fieldTypeHostDesc = env->GetFieldID(javaHostMemAllocDescClass, "stype", "I");
    int typeHostDesc = env->GetIntField(javaHostMemAllocDesc, fieldTypeHostDesc);
    jfieldID fieldFlagsHostDesc = env->GetFieldID(javaHostMemAllocDescClass, "flags", "J");
    long flagsHostDesc = env->GetLongField(javaHostMemAllocDesc, fieldFlagsHostDesc);

    jfieldID fieldPNextHostAlloc = env->GetFieldID(javaDeviceMemAllocDescClass, "pNext", "J");
    ulong pnextHostAlloc = env->GetLongField(javaDeviceMemAllocDesc, fieldPNextHostAlloc);

    ze_host_mem_alloc_desc_t hostDesc;
    hostDesc.stype = static_cast<ze_structure_type_t>(typeHostDesc);
    hostDesc.flags = flagsHostDesc;
    if (pnextHostAlloc != -1) {
        hostDesc.pNext = reinterpret_cast<void *>(pnextHostAlloc);
    }

    ze_result_t result = zeMemAllocShared(context, &deviceDesc, &hostDesc, bufferSize, aligmnent, device, &buffer);
    LOG_ZE_JNI("zeMemAllocShared", result);

    // Set Buffer Pointer
    env->SetLongField(javaLevelZeroBuffer, fieldBuffer, reinterpret_cast<jlong>(buffer));

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeMemAllocShared_nativeByte
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeDeviceMemAllocDescriptor;Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeHostMemAllocDescriptor;JJJLuk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroByteBuffer;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeMemAllocShared_1nativeByte
        (JNIEnv *env, jobject object, jlong javaContextPtr, jobject javaDeviceMemAllocDesc, jobject javaHostMemAllocDesc, jlong bufferSize, jlong aligmnent, jlong javaDeviceHandler, jobject javaLevelZeroBuffer) {

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

    jfieldID fieldPNextMemAlloc = env->GetFieldID(javaDeviceMemAllocDescClass, "pNext", "J");
    ulong pnextDeviceAlloc = env->GetLongField(javaDeviceMemAllocDesc, fieldPNextMemAlloc);

    ze_device_mem_alloc_desc_t deviceDesc = {};
    deviceDesc.stype = static_cast<ze_structure_type_t>(typeDeviceDesc);
    deviceDesc.ordinal = ordinalDeviceDesc;
    deviceDesc.flags = flagDeviceDesc;

    if (pnextDeviceAlloc != -1) {
        deviceDesc.pNext = reinterpret_cast<void *>(pnextDeviceAlloc);
    }

    jclass javaHostMemAllocDescClass = env->GetObjectClass(javaHostMemAllocDesc);
    jfieldID fieldTypeHostDesc = env->GetFieldID(javaHostMemAllocDescClass, "stype", "I");
    int typeHostDesc = env->GetIntField(javaHostMemAllocDesc, fieldTypeHostDesc);
    jfieldID fieldFlagsHostDesc = env->GetFieldID(javaHostMemAllocDescClass, "flags", "J");
    long flagsHostDesc = env->GetLongField(javaHostMemAllocDesc, fieldFlagsHostDesc);

    jfieldID fieldPNextHostAlloc = env->GetFieldID(javaHostMemAllocDescClass, "pNext", "J");
    ulong pnextHostAlloc = env->GetLongField(javaHostMemAllocDesc, fieldPNextHostAlloc);

    ze_host_mem_alloc_desc_t hostDesc;
    hostDesc.stype = static_cast<ze_structure_type_t>(typeHostDesc);
    hostDesc.flags = flagsHostDesc;

    if (pnextHostAlloc != -1) {
        hostDesc.pNext = reinterpret_cast<void *>(pnextHostAlloc);
    }

    ze_result_t result = zeMemAllocShared(context, &deviceDesc, &hostDesc, bufferSize, aligmnent, device, &buffer);
    LOG_ZE_JNI("zeMemAllocShared - [ByteBuffer]", result);

    // Set Buffer Pointer
    env->SetLongField(javaLevelZeroBuffer, fieldBuffer, reinterpret_cast<jlong>(buffer));

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeMemAllocDevice_native
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeDeviceMemAllocDescriptor;JJJLuk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroByteBuffer;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeMemAllocDevice_1native
        (JNIEnv *env, jobject object, jlong javaContextPtr, jobject javaDeviceMemAllocDesc, jlong allocSize, jlong alignment, jlong javaDeviceHandler, jobject javaLevelZeroBuffer) {

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

    jfieldID fieldPNextMemAlloc = env->GetFieldID(javaDeviceMemAllocDescClass, "pNext", "J");
    ulong pnextDeviceAlloc = env->GetLongField(javaDeviceMemAllocDesc, fieldPNextMemAlloc);

    ze_device_mem_alloc_desc_t deviceDesc = {};
    deviceDesc.stype = static_cast<ze_structure_type_t>(typeDeviceDesc);
    deviceDesc.ordinal = ordinalDeviceDesc;
    deviceDesc.flags = flagDeviceDesc;

    if (pnextDeviceAlloc != -1) {
        deviceDesc.pNext = reinterpret_cast<void *>(pnextDeviceAlloc);
    }

    ze_result_t result = zeMemAllocDevice(context, &deviceDesc, allocSize, alignment, device, &buffer);
    LOG_ZE_JNI("zeMemAllocDevice", result)

    // Set Buffer Pointer and attributes
    jfieldID fieldBufferSize = env->GetFieldID(javaBufferClass, "size", "J");
    jfieldID alignmentField = env->GetFieldID(javaBufferClass, "alignment", "J");
    env->SetLongField(javaLevelZeroBuffer, fieldBuffer, reinterpret_cast<ulong>(buffer));
    env->SetLongField(javaLevelZeroBuffer, fieldBufferSize, allocSize);
    env->SetLongField(javaLevelZeroBuffer, alignmentField, alignment);

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeMemAllocDevice_nativeLong
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeDeviceMemAllocDescriptor;JJJLuk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroBufferLong;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeMemAllocDevice_1nativeLong
        (JNIEnv * env, jobject object, jlong javaContextPtr, jobject javaDeviceMemAllocDesc, jlong allocSize, jlong alignment, jlong javaDeviceHandler, jobject javaLevelZeroBuffer) {
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

    jfieldID fieldPNextMemAlloc = env->GetFieldID(javaDeviceMemAllocDescClass, "pNext", "J");
    ulong pnextDeviceAlloc = env->GetLongField(javaDeviceMemAllocDesc, fieldPNextMemAlloc);


    ze_device_mem_alloc_desc_t deviceDesc = {};
    deviceDesc.stype = static_cast<ze_structure_type_t>(typeDeviceDesc);
    deviceDesc.ordinal = ordinalDeviceDesc;
    deviceDesc.flags = flagDeviceDesc;

    if (pnextDeviceAlloc != -1) {
        deviceDesc.pNext = reinterpret_cast<void *>(pnextDeviceAlloc);
    }

    ze_result_t result = zeMemAllocDevice(context, &deviceDesc, allocSize, alignment, device, &buffer);
    LOG_ZE_JNI("zeMemAllocDevice - [LONG]", result)

    // Set Buffer Pointer and attributes
    jfieldID fieldBufferSize = env->GetFieldID(javaBufferClass, "size", "J");
    jfieldID alignmentField = env->GetFieldID(javaBufferClass, "alignment", "J");
    env->SetLongField(javaLevelZeroBuffer, fieldBuffer, reinterpret_cast<ulong>(buffer));
    env->SetLongField(javaLevelZeroBuffer, fieldBufferSize, allocSize);
    env->SetLongField(javaLevelZeroBuffer, alignmentField, alignment);

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeModuleCreate_nativeWithPath
 * Signature: (JJLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeModuleDescriptor;Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeModuleHandle;Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeBuildLogHandle;Ljava/lang/String;)I
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

        // update module Descriptor object
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

        env->ReleaseStringUTFChars(objectString, buildFlags);
        env->ReleaseStringUTFChars(pathToBinary, fileName);

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
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeEventPoolDescriptor;IJLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeEventPoolHandle;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeEventPoolCreate_1native
        (JNIEnv *env, jobject object, jlong javaContextPtr, jobject javaEventPoolDescriptor, jint numDevices, jlong javaDeviceHandler, jobject javaEventPool) {

    ze_context_handle_t context = reinterpret_cast<ze_context_handle_t>(javaContextPtr);
    ze_device_handle_t  device = reinterpret_cast<ze_device_handle_t>(javaDeviceHandler);

    jclass klassEventPoolDescriptor = env->GetObjectClass(javaEventPoolDescriptor);
    jfieldID field = env->GetFieldID(klassEventPoolDescriptor, "stype", "I");
    int stype = env->GetIntField(javaEventPoolDescriptor, field);
    field = env->GetFieldID(klassEventPoolDescriptor, "count", "I");
    jint count = env->GetIntField(javaEventPoolDescriptor, field);
    field = env->GetFieldID(klassEventPoolDescriptor, "flags", "I");
    jint flags = env->GetIntField(javaEventPoolDescriptor, field);
    field = env->GetFieldID(klassEventPoolDescriptor, "pNext", "J");
    jint pNext = env->GetLongField(javaEventPoolDescriptor, field);

    ze_event_pool_desc_t eventPoolDescriptor = {};
    eventPoolDescriptor.stype = static_cast<ze_structure_type_t>(stype);
    eventPoolDescriptor.pNext = reinterpret_cast<const void *>(pNext);
    eventPoolDescriptor.count = count;
    eventPoolDescriptor.flags = flags;

    ze_event_pool_handle_t eventPool = nullptr;

    ze_result_t result = zeEventPoolCreate(context, &eventPoolDescriptor, numDevices, &device, &eventPool);
    LOG_ZE_JNI("zeEventPoolCreate", result);

    // Set fields for Java Event Pool Handle
    jclass eventPoolHandlerClass = env->GetObjectClass(javaEventPool);
    field = env->GetFieldID(eventPoolHandlerClass, "ptrZeEventPoolHandle", "J");
    env->SetLongField(javaEventPool, field, reinterpret_cast<jlong>(eventPool));

    // Set Event Pool Descriptor
    field = env->GetFieldID(klassEventPoolDescriptor, "ptrZeEventPoolDescriptor", "J");
    env->SetLongField(javaEventPoolDescriptor, field, reinterpret_cast<jlong>(&eventPoolDescriptor));

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext
 * Method:    zeEventCreate_native
 * Signature: (Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeEventPoolHandle;Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeEventDescriptor;Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeEventHandle;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeEventCreate_1native
        (JNIEnv *env, jobject object, jobject javaEventPoolHandler, jobject javaEventDescriptor, jobject javaEventHandler) {

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

    jclass klassEventDesc = env->GetObjectClass(javaEventDescriptor);
    field = env->GetFieldID(klassEventDesc, "stype", "I");
    int stype = env->GetLongField(javaEventDescriptor, field);
    field = env->GetFieldID(klassEventDesc, "index", "J");
    jint index = env->GetLongField(javaEventDescriptor, field);
    field = env->GetFieldID(klassEventDesc, "signal", "I");
    jint signal = env->GetLongField(javaEventDescriptor, field);
    field = env->GetFieldID(klassEventDesc, "wait", "I");
    jint wait = env->GetLongField(javaEventDescriptor, field);

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

    // Set Event Pool Descriptor
    field = env->GetFieldID(klassEventDesc, "ptrZeEventDescriptor", "J");
    env->SetLongField(javaEventDescriptor, field, reinterpret_cast<jlong>(&eventDesc));

    field = env->GetFieldID(klassEventDesc, "stype", "I");
    env->SetIntField(javaEventDescriptor, field, eventDesc.stype);

    field = env->GetFieldID(klassEventDesc, "pNext", "J");
    env->SetLongField(javaEventDescriptor, field, reinterpret_cast<jlong>(eventDesc.pNext));

    field = env->GetFieldID(klassEventDesc, "index", "J");
    env->SetIntField(javaEventDescriptor, field, eventDesc.index);

    field = env->GetFieldID(klassEventDesc, "signal", "I");
    env->SetIntField(javaEventDescriptor, field, eventDesc.signal);

    field = env->GetFieldID(klassEventDesc, "wait", "I");
    env->SetIntField(javaEventDescriptor, field, eventDesc.wait);

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
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeHostMemAllocDescriptor;JJLuk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroByteBuffer;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroContext_zeMemAllocHost_1native
        (JNIEnv *env, jobject, jlong javaContextPtr, jobject javaHostMemAllocDesc, jlong allocSize, jlong alignment, jobject javaLevelZeroBuffer) {


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

    jfieldID fieldPNextMemAlloc = env->GetFieldID(javaHostDescClass, "pNext", "J");
    ulong pnextHostAlloc = env->GetLongField(javaHostMemAllocDesc, fieldPNextMemAlloc);

    ze_host_mem_alloc_desc_t hostDescriptor = {};
    hostDescriptor.stype = static_cast<ze_structure_type_t>(typeDeviceDesc);
    hostDescriptor.flags = flagDeviceDesc;
    hostDescriptor.pNext = nullptr;


    if (pnextHostAlloc != -1) {
        hostDescriptor.pNext = reinterpret_cast<void *>(pnextHostAlloc);
    }

    ze_result_t result = zeMemAllocHost(context, &hostDescriptor, allocSize, alignment, (void**) &buffer);
    LOG_ZE_JNI("zeMemAllocHost", result);
    
    // Set Buffer Pointer and attributes
    jfieldID fieldBufferSize = env->GetFieldID(javaBufferClass, "size", "J");
    jfieldID alignmentField = env->GetFieldID(javaBufferClass, "alignment", "J");
    env->SetLongField(javaLevelZeroBuffer, fieldBuffer, reinterpret_cast<jlong>(buffer));
    env->SetLongField(javaLevelZeroBuffer, fieldBufferSize, allocSize);
    env->SetLongField(javaLevelZeroBuffer, alignmentField, alignment);

    return result;
}