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
 * Class:     jacc_runtime_drivers_opencl_OCLPlatform
 * Method:    clGetPlatformInfo
 * Signature: (JI)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_tornado_drivers_opencl_OCLPlatform_clGetPlatformInfo
(JNIEnv *env, jclass clazz, jlong platform_id, jint platform_info){
    OPENCL_PROLOGUE;
    
    char value[512];
    
    OPENCL_SOFT_ERROR("clGetPlatformInfo",
                    clGetPlatformInfo((cl_platform_id) platform_id,(cl_platform_info) platform_info,sizeof(char)*512,value,NULL),0);
  
    return (*env)->NewStringUTF(env, value);
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLPlatform
 * Method:    clGetDeviceCount
 * Signature: (JJ)I
 */
JNIEXPORT jint JNICALL Java_tornado_drivers_opencl_OCLPlatform_clGetDeviceCount
(JNIEnv *env, jclass clazz, jlong platform_id, jlong device_type){
    OPENCL_PROLOGUE;
    cl_uint num_devices = 0;
    OPENCL_SOFT_ERROR("clGetDeviceIDs",
                    clGetDeviceIDs((cl_platform_id) platform_id, (cl_device_type) device_type,0,NULL,&num_devices),0);
    return (jint) num_devices;
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLPlatform
 * Method:    clGetDeviceIDs
 * Signature: (JJ[J)I
 */
JNIEXPORT jint JNICALL Java_tornado_drivers_opencl_OCLPlatform_clGetDeviceIDs
(JNIEnv *env, jclass clazz, jlong platform_id, jlong device_type, jlongArray array){
    OPENCL_PROLOGUE;
    
    jlong *devices;
    jsize len;
    
    devices = (*env)->GetPrimitiveArrayCritical(env, array, NULL);
    len = (*env)->GetArrayLength(env, array);
    
    cl_uint num_devices = 0;
    OPENCL_SOFT_ERROR("clGetDeviceIDs",
                    clGetDeviceIDs((cl_platform_id) platform_id, (cl_device_type) device_type,len, (cl_device_id*) devices,&num_devices),0);
    
    (*env)->ReleasePrimitiveArrayCritical(env, array, devices, 0);
    return (jint) num_devices;

}

void context_notify(const char *errinfo, const void *private_info, size_t cb, void * user_data){
    printf("tornado.drivers.opencl> notify error:\n");
    printf("tornado.drivers.opencl> %s\n",errinfo);
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLPlatform
 * Method:    clCreateContext
 * Signature: (J[J)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLPlatform_clCreateContext
(JNIEnv *env, jclass clazz, jlong platform_id, jlongArray array){
    OPENCL_PROLOGUE;
    
    jlong *devices;
    jsize len;
    cl_context context;
    
    cl_context_properties properties[] = {CL_CONTEXT_PLATFORM, platform_id, 0 };
    
    devices = (*env)->GetPrimitiveArrayCritical(env, array, NULL);
    len = (*env)->GetArrayLength(env, array);
    
    OPENCL_CHECK_ERROR("clCreateContext",
                      context = clCreateContext(properties,len,(cl_device_id*) devices,&context_notify,NULL,&error_id),0);

    
    (*env)->ReleasePrimitiveArrayCritical(env, array, devices, 0);
    return (jlong) context;
}
