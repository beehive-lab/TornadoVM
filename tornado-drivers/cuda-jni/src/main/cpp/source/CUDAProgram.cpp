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
#include <string>
#include <vector>
#include <cstdlib>
#include <fstream>
#include <unordered_map>
#include <mutex>
#include "cuda_jni.h"

// In-process NVRTC cubin cache. GPULlama (and TornadoVM task graphs in general)
// emit the *same* kernel source once per transformer layer, so the identical CUDA
// C is otherwise NVRTC-compiled dozens of times during warmup. Caching the
// compiled image keyed by (architecture + source) collapses those redundant
// compiles to one each. The key embeds the arch string, and the full source is
// the map key (not just a hash) so there is no collision risk.
static std::mutex g_cubin_cache_mtx;
static std::unordered_map<std::string, std::string> g_cubin_cache;

extern "C" {

/* OpenCL cl_program_build_info / cl_program_info values used by the Java clone. */
#define CL_PROGRAM_BUILD_STATUS 0x1181
#define CL_PROGRAM_BUILD_LOG    0x1183
#define CL_PROGRAM_NUM_DEVICES  0x1162
#define CL_PROGRAM_BINARY_SIZES 0x1165

/* CL_BUILD_* status codes (see cloned CUDABuildStatus enum). */
#define CL_BUILD_SUCCESS 0
#define CL_BUILD_ERROR (-2)

} // extern "C" -- the helpers below are internal (static) and need ordinary C++
  // linkage: MSVC rejects a C-linkage function returning a C++ class (C2526),
  // unlike GCC/Clang which tolerate it as an extension. Only the actual JNI
  // entry points below need C linkage, so they get their own extern "C" block.

// Locates directories that contain the toolkit's cuda_fp16.h, so an explicit
// NVRTC include path can be supplied on toolkits whose NVRTC cannot resolve
// the standard CUDA headers on its own (see comment at the call site).
// Candidate roots are probed in priority order and only those that actually
// hold the header are returned. Returns an empty vector if none exist.
static std::vector<std::string> find_cuda_header_include_dirs() {
    std::vector<std::string> candidates;
    for (const char *var : {"CUDA_PATH", "CUDA_HOME", "CUDA_ROOT"}) {
        const char *root = std::getenv(var);
        if (root && *root) {
            candidates.push_back(std::string(root) + "/include");
        }
    }
    candidates.push_back("/usr/local/cuda/include");
    candidates.push_back("/usr/include"); // distro-packaged toolkit (nvcc in /usr/bin)

    std::vector<std::string> found;
    for (const std::string &dir : candidates) {
        if (std::ifstream(dir + "/cuda_fp16.h").good()) {
            bool dup = false;
            for (const std::string &f : found) {
                if (f == dir) { dup = true; break; }
            }
            if (!dup) {
                found.push_back(dir);
            }
        }
    }
    return found;
}

// Copies the NVRTC build log (errors and warnings) of `prog` into the
// program's log buffer, replacing any log from a previous compile attempt.
static void capture_nvrtc_log(nvrtcProgram prog, cuda_program_t *program) {
    program->log.clear();
    size_t log_size = 0;
    nvrtcGetProgramLogSize(prog, &log_size);
    if (log_size > 1) {
        std::vector<char> log(log_size);
        nvrtcGetProgramLog(prog, log.data());
        program->log.assign(log.data(), log_size);
    }
}

/*
 * Compiles the stored CUDA C source to a cubin using NVRTC, targeting the
 * compute capability of the program's device, then loads it as a CUmodule.
 */
