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
 * CUDA Graph (stream-capture) JNI bindings for the tornado-cuda backend.
 *
 * The Java layer drives capture/replay through the OpenCL-style command queue
 * (cuda_queue_t*), whose underlying CUstream is captured. Opaque CUgraph and
 * CUgraphExec handles are returned to Java as plain jlong pointers.
 *
 * Design notes:
 *   - The capturing stream must NOT be synchronised while capture is active
 *     (cuStreamSynchronize invalidates the capture). The transfer helpers in
 *     CUDACommandQueue.cpp therefore consult cuStreamIsCapturing and skip the
 *     post-copy synchronise when the stream is mid-capture.
 *   - cuGraphInstantiate has different signatures across CUDA major versions
 *     (11.x vs 12+); both are handled below.
 */

#include <jni.h>
#include "cuda_jni.h"

extern "C" {

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue
 * Method:    cuStreamBeginCapture
 * Signature: (JI)J
 *
 * Puts the queue's stream into capture mode. mode maps directly onto
 * CUstreamCaptureMode (0 = CU_STREAM_CAPTURE_MODE_GLOBAL).
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue_cuStreamBeginCapture
        (JNIEnv *env, jclass clazz, jlong queue_id, jint mode) {
    cuda_queue_t *queue = (cuda_queue_t *) queue_id;
    if (queue == nullptr) {
        return (jlong) CUDA_ERROR_INVALID_VALUE;
    }
    cuCtxSetCurrent(queue->context);
    CUresult result = cuStreamBeginCapture(queue->stream, (CUstreamCaptureMode) mode);
    LOG_CUDA_AND_VALIDATE("cuStreamBeginCapture", result);
    return (jlong) result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue
 * Method:    cuStreamEndCapture
 * Signature: (J)J
 *
 * Ends capture on the queue's stream and returns the constructed CUgraph as an
 * opaque pointer (0 on failure).
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue_cuStreamEndCapture
        (JNIEnv *env, jclass clazz, jlong queue_id) {
    cuda_queue_t *queue = (cuda_queue_t *) queue_id;
    if (queue == nullptr) {
        return 0;
    }
    cuCtxSetCurrent(queue->context);
    CUgraph graph = nullptr;
    CUresult result = cuStreamEndCapture(queue->stream, &graph);
    LOG_CUDA_AND_VALIDATE("cuStreamEndCapture", result);
    if (result != CUDA_SUCCESS) {
        return 0;
    }
    return (jlong) graph;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue
 * Method:    cuStreamIsCapturing
 * Signature: (J)Z
 *
 * Returns true if the queue's stream is actively capturing.
 */
JNIEXPORT jboolean JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue_cuStreamIsCapturing
        (JNIEnv *env, jclass clazz, jlong queue_id) {
    cuda_queue_t *queue = (cuda_queue_t *) queue_id;
    if (queue == nullptr) {
        return JNI_FALSE;
    }
    CUstreamCaptureStatus status = CU_STREAM_CAPTURE_STATUS_NONE;
    CUresult result = cuStreamIsCapturing(queue->stream, &status);
    if (result != CUDA_SUCCESS) {
        return JNI_FALSE;
    }
    return (status == CU_STREAM_CAPTURE_STATUS_ACTIVE) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue
 * Method:    cuGraphInstantiate
 * Signature: (J)J
 *
 * Instantiates an executable graph from a CUgraph and returns the CUgraphExec
 * as an opaque pointer (0 on failure).
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue_cuGraphInstantiate
        (JNIEnv *env, jclass clazz, jlong graph_handle) {
    CUgraph graph = (CUgraph) graph_handle;
    if (graph == nullptr) {
        return 0;
    }
    CUgraphExec graphExec = nullptr;
#if CUDA_VERSION >= 12000
    CUresult result = cuGraphInstantiate(&graphExec, graph, 0);
#else
    CUresult result = cuGraphInstantiate(&graphExec, graph, NULL, NULL, 0);
#endif
    LOG_CUDA_AND_VALIDATE("cuGraphInstantiate", result);
    if (result != CUDA_SUCCESS) {
        return 0;
    }
    return (jlong) graphExec;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue
 * Method:    cuGraphExecUpdate
 * Signature: (JJ)J
 *
 * Attempts to update an instantiated graph in place with the topology of a new
 * CUgraph. Returns CUDA_SUCCESS when the update succeeded, otherwise the error
 * code so the caller can fall back to re-instantiation.
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue_cuGraphExecUpdate
        (JNIEnv *env, jclass clazz, jlong graph_exec_handle, jlong graph_handle) {
    CUgraphExec graphExec = (CUgraphExec) graph_exec_handle;
    CUgraph graph = (CUgraph) graph_handle;
    if (graphExec == nullptr || graph == nullptr) {
        return (jlong) CUDA_ERROR_INVALID_VALUE;
    }
#if CUDA_VERSION >= 12000
    CUgraphExecUpdateResultInfo info;
    std::memset(&info, 0, sizeof(info));
    CUresult result = cuGraphExecUpdate(graphExec, graph, &info);
#else
    CUgraphNode errorNode = nullptr;
    CUgraphExecUpdateResult updateResult = CU_GRAPH_EXEC_UPDATE_SUCCESS;
    CUresult result = cuGraphExecUpdate(graphExec, graph, &errorNode, &updateResult);
#endif
    LOG_CUDA_AND_VALIDATE("cuGraphExecUpdate", result);
    return (jlong) result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue
 * Method:    cuGraphLaunch
 * Signature: (JJ)J
 *
 * Launches an instantiated graph on the queue's stream.
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue_cuGraphLaunch
        (JNIEnv *env, jclass clazz, jlong graph_exec_handle, jlong queue_id) {
    CUgraphExec graphExec = (CUgraphExec) graph_exec_handle;
    cuda_queue_t *queue = (cuda_queue_t *) queue_id;
    if (graphExec == nullptr || queue == nullptr) {
        return (jlong) CUDA_ERROR_INVALID_VALUE;
    }
    cuCtxSetCurrent(queue->context);
    CUresult result = cuGraphLaunch(graphExec, queue->stream);
    LOG_CUDA_AND_VALIDATE("cuGraphLaunch", result);
    return (jlong) result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue
 * Method:    cuGraphExecDestroy
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue_cuGraphExecDestroy
        (JNIEnv *env, jclass clazz, jlong graph_exec_handle) {
    CUgraphExec graphExec = (CUgraphExec) graph_exec_handle;
    if (graphExec == nullptr) {
        return (jlong) CUDA_SUCCESS;
    }
    CUresult result = cuGraphExecDestroy(graphExec);
    LOG_CUDA_AND_VALIDATE("cuGraphExecDestroy", result);
    return (jlong) result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue
 * Method:    cuGraphDestroy
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue_cuGraphDestroy
        (JNIEnv *env, jclass clazz, jlong graph_handle) {
    CUgraph graph = (CUgraph) graph_handle;
    if (graph == nullptr) {
        return (jlong) CUDA_SUCCESS;
    }
    CUresult result = cuGraphDestroy(graph);
    LOG_CUDA_AND_VALIDATE("cuGraphDestroy", result);
    return (jlong) result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue
 * Method:    getStreamPointer
 * Signature: (J)J
 *
 * Exposes the raw CUstream so external native libraries (e.g. cuBLAS via
 * cublasSetStream) can enqueue work ordered with TornadoVM's own stream.
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue_getStreamPointer
        (JNIEnv *env, jclass clazz, jlong queue_id) {
    cuda_queue_t *queue = (cuda_queue_t *) queue_id;
    if (queue == nullptr) {
        return 0;
    }
    return (jlong) queue->stream;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue
 * Method:    getContextPointer
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue_getContextPointer
        (JNIEnv *env, jclass clazz, jlong queue_id) {
    cuda_queue_t *queue = (cuda_queue_t *) queue_id;
    if (queue == nullptr) {
        return 0;
    }
    return (jlong) queue->context;
}

} // extern "C"
