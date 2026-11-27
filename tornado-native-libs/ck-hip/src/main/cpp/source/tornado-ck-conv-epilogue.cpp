/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

/*
 * The epilogue of a fused NHWC convolution on HIP devices. MIOpen has no public fusion of a
 * convolution with a residual add, and its element-wise operations need every tensor in one data
 * type, while the FP16 convolution takes an FP32 bias. So the convolution runs first, writing the
 * output, and this kernel then applies the epilogue to it in place, in FP32:
 * out = relu(out + bias[channel] + residual), each term selected by a flag.
 */

#include <cstdint>

#include <hip/hip_fp16.h>
#include <hip/hip_runtime.h>

#include "tornado-ck-arch.hpp"

namespace {

enum { SUCCESS = 0, NO_KERNEL = 1, INVALID_ARGUMENT = 2, HIP_FAILURE = 3 };

/** Epilogue flags, the values of CuDnn.CONV_BIAS, CONV_RESIDUAL and CONV_RELU. */
enum { EPILOGUE_BIAS = 1, EPILOGUE_RESIDUAL = 2, EPILOGUE_RELU = 4 };

__device__ inline float load(const float *p, int64_t i) {
    return p[i];
}

__device__ inline float load(const __half *p, int64_t i) {
    return __half2float(p[i]);
}

__device__ inline void store(float *p, int64_t i, float v) {
    p[i] = v;
}

__device__ inline void store(__half *p, int64_t i, float v) {
    p[i] = __float2half(v);
}

/** One thread per element of the NHWC output, whose innermost dimension is the channel. */
template <typename T>
__global__ void convEpilogue(T *out, const float *bias, const T *residual, int64_t total, int channels, int flags) {
    for (int64_t i = blockIdx.x * (int64_t) blockDim.x + threadIdx.x; i < total; i += (int64_t) gridDim.x * blockDim.x) {
        float v = load(out, i);
        if (flags & EPILOGUE_BIAS) {
            v += bias[i % channels];
        }
        if (flags & EPILOGUE_RESIDUAL) {
            v += load(residual, i);
        }
        if (flags & EPILOGUE_RELU) {
            v = fmaxf(v, 0.0f);
        }
        store(out, i, v);
    }
}

template <typename T>
int launch(hipStream_t stream, void *out, const void *bias, const void *residual, int64_t total, int channels, int flags) {
    constexpr int threads = 256;
    int64_t blocks = (total + threads - 1) / threads;
    // a grid-stride loop covers the rest
    if (blocks > 65535 * 16) {
        blocks = 65535 * 16;
    }
    hipLaunchKernelGGL(convEpilogue<T>, dim3((unsigned) blocks), dim3(threads), 0, stream, static_cast<T *>(out), static_cast<const float *>(bias),
                       static_cast<const T *>(residual), total, channels, flags);
    return hipGetLastError() == hipSuccess ? SUCCESS : HIP_FAILURE;
}

} // namespace

extern "C" {

/*
 * Applies out = relu(out + bias[c] + residual) in place on stream, to an NHWC tensor of total
 * elements with channels channels: FP16 when half is non-zero, FP32 otherwise. The bias holds
 * channels FP32 values; the residual has the output's type, shape and layout. flags selects the
 * terms (1 bias, 2 residual, 4 ReLU); an array whose flag is off is not read. Returns 0 on
 * success, 1 on a device the library was not compiled for, 2 for an invalid argument, 3 when the
 * launch fails.
 */
int tornado_ck_conv_epilogue(hipStream_t stream, int half, void *out, const void *bias, const void *residual, int64_t total, int channels, int flags) {
    if (out == nullptr || total <= 0 || channels <= 0 || (flags & ~(EPILOGUE_BIAS | EPILOGUE_RESIDUAL | EPILOGUE_RELU)) != 0 //
        || ((flags & EPILOGUE_BIAS) && bias == nullptr) || ((flags & EPILOGUE_RESIDUAL) && residual == nullptr)) {
        return INVALID_ARGUMENT;
    }
    if (flags == 0) {
        return SUCCESS;
    }
    if (!tornado_ck::currentDeviceIsCompiledTarget()) {
        return NO_KERNEL;
    }
    return half ? launch<__half>(stream, out, bias, residual, total, channels, flags) : launch<float>(stream, out, bias, residual, total, channels, flags);
}

} // extern "C"
