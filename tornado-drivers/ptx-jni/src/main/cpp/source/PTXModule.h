/*
 * MIT License
 *
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
 * The University of Manchester.
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
#include <cuda.h>
/* Header for class uk_ac_manchester_tornado_drivers_ptx_PTXModule */

#ifndef _Included_uk_ac_manchester_tornado_drivers_ptx_PTXModule
#define _Included_uk_ac_manchester_tornado_drivers_ptx_PTXModule
#ifdef __cplusplus
extern "C" {
#endif

void array_to_module(JNIEnv *env, CUmodule *module_ptr, jbyteArray javaWrapper);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXModule
 * Method:    cuModuleLoadData
 * Signature: ([B)[B
 */
JNIEXPORT jbyteArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXModule_cuModuleLoadData
        (JNIEnv *, jclass, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXModule
 * Method:    cuModuleLoadDataEx
 * Signature: ([BLjava/lang/String;)[B
 */
JNIEXPORT jbyteArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXModule_cuModuleLoadDataEx
        (JNIEnv *, jclass, jbyteArray, jstring);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXModule
 * Method:    cuModuleUnload
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXModule_cuModuleUnload
        (JNIEnv *, jclass, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXModule
 * Method:    cuOccupancyMaxPotentialBlockSize
 * Signature: ([BLjava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXModule_cuOccupancyMaxPotentialBlockSize
        (JNIEnv *, jclass, jbyteArray, jstring);

#ifdef __cplusplus
}
#endif
#endif