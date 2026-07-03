/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

/*
 * NVML power metrics are DEFERRED per the plan. These stubs report success with
 * a zero power reading so the cloned Java power handler degrades gracefully.
 */

#include <jni.h>

extern "C" {

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_power_CUDANvidiaPowerMetricHandler
 * Method:    clNvmlInit
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_power_CUDANvidiaPowerMetricHandler_clNvmlInit
        (JNIEnv *env, jclass clazz) {
    return 0; // NVML_SUCCESS
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_power_CUDANvidiaPowerMetricHandler
 * Method:    clNvmlDeviceGetHandleByIndex
 * Signature: (J[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_power_CUDANvidiaPowerMetricHandler_clNvmlDeviceGetHandleByIndex
        (JNIEnv *env, jclass clazz, jlong index, jlongArray device) {
    return 0; // NVML_SUCCESS
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_power_CUDANvidiaPowerMetricHandler
 * Method:    clNvmlDeviceGetPowerUsage
 * Signature: ([J[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_power_CUDANvidiaPowerMetricHandler_clNvmlDeviceGetPowerUsage
        (JNIEnv *env, jclass clazz, jlongArray device, jlongArray powerUsage) {
    if (powerUsage != NULL && env->GetArrayLength(powerUsage) > 0) {
        jlong zero = 0;
        env->SetLongArrayRegion(powerUsage, 0, 1, &zero);
    }
    return 0; // NVML_SUCCESS
}

} // extern "C"