static void compile_with_nvrtc(cuda_program_t *program, CUdevice device) {
    if (!program->binary.empty()) {
        // Already have a module image (createProgramWithBinary path) - just load it below.
        program->build_status = CL_BUILD_SUCCESS;
    } else {
        int major = 0, minor = 0;
        cuDeviceGetAttribute(&major, CU_DEVICE_ATTRIBUTE_COMPUTE_CAPABILITY_MAJOR, device);
        cuDeviceGetAttribute(&minor, CU_DEVICE_ATTRIBUTE_COMPUTE_CAPABILITY_MINOR, device);
        // Choose the NVRTC target based on what *this* toolkit actually supports
        // (nvrtcGetSupportedArchs), which decides between two compilation modes:
        //
        //   * cubin (sm_XX): emit finished SASS for the device's real arch, loaded
        //     directly with no driver PTX-JIT. Requires the toolkit to know the GPU
        //     arch. Avoids the driver rejecting PTX whose ISA is newer than its JIT
        //     supports (e.g. a 13.1 toolkit's PTX 9.1 on a 13.0 driver).
        //
        //   * PTX (compute_XX): when the toolkit is OLDER than the GPU (e.g. a CUDA
        //     12.0 NVRTC on a Blackwell sm_120 device), it cannot emit sm_120 at all.
        //     Fall back to PTX for the newest virtual arch the toolkit knows and let
        //     the (newer) driver JIT it onto the real GPU — PTX forward compatibility.
        //
        // This makes the backend robust across the full (toolkit, driver, GPU) matrix
        // instead of assuming toolkit >= GPU.
        const int gpuArchNum = major * 10 + minor;   // e.g. cc 12.0 -> 120, 9.0 -> 90
        bool gpuArchSupported = false;
        int fallbackArch = 0;                         // highest supported arch <= GPU
        int numArchs = 0;
        if (nvrtcGetNumSupportedArchs(&numArchs) == NVRTC_SUCCESS && numArchs > 0) {
            std::vector<int> supported(numArchs);
            if (nvrtcGetSupportedArchs(supported.data()) == NVRTC_SUCCESS) {
                for (int a : supported) {
                    if (a == gpuArchNum) {
                        gpuArchSupported = true;
                    }
                    if (a <= gpuArchNum && a > fallbackArch) {
                        fallbackArch = a;
                    }
                }
            }
        }

        bool useCubin;
        std::string arch;
        if (gpuArchSupported) {
            arch = "--gpu-architecture=sm_" + std::to_string(gpuArchNum);
            useCubin = true;
        } else {
            // Toolkit does not know the GPU's real arch: emit forward-compatible PTX
            // for the best virtual arch it does know (or the GPU arch as a last resort
            // if the query failed), and let the driver JIT it.
            int target = fallbackArch > 0 ? fallbackArch : gpuArchNum;
            arch = "--gpu-architecture=compute_" + std::to_string(target);
            useCubin = false;
        }

        // Cache lookup: identical kernel source for the same architecture yields an
        // identical cubin, so reuse a previously compiled image instead of invoking
        // NVRTC again (the same kernel is emitted once per model layer).
        const std::string cacheKey = arch + "\n" + program->source;
        {
            std::lock_guard<std::mutex> lock(g_cubin_cache_mtx);
            auto it = g_cubin_cache.find(cacheKey);
            if (it != g_cubin_cache.end()) {
                program->binary = it->second;
                program->build_status = CL_BUILD_SUCCESS;
            }
        }
        if (program->binary.empty()) {

        nvrtcProgram prog;
        nvrtcResult nv = nvrtcCreateProgram(&prog, program->source.c_str(), "tornado_kernel.cu", 0, nullptr, nullptr);
        LOG_NVRTC_AND_VALIDATE("nvrtcCreateProgram", nv);

        // Header resolution for #include <cuda_fp16.h> et al. varies by toolkit:
        //
        //   * Some NVRTC builds (observed on 12.x) resolve the standard CUDA
        //     headers via RTC built-ins with an empty include list. For those,
        //     adding the on-disk include dir is actively harmful: their on-disk
        //     cuda_fp16.hpp guards <nv/target> with !defined(__CUDACC_RTC__),
        //     so NV_IF_ELSE_TARGET / NV_IS_DEVICE are undefined under NVRTC and
        //     produce 100+ errors.
        //
        //   * Others (11.x, 13.x) have no built-in cuda_fp16.h and fail with
        //     "could not open source file" unless the toolkit include dir is
        //     supplied; their on-disk headers are RTC-safe.
        //
        // A version gate cannot capture this reliably across the toolkit matrix,
        // so compile with NVRTC's own resolution first and, only if that fails
        // on a missing header, retry once with the toolkit include dirs.
        std::vector<std::string> includeOpts; // storage must outlive `options`
        std::vector<const char *> options;
        options.push_back(arch.c_str());
        nv = nvrtcCompileProgram(prog, (int) options.size(), options.data());
        // Deliberately no LOG_NVRTC_AND_VALIDATE here: on toolkits whose NVRTC has no
        // built-in cuda_fp16.h (see comment above), this first attempt is *expected*
        // to fail with "could not open source file" and silently succeed on the
        // include-path retry below. Logging an ERROR line for that handled, transient
        // failure just alarms users over nothing. A failure that survives the retry
        // (or isn't a missing-header failure at all) is still fully reported by the
        // "NVRTC compilation failed" dump below, with the complete compiler log.
        capture_nvrtc_log(prog, program);

        if (nv != NVRTC_SUCCESS && program->log.find("could not open source file") != std::string::npos) {
            for (const std::string &dir : find_cuda_header_include_dirs()) {
                includeOpts.push_back("--include-path=" + dir);
            }
            if (!includeOpts.empty()) {
                // A failed nvrtcProgram cannot be recompiled; rebuild it for the retry.
                nvrtcDestroyProgram(&prog);
                nv = nvrtcCreateProgram(&prog, program->source.c_str(), "tornado_kernel.cu", 0, nullptr, nullptr);
                LOG_NVRTC_AND_VALIDATE("nvrtcCreateProgram", nv);
                for (const std::string &opt : includeOpts) {
                    options.push_back(opt.c_str());
                }
                nv = nvrtcCompileProgram(prog, (int) options.size(), options.data());
                LOG_NVRTC_AND_VALIDATE("nvrtcCompileProgram", nv);
                capture_nvrtc_log(prog, program);
            }
        }

        if (nv != NVRTC_SUCCESS) {
            program->build_status = CL_BUILD_ERROR;
            std::cout << "[TornadoVM-CUDA-JNI] NVRTC compilation failed:\n" << program->log << std::endl;
            nvrtcDestroyProgram(&prog);
            return;
        }

        // Retrieve the compiled image. cuModuleLoadDataEx detects the format from its
        // header, so the load path below handles a cubin and raw PTX identically.
        // cubin (sm_XX) loads directly; PTX (compute_XX) is JIT'd by the driver.
        size_t image_size = 0;
        if (useCubin) {
            nvrtcGetCUBINSize(prog, &image_size);
            program->binary.resize(image_size);
            nvrtcGetCUBIN(prog, &program->binary[0]);
        } else {
            nvrtcGetPTXSize(prog, &image_size);
            program->binary.resize(image_size);
            nvrtcGetPTX(prog, &program->binary[0]);
        }
        nvrtcDestroyProgram(&prog);
        program->build_status = CL_BUILD_SUCCESS;

        // Store the freshly compiled image for reuse by identical sources.
        {
            std::lock_guard<std::mutex> lock(g_cubin_cache_mtx);
            g_cubin_cache.emplace(cacheKey, program->binary);
        }
        } // end cache-miss compile
    }

    // Load the module image into a module via the Driver API.
    if (program->context != nullptr) {
        cuCtxSetCurrent(program->context);
    }
    // Capture the driver's JIT error log so that an invalid image reports a
    // diagnostic instead of an opaque CUDA_ERROR_INVALID_PTX / unsupported-version error.
    char jitErrorLog[8192] = {0};
    CUjit_option jitOptions[] = {CU_JIT_ERROR_LOG_BUFFER, CU_JIT_ERROR_LOG_BUFFER_SIZE_BYTES};
    void *jitOptionValues[] = {(void *) jitErrorLog, (void *) (uintptr_t) sizeof(jitErrorLog)};
    CUresult result = cuModuleLoadDataEx(&program->module, program->binary.c_str(), 2, jitOptions, jitOptionValues);
    LOG_CUDA_AND_VALIDATE("cuModuleLoadDataEx", result);
    if (result != CUDA_SUCCESS) {
        program->build_status = CL_BUILD_ERROR;
        const char *err = nullptr;
        cuGetErrorString(result, &err);
        program->log = std::string("cuModuleLoadDataEx failed: ") + (err ? err : "unknown");
        if (jitErrorLog[0] != '\0') {
            program->log += std::string("\nJIT log:\n") + jitErrorLog;
        }
        std::cout << "[TornadoVM-CUDA-JNI] " << program->log << std::endl;
        program->module_loaded = false;
    } else {
        program->module_loaded = true;
    }
}

extern "C" {

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
        long long size = (program != nullptr) ? (long long) program->binary.size() : 0;
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
 * Copies the compiled module image (cubin or PTX) into the provided direct ByteBuffer.
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAProgram_getBinaries
        (JNIEnv *env, jclass clazz, jlong program_id, jlong num_devices, jobject buffer) {
    cuda_program_t *program = (cuda_program_t *) program_id;
    if (program == nullptr) {
        return;
    }
    jbyte *dst = (jbyte *) env->GetDirectBufferAddress(buffer);
    jlong capacity = env->GetDirectBufferCapacity(buffer);
    jsize n = (jsize) program->binary.size();
    if (n > capacity) {
        n = (jsize) capacity;
    }
    if (dst != nullptr && n > 0) {
        std::memcpy(dst, program->binary.c_str(), n);
    }
}

} // extern "C"
