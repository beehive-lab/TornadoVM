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
#include "levelZeroDriver.h"

#include <iostream>
#include <sstream>

#include "ze_api.h"
#include "ze_log.h"

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDriver
 * Method:    zeInit
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDriver_zeInit
    (JNIEnv *, jobject , jint flags) {
	ze_result_t result = zeInit(flags);
    LOG_ZE_JNI("zeInit", result);
	return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDriver
 * Method:    zeDriverGet_native
 * Signature: ([I[J)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDriver_zeDriverGet_1native
    (JNIEnv *env, jobject, jintArray numDrivers, jlongArray zeDriverHandler) {

    ze_driver_handle_t driverHandle = nullptr;
    bool setDeviceNum = false;
    jlong *driverArray;

    if (zeDriverHandler == nullptr) {
        setDeviceNum = true;
    }

    jint *arrayContent = static_cast<jint *>(env->GetIntArrayElements(numDrivers, 0));
    auto driversCount = (uint32_t) arrayContent[0];

    if (!setDeviceNum) {
        driverArray = static_cast<jlong *>(env->GetLongArrayElements(zeDriverHandler, 0));
        driverHandle = reinterpret_cast<ze_driver_handle_t>(driverArray);
    }

    ze_result_t result = zeDriverGet(&driversCount, &driverHandle);
    LOG_ZE_JNI("zeDriverGet", result);

    if (setDeviceNum) {
        arrayContent[0] = driversCount;
    } else {
        int elements = env->GetArrayLength(zeDriverHandler);
        for (int i = 0; i < elements; i++) {
            driverArray[i] = reinterpret_cast<jlong>(*(&driverHandle + i));
        }
        env->ReleaseLongArrayElements(zeDriverHandler, driverArray, 0);
    }
    env->ReleaseIntArrayElements(numDrivers, arrayContent, 0);
    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDriver
 * Method:    zeDeviceGet
 * Signature: (J[ILuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeDevicesHandle;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDriver_zeDeviceGet
        (JNIEnv *env, jobject object, jlong driverHandler, jintArray deviceCountArray, jobject javaDeviceHandler) {

    uint32_t deviceCountNumber = 0;
    jint *numDevices = static_cast<jint *>(env->GetIntArrayElements(deviceCountArray, 0));

    if (numDevices[0] != 0) {
        deviceCountNumber = numDevices[0];
    }

    ze_device_handle_t deviceHandler = nullptr;

    jlongArray longArray;
    jlong* deviceHandlerArray;

    jfieldID fieldArrayPointers;
    jfieldID fieldNumDevices;

    if (javaDeviceHandler != nullptr) {
        jclass descriptionClass = env->GetObjectClass(javaDeviceHandler);
        fieldArrayPointers = env->GetFieldID(descriptionClass, "devicePtr", "[J");
        fieldNumDevices = env->GetFieldID(descriptionClass, "numDevices", "I");
        longArray = reinterpret_cast<jlongArray>(env->GetObjectField(javaDeviceHandler, fieldArrayPointers));
        deviceHandlerArray = env->GetLongArrayElements(longArray, 0);
        deviceHandler = reinterpret_cast<ze_device_handle_t>(deviceHandlerArray);
    }

    auto driver = reinterpret_cast<ze_driver_handle_t>(driverHandler);

    ze_result_t result = zeDeviceGet(driver, &deviceCountNumber, &deviceHandler);
    LOG_ZE_JNI("zeDeviceGet", result);

    if (javaDeviceHandler == nullptr) {
        // update the array that contains the number of devices
        numDevices[0] = deviceCountNumber;
    } else {
        // Update object deviceHandlerArray
        for (int i = 0; i < deviceCountNumber; i++) {
            deviceHandlerArray[i] = reinterpret_cast<jlong>(*(&(deviceHandler) + i));
        }
        env->SetIntField(javaDeviceHandler, fieldNumDevices, deviceCountNumber);
        env->ReleaseLongArrayElements(longArray, deviceHandlerArray, 0);
        env->SetObjectField(javaDeviceHandler, fieldArrayPointers, longArray);
    }

    env->ReleaseIntArrayElements(deviceCountArray, numDevices, 0);

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDriver
 * Method:    zeDriverGetProperties
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeDriverProperties;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDriver_zeDriverGetProperties
        (JNIEnv *env, jobject object, jlong javaDriverHandler, jobject javaDriverProperties) {

    jclass descriptionClass = env->GetObjectClass(javaDriverProperties);
    jfieldID fieldDescriptorPointer = env->GetFieldID(descriptionClass, "nativePointer", "J");
    long valuePointerDescriptor = env->GetLongField(javaDriverProperties, fieldDescriptorPointer);

    jfieldID fieldDescriptorType = env->GetFieldID(descriptionClass, "type", "I");
    auto type = static_cast<ze_structure_type_t>(env->GetIntField(javaDriverProperties, fieldDescriptorType));

    ze_driver_properties_t driverProperties = {};
    ze_driver_properties_t *driverPropertiesPtr;
    if (valuePointerDescriptor != -1) {
        driverPropertiesPtr = reinterpret_cast<ze_driver_properties_t *>(valuePointerDescriptor);
        driverProperties = *(driverPropertiesPtr);
    }

    driverProperties.stype = type;
    auto driver = reinterpret_cast<ze_driver_handle_t>(javaDriverHandler);
    ze_result_t  result = zeDriverGetProperties(driver, &driverProperties);
    LOG_ZE_JNI("zeDriverGetProperties", result);

    valuePointerDescriptor = reinterpret_cast<long>(&(driverProperties));
    env->SetLongField(javaDriverProperties, fieldDescriptorPointer, valuePointerDescriptor);

    jfieldID field = env->GetFieldID(descriptionClass, "uuid", "[I");
    jintArray array = env->NewIntArray(ZE_MAX_DRIVER_UUID_SIZE);
    jint* arr = env->GetIntArrayElements(array, 0);
    for (int i = 0; i < ZE_MAX_DRIVER_UUID_SIZE; i++) {
        arr[i] = driverProperties.uuid.id[i];
    }
    env->ReleaseIntArrayElements(array, arr, 0);
    env->SetObjectField(javaDriverProperties, field, array);

    jfieldID fieldDriverVersion = env->GetFieldID(descriptionClass, "driverVersion", "I");
    env->SetIntField(javaDriverProperties, fieldDriverVersion, driverProperties.driverVersion);

    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDriver
 * Method:    zeDriverGetApiVersion
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeAPIVersion;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDriver_zeDriverGetApiVersion
        (JNIEnv *env, jobject object, jlong javaDriverHandler, jobject javaAPIVersion) {

    jclass descriptionClass = env->GetObjectClass(javaAPIVersion);
    jfieldID fieldAPIVersionPtr = env->GetFieldID(descriptionClass, "apiVersionPtr", "J");
    jfieldID fieldAPIVersionString = env->GetFieldID(descriptionClass, "version", "Ljava/lang/String;");
    long valueAPIVersion;

    auto driver = reinterpret_cast<ze_driver_handle_t>(javaDriverHandler);

    ze_api_version_t version = {};
    ze_result_t result = zeDriverGetApiVersion(driver, &version);
    LOG_ZE_JNI("zeDriverGetApiVersion", result);

    valueAPIVersion = reinterpret_cast<long>(&version);
    env->SetIntField(javaAPIVersion, fieldAPIVersionPtr, valueAPIVersion);

    std::stringstream ss;
    ss << ZE_MAJOR_VERSION(version) << "." << ZE_MINOR_VERSION(version);
    const char* versionString = ss.str().c_str();
    jstring javaString = env->NewStringUTF(versionString);
    env->SetObjectField(javaAPIVersion, fieldAPIVersionString, javaString);
    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDriver
 * Method:    zeContextDestroy
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroDriver_zeContextDestroy
        (JNIEnv *env, jobject object, jlong javaContextHandler) {
    auto context = reinterpret_cast<ze_context_handle_t>(javaContextHandler);
    ze_result_t result = zeContextDestroy(context);
    LOG_ZE_JNI("zeContextDestroy", result);
    return result;
}

