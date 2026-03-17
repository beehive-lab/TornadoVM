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
#include <nvrtc.h>

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

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXModule
 * Method:    nvrtcCompile
 * Signature: ([BLjava/lang/String;Ljava/lang/String;)[B
 *
 * Compiles CUDA C source to PTX using NVRTC. Returns PTX bytes on success,
 * or throws TornadoBailoutRuntimeException on failure with the compiler log.
 */
JNIEXPORT jbyteArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXModule_nvrtcCompile
  (JNIEnv *env, jclass clazz, jbyteArray source, jstring kernelName, jstring archOption) {

    // Get CUDA C source as null-terminated string
    jsize src_len = env->GetArrayLength(source);
    std::vector<char> src_buf(src_len + 1, 0);
    env->GetByteArrayRegion(source, 0, src_len, reinterpret_cast<jbyte*>(src_buf.data()));

    const char *kernel_name_str = env->GetStringUTFChars(kernelName, nullptr);
    const char *arch_option_str = env->GetStringUTFChars(archOption, nullptr);

    // Build options list
    std::vector<const char*> options;
    std::string arch_str(arch_option_str);
    if (!arch_str.empty()) {
        options.push_back(arch_option_str);
    }
    // Note: --use_fast_math is intentionally omitted here because it changes FMA
    // rounding behaviour and can produce results that diverge from the PTX JIT path.
    // The safe subset of fast-math flags: flush denormals to zero, disable precise
    // div/sqrt (matching cuModuleLoadDataEx JIT defaults for PTX).
    options.push_back("--ftz=true");
    options.push_back("--prec-div=false");
    options.push_back("--prec-sqrt=false");

    // Add CUDA include path so headers like <cuda_fp16.h> can be found.
    // Check CUDA_HOME, then CUDA_PATH, then fall back to the default install location.
    std::string cuda_include;
    const char *cuda_home = getenv("CUDA_HOME");
    if (!cuda_home) cuda_home = getenv("CUDA_PATH");
    if (cuda_home) {
        cuda_include = std::string("-I") + cuda_home + "/include";
    } else {
        cuda_include = "-I/usr/local/cuda/include";
    }
    options.push_back(cuda_include.c_str());

    // Create NVRTC program
    nvrtcProgram prog;
    nvrtcResult nvrtc_result = nvrtcCreateProgram(&prog, src_buf.data(), kernel_name_str,
                                                  0, nullptr, nullptr);
    env->ReleaseStringUTFChars(kernelName, kernel_name_str);

    if (nvrtc_result != NVRTC_SUCCESS) {
        env->ReleaseStringUTFChars(archOption, arch_option_str);
        std::string msg = std::string("nvrtcCreateProgram failed: ") + nvrtcGetErrorString(nvrtc_result);
        jclass exClass = env->FindClass("uk/ac/manchester/tornado/api/exceptions/TornadoBailoutRuntimeException");
        env->ThrowNew(exClass, msg.c_str());
        return nullptr;
    }

    // Compile
    nvrtc_result = nvrtcCompileProgram(prog, (int)options.size(), options.data());
    env->ReleaseStringUTFChars(archOption, arch_option_str);

    // Always retrieve the compilation log
    size_t log_size = 0;
    nvrtcGetProgramLogSize(prog, &log_size);
    std::string compile_log(log_size, '\0');
    if (log_size > 1) {
        nvrtcGetProgramLog(prog, &compile_log[0]);
    }

    if (nvrtc_result != NVRTC_SUCCESS) {
        nvrtcDestroyProgram(&prog);
        std::string msg = std::string("NVRTC compilation failed: ")
                          + nvrtcGetErrorString(nvrtc_result) + "\n" + compile_log;
        jclass exClass = env->FindClass("uk/ac/manchester/tornado/api/exceptions/TornadoBailoutRuntimeException");
        env->ThrowNew(exClass, msg.c_str());
        return nullptr;
    }

    if (log_size > 1 && !compile_log.empty() && compile_log[0] != '\0') {
        printf("[NVRTC] %s\n", compile_log.c_str());
        fflush(stdout);
    }

    // Retrieve PTX
    size_t ptx_size = 0;
    nvrtcGetPTXSize(prog, &ptx_size);
    std::vector<char> ptx_buf(ptx_size);
    nvrtcGetPTX(prog, ptx_buf.data());
    nvrtcDestroyProgram(&prog);

    // Return PTX as byte array (null-terminated string safe for cuModuleLoadDataEx)
    jbyteArray result_array = env->NewByteArray((jsize)ptx_size);
    env->SetByteArrayRegion(result_array, 0, (jsize)ptx_size,
                            reinterpret_cast<const jbyte*>(ptx_buf.data()));
    return result_array;
}