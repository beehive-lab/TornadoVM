/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

#include <jni.h>
#include <cuda.h>
#include <stdio.h>

#include "PTXModule.h"
#include "macros.h"

jbyteArray from_module(JNIEnv *env, CUmodule *module) {
    jbyteArray array = (*env)->NewByteArray(env, sizeof(CUmodule));

    (*env)->SetByteArrayRegion(env, array, 0, sizeof(CUmodule), (void *) module);
    return array;
}

void array_to_module(JNIEnv *env, CUmodule *module_ptr, jbyteArray javaWrapper) {
    (*env)->GetByteArrayRegion(env, javaWrapper, 0, sizeof(CUmodule), (void *) module_ptr);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXModule
 * Method:    cuModuleLoadData
 * Signature: ([B)[B
 */
JNIEXPORT jbyteArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXModule_cuModuleLoadData
  (JNIEnv *env, jclass clazz, jbyteArray source) {
    CUresult result;

    size_t ptx_length = (*env)->GetArrayLength(env, source);
    char ptx[ptx_length + 1];
    (*env)->GetByteArrayRegion(env, source, 0, ptx_length, ptx);
    ptx[ptx_length] = 0; // Make sure string terminates with a 0

    CUmodule module;
    CUDA_CHECK_ERROR("cuModuleLoadData", cuModuleLoadData(&module, ptx));

    if (result != CUDA_SUCCESS) {
        printf("PTX to cubin JIT compilation failed! (%d)\n", result);
        fflush(stdout);
        jbyteArray error_array = (*env)->NewByteArray(env, 0);
        return error_array;
    }

    return from_module(env, &module);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXModule
 * Method:    cuOccupancyMaxPotentialBlockSize
 * Signature: ([BLjava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXModule_cuOccupancyMaxPotentialBlockSize
  (JNIEnv *env, jclass clazz, jbyteArray module_wrapper, jstring func_name) {
    CUresult result;
    CUmodule module;
    array_to_module(env, &module, module_wrapper);

    const char *native_function_name = (*env)->GetStringUTFChars(env, func_name, 0);
    CUfunction kernel;
    CUDA_CHECK_ERROR("cuModuleGetFunction", cuModuleGetFunction(&kernel, module, native_function_name));
    (*env)->ReleaseStringUTFChars(env, func_name, native_function_name);

    int min_grid_size;
    int block_size;
    CUDA_CHECK_ERROR("cuOccupancyMaxPotentialBlockSize", cuOccupancyMaxPotentialBlockSize (&min_grid_size, &block_size, kernel, 0, 0, 0));
    return block_size;
}