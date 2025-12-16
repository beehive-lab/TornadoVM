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

#include <iostream>
#include <cuda_runtime_api.h>
#include <vector>
#include <string>
#include <sstream>
#include <cstdint>
#include <limits>
#include <unordered_map>
#include "PTXModule.h"
#include "ptx_log.h"

/*static const std::unordered_map<std::string, CUjit_option> CUDAJITFlagsMap {
    {"CU_JIT_OPTIMIZATION_LEVEL", CU_JIT_OPTIMIZATION_LEVEL},
    {"CU_JIT_MAX_REGISTERS",  CU_JIT_MAX_REGISTERS},
    {"CU_JIT_CACHE_MODE",   CU_JIT_CACHE_MODE},
    {"CU_JIT_GENERATE_DEBUG_INFO",         CU_JIT_GENERATE_DEBUG_INFO},
    {"CU_JIT_LOG_VERBOSE",             CU_JIT_LOG_VERBOSE},
    {"CU_JIT_GENERATE_LINE_INFO",             CU_JIT_GENERATE_LINE_INFO},
    {"CU_JIT_TARGET",             CU_JIT_TARGET}
};*///currently supported CUDA JIT options in TornadoVM, not all options are included due to backwards compatibility.

jbyteArray from_module(JNIEnv *env, CUmodule *module) {
    jbyteArray array = env->NewByteArray(sizeof(CUmodule));
    env->SetByteArrayRegion(array, 0, sizeof(CUmodule), static_cast<const jbyte *>((void *) module));
    return array;
}

void array_to_module(JNIEnv *env, CUmodule *module_ptr, jbyteArray javaWrapper) {
    env->GetByteArrayRegion(javaWrapper, 0, sizeof(CUmodule), static_cast<jbyte *>((void *) module_ptr));
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXModule
 * Method:    cuModuleLoadData
 * Signature: ([B)[B
 */
JNIEXPORT jbyteArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXModule_cuModuleLoadData
  (JNIEnv *env, jclass clazz, jbyteArray source) {
    CUresult result;

    size_t ptx_length = env->GetArrayLength(source);
#ifdef _WIN32
    char *ptx = new char[ptx_length + 1];
#else
    char ptx[ptx_length + 1];
#endif
    env->GetByteArrayRegion(source, 0, ptx_length, reinterpret_cast<jbyte *>(ptx));
    ptx[ptx_length] = 0; // Make sure string terminates with a 0

    CUmodule module;
    result = cuModuleLoadData(&module, ptx);
    LOG_PTX_AND_VALIDATE("cuModuleLoadData", result);
#ifdef _WIN32
    delete[] ptx;
#endif

    if (result != CUDA_SUCCESS) {
        printf("PTX to cubin JIT compilation failed! (%d)\n", result);
        fflush(stdout);
        jbyteArray error_array = env->NewByteArray(0);
        return error_array;
    }
    return from_module(env, &module);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXModule
 * Method:    cuModuleLoadDataEx
 * Signature: ([B[I[J)[B
 */
JNIEXPORT jbyteArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXModule_cuModuleLoadDataEx
  (JNIEnv *env, jclass clazz, jbyteArray source, jintArray jitOptions, jlongArray jitValues) {
    CUresult result;

    size_t ptx_length = env->GetArrayLength(source);
#ifdef _WIN32
    char *ptx = new char[ptx_length + 1];
#else
    char ptx[ptx_length + 1];
#endif
    env->GetByteArrayRegion(source, 0, ptx_length, reinterpret_cast<jbyte *>(ptx));
    ptx[ptx_length] = 0; // Make sure string terminates with a 0

    // --- CUDA JIT options ---
    jsize numOptions = env->GetArrayLength(jitOptions);
    jsize numValues  = env->GetArrayLength(jitValues);

    if (numOptions != numValues) {
        printf("JIT options and values length mismatch");
        jbyteArray error_array = env->NewByteArray(0);
        return error_array;
    }

    jint* options = env->GetIntArrayElements(jitOptions, nullptr);
    jlong* values = env->GetLongArrayElements(jitValues, nullptr);

    // CUDA expects void**
    std::vector<void*> jitOptVals(numOptions);
    for (jsize i = 0; i < numOptions; i++) {
        jitOptVals[i] = reinterpret_cast<void*>(
                static_cast<uintptr_t>(values[i]));
    }

    CUmodule module;
    result = cuModuleLoadDataEx(
            &module,
            ptx,
            numOptions,
            reinterpret_cast<CUjit_option*>(options),
            jitOptVals.data()
    );

    env->ReleaseIntArrayElements(jitOptions, options, JNI_ABORT);
    env->ReleaseLongArrayElements(jitValues, values, JNI_ABORT);

    LOG_PTX_AND_VALIDATE("cuModuleLoadDataEx", result);
#ifdef _WIN32
    delete[] ptx;
#endif

    if (result != CUDA_SUCCESS) {
        printf("PTX to cubin JIT compilation using cuModuleLoadDataEx failed! (%d)\n", result);
        fflush(stdout);
        jbyteArray error_array = env->NewByteArray(0);
        return error_array;
    }
    return from_module(env, &module);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXModule
 * Method:    cuModuleUnload
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXModule_cuModuleUnload
  (JNIEnv *env, jclass clazz, jbyteArray module_wrapper) {
    CUresult result;
    CUmodule module;
    array_to_module(env, &module, module_wrapper);

    result = cuModuleUnload(module);
    LOG_PTX_AND_VALIDATE("cuModuleUnload", result);
    return (jlong) result;
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

    const char *native_function_name = env->GetStringUTFChars(func_name, 0);
    CUfunction kernel;
    result = cuModuleGetFunction(&kernel, module, native_function_name);
    LOG_PTX_AND_VALIDATE("cuModuleGetFunction", result);
    env->ReleaseStringUTFChars(func_name, native_function_name);

    int min_grid_size;
    int block_size;
    result = cuOccupancyMaxPotentialBlockSize(&min_grid_size, &block_size, kernel, 0, 0, 0);
    LOG_PTX_AND_VALIDATE("cuOccupancyMaxPotentialBlockSize", result);
    return block_size;
}