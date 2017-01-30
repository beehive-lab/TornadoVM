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
 * Class:     jacc_runtime_drivers_opencl_OCLProgram
 * Method:    clReleaseProgram
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLProgram_clReleaseProgram
(JNIEnv *env, jclass clazz, jlong program_id) {
    OPENCL_PROLOGUE;

    OPENCL_SOFT_ERROR("clReleaseProgram", clReleaseProgram((cl_program) program_id),);
}

void notify_compilation_error(cl_program program_id, void *user_data) {
    printf("OpenCL> compilation error: program_id = %p\n", program_id);
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLProgram
 * Method:    clBuildProgram
 * Signature: (J[J[C)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLProgram_clBuildProgram
(JNIEnv *env, jclass clazz, jlong program_id, jlongArray array1, jstring str) {
    OPENCL_PROLOGUE;

    jlong *devices = (*env)->GetPrimitiveArrayCritical(env, array1, NULL);
    jsize numDevices = (*env)->GetArrayLength(env, array1);

    const char *options = (*env)->GetStringUTFChars(env, str, NULL);

    // if pfn_notify callback is set, clBuildProgram will return immediately and compilation will be asynchronous
    // otherwise, it will behave synchronously
    OPENCL_SOFT_ERROR("clBuildProgarm", clBuildProgram((cl_program) program_id, (cl_uint) numDevices, (cl_device_id*) devices, options, NULL, NULL),);

    (*env)->ReleasePrimitiveArrayCritical(env, array1, devices, 0);
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLProgram
 * Method:    clGetProgramInfo
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLProgram_clGetProgramInfo
(JNIEnv *env, jclass clazz, jlong program_id, jint param_name, jbyteArray array) {
    OPENCL_PROLOGUE;

    jbyte *value = (*env)->GetPrimitiveArrayCritical(env, array, NULL);
    jsize len = (*env)->GetArrayLength(env, array);

    debug("size of cl_program_info: %lx\n", sizeof (cl_program_info));
    debug("param_name: %x\n", param_name);
    debug("len: %x\n", len);


    size_t return_size = 0;


    OPENCL_SOFT_ERROR("clGetProgramInfo",
            clGetProgramInfo((cl_program) program_id, (cl_program_info) param_name, len, (void *) value, &return_size),);

    debug("return size: %zx\n", return_size);

    (*env)->ReleasePrimitiveArrayCritical(env, array, value, 0);
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLProgram
 * Method:    clGetProgramBuildInfo
 * Signature: (JJI[B)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLProgram_clGetProgramBuildInfo
(JNIEnv *env, jclass clazz, jlong program_id, jlong device_id, jint param_name, jbyteArray array) {
    OPENCL_PROLOGUE;

    jbyte *value = (*env)->GetPrimitiveArrayCritical(env, array, NULL);
    jsize len = (*env)->GetArrayLength(env, array);

    size_t return_size = 0;
    OPENCL_SOFT_ERROR("clGetProgramBuildInfo",
            clGetProgramBuildInfo((cl_program) program_id, (cl_device_id) device_id, (cl_program_build_info) param_name, len, (void *) value, &return_size),);


    (*env)->ReleasePrimitiveArrayCritical(env, array, value, 0);
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLProgram
 * Method:    clCreateKernel
 * Signature: (JLjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLProgram_clCreateKernel
(JNIEnv *env, jclass clazz, jlong program_id, jstring str) {
    OPENCL_PROLOGUE;

    const char *kernel_name = (*env)->GetStringUTFChars(env, str, NULL);

    cl_kernel kernel;
    OPENCL_CHECK_ERROR("clCreateKernel", kernel = clCreateKernel((cl_program) program_id, kernel_name, &error_id), -1);

    return (jlong) kernel;
}

/*
 * Class:     tornado_drivers_opencl_OCLProgram
 * Method:    getBinaries
 * Signature: (JJ[B)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLProgram_getBinaries
(JNIEnv *env, jclass clazz, jlong program_id, jlong num_devices, jobject array) {
    OPENCL_PROLOGUE;

    jbyte *value = (jbyte *) (*env)->GetDirectBufferAddress(env, array);
    size_t return_size = 0;

    size_t *binarySizes = malloc(sizeof (size_t) * num_devices);

    OPENCL_SOFT_ERROR("clGetProgramInfo",
            clGetProgramInfo((cl_program) program_id, CL_PROGRAM_BINARY_SIZES, sizeof (size_t) * num_devices, binarySizes, &return_size),);

    unsigned char **binaries = malloc(sizeof (unsigned char *) * num_devices);
    binaries[0] = value;
    for (int i = 1; i < num_devices; i++) {
        binaries[i] = value + binarySizes[i - 1];
    }

    OPENCL_SOFT_ERROR("clGetProgramInfo",
            clGetProgramInfo((cl_program) program_id, CL_PROGRAM_BINARIES, sizeof (unsigned char**), (void *) binaries, &return_size),);

    free(binarySizes);
    free(binaries);
}