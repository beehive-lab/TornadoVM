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

/* OpenCL cl_command_queue_info values (cloned CUDACommandQueueInfo). */
#define CL_QUEUE_CONTEXT 0x1090
#define CL_QUEUE_DEVICE  0x1091

/*
 * Returns true when the queue's stream is currently capturing into a CUDA
 * graph. During capture the stream must not be synchronised (that invalidates
 * the capture), so the transfer helpers below defer their cuStreamSynchronize.
 */
static bool stream_is_capturing(cuda_queue_t *queue) {
    if (queue == nullptr) {
        return false;
    }
    CUstreamCaptureStatus status = CU_STREAM_CAPTURE_STATUS_NONE;
    CUresult result = cuStreamIsCapturing(queue->stream, &status);
    return (result == CUDA_SUCCESS) && (status == CU_STREAM_CAPTURE_STATUS_ACTIVE);
}

/*
 * Creates and records a CUevent on the queue's stream, returning its boxed
 * handle. Used as the "event" result the OpenCL clone expects from enqueue ops.
 * No start event is recorded, so the reported elapsed time is 0 (used by
 * markers/barriers that do not bracket a timed operation).
 */
static jlong record_event(cuda_queue_t *queue) {
    cuda_event_t *ev = new cuda_event_t();
    ev->start = nullptr;
    CUresult result = cuEventCreate(&ev->event, CU_EVENT_DEFAULT);
    LOG_CUDA_AND_VALIDATE("cuEventCreate", result);
    result = cuEventRecord(ev->event, queue->stream);
    LOG_CUDA_AND_VALIDATE("cuEventRecord", result);
    return (jlong) ev;
}

/*
 * Makes the queue's stream wait (GPU-side) on each event of the wait list.
 * The array is laid out as [count, e0, e1, ...]. Events are CUevents and are
 * therefore valid across streams, which is what allows cross-queue ordering
 * when a plan runs with intra-plan concurrency (one queue per role).
 */
static void wait_events(JNIEnv *env, cuda_queue_t *queue, jlongArray array) {
    if (queue == nullptr || array == NULL) {
        return;
    }
    jlong *raw = static_cast<jlong *>(env->GetPrimitiveArrayCritical(array, NULL));
    jsize count = (jsize) raw[0];
    for (jsize i = 0; i < count; i++) {
        cuda_event_t *ev = (cuda_event_t *) raw[i + 1];
        if (ev != nullptr) {
            cuStreamWaitEvent(queue->stream, ev->event, 0);
        }
    }
    env->ReleasePrimitiveArrayCritical(array, raw, JNI_ABORT);
}

/*
 * Allocates an event handle and records a START timestamp on the stream, to be
 * paired with end_event() after the operation. CU_EVENT_DEFAULT keeps timing
 * enabled (CU_EVENT_DISABLE_TIMING would not), so cuEventElapsedTime works.
 */
static cuda_event_t *begin_event(cuda_queue_t *queue) {
    cuda_event_t *ev = new cuda_event_t();
    ev->event = nullptr;
    ev->start = nullptr;
    CUresult result = cuEventCreate(&ev->start, CU_EVENT_DEFAULT);
    LOG_CUDA_AND_VALIDATE("cuEventCreate(start)", result);
    result = cuEventRecord(ev->start, queue->stream);
    LOG_CUDA_AND_VALIDATE("cuEventRecord(start)", result);
    return ev;
}

/*
 * Records the END/completion timestamp for an event started with begin_event(),
 * and returns the boxed handle. cuEventElapsedTime(start, event) then yields the
 * device time of the operation enqueued between the two records.
 */
