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

/*
 * Shared internal definitions for the CUDA-C JNI backend (tornado-cuda).
 *
 * The cloned Java layer (uk.ac.manchester.tornado.drivers.cuda.*) keeps the
 * OpenCL-style native ABI: opaque handles are passed back and forth as Java
 * longs, info is fetched into little-endian byte[] buffers, and the events
 * array uses the [count, e0, e1, ...] convention. This header maps that ABI
 * onto the CUDA Driver API + NVRTC by boxing CUDA primitives inside small
 * structs whose pointers are the opaque longs the Java side stores.
 */

#ifndef TORNADO_CUDA_JNI_H
#define TORNADO_CUDA_JNI_H

#include <cuda.h>
#include <nvrtc.h>
#include <iostream>
#include <vector>
#include <string>
#include <cstring>

#define LOG_CUDA 0

#define LOG_CUDA_AND_VALIDATE(name, result)                       \
    if (LOG_CUDA == 1) {                                          \
        std::cout << "[TornadoVM-CUDA-JNI] Calling : " << name    \
                  << " -> Status: " << result << std::endl;       \
    }                                                             \
    if (result != CUDA_SUCCESS) {                                 \
        const char *_err_str = nullptr;                          \
        cuGetErrorName((CUresult) result, &_err_str);            \
        std::cout << "[TornadoVM-CUDA-JNI] ERROR : " << name      \
                  << " -> Returned: " << result << " ("           \
                  << (_err_str ? _err_str : "?") << ")"           \
                  << std::endl;                                   \
    }

#define LOG_NVRTC_AND_VALIDATE(name, result)                      \
    if (LOG_CUDA == 1) {                                          \
        std::cout << "[TornadoVM-CUDA-NVRTC-JNI] Calling : "      \
                  << name << " -> Status: " << result             \
                  << std::endl;                                   \
    }                                                             \
    if (result != NVRTC_SUCCESS) {                                \
        std::cout << "[TornadoVM-CUDA-NVRTC-JNI] ERROR : "        \
                  << name << " -> Returned: "                     \
                  << nvrtcGetErrorString((nvrtcResult) result)    \
                  << std::endl;                                   \
    }

// Variant without the unconditional ERROR print, for calls whose failure may
// be handled by a retry; the caller is responsible for reporting terminal
// failures (with the full NVRTC build log) itself.
#define LOG_NVRTC_CALL(name, result)                              \
    if (LOG_CUDA == 1) {                                          \
        std::cout << "[TornadoVM-CUDA-NVRTC-JNI] Calling : "      \
                  << name << " -> Status: " << result             \
                  << std::endl;                                   \
    }

/*
 * One physical platform is modelled. The OpenCL clone enumerates one platform
 * (CUDA) and then asks it for its devices.
 */
#define TORNADO_CUDA_PLATFORM_HANDLE ((jlong) 0x1)

/* Opaque handle: maps the OpenCL cl_device_id long to a CUdevice ordinal. */
typedef struct cuda_device_s {
    CUdevice device;
    int ordinal;
} cuda_device_t;

/* Opaque handle: maps the OpenCL cl_context long. */
typedef struct cuda_context_s {
    CUcontext context;
    CUdevice device;
    int ordinal;
} cuda_context_t;

/* Opaque handle: maps the OpenCL cl_command_queue long to a CUstream. */
typedef struct cuda_queue_s {
    CUstream stream;
    CUcontext context;
    CUdevice device;
    long properties;
} cuda_queue_t;

/* Opaque handle: maps the OpenCL cl_program long. Source is CUDA C compiled by NVRTC. */
typedef struct cuda_program_s {
    CUcontext context;
    std::string source;
    std::string binary;     // loadable module image for cuModuleLoadDataEx: a cubin
                            // produced by NVRTC, or raw PTX via createProgramWithBinary
    std::string log;        // NVRTC build log
    int build_status;       // CL_BUILD_* code (0 = success, -2 = error, -1 = none)
    CUmodule module;
    bool module_loaded;
} cuda_program_t;

/* Opaque handle: maps the OpenCL cl_kernel long to a CUfunction + packed args. */
typedef struct cuda_kernel_s {
    CUfunction function;
    CUmodule module;
    std::string name;
    // Argument storage. Each arg is a contiguous byte blob; arg_ptrs[i] points
    // to arg_data[i] so it can be handed to cuLaunchKernel(kernelParams).
    std::vector<std::vector<char>> arg_data;
} cuda_kernel_t;

/* Opaque handle: maps the OpenCL cl_event long to a pair of CUevents.
 * `event` is the completion event (used for wait/sync/query/dependency).
 * `start` is an optional timestamp recorded BEFORE the operation so that
 * cuEventElapsedTime(start, event) yields the operation's device time.
 * `start` is nullptr for events that do not bracket a timed operation
 * (e.g. markers/barriers), in which case the elapsed time is reported as 0. */
