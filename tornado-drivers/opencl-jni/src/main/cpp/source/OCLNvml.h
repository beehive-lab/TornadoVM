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
/* Header for class uk_ac_manchester_tornado_drivers_opencl_OCLNvml */

#ifndef _Included_uk_ac_manchester_tornado_drivers_opencl_OCLNvml
#define _Included_uk_ac_manchester_tornado_drivers_opencl_OCLNvml
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLNvml
 * Method:    nvmlInit
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLNvml_nvmlInit
        (JNIEnv *, jclass);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLNvml
 * Method:    nvmlDeviceGetHandleByIndex
 * Signature: (J[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLNvml_nvmlDeviceGetHandleByIndex
        (JNIEnv *, jclass, jlong, jlongArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLNvml
 * Method:    nvmlDeviceGetPowerUsage
 * Signature: ([J[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLNvml_nvmlDeviceGetPowerUsage
        (JNIEnv *, jclass, jlongArray, jlongArray);


#ifdef __cplusplus
}
#endif
#endif