static jlong end_event(cuda_event_t *ev, cuda_queue_t *queue) {
    CUresult result = cuEventCreate(&ev->event, CU_EVENT_DEFAULT);
    LOG_CUDA_AND_VALIDATE("cuEventCreate(end)", result);
    result = cuEventRecord(ev->event, queue->stream);
    LOG_CUDA_AND_VALIDATE("cuEventRecord(end)", result);
    return (jlong) ev;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue
 * Method:    clReleaseCommandQueue
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue_clReleaseCommandQueue
        (JNIEnv *env, jclass clazz, jlong queue_id) {
    cuda_queue_t *queue = (cuda_queue_t *) queue_id;
    if (queue == nullptr) {
        return;
    }
    CUresult result = cuStreamDestroy(queue->stream);
    LOG_CUDA_AND_VALIDATE("cuStreamDestroy", result);
    delete queue;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue
 * Method:    clGetCommandQueueInfo
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue_clGetCommandQueueInfo
        (JNIEnv *env, jclass clazz, jlong queue_id, jint param_name, jbyteArray array) {
    cuda_queue_t *queue = (cuda_queue_t *) queue_id;
    jbyte *buf = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(array, NULL));
    jsize len = env->GetArrayLength(array);
    std::memset(buf, 0, len);

    if (queue != nullptr && len >= (jsize) sizeof(jlong)) {
        jlong value = 0;
        if (param_name == CL_QUEUE_CONTEXT) {
            value = (jlong) queue->context;
        } else if (param_name == CL_QUEUE_DEVICE) {
            value = (jlong) queue->device;
        }
        std::memcpy(buf, &value, sizeof(jlong));
    }
    env->ReleasePrimitiveArrayCritical(array, buf, 0);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue
 * Method:    clFlush
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue_clFlush
        (JNIEnv *env, jclass clazz, jlong queue_id) {
    cuda_queue_t *queue = (cuda_queue_t *) queue_id;
    if (queue == nullptr) {
        return;
    }
    CUresult result = cuStreamSynchronize(queue->stream);
    LOG_CUDA_AND_VALIDATE("cuStreamSynchronize", result);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue
 * Method:    clFinish
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue_clFinish
        (JNIEnv *env, jclass clazz, jlong queue_id) {
    cuda_queue_t *queue = (cuda_queue_t *) queue_id;
    if (queue == nullptr) {
        return;
    }
    CUresult result = cuStreamSynchronize(queue->stream);
    LOG_CUDA_AND_VALIDATE("cuStreamSynchronize", result);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue
 * Method:    clEnqueueNDRangeKernel
 * Signature: (JJI[J[J[J[J)J
 *
 * Translates an OpenCL NDRange into a CUDA grid/block launch. globalWorkSize is
 * the total thread count per dimension; localWorkSize is the block dimension.
 * If localWorkSize is null we pick a default block size and derive the grid.
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue_clEnqueueNDRangeKernel
        (JNIEnv *env, jclass clazz, jlong queue_id, jlong kernel_id, jint work_dim,
         jlongArray global_work_offset, jlongArray global_work_size, jlongArray local_work_size, jlongArray events) {
    cuda_queue_t *queue = (cuda_queue_t *) queue_id;
    cuda_kernel_t *kernel = (cuda_kernel_t *) kernel_id;
    if (queue == nullptr || kernel == nullptr) {
        return 0;
    }

    long long global[3] = { 1, 1, 1 };
    long long local[3] = { 0, 0, 0 };

    if (global_work_size != NULL) {
        jsize n = env->GetArrayLength(global_work_size);
        for (jsize i = 0; i < n && i < 3; i++) {
            jlong v;
            env->GetLongArrayRegion(global_work_size, i, 1, &v);
            global[i] = (long long) v;
        }
    }
    if (local_work_size != NULL) {
        jsize n = env->GetArrayLength(local_work_size);
        for (jsize i = 0; i < n && i < 3; i++) {
            jlong v;
            env->GetLongArrayRegion(local_work_size, i, 1, &v);
            local[i] = (long long) v;
        }
    }

    // Derive a block size when none is provided.
    unsigned int block[3];
    block[0] = (local[0] > 0) ? (unsigned int) local[0] : (work_dim >= 1 ? 256u : 1u);
    block[1] = (local[1] > 0) ? (unsigned int) local[1] : 1u;
    block[2] = (local[2] > 0) ? (unsigned int) local[2] : 1u;
    if (block[0] > (unsigned int) global[0] && global[0] > 0) {
        block[0] = (unsigned int) global[0];
    }

    unsigned int grid[3];
    grid[0] = (unsigned int) ((global[0] + block[0] - 1) / block[0]);
    grid[1] = (unsigned int) ((global[1] + block[1] - 1) / block[1]);
    grid[2] = (unsigned int) ((global[2] + block[2] - 1) / block[2]);
    if (grid[0] == 0) grid[0] = 1;
    if (grid[1] == 0) grid[1] = 1;
    if (grid[2] == 0) grid[2] = 1;

    // Build the kernelParams array of pointers into the stored arg slots.
    std::vector<void *> params(kernel->arg_data.size());
    for (size_t i = 0; i < kernel->arg_data.size(); i++) {
        params[i] = kernel->arg_data[i].empty() ? nullptr : (void *) kernel->arg_data[i].data();
    }

    cuCtxSetCurrent(queue->context);
    wait_events(env, queue, events);
    cuda_event_t *ev = begin_event(queue);
    CUresult result = cuLaunchKernel(
            kernel->function,
            grid[0], grid[1], grid[2],
            block[0], block[1], block[2],
            0,            // dynamic shared memory bytes
            queue->stream,
            params.empty() ? nullptr : params.data(),
            nullptr);
    LOG_CUDA_AND_VALIDATE("cuLaunchKernel", result);

    return end_event(ev, queue);
}

/*
 * Host <-> device transfer helper. The OpenCL semantics copy numBytes from the
 * host buffer (at byte hostOffset) to/from the device pointer (at byte offset).
 */
/*
 * sync_after: the caller requires the copy to have completed on return. Always
 * true for Java-array transfers (the pinned critical region is released right
 * after this call, so an in-flight async copy would race the GC) and for
 * blocking off-heap transfers; false for async off-heap (MemorySegment)
 * transfers, whose completion is ordered by events / the end-of-plan sync.
 * While capturing into a CUDA graph the stream must NOT be synchronised (it
 * would invalidate the capture); the copy becomes a graph node whose host
 * pointer (a stable off-heap MemorySegment) is re-read on each graph launch.
 */
static jlong transfer_to_device(JNIEnv *env, cuda_queue_t *queue, void *host_base,
                                jlong host_offset, jlong device_offset, jlong num_bytes, jlong device_ptr,
                                jlongArray events, bool sync_after) {
    if (queue == nullptr) {
        return 0;
    }
    cuCtxSetCurrent(queue->context);
    wait_events(env, queue, events);
    cuda_event_t *ev = begin_event(queue);
    CUresult result = cuMemcpyHtoDAsync(
            (CUdeviceptr) (device_ptr + device_offset),
            (const void *) ((char *) host_base + host_offset),
            (size_t) num_bytes,
            queue->stream);
    LOG_CUDA_AND_VALIDATE("cuMemcpyHtoDAsync", result);
    if (sync_after && !stream_is_capturing(queue)) {
        cuStreamSynchronize(queue->stream);
    }
    return end_event(ev, queue);
}

static jlong transfer_to_host(JNIEnv *env, cuda_queue_t *queue, void *host_base,
                              jlong host_offset, jlong device_offset, jlong num_bytes, jlong device_ptr,
                              jlongArray events, bool sync_after) {
    if (queue == nullptr) {
        return 0;
    }
    cuCtxSetCurrent(queue->context);
    wait_events(env, queue, events);
    cuda_event_t *ev = begin_event(queue);
    CUresult result = cuMemcpyDtoHAsync(
            (void *) ((char *) host_base + host_offset),
            (CUdeviceptr) (device_ptr + device_offset),
            (size_t) num_bytes,
            queue->stream);
    LOG_CUDA_AND_VALIDATE("cuMemcpyDtoHAsync", result);
    if (sync_after && !stream_is_capturing(queue)) {
        cuStreamSynchronize(queue->stream);
    }
    return end_event(ev, queue);
}

/* ---- writeArrayToDevice overloads (byte/char/short/int/long/float/double) ---- */

#define DEFINE_WRITE(MANGLE, JARRTYPE)                                                                          \
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue_writeArrayToDevice__##MANGLE \
        (JNIEnv *env, jclass clazz, jlong queue_id, JARRTYPE host_array, jlong host_offset, jboolean blocking,  \
         jlong offset, jlong num_bytes, jlong device_ptr, jlongArray events) {                                  \
    cuda_queue_t *queue = (cuda_queue_t *) queue_id;                                                            \
    void *host = env->GetPrimitiveArrayCritical((jarray) host_array, NULL);                                     \
    jlong ev = transfer_to_device(env, queue, host, host_offset, offset, num_bytes, device_ptr, events, true); \
    env->ReleasePrimitiveArrayCritical((jarray) host_array, host, JNI_ABORT);                                   \
    return ev;                                                                                                  \
}

DEFINE_WRITE(J_3BJZJJJ_3J, jbyteArray)
DEFINE_WRITE(J_3CJZJJJ_3J, jcharArray)
DEFINE_WRITE(J_3SJZJJJ_3J, jshortArray)
DEFINE_WRITE(J_3IJZJJJ_3J, jintArray)
DEFINE_WRITE(J_3JJZJJJ_3J, jlongArray)
DEFINE_WRITE(J_3FJZJJJ_3J, jfloatArray)
DEFINE_WRITE(J_3DJZJJJ_3J, jdoubleArray)

/*
 * Off-heap write overload: writeArrayToDevice(long queueId, long hostPointer, ...).
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue_writeArrayToDevice__JJJZJJJ_3J
        (JNIEnv *env, jclass clazz, jlong queue_id, jlong host_pointer, jlong host_offset, jboolean blocking,
         jlong offset, jlong num_bytes, jlong device_ptr, jlongArray events) {
    cuda_queue_t *queue = (cuda_queue_t *) queue_id;
    return transfer_to_device(env, queue, (void *) host_pointer, host_offset, offset, num_bytes, device_ptr, events, blocking);
}

/* ---- readArrayFromDevice overloads ---- */

#define DEFINE_READ(MANGLE, JARRTYPE)                                                                            \
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue_readArrayFromDevice__##MANGLE \
        (JNIEnv *env, jclass clazz, jlong queue_id, JARRTYPE host_array, jlong host_offset, jboolean blocking,   \
         jlong offset, jlong num_bytes, jlong device_ptr, jlongArray events) {                                   \
    cuda_queue_t *queue = (cuda_queue_t *) queue_id;                                                             \
    void *host = env->GetPrimitiveArrayCritical((jarray) host_array, NULL);                                      \
    jlong ev = transfer_to_host(env, queue, host, host_offset, offset, num_bytes, device_ptr, events, true);    \
    env->ReleasePrimitiveArrayCritical((jarray) host_array, host, 0);                                            \
    return ev;                                                                                                   \
}

DEFINE_READ(J_3BJZJJJ_3J, jbyteArray)
DEFINE_READ(J_3CJZJJJ_3J, jcharArray)
DEFINE_READ(J_3SJZJJJ_3J, jshortArray)
DEFINE_READ(J_3IJZJJJ_3J, jintArray)
DEFINE_READ(J_3JJZJJJ_3J, jlongArray)
DEFINE_READ(J_3FJZJJJ_3J, jfloatArray)
DEFINE_READ(J_3DJZJJJ_3J, jdoubleArray)

/*
 * Off-heap read overload: readArrayFromDeviceOffHeap(long queueId, long hostPointer, ...).
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue_readArrayFromDeviceOffHeap__JJJZJJJ_3J
        (JNIEnv *env, jclass clazz, jlong queue_id, jlong host_pointer, jlong host_offset, jboolean blocking,
         jlong offset, jlong num_bytes, jlong device_ptr, jlongArray events) {
    cuda_queue_t *queue = (cuda_queue_t *) queue_id;
    return transfer_to_host(env, queue, (void *) host_pointer, host_offset, offset, num_bytes, device_ptr, events, blocking);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue
 * Method:    clEnqueueWaitForEvents
 * Signature: (J[J)V
 *
 * The events array is laid out as [count, e0, e1, ...]. We make the stream wait
 * on each recorded event.
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue_clEnqueueWaitForEvents
        (JNIEnv *env, jclass clazz, jlong queue_id, jlongArray array) {
    cuda_queue_t *queue = (cuda_queue_t *) queue_id;
    if (queue == nullptr || array == NULL) {
        return;
    }
    jlong *raw = static_cast<jlong *>(env->GetPrimitiveArrayCritical(array, NULL));
    jsize count = (jsize) raw[0];
    for (jsize i = 0; i < count; i++) {
        cuda_event_t *ev = (cuda_event_t *) raw[i + 1];
        if (ev != nullptr) {
            cuStreamWaitEvent(queue->stream, ev->event, 0);
        }
    }
    env->ReleasePrimitiveArrayCritical(array, raw, JNI_ABORT);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue
 * Method:    clEnqueueMarkerWithWaitList
 * Signature: (J[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue_clEnqueueMarkerWithWaitList
        (JNIEnv *env, jclass clazz, jlong queue_id, jlongArray array) {
    cuda_queue_t *queue = (cuda_queue_t *) queue_id;
    if (queue == nullptr) {
        return 0;
    }
    return record_event(queue);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue
 * Method:    clEnqueueBarrierWithWaitList
 * Signature: (J[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue_clEnqueueBarrierWithWaitList
        (JNIEnv *env, jclass clazz, jlong queue_id, jlongArray array) {
    cuda_queue_t *queue = (cuda_queue_t *) queue_id;
    if (queue == nullptr) {
        return 0;
    }
    // Honour the wait list, then place a marker event.
    Java_uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue_clEnqueueWaitForEvents(env, clazz, queue_id, array);
    return record_event(queue);
}

} // extern "C"
