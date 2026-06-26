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
#include <cstdlib>
#include "cuda_jni.h"

extern "C" {

#if defined(_WIN32)
#include <malloc.h>
#endif

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAContext
 * Method:    clReleaseContext
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAContext_clReleaseContext
        (JNIEnv *env, jobject obj, jlong context_id) {
    cuda_context_t *ctx = (cuda_context_t *) context_id;
    if (ctx == nullptr) {
        return;
    }
    CUresult result = cuCtxDestroy(ctx->context);
    LOG_CUDA_AND_VALIDATE("cuCtxDestroy", result);
    delete ctx;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAContext
 * Method:    clGetContextInfo
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAContext_clGetContextInfo
        (JNIEnv *env, jobject obj, jlong context_id, jint param_name, jbyteArray array) {
    // Context info is only used for debugging logs in the cloned Java; zero-fill.
    jbyte *buf = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(array, NULL));
    jsize len = env->GetArrayLength(array);
    std::memset(buf, 0, len);
    env->ReleasePrimitiveArrayCritical(array, buf, 0);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAContext
 * Method:    clCreateCommandQueue
 * Signature: (JJJ)J
 *
 * Maps to a CUstream pinned to the device's context.
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAContext_clCreateCommandQueue
        (JNIEnv *env, jobject obj, jlong context_id, jlong device_id, jlong properties) {
    cuda_context_t *ctx = (cuda_context_t *) context_id;
    if (ctx == nullptr) {
        return 0;
    }
    CUresult result = cuCtxSetCurrent(ctx->context);
    LOG_CUDA_AND_VALIDATE("cuCtxSetCurrent", result);

    cuda_queue_t *queue = new cuda_queue_t();
    queue->context = ctx->context;
    queue->device = ctx->device;
    queue->properties = (long) properties;
    result = cuStreamCreate(&queue->stream, CU_STREAM_NON_BLOCKING);
    LOG_CUDA_AND_VALIDATE("cuStreamCreate", result);
    if (result != CUDA_SUCCESS) {
        delete queue;
        return 0;
    }
    return (jlong) queue;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAContext
 * Method:    allocateOffHeapMemory
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAContext_allocateOffHeapMemory
        (JNIEnv *env, jobject obj, jlong size, jlong alignment) {
    void *ptr = nullptr;
#if defined(_WIN32)
    ptr = _aligned_malloc((size_t) size, (size_t) alignment);
    if (ptr == nullptr) {
        printf("CUDA off-heap memory allocation (aligned_malloc) failed.\n");
    }
#else
    int rc = posix_memalign(&ptr, (size_t) alignment, (size_t) size);
    if (rc != 0) {
        printf("CUDA off-heap memory allocation (posix_memalign) failed. Error value: %d.\n", rc);
        return 0;
    }
#endif
    std::memset(ptr, 0, (size_t) size);
    return (jlong) ptr;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAContext
 * Method:    freeOffHeapMemory
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAContext_freeOffHeapMemory
        (JNIEnv *env, jobject obj, jlong address) {
    free((void *) address);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAContext
 * Method:    asByteBuffer
 * Signature: (JJ)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAContext_asByteBuffer
        (JNIEnv *env, jobject obj, jlong address, jlong capacity) {
    return env->NewDirectByteBuffer((void *) address, capacity);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAContext
 * Method:    createBuffer
 * Signature: (JJJJ)Luk/ac/manchester/tornado/drivers/cuda/CUDAContext/CUDABufferResult;
 *
 * Allocates device memory with cuMemAlloc. The returned CUdeviceptr is the
 * "buffer" the Java side stores and later passes to read/write/launch.
 */
JNIEXPORT jobject JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAContext_createBuffer
        (JNIEnv *env, jobject obj, jlong context_id, jlong flags, jlong size, jlong host_ptr) {
    jclass resultClass = env->FindClass("uk/ac/manchester/tornado/drivers/cuda/CUDAContext$CUDABufferResult");
    jmethodID constructorId = env->GetMethodID(resultClass, "<init>", "(JJI)V");

    cuda_context_t *ctx = (cuda_context_t *) context_id;
    int status = 0;
    CUdeviceptr dev_ptr = 0;
    if (ctx != nullptr) {
        CUresult result = cuCtxSetCurrent(ctx->context);
        LOG_CUDA_AND_VALIDATE("cuCtxSetCurrent", result);
        result = cuMemAlloc(&dev_ptr, (size_t) size);
        LOG_CUDA_AND_VALIDATE("cuMemAlloc", result);
        status = (int) result;
    } else {
        status = (int) CUDA_ERROR_INVALID_CONTEXT;
    }
    return env->NewObject(resultClass, constructorId, (jlong) dev_ptr, host_ptr, status);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAContext
 * Method:    createManagedBuffer
 * Signature: (JJ)Luk/ac/manchester/tornado/drivers/cuda/CUDAContext/CUDABufferResult;
 *
 * Allocates a CUDA Managed (Unified) Memory buffer with cuMemAllocManaged. The
 * returned CUdeviceptr is addressable from both host and device; the CUDA runtime
 * pages it on demand and can over-subscribe physical VRAM. CU_MEM_ATTACH_GLOBAL
 * makes the region visible to all streams and the CPU immediately, matching the
 * access model TornadoVM expects. The allocation is zero-initialised by the
 * runtime, so no explicit cuMemsetD8 is required for write-only buffers.
 *
 * Deallocation uses the same cuMemFree path as cuMemAlloc (see clReleaseMemObject).
 */
JNIEXPORT jobject JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAContext_createManagedBuffer
        (JNIEnv *env, jobject obj, jlong context_id, jlong size) {
    jclass resultClass = env->FindClass("uk/ac/manchester/tornado/drivers/cuda/CUDAContext$CUDABufferResult");
    jmethodID constructorId = env->GetMethodID(resultClass, "<init>", "(JJI)V");

    cuda_context_t *ctx = (cuda_context_t *) context_id;
    int status = 0;
    CUdeviceptr dev_ptr = 0;
    if (ctx != nullptr) {
        CUresult result = cuCtxSetCurrent(ctx->context);
        LOG_CUDA_AND_VALIDATE("cuCtxSetCurrent", result);
        result = cuMemAllocManaged(&dev_ptr, (size_t) size, CU_MEM_ATTACH_GLOBAL);
        LOG_CUDA_AND_VALIDATE("cuMemAllocManaged", result);
        status = (int) result;
    } else {
        status = (int) CUDA_ERROR_INVALID_CONTEXT;
    }
    // host_ptr = 0: no host-side pointer is associated in Phase 1 (copies retained).
    return env->NewObject(resultClass, constructorId, (jlong) dev_ptr, (jlong) 0, status);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAContext
 * Method:    registerHostMemory
 * Signature: (JJJ)J
 *
 * Zero-copy path: pins an existing host region (the array's off-heap
 * MemorySegment) and maps it into the device address space via
 * cuMemHostRegister(..., CU_MEMHOSTREGISTER_DEVICEMAP). The kernel then accesses
 * the host memory directly -- no cuMemcpy H2D/D2H -- over the CPU<->GPU
 * interconnect (NVLink-C2C on Grace-Hopper, PCIe on discrete GPUs). Returns the
 * device-accessible pointer (equal to the host pointer under UVA), or 0 on
 * failure so the caller can fall back to the copy path.
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAContext_registerHostMemory
        (JNIEnv *env, jobject obj, jlong context_id, jlong host_ptr, jlong size) {
    cuda_context_t *ctx = (cuda_context_t *) context_id;
    if (ctx == nullptr || host_ptr == 0 || size <= 0) {
        return 0;
    }
    CUresult result = cuCtxSetCurrent(ctx->context);
    LOG_CUDA_AND_VALIDATE("cuCtxSetCurrent", result);
    result = cuMemHostRegister((void *) host_ptr, (size_t) size, CU_MEMHOSTREGISTER_DEVICEMAP);
    if (result != CUDA_SUCCESS) {
        LOG_CUDA_AND_VALIDATE("cuMemHostRegister", result);
        return 0;
    }
    CUdeviceptr dev_ptr = 0;
    result = cuMemHostGetDevicePointer(&dev_ptr, (void *) host_ptr, 0);
    if (result != CUDA_SUCCESS) {
        LOG_CUDA_AND_VALIDATE("cuMemHostGetDevicePointer", result);
        cuMemHostUnregister((void *) host_ptr);
        return 0;
    }
    return (jlong) dev_ptr;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAContext
 * Method:    unregisterHostMemory
 * Signature: (JJ)V
 *
 * Releases a host region pinned by registerHostMemory.
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAContext_unregisterHostMemory
        (JNIEnv *env, jobject obj, jlong context_id, jlong host_ptr) {
    if (host_ptr == 0) {
        return;
    }
    cuda_context_t *ctx = (cuda_context_t *) context_id;
    if (ctx != nullptr) {
        cuCtxSetCurrent(ctx->context);
    }
    CUresult result = cuMemHostUnregister((void *) host_ptr);
    LOG_CUDA_AND_VALIDATE("cuMemHostUnregister", result);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAContext
 * Method:    memSetZero
 * Signature: (JJJ)I
 *
 * Zero-initialises a device buffer. cuMemAlloc returns uninitialised device
 * memory, and TornadoVM reuses pooled device buffers across executions, so a
 * write-only output that the kernel never writes (e.g. an early-returning
 * kernel) would otherwise read back stale/garbage data. cuMemsetD8 sets N
 * 8-bit values (i.e. N bytes) to the given byte; here we set the whole buffer
 * to 0. This is the synchronous variant so the zeroing is complete before the
 * buffer is used by a subsequent host->device copy or kernel launch.
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAContext_memSetZero
        (JNIEnv *env, jobject obj, jlong context_id, jlong device_ptr, jlong bytes) {
    cuda_context_t *ctx = (cuda_context_t *) context_id;
    if (ctx == nullptr || device_ptr == 0 || bytes <= 0) {
        return (jint) CUDA_ERROR_INVALID_VALUE;
    }
    CUresult result = cuCtxSetCurrent(ctx->context);
    LOG_CUDA_AND_VALIDATE("cuCtxSetCurrent", result);
    result = cuMemsetD8((CUdeviceptr) device_ptr, (unsigned char) 0, (size_t) bytes);
    LOG_CUDA_AND_VALIDATE("cuMemsetD8", result);
    return (jint) result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAContext
 * Method:    createSubBuffer
 * Signature: (JJI[B)J
 *
 * STUB: CUDA has no direct sub-buffer concept; not on the vectorAdd path.
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAContext_createSubBuffer
        (JNIEnv *env, jobject obj, jlong buffer, jlong flags, jint create_type, jbyteArray create_info) {
    return buffer;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAContext
 * Method:    clReleaseMemObject
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAContext_clReleaseMemObject
        (JNIEnv *env, jobject obj, jlong mem_id) {
    if (mem_id == 0) {
        return;
    }
    CUresult result = cuMemFree((CUdeviceptr) mem_id);
    LOG_CUDA_AND_VALIDATE("cuMemFree", result);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAContext
 * Method:    clCreateProgramWithSource
 * Signature: (J[B[J)J
 *
 * The byte[] holds the generated CUDA C source. We stash it; NVRTC compilation
 * happens later in clBuildProgram (the OpenCL clone's two-step create+build).
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAContext_clCreateProgramWithSource
        (JNIEnv *env, jobject obj, jlong context_id, jbyteArray data, jlongArray lengths) {
    cuda_context_t *ctx = (cuda_context_t *) context_id;
    cuda_program_t *program = new cuda_program_t();
    program->context = (ctx != nullptr) ? ctx->context : nullptr;
    program->build_status = -1; // CL_BUILD_NONE
    program->module_loaded = false;

    jsize src_len = env->GetArrayLength(data);
    jbyte *src = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(data, NULL));
    program->source.assign((const char *) src, (size_t) src_len);
    env->ReleasePrimitiveArrayCritical(data, src, JNI_ABORT);

    return (jlong) program;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAContext
 * Method:    clCreateProgramWithBinary
 * Signature: (JJ[B[J)J
 *
 * Accepts pre-compiled PTX as the "binary" and skips NVRTC.
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAContext_clCreateProgramWithBinary
        (JNIEnv *env, jobject obj, jlong context_id, jlong device_id, jbyteArray data, jlongArray lengths) {
    cuda_context_t *ctx = (cuda_context_t *) context_id;
    cuda_program_t *program = new cuda_program_t();
    program->context = (ctx != nullptr) ? ctx->context : nullptr;
    program->module_loaded = false;

    jsize bin_len = env->GetArrayLength(data);
    jbyte *bin = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(data, NULL));
    program->binary.assign((const char *) bin, (size_t) bin_len);
    env->ReleasePrimitiveArrayCritical(data, bin, JNI_ABORT);
    program->build_status = 0; // ready to load; clBuildProgram will load the module

    return (jlong) program;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAContext
 * Method:    clCreateProgramWithIL
 * Signature: (J[B[J)J
 *
 * STUB: SPIR-V ingestion is not supported by CUDA; return -1 so the Java side
 * raises TornadoNoOpenCLPlatformException, matching the OpenCL <2.1 behaviour.
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAContext_clCreateProgramWithIL
        (JNIEnv *env, jobject obj, jlong context_id, jbyteArray spirv, jlongArray lengths) {
    return -1;
}

} // extern "C"
