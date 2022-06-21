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
#include "levelZeroDevice.h"

#include <iostream>
#include <vector>

#include "ze_api.h"
#include "ze_log.h"

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDevice
 * Method:    zeDeviceGetProperties_native
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeDeviceProperties;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDevice_zeDeviceGetProperties_1native
        (JNIEnv *env, jobject object, jlong javaDeviceHandler, jobject javaDeviceProperties) {

    jclass descriptionClass = env->GetObjectClass(javaDeviceProperties);
    jfieldID fieldType = env->GetFieldID(descriptionClass, "stype", "I");
    ze_structure_type_t type = static_cast<ze_structure_type_t>(env->GetIntField(javaDeviceProperties, fieldType));

    ze_device_handle_t device = reinterpret_cast<ze_device_handle_t>(javaDeviceHandler);
    ze_device_properties_t device_properties = {};
    device_properties.stype = type;
    ze_result_t result = zeDeviceGetProperties(device, &device_properties);
    LOG_ZE_JNI("zeDeviceGetProperties", result);

    // Update object
    jfieldID field = env->GetFieldID(descriptionClass, "stype", "I");
    env->SetIntField(javaDeviceProperties, field, device_properties.stype);

    field = env->GetFieldID(descriptionClass, "pNext", "J");
    env->SetLongField(javaDeviceProperties, field, reinterpret_cast<jlong>(device_properties.pNext));

    field = env->GetFieldID(descriptionClass, "type", "I");
    env->SetIntField(javaDeviceProperties, field, device_properties.type);

    field = env->GetFieldID(descriptionClass, "vendorId", "I");
    env->SetIntField(javaDeviceProperties, field, device_properties.vendorId);

    field = env->GetFieldID(descriptionClass, "deviceId", "I");
    env->SetIntField(javaDeviceProperties, field, device_properties.deviceId);

    field = env->GetFieldID(descriptionClass, "flags", "I");
    env->SetIntField(javaDeviceProperties, field, device_properties.flags);

    field = env->GetFieldID(descriptionClass, "subdeviceId", "I");
    env->SetIntField(javaDeviceProperties, field, device_properties.subdeviceId);

    field = env->GetFieldID(descriptionClass, "coreClockRate", "I");
    env->SetIntField(javaDeviceProperties, field, device_properties.coreClockRate);

    field = env->GetFieldID(descriptionClass, "maxMemAllocSize", "J");
    env->SetLongField(javaDeviceProperties, field, device_properties.maxMemAllocSize);

    field = env->GetFieldID(descriptionClass, "maxHardwareContexts", "I");
    env->SetIntField(javaDeviceProperties, field, device_properties.maxHardwareContexts);

    field = env->GetFieldID(descriptionClass, "maxCommandQueuePriority", "I");
    env->SetIntField(javaDeviceProperties, field, device_properties.maxCommandQueuePriority);

    field = env->GetFieldID(descriptionClass, "numThreadsPerEU", "I");
    env->SetIntField(javaDeviceProperties, field, device_properties.numThreadsPerEU);

    field = env->GetFieldID(descriptionClass, "physicalEUSimdWidth", "I");
    env->SetIntField(javaDeviceProperties, field, device_properties.physicalEUSimdWidth);

    field = env->GetFieldID(descriptionClass, "numEUsPerSubslice", "I");
    env->SetIntField(javaDeviceProperties, field, device_properties.numEUsPerSubslice);

    field = env->GetFieldID(descriptionClass, "numSubslicesPerSlice", "I");
    env->SetIntField(javaDeviceProperties, field, device_properties.numSubslicesPerSlice);

    field = env->GetFieldID(descriptionClass, "numSlices", "I");
    env->SetIntField(javaDeviceProperties, field, device_properties.numSlices);

    field = env->GetFieldID(descriptionClass, "timerResolution", "I");
    env->SetIntField(javaDeviceProperties, field, device_properties.timerResolution);

    field = env->GetFieldID(descriptionClass, "timestampValidBits", "I");
    env->SetIntField(javaDeviceProperties, field, device_properties.timestampValidBits);

    field = env->GetFieldID(descriptionClass, "kernelTimestampValidBits", "I");
    env->SetIntField(javaDeviceProperties, field, device_properties.kernelTimestampValidBits);

    field = env->GetFieldID(descriptionClass, "uuid", "[I");
    jintArray array = env->NewIntArray(ZE_MAX_DEVICE_UUID_SIZE);
    jint* arr = env->GetIntArrayElements(array, 0);
    for (int i = 0; i < ZE_MAX_DEVICE_UUID_SIZE; i++) {
        arr[i] = device_properties.uuid.id[i];
    }
    env->ReleaseIntArrayElements(array, arr, 0);
    env->SetObjectField(javaDeviceProperties, field, array);

    field = env->GetFieldID(descriptionClass, "name", "Ljava/lang/String;");
    jstring javaString = env->NewStringUTF(device_properties.name);
    env->SetObjectField(javaDeviceProperties, field, javaString);

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDevice
 * Method:    zeDeviceGetComputeProperties
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeComputeProperties;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDevice_zeDeviceGetComputeProperties
        (JNIEnv *env, jobject object, jlong javaDeviceHandler, jobject javaComputeProperties) {

    jclass descriptionClass = env->GetObjectClass(javaComputeProperties);
    jfieldID fieldType = env->GetFieldID(descriptionClass, "type", "I");
    ze_structure_type_t type = static_cast<ze_structure_type_t>(env->GetIntField(javaComputeProperties, fieldType));

    ze_device_handle_t device = reinterpret_cast<ze_device_handle_t>(javaDeviceHandler);
    ze_device_compute_properties_t compute_properties = {};
    compute_properties.stype = type;
    ze_result_t result = zeDeviceGetComputeProperties(device, &compute_properties);
    LOG_ZE_JNI("zeDeviceGetComputeProperties", result);

    // update Java object
    jfieldID field = env->GetFieldID(descriptionClass, "type", "I");
    env->SetIntField(javaComputeProperties, field, compute_properties.stype);

    field = env->GetFieldID(descriptionClass, "pNext", "J");
    env->SetLongField(javaComputeProperties, field, reinterpret_cast<jlong>(compute_properties.pNext));

    field = env->GetFieldID(descriptionClass, "maxTotalGroupSize", "I");
    env->SetIntField(javaComputeProperties, field, compute_properties.maxTotalGroupSize);

    field = env->GetFieldID(descriptionClass, "maxGroupSizeX", "I");
    env->SetIntField(javaComputeProperties, field, compute_properties.maxGroupSizeX);
    field = env->GetFieldID(descriptionClass, "maxGroupSizeY", "I");
    env->SetIntField(javaComputeProperties, field, compute_properties.maxGroupSizeY);
    field = env->GetFieldID(descriptionClass, "maxGroupSizeZ", "I");
    env->SetIntField(javaComputeProperties, field, compute_properties.maxGroupSizeZ);

    field = env->GetFieldID(descriptionClass, "maxGroupCountX", "I");
    env->SetIntField(javaComputeProperties, field, compute_properties.maxGroupCountX);
    field = env->GetFieldID(descriptionClass, "maxGroupCountY", "I");
    env->SetIntField(javaComputeProperties, field, compute_properties.maxGroupCountY);
    field = env->GetFieldID(descriptionClass, "maxGroupCountZ", "I");
    env->SetIntField(javaComputeProperties, field, compute_properties.maxGroupCountZ);

    field = env->GetFieldID(descriptionClass, "maxSharedLocalMemory", "I");
    env->SetIntField(javaComputeProperties, field, compute_properties.maxSharedLocalMemory);

    field = env->GetFieldID(descriptionClass, "numSubGroupSizes", "I");
    env->SetIntField(javaComputeProperties, field, compute_properties.numSubGroupSizes);

    field = env->GetFieldID(descriptionClass, "subGroupSizes", "[I");
    jintArray array = env->NewIntArray(ZE_SUBGROUPSIZE_COUNT);
    jint* arr = env->GetIntArrayElements(array, 0);
    for (int i = 0; i < ZE_SUBGROUPSIZE_COUNT; i++) {
        arr[i] = compute_properties.subGroupSizes[i];
    }
    env->ReleaseIntArrayElements(array, arr, 0);
    env->SetObjectField(javaComputeProperties, field, array);

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDevice
 * Method:    zeDeviceGetImageProperties
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeDeviceImageProperties;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDevice_zeDeviceGetImageProperties
        (JNIEnv * env, jobject objet, jlong javaDeviceHandler, jobject javaImageProperties) {

    jclass descriptionClass = env->GetObjectClass(javaImageProperties);
    jfieldID fieldType = env->GetFieldID(descriptionClass, "type", "I");
    ze_structure_type_t type = static_cast<ze_structure_type_t>(env->GetIntField(javaImageProperties, fieldType));

    ze_device_handle_t device = reinterpret_cast<ze_device_handle_t>(javaDeviceHandler);
    ze_device_image_properties_t image_properties = {};
    image_properties.stype = type;
    ze_result_t result = zeDeviceGetImageProperties(device, &image_properties);
    LOG_ZE_JNI("zeDeviceGetImageProperties", result);

    // Update the Java object
    jfieldID field = env->GetFieldID(descriptionClass, "type", "I");
    env->SetIntField(javaImageProperties, field, image_properties.stype);

    field = env->GetFieldID(descriptionClass, "pNext", "J");
    env->SetLongField(javaImageProperties, field, reinterpret_cast<jlong>(image_properties.pNext));

    field = env->GetFieldID(descriptionClass, "maxImageDims1D", "J");
    env->SetIntField(javaImageProperties, field, image_properties.maxImageDims1D);
    field = env->GetFieldID(descriptionClass, "maxImageDims2D", "J");
    env->SetIntField(javaImageProperties, field, image_properties.maxImageDims2D);
    field = env->GetFieldID(descriptionClass, "maxImageDims3D", "J");
    env->SetIntField(javaImageProperties, field, image_properties.maxImageDims3D);

    field = env->GetFieldID(descriptionClass, "maxImageBufferSize", "J");
    env->SetIntField(javaImageProperties, field, image_properties.maxImageBufferSize);

    field = env->GetFieldID(descriptionClass, "maxImageArraySlices", "J");
    env->SetIntField(javaImageProperties, field, image_properties.maxImageArraySlices);

    field = env->GetFieldID(descriptionClass, "maxSamplers", "J");
    env->SetIntField(javaImageProperties, field, image_properties.maxSamplers);

    field = env->GetFieldID(descriptionClass, "maxReadImageArgs", "J");
    env->SetIntField(javaImageProperties, field, image_properties.maxReadImageArgs);

    field = env->GetFieldID(descriptionClass, "maxWriteImageArgs", "J");
    env->SetIntField(javaImageProperties, field, image_properties.maxWriteImageArgs);

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDevice
 * Method:    zeDeviceGetMemoryProperties_native
 * Signature: (J[I[Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeMemoryProperties;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDevice_zeDeviceGetMemoryProperties_1native
        (JNIEnv *env, jobject object, jlong javaDeviceHandler, jintArray javaMemoryCount, jobjectArray javaMemoryPropertiesArray) {

    ze_device_memory_properties_t *memoryProperties = nullptr;

    jint *arrayContent = static_cast<jint *>(env->GetIntArrayElements(javaMemoryCount, 0));
    uint32_t memoryCount = (uint32_t) arrayContent[0];

    if (javaMemoryPropertiesArray != nullptr) {
        memoryProperties = new ze_device_memory_properties_t[memoryCount];
        for (uint32_t mem = 0; mem < memoryCount; mem++) {
            memoryProperties[mem].stype = ZE_STRUCTURE_TYPE_DEVICE_MEMORY_PROPERTIES;
            memoryProperties[mem].pNext = nullptr;
        }
    }

    ze_device_handle_t device = reinterpret_cast<ze_device_handle_t>(javaDeviceHandler);

    ze_result_t result = zeDeviceGetMemoryProperties(device, &memoryCount,  memoryProperties);
    LOG_ZE_JNI("zeDeviceGetMemoryProperties", result);

    if (javaMemoryPropertiesArray != nullptr) {
        // set the values back to Java
        for (int i = 0; i < memoryCount; i++) {
            jobject javaMemoryProperty = static_cast<jobject>(env->GetObjectArrayElement(javaMemoryPropertiesArray, i));
            jclass descriptionClass = env->GetObjectClass(javaMemoryProperty);
            jfieldID field = env->GetFieldID(descriptionClass, "type", "I");
            env->SetIntField(javaMemoryProperty, field, memoryProperties[i].stype);

            field = env->GetFieldID(descriptionClass, "pNext", "J");
            env->SetLongField(javaMemoryProperty, field, reinterpret_cast<jlong>(memoryProperties[i].pNext));

            field = env->GetFieldID(descriptionClass, "flags", "J");
            env->SetLongField(javaMemoryProperty, field, memoryProperties[i].flags);

            field = env->GetFieldID(descriptionClass, "maxClockRate", "J");
            env->SetLongField(javaMemoryProperty, field, memoryProperties[i].maxClockRate);

            field = env->GetFieldID(descriptionClass, "maxBusWidth", "J");
            env->SetLongField(javaMemoryProperty, field, memoryProperties[i].maxBusWidth);

            field = env->GetFieldID(descriptionClass, "totalSize", "J");
            env->SetLongField(javaMemoryProperty, field, memoryProperties[i].totalSize);

            jstring javaString = env->NewStringUTF(memoryProperties[i].name);
            field = env->GetFieldID(descriptionClass, "name", "Ljava/lang/String;");
            env->SetObjectField(javaMemoryProperty, field, javaString);

            field = env->GetFieldID(descriptionClass, "ptrZeMemoryProperty", "J");
            env->SetLongField(javaMemoryProperty, field, reinterpret_cast<jlong>(&memoryProperties[i]));
        }
    } else {
        arrayContent[0] = memoryCount;
        env->ReleaseIntArrayElements(javaMemoryCount, arrayContent, 0);
    }
    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDevice
 * Method:    zeDeviceGetMemoryAccessProperties
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeMemoryAccessProperties;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDevice_zeDeviceGetMemoryAccessProperties
        (JNIEnv *env, jobject object, jlong javaDeviceHandler, jobject javaMemoryAccessProperties) {

    jclass descriptionClass = env->GetObjectClass(javaMemoryAccessProperties);
    jfieldID fieldType = env->GetFieldID(descriptionClass, "type", "I");
    ze_structure_type_t type = static_cast<ze_structure_type_t>(env->GetIntField(javaMemoryAccessProperties, fieldType));

    ze_device_handle_t device = reinterpret_cast<ze_device_handle_t>(javaDeviceHandler);
    ze_device_memory_access_properties_t memory_access_properties = {};
    memory_access_properties.stype = type;
    ze_result_t result = zeDeviceGetMemoryAccessProperties(device, &memory_access_properties);
    LOG_ZE_JNI("zeDeviceGetMemoryAccessProperties", result);

    // Update the Java Object
    jfieldID field = env->GetFieldID(descriptionClass, "type", "I");
    env->SetIntField(javaMemoryAccessProperties, field, memory_access_properties.stype);

    field = env->GetFieldID(descriptionClass, "pNext", "J");
    env->SetLongField(javaMemoryAccessProperties, field, reinterpret_cast<jlong>(memory_access_properties.pNext));

    field = env->GetFieldID(descriptionClass, "hostAllocCapabilities", "J");
    env->SetLongField(javaMemoryAccessProperties, field, memory_access_properties.hostAllocCapabilities);

    field = env->GetFieldID(descriptionClass, "deviceAllocCapabilities", "J");
    env->SetLongField(javaMemoryAccessProperties, field, memory_access_properties.deviceAllocCapabilities);

    field = env->GetFieldID(descriptionClass, "sharedSingleDeviceAllocCapabilities", "J");
    env->SetLongField(javaMemoryAccessProperties, field, memory_access_properties.sharedSingleDeviceAllocCapabilities);

    field = env->GetFieldID(descriptionClass, "sharedCrossDeviceAllocCapabilities", "J");
    env->SetLongField(javaMemoryAccessProperties, field, memory_access_properties.sharedCrossDeviceAllocCapabilities);

    field = env->GetFieldID(descriptionClass, "sharedSystemAllocCapabilities", "J");
    env->SetLongField(javaMemoryAccessProperties, field, memory_access_properties.sharedSystemAllocCapabilities);

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDevice
 * Method:    zeDeviceGetCacheProperties_native
 * Signature: (J[I[Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeDeviceCacheProperties;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDevice_zeDeviceGetCacheProperties_1native
        (JNIEnv *env, jobject object, jlong javaDeviceHandler, jintArray javaCacheCount, jobjectArray javaCachePropertiesArray) {

    ze_device_cache_properties_t *cacheMemoryProperties = nullptr;

    jint *arrayContent = static_cast<jint *>(env->GetIntArrayElements(javaCacheCount, 0));
    uint32_t cacheCount = (uint32_t) arrayContent[0];

    if (javaCachePropertiesArray != nullptr) {
        cacheMemoryProperties = new ze_device_cache_properties_t[cacheCount];
        for (uint32_t mem = 0; mem < cacheCount; mem++) {
            cacheMemoryProperties[mem].stype = ZE_STRUCTURE_TYPE_DEVICE_CACHE_PROPERTIES;
            cacheMemoryProperties[mem].pNext = nullptr;
        }
    }

    ze_device_handle_t device = reinterpret_cast<ze_device_handle_t>(javaDeviceHandler);

    ze_result_t result = zeDeviceGetCacheProperties(device, &cacheCount, cacheMemoryProperties);
    LOG_ZE_JNI("zeDeviceGetMemoryProperties", result);

    if (javaCachePropertiesArray != nullptr) {
        // set the values back to Java
        for (int i = 0; i < cacheCount; i++) {
            jobject javaMemoryProperty = static_cast<jobject>(env->GetObjectArrayElement(javaCachePropertiesArray, i));
            jclass descriptionClass = env->GetObjectClass(javaMemoryProperty);
            jfieldID field = env->GetFieldID(descriptionClass, "type", "I");
            env->SetIntField(javaMemoryProperty, field, cacheMemoryProperties[i].stype);

            field = env->GetFieldID(descriptionClass, "pNext", "J");
            env->SetLongField(javaMemoryProperty, field, reinterpret_cast<jlong>(cacheMemoryProperties[i].pNext));

            field = env->GetFieldID(descriptionClass, "flags", "J");
            env->SetLongField(javaMemoryProperty, field, cacheMemoryProperties[i].flags);

            field = env->GetFieldID(descriptionClass, "cacheSize", "I");
            env->SetLongField(javaMemoryProperty, field, cacheMemoryProperties[i].cacheSize);
        }
    } else {
        arrayContent[0] = cacheCount;
        env->ReleaseIntArrayElements(javaCacheCount, arrayContent, 0);
    }
    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDevice
 * Method:    zeDeviceGetModuleProperties_native
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeDeviceModuleProperties;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDevice_zeDeviceGetModuleProperties_1native
        (JNIEnv *env, jobject object, jlong javaDeviceHandler, jobject javaDeviceModuleProperties) {

    ze_device_handle_t device = reinterpret_cast<ze_device_handle_t>(javaDeviceHandler);

    jclass descriptionClass = env->GetObjectClass(javaDeviceModuleProperties);
    jfieldID fieldType = env->GetFieldID(descriptionClass, "stype", "I");
    ze_structure_type_t type = static_cast<ze_structure_type_t>(env->GetIntField(javaDeviceModuleProperties, fieldType));
    jfieldID fieldPNext = env->GetFieldID(descriptionClass, "pNext", "J");
    long pNext = static_cast<long>(env->GetLongField(javaDeviceModuleProperties, fieldPNext));
    void* ptrNext = nullptr;
    if (pNext != -1) {
        ptrNext = reinterpret_cast<void *>(pNext);
    }

    ze_device_module_properties_t deviceModuleProperties;
    deviceModuleProperties.stype = type;
    deviceModuleProperties.pNext = ptrNext;

    ze_result_t result = zeDeviceGetModuleProperties(device, &deviceModuleProperties);
    LOG_ZE_JNI("zeDeviceGetModuleProperties", result);

    // Update the device module structure
    jfieldID field = env->GetFieldID(descriptionClass, "pNext", "J");
    env->SetLongField(javaDeviceModuleProperties, field, reinterpret_cast<jlong>(deviceModuleProperties.pNext));

    field = env->GetFieldID(descriptionClass, "spirvVersionSupported", "I");
    env->SetIntField(javaDeviceModuleProperties, field, deviceModuleProperties.spirvVersionSupported);

    field = env->GetFieldID(descriptionClass, "flags", "I");
    env->SetIntField(javaDeviceModuleProperties, field, deviceModuleProperties.flags);

    field = env->GetFieldID(descriptionClass, "fp16flags", "I");
    env->SetIntField(javaDeviceModuleProperties, field, deviceModuleProperties.fp16flags);

    field = env->GetFieldID(descriptionClass, "fp32flags", "I");
    env->SetIntField(javaDeviceModuleProperties, field, deviceModuleProperties.fp32flags);

    field = env->GetFieldID(descriptionClass, "fp64flags", "I");
    env->SetIntField(javaDeviceModuleProperties, field, deviceModuleProperties.fp64flags);

    field = env->GetFieldID(descriptionClass, "maxArgumentsSize", "I");
    env->SetIntField(javaDeviceModuleProperties, field, deviceModuleProperties.maxArgumentsSize);

    field = env->GetFieldID(descriptionClass, "printBufferSize", "I");
    env->SetIntField(javaDeviceModuleProperties, field, deviceModuleProperties.printfBufferSize);

    field = env->GetFieldID(descriptionClass, "nativeKernelSupported", "[I");

    jintArray intArray = env->NewIntArray(ZE_MAX_NATIVE_KERNEL_UUID_SIZE);
    jint *ids = env->GetIntArrayElements(intArray, 0);
    for (int i = 0; i  < ZE_MAX_NATIVE_KERNEL_UUID_SIZE; i++) {
        ids[i] = deviceModuleProperties.nativeKernelSupported.id[i];
    }
    env->ReleaseIntArrayElements(intArray, ids, 0);
    env->SetObjectField(javaDeviceModuleProperties, field, intArray);

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDevice
 * Method:    zeDeviceGetCommandQueueGroupProperties_native
 * Signature: (J[I[Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeCommandQueueGroupProperties;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDevice_zeDeviceGetCommandQueueGroupProperties_1native
        (JNIEnv *env, jobject object, jlong javaDeviceHandler, jintArray javaNumGroups, jobjectArray javaQueuePropertiesArray) {

    ze_command_queue_group_properties_t *queueProperties = nullptr;
    ze_device_handle_t device = reinterpret_cast<ze_device_handle_t>(javaDeviceHandler);

    jint *arrayContent = static_cast<jint *>(env->GetIntArrayElements(javaNumGroups, 0));
    uint32_t numQueueGroups = (uint32_t) arrayContent[0];

    if (javaQueuePropertiesArray != nullptr) {
        queueProperties = new ze_command_queue_group_properties_t[numQueueGroups];
    }

    ze_result_t result = zeDeviceGetCommandQueueGroupProperties(device, &numQueueGroups, queueProperties);
    LOG_ZE_JNI("zeDeviceGetCommandQueueGroupProperties", result);

    if (javaQueuePropertiesArray != nullptr) {
        for (int i = 0; i < numQueueGroups; i++) {
            jobject javaMemoryProperty = static_cast<jobject>(env->GetObjectArrayElement(javaQueuePropertiesArray, i));
            jclass descriptionClass = env->GetObjectClass(javaMemoryProperty);
            jfieldID field = env->GetFieldID(descriptionClass, "type", "I");
            env->SetIntField(javaMemoryProperty, field, queueProperties[i].stype);

            field = env->GetFieldID(descriptionClass, "pNext", "J");
            env->SetLongField(javaMemoryProperty, field, reinterpret_cast<jlong>(queueProperties[i].pNext));

            field = env->GetFieldID(descriptionClass, "flags", "I");
            env->SetIntField(javaMemoryProperty, field, queueProperties[i].flags);

            field = env->GetFieldID(descriptionClass, "maxMemoryFillPatternSize", "I");
            env->SetIntField(javaMemoryProperty, field, queueProperties[i].maxMemoryFillPatternSize);

            field = env->GetFieldID(descriptionClass, "numQueues", "I");
            env->SetIntField(javaMemoryProperty, field, queueProperties[i].numQueues);
        }
    } else {
        arrayContent[0] = numQueueGroups;
        env->ReleaseIntArrayElements(javaNumGroups, arrayContent, 0);
    }

    return result;
}
