#include <jni.h>
#ifdef _OSX
#include <OpenCL/cl.h>
#else
#include <CL/cl.h>
#endif
#include <stdio.h>
#include "macros.h"
#include "utils.h"
#include <stdlib.h>
#include <string.h>

/*
 * Class:     jacc_runtime_drivers_opencl_OCLContext
 * Method:    clReleaseContext
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLContext_clReleaseContext
(JNIEnv *env, jclass clazz, jlong context_id){
    OPENCL_PROLOGUE;
    
    OPENCL_SOFT_ERROR("clReleaseContext",clReleaseContext((cl_context) context_id),);
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLContext
 * Method:    clGetContextInfo
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLContext_clGetContextInfo
(JNIEnv *env, jclass clazz, jlong context_id, jint param_name, jbyteArray array){
    OPENCL_PROLOGUE;
    
    jbyte *value;
    jsize len;
    
    value = (*env)->GetPrimitiveArrayCritical(env, array, NULL);
    len = (*env)->GetArrayLength(env, array);
    
    size_t return_size = 0;
    OPENCL_SOFT_ERROR("clGetContextInfo",
                      clGetContextInfo((cl_context) context_id,(cl_context_info) param_name,len,(void *)value,&return_size),);
    
    
    (*env)->ReleasePrimitiveArrayCritical(env, array, value, 0);
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLContext
 * Method:    clCreateCommandQueue
 * Signature: (JJJ)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLContext_clCreateCommandQueue
(JNIEnv *env, jclass clazz, jlong context_id, jlong device_id, jlong properties){
    OPENCL_PROLOGUE;

    cl_command_queue queue;
    OPENCL_CHECK_ERROR("clCreateCommandQueue",
                       queue = clCreateCommandQueue((cl_context) context_id, (cl_device_id) device_id, (cl_command_queue_properties) properties, &error_id),-1);
    
    return (jlong) queue;
}

/*
 * Class:     tornado_drivers_opencl_OCLContext
 * Method:    allocateOffHeapMemory
 * Signature: (J)JJ
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLContext_allocateOffHeapMemory
  (JNIEnv *env, jclass clazz, jlong size, jlong alignment){

	void *ptr;
	int rc = posix_memalign(&ptr, (size_t) alignment, (size_t) size);
	if(rc != 0){
		printf("posix_memalign: did not work!\n");
	}

	memset(ptr,0,(size_t) size);
	for(size_t i=0;i<(size_t) size/4;i++){
		((int *) ptr)[i] = i;
	}

	return (jlong) ptr;
}

/*
 * Class:     tornado_drivers_opencl_OCLContext
 * Method:    freeOffHeapMemory
 * Signature: (J)J
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLContext_freeOffHeapMemory
  (JNIEnv *env, jclass clazz, jlong address){
	free((void *) address);
}


/*
 * Class:     tornado_drivers_opencl_OCLContext
 * Method:    asByteBuffer
 * Signature: (J)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL Java_tornado_drivers_opencl_OCLContext_asByteBuffer
  (JNIEnv *env, jclass clazz, jlong address, jlong capacity){
	return (*env)->NewDirectByteBuffer(env, (void *) address, capacity);
}

/*
 * Class:     tornado_drivers_opencl_OCLContext
 * Method:    createBuffer
 * Signature: (JJJJ)Ltornado/drivers/opencl/OCLContext/OCLBufferResult;
 */
JNIEXPORT jobject JNICALL Java_tornado_drivers_opencl_OCLContext_createBuffer
 (JNIEnv *env, jclass clazz, jlong context_id, jlong flags, jlong size, jlong host_ptr){
    OPENCL_PROLOGUE;

    jclass resultClass = (*env)->FindClass(env, "tornado/drivers/opencl/OCLContext$OCLBufferResult");
    jmethodID constructorId = (*env)->GetMethodID(env, resultClass,"<init>","(JJI)V");

    cl_mem mem;
    OPENCL_CHECK_ERROR("clCreateBuffer",
                       mem = clCreateBuffer((cl_context) context_id, (cl_mem_flags) flags , (size_t) size, (void *) host_ptr,&error_id),NULL);

    return (*env)->NewObject(env,resultClass,constructorId,(jlong) mem, (jlong) host_ptr, (jint) error_id);
}



/*
 * Class:     jacc_runtime_drivers_opencl_OCLContext
 * Method:    createSubBuffer
 * Signature: (JJI[B)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLContext_createSubBuffer
(JNIEnv *env, jclass clazz, jlong buffer, jlong flags, jint buffer_create_type, jbyteArray array){
    OPENCL_PROLOGUE;
    
    jbyte *buffer_create_info = (*env)->GetPrimitiveArrayCritical(env, array, NULL);

    cl_mem mem;
    OPENCL_CHECK_ERROR("clCreateSubBuffer",mem = clCreateSubBuffer((cl_mem) buffer, (cl_mem_flags) flags, (cl_buffer_create_type) buffer_create_type, (void *) buffer_create_info, &error_id),0);
    
    (*env)->ReleasePrimitiveArrayCritical(env, array, buffer_create_info, 0);

    return (jlong) mem;
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLContext
 * Method:    clReleaseMemObject
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLContext_clReleaseMemObject
(JNIEnv *env, jclass clazz, jlong memobj){
    OPENCL_PROLOGUE;
    OPENCL_SOFT_ERROR("clReleaseMemObject",clReleaseMemObject((cl_mem) memobj),);
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLContext
 * Method:    clCreateProgramWithSource
 * Signature: (J[B[J)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLContext_clCreateProgramWithSource
(JNIEnv *env, jclass clazz, jlong context_id, jbyteArray array1, jlongArray array2){
    OPENCL_PROLOGUE;
    
    jbyte *source = (*env)->GetPrimitiveArrayCritical(env, array1, NULL);
    jlong *lengths = (*env)->GetPrimitiveArrayCritical(env, array2, NULL);
    jsize numLengths = (*env)->GetArrayLength(env, array2);
    
    cl_program program;
    OPENCL_CHECK_ERROR("clCreateProgramWithSource",program = clCreateProgramWithSource((cl_context) context_id, (cl_uint) numLengths, (const char **) &source, (size_t*) lengths,&error_id),-1);
    
    (*env)->ReleasePrimitiveArrayCritical(env, array1, source, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, array2, lengths, 0);
    
    return (jlong) program;
}
