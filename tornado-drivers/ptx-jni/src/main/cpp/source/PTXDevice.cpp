/*
 * MIT License
 *
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
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

#include <jni.h>
#include <cuda.h>

#include <iostream>
#include "PTXDevice.h"
#include "ptx_log.h"


/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXDevice
 * Method:    cuDeviceGet
 * Signature: (I)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXDevice_cuDeviceGet
  (JNIEnv *env, jclass clazz, jint device_id) {
    CUdevice *dev = static_cast<CUdevice *>(malloc(sizeof(CUdevice)));
    CUresult result = cuDeviceGet(dev, (int) device_id);
    LOG_PTX_AND_VALIDATE("cuDeviceGet", result);
    return (jlong) dev;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXDevice
 * Method:    cuDeviceGetName
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXDevice_cuDeviceGetName
  (JNIEnv *env, jclass clazz, jlong cuDevice) {
    CUdevice *dev = (CUdevice *) cuDevice;
    char name[256];
    CUresult result = cuDeviceGetName(name, 256, *dev);
    LOG_PTX_AND_VALIDATE("cuDeviceGetName", result);
    return env->NewStringUTF(name);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXDevice
 * Method:    cuDeviceGetAttribute
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXDevice_cuDeviceGetAttribute
  (JNIEnv *env, jclass clazz, jlong cuDevice, jint attr_id) {
    CUdevice *dev = (CUdevice *) cuDevice;
    int attribute_value;
    CUresult result = cuDeviceGetAttribute(&attribute_value, (CUdevice_attribute) attr_id, *dev);
    LOG_PTX_AND_VALIDATE("cuDeviceGetAttribute", result);
    return (jint) attribute_value;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXDevice
 * Method:    cuDeviceTotalMem
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXDevice_cuDeviceTotalMem
  (JNIEnv *env, jclass clazz, jlong cuDevice) {
    CUdevice *dev = (CUdevice *) cuDevice;
    size_t mem_in_bytes;
    CUresult result = cuDeviceTotalMem(&mem_in_bytes, *dev);
    LOG_PTX_AND_VALIDATE("cuDeviceTotalMem", result);
    return (jlong) mem_in_bytes;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXDevice
 * Method:    cuMemGetInfo
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXDevice_cuMemGetInfo
  (JNIEnv *env, jclass clazz) {
    size_t free;
    size_t total;
    CUresult result = cuMemGetInfo(&free, &total);
    LOG_PTX_AND_VALIDATE("cuMemGetInfo", result);
    return free;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXDevice
 * Method:    cuDriverGetVersion
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXDevice_cuDriverGetVersion
  (JNIEnv *env, jclass clazz) {
    int driver_version;
    CUresult result = cuDriverGetVersion(&driver_version);
    LOG_PTX_AND_VALIDATE("cuDriverGetVersion", result);
    return (jint) driver_version;
}