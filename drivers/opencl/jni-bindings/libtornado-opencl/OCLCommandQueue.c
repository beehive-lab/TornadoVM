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
 * Class:     jacc_runtime_drivers_opencl_OCLCommandQueue
 * Method:    clReleaseCommandQueue
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_clReleaseCommandQueue
(JNIEnv *env, jclass clazz, jlong queue_id){
    OPENCL_PROLOGUE;
    
    OPENCL_SOFT_ERROR("clReleaseCommandQueue",clReleaseCommandQueue((cl_command_queue) queue_id),);
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLCommandQueue
 * Method:    clGetCommandQueueInfo
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_clGetCommandQueueInfo
(JNIEnv *env, jclass clazz, jlong queue_id, jint param_name, jbyteArray array){
    OPENCL_PROLOGUE;
    
    jbyte *value;
    jsize len;
    
    len = (*env)->GetArrayLength(env, array);
    value = (*env)->GetPrimitiveArrayCritical(env, array, NULL);
    
    size_t return_size = 0;
    OPENCL_SOFT_ERROR("clGetCommandQueueInfo",
                      clGetCommandQueueInfo((cl_command_queue) queue_id,(cl_command_queue_info) param_name,len,(void *)value,&return_size),);
    
    
    (*env)->ReleasePrimitiveArrayCritical(env, array, value, 0);
    
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLCommandQueue
 * Method:    clSetCommandQueueProperty
 * Signature: (JJZ)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_clSetCommandQueueProperty
(JNIEnv *env, jclass clazz, jlong queue_id, jlong properties, jboolean value){
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
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_clFlush
(JNIEnv *env, jclass clazz, jlong queue_id){
    OPENCL_PROLOGUE;
    
    OPENCL_SOFT_ERROR("clFlush",clFlush((cl_command_queue) queue_id),);
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLCommandQueue
 * Method:    clFinish
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_clFinish
(JNIEnv *env, jclass clazz, jlong queue_id){
    OPENCL_PROLOGUE;
    
    OPENCL_SOFT_ERROR("clFinish",clFinish((cl_command_queue) queue_id),);
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLCommandQueue
 * Method:    clEnqueueReadBuffer
 * Signature: (JJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_clEnqueueReadBuffer
(JNIEnv *env, jclass clazz, jlong queue_id, jlong buffer, jboolean blocking, jlong offset, jlong cb, jlong ptr, jlongArray array){
    OPENCL_PROLOGUE;
    
    cl_bool blocking_read = (blocking) ? CL_TRUE : CL_FALSE;
    jsize len = (array != NULL) ? (*env)->GetArrayLength(env, array) : 0;
    
    debug("queue: %p\n",(void *)queue_id);
    debug("cl_mem: %p\n",(void *)buffer);
    debug("blocking: %x\n",blocking_read);
    debug("offset: %lu\n",offset);
    debug("cb: %lu\n",cb);
    debug("ptr: %p\n", (void *) ptr);

    jlong *events = (array != NULL) ? (*env)->GetPrimitiveArrayCritical(env, array, NULL) : NULL;
    
    cl_event event;
    OPENCL_SOFT_ERROR("clEnqueueReadBuffer",
                      clEnqueueReadBuffer((cl_command_queue) queue_id, (cl_mem) buffer, blocking_read, (size_t) offset,(size_t) cb,(void *) ptr, (cl_uint) len,(const cl_event*) events,&event),-1);
    
    if(array != NULL)
        (*env)->ReleasePrimitiveArrayCritical(env, array, events, JNI_ABORT);
    
    return (jlong) event;
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLCommandQueue
 * Method:    clEnqueueWriteBuffer
 * Signature: (JJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_clEnqueueWriteBuffer
(JNIEnv *env, jclass clazz, jlong queue_id, jlong buffer, jboolean blocking, jlong offset, jlong cb, jlong ptr, jlongArray array){
    OPENCL_PROLOGUE;
    
    cl_bool blocking_write = (blocking) ? CL_TRUE : CL_FALSE;
    jsize len = (array != NULL) ? (*env)->GetArrayLength(env, array) : 0;
    
    jlong *events = (array != NULL) ? (*env)->GetPrimitiveArrayCritical(env, array, NULL) : NULL;

    cl_event event;
    OPENCL_SOFT_ERROR("clEnqueueWriteBuffer",
                      clEnqueueWriteBuffer((cl_command_queue) queue_id, (cl_mem) buffer, blocking_write, (size_t) offset,(size_t) cb,(void *) ptr, (cl_uint) len,(const cl_event*) events,&event),-1);
    
    if(array != NULL)
        (*env)->ReleasePrimitiveArrayCritical(env, array, events, JNI_ABORT);
    
    return (jlong) event;
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLCommandQueue
 * Method:    clEnqueueNDRangeKernel
 * Signature: (JJI[J[J[J[J)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_clEnqueueNDRangeKernel
(JNIEnv *env, jclass clazz, jlong queue_id, jlong kernel_id, jint work_dim, jlongArray array1, jlongArray array2, jlongArray array3, jlongArray array4){
    OPENCL_PROLOGUE;
    
    jsize numEvents =  (array4 != NULL) ? (*env)->GetArrayLength(env, array4) : 0;

    jlong *global_work_offset = (array1 != NULL) ? (*env)->GetPrimitiveArrayCritical(env, array1, NULL) : NULL;
    jlong *global_work_size = (*env)->GetPrimitiveArrayCritical(env, array2, NULL);
    jlong *local_work_size = (array3 != NULL) ? (*env)->GetPrimitiveArrayCritical(env, array3, NULL) : NULL;
    jlong *events = (array4 != NULL) ? (*env)->GetPrimitiveArrayCritical(env, array4, NULL) : NULL;
    
    
    cl_event event;
    OPENCL_SOFT_ERROR("clEnqueueNDRangeKernel",
                      clEnqueueNDRangeKernel((cl_command_queue) queue_id, (cl_kernel) kernel_id, (cl_uint) work_dim, (size_t*) global_work_offset, (size_t*) global_work_size, (size_t*) local_work_size, (cl_uint) numEvents, (cl_event*) events, &event),0);
    
    if(array4 != NULL)
            (*env)->ReleasePrimitiveArrayCritical(env, array4, events, JNI_ABORT);
    if(array3 != NULL)
    	(*env)->ReleasePrimitiveArrayCritical(env, array3, local_work_size, JNI_ABORT);

    if(array1 != NULL)
        	(*env)->ReleasePrimitiveArrayCritical(env, array1, global_work_offset, JNI_ABORT);
    
    (*env)->ReleasePrimitiveArrayCritical(env, array2, global_work_size, JNI_ABORT);


    return (jlong) event;
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLCommandQueue
 * Method:    clEnqueueTask
 * Signature: (JJ[J)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_clEnqueueTask
(JNIEnv *env, jclass clazz, jlong queue_id, jlong kernel_id, jlongArray array){
    OPENCL_PROLOGUE;

    jsize len = (array != NULL) ? (*env)->GetArrayLength(env, array) : 0;
    jlong *events = (array != NULL) ? (*env)->GetPrimitiveArrayCritical(env, array, NULL) : NULL;

    cl_event event;
    OPENCL_SOFT_ERROR("clEnqueueTask",
                      clEnqueueTask((cl_command_queue) queue_id, (cl_kernel) kernel_id, (size_t) len, (cl_event *) events, &event ),0);
    
    if(array != NULL)
        (*env)->ReleasePrimitiveArrayCritical(env, array, events, JNI_ABORT);

    return (jlong) event;
}

/*
 * Class:     tornado_drivers_opencl_OCLCommandQueue
 * Method:    clEnqueueMarker
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_clEnqueueMarker
  (JNIEnv *env, jclass clazz, jlong queue_id){
	OPENCL_PROLOGUE;

		    cl_event event;
		    OPENCL_SOFT_ERROR("clEnqueueMarker",
		    		clEnqueueMarker((cl_command_queue) queue_id, &event ),0);

	return (jlong) event;
}

/*
 * Class:     tornado_drivers_opencl_OCLCommandQueue
 * Method:    clEnqueueBarrier
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_clEnqueueBarrier
(JNIEnv *env, jclass clazz, jlong queue_id){
	OPENCL_PROLOGUE;

		    cl_event event;
		    OPENCL_SOFT_ERROR("clEnqueueBarrier",
		    		clEnqueueBarrier((cl_command_queue) queue_id),);

}

/*
 * Class:     tornado_drivers_opencl_OCLCommandQueue
 * Method:    clEnqueueWaitForEvents
 * Signature: (J[J)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_clEnqueueWaitForEvents
(JNIEnv *env, jclass clazz, jlong queue_id, jlongArray array){
	OPENCL_PROLOGUE;

	    jlong *events = (array != NULL) ? (*env)->GetPrimitiveArrayCritical(env, array, NULL) : NULL;
	    cl_uint len = (array != NULL) ? (*env)->GetArrayLength(env, array) : 0;

	    if (len > 0 && events != NULL)
	    	OPENCL_SOFT_ERROR("clEnqueueWaitForEvents",
	    		clEnqueueWaitForEvents((cl_command_queue) queue_id, len, (cl_event *) events),);

	    if(array != NULL)
	        (*env)->ReleasePrimitiveArrayCritical(env, array, events, 0);

}

/*
 * Class:     tornado_drivers_opencl_OCLCommandQueue
 * Method:    clEnqueueMarkerWithWaitList
 * Signature: (J[J)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_clEnqueueMarkerWithWaitList
  (JNIEnv *env, jclass clazz, jlong queue_id, jlongArray array){
	OPENCL_PROLOGUE;

	cl_uint len = (array != NULL) ? (*env)->GetArrayLength(env, array) : 0;
	jlong *events = (array != NULL) ? (*env)->GetPrimitiveArrayCritical(env, array, NULL) : NULL;


	cl_event event;
	OPENCL_SOFT_ERROR("clEnqueueMarkerWithWaitList",
			clEnqueueMarkerWithWaitList((cl_command_queue) queue_id, len, (cl_event *) events, &event ),0);

	if(array != NULL)
		(*env)->ReleasePrimitiveArrayCritical(env, array, events, JNI_ABORT);

	return (jlong) event;
}

/*
 * Class:     tornado_drivers_opencl_OCLCommandQueue
 * Method:    clEnqueueBarrierWithWaitList
 * Signature: (J[J)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLCommandQueue_clEnqueueBarrierWithWaitList
  (JNIEnv *env, jclass clazz, jlong queue_id, jlongArray array){
	OPENCL_PROLOGUE;

	cl_uint len = (array != NULL) ? (*env)->GetArrayLength(env, array) : 0;
	jlong *events = (array != NULL) ? (*env)->GetPrimitiveArrayCritical(env, array, NULL) : NULL;

	cl_event event;
	OPENCL_SOFT_ERROR("clEnqueueBarrierWithWaitList",
			clEnqueueBarrierWithWaitList((cl_command_queue) queue_id, len, (cl_event *) events, &event ),0);

	if(array != NULL)
		(*env)->ReleasePrimitiveArrayCritical(env, array, events, JNI_ABORT);

	return (jlong) event;
}
