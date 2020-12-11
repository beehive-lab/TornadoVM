/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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

#define CL_TARGET_OPENCL_VERSION 120
#ifdef __APPLE__
    #include <OpenCL/cl.h>
#else
    #include <CL/cl.h>
#endif

#include <iostream>

#include "opencl_time_utils.h"
#include "OCLCommandQueue.h"
#include "ocl_log.h"

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    clReleaseCommandQueue
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clReleaseCommandQueue
(JNIEnv *env, jclass clazz, jlong queue_id) {
    cl_int status = clReleaseCommandQueue((cl_command_queue) queue_id);
    LOG_OCL_JNI("clReleaseCommandQueue", status);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_OCLCommandQueue
 * Method:    clGetCommandQueueInfo
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clGetCommandQueueInfo
(JNIEnv *env, jclass clazz, jlong queue_id, jint param_name, jbyteArray array) {
    jlong len = env->GetArrayLength(array);
    jbyte *value = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(array, NULL));

    size_t return_size = 0;
    cl_int status = clGetCommandQueueInfo((cl_command_queue) queue_id, (cl_command_queue_info) param_name, len, (void *) value, &return_size);
    LOG_OCL_JNI("clGetCommandQueueInfo", status);
    env->ReleasePrimitiveArrayCritical(array, value, 0);

}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    clSetCommandQueueProperty
 * Signature: (JJZ)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clSetCommandQueueProperty
