/*
* MIT License
 *
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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

#ifdef __APPLE__
    #include <OpenCL/cl.h>
#else
    #include <CL/cl.h>
#endif

#include "OCLNativeCommandQueue.h"

#include <iostream>
#include <ostream>
#include <ocl_log.h>

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_natives_NativeCommandQueue
 * Method:    copyDevicePointer
 * Signature: (JJJI)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_natives_NativeCommandQueue_copyDevicePointer
(JNIEnv * env, jclass klass, jlong destDevicePointer, jlong srcDevicePointer, jlong offset, jint sizeDataType) {
    auto src = reinterpret_cast<unsigned char *>(srcDevicePointer);
    auto address = src + (offset * sizeDataType);
    return reinterpret_cast<jlong>(address);
}


/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_natives_NativeCommandQueue
 * Method:    copyDevicePointerWithMapping
 * Signature: (JJJJIII)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_natives_NativeCommandQueue_copyDevicePointerWithMapping
  (JNIEnv * env, jclass klass, jlong commandQueuePtr, jlong destDevicePtr, jlong srcDevicePtr, jlong offset, jint sizeDataType, jint sizeA, jint sizeB) {

    // FIXME: this is just a PoC to check custom ranges (sub-ranges from the source array) in OpenCL.

    const auto commandQueue = reinterpret_cast<cl_command_queue >(commandQueuePtr);
    cl_int status;
    const int headerSize = 6;
    const auto mappedPtr1 = static_cast<float *>(clEnqueueMapBuffer(commandQueue,
        reinterpret_cast<cl_mem>(srcDevicePtr),
        CL_TRUE,
        CL_MAP_READ,
        0,
        (sizeA * sizeDataType) + (headerSize * sizeof(int)),
        0,
        nullptr,
        nullptr,
        &status));
    LOG_OCL_AND_VALIDATE("clEnqueueMapBuffer", status);
    const auto mappedPtr2 = static_cast<float *>(clEnqueueMapBuffer(commandQueue,
        reinterpret_cast<cl_mem>(destDevicePtr),
        CL_TRUE,
        CL_MAP_WRITE,
        0,
        (sizeB * sizeDataType) + (headerSize * sizeof(int)),
        0,
        nullptr,
        nullptr,
        &status));

    // Checking with skip TornadoVM/Segment header
    const float *p1 = mappedPtr1 + (offset + headerSize);
    float *p2 = mappedPtr2 + headerSize;
    for (int i = 0; i < sizeB; i++) {
        *p2 = *p1;
        p2++;
        p1++;
    }
    LOG_OCL_AND_VALIDATE("clEnqueueMapBuffer", status);
    status = clEnqueueUnmapMemObject(commandQueue,
        reinterpret_cast<cl_mem>(destDevicePtr),
        mappedPtr2,
        0,
        nullptr,
        nullptr);
    LOG_OCL_AND_VALIDATE("clEnqueueUnmapMemObject", status);
    return destDevicePtr;
}


