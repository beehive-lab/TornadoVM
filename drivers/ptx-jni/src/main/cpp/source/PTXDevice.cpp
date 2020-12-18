/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
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