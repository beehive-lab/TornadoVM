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

#include <jni.h>
#include "cuda_jni.h"

extern "C" {

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_natives_NativeCommandQueue
 * Method:    mapOnDeviceMemoryRegion
 * Signature: (JJ)J
 *
 * CUDA device pointers are flat addresses, so mapping a source region onto a
 * destination simply yields the source device pointer (no host map needed).
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_natives_NativeCommandQueue_mapOnDeviceMemoryRegion
        (JNIEnv *env, jclass clazz, jlong destDevicePtr, jlong srcDevicePtr) {
    return srcDevicePtr;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_natives_NativeCommandQueue
 * Method:    mapOnDeviceMemoryNDRegion
 * Signature: (JJJJIJJJ)J
 *
 * STUB / PoC: device-to-device copy of the payload (skipping the TornadoVM
 * segment header). Not on the scalar vectorAdd path.
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_natives_NativeCommandQueue_mapOnDeviceMemoryNDRegion
        (JNIEnv *env, jclass clazz, jlong commandQueuePtr, jlong destDevicePtr, jlong srcDevicePtr, jlong offset,
         jint sizeDataType, jlong headerSize, jlong sizeSource, jlong sizeDest) {
    cuda_queue_t *queue = (cuda_queue_t *) commandQueuePtr;
    long long headerBytes = (long long) headerSize * sizeDataType;
    long long payloadBytes = (long long) sizeDest - headerBytes;
    if (payloadBytes < 0) {
        payloadBytes = 0;
    }
    if (queue != nullptr) {
        cuCtxSetCurrent(queue->context);
    }
    CUresult result = cuMemcpyDtoD(
            (CUdeviceptr) (destDevicePtr + headerBytes),
            (CUdeviceptr) (srcDevicePtr + (offset * sizeDataType) + headerBytes),
            (size_t) payloadBytes);
    LOG_CUDA_AND_VALIDATE("cuMemcpyDtoD", result);
    return destDevicePtr;
}

} // extern "C"
