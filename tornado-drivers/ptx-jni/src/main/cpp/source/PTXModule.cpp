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
#include <unordered_map>
#include <vector>
#include <string>
#include <sstream>
#include "PTXModule.h"
#include "ptx_log.h"

static const std::unordered_map<std::string, CUjit_option> CUDAJITFlagsMap {
    {"CU_JIT_OPTIMIZATION_LEVEL", CU_JIT_OPTIMIZATION_LEVEL},
    {"CU_JIT_TARGET",             CU_JIT_TARGET},
    {"CU_JIT_MAX_REGISTERS",  CU_JIT_MAX_REGISTERS},
    {"CU_JIT_THREADS_PER_BLOCK",         CU_JIT_THREADS_PER_BLOCK},
    {"CU_JIT_MIN_CTA_PER_SM",    CU_JIT_MIN_CTA_PER_SM},
    {"CU_JIT_MAX_THREADS_PER_BLOCK",   CU_JIT_MAX_THREADS_PER_BLOCK},
    {"CU_JIT_FALLBACK_STRATEGY",   CU_JIT_FALLBACK_STRATEGY},
    {"CU_JIT_GENERATE_DEBUG_INFO",   CU_JIT_GENERATE_DEBUG_INFO},
    {"CU_JIT_LOG_VERBOSE",   CU_JIT_LOG_VERBOSE},
    {"CU_JIT_GENERATE_LINE_INFO",   CU_JIT_GENERATE_LINE_INFO},
    {"CU_JIT_CACHE_MODE",   CU_JIT_CACHE_MODE},
    {"CU_JIT_POSITION_INDEPENDENT_CODE",   CU_JIT_POSITION_INDEPENDENT_CODE},
    {"CU_JIT_OVERRIDE_DIRECTIVE_VALUES",   CU_JIT_OVERRIDE_DIRECTIVE_VALUES}
};//currently supported CUDA JIT options

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

    /// FIXME
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
 * Signature:
 */
JNIEXPORT jbyteArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXModule_cuModuleLoadDataEx
  (JNIEnv *env, jclass clazz, jbyteArray source, jstring compilerFlags) {
    CUresult result;

    size_t ptx_length = env->GetArrayLength(source);
#ifdef _WIN32
    char *ptx = new char[ptx_length + 1];
#else
    char ptx[ptx_length + 1];
#endif
    env->GetByteArrayRegion(source, 0, ptx_length, reinterpret_cast<jbyte *>(ptx));
    ptx[ptx_length] = 0; // Make sure string terminates with a 0

    const char* chars = env->GetStringUTFChars(compilerFlags, nullptr);
    std::string flags(chars);
    env->ReleaseStringUTFChars(compilerFlags, chars);

    std::istringstream iss(flags);
    std::vector<CUjit_option> options;
    std::vector<unsigned int> values;

    std::string token;
    std::vector<std::pair<std::string, std::string>> entries;
    while (iss >> token) {
        if (token.rfind("CU_JIT", 0) == 0) {
            std::string val;
            if (!(iss >> val)) {
                std::cerr << "Missing value for flag: " << token << "\n";
                break;
            }
            entries.emplace_back(token, val);
        } else {
            std::cerr << "Skipping unsupported CUDA JIT flag: " << token << "\n";
        }
    }
    options.reserve(entries.size());
    values.reserve(entries.size());

    for (const auto &p : entries) {
        const std::string &flagName = p.first;
        const std::string &valStr = p.second;

        auto it = CUDAJITFlagsMap.find(flagName);
        if (it == CUDAJITFlagsMap.end()) {
            std::cerr << "Unsupported CUDA JIT flag: " << flagName << "\n";
            continue;
        }

        options.push_back(it->second);

        unsigned int v = std::stoi(valStr);
        values.push_back(v);
    }

    void **jitOptVals = new void *[options.size()];
    for(int i = 0;i < options.size();i++){
        jitOptVals[i] = (void *)(size_t)values[i];
    }

    CUmodule module;
    result = cuModuleLoadDataEx(&module, ptx, options.size(),  options.data(), (void **)jitOptVals);

    LOG_PTX_AND_VALIDATE("cuModuleLoadDataEx", result);
#ifdef _WIN32
    delete[] ptx;
#endif
    /// FIXME
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