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
    std::string ptx;        // produced by NVRTC
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

/* Opaque handle: maps the OpenCL cl_event long to a CUevent. */
typedef struct cuda_event_s {
    CUevent event;
} cuda_event_t;

#endif // TORNADO_CUDA_JNI_H
