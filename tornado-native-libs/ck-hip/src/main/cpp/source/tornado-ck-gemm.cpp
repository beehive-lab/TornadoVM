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

#include "tornado-ck-arch.hpp"
#include "tornado-ck-gemm.hpp"

using namespace tornado_ck;

namespace {

/**
 * Runs f on a device the kernels were compiled for (NO_KERNEL on any other), mapping an exception
 * (CK reports launch errors with them) to HIP_FAILURE: none may cross the C boundary.
 */
template <typename F>
int guarded(F f) {
    try {
        return currentDeviceIsCompiledTarget() ? f() : NO_KERNEL;
    } catch (...) {
        return HIP_FAILURE;
    }
}

} // namespace

extern "C" {

/** Element types of tornado_ck_gemm. */
enum { TORNADO_CK_F32 = 0, TORNADO_CK_F16 = 1, TORNADO_CK_BF16 = 2 };

/** Activations of tornado_ck_gemm_bias_act. */
enum { TORNADO_CK_RELU = 0, TORNADO_CK_GELU = 1, TORNADO_CK_SILU = 2, TORNADO_CK_SIGMOID = 3, TORNADO_CK_TANH = 4, TORNADO_CK_HARDSWISH = 5 };

/*
 * Row-major C (m x n) = alpha A (m x k) B (k x n) + beta C. Returns 0 on success, 1 when no
 * kernel supports the problem on the current device, 2 for an invalid argument, 3 when HIP
 * reports an error or a launch fails. With query non-null nothing runs: *query receives the workspace bytes the
 * problem needs, which the caller then passes as workspace.
 */
int tornado_ck_gemm(hipStream_t stream, int type, int m, int n, int k, float alpha, const void *a, const void *b, float beta, void *c, void *workspace,
                    size_t *query) {
    if (m <= 0 || n <= 0 || k <= 0) {
        return INVALID_ARGUMENT;
    }
    return guarded([&] {
        switch (type) {
            case TORNADO_CK_F32: return gemmF32(stream, m, n, k, alpha, a, b, beta, c, query, workspace);
            case TORNADO_CK_F16: return gemmF16(stream, m, n, k, alpha, a, b, beta, c, query, workspace);
            case TORNADO_CK_BF16: return gemmBF16(stream, m, n, k, alpha, a, b, beta, c, query, workspace);
            default: return INVALID_ARGUMENT;
        }
    });
}

/** Row-major FP16 D (m x n) = act(A (m x k) B (k x n) + bias (n)). Return codes and query as tornado_ck_gemm. */
int tornado_ck_gemm_bias_act(hipStream_t stream, int activation, int m, int n, int k, const void *a, const void *b, const void *bias, void *d, void *workspace,
                             size_t *query) {
    if (m <= 0 || n <= 0 || k <= 0) {
        return INVALID_ARGUMENT;
    }
    return guarded([&] {
        switch (activation) {
            case TORNADO_CK_RELU: return gemmBiasRelu(stream, m, n, k, a, b, bias, d, query, workspace);
            case TORNADO_CK_GELU: return gemmBiasGelu(stream, m, n, k, a, b, bias, d, query, workspace);
            case TORNADO_CK_SILU: return gemmBiasSilu(stream, m, n, k, a, b, bias, d, query, workspace);
            case TORNADO_CK_SIGMOID: return gemmBiasSigmoid(stream, m, n, k, a, b, bias, d, query, workspace);
            case TORNADO_CK_TANH: return gemmBiasTanh(stream, m, n, k, a, b, bias, d, query, workspace);
            case TORNADO_CK_HARDSWISH: return gemmBiasHardSwish(stream, m, n, k, a, b, bias, d, query, workspace);
            default: return INVALID_ARGUMENT;
        }
    });
}

/**
 * batchCount independent FP16 GEMMs C_i = alpha A_i B_i + beta C_i, packed contiguously (batch
 * strides m k, k n and m n), one launch each. Return codes and query as tornado_ck_gemm.
 */
int tornado_ck_hgemm_batched(hipStream_t stream, int m, int n, int k, float alpha, const void *a, const void *b, float beta, void *c, int batchCount,
                             void *workspace, size_t *query) {
    if (m <= 0 || n <= 0 || k <= 0 || batchCount <= 0) {
        return INVALID_ARGUMENT;
    }
    return guarded([&] {
        if (query != nullptr) {
            return gemmF16(stream, m, n, k, alpha, a, b, beta, c, query, workspace);
        }
        for (int i = 0; i < batchCount; i++) {
            const char *ai = static_cast<const char *>(a) + (size_t) i * m * k * sizeof(F16);
            const char *bi = static_cast<const char *>(b) + (size_t) i * k * n * sizeof(F16);
            char *ci = static_cast<char *>(c) + (size_t) i * m * n * sizeof(F16);
            int status = gemmF16(stream, m, n, k, alpha, ai, bi, beta, ci, nullptr, workspace);
            if (status != SUCCESS) {
                return status;
            }
        }
        return SUCCESS;
    });
}

}
