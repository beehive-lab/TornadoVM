#include <jni.h>
#ifdef _OSX
#include <OpenCL/cl.h>
#else
#include <CL/cl.h>
#endif
#include <stdio.h>
#include "macros.h"
#include "utils.h"

/*
 * Class:     tornado_drivers_opencl_OCLEvent
 * Method:    clGetEventInfo
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLEvent_clGetEventInfo
(JNIEnv *env, jclass clazz, jlong event_id, jint param_name, jbyteArray array) {
    OPENCL_PROLOGUE;

    jbyte *value;
    jsize len;

    value = (*env)->GetPrimitiveArrayCritical(env, array, NULL);
    len = (*env)->GetArrayLength(env, array);

    size_t return_size = 0;
    OPENCL_SOFT_ERROR("clGetEventInfo",
            clGetEventInfo((cl_event) event_id, (cl_event_info) param_name, len, (void *) value, &return_size),);

    (*env)->ReleasePrimitiveArrayCritical(env, array, value, 0);
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLEvent
 * Method:    clGetEventProfilingInfo
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLEvent_clGetEventProfilingInfo
(JNIEnv *env, jclass clazz, jlong event_id, jint param_name, jbyteArray array) {
    OPENCL_PROLOGUE;

    jbyte *value;
    jsize len;

    value = (*env)->GetPrimitiveArrayCritical(env, array, NULL);
    len = (*env)->GetArrayLength(env, array);

    size_t return_size = 0;
    OPENCL_SOFT_ERROR("clGetEventProfilingInfo",
            clGetEventProfilingInfo((cl_event) event_id, (cl_profiling_info) param_name, len, (void *) value, &return_size),);

    (*env)->ReleasePrimitiveArrayCritical(env, array, value, 0);
}

/*
 * Class:     tornado_drivers_opencl_OCLEvent
 * Method:    clWaitForEvents
 * Signature: ([J)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLEvent_clWaitForEvents
(JNIEnv *env, jclass clazz, jlongArray array) {
    OPENCL_PROLOGUE;

    OPENCL_DECODE_WAITLIST(array, events, len);

    size_t return_size = 0;
    OPENCL_SOFT_ERROR("clWaitForEvents",
            clWaitForEvents((cl_uint) len, (const cl_event *) events),);

    OPENCL_RELEASE_WAITLIST(array);
}

/*
 * Class:     tornado_drivers_opencl_OCLEvent
 * Method:    clReleaseEvent
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLEvent_clReleaseEvent
(JNIEnv *env, jclass clazz, jlong event) {
    OPENCL_PROLOGUE;
    OPENCL_SOFT_ERROR("clReleaseEvent",
            clReleaseEvent((const cl_event) event),);
}
