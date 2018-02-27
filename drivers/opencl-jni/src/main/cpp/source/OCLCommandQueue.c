/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science,
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
#include <jni.h>
#ifdef __APPLE__
#include <OpenCL/cl.h>
#else
#include <CL/cl.h>
#endif
#include <stdio.h>
#include "macros.h"
#include "utils.h"

/*
 * Class:     jacc_runtime_drivers_opencl_OCLCommandQueue
 * Method:    clReleaseCommandQueue
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clReleaseCommandQueue
(JNIEnv *env, jclass clazz, jlong queue_id) {
    OPENCL_PROLOGUE;

    OPENCL_SOFT_ERROR("clReleaseCommandQueue", clReleaseCommandQueue((cl_command_queue) queue_id),);
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLCommandQueue
 * Method:    clGetCommandQueueInfo
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clGetCommandQueueInfo
(JNIEnv *env, jclass clazz, jlong queue_id, jint param_name, jbyteArray array) {
    OPENCL_PROLOGUE;

    jbyte *value;
    jlong len;

    len = (*env)->GetArrayLength(env, array);
    value = (*env)->GetPrimitiveArrayCritical(env, array, NULL);

    size_t return_size = 0;
    OPENCL_SOFT_ERROR("clGetCommandQueueInfo",
            clGetCommandQueueInfo((cl_command_queue) queue_id, (cl_command_queue_info) param_name, len, (void *) value, &return_size),);


    (*env)->ReleasePrimitiveArrayCritical(env, array, value, 0);

}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLCommandQueue
 * Method:    clSetCommandQueueProperty
 * Signature: (JJZ)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clSetCommandQueueProperty
(JNIEnv *env, jclass clazz, jlong queue_id, jlong properties, jboolean value) {
    //OPENCL_PROLOGUE;

    // Not implemented in OpenCL 1.2

    //cl_bool enable = (value) ? CL_TRUE : CL_FALSE;
    //OPENCL_SOFT_ERROR("clSetCommandQueueProperty",clSetCommandQueueProperty((cl_command_queue) queue_id, (cl_command_queue_properties) properties,enable,NULL),);

}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLCommandQueue
 * Method:    clFlush
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clFlush
(JNIEnv *env, jclass clazz, jlong queue_id) {
    OPENCL_PROLOGUE;

    OPENCL_SOFT_ERROR("clFlush", clFlush((cl_command_queue) queue_id),);
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLCommandQueue
 * Method:    clFinish
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clFinish
(JNIEnv *env, jclass clazz, jlong queue_id) {
    OPENCL_PROLOGUE;

    OPENCL_SOFT_ERROR("clFinish", clFinish((cl_command_queue) queue_id),);
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLCommandQueue
 * Method:    clEnqueueNDRangeKernel
 * Signature: (JJI[J[J[J[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueNDRangeKernel
(JNIEnv *env, jclass clazz, jlong queue_id, jlong kernel_id, jint work_dim, jlongArray array1, jlongArray array2, jlongArray array3, jlongArray array4) {
    OPENCL_PROLOGUE;

    JNI_ACQUIRE_ARRAY_OR_NULL(jlong, global_work_offset, array1);
    JNI_ACQUIRE_ARRAY_OR_NULL(jlong, global_work_size, array2);
    JNI_ACQUIRE_ARRAY_OR_NULL(jlong, local_work_size, array3);

    OPENCL_DECODE_WAITLIST(array4, events, numEvents);

    cl_event event;
    OPENCL_SOFT_ERROR("clEnqueueNDRangeKernel",
            clEnqueueNDRangeKernel((cl_command_queue) queue_id, (cl_kernel) kernel_id, (cl_uint) work_dim, (size_t*) global_work_offset, (size_t*) global_work_size, (size_t*) local_work_size, (cl_uint) numEvents, (cl_event*) events, &event), 0);

    OPENCL_RELEASE_WAITLIST(array4);

    JNI_RELEASE_ARRAY(array1, global_work_offset);
    JNI_RELEASE_ARRAY(array2, global_work_size);

    return (jlong) event;
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLCommandQueue
 * Method:    clEnqueueTask
 * Signature: (JJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueTask
(JNIEnv *env, jclass clazz, jlong queue_id, jlong kernel_id, jlongArray array) {
    OPENCL_PROLOGUE;

    jlong *waitList = (array != NULL) ? (*env)->GetPrimitiveArrayCritical(env, array, NULL) : NULL;
    jlong *events = (array != NULL) ? &waitList[1] : NULL;
    jsize len = (array != NULL) ? waitList[0] : 0;

    cl_event event;
    OPENCL_SOFT_ERROR("clEnqueueTask",
            clEnqueueTask((cl_command_queue) queue_id, (cl_kernel) kernel_id, (size_t) len, (cl_event *) events, &event), 0);

    if (array != NULL)
        (*env)->ReleasePrimitiveArrayCritical(env, array, waitList, JNI_ABORT);

    return (jlong) event;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    clEnqueueMarker
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueMarker
(JNIEnv *env, jclass clazz, jlong queue_id) {
    OPENCL_PROLOGUE;

    cl_event event;
    OPENCL_SOFT_ERROR("clEnqueueMarker",
            clEnqueueMarker((cl_command_queue) queue_id, &event), 0);

    return (jlong) event;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    clEnqueueBarrier
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueBarrier
(JNIEnv *env, jclass clazz, jlong queue_id) {
    OPENCL_PROLOGUE;

    cl_event event;
    OPENCL_SOFT_ERROR("clEnqueueBarrier",
            clEnqueueBarrier((cl_command_queue) queue_id),);

}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    clEnqueueWaitForEvents
 * Signature: (J[J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueWaitForEvents
(JNIEnv *env, jclass clazz, jlong queue_id, jlongArray array) {
    OPENCL_PROLOGUE;

    OPENCL_DECODE_WAITLIST(array, events, len);

    if (len > 0 && events != NULL)
        OPENCL_SOFT_ERROR("clEnqueueWaitForEvents",
            clEnqueueWaitForEvents((cl_command_queue) queue_id, len, (cl_event *) events),);

    OPENCL_RELEASE_WAITLIST(array);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    clEnqueueMarkerWithWaitList
 * Signature: (J[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueMarkerWithWaitList
(JNIEnv *env, jclass clazz, jlong queue_id, jlongArray array) {
    OPENCL_PROLOGUE;

    OPENCL_DECODE_WAITLIST(array, events, len);

    cl_event event;
    OPENCL_SOFT_ERROR("clEnqueueMarkerWithWaitList",
            clEnqueueMarkerWithWaitList((cl_command_queue) queue_id, len, (cl_event *) events, &event), 0);

    OPENCL_RELEASE_WAITLIST(array)

    return (jlong) event;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    clEnqueueBarrierWithWaitList
 * Signature: (J[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueBarrierWithWaitList
(JNIEnv *env, jclass clazz, jlong queue_id, jlongArray array) {
    OPENCL_PROLOGUE;

    OPENCL_DECODE_WAITLIST(array, events, len);

    cl_event event;
    OPENCL_SOFT_ERROR("clEnqueueBarrierWithWaitList",
            clEnqueueBarrierWithWaitList((cl_command_queue) queue_id, len, (cl_event *) events, &event), 0);

    OPENCL_RELEASE_WAITLIST(array)

    return (jlong) event;
}
