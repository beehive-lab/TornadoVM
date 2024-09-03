/*
 * MIT License
 *
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
#include <iostream>

#ifdef __APPLE__
    #include <OpenCL/cl.h>
#else
    #include <CL/cl.h>
#endif

#include "SPIRVOCLDispatcher.h"
#include "ocl_log.h"

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_ocl_SPIRVOCLNativeDispatcher
 * Method:    clCreateProgramWithIL_native
 * Signature: (J[B[J[I)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_ocl_SPIRVOCLNativeDispatcher_clCreateProgramWithIL_1native
        (JNIEnv * env, jobject object, jlong contextPointer, jbyteArray spirvBinary, jlongArray spirvArrayLength, jintArray errorCodeArray) {

    #ifdef __APPLE__
        return 0;
    #else
    cl_context context = reinterpret_cast<cl_context>(contextPointer);
    jbyte* spirv = env->GetByteArrayElements(spirvBinary, 0);
    jlong* length = env->GetLongArrayElements(spirvArrayLength, 0);
    cl_int status;
    cl_program programPointer = clCreateProgramWithIL(context, spirv , length[0], &status);
    LOG_OCL_AND_VALIDATE("clCreateProgramWithIL", status);

    jint* statusArray = static_cast<jint *>(malloc(sizeof(jint)));
    statusArray[0] = status;
    env->SetIntArrayRegion(errorCodeArray, 0, 1, statusArray);

    // Release all arrays
    env->ReleaseLongArrayElements(spirvArrayLength, length, 0);
    env->ReleaseByteArrayElements(spirvBinary, spirv, 0);
    return reinterpret_cast<jlong>(programPointer);
    #endif
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_ocl_SPIRVOCLNativeDispatcher
 * Method:    clBuildProgram_native
 * Signature: (JI[JLjava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_ocl_SPIRVOCLNativeDispatcher_clBuildProgram_1native
        (JNIEnv * env, jobject object, jlong programPointer, jint numDevices, jlongArray devicesArray, jstring optionsString) {
    jlong *devices = static_cast<jlong *>(env->GetPrimitiveArrayCritical(devicesArray, NULL));
    const char *options = env->GetStringUTFChars(optionsString, NULL);
    cl_int status = clBuildProgram((cl_program) programPointer, (cl_uint) numDevices, (cl_device_id*) devices, options, NULL, NULL);
    LOG_OCL_AND_VALIDATE("clBuildProgram", status);
    env->ReleasePrimitiveArrayCritical(devicesArray, devices, 0);
    env->ReleaseStringUTFChars(optionsString, options);
    return status;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_ocl_SPIRVOCLNativeDispatcher
 * Method:    clCreateKernel_native
 * Signature: (JLjava/lang/String;[I)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_ocl_SPIRVOCLNativeDispatcher_clCreateKernel_1native
        (JNIEnv * env, jobject object, jlong programPointer, jstring kernelName, jintArray errorCode) {
    const char *kernelNameC = env->GetStringUTFChars(kernelName, NULL);
    cl_int status;
    cl_kernel kernel = clCreateKernel((cl_program) programPointer,kernelNameC, &status);
    jint* statusArray = static_cast<jint *>(malloc(sizeof(jint)));
    statusArray[0] = status;
    env->SetIntArrayRegion(errorCode, 0, 1, statusArray);
    env->ReleaseStringUTFChars(kernelName, kernelNameC);
    return reinterpret_cast<jlong>(kernel);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_ocl_SPIRVOCLNativeDispatcher
 * Method:    clGetProgramBuildInfo_native
 * Signature: (JJ)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_ocl_SPIRVOCLNativeDispatcher_clGetProgramBuildInfo_1native
        (JNIEnv * env, jobject object, jlong programPointer, jlong devicePointer) {
    size_t log_size;
    clGetProgramBuildInfo((cl_program) programPointer,  (cl_device_id) devicePointer, CL_PROGRAM_BUILD_LOG, 0, NULL, &log_size);
    char* log = (char*) malloc(log_size);
    clGetProgramBuildInfo((cl_program) programPointer, (cl_device_id) devicePointer, CL_PROGRAM_BUILD_LOG, log_size, log, NULL);
    return env->NewStringUTF(log);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_ocl_SPIRVOCLNativeDispatcher
 * Method:    clSetKernelArg_native
 * Signature: (JIIJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_ocl_SPIRVOCLNativeDispatcher_clSetKernelArg_1native
        (JNIEnv * env, jobject object, jlong kernelPointer, jint argIndex, jint argSize, jlong argument) {
    cl_int status = clSetKernelArg((cl_kernel) kernelPointer, (cl_uint) argIndex, (size_t) argSize, (void*) &argument);
    LOG_OCL_AND_VALIDATE("clSetKernelArg", status);
    return (cl_int) status;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_ocl_SPIRVOCLNativeDispatcher
 * Method:    clEnqueueNDRangeKernel_native
 * Signature: (JJI[J[J[J[J[J)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_ocl_SPIRVOCLNativeDispatcher_clEnqueueNDRangeKernel_1native
        (JNIEnv * env, jobject object, jlong commandQueuePointer, jlong kernelPointer, jint dimensions, jlongArray globalOffsets, jlongArray globalWorkGroup, jlongArray localWorkGroup, jlongArray waitEvents, jlongArray kernelEventArray) {

    jlong *gwo = static_cast<jlong *>((globalOffsets != nullptr) ? env->GetPrimitiveArrayCritical(globalOffsets, nullptr) : nullptr);
    jlong *gws = static_cast<jlong *>((globalWorkGroup != nullptr) ? env->GetPrimitiveArrayCritical(globalWorkGroup, nullptr) : nullptr);
    jlong *lws = static_cast<jlong *>((localWorkGroup != nullptr) ? env->GetPrimitiveArrayCritical(localWorkGroup, nullptr) : nullptr);

    jlong *clWaitEvents = static_cast<jlong *>((waitEvents != nullptr) ? env->GetPrimitiveArrayCritical(waitEvents, nullptr) : nullptr);
    jsize numEvents = 0;
    if (waitEvents != nullptr) {
        numEvents = env->GetArrayLength(waitEvents);
    }

    cl_event kernelEvent;
    cl_int status = clEnqueueNDRangeKernel((cl_command_queue) commandQueuePointer,
                                           (cl_kernel) kernelPointer,
                                           (cl_uint) dimensions,
                                           (size_t*) gwo,
                                           (size_t*) gws,
                                           (size_t*) lws,
                                           numEvents,
                                           (cl_event*) clWaitEvents,
                                           &kernelEvent);
    LOG_OCL_AND_VALIDATE("clEnqueueNDRangeKernel", status);

    if (globalOffsets != nullptr) {
        env->ReleasePrimitiveArrayCritical(globalOffsets, gwo, JNI_ABORT);
    }
    if (globalWorkGroup != nullptr) {
        env->ReleasePrimitiveArrayCritical(globalWorkGroup, gws, JNI_ABORT);
    }
    if (localWorkGroup != nullptr) {
        env->ReleasePrimitiveArrayCritical(localWorkGroup, lws, JNI_ABORT);
    }

    if (kernelEventArray != nullptr) {
        jlong* kernelEventNative = static_cast<jlong *>(malloc(sizeof(jlong)));
        kernelEventNative[0] = reinterpret_cast<jlong>(kernelEvent);
        env->SetLongArrayRegion(kernelEventArray, 0, 1, kernelEventNative);
    }
    return status;
}
