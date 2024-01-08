/*
 * MIT License
 *
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
    LOG_OCL_AND_VALIDATE("clReleaseCommandQueue", status);
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
    LOG_OCL_AND_VALIDATE("clGetCommandQueueInfo", status);
    env->ReleasePrimitiveArrayCritical(array, value, 0);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    clFlush
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clFlush
(JNIEnv *env, jclass clazz, jlong queue_id) {
    cl_int status = clFlush((cl_command_queue) queue_id);
    LOG_OCL_AND_VALIDATE("clFlush", status);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    clFinish
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clFinish
(JNIEnv *env, jclass clazz, jlong queue_id) {
    cl_int status = clFinish((cl_command_queue) queue_id);
    LOG_OCL_AND_VALIDATE("clFinish", status);
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
    LOG_OCL_AND_VALIDATE("clEnqueueNDRangeKernel", status);

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
 * Method:    clEnqueueWaitForEvents
 * Signature: (J[J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueWaitForEvents
(JNIEnv *env, jclass clazz, jlong queue_id, jlongArray array) {

    jlong *arrayEvents = static_cast<jlong *>((array != NULL) ? env->GetPrimitiveArrayCritical(array, NULL) : NULL);
    jlong *events = (array != NULL) ? &arrayEvents[1] : NULL;
    jsize len = (array != NULL) ? arrayEvents[0] : 0;
    cl_int status = clEnqueueWaitForEvents((cl_command_queue) queue_id, len, (cl_event *) events);
    LOG_OCL_AND_VALIDATE("clEnqueueWaitForEvents", status);

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
    LOG_OCL_AND_VALIDATE("clEnqueueMarkerWithWaitList", status);

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
    LOG_OCL_AND_VALIDATE("clEnqueueBarrierWithWaitList", status);
    if (array != NULL) {
        env->ReleasePrimitiveArrayCritical(array, arrayEvents, JNI_ABORT);
    }
    return (jlong) event;
}

jlong transferFromHostToDevice(JNIEnv * env, jclass javaClass,
                               jlong commandQueue,          // Pointer to the OpenCL Command Queue
                               jbyteArray hostArray,        // Host Array
                               jlong hostOffset,            // Offset within the host array
                               jboolean blocking,           // Perform a blocking/Async call
                               jlong deviceOffset,          // Offset within the device buffer
                               jlong numBytes,              // Number of bytes to copy
                               jlong devicePtr,             // Pointer to the device buffer
                               jlongArray javaArrayEvents   // Events array
                               ) {

    cl_bool blocking_write = blocking ? CL_TRUE : CL_FALSE;
    jlong *arrayEvents = static_cast<jlong *>((javaArrayEvents != NULL) ? env->GetPrimitiveArrayCritical(javaArrayEvents, NULL) : NULL);
    jlong *events = (javaArrayEvents != NULL) ? &arrayEvents[1] : NULL;
    jsize numberOfEvents = (javaArrayEvents != NULL) ? arrayEvents[0] : 0;

    jbyte *buffer = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(hostArray, NULL));
    if (PRINT_DATA_SIZES) {
        std::cout << "[TornadoVM JNI] transferFromHostToDevice from " << deviceOffset << " (" << numBytes << ") from buffer: " << buffer << std::endl;
    }
    cl_event event;
    /* we must wait irrespective of jboolean blocking flag or we risk Java GC/OpenCL Runtime race condition */
    cl_int status = clEnqueueWriteBuffer((cl_command_queue) commandQueue, (cl_mem) devicePtr, CL_TRUE,
                                         (size_t) deviceOffset, (size_t) numBytes, &buffer[hostOffset], (cl_uint) numberOfEvents,
                                         (cl_event *) events, &event);
    LOG_OCL_AND_VALIDATE("clEnqueueWriteBuffer", status);
    if (PRINT_DATA_TIMES) {
        long writeTime = getElapsedTimeEvent(event);
        std::cout << "[TornadoVM-JNI] H2D time: " << writeTime << " (ns)" << std::endl;
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

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    writeArrayToDevice
 * Signature: (JJJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__JJJZJJJ_3J
        (JNIEnv * env, jclass klass, jlong commandQueue, jlong hostBufferPointer, jlong hostOffset, jboolean blocking, jlong offset, jlong numBytes, jlong devicePtr, jlongArray javaArrayEvents) {
    cl_bool blocking_write = blocking ? CL_TRUE : CL_FALSE;
    jlong *arrayEvents = static_cast<jlong *>((javaArrayEvents != NULL) ? env->GetPrimitiveArrayCritical(javaArrayEvents, NULL) : NULL);
    jlong *events = (javaArrayEvents != NULL) ? &arrayEvents[1] : NULL;
    jsize numberOfEvents = (javaArrayEvents != NULL) ? arrayEvents[0] : 0;

    if (PRINT_DATA_SIZES) {
        std::cout << "[TornadoVM JNI] transferSegmentFromHostToDevice from offset: " << offset << " (bytes=" << numBytes << ") from buffer: " << hostBufferPointer << std::endl;
    }
    cl_event event;
    cl_int status = clEnqueueWriteBuffer((cl_command_queue) commandQueue,
                                         (cl_mem) devicePtr,
                                         blocking_write,
                                         (size_t) offset,
                                         (size_t) numBytes,
                                         (void*) (hostBufferPointer + hostOffset),
                                         (cl_uint) numberOfEvents,
                                         (cl_event *) events,
                                         &event);
    LOG_OCL_AND_VALIDATE("clEnqueueWriteBuffer", status);
    if (PRINT_DATA_TIMES) {
        long writeTime = getElapsedTimeEvent(event);
        std::cout << "[TornadoVM-JNI] H2D time: " << writeTime << " (ns)" << std::endl;
    }

    if (javaArrayEvents != NULL) {
        env->ReleasePrimitiveArrayCritical(javaArrayEvents, arrayEvents, JNI_ABORT);
    }
    return (jlong) event;
}

jlong transferFromDeviceToHost(JNIEnv *env, jclass javaClass,
                                jlong commandQueue,             // Pointer to the OpenCL command queue
                                jbyteArray hostArray,           // Host array
                                jlong hostOffset,               // Offset within the Host Array
                                jboolean blocking,              // Perform a blocking/async call
                                jlong offset,                   // Offset within the device buffer
                                jlong numBytes,                 // Number of bytes to be copied
                                jlong devicePtr,                // Pointer to the device array
                                jlongArray javaArrayEvents) {   // Array of previous events

    cl_bool blocking_read = blocking ? CL_TRUE : CL_FALSE;
    jlong *eventsArray = static_cast<jlong *>((javaArrayEvents != NULL) ? env->GetPrimitiveArrayCritical(javaArrayEvents, NULL) : NULL);
    jlong *events = (javaArrayEvents != NULL) ? &eventsArray[1] : NULL;
    jsize num_events = (javaArrayEvents != NULL) ? eventsArray[0] : 0;
    jbyte *buffer = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(hostArray, NULL));
    if (PRINT_DATA_SIZES) {
        std::cout << "[TornadoVM JNI] transferFromDeviceToHost from " << offset << " (" << numBytes << ") from buffer: " << buffer << std::endl;
    }
    cl_event readEvent;
    /* we must wait irrespective of jboolean blocking flag or we risk Java GC/OpenCL Runtime race condition */
    cl_int status = clEnqueueReadBuffer((cl_command_queue) commandQueue, (cl_mem) devicePtr, CL_TRUE,
                                        (size_t) offset, (size_t) numBytes, (void *) &buffer[hostOffset],
                                        (cl_uint) num_events, (cl_event *) events, &readEvent);
    if (status != CL_SUCCESS) {
        printf("[ERROR] clEnqueueReadBuffer, code = %d n", status);
    }
    LOG_OCL_AND_VALIDATE("clEnqueueReadBuffer", status);
    if (PRINT_DATA_TIMES) {
        long readTime = getElapsedTimeEvent(readEvent); /* clWaitForEvents call a side effect of this call so safe to not wait */
        std::cout << "[TornadoVM-JNI] D2H time: " << readTime << " (ns)" << std::endl;
    }
    if (hostArray != NULL) {
        env->ReleasePrimitiveArrayCritical(hostArray, buffer, JNI_ABORT);
    }
    if (javaArrayEvents != NULL) {
        env->ReleasePrimitiveArrayCritical(javaArrayEvents, eventsArray, JNI_ABORT);
    }
    return (jlong) readEvent;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDevice
 * Signature: (J[BJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3BJZJJJ_3J
        (JNIEnv *env, jclass clazz, jlong commandQueue, jbyteArray hostArray, jlong hostOffset, jboolean blocking,
         jlong offset, jlong numBytes, jlong devicePtr, jlongArray javaArrayEvents) {
    return transferFromDeviceToHost(env, clazz, commandQueue, hostArray, hostOffset, blocking, offset, numBytes, devicePtr, javaArrayEvents);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDevice
 * Signature: (J[CJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3CJZJJJ_3J
        (JNIEnv *env, jclass clazz, jlong commandQueue, jcharArray hostArray, jlong hostOffset, jboolean blocking,
         jlong offset, jlong numBytes, jlong devicePtr, jlongArray javaArrayEvents) {
    return transferFromDeviceToHost(env, clazz, commandQueue, reinterpret_cast<jbyteArray>(hostArray), hostOffset, blocking, offset, numBytes,
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
    return transferFromDeviceToHost(env, clazz, commandQueue, reinterpret_cast<jbyteArray>(hostArray), hostOffset, blocking, offset, numBytes,
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
    return transferFromDeviceToHost(env, clazz, commandQueue, reinterpret_cast<jbyteArray>(hostArray), hostOffset, blocking, offset, numBytes, devicePtr, javaArrayEvents);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDevice
 * Signature: (J[JJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3JJZJJJ_3J
        (JNIEnv *env, jclass clazz, jlong commandQueue, jlongArray hostArray, jlong hostOffset, jboolean blocking,
         jlong offset, jlong numBytes, jlong devicePtr, jlongArray javaArrayEvents) {
    return transferFromDeviceToHost(env, clazz, commandQueue, reinterpret_cast<jbyteArray>(hostArray), hostOffset, blocking, offset, numBytes, devicePtr, javaArrayEvents);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDevice
 * Signature: (J[FJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3FJZJJJ_3J
        (JNIEnv *env, jclass clazz, jlong commandQueue, jfloatArray hostArray, jlong hostOffset, jboolean blocking,
         jlong offset, jlong numBytes, jlong devicePtr, jlongArray javaArrayEvents) {
    return transferFromDeviceToHost(env, clazz, commandQueue, reinterpret_cast<jbyteArray>(hostArray), hostOffset, blocking, offset, numBytes, devicePtr, javaArrayEvents);
}
/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDevice
 * Signature: (J[DJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3DJZJJJ_3J
        (JNIEnv *env, jclass clazz, jlong commandQueue, jdoubleArray hostArray, jlong hostOffset, jboolean blocking,
         jlong offset, jlong numBytes, jlong devicePtr, jlongArray javaArrayEvents) {
    return transferFromDeviceToHost(env, clazz, commandQueue, reinterpret_cast<jbyteArray>(hostArray), hostOffset, blocking, offset, numBytes, devicePtr, javaArrayEvents);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDeviceOffHeap
 * Signature: (JJJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDeviceOffHeap__JJJZJJJ_3J
        (JNIEnv *env, jclass clazz, jlong commandQueue, jlong hostBufferPointer, jlong hostOffset, jboolean blocking,
         jlong offset, jlong numBytes, jlong devicePtr, jlongArray javaArrayEvents) {

     cl_bool blocking_read = blocking ? CL_TRUE : CL_FALSE;
     jlong *eventsArray = static_cast<jlong *>((javaArrayEvents != NULL) ? env->GetPrimitiveArrayCritical(javaArrayEvents, NULL) : NULL);
     jlong *eventWaitList = (javaArrayEvents != NULL) ? &eventsArray[1] : NULL;
     jsize num_events = (javaArrayEvents != NULL) ? eventsArray[0] : 0;

     if (PRINT_DATA_SIZES) {
         std::cout << "[TornadoVM JNI] transferSegmentFromDeviceToHost from offset: " << offset << " (bytes=" << numBytes << ") from buffer: " << hostBufferPointer << std::endl;
     }
     cl_event readEvent;
     cl_int status = clEnqueueReadBuffer((cl_command_queue) commandQueue,
                                         (cl_mem) devicePtr,
                                         blocking_read,
                                         (size_t) offset,
                                         (size_t) numBytes,
                                         (void *) (hostBufferPointer + hostOffset),
                                         (cl_uint) num_events,
                                         (cl_event *) eventWaitList, // check this array for multiple events
                                         &readEvent);
     if (status != CL_SUCCESS) {
         printf("[ERROR] clEnqueueReadBuffer, code = %d n", status);
     }
     LOG_OCL_AND_VALIDATE("clEnqueueReadBuffer", status);
     if (javaArrayEvents != NULL) {
         env->ReleasePrimitiveArrayCritical(javaArrayEvents, eventsArray, JNI_ABORT);
     }
     return (jlong) readEvent;
}
