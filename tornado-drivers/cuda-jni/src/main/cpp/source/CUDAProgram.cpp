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

// One-time NVRTC header-resolution mode. Whether NVRTC can resolve
// #include <cuda_fp16.h> on its own, needs an explicit toolkit include path, or
// cannot resolve it at all is a fixed property of the (NVRTC, toolkit) pair on
// this machine — not of any individual kernel. It is therefore discovered once
// (by the first FP16 kernel) and reused, so later FP16 kernels compile in a
// single attempt instead of repeating the failed built-in probe. Kernels with
// no #include never consult it. See compile_with_nvrtc.
enum class Fp16IncludeMode { UNKNOWN, NEEDS_INCLUDE_PATHS, UNRESOLVABLE };
static std::mutex g_fp16_mode_mtx;
static Fp16IncludeMode g_fp16_mode = Fp16IncludeMode::UNKNOWN;
static std::vector<std::string> g_fp16_include_opts; // "--include-path=..." dirs, once resolved

// One-time latch for the working-but-suboptimal PTX-JIT fallback warning (GPU
// arch newer than the toolkit). The fallback runs correctly, so this is a
// warning, not an error; emit it once per process rather than once per kernel.
static std::once_flag g_arch_fallback_warn_once;

extern "C" {

/* OpenCL cl_program_build_info / cl_program_info values used by the Java clone. */
#define CL_PROGRAM_BUILD_STATUS 0x1181
#define CL_PROGRAM_BUILD_LOG    0x1183
#define CL_PROGRAM_NUM_DEVICES  0x1162
#define CL_PROGRAM_BINARY_SIZES 0x1165

/* CL_BUILD_* status codes (see cloned CUDABuildStatus enum). */
#define CL_BUILD_SUCCESS 0
#define CL_BUILD_ERROR (-2)

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

// Explanatory root cause + remediation for an unresolved <cuda_fp16.h>. The raw
// NVRTC diagnostic is only "could not open source file", which says neither why
// nor how to fix it; this spells out that the toolkit/host is missing the CUDA
// headers the FP16 kernels need.
static std::string fp16_unresolvable_message() {
    return "NVRTC cannot resolve <cuda_fp16.h>: this NVRTC provides no built-in CUDA headers "
           "and no CUDA toolkit include directory containing cuda_fp16.h was found (checked "
           "$CUDA_PATH/$CUDA_HOME/$CUDA_ROOT/include, /usr/local/cuda/include, /usr/include). "
           "Install a CUDA toolkit or set CUDA_PATH to one so the CUDA backend can compile FP16 kernels.";
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
    // Hoisted to function scope so the module-load failure branch can explain an
    // NVRTC/GPU-arch mismatch. Defaults suit the pre-built-binary path below,
    // which loads a cubin directly (no PTX-JIT fallback).
    int gpuArchNum = 0;        // device compute capability, e.g. cc 12.0 -> 120
    int maxSupportedArch = 0;  // highest arch this NVRTC can emit
    int fallbackArch = 0;      // highest supported arch <= GPU (0 if none)
    bool useCubin = true;
    bool archKnown = false;    // arch vars populated (false for pre-supplied images)
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
        gpuArchNum = major * 10 + minor;              // e.g. cc 12.0 -> 120, 9.0 -> 90
        bool gpuArchSupported = false;
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
                    if (a > maxSupportedArch) {
                        maxSupportedArch = a;
                    }
                }
            }
        }

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
        archKnown = true; // gpuArchNum/maxSupportedArch/fallbackArch/useCubin now describe this compile

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
        // and it is a fixed property of the (NVRTC, toolkit) pair rather than of
        // any one kernel. So the first FP16 kernel probes it (built-in first,
        // then the toolkit include dirs) and memoizes the outcome in g_fp16_mode;
        // later FP16 kernels read the memo and compile in a single attempt. Only
        // kernels that actually #include a header consult this at all.
        const bool needsHeader = program->source.find("#include") != std::string::npos;

        Fp16IncludeMode mode;
        std::vector<std::string> includeOpts; // storage must outlive `options`
        {
            std::lock_guard<std::mutex> lock(g_fp16_mode_mtx);
            mode = g_fp16_mode;
            if (needsHeader && mode == Fp16IncludeMode::NEEDS_INCLUDE_PATHS) {
                includeOpts = g_fp16_include_opts;
            }
        }

        // A previous probe already proved the header cannot be resolved on this
        // host: skip the doomed compile and report the explanatory failure.
        if (needsHeader && mode == Fp16IncludeMode::UNRESOLVABLE) {
            program->build_status = CL_BUILD_ERROR;
            program->log = fp16_unresolvable_message();
            std::cout << "[TornadoVM-CUDA-JNI] " << program->log << std::endl;
            nvrtcDestroyProgram(&prog);
            return;
        }

        std::vector<const char *> options;
        options.push_back(arch.c_str());
        for (const std::string &opt : includeOpts) {
            options.push_back(opt.c_str());
        }
        // A failure here may be recovered by the missing-header probe below,
        // so don't print an ERROR for it; terminal failures are reported after
        // the probe with the full NVRTC build log.
        nv = nvrtcCompileProgram(prog, (int) options.size(), options.data());
        LOG_NVRTC_CALL("nvrtcCompileProgram", nv);
        capture_nvrtc_log(prog, program);

        // First FP16 kernel whose built-in header resolution failed: probe the
        // toolkit include dirs once, retry, then memoize what worked so no other
        // kernel in this process repeats the failed built-in attempt.
        if (needsHeader && nv != NVRTC_SUCCESS && mode == Fp16IncludeMode::UNKNOWN &&
                program->log.find("could not open source file") != std::string::npos) {
            for (const std::string &dir : find_cuda_header_include_dirs()) {
                includeOpts.push_back("--include-path=" + dir);
            }
            if (!includeOpts.empty()) {
                // A failed nvrtcProgram cannot be recompiled; rebuild it for the retry.
                nvrtcDestroyProgram(&prog);
                nv = nvrtcCreateProgram(&prog, program->source.c_str(), "tornado_kernel.cu", 0, nullptr, nullptr);
                LOG_NVRTC_AND_VALIDATE("nvrtcCreateProgram", nv);
                options.clear();
                options.push_back(arch.c_str());
                for (const std::string &opt : includeOpts) {
                    options.push_back(opt.c_str());
                }
                nv = nvrtcCompileProgram(prog, (int) options.size(), options.data());
                LOG_NVRTC_CALL("nvrtcCompileProgram", nv);
                capture_nvrtc_log(prog, program);
            }
            {
                std::lock_guard<std::mutex> lock(g_fp16_mode_mtx);
                if (nv == NVRTC_SUCCESS && !includeOpts.empty()) {
                    g_fp16_mode = Fp16IncludeMode::NEEDS_INCLUDE_PATHS;
                    g_fp16_include_opts = includeOpts;
                } else if (program->log.find("could not open source file") != std::string::npos) {
                    // Neither built-ins nor any toolkit include dir has the header.
                    g_fp16_mode = Fp16IncludeMode::UNRESOLVABLE;
                }
            }
        }

        if (nv != NVRTC_SUCCESS) {
            program->build_status = CL_BUILD_ERROR;
            // An unresolved cuda_fp16.h reports only "could not open source file";
            // prepend the root cause + remediation so the failure is actionable.
            if (program->log.find("could not open source file") != std::string::npos) {
                program->log = fp16_unresolvable_message() + "\n\nNVRTC log:\n" + program->log;
            }
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
        // A load failure on a freshly compiled image (archKnown) means the driver
        // rejected our output; which component is stale depends on the image kind.
        // The raw error is only INVALID_PTX/unsupported-version, so name the cause.
        if (archKnown && !useCubin) {
            // PTX fallback (toolkit older than GPU): the driver JIT'd compute_<fallback>
            // PTX and failed, which means the driver's ptxas is too old for this
            // toolkit's PTX ISA. Updating the driver is the direct fix; a newer
            // toolkit that natively supports the GPU also helps by skipping PTX JIT.
            program->log = std::string("Driver could not JIT compute_") +
                           std::to_string(fallbackArch > 0 ? fallbackArch : gpuArchNum) +
                           " PTX for this GPU (sm_" + std::to_string(gpuArchNum) +
                           "): the GPU driver is too old for this toolkit's PTX ISA. Update the GPU "
                           "driver, or use a CUDA toolkit whose NVRTC natively supports sm_" +
                           std::to_string(gpuArchNum) + " (native cubin avoids PTX JIT entirely).\n\n" +
                           program->log;
        } else if (archKnown) {
            // Native cubin (toolkit knew the GPU arch) rejected at load: the driver
            // is older than the toolkit that produced the cubin. Update the driver,
            // or build with a toolkit matching the driver.
            program->log = std::string("Driver rejected the native sm_") + std::to_string(gpuArchNum) +
                           " cubin: the GPU driver is older than the CUDA toolkit that produced it. "
                           "Update the GPU driver, or use a CUDA toolkit matching the installed driver.\n\n" +
                           program->log;
        }
        std::cout << "[TornadoVM-CUDA-JNI] " << program->log << std::endl;
        program->module_loaded = false;
    } else {
        program->module_loaded = true;
        // Working, but suboptimal: the toolkit did not know the GPU's arch, so we
        // loaded compute_<fallback> PTX JIT'd by the (newer) driver. It runs, but
        // every load pays a driver JIT and codegen is capped at the compute_<fallback>
        // virtual ISA — no sm_<gpuArch>-native instructions (e.g. newer tensor-core
        // MMA shapes). Warn once so the degraded path is visible; native codegen
        // needs a newer toolkit, not a driver change (the driver is already ahead).
        if (archKnown && !useCubin) {
            std::call_once(g_arch_fallback_warn_once, [&]() {
                std::cout << "[TornadoVM-CUDA-JNI] WARNING: CUDA toolkit (NVRTC max sm_"
                          << maxSupportedArch << ") predates this GPU (sm_" << gpuArchNum
                          << "). Using compute_" << (fallbackArch > 0 ? fallbackArch : gpuArchNum)
                          << " PTX JIT'd by the driver - functional but not optimal (extra load-time "
                             "JIT; codegen limited to the compute_"
                          << (fallbackArch > 0 ? fallbackArch : gpuArchNum)
                          << " ISA). Upgrade the CUDA toolkit to one supporting sm_" << gpuArchNum
                          << " for native codegen." << std::endl;
            });
        }
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
