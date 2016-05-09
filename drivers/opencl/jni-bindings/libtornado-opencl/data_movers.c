#include <jni.h>
#ifdef _OSX
#include <OpenCL/cl.h>
#else
#include <CL/cl.h>
#endif
#include <stdio.h>
#include "macros.h"
#include "utils.h"


///*
// * Class:     jacc_runtime_drivers_opencl_OCLContext
// * Method:    createBuffer
// * Signature: (JJJ)J
// */
//JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLContext_createBuffer
//(JNIEnv *env, jclass clazz, jlong context_id, jlong flags, jlong len){
//    OPENCL_PROLOGUE;
//
//    void *mapped_buffer = malloc(len);
//    printf("create: %p\n",mapped_buffer);
//
//    cl_mem mem;
//    OPENCL_CHECK_ERROR("clCreateBuffer (long)", mem = clCreateBuffer((cl_context) context_id, (cl_mem_flags) flags | CL_MEM_USE_HOST_PTR, (size_t) len, mapped_buffer, &error_id),-1);
//
//    return (jlong) mem;
//}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLContext
 * Method:    createArrayOnDevice
 * Signature: (JJ[B)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLContext_createArrayOnDevice__JJ_3B
(JNIEnv *env, jclass clazz, jlong context_id, jlong flags, jbyteArray array){
        OPENCL_PROLOGUE;
        
    jsize len = (*env)->GetArrayLength(env, array);
    jbyte *buffer = (*env)->GetPrimitiveArrayCritical(env, array, NULL);

    cl_mem mem;
    OPENCL_CHECK_ERROR("clCreateBuffer (byte)", mem = clCreateBuffer((cl_context) context_id, (cl_mem_flags) flags, (size_t) len, (void *) buffer, &error_id),-1);
    
   (*env)->ReleasePrimitiveArrayCritical(env, array, buffer, JNI_ABORT);
        
   return (jlong) mem;
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLContext
 * Method:    createArrayOnDevice
 * Signature: (JJ[I)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLContext_createArrayOnDevice__JJ_3I
(JNIEnv *env, jclass clazz, jlong context_id, jlong flags, jintArray array){
    OPENCL_PROLOGUE;
    
    jsize len = (*env)->GetArrayLength(env, array) * sizeof(jint);
    jint *buffer = (*env)->GetPrimitiveArrayCritical(env, array, NULL);
    
    cl_mem mem;
    OPENCL_CHECK_ERROR("clCreateBuffer (int)", mem = clCreateBuffer((cl_context) context_id, (cl_mem_flags) flags, (size_t) len, (void *) buffer, &error_id),-1);
   
    (*env)->ReleasePrimitiveArrayCritical(env, array, buffer, JNI_ABORT);
    
    return (jlong) mem;
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLContext
 * Method:    createArrayOnDevice
 * Signature: (JJ[F)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLContext_createArrayOnDevice__JJ_3F
(JNIEnv *env, jclass clazz, jlong context_id, jlong flags, jfloatArray array){
    OPENCL_PROLOGUE;

    jsize len = (*env)->GetArrayLength(env, array) * sizeof(jfloat);
    jfloat *buffer = (*env)->GetPrimitiveArrayCritical(env, array, NULL);

    cl_mem mem;
    OPENCL_CHECK_ERROR("clCreateBuffer (float)", mem = clCreateBuffer((cl_context) context_id, (cl_mem_flags) flags, (size_t) len, (void *) buffer, &error_id),-1);
    
    (*env)->ReleasePrimitiveArrayCritical(env, array, buffer, JNI_ABORT);
    
    return (jlong) mem;
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLContext
 * Method:    createArrayOnDevice
 * Signature: (JJ[D)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLContext_createArrayOnDevice__JJ_3D
(JNIEnv *env, jclass clazz, jlong context_id, jlong flags, jdoubleArray array){
    OPENCL_PROLOGUE;
    
    jsize len = (*env)->GetArrayLength(env, array) * sizeof(jdouble);
    jdouble *buffer = (*env)->GetPrimitiveArrayCritical(env, array, NULL);

    cl_mem mem;
    OPENCL_CHECK_ERROR("clCreateBuffer (double)", mem = clCreateBuffer((cl_context) context_id, (cl_mem_flags) flags, (size_t) len, (void *) buffer, &error_id),-1);
    
    (*env)->ReleasePrimitiveArrayCritical(env, array, buffer, JNI_ABORT);
    
    return (jlong) mem;
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLCommandQueue
 * Method:    writeArrayToDevice
 * Signature: (J[BZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3BZJJJ_3J
(JNIEnv *env, jclass clazz, jlong queue_id, jbyteArray array1, jboolean blocking, jlong offset, jlong cb, jlong device_ptr, jlongArray array2){
    OPENCL_PROLOGUE;
    
    cl_bool blocking_write = blocking ? CL_TRUE : CL_FALSE;
    jsize num_bytes = (cb != -1) ? cb : (*env)->GetArrayLength(env, array1) * sizeof(jbyte);
    jsize num_events = (array2 == NULL) ? 0 :(*env)->GetArrayLength(env, array2);

    jlong *events = (array2 == NULL) ? NULL : (*env)->GetPrimitiveArrayCritical(env, array2, NULL);
    jbyte *buffer = (*env)->GetPrimitiveArrayCritical(env, array1, NULL);

    cl_event event;
    OPENCL_SOFT_ERROR("clEnqueueWriteBuffer (byte)", clEnqueueWriteBuffer((cl_command_queue) queue_id, (cl_mem) device_ptr, blocking_write, (size_t) offset, (size_t) num_bytes, (void *) buffer,(cl_uint) num_events, (cl_event*) events, &event),-1);
    
    (*env)->ReleasePrimitiveArrayCritical(env, array1, buffer, JNI_ABORT);
    if(array2 != NULL)
        (*env)->ReleasePrimitiveArrayCritical(env, array2, events, JNI_ABORT);
    
    return (jlong) event;
}

/*
 * Class:     tornado_drivers_opencl_OCLCommandQueue
 * Method:    writeArrayToDevice
 * Signature: (J[SZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3SZJJJ_3J
  (JNIEnv *env, jclass clazz, jlong queue_id, jshortArray array1, jboolean blocking, jlong offset, jlong cb, jlong device_ptr, jlongArray array2) {
	 OPENCL_PROLOGUE;

	cl_bool blocking_write = blocking ? CL_TRUE : CL_FALSE;
    jsize num_bytes = (cb != -1) ? cb : (*env)->GetArrayLength(env, array1) * sizeof(jshort);
    jsize num_events = (array2 == NULL) ? 0 :(*env)->GetArrayLength(env, array2);

    jlong *events = (array2 == NULL) ? NULL : (*env)->GetPrimitiveArrayCritical(env, array2, NULL);
    jshort *buffer = (*env)->GetPrimitiveArrayCritical(env, array1, NULL);

    cl_event event;
    OPENCL_SOFT_ERROR("clEnqueueWriteBuffer (short)", clEnqueueWriteBuffer((cl_command_queue) queue_id, (cl_mem) device_ptr, blocking_write, (size_t) offset, (size_t) num_bytes, (void *) buffer,(cl_uint) num_events, (cl_event*) events, &event),-1);

    (*env)->ReleasePrimitiveArrayCritical(env, array1, buffer, JNI_ABORT);
    if(array2 != NULL)
        (*env)->ReleasePrimitiveArrayCritical(env, array2, events, JNI_ABORT);

    return (jlong) event;
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLCommandQueue
 * Method:    writeArrayToDevice
 * Signature: (J[IZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3IZJJJ_3J
(JNIEnv *env, jclass clazz, jlong queue_id, jintArray array1, jboolean blocking, jlong offset, jlong cb, jlong device_ptr, jlongArray array2){
    OPENCL_PROLOGUE;
    
    cl_bool blocking_write = blocking ? CL_TRUE : CL_FALSE;
    jsize num_bytes = (cb != -1) ? cb : (*env)->GetArrayLength(env, array1) * sizeof(jint);
    jsize num_events = (array2 == NULL) ? 0 :(*env)->GetArrayLength(env, array2);

    jlong *events = (array2 == NULL) ? NULL : (*env)->GetPrimitiveArrayCritical(env, array2, NULL);
    jint *buffer = (*env)->GetPrimitiveArrayCritical(env, array1, NULL);
    
    cl_event event;
    OPENCL_SOFT_ERROR("clEnqueueWriteBuffer (int)", clEnqueueWriteBuffer((cl_command_queue) queue_id, (cl_mem) device_ptr, blocking_write, (size_t) offset, (size_t) num_bytes, (void *) buffer,(cl_uint) num_events, (cl_event*) events, &event),-1);
    
    (*env)->ReleasePrimitiveArrayCritical(env, array1, buffer, JNI_ABORT);
    if(array2 != NULL)
        (*env)->ReleasePrimitiveArrayCritical(env, array2, events, JNI_ABORT);
    
    return (jlong) event;
}

/*
 * Class:     tornado_drivers_opencl_OCLCommandQueue
 * Method:    writeArrayToDevice
 * Signature: (J[JZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3JZJJJ_3J
  (JNIEnv *env, jclass clazz, jlong queue_id, jlongArray array1, jboolean blocking, jlong offset, jlong cb, jlong device_ptr, jlongArray array2){
	 OPENCL_PROLOGUE;

	    cl_bool blocking_write = blocking ? CL_TRUE : CL_FALSE;
	    jsize num_bytes = (cb != -1) ? cb : (*env)->GetArrayLength(env, array1) * sizeof(jlong);
	    jsize num_events = (array2 == NULL) ? 0 :(*env)->GetArrayLength(env, array2);

	    jlong *events = (array2 == NULL) ? NULL : (*env)->GetPrimitiveArrayCritical(env, array2, NULL);
	    jlong *buffer = (*env)->GetPrimitiveArrayCritical(env, array1, NULL);

	    cl_event event;
	    OPENCL_SOFT_ERROR("clEnqueueWriteBuffer (long)", clEnqueueWriteBuffer((cl_command_queue) queue_id, (cl_mem) device_ptr, blocking_write, (size_t) offset, (size_t) num_bytes, (void *) buffer,(cl_uint) num_events, (cl_event*) events, &event),-1);

	    (*env)->ReleasePrimitiveArrayCritical(env, array1, buffer, JNI_ABORT);
	    if(array2 != NULL)
	        (*env)->ReleasePrimitiveArrayCritical(env, array2, events, JNI_ABORT);

	    return (jlong) event;
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLCommandQueue
 * Method:    writeArrayToDevice
 * Signature: (J[FZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3FZJJJ_3J
(JNIEnv *env, jclass clazz, jlong queue_id, jfloatArray array1, jboolean blocking, jlong offset, jlong cb, jlong device_ptr, jlongArray array2){
    OPENCL_PROLOGUE;
    
    cl_bool blocking_write = blocking ? CL_TRUE : CL_FALSE;
    jsize num_bytes = (cb != -1) ? cb : (*env)->GetArrayLength(env, array1) * sizeof(jfloat);
    jsize num_events = (array2 == NULL) ? 0 :(*env)->GetArrayLength(env, array2);
    
    jlong *events = (array2 == NULL) ? NULL : (*env)->GetPrimitiveArrayCritical(env, array2, NULL);
    jfloat *buffer = (*env)->GetPrimitiveArrayCritical(env, array1, NULL);
    
    cl_event event;
    OPENCL_SOFT_ERROR("clEnqueueWriteBuffer (float)", clEnqueueWriteBuffer((cl_command_queue) queue_id, (cl_mem) device_ptr, blocking_write, (size_t) offset, (size_t) num_bytes, (void *) buffer,(cl_uint) num_events, (cl_event*) events, &event),-1);
    
    (*env)->ReleasePrimitiveArrayCritical(env, array1, buffer, JNI_ABORT);
    if(array2 != NULL)
        (*env)->ReleasePrimitiveArrayCritical(env, array2, events, JNI_ABORT);
    
    return (jlong) event;
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLCommandQueue
 * Method:    writeArrayToDevice
 * Signature: (J[DZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3DZJJJ_3J
(JNIEnv *env, jclass clazz, jlong queue_id, jdoubleArray array1, jboolean blocking, jlong offset, jlong cb, jlong device_ptr, jlongArray array2){
    OPENCL_PROLOGUE;
    
    cl_bool blocking_write = blocking ? CL_TRUE : CL_FALSE;
    jsize num_bytes = (cb != -1) ? cb : (*env)->GetArrayLength(env, array1) * sizeof(jdouble);
    jsize num_events = (array2 == NULL) ? 0 :(*env)->GetArrayLength(env, array2);
    
    jlong *events = (array2 == NULL) ? NULL : (*env)->GetPrimitiveArrayCritical(env, array2, NULL);
    jdouble *buffer = (*env)->GetPrimitiveArrayCritical(env, array1, NULL);
    
    cl_event event;
    OPENCL_SOFT_ERROR("clEnqueueWriteBuffer (double)", clEnqueueWriteBuffer((cl_command_queue) queue_id, (cl_mem) device_ptr, blocking_write, (size_t) offset, (size_t) num_bytes, (void *) buffer,(cl_uint) num_events, (cl_event*) events, &event),-1);
    
    (*env)->ReleasePrimitiveArrayCritical(env, array1, buffer, JNI_ABORT);
    if(array2 != NULL)
        (*env)->ReleasePrimitiveArrayCritical(env, array2, events, JNI_ABORT);
    
    return (jlong) event;

}


/*
 * Class:     jacc_runtime_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDevice
 * Signature: (J[BZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3BZJJJ_3J
(JNIEnv *env, jclass clazz, jlong queue_id, jbyteArray array1, jboolean blocking, jlong offset, jlong cb, jlong device_ptr, jlongArray array2){
    OPENCL_PROLOGUE;
    
    cl_bool blocking_read = blocking ? CL_TRUE : CL_FALSE;
    jsize num_events = (array2 == NULL) ? 0 :(*env)->GetArrayLength(env, array2);
    jsize num_bytes = (cb != -1) ? cb : (*env)->GetArrayLength(env, array1) * sizeof(jbyte);

    jlong *events = (array2 == NULL) ? NULL : (*env)->GetPrimitiveArrayCritical(env, array2, NULL);
    jbyte *buffer = (*env)->GetPrimitiveArrayCritical(env, array1, NULL);
    
    cl_event event;
    OPENCL_SOFT_ERROR("clEnqueueReadBuffer (byte)", clEnqueueReadBuffer((cl_command_queue) queue_id, (cl_mem) device_ptr, blocking, (size_t) offset, (size_t) num_bytes, (void *) buffer,(cl_uint) num_events, (cl_event*) events, &event),-1);

    (*env)->ReleasePrimitiveArrayCritical(env, array1, buffer, 0);
    
    if(array2 != NULL)
        (*env)->ReleasePrimitiveArrayCritical(env, array2, events, JNI_ABORT);
    
    return (jlong) event;
}

/*
 * Class:     tornado_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDevice
 * Signature: (J[SZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3SZJJJ_3J
  (JNIEnv *env, jclass clazz, jlong queue_id, jshortArray array1, jboolean blocking, jlong offset, jlong cb, jlong device_ptr, jlongArray array2){
    OPENCL_PROLOGUE;

    cl_bool blocking_read = blocking ? CL_TRUE : CL_FALSE;
    jsize num_bytes = (cb != -1) ? cb : (*env)->GetArrayLength(env, array1) * sizeof(jshort);
    jsize num_events = (array2 == NULL) ? 0 :(*env)->GetArrayLength(env, array2);

    jlong *events = (array2 == NULL) ? NULL : (*env)->GetPrimitiveArrayCritical(env, array2, NULL);
    jshort *buffer = (*env)->GetPrimitiveArrayCritical(env, array1, NULL);

    cl_event event;
    OPENCL_SOFT_ERROR("clEnqueueReadBuffer (short)", clEnqueueReadBuffer((cl_command_queue) queue_id, (cl_mem) device_ptr, blocking_read, (size_t) offset, (size_t) num_bytes, (void *) buffer,(cl_uint) num_events, (cl_event*) events, &event),-1);

    (*env)->ReleasePrimitiveArrayCritical(env, array1, buffer, 0);

    if(array2 != NULL)
        (*env)->ReleasePrimitiveArrayCritical(env, array2, events, JNI_ABORT);

    return (jlong) event;
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDevice
 * Signature: (J[IZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3IZJJJ_3J
(JNIEnv *env, jclass clazz, jlong queue_id, jintArray array1, jboolean blocking, jlong offset, jlong cb, jlong device_ptr, jlongArray array2){
    OPENCL_PROLOGUE;
    
    cl_bool blocking_read = blocking ? CL_TRUE : CL_FALSE;
    jsize num_bytes = (cb != -1) ? cb : (*env)->GetArrayLength(env, array1) * sizeof(jint);
    jsize num_events = (array2 == NULL) ? 0 :(*env)->GetArrayLength(env, array2);

    jlong *events = (array2 == NULL) ? NULL : (*env)->GetPrimitiveArrayCritical(env, array2, NULL);
    jint *buffer = (*env)->GetPrimitiveArrayCritical(env, array1, NULL);
    
    cl_event event;
    OPENCL_SOFT_ERROR("clEnqueueReadBuffer (int)", clEnqueueReadBuffer((cl_command_queue) queue_id, (cl_mem) device_ptr, blocking_read, (size_t) offset, (size_t) num_bytes, (void *) buffer,(cl_uint) num_events, (cl_event*) events, &event),-1);
    
    (*env)->ReleasePrimitiveArrayCritical(env, array1, buffer, 0);
    
    if(array2 != NULL)
        (*env)->ReleasePrimitiveArrayCritical(env, array2, events, JNI_ABORT);
    
    return (jlong) event;
}

/*
 * Class:     tornado_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDevice
 * Signature: (J[JZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3JZJJJ_3J
  (JNIEnv *env, jclass clazz, jlong queue_id, jlongArray array1, jboolean blocking, jlong offset, jlong cb, jlong device_ptr, jlongArray array2){
	 OPENCL_PROLOGUE;

	    cl_bool blocking_read = blocking ? CL_TRUE : CL_FALSE;
	    jsize num_bytes = (cb != -1) ? cb : (*env)->GetArrayLength(env, array1) * sizeof(jlong);
	    jsize num_events = (array2 == NULL) ? 0 :(*env)->GetArrayLength(env, array2);

	    jlong *events = (array2 == NULL) ? NULL : (*env)->GetPrimitiveArrayCritical(env, array2, NULL);
	    jlong *buffer = (*env)->GetPrimitiveArrayCritical(env, array1, NULL);

	    cl_event event;
	    OPENCL_SOFT_ERROR("clEnqueueReadBuffer (long)", clEnqueueReadBuffer((cl_command_queue) queue_id, (cl_mem) device_ptr, blocking_read, (size_t) offset, (size_t) num_bytes, (void *) buffer,(cl_uint) num_events, (cl_event*) events, &event),-1);

	    (*env)->ReleasePrimitiveArrayCritical(env, array1, buffer, 0);

	    if(array2 != NULL)
	        (*env)->ReleasePrimitiveArrayCritical(env, array2, events, JNI_ABORT);

	    return (jlong) event;
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDevice
 * Signature: (J[FZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3FZJJJ_3J
(JNIEnv *env, jclass clazz, jlong queue_id, jfloatArray array1, jboolean blocking, jlong offset, jlong cb, jlong device_ptr, jlongArray array2){
    OPENCL_PROLOGUE;
    
    cl_bool blocking_read = blocking ? CL_TRUE : CL_FALSE;
    jsize num_bytes = (cb != -1) ? cb : (*env)->GetArrayLength(env, array1) * sizeof(jfloat);
    jsize num_events = (array2 == NULL) ? 0 :(*env)->GetArrayLength(env, array2);

    jlong *events = (array2 == NULL) ? NULL : (*env)->GetPrimitiveArrayCritical(env, array2, NULL);
    jfloat *buffer = (*env)->GetPrimitiveArrayCritical(env, array1, NULL);
    
    cl_event event;
    OPENCL_SOFT_ERROR("clEnqueueReadBuffer (float)", clEnqueueReadBuffer((cl_command_queue) queue_id, (cl_mem) device_ptr, blocking_read, (size_t) offset, (size_t) num_bytes, (void *) buffer,(cl_uint) num_events, (cl_event*) events, &event),-1);
    
    (*env)->ReleasePrimitiveArrayCritical(env, array1, buffer, 0);
    
    if(array2 != NULL)
        (*env)->ReleasePrimitiveArrayCritical(env, array2, events, JNI_ABORT);
    
    return (jlong) event;
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDevice
 * Signature: (J[DZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3DZJJJ_3J
(JNIEnv *env, jclass clazz, jlong queue_id, jdoubleArray array1, jboolean blocking, jlong offset, jlong cb, jlong device_ptr, jlongArray array2){
    OPENCL_PROLOGUE;
    
    cl_bool blocking_read = blocking ? CL_TRUE : CL_FALSE;
    jsize num_bytes = (cb != -1) ? cb : (*env)->GetArrayLength(env, array1) * sizeof(jdouble);
    jsize num_events = (array2 == NULL) ? 0 :(*env)->GetArrayLength(env, array2);

    jlong *events = (array2 == NULL) ? NULL : (*env)->GetPrimitiveArrayCritical(env, array2, NULL);
    jdouble *buffer = (*env)->GetPrimitiveArrayCritical(env, array1, NULL);
    
    cl_event event;
    OPENCL_SOFT_ERROR("clEnqueueReadBuffer (double)", clEnqueueReadBuffer((cl_command_queue) queue_id, (cl_mem) device_ptr, blocking_read, (size_t) offset, (size_t) num_bytes, (void *) buffer,(cl_uint) num_events, (cl_event*) events, &event),-1);
    
    (*env)->ReleasePrimitiveArrayCritical(env, array1, buffer, 0);
    
    if(array2 != NULL)
        (*env)->ReleasePrimitiveArrayCritical(env, array2, events, JNI_ABORT);
    
    return (jlong) event;
}
