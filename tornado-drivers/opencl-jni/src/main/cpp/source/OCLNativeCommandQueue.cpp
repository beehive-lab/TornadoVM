/*
* MIT License
 *
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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

#include <iostream>

#include "OCLNativeCommandQueue.h"

// Copy device pointers
void copyDevicePointers(unsigned char **dst, unsigned char* src, long offset, int sizeOfType) {
    std::cout << "[jni] copyDevicePointers ... " << std::endl;
    *dst = src + (offset * sizeOfType);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_natives_NativeCommandQueue
 * Method:    copyDevicePointer
 * Signature: (JJJ)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_natives_NativeCommandQueue_copyDevicePointer
    (JNIEnv * env, jclass klass, jlong destDevicePointer, jlong srcDevicePointer, jlong offset) {
    unsigned char *src = reinterpret_cast<unsigned char *>(srcDevicePointer);
    unsigned char *dest = reinterpret_cast<unsigned char *>(destDevicePointer);
    copyDevicePointers(& dest, src , offset, 4);
    std::cout << "[jni] copyDevicePointers done" << std::endl;
    return -1;
}


