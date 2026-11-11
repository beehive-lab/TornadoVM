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

#include "tornado-ck-gemm.hpp"

namespace tornado_ck {

namespace {

int roundUp(int x, int to) { return (x + to - 1) / to * to; }

/** Bytes of the zero-padded buffers the DL path runs on: A (Mp x Kp), B (Kp x Np) and the product (Mp x Np). */
size_t dlPaddedBytes(int m, int n, int k) {
    size_t mp = roundUp(m, DL_M), np = roundUp(n, DL_N), kp = roundUp(k, DL_K);
    return (mp * kp + kp * np + mp * np) * sizeof(float);
}

/** c = product + beta c over the m x n block of the padded product (row pitch np); beta == 0 does not read c. */
__global__ void storeProduct(float *c, const float *product, int m, int n, int np, float beta) {
    size_t index = (size_t) blockIdx.x * blockDim.x + threadIdx.x;
    if (index < (size_t) m * n) {
        size_t i = index / n;
        size_t j = index % n;
        float value = product[i * np + j];
        c[index] = beta == 0.0f ? value : value + beta * c[index];
    }
}

/**
 * FP32 C = alpha A B + beta C on the DL operation, which needs tile-multiple shapes: A and B are
 * copied into zero-padded buffers in the workspace, the padded product alpha A B is computed, and
 * its m x n block is combined into C. Copies and kernels are all enqueued on the stream, so the
 * sequence can be captured in a graph.
 */
} // namespace

int dlFloatGemm(hipStream_t stream, int m, int n, int k, float alpha, const void *a, const void *b, float beta, void *c, size_t *workspaceQuery,
                void *workspace) {
    int mp = roundUp(m, DL_M), np = roundUp(n, DL_N), kp = roundUp(k, DL_K);
    float *ap = static_cast<float *>(workspace);
    float *bp = ap + (size_t) mp * kp;
    float *product = bp + (size_t) kp * np;
    std::array<const void *, 0> none{};
    std::array<ck::index_t, 0> noStrides{};
    size_t operationBytes = 0;
    int status = tryRun<DlFloat<ck::Tuple<>, AlphaScale>>(stream, ap, bp, none, product, mp, np, kp, kp, np, noStrides, np, AlphaScale{alpha}, &operationBytes,
                                                          nullptr);
    if (status != SUCCESS || operationBytes != 0) {
        return status != SUCCESS ? status : NO_KERNEL;
    }
    if (workspaceQuery != nullptr) {
        *workspaceQuery = dlPaddedBytes(m, n, k);
        return SUCCESS;
    }
    hipError_t err = hipMemsetAsync(workspace, 0, ((size_t) mp * kp + (size_t) kp * np) * sizeof(float), stream);
    if (err == hipSuccess) {
        err = hipMemcpy2DAsync(ap, (size_t) kp * sizeof(float), a, (size_t) k * sizeof(float), (size_t) k * sizeof(float), m, hipMemcpyDeviceToDevice, stream);
    }
    if (err == hipSuccess) {
        err = hipMemcpy2DAsync(bp, (size_t) np * sizeof(float), b, (size_t) n * sizeof(float), (size_t) n * sizeof(float), k, hipMemcpyDeviceToDevice, stream);
    }
    if (err != hipSuccess) {
        return HIP_FAILURE;
    }
    status = tryRun<DlFloat<ck::Tuple<>, AlphaScale>>(stream, ap, bp, none, product, mp, np, kp, kp, np, noStrides, np, AlphaScale{alpha}, nullptr, nullptr);
    if (status != SUCCESS) {
        return status;
    }
    // one thread per element over a 1-D grid: a grid row per matrix row would cap m at 65535
    constexpr int threads = 256;
    size_t blocks = ((size_t) m * n + threads - 1) / threads;
    if (blocks > INT32_MAX) {
        return INVALID_ARGUMENT;
    }
    storeProduct<<<dim3((unsigned) blocks), threads, 0, stream>>>(static_cast<float *>(c), product, m, n, np, beta);
    return hipPeekAtLastError() == hipSuccess ? SUCCESS : HIP_FAILURE;
}

int gemmF32(hipStream_t stream, int m, int n, int k, float alpha, const void *a, const void *b, float beta, void *c, size_t *workspaceQuery, void *workspace) {
    return gemm<F32>(stream, m, n, k, alpha, a, b, beta, c, workspaceQuery, workspace);
}

} // namespace tornado_ck
