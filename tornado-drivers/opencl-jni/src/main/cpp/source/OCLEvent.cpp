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
#include "OCLEvent.h"
#include "ocl_log.h"

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLEvent
 * Method:    clGetEventInfo
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLEvent_clGetEventInfo
        (JNIEnv *env, jclass clazz, jlong event_id, jint param_name, jbyteArray array) {
    jbyte *value;
    jsize len;
    value = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(array, NULL));
    len = env->GetArrayLength(array);
    size_t return_size = 0;
    cl_int status = clGetEventInfo((cl_event) event_id, (cl_event_info) param_name, len, (void *) value, &return_size);
    LOG_OCL_AND_VALIDATE("clGetEventInfo", status);
    env->ReleasePrimitiveArrayCritical(array, value, 0);
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLEvent
 * Method:    clGetEventProfilingInfo
 * Signature: (JJ[B)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLEvent_clGetEventProfilingInfo
        (JNIEnv *env, jclass clazz, jlong event_id, jlong param_name, jbyteArray array) {
    jbyte *value = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(array, NULL));
    size_t return_size = 0;
    cl_int status = clGetEventProfilingInfo((cl_event) event_id, (cl_profiling_info) param_name, sizeof(cl_ulong),
                                            (void *) value, NULL);
    LOG_OCL_AND_VALIDATE("clGetEventProfilingInfo", status);
    env->ReleasePrimitiveArrayCritical(array, value, 0);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLEvent
 * Method:    clWaitForEvents
 * Signature: ([J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLEvent_clWaitForEvents
        (JNIEnv *env, jclass clazz, jlongArray array) {
    if (array != NULL) {
        jsize len;
        cl_event *events = static_cast<cl_event *>(env->GetPrimitiveArrayCritical(array, NULL));
        len = env->GetArrayLength(array);
        cl_int status = clWaitForEvents((cl_uint) len, (const cl_event *) events);
        LOG_OCL_AND_VALIDATE("clWaitForEvents", status);
        env->ReleasePrimitiveArrayCritical(array, events, 0);
    }
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLEvent
 * Method:    clReleaseEvent
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLEvent_clReleaseEvent
        (JNIEnv *env, jclass clazz, jlong event) {
    cl_int status = clReleaseEvent((const cl_event) event);
    LOG_OCL_AND_VALIDATE("clReleaseEvent", status);
}
