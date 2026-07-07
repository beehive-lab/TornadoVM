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

/* OpenCL cl_kernel_info value queried by the Java clone (CUDAKernelInfo). */
#define CL_KERNEL_FUNCTION_NAME 0x1190

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAKernel
 * Method:    clReleaseKernel
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAKernel_clReleaseKernel
        (JNIEnv *env, jclass clazz, jlong kernel_id) {
    cuda_kernel_t *kernel = (cuda_kernel_t *) kernel_id;
    delete kernel; // CUfunction is owned by the module, nothing to release here.
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAKernel
 * Method:    clSetKernelArg
 * Signature: (JIJ[B)V
 *
 * Stores the argument bytes into the kernel's arg list, growing it as needed so
 * that index i maps to slot i. cuLaunchKernel consumes these via kernelParams.
 * A null byte[] denotes a __shared__ (local-memory) arg of the given size;
 * CUDA expresses dynamic shared memory through cuLaunchKernel's sharedMemBytes,
 * so for MVP scalar kernels we record an empty slot (no per-arg shared mem).
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAKernel_clSetKernelArg
        (JNIEnv *env, jclass clazz, jlong kernel_id, jint index, jlong size, jbyteArray array) {
    cuda_kernel_t *kernel = (cuda_kernel_t *) kernel_id;
    if (kernel == nullptr) {
        return;
    }
    if ((jsize) kernel->arg_data.size() <= index) {
        kernel->arg_data.resize(index + 1);
    }
    std::vector<char> &slot = kernel->arg_data[index];
    if (array == NULL) {
        // Local/shared-memory placeholder; no value passed by reference.
        slot.assign((size_t) size, 0);
    } else {
        jsize len = env->GetArrayLength(array);
        jbyte *value = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(array, 0));
        slot.assign((const char *) value, (const char *) value + len);
        env->ReleasePrimitiveArrayCritical(array, value, JNI_ABORT);
    }
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAKernel
 * Method:    clSetKernelArgRef
 * Signature: (JIJ)V
 *
 * Stores a device pointer argument by value (8 bytes).
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAKernel_clSetKernelArgRef
        (JNIEnv *env, jclass clazz, jlong kernel_id, jint index, jlong buffer) {
    cuda_kernel_t *kernel = (cuda_kernel_t *) kernel_id;
    if (kernel == nullptr) {
        return;
    }
    if ((jsize) kernel->arg_data.size() <= index) {
        kernel->arg_data.resize(index + 1);
    }
    std::vector<char> &slot = kernel->arg_data[index];
    slot.assign((const char *) &buffer, (const char *) &buffer + sizeof(jlong));
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAKernel
 * Method:    clGetKernelInfo
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAKernel_clGetKernelInfo
        (JNIEnv *env, jclass clazz, jlong kernel_id, jint kernel_info, jbyteArray array) {
    cuda_kernel_t *kernel = (cuda_kernel_t *) kernel_id;
    jbyte *buf = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(array, 0));
    jsize len = env->GetArrayLength(array);
    std::memset(buf, 0, len);

    if (kernel != nullptr && kernel_info == CL_KERNEL_FUNCTION_NAME) {
        jsize n = (jsize) kernel->name.size();
        if (n >= len) {
            n = len - 1;
        }
        if (n > 0) {
            std::memcpy(buf, kernel->name.c_str(), n);
        }
        if (len > n) {
            buf[n] = 0;
        }
    }
    env->ReleasePrimitiveArrayCritical(array, buf, 0);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAKernel
 * Method:    cuOccupancyMaxPotentialBlockSize
 * Signature: (J)I
 *
 * Returns the block size (threads/block) that maximises occupancy for this kernel,
 * accounting for its register and shared-memory usage. Used by the scheduler to pick
 * a launchable block for register-heavy kernels. Returns 0 on failure.
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAKernel_cuOccupancyMaxPotentialBlockSize
        (JNIEnv *env, jclass clazz, jlong kernel_id) {
    cuda_kernel_t *kernel = (cuda_kernel_t *) kernel_id;
    if (kernel == nullptr) {
        return 0;
    }
    int min_grid_size = 0;
    int block_size = 0;
    CUresult result = cuOccupancyMaxPotentialBlockSize(&min_grid_size, &block_size, kernel->function, 0, 0, 0);
    LOG_CUDA_AND_VALIDATE("cuOccupancyMaxPotentialBlockSize", result);
    if (result != CUDA_SUCCESS) {
        return 0;
    }
    return block_size;
}

} // extern "C"
