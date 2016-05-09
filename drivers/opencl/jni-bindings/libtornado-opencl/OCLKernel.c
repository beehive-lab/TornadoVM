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
 * Class:     jacc_runtime_drivers_opencl_OCLKernel
 * Method:    clReleaseKernel
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLKernel_clReleaseKernel
(JNIEnv *env, jclass clazz, jlong kernel_id){
    OPENCL_PROLOGUE;
    OPENCL_SOFT_ERROR("clReleaseKernel",clReleaseKernel((cl_kernel)kernel_id),);
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLKernel
 * Method:    clSetKernelArg
 * Signature: (JIJ[B)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLKernel_clSetKernelArg
(JNIEnv *env, jclass clazz, jlong kernel_id, jint index, jlong size, jbyteArray array){
    OPENCL_PROLOGUE;
    
    jbyte *value = (*env)->GetPrimitiveArrayCritical(env, array, NULL);
   
    OPENCL_SOFT_ERROR("clSetKernelArg",clSetKernelArg((cl_kernel) kernel_id,(cl_uint) index,(size_t) size, (void*) value),);
    
    (*env)->ReleasePrimitiveArrayCritical(env, array, value, 0);
}

/*
 * Class:     tornado_drivers_opencl_OCLKernel
 * Method:    clGetKernelInfo
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLKernel_clGetKernelInfo
  (JNIEnv *env, jclass clazz, jlong kernel_id, jint kernel_info, jbyteArray array){
	 OPENCL_PROLOGUE;

	    jbyte *value;
	    jsize len;

	    value = (*env)->GetPrimitiveArrayCritical(env, array, NULL);
	    len = (*env)->GetArrayLength(env, array);

	    size_t return_size = 0;
	    OPENCL_SOFT_ERROR("clGetKernelInfo",
	    		clGetKernelInfo((cl_kernel) kernel_id,(cl_kernel_info) kernel_info,len,(void *)value,&return_size),);

	    (*env)->ReleasePrimitiveArrayCritical(env, array, value, 0);
}