(JNIEnv *env, jclass clazz, jlong queue_id, jlong properties, jboolean value) {
    // Not implemented in OpenCL 1.2
    //OPENCL_SOFT_ERROR("clSetCommandQueueProperty",clSetCommandQueueProperty((cl_command_queue) queue_id, (cl_command_queue_properties) properties,enable,NULL),);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    clFlush
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clFlush
(JNIEnv *env, jclass clazz, jlong queue_id) {
    cl_int status = clFlush((cl_command_queue) queue_id);
    LOG_OCL_JNI("clFlush", status);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    clFinish
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clFinish
(JNIEnv *env, jclass clazz, jlong queue_id) {
    cl_int status = clFinish((cl_command_queue) queue_id);
    LOG_OCL_JNI("clFinish", status);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_OCLCommandQueue
 * Method:    clEnqueueNDRangeKernel
 * Signature: (JJI[J[J[J[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueNDRangeKernel
(JNIEnv *env, jclass clazz, jlong queue_id, jlong kernel_id, jint work_dim, jlongArray array1, jlongArray array2, jlongArray array3, jlongArray array4) {
    jlong *global_work_offset = static_cast<jlong *>((array1 != NULL) ? env->GetPrimitiveArrayCritical(array1, NULL)
                                                                      : NULL);
    jlong *global_work_size = static_cast<jlong *>((array2 != NULL) ? env->GetPrimitiveArrayCritical(array2, NULL)
                                                                    : NULL);
    jlong *local_work_size = static_cast<jlong *>((array3 != NULL) ? env->GetPrimitiveArrayCritical(array3, NULL)
                                                                   : NULL);

    jlong *javaArrayEvents = static_cast<jlong *>((array4 != NULL) ? env->GetPrimitiveArrayCritical(array4, NULL) : NULL);
    jlong *events = (array4 != NULL) ? &javaArrayEvents[1] : NULL;
    jsize numEvents = (array4 != NULL) ? javaArrayEvents[0] : 0;

    cl_event kernelEvent = NULL;
    cl_int status = clEnqueueNDRangeKernel((cl_command_queue) queue_id, (cl_kernel) kernel_id, (cl_uint) work_dim, (size_t*) global_work_offset, (size_t*) global_work_size, (size_t*) local_work_size, (cl_uint) numEvents, (numEvents == 0) ? NULL : (cl_event*) events, &kernelEvent);
    LOG_OCL_JNI("clEnqueueNDRangeKernel", status);

	if (PRINT_KERNEL_EVENTS) {
		long kernelTime = getElapsedTimeEvent(kernelEvent);
		printf("Kernel time: %ld (ns) \n", kernelTime);
	}

    if (array4 != NULL) {
        env->ReleasePrimitiveArrayCritical(array4, javaArrayEvents, JNI_ABORT);
    }

	if (array1 != NULL) {
	    env->ReleasePrimitiveArrayCritical(array1, global_work_offset, JNI_ABORT);
	}

    if (array2 != NULL) {
        env->ReleasePrimitiveArrayCritical(array2, global_work_size, JNI_ABORT);
    }

    if (array3 != NULL) {
        env->ReleasePrimitiveArrayCritical(array3, local_work_size, JNI_ABORT);
    }

    return (jlong) kernelEvent;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    clEnqueueTask
 * Signature: (JJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueTask
(JNIEnv *env, jclass clazz, jlong queue_id, jlong kernel_id, jlongArray array) {
    jlong *waitList = static_cast<jlong *>((array != NULL) ? env->GetPrimitiveArrayCritical(array, NULL) : NULL);
    jlong *events = (array != NULL) ? &waitList[1] : NULL;
    jsize len = (array != NULL) ? waitList[0] : 0;

    cl_event event;
    cl_int status = clEnqueueTask((cl_command_queue) queue_id, (cl_kernel) kernel_id, (size_t) len, (cl_event *) events, &event);
    LOG_OCL_JNI("clEnqueueTask", status);
    if (PRINT_KERNEL_EVENTS) {
        long kernelTime = getElapsedTimeEvent(event);
        printf("Kernel time: %ld (ns) \n", kernelTime);
    }
    if (array != NULL) {
        env->ReleasePrimitiveArrayCritical(array, waitList, JNI_ABORT);
    }
    return (jlong) event;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    clEnqueueMarker
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueMarker
(JNIEnv *env, jclass clazz, jlong queue_id) {
    cl_event event;
    cl_int status = clEnqueueMarker((cl_command_queue) queue_id, &event);
    LOG_OCL_JNI("clEnqueueMarker", status);
    return (jlong) event;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    clEnqueueBarrier
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueBarrier
(JNIEnv *env, jclass clazz, jlong queue_id) {
    cl_event event;
    cl_int status = clEnqueueBarrier((cl_command_queue) queue_id);
    LOG_OCL_JNI("clEnqueueBarrier", status);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    clEnqueueWaitForEvents
 * Signature: (J[J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueWaitForEvents
(JNIEnv *env, jclass clazz, jlong queue_id, jlongArray array) {

    jlong *arrayEvents = static_cast<jlong *>((array != NULL) ? env->GetPrimitiveArrayCritical(array, NULL) : NULL);
    jlong *events = (array != NULL) ? &arrayEvents[1] : NULL;
    jsize len = (array != NULL) ? arrayEvents[0] : 0;
    cl_int status = clEnqueueWaitForEvents((cl_command_queue) queue_id, len, (cl_event *) events);
    LOG_OCL_JNI("clEnqueueWaitForEvents", status);

    if (array != NULL) {
        env->ReleasePrimitiveArrayCritical(array, arrayEvents, JNI_ABORT);
    }
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    clEnqueueMarkerWithWaitList
 * Signature: (J[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueMarkerWithWaitList
(JNIEnv *env, jclass clazz, jlong queue_id, jlongArray array) {
    jlong *arrayEvents = static_cast<jlong *>((array != NULL) ? env->GetPrimitiveArrayCritical(array, NULL) : NULL);
    jlong *events = (array != NULL) ? &arrayEvents[1] : NULL;
    jsize len = (array != NULL) ? arrayEvents[0] : 0;

    cl_event event;
    cl_int status = clEnqueueMarkerWithWaitList((cl_command_queue) queue_id, len, (cl_event *) events, &event);
    LOG_OCL_JNI("clEnqueueMarkerWithWaitList", status);

    if (array != NULL) {
        env->ReleasePrimitiveArrayCritical(array, arrayEvents, JNI_ABORT);
    }

    return (jlong) event;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    clEnqueueBarrierWithWaitList
 * Signature: (J[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueBarrierWithWaitList
(JNIEnv *env, jclass clazz, jlong queue_id, jlongArray array) {
    jlong *arrayEvents = static_cast<jlong *>((array != NULL) ? env->GetPrimitiveArrayCritical(array, NULL) : NULL);
    jlong *events = (array != NULL) ? &arrayEvents[1] : NULL;
    jsize len = (array != NULL) ? arrayEvents[0] : 0;
    cl_event event;
    cl_int status = clEnqueueBarrierWithWaitList((cl_command_queue) queue_id, len, (cl_event *) events, &event);
    LOG_OCL_JNI("clEnqueueBarrierWithWaitList", status);
    if (array != NULL) {
        env->ReleasePrimitiveArrayCritical(array, arrayEvents, JNI_ABORT);
    }
    return (jlong) event;
}

jlong transferFromHostToDevice(JNIEnv * env, jclass klass, jlong commandQueue, jbyteArray hostArray, jlong hostOffset, jboolean blocking, jlong offset, jlong numBytes, jlong devicePtr, jlongArray javaArrayEvents) {
    cl_bool blocking_write = blocking ? CL_TRUE : CL_FALSE;
    jsize num_bytes = (numBytes != -1) ? numBytes : env->GetArrayLength(hostArray) * sizeof(jbyte);
    jlong *arrayEvents = static_cast<jlong *>((javaArrayEvents != NULL) ? env->GetPrimitiveArrayCritical(javaArrayEvents, NULL) : NULL);
    jlong *events = (javaArrayEvents != NULL) ? &arrayEvents[1] : NULL;
    jsize numberOfEvents = (javaArrayEvents != NULL) ? arrayEvents[0] : 0;

    jbyte *buffer = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(hostArray, NULL));
    if (PRINT_DATA_SIZES) {
        std::cout << "[TornadoVM JNI] transferFromHostToDevice from " << offset << " (" << numBytes << ") from buffer: " << buffer << std::endl;
    }
    cl_event event;
    cl_int status = clEnqueueWriteBuffer((cl_command_queue) commandQueue, (cl_mem) devicePtr, blocking_write,
                                         (size_t) offset, (size_t) num_bytes, &buffer[hostOffset], (cl_uint) numberOfEvents,
                                         (cl_event *) events, &event);
    LOG_OCL_JNI("clEnqueueWriteBuffer", status);
    if (PRINT_DATA_TIMES) {
        long writeTime = getElapsedTimeEvent(event);
        std::cout << "[TornadoVM-JNI] H2D time: " << writeTime << " (ns)" << std::endl;
    } else {
        /* we must wait irrespective of jboolean blocking flag or we risk Java GC/OpenCL Runtime race condition */
        clWaitForEvents(1, &event);
    }
    if (hostArray != NULL) {
        env->ReleasePrimitiveArrayCritical(hostArray, buffer, JNI_ABORT);
    }
    if (javaArrayEvents != NULL) {
        env->ReleasePrimitiveArrayCritical(javaArrayEvents, arrayEvents, JNI_ABORT);
    }
    return (jlong) event;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    writeArrayToDevice
 * Signature: (J[BJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3BJZJJJ_3J
        (JNIEnv * env, jclass klass, jlong commandQueue, jbyteArray hostArray, jlong hostOffset, jboolean blocking, jlong offset, jlong numBytes, jlong devicePtr, jlongArray javaArrayEvents) {
    return transferFromHostToDevice(env, klass, commandQueue, hostArray, hostOffset, blocking, offset, numBytes, devicePtr, javaArrayEvents);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    writeArrayToDevice
 * Signature: (J[CJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3CJZJJJ_3J
        (JNIEnv * env, jclass klass, jlong commandQueue, jcharArray hostArray, jlong hostOffset, jboolean blocking, jlong offset, jlong numBytes, jlong devicePtr, jlongArray javaArrayEvents) {
    return transferFromHostToDevice(env, klass, commandQueue, reinterpret_cast<jbyteArray>(hostArray), hostOffset, blocking, offset, numBytes, devicePtr, javaArrayEvents);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    writeArrayToDevice
 * Signature: (J[SJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3SJZJJJ_3J
        (JNIEnv * env, jclass klass, jlong commandQueue, jshortArray hostArray, jlong hostOffset, jboolean blocking, jlong offset, jlong numBytes, jlong devicePtr, jlongArray javaArrayEvents) {
    return transferFromHostToDevice(env, klass, commandQueue, reinterpret_cast<jbyteArray>(hostArray), hostOffset, blocking, offset, numBytes, devicePtr, javaArrayEvents);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    writeArrayToDevice
 * Signature: (J[IJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3IJZJJJ_3J
        (JNIEnv * env, jclass klass, jlong commandQueue, jintArray hostArray, jlong hostOffset, jboolean blocking, jlong offset, jlong numBytes, jlong devicePtr, jlongArray javaArrayEvents) {
    return transferFromHostToDevice(env, klass, commandQueue, reinterpret_cast<jbyteArray>(hostArray), hostOffset, blocking, offset, numBytes, devicePtr, javaArrayEvents);
}


/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    writeArrayToDevice
 * Signature: (J[JJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3JJZJJJ_3J
        (JNIEnv * env, jclass klass, jlong commandQueue, jlongArray hostArray, jlong hostOffset, jboolean blocking, jlong offset, jlong numBytes, jlong devicePtr, jlongArray javaArrayEvents) {
    return transferFromHostToDevice(env, klass, commandQueue, reinterpret_cast<jbyteArray>(hostArray), hostOffset, blocking, offset, numBytes, devicePtr, javaArrayEvents);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    writeArrayToDevice
 * Signature: (J[FJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3FJZJJJ_3J
        (JNIEnv * env, jclass klass, jlong commandQueue, jfloatArray hostArray, jlong hostOffset, jboolean blocking, jlong offset, jlong numBytes, jlong devicePtr, jlongArray javaArrayEvents) {
    return transferFromHostToDevice(env, klass, commandQueue, reinterpret_cast<jbyteArray>(hostArray), hostOffset, blocking, offset, numBytes, devicePtr, javaArrayEvents);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    writeArrayToDevice
 * Signature: (J[DJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3DJZJJJ_3J
        (JNIEnv * env, jclass klass, jlong commandQueue, jdoubleArray hostArray, jlong hostOffset, jboolean blocking, jlong offset, jlong numBytes, jlong devicePtr, jlongArray javaArrayEvents) {
    return transferFromHostToDevice(env, klass, commandQueue, reinterpret_cast<jbyteArray>(hostArray), hostOffset, blocking, offset, numBytes, devicePtr, javaArrayEvents);
}

jlong transfersFromDeviceToHost(JNIEnv *env, jclass clazz, jlong commandQueue, jbyteArray hostArray, jlong hostOffset, jboolean blocking,
                                jlong offset, jlong numBytes, jlong devicePtr, jlongArray javaArrayEvents) {
    cl_bool blocking_read = blocking ? CL_TRUE : CL_FALSE;
    jsize num_bytes = (numBytes != -1) ? numBytes : env->GetArrayLength(hostArray) * sizeof(jbyteArray);
    jlong *__array2 = static_cast<jlong *>((javaArrayEvents != NULL) ? env->GetPrimitiveArrayCritical(javaArrayEvents, NULL) : NULL);
    jlong *events = (javaArrayEvents != NULL) ? &__array2[1] : NULL;
    jsize num_events = (javaArrayEvents != NULL) ? __array2[0] : 0;
    jbyte *buffer = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(hostArray, NULL));
    if (PRINT_DATA_SIZES) {
        std::cout << "[TornadoVM JNI] transfersFromDeviceToHost from " << offset << " (" << numBytes << ") from buffer: " << buffer << std::endl;
    }
    cl_event event;
    cl_int status = clEnqueueReadBuffer((cl_command_queue) commandQueue, (cl_mem) devicePtr, blocking_read,
                                        (size_t) offset, (size_t) num_bytes, (void *) &buffer[hostOffset],
                                        (cl_uint) num_events, (cl_event *) events, &event);
    if (status != CL_SUCCESS) {
        printf("[ERROR] clEnqueueReadBuffer, code = %d n", status);
    }
    LOG_OCL_JNI("clEnqueueReadBuffer", status);
    if (PRINT_DATA_TIMES) {
        long readTime = getElapsedTimeEvent(event); /* clWaitForEvents call a side effect of this call so safe to not wait */
        std::cout << "[TornadoVM-JNI] D2H time: " << readTime << " (ns)" << std::endl;
    } else {
        /* we must wait irrespective of jboolean blocking flag or we risk Java GC/OpenCL Runtime race condition */
        clWaitForEvents(1, &event);
    }
    if (hostArray != NULL) {
        env->ReleasePrimitiveArrayCritical(hostArray, buffer, JNI_ABORT);
    }
    if (javaArrayEvents != NULL) {
        env->ReleasePrimitiveArrayCritical(javaArrayEvents, __array2, JNI_ABORT);
    }
    return (jlong) event;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDevice
 * Signature: (J[BJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3BJZJJJ_3J
        (JNIEnv *env, jclass clazz, jlong commandQueue, jbyteArray hostArray, jlong hostOffset, jboolean blocking,
         jlong offset, jlong numBytes, jlong devicePtr, jlongArray javaArrayEvents) {
    return transfersFromDeviceToHost(env, clazz, commandQueue, hostArray, hostOffset, blocking, offset, numBytes, devicePtr, javaArrayEvents);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDevice
 * Signature: (J[CJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3CJZJJJ_3J
        (JNIEnv *env, jclass clazz, jlong commandQueue, jcharArray hostArray, jlong hostOffset, jboolean blocking,
         jlong offset, jlong numBytes, jlong devicePtr, jlongArray javaArrayEvents) {
    return transfersFromDeviceToHost(env, clazz, commandQueue, reinterpret_cast<jbyteArray>(hostArray), hostOffset, blocking, offset, numBytes,
                                     devicePtr, javaArrayEvents);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDevice
 * Signature: (J[SJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3SJZJJJ_3J
        (JNIEnv *env, jclass clazz, jlong commandQueue, jshortArray hostArray, jlong hostOffset, jboolean blocking,
         jlong offset, jlong numBytes, jlong devicePtr, jlongArray javaArrayEvents) {
    return transfersFromDeviceToHost(env, clazz, commandQueue, reinterpret_cast<jbyteArray>(hostArray), hostOffset, blocking, offset, numBytes,
                                     devicePtr, javaArrayEvents);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDevice
 * Signature: (J[IJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3IJZJJJ_3J
        (JNIEnv *env, jclass clazz, jlong commandQueue, jintArray hostArray, jlong hostOffset, jboolean blocking,
         jlong offset, jlong numBytes, jlong devicePtr, jlongArray javaArrayEvents) {
    return transfersFromDeviceToHost(env, clazz, commandQueue, reinterpret_cast<jbyteArray>(hostArray), hostOffset, blocking, offset, numBytes, devicePtr, javaArrayEvents);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDevice
 * Signature: (J[JJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3JJZJJJ_3J
        (JNIEnv *env, jclass clazz, jlong commandQueue, jlongArray hostArray, jlong hostOffset, jboolean blocking,
         jlong offset, jlong numBytes, jlong devicePtr, jlongArray javaArrayEvents) {
    return transfersFromDeviceToHost(env, clazz, commandQueue, reinterpret_cast<jbyteArray>(hostArray), hostOffset, blocking, offset, numBytes, devicePtr, javaArrayEvents);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDevice
 * Signature: (J[FJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3FJZJJJ_3J
        (JNIEnv *env, jclass clazz, jlong commandQueue, jfloatArray hostArray, jlong hostOffset, jboolean blocking,
         jlong offset, jlong numBytes, jlong devicePtr, jlongArray javaArrayEvents) {
    return transfersFromDeviceToHost(env, clazz, commandQueue, reinterpret_cast<jbyteArray>(hostArray), hostOffset, blocking, offset, numBytes, devicePtr, javaArrayEvents);
}
/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDevice
 * Signature: (J[DJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3DJZJJJ_3J
        (JNIEnv *env, jclass clazz, jlong commandQueue, jdoubleArray hostArray, jlong hostOffset, jboolean blocking,
         jlong offset, jlong numBytes, jlong devicePtr, jlongArray javaArrayEvents) {
    return transfersFromDeviceToHost(env, clazz, commandQueue, reinterpret_cast<jbyteArray>(hostArray), hostOffset, blocking, offset, numBytes, devicePtr, javaArrayEvents);
}
