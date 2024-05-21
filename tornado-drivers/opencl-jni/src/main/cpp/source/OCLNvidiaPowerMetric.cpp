/*
 * MIT License
 *
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
#include <iostream>
#ifdef NVML_IS_SUPPORTED
#include <nvml.h>
#endif
#include "OCLNvidiaPowerMetric.h"
#include "ocl_log.h"

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_power_OCLNvidiaPowerMetric
 * Method:    clNvmlInit
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_power_OCLNvidiaPowerMetric_clNvmlInit
        (JNIEnv *env, jclass) {
#ifdef NVML_IS_SUPPORTED
    nvmlReturn_t result = nvmlInit();
    LOG_NVML_AND_VALIDATE("nvmlInit", result);

    return (jlong) result;
#else
    return (jlong) -1;
#endif
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_power_OCLNvidiaPowerMetric
 * Method:    clNvmlDeviceGetHandleByIndex
 * Signature: (J[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_power_OCLNvidiaPowerMetric_clNvmlDeviceGetHandleByIndex
        (JNIEnv *env, jclass clazz, jlong deviceIndex, jlongArray array1) {
#ifdef NVML_IS_SUPPORTED
    jlong *device = static_cast<jlong *>((array1 != NULL) ? env->GetPrimitiveArrayCritical(array1, NULL)
                                                                      : NULL);
    nvmlReturn_t result = nvmlDeviceGetHandleByIndex(deviceIndex, (nvmlDevice_t*) device);
    LOG_NVML_AND_VALIDATE("nvmlDeviceGetHandleByIndex", result);

    if (array1 != NULL) {
        env->ReleasePrimitiveArrayCritical(array1, device, JNI_ABORT);
    }

    return (jlong) result;
#else
    return (jlong) -1;
#endif
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_power_OCLNvidiaPowerMetric
 * Method:    clNvmlDeviceGetPowerUsage
 * Signature: ([J[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_power_OCLNvidiaPowerMetric_clNvmlDeviceGetPowerUsage
        (JNIEnv *env, jclass clazz, jlongArray array1, jlongArray array2) {
#ifdef NVML_IS_SUPPORTED
    jlong *device = static_cast<jlong *>((array1 != NULL) ? env->GetPrimitiveArrayCritical(array1, NULL)
                                                                      : NULL);
    jlong *powerUsage = static_cast<jlong *>((array2 != NULL) ? env->GetPrimitiveArrayCritical(array2, NULL)
                                                                      : NULL);

    nvmlReturn_t result = nvmlDeviceGetPowerUsage((nvmlDevice_t) *device, (unsigned int*) powerUsage);
    LOG_NVML_AND_VALIDATE("nvmlDeviceGetPowerUsage", result);

    if (array1 != NULL) {
        env->ReleasePrimitiveArrayCritical(array1, device, JNI_ABORT);
    }

    if (array2 != NULL) {
        env->ReleasePrimitiveArrayCritical(array2, powerUsage, JNI_ABORT);
    }

    return (jlong) result;
#else
    return (jlong) -1;
#endif
}
