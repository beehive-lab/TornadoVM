/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

#include <jni.h>
#include "cuda_jni.h"

extern "C" {

/* OpenCL cl_program_build_info / cl_program_info values used by the Java clone. */
#define CL_PROGRAM_BUILD_STATUS 0x1181
#define CL_PROGRAM_BUILD_LOG    0x1183
#define CL_PROGRAM_NUM_DEVICES  0x1162
#define CL_PROGRAM_BINARY_SIZES 0x1165

/* CL_BUILD_* status codes (see cloned CUDABuildStatus enum). */
#define CL_BUILD_SUCCESS 0
#define CL_BUILD_ERROR (-2)

/*
 * Compiles the stored CUDA C source to PTX using NVRTC, targeting the compute
 * capability of the program's device, then loads it as a CUmodule.
 */
static void compile_with_nvrtc(cuda_program_t *program, CUdevice device) {
    if (!program->ptx.empty()) {
        // Already have PTX (createProgramWithBinary path) - just load it below.
        program->build_status = CL_BUILD_SUCCESS;
    } else {
        int major = 0, minor = 0;
        cuDeviceGetAttribute(&major, CU_DEVICE_ATTRIBUTE_COMPUTE_CAPABILITY_MAJOR, device);
        cuDeviceGetAttribute(&minor, CU_DEVICE_ATTRIBUTE_COMPUTE_CAPABILITY_MINOR, device);
        std::string arch = "--gpu-architecture=compute_" + std::to_string(major) + std::to_string(minor);

        nvrtcProgram prog;
        nvrtcResult nv = nvrtcCreateProgram(&prog, program->source.c_str(), "tornado_kernel.cu", 0, nullptr, nullptr);
        LOG_NVRTC_AND_VALIDATE("nvrtcCreateProgram", nv);

        const char *options[] = { arch.c_str() };
        nv = nvrtcCompileProgram(prog, 1, options);
        LOG_NVRTC_AND_VALIDATE("nvrtcCompileProgram", nv);

        // Always capture the build log (errors and warnings).
        size_t log_size = 0;
        nvrtcGetProgramLogSize(prog, &log_size);
        if (log_size > 1) {
            std::vector<char> log(log_size);
            nvrtcGetProgramLog(prog, log.data());
            program->log.assign(log.data(), log_size);
        }

        if (nv != NVRTC_SUCCESS) {
            program->build_status = CL_BUILD_ERROR;
            std::cout << "[TornadoVM-CUDA-JNI] NVRTC compilation failed:\n" << program->log << std::endl;
            nvrtcDestroyProgram(&prog);
            return;
        }

        size_t ptx_size = 0;
        nvrtcGetPTXSize(prog, &ptx_size);
        program->ptx.resize(ptx_size);
        nvrtcGetPTX(prog, &program->ptx[0]);
        nvrtcDestroyProgram(&prog);
        program->build_status = CL_BUILD_SUCCESS;
    }

    // Load the PTX into a module via the Driver API.
    if (program->context != nullptr) {
        cuCtxSetCurrent(program->context);
    }
    CUresult result = cuModuleLoadDataEx(&program->module, program->ptx.c_str(), 0, nullptr, nullptr);
    LOG_CUDA_AND_VALIDATE("cuModuleLoadDataEx", result);
    if (result != CUDA_SUCCESS) {
        program->build_status = CL_BUILD_ERROR;
        if (program->log.empty()) {
            const char *err = nullptr;
            cuGetErrorString(result, &err);
            program->log = std::string("cuModuleLoadDataEx failed: ") + (err ? err : "unknown");
        }
        program->module_loaded = false;
    } else {
        program->module_loaded = true;
    }
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAProgram
 * Method:    clReleaseProgram
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAProgram_clReleaseProgram
        (JNIEnv *env, jclass clazz, jlong program_id) {
    cuda_program_t *program = (cuda_program_t *) program_id;
    if (program == nullptr) {
        return;
    }
    if (program->module_loaded) {
        CUresult result = cuModuleUnload(program->module);
        LOG_CUDA_AND_VALIDATE("cuModuleUnload", result);
    }
    delete program;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAProgram
 * Method:    clBuildProgram
 * Signature: (J[JLjava/lang/String;)V
 *
 * The OpenCL clone calls this to build for a set of devices. We compile the
 * CUDA C with NVRTC for the first device's architecture and load the module.
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAProgram_clBuildProgram
        (JNIEnv *env, jclass clazz, jlong program_id, jlongArray devices, jstring options) {
    cuda_program_t *program = (cuda_program_t *) program_id;
    if (program == nullptr) {
        return;
    }
    CUdevice device = 0;
    jsize numDevices = env->GetArrayLength(devices);
    if (numDevices > 0) {
        jlong first;
        env->GetLongArrayRegion(devices, 0, 1, &first);
        cuda_device_t *dev = (cuda_device_t *) first;
        if (dev != nullptr) {
            device = dev->device;
        }
    }
    compile_with_nvrtc(program, device);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAProgram
 * Method:    clGetProgramInfo
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAProgram_clGetProgramInfo
        (JNIEnv *env, jclass clazz, jlong program_id, jint param_name, jbyteArray array) {
    cuda_program_t *program = (cuda_program_t *) program_id;
    jbyte *buf = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(array, NULL));
    jsize len = env->GetArrayLength(array);
    std::memset(buf, 0, len);

    if (param_name == CL_PROGRAM_NUM_DEVICES) {
        int one = 1;
        if (len >= (jsize) sizeof(int)) {
            std::memcpy(buf, &one, sizeof(int));
        }
    } else if (param_name == CL_PROGRAM_BINARY_SIZES) {
        long long size = (program != nullptr) ? (long long) program->ptx.size() : 0;
        if (len >= (jsize) sizeof(long long)) {
            std::memcpy(buf, &size, sizeof(long long));
        }
    }
    env->ReleasePrimitiveArrayCritical(array, buf, 0);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAProgram
 * Method:    clGetProgramBuildInfo
 * Signature: (JJI[B)V
 *
 * Surfaces the NVRTC build status / log to the Java side.
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAProgram_clGetProgramBuildInfo
        (JNIEnv *env, jclass clazz, jlong program_id, jlong device_id, jint param_name, jbyteArray array) {
    cuda_program_t *program = (cuda_program_t *) program_id;
    jbyte *buf = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(array, NULL));
    jsize len = env->GetArrayLength(array);
    std::memset(buf, 0, len);

    if (program != nullptr) {
        if (param_name == CL_PROGRAM_BUILD_STATUS) {
            int status = program->build_status;
            if (len >= (jsize) sizeof(int)) {
                std::memcpy(buf, &status, sizeof(int));
            }
        } else if (param_name == CL_PROGRAM_BUILD_LOG) {
            jsize n = (jsize) program->log.size();
            if (n >= len) {
                n = len - 1;
            }
            if (n > 0) {
                std::memcpy(buf, program->log.c_str(), n);
            }
            if (len > n) {
                buf[n] = 0; // the Java getBuildLog substrings up to the first NUL
            }
        }
    }
    env->ReleasePrimitiveArrayCritical(array, buf, 0);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAProgram
 * Method:    clCreateKernel
 * Signature: (JLjava/lang/String;)J
 *
 * Resolves a CUfunction from the loaded module via cuModuleGetFunction.
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAProgram_clCreateKernel
        (JNIEnv *env, jclass clazz, jlong program_id, jstring name) {
    cuda_program_t *program = (cuda_program_t *) program_id;
    if (program == nullptr || !program->module_loaded) {
        return 0;
    }
    const char *kernel_name = env->GetStringUTFChars(name, NULL);

    cuda_kernel_t *kernel = new cuda_kernel_t();
    kernel->module = program->module;
    kernel->name = kernel_name;
    CUresult result = cuModuleGetFunction(&kernel->function, program->module, kernel_name);
    LOG_CUDA_AND_VALIDATE("cuModuleGetFunction", result);
    env->ReleaseStringUTFChars(name, kernel_name);

    if (result != CUDA_SUCCESS) {
        delete kernel;
        return 0;
    }
    return (jlong) kernel;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAProgram
 * Method:    getBinaries
 * Signature: (JJLjava/nio/ByteBuffer;)V
 *
 * Copies the compiled PTX into the provided direct ByteBuffer.
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAProgram_getBinaries
        (JNIEnv *env, jclass clazz, jlong program_id, jlong num_devices, jobject buffer) {
    cuda_program_t *program = (cuda_program_t *) program_id;
    if (program == nullptr) {
        return;
    }
    jbyte *dst = (jbyte *) env->GetDirectBufferAddress(buffer);
    jlong capacity = env->GetDirectBufferCapacity(buffer);
    jsize n = (jsize) program->ptx.size();
    if (n > capacity) {
        n = (jsize) capacity;
    }
    if (dst != nullptr && n > 0) {
        std::memcpy(dst, program->ptx.c_str(), n);
    }
}

} // extern "C"
