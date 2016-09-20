#include <jni.h>
#ifdef _OSX
#include <OpenCL/cl.h>
#else
#include <CL/cl.h>
#endif
#include <stdio.h>
#include "macros.h"
#include "utils.h"

#define CREATE_ARRAY(classname,sig,type) \
    JNIEXPORT jlong JNICALL classname ## _createArrayOnDevice__JJ_3 ## sig \
        (JNIEnv *env, jclass clazz, jlong context_id, jlong flags, j ## type ## Array array){  \
            OPENCL_PROLOGUE; \
            jsize len = (*env)->GetArrayLength(env, array); \
            jbyte *buffer = (*env)->GetPrimitiveArrayCritical(env, array, NULL); \
            cl_mem mem; \
            OPENCL_CHECK_ERROR("clCreateBuffer (byte)", mem = clCreateBuffer((cl_context) context_id, (cl_mem_flags) flags, (size_t) len, (void *) buffer, &error_id),-1); \
            (*env)->ReleasePrimitiveArrayCritical(env, array, buffer, JNI_ABORT); \
            return (jlong) mem; \
        } 

CREATE_ARRAY(Java_tornado_drivers_opencl_OCLContext, B, byte)
CREATE_ARRAY(Java_tornado_drivers_opencl_OCLContext, I, int)
CREATE_ARRAY(Java_tornado_drivers_opencl_OCLContext, J, long)
CREATE_ARRAY(Java_tornado_drivers_opencl_OCLContext, F, float)
CREATE_ARRAY(Java_tornado_drivers_opencl_OCLContext, D, double)

#define WRITE_ARRAY(CLASSNAME,SIG,TYPE) \
    JNIEXPORT jlong JNICALL CLASSNAME ## _writeArrayToDevice__J_3 ## SIG ## ZJJJ_3J \
        (JNIEnv *env, jclass clazz, jlong queue_id, j ## TYPE ## Array array1, jboolean blocking, jlong offset, jlong cb, jlong device_ptr, jlongArray array2){ \
            OPENCL_PROLOGUE; \
            cl_bool blocking_write = blocking ? CL_TRUE : CL_FALSE; \
            jsize num_bytes = (cb != -1) ? cb : (*env)->GetArrayLength(env, array1) * sizeof ( j ## TYPE ); \
            debug("write array> 0x%lx (%d bytes) \n",offset, num_bytes);\
            OPENCL_DECODE_WAITLIST(array2, events, num_events) \
            JNI_ACQUIRE_ARRAY(jbyte,buffer,array1);\
            cl_event event; \
            OPENCL_SOFT_ERROR("clEnqueueWriteBuffer (" #TYPE  ")", clEnqueueWriteBuffer((cl_command_queue) queue_id, (cl_mem) device_ptr, blocking_write, (size_t) offset, (size_t) num_bytes, (void *) buffer,(cl_uint) num_events, (cl_event*) events, &event),-1); \
            JNI_RELEASE_ARRAY(array1,buffer); \
            OPENCL_RELEASE_WAITLIST(array2); \
            return (jlong) event; \
    }

WRITE_ARRAY(Java_tornado_drivers_opencl_OCLCommandQueue, B, byte)
WRITE_ARRAY(Java_tornado_drivers_opencl_OCLCommandQueue, S, short)
WRITE_ARRAY(Java_tornado_drivers_opencl_OCLCommandQueue, I, int)
WRITE_ARRAY(Java_tornado_drivers_opencl_OCLCommandQueue, J, long)
WRITE_ARRAY(Java_tornado_drivers_opencl_OCLCommandQueue, F, float)
WRITE_ARRAY(Java_tornado_drivers_opencl_OCLCommandQueue, D, double)


#define READ_ARRAY(CLASSNAME, SIG, TYPE) \
    JNIEXPORT jlong JNICALL CLASSNAME ## _readArrayFromDevice__J_3 ## SIG ## ZJJJ_3J \
        (JNIEnv *env, jclass clazz, jlong queue_id, j ## TYPE ##Array array1, jboolean blocking, jlong offset, jlong cb, jlong device_ptr, jlongArray array2) { \
            OPENCL_PROLOGUE; \
            cl_bool blocking_read = blocking ? CL_TRUE : CL_FALSE; \
            jsize num_bytes = (cb != -1) ? cb : (*env)->GetArrayLength(env, array1) * sizeof ( j ## TYPE ); \
            debug("read array> 0x%lx (%d bytes) \n",offset, num_bytes);\
            OPENCL_DECODE_WAITLIST(array2, events, num_events) \
            JNI_ACQUIRE_ARRAY(jbyte,buffer,array1);\
            cl_event event; \
            OPENCL_SOFT_ERROR("clEnqueueReadBuffer (" #TYPE ")", clEnqueueReadBuffer((cl_command_queue) queue_id, (cl_mem) device_ptr, blocking, (size_t) offset, (size_t) num_bytes, (void *) buffer, (cl_uint) num_events, (cl_event*) events, &event), -1); \
            JNI_RELEASE_ARRAY(array1, buffer); \
            OPENCL_RELEASE_WAITLIST(array2); \
            return (jlong) event; \
    }
READ_ARRAY(Java_tornado_drivers_opencl_OCLCommandQueue,B,byte)
READ_ARRAY(Java_tornado_drivers_opencl_OCLCommandQueue,S,short)
READ_ARRAY(Java_tornado_drivers_opencl_OCLCommandQueue,I,int)
READ_ARRAY(Java_tornado_drivers_opencl_OCLCommandQueue,J,long)
READ_ARRAY(Java_tornado_drivers_opencl_OCLCommandQueue,F,float)
READ_ARRAY(Java_tornado_drivers_opencl_OCLCommandQueue,D,double)
