/*
 * MIT License
 *
 * Copyright (c) 2022, APT Group, Department of Computer Science,
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
#include "levelZeroDescriptors.h"
#include <iostream>
#include "ze_api.h"

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_ZeDeviceMemAllocDescriptor
 * Method:    materializeNative_ZeDeviceMemAllocDescriptor
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_ZeDeviceMemAllocDescriptor_materializeNative_1ZeDeviceMemAllocDescriptor
        (JNIEnv *env, jobject thisObject) {
    jclass classDescriptor = env->GetObjectClass(thisObject);

    jfieldID fieldFlags = env->GetFieldID(classDescriptor, "flags", "I");
    ze_device_mem_alloc_flags_t flags = env->GetLongField(thisObject, fieldFlags);

    jfieldID fieldOrdinal = env->GetFieldID(classDescriptor, "ordinal", "I");
    uint32_t ordinal = env->GetIntField(thisObject, fieldOrdinal);

    ze_device_mem_alloc_desc_t *descriptor;
    descriptor = new ze_device_mem_alloc_desc_t {
            ZE_STRUCTURE_TYPE_DEVICE_MEM_ALLOC_DESC,
            nullptr,
            flags,
            ordinal
    };

    ulong ptrToStruct = reinterpret_cast<ulong>(descriptor);
    jfieldID fieldSelfPTr = env->GetFieldID(classDescriptor, "selfPtr", "J");
    env->SetLongField(thisObject, fieldSelfPTr, ptrToStruct);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_ZeFenceDescriptor
 * Method:    materializeNative_ZeFenceDescriptor
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_ZeRelaxedAllocationLimitsExpDescriptor_materializeNative_1ZeFenceDescriptor
        (JNIEnv * env, jobject thisObject) {
    jclass classDescriptor = env->GetObjectClass(thisObject);

    jfieldID fieldFlags = env->GetFieldID(classDescriptor, "flags", "I");
    ze_fence_flags_t flags = env->GetLongField(thisObject, fieldFlags);

    ze_fence_desc_t *descriptor;
    descriptor = new ze_fence_desc_t {
            ZE_STRUCTURE_TYPE_FENCE_DESC,
            nullptr,
            flags,
    };

    ulong ptrToStruct = reinterpret_cast<ulong>(descriptor);
    jfieldID fieldSelfPTr = env->GetFieldID(classDescriptor, "selfPtr", "J");
    env->SetLongField(thisObject, fieldSelfPTr, ptrToStruct);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_ZeRelaxedAllocationLimitsExpDescriptor
 * Method:    materializeNative_ZeRelaxedAllocationLimitsExpDescriptor
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_ZeRelaxedAllocationLimitsExpDescriptor_materializeNative_1ZeRelaxedAllocationLimitsExpDescriptor
        (JNIEnv * env, jobject thisObject) {

    jclass classDescriptor = env->GetObjectClass(thisObject);

    jfieldID fieldFlags = env->GetFieldID(classDescriptor, "flags", "I");
    ze_relaxed_allocation_limits_exp_flags_t flags = env->GetLongField(thisObject, fieldFlags);

    ze_relaxed_allocation_limits_exp_desc_t *exceedCapacity;
    exceedCapacity = new ze_relaxed_allocation_limits_exp_desc_t {
            ZE_STRUCTURE_TYPE_RELAXED_ALLOCATION_LIMITS_EXP_DESC,
            nullptr,
            flags
    };

    ulong ptrToStruct = reinterpret_cast<ulong>(exceedCapacity);
    jfieldID fieldSelfPTr = env->GetFieldID(classDescriptor, "selfPtr", "J");
    env->SetLongField(thisObject, fieldSelfPTr, ptrToStruct);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_ZeEventPoolDescriptor
 * Method:    materializeNative_ZeEventPoolDescriptor
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_ZeRelaxedAllocationLimitsExpDescriptor_materializeNative_1ZeEventPoolDescriptor
        (JNIEnv * env, jobject thisObject) {
    jclass classDescriptor = env->GetObjectClass(thisObject);

    jfieldID fieldFlags = env->GetFieldID(classDescriptor, "flags", "I");
    ze_event_pool_flags_t flags = env->GetIntField(thisObject, fieldFlags);

    jfieldID fieldCount = env->GetFieldID(classDescriptor, "count", "I");
    uint32_t count = env->GetIntField(thisObject, fieldCount);

    ze_event_pool_desc_t *descriptor;
    descriptor = new ze_event_pool_desc_t {
            ZE_STRUCTURE_TYPE_EVENT_POOL_DESC,
            nullptr,
            flags,
            count,
    };

    ulong ptrToStruct = reinterpret_cast<ulong>(descriptor);
    jfieldID fieldSelfPTr = env->GetFieldID(classDescriptor, "selfPtr", "J");
    env->SetLongField(thisObject, fieldSelfPTr, ptrToStruct);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_ZeCommandListDescriptor
 * Method:    materializeNative_ZeCommandListDescriptor
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_ZeRelaxedAllocationLimitsExpDescriptor_materializeNative_1ZeCommandListDescriptor
        (JNIEnv * env, jobject thisObject) {
    jclass classDescriptor = env->GetObjectClass(thisObject);

    jfieldID fieldFlags = env->GetFieldID(classDescriptor, "flags", "I");
    ze_command_list_flags_t flags = env->GetIntField(thisObject, fieldFlags);

    jfieldID fieldCount = env->GetFieldID(classDescriptor, "commandQueueGroupOrdinal", "I");
    uint32_t commandQueueGroupOrdinal = env->GetIntField(thisObject, fieldCount);

    ze_command_list_desc_t *descriptor;
    descriptor = new ze_command_list_desc_t {
            ZE_STRUCTURE_TYPE_COMMAND_LIST_DESC,
            nullptr,
            commandQueueGroupOrdinal,
            flags,
    };

    ulong ptrToStruct = reinterpret_cast<ulong>(descriptor);
    jfieldID fieldSelfPTr = env->GetFieldID(classDescriptor, "selfPtr", "J");
    env->SetLongField(thisObject, fieldSelfPTr, ptrToStruct);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_ZeHostMemAllocDescriptor
 * Method:    materializeNative_ZeHostMemAllocDescriptor
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_ZeRelaxedAllocationLimitsExpDescriptor_materializeNative_1ZeHostMemAllocDescriptor
        (JNIEnv * env, jobject thisObject) {
    jclass classDescriptor = env->GetObjectClass(thisObject);

    jfieldID fieldFlags = env->GetFieldID(classDescriptor, "flags", "I");
    ze_host_mem_alloc_flags_t flags = env->GetIntField(thisObject, fieldFlags);

    ze_host_mem_alloc_desc_t *descriptor;
    descriptor = new ze_host_mem_alloc_desc_t {
            ZE_STRUCTURE_TYPE_HOST_MEM_ALLOC_DESC,
            nullptr,
            flags,
    };

    ulong ptrToStruct = reinterpret_cast<ulong>(descriptor);
    jfieldID fieldSelfPTr = env->GetFieldID(classDescriptor, "selfPtr", "J");
    env->SetLongField(thisObject, fieldSelfPTr, ptrToStruct);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_ZeKernelDescriptor
 * Method:    materializeNative_ZeKernelDescriptor
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_ZeRelaxedAllocationLimitsExpDescriptor_materializeNative_1ZeKernelDescriptor
        (JNIEnv * env, jobject thisObject) {
    jclass classDescriptor = env->GetObjectClass(thisObject);

    jfieldID fieldFlags = env->GetFieldID(classDescriptor, "flags", "I");
    ze_kernel_flags_t flags = env->GetIntField(thisObject, fieldFlags);

    jfieldID fieldKernelName = env->GetFieldID(classDescriptor, "kernelName", "Ljava/lang/String;");
    jstring javaString = (jstring)env->GetObjectField(thisObject, fieldKernelName);
    const char *pKernelName = env->GetStringUTFChars(javaString, 0);

    ze_kernel_desc_t *descriptor;
    descriptor = new ze_kernel_desc_t {
            ZE_STRUCTURE_TYPE_KERNEL_DESC,
            nullptr,
            flags,
            pKernelName,
    };

    ulong ptrToStruct = reinterpret_cast<ulong>(descriptor);
    jfieldID fieldSelfPTr = env->GetFieldID(classDescriptor, "selfPtr", "J");
    env->SetLongField(thisObject, fieldSelfPTr, ptrToStruct);
    env->ReleaseStringUTFChars(javaString, pKernelName);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_ZeCommandQueueDescriptor
 * Method:    materializeNative_ZeCommandQueueDescriptor
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_ZeRelaxedAllocationLimitsExpDescriptor_materializeNative_1ZeCommandQueueDescriptor
        (JNIEnv * env, jobject thisObject) {
    jclass classDescriptor = env->GetObjectClass(thisObject);

    jfieldID fieldFlags = env->GetFieldID(classDescriptor, "flags", "I");
    ze_command_queue_flags_t flags = env->GetIntField(thisObject, fieldFlags);

    jfieldID fieldOrdinal = env->GetFieldID(classDescriptor, "ordinal", "I");
    uint32_t ordinal = env->GetIntField(thisObject, fieldOrdinal);

    jfieldID fieldIndex = env->GetFieldID(classDescriptor, "ordinal", "I");
    uint32_t index = env->GetIntField(thisObject, fieldIndex);

    jfieldID fieldMode = env->GetFieldID(classDescriptor, "mode", "I");
    int mode = env->GetIntField(thisObject, fieldMode);

    jfieldID fieldPriority = env->GetFieldID(classDescriptor, "priority", "I");
    int priority = env->GetIntField(thisObject, fieldPriority);

    ze_command_queue_desc_t *descriptor;
    descriptor = new ze_command_queue_desc_t {
            ZE_STRUCTURE_TYPE_COMMAND_QUEUE_DESC,
            nullptr,
            ordinal,
            index,
            flags,
            static_cast<ze_command_queue_mode_t>(mode),
            static_cast<ze_command_queue_priority_t>(priority)
    };

    ulong ptrToStruct = reinterpret_cast<ulong>(descriptor);
    jfieldID fieldSelfPTr = env->GetFieldID(classDescriptor, "selfPtr", "J");
    env->SetLongField(thisObject, fieldSelfPTr, ptrToStruct);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_ZeModuleDescriptor
 * Method:    materializeNative_ZeModuleDescriptor
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_ZeRelaxedAllocationLimitsExpDescriptor_materializeNative_1ZeModuleDescriptor
        (JNIEnv * env, jobject thisObject) {
    jclass classDescriptor = env->GetObjectClass(thisObject);

    // XXX To me completed here
    ze_module_desc_t *descriptor;
    descriptor = new ze_module_desc_t {
            ZE_STRUCTURE_TYPE_MODULE_DESC,
            nullptr,

    };

    ulong ptrToStruct = reinterpret_cast<ulong>(descriptor);
    jfieldID fieldSelfPTr = env->GetFieldID(classDescriptor, "selfPtr", "J");
    env->SetLongField(thisObject, fieldSelfPTr, ptrToStruct);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_ZeEventDescriptor
 * Method:    materializeNative_ZeEventDescriptor
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_ZeRelaxedAllocationLimitsExpDescriptor_materializeNative_1ZeEventDescriptor
        (JNIEnv * env, jobject thisObject) {
    jclass classDescriptor = env->GetObjectClass(thisObject);

    jfieldID fieldIndex = env->GetFieldID(classDescriptor, "index", "I");
    uint32_t index = env->GetIntField(thisObject, fieldIndex);

    jfieldID fieldSignal = env->GetFieldID(classDescriptor, "index", "I");
    uint32_t signal = env->GetIntField(thisObject, fieldSignal);

    jfieldID fieldWait = env->GetFieldID(classDescriptor, "index", "I");
    uint32_t wait = env->GetIntField(thisObject, fieldWait);

    ze_event_desc_t *descriptor;
    descriptor = new ze_event_desc_t {
            ZE_STRUCTURE_TYPE_EVENT_DESC,
            nullptr,
            index,
            signal,
            wait,
    };

    ulong ptrToStruct = reinterpret_cast<ulong>(descriptor);
    jfieldID fieldSelfPTr = env->GetFieldID(classDescriptor, "selfPtr", "J");
    env->SetLongField(thisObject, fieldSelfPTr, ptrToStruct);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_ZeContextDescriptor
 * Method:    materializeNative_ZeContextDescriptor
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_ZeRelaxedAllocationLimitsExpDescriptor_materializeNative_1ZeContextDescriptor
        (JNIEnv * env, jobject thisObject) {
    jclass classDescriptor = env->GetObjectClass(thisObject);

    jfieldID fieldFlags = env->GetFieldID(classDescriptor, "flags", "I");
    ze_context_flags_t flags = env->GetLongField(thisObject, fieldFlags);

    ze_context_desc_t *descriptor;
    descriptor = new ze_context_desc_t {
            ZE_STRUCTURE_TYPE_CONTEXT_DESC,
            nullptr,
            flags,
    };

    ulong ptrToStruct = reinterpret_cast<ulong>(descriptor);
    jfieldID fieldSelfPTr = env->GetFieldID(classDescriptor, "selfPtr", "J");
    env->SetLongField(thisObject, fieldSelfPTr, ptrToStruct);
}