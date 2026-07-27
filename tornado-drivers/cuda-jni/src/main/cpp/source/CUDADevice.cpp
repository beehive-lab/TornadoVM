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

/*
 * OpenCL cl_device_info values queried by the cloned Java CUDADevice via the
 * CUDADeviceInfo enum. The Java side parses the little-endian byte[] back into
 * the matching primitive width, so the binary layout below must agree exactly.
 */
#define CL_DEVICE_TYPE                       0x1000
#define CL_DEVICE_VENDOR_ID                  0x1001
#define CL_DEVICE_MAX_COMPUTE_UNITS          0x1002
#define CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS   0x1003
#define CL_DEVICE_MAX_WORK_GROUP_SIZE        0x1004
#define CL_DEVICE_MAX_WORK_ITEM_SIZES        0x1005
#define CL_DEVICE_MAX_CLOCK_FREQUENCY        0x100C
#define CL_DEVICE_ADDRESS_BITS               0x100D
#define CL_DEVICE_MAX_MEM_ALLOC_SIZE         0x1010
#define CL_DEVICE_MEM_BASE_ADDR_ALIGN        0x1019
#define CL_DEVICE_SINGLE_FP_CONFIG           0x101B
#define CL_DEVICE_GLOBAL_MEM_SIZE            0x101F
#define CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE   0x1020
#define CL_DEVICE_LOCAL_MEM_TYPE             0x1022
#define CL_DEVICE_LOCAL_MEM_SIZE             0x1023
#define CL_DEVICE_AVAILABLE                  0x1027
#define CL_DEVICE_NAME                       0x102B
#define CL_DEVICE_VENDOR                     0x102C
#define CL_DRIVER_VERSION                    0x102D
#define CL_DEVICE_PROFILE                    0x102E
#define CL_DEVICE_VERSION                    0x102F
#define CL_DEVICE_EXTENSIONS                 0x1030
#define CL_DEVICE_DOUBLE_FP_CONFIG           0x1032
#define CL_DEVICE_HOST_UNIFIED_MEMORY        0x1035
#define CL_DEVICE_OPENCL_C_VERSION           0x103D
#define CL_DEVICE_ENDIAN_LITTLE              0x1026
#define CL_DEVICE_IL_VERSION                 0x105B

/*
 * TornadoVM-private info keys (not OpenCL): they map straight onto CUDA driver device
 * attributes that have no cl_device_info equivalent, so the Java CUDADevice can gate
 * intra-plan concurrency on the same hardware overlap capabilities as the PTX backend.
 */
#define TORNADO_DEVICE_ASYNC_ENGINE_COUNT    0x4100
#define TORNADO_DEVICE_CONCURRENT_KERNELS    0x4101

/* CL_DEVICE_TYPE_GPU == 1 << 2 (see cloned CUDADeviceType enum). */
#define CL_DEVICE_TYPE_GPU 4L

/* CL_LOCAL == 1 (see cloned CUDALocalMemType). */
#define CL_LOCAL 1

/* fp-config bit (CL_FP_FMA) used only as a non-zero "supported" marker. */
#define CL_FP_NONZERO 0x20

static void put_int(jbyte *buf, jsize len, int value) {
    if (len >= (jsize) sizeof(int)) {
        std::memcpy(buf, &value, sizeof(int));
    }
}

static void put_long(jbyte *buf, jsize len, long long value) {
    if (len >= (jsize) sizeof(long long)) {
        std::memcpy(buf, &value, sizeof(long long));
    }
}

static void put_string(jbyte *buf, jsize len, const std::string &value) {
    jsize n = (jsize) value.size();
    if (n >= len) {
        n = len - 1;
    }
    if (n > 0) {
        std::memcpy(buf, value.c_str(), n);
    }
    if (len > n) {
        buf[n] = 0;
    }
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDADevice
 * Method:    clGetDeviceInfo
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDADevice_clGetDeviceInfo
        (JNIEnv *env, jclass clazz, jlong device_id, jint device_info, jbyteArray array) {
    cuda_device_t *dev = (cuda_device_t *) device_id;
    if (dev == nullptr) {
        return;
    }
    CUdevice cuDevice = dev->device;

    jbyte *buf = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(array, NULL));
    jsize len = env->GetArrayLength(array);
    std::memset(buf, 0, len);

    int attr = 0;
    CUresult result;

    switch (device_info) {
        case CL_DEVICE_TYPE:
            put_long(buf, len, CL_DEVICE_TYPE_GPU);
            break;
        case CL_DEVICE_VENDOR_ID:
            put_int(buf, len, 0x10DE); // NVIDIA PCI vendor id
            break;
        case CL_DEVICE_MAX_COMPUTE_UNITS:
            result = cuDeviceGetAttribute(&attr, CU_DEVICE_ATTRIBUTE_MULTIPROCESSOR_COUNT, cuDevice);
            LOG_CUDA_AND_VALIDATE("cuDeviceGetAttribute(SM_COUNT)", result);
            put_int(buf, len, attr);
            break;
        case CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS:
            put_int(buf, len, 3);
            break;
        case CL_DEVICE_MAX_WORK_GROUP_SIZE:
            result = cuDeviceGetAttribute(&attr, CU_DEVICE_ATTRIBUTE_MAX_THREADS_PER_BLOCK, cuDevice);
            LOG_CUDA_AND_VALIDATE("cuDeviceGetAttribute(MAX_THREADS_PER_BLOCK)", result);
            put_int(buf, len, attr);
            break;
        case CL_DEVICE_MAX_WORK_ITEM_SIZES: {
            int x = 0, y = 0, z = 0;
            cuDeviceGetAttribute(&x, CU_DEVICE_ATTRIBUTE_MAX_BLOCK_DIM_X, cuDevice);
            cuDeviceGetAttribute(&y, CU_DEVICE_ATTRIBUTE_MAX_BLOCK_DIM_Y, cuDevice);
            cuDeviceGetAttribute(&z, CU_DEVICE_ATTRIBUTE_MAX_BLOCK_DIM_Z, cuDevice);
            if (len >= (jsize) (3 * sizeof(long long))) {
                long long sizes[3] = { (long long) x, (long long) y, (long long) z };
                std::memcpy(buf, sizes, 3 * sizeof(long long));
            }
            break;
        }
        case CL_DEVICE_MAX_CLOCK_FREQUENCY:
            result = cuDeviceGetAttribute(&attr, CU_DEVICE_ATTRIBUTE_CLOCK_RATE, cuDevice);
            LOG_CUDA_AND_VALIDATE("cuDeviceGetAttribute(CLOCK_RATE)", result);
            put_int(buf, len, attr / 1000); // kHz -> MHz
            break;
        case CL_DEVICE_ADDRESS_BITS:
            put_int(buf, len, 64);
            break;
        case CL_DEVICE_MAX_MEM_ALLOC_SIZE: {
            size_t total = 0;
            cuDeviceTotalMem(&total, cuDevice);
            put_long(buf, len, (long long) total);
            break;
        }
        case CL_DEVICE_MEM_BASE_ADDR_ALIGN:
            put_int(buf, len, 4096 * 8); // textureAlignment in bits, generous default
            break;
        case CL_DEVICE_SINGLE_FP_CONFIG:
            put_long(buf, len, CL_FP_NONZERO);
            break;
        case CL_DEVICE_GLOBAL_MEM_SIZE: {
            size_t total = 0;
            cuDeviceTotalMem(&total, cuDevice);
            put_long(buf, len, (long long) total);
            break;
        }
        case CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE:
            result = cuDeviceGetAttribute(&attr, CU_DEVICE_ATTRIBUTE_TOTAL_CONSTANT_MEMORY, cuDevice);
            LOG_CUDA_AND_VALIDATE("cuDeviceGetAttribute(CONSTANT_MEM)", result);
            put_long(buf, len, (long long) attr);
            break;
        case CL_DEVICE_LOCAL_MEM_TYPE:
            put_int(buf, len, CL_LOCAL);
            break;
        case CL_DEVICE_LOCAL_MEM_SIZE:
            result = cuDeviceGetAttribute(&attr, CU_DEVICE_ATTRIBUTE_MAX_SHARED_MEMORY_PER_BLOCK, cuDevice);
            LOG_CUDA_AND_VALIDATE("cuDeviceGetAttribute(SHARED_MEM)", result);
            put_long(buf, len, (long long) attr);
            break;
        case CL_DEVICE_AVAILABLE:
            buf[0] = 1;
            break;
        case CL_DEVICE_NAME: {
            char name[256];
            result = cuDeviceGetName(name, sizeof(name), cuDevice);
            LOG_CUDA_AND_VALIDATE("cuDeviceGetName", result);
            put_string(buf, len, std::string(name));
            break;
        }
        case CL_DEVICE_VENDOR:
            put_string(buf, len, "NVIDIA Corporation");
            break;
        case CL_DRIVER_VERSION: {
            int driver_version = 0;
            cuDriverGetVersion(&driver_version);
            int major = driver_version / 1000;
            int minor = (driver_version % 1000) / 10;
            put_string(buf, len, std::to_string(major) + "." + std::to_string(minor));
            break;
        }
        case CL_DEVICE_PROFILE:
            put_string(buf, len, "FULL_PROFILE");
            break;
        case CL_DEVICE_VERSION: {
            // Reported as the compute capability so the Java parser
            // (split(" ")[1] -> "X.Y") yields the SM version.
            int major = 0, minor = 0;
            cuDeviceGetAttribute(&major, CU_DEVICE_ATTRIBUTE_COMPUTE_CAPABILITY_MAJOR, cuDevice);
            cuDeviceGetAttribute(&minor, CU_DEVICE_ATTRIBUTE_COMPUTE_CAPABILITY_MINOR, cuDevice);
            put_string(buf, len, "CUDA " + std::to_string(major) + "." + std::to_string(minor));
            break;
        }
        case CL_DEVICE_OPENCL_C_VERSION:
            put_string(buf, len, "CUDA C 1.0");
            break;
        case CL_DEVICE_EXTENSIONS: {
            // FP16 arithmetic requires compute capability >= 5.3.
            int major = 0, minor = 0;
            cuDeviceGetAttribute(&major, CU_DEVICE_ATTRIBUTE_COMPUTE_CAPABILITY_MAJOR, cuDevice);
            cuDeviceGetAttribute(&minor, CU_DEVICE_ATTRIBUTE_COMPUTE_CAPABILITY_MINOR, cuDevice);
            std::string extensions = "cl_khr_fp64";
            if (major > 5 || (major == 5 && minor >= 3)) {
                extensions += " cl_khr_fp16";
            }
            put_string(buf, len, extensions);
            break;
        }
        case CL_DEVICE_DOUBLE_FP_CONFIG:
            put_long(buf, len, CL_FP_NONZERO); // all NVIDIA GPUs support fp64
            break;
        case CL_DEVICE_HOST_UNIFIED_MEMORY:
            put_int(buf, len, 0);
            break;
        case CL_DEVICE_ENDIAN_LITTLE:
            put_int(buf, len, 1);
            break;
        case CL_DEVICE_IL_VERSION:
            // No SPIR-V ingestion path for CUDA; report empty (Java treats as unsupported).
            put_string(buf, len, "");
            break;
        case TORNADO_DEVICE_ASYNC_ENGINE_COUNT:
            // Number of async DMA copy engines: 0 = no copy/compute overlap, 1 = one direction,
            // >= 2 = H2D and D2H can both overlap compute (and each other).
            result = cuDeviceGetAttribute(&attr, CU_DEVICE_ATTRIBUTE_ASYNC_ENGINE_COUNT, cuDevice);
            LOG_CUDA_AND_VALIDATE("cuDeviceGetAttribute(ASYNC_ENGINE_COUNT)", result);
            put_int(buf, len, attr);
            break;
        case TORNADO_DEVICE_CONCURRENT_KERNELS:
            // 1 if the device can execute multiple kernels from the same context concurrently.
            result = cuDeviceGetAttribute(&attr, CU_DEVICE_ATTRIBUTE_CONCURRENT_KERNELS, cuDevice);
            LOG_CUDA_AND_VALIDATE("cuDeviceGetAttribute(CONCURRENT_KERNELS)", result);
            put_int(buf, len, attr);
            break;
        default:
            // Unknown / unsupported info key: leave buffer zeroed.
            break;
    }

    env->ReleasePrimitiveArrayCritical(array, buf, 0);
}

} // extern "C"
