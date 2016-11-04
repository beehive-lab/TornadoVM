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
 * Class:     jacc_runtime_drivers_opencl_OCLDevice
 * Method:    clGetDeviceInfo
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLDevice_clGetDeviceInfo
(JNIEnv *env, jclass clazz, jlong device_id, jint device_info, jbyteArray array){

    OPENCL_PROLOGUE;

    jbyte *value;
    jsize len;
    
    value = (*env)->GetPrimitiveArrayCritical(env, array, NULL);
    len = (*env)->GetArrayLength(env, array);
    debug("tornado.drivers.opencl> clGetDeviceInfo param=0x%x\n",device_info);
    size_t return_size = 0;
    OPENCL_SOFT_ERROR("clGetDeviceInfo",
                    clGetDeviceInfo((cl_device_id) device_id,(cl_device_info) device_info,len,(void *)value,&return_size),);
    
    (*env)->ReleasePrimitiveArrayCritical(env, array, value, 0);
}