typedef struct cuda_event_s {
    CUevent event;
    CUevent start;
} cuda_event_t;

/*
 * Raises CUDAException on the Java side for a failed driver call, and returns whether it
 * did so. The Java command-queue wrappers already catch CUDAException and convert it into
 * a TornadoBailoutRuntimeException; without this, a failing call is only printed and the
 * caller carries on with buffers the device never wrote.
 *
 * Must NOT be called while a GetPrimitiveArrayCritical region is held: ThrowNew allocates.
 */
static inline bool tornado_throw_cuda_exception(JNIEnv *env, const char *call, CUresult result) {
    // An exception already pending is the first failure of the operation, and it is the useful
    // one; ThrowNew on top of it is undefined. Report that the caller must unwind either way.
    if (env->ExceptionCheck() == JNI_TRUE) {
        return true;
    }
    if (result == CUDA_SUCCESS) {
        return false;
    }
    const char *errorName = nullptr;
    const char *errorText = nullptr;
    cuGetErrorName(result, &errorName);
    cuGetErrorString(result, &errorText);

    std::string message = std::string(call) + " failed: " + (errorName != nullptr ? errorName : "UNKNOWN") + " (" + std::to_string((int) result) + ")";
    if (errorText != nullptr) {
        message += " - ";
        message += errorText;
    }
    jclass exceptionClass = env->FindClass("uk/ac/manchester/tornado/drivers/cuda/exceptions/CUDAException");
    if (exceptionClass != nullptr) {
        env->ThrowNew(exceptionClass, message.c_str());
        env->DeleteLocalRef(exceptionClass);
    }
    // Callers use the answer to decide whether to abandon their result, so report what actually
    // happened rather than what was attempted: a failed FindClass/ThrowNew must not make a caller
    // return an empty handle with no exception to explain it.
    return env->ExceptionCheck() == JNI_TRUE;
}

/*
 * Destroys a boxed event whose handle is about to be dropped. A JNI method that returns with an
 * exception pending has its return value discarded by the JVM, so an event handed back on that path
 * never reaches the Java side that would release it - the CUevents behind it would leak.
 */
static inline void tornado_discard_event(jlong event_handle) {
    cuda_event_t *ev = (cuda_event_t *) event_handle;
    if (ev == nullptr) {
        return;
    }
    if (ev->start != nullptr) {
        cuEventDestroy(ev->start);
    }
    if (ev->event != nullptr) {
        cuEventDestroy(ev->event);
    }
    delete ev;
}

/*
 * Checked driver calls. LOG_CUDA_AND_VALIDATE only prints, which is how a failed call used to
 * reach Java as a normal return with buffers the device never wrote. These log and then raise
 * CUDAException, so the failure arrives where the caller can see it.
 *
 * Use the plain form in a void JNI method and the _RET form elsewhere, passing the value the
 * method must return once the exception is pending (the return value is ignored by the JVM, but
 * the C++ signature still needs one).
 *
 * Neither may be used while a GetPrimitiveArrayCritical region is held: ThrowNew allocates.
 * Cleanup paths (destroy, free, unregister, unload) deliberately keep LOG_CUDA_AND_VALIDATE:
 * they run while unwinding, often with an exception already pending, and failing them louder
 * helps nobody.
 */
/*
 * Logs a failed driver call and raises CUDAException, returning whether it did. Use it where the
 * JNI signature has no status to hand back — a discarded CUresult is how a failure becomes a wrong
 * answer instead of an error.
 *
 * Calls that already return their CUresult to Java (the CUDA-graph family, createBuffer,
 * cuMemHostRegister) keep doing that: one route per call, never two. Cleanup paths (destroy, free,
 * unregister, unload) and device enumeration keep LOG_CUDA_AND_VALIDATE deliberately - the first
 * run while unwinding, the second must degrade to "no devices" rather than abort discovery.
 */
static inline bool tornado_report_cuda_failure(JNIEnv *env, const char *call, CUresult result) {
    LOG_CUDA_AND_VALIDATE(call, result);
    return tornado_throw_cuda_exception(env, call, result);
}

#define TORNADO_CHECK_CUDA(env, name, result)                     \
    do {                                                          \
        LOG_CUDA_AND_VALIDATE(name, result);                      \
        if (tornado_throw_cuda_exception(env, name, (CUresult) (result))) { \
            return;                                               \
        }                                                         \
    } while (0)

#define TORNADO_CHECK_CUDA_RET(env, name, result, retval)         \
    do {                                                          \
        LOG_CUDA_AND_VALIDATE(name, result);                      \
        if (tornado_throw_cuda_exception(env, name, (CUresult) (result))) { \
            return retval;                                        \
        }                                                         \
    } while (0)

#endif // TORNADO_CUDA_JNI_H
