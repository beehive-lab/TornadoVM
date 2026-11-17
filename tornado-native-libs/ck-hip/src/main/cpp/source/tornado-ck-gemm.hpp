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

#pragma once

#include <hip/hip_runtime.h>

#include <array>
#include <cmath>
#include <cstdlib>

#include "ck/ck.hpp"
#include "ck/tensor_operation/gpu/device/gemm_specialization.hpp"
#include "ck/tensor_operation/gpu/device/impl/device_gemm_multiple_d_dl.hpp"
#include "ck/tensor_operation/gpu/device/impl/device_gemm_multiple_d_wmma_cshuffle_v3.hpp"
#include "ck/tensor_operation/gpu/device/impl/device_gemm_multiple_d_xdl_cshuffle.hpp"
#include "ck/tensor_operation/gpu/element/element_wise_operation.hpp"

/*
 * Row-major GEMM library tasks for HIP devices on Composable Kernel device operations: XDL on CDNA
 * (gfx9), WMMA on RDNA3/RDNA4 (gfx11, gfx12) for FP16/BF16, and the DL operation for FP32 where
 * there is no XDL. Every entry point tries the operations in that order and runs the first one
 * that supports the problem on the current device.
 */

namespace tornado_ck {

using F16 = ck::half_t;
using BF16 = ck::bhalf_t;
using F32 = float;
using Row = ck::tensor_layout::gemm::RowMajor;
using PassThrough = ck::tensor_operation::element_wise::PassThrough;
using ck::tensor_operation::device::GemmSpecialization;
using ck::BlockGemmPipelineScheduler;
using ck::BlockGemmPipelineVersion;

template <ck::index_t... Is>
using S = ck::Sequence<Is...>;

inline constexpr int SUCCESS = 0;
inline constexpr int NO_KERNEL = 1;
inline constexpr int INVALID_ARGUMENT = 2;
inline constexpr int HIP_FAILURE = 3;

/** e = alpha * c. */
struct AlphaScale {
    float alpha;

    template <typename E, typename C>
    __host__ __device__ constexpr void operator()(E &e, const C &c) const {
        e = ck::type_convert<E>(alpha * ck::type_convert<float>(c));
    }
};

/** e = alpha * c + beta * d. */
struct AlphaBeta {
    float alpha;
    float beta;

    template <typename E, typename C, typename D>
    __host__ __device__ constexpr void operator()(E &e, const C &c, const D &d) const {
        e = ck::type_convert<E>(alpha * ck::type_convert<float>(c) + beta * ck::type_convert<float>(d));
    }
};

struct Relu {
    __host__ __device__ float operator()(float x) const { return x > 0.0f ? x : 0.0f; }
};

/** The exact (erf) GELU. */
struct Gelu {
    __host__ __device__ float operator()(float x) const { return 0.5f * x * (1.0f + erff(x * 0.70710678118654752f)); }
};

struct Sigmoid {
    __host__ __device__ float operator()(float x) const { return 1.0f / (1.0f + expf(-x)); }
};

struct Silu {
    __host__ __device__ float operator()(float x) const { return x / (1.0f + expf(-x)); }
};

struct Tanh {
    __host__ __device__ float operator()(float x) const { return tanhf(x); }
};

struct HardSwish {
    __host__ __device__ float operator()(float x) const { return x * fminf(fmaxf(x + 3.0f, 0.0f), 6.0f) / 6.0f; }
};

/** e = act(c + d), d being the bias row broadcast over the rows. */
template <typename Act>
struct BiasAct {
    template <typename E, typename C, typename D>
    __host__ __device__ constexpr void operator()(E &e, const C &c, const D &d) const {
        e = ck::type_convert<E>(Act{}(ck::type_convert<float>(c) + ck::type_convert<float>(d)));
    }
};

template <typename Ds>
using RowTuple = std::conditional_t<Ds::Size() == 0, ck::Tuple<>, ck::Tuple<Row>>;

/*
 * The operations. Operand addresses are only 8-byte aligned (a TornadoVM array's data follows a
 * 24-byte header), so the 2-byte types load at most 4 elements per vector, and FP32 loads one
 * (any shape). MNK padding handles shapes that are not tile multiples.
 */
template <typename T, typename Ds, typename Op>
using XdlHalf = ck::tensor_operation::device::DeviceGemmMultipleD_Xdl_CShuffle<
        Row, Row, RowTuple<Ds>, Row, T, T, F32, F32, Ds, T, PassThrough, PassThrough, Op, GemmSpecialization::MNKPadding, //
        1, 128, 128, 64, 32, 8, 2, 32, 32, 2, 2,                                                                         //
        S<4, 32, 1>, S<1, 0, 2>, S<1, 0, 2>, 2, 4, 8, 1,                                                                 //
        S<8, 16, 1>, S<0, 2, 1>, S<0, 2, 1>, 1, 4, 2, 0,                                                                 //
        1, 1, S<1, 32, 1, 4>, 4>;

template <typename Ds, typename Op>
using XdlFloat = ck::tensor_operation::device::DeviceGemmMultipleD_Xdl_CShuffle<
        Row, Row, RowTuple<Ds>, Row, F32, F32, F32, F32, Ds, F32, PassThrough, PassThrough, Op, GemmSpecialization::MNKPadding, //
        1, 256, 128, 128, 16, 4, 4, 32, 32, 2, 2,                                                                               //
        S<4, 64, 1>, S<1, 0, 2>, S<1, 0, 2>, 2, 1, 4, 1,                                                                        //
        S<4, 64, 1>, S<0, 2, 1>, S<0, 2, 1>, 1, 1, 4, 1,                                                                        //
        1, 1, S<1, 16, 1, 16>, 1>;

template <typename Ds>
using CdeVectors = std::conditional_t<Ds::Size() == 0, S<4>, S<4, 4>>;

template <typename T, typename Ds, typename Op>
using WmmaHalf = ck::tensor_operation::device::DeviceGemmMultipleD_Wmma_CShuffleV3<
        Row, Row, RowTuple<Ds>, Row, T, T, Ds, T, F32, F32, PassThrough, PassThrough, Op, GemmSpecialization::MNKPadding, //
        128, 128, 64, 64, 8, 8, 16, 16, 4, 2,                                                                            //
        S<4, 32, 1>, S<1, 0, 2>, S<1, 0, 2>, 2, 4, 8, 0,                                                                 //
        S<4, 32, 1>, S<0, 2, 1>, S<0, 2, 1>, 1, 1, 8, 0,                                                                 //
        1, 1, S<1, 32, 1, 4>, CdeVectors<Ds>, BlockGemmPipelineScheduler::Intrawave, BlockGemmPipelineVersion::v1>;

/** The DL operation needs M, N and K to be multiples of these; its callers pad. */
inline constexpr int DL_M = 128;
inline constexpr int DL_N = 128;
inline constexpr int DL_K = 16;

template <typename Ds, typename Op>
using DlFloat = ck::tensor_operation::device::DeviceGemmMultipleD_Dl<
        Row, Row, RowTuple<Ds>, Row, F32, F32, F32, Ds, F32, PassThrough, PassThrough, Op, GemmSpecialization::Default, //
        256, DL_M, DL_N, DL_K, 1, 4, 4, 1, S<8, 2>, S<8, 2>,                                                           //
        S<8, 1, 1, 1>, S<2, 1, 128, 1>, S<1, 2, 0, 3>, S<1, 2, 0, 3>, S<1, 1, 1, 1>, S<1, 2, 0, 3>, S<1, 1, 1, 1>,     //
        S<2, 1, 4, 1>, S<8, 1, 32, 1>, S<0, 3, 1, 2>, S<0, 3, 1, 2>, S<1, 1, 1, 1>, S<0, 3, 1, 2>, S<1, 1, 4, 1>,      //
        S<0, 1, 2, 3, 4, 5>, 5, 1>;

/**
 * Runs {@code Gemm} if it supports the problem on the current device; returns NO_KERNEL when it
 * does not. With {@code workspaceQuery} non-null nothing is launched: it receives the operation's
 * workspace size.
 */
template <typename Gemm, typename Op, size_t NumD>
int tryRun(hipStream_t stream, const void *a, const void *b, std::array<const void *, NumD> ds, void *e, int m, int n, int k, int lda, int ldb,
           std::array<ck::index_t, NumD> ldds, int lde, Op op, size_t *workspaceQuery, void *workspace) {
    Gemm gemm;
    auto argument = [&] {
        // the WMMA operations also take a split-K factor
        if constexpr (requires { gemm.MakeArgumentPointer(a, b, ds, e, m, n, k, lda, ldb, ldds, lde, 1, PassThrough{}, PassThrough{}, op); }) {
            return gemm.MakeArgumentPointer(a, b, ds, e, m, n, k, lda, ldb, ldds, lde, 1, PassThrough{}, PassThrough{}, op);
        } else {
            return gemm.MakeArgumentPointer(a, b, ds, e, m, n, k, lda, ldb, ldds, lde, PassThrough{}, PassThrough{}, op);
        }
    }();
    if (!gemm.IsSupportedArgument(argument.get())) {
        return NO_KERNEL;
    }
    size_t bytes = gemm.GetWorkSpaceSize(argument.get());
    if (workspaceQuery != nullptr) {
        *workspaceQuery = bytes;
        return SUCCESS;
    }
    if (bytes != 0) {
        gemm.SetWorkSpacePointer(argument.get(), workspace);
    }
    gemm.MakeInvokerPointer()->Run(argument.get(), StreamConfig{stream, false});
    return hipPeekAtLastError() == hipSuccess ? SUCCESS : HIP_FAILURE;
}

/** FP32 GEMM on the DL operation over zero-padded copies (tornado-ck-gemm-f32.cpp). */
int dlFloatGemm(hipStream_t stream, int m, int n, int k, float alpha, const void *a, const void *b, float beta, void *c, size_t *workspaceQuery, void *workspace);

/** C = alpha A B + beta C; beta == 0 does not read C. */
template <typename T>
int gemm(hipStream_t stream, int m, int n, int k, float alpha, const void *a, const void *b, float beta, void *c, size_t *workspaceQuery, void *workspace) {
    std::array<const void *, 0> none{};
    std::array<ck::index_t, 0> noStrides{};
    std::array<const void *, 1> source{c};
    std::array<ck::index_t, 1> sourceStride{n};
    int status;
    if constexpr (std::is_same_v<T, F32>) {
        status = beta == 0.0f ? tryRun<XdlFloat<ck::Tuple<>, AlphaScale>>(stream, a, b, none, c, m, n, k, k, n, noStrides, n, AlphaScale{alpha}, workspaceQuery, workspace)
                              : tryRun<XdlFloat<ck::Tuple<F32>, AlphaBeta>>(stream, a, b, source, c, m, n, k, k, n, sourceStride, n, AlphaBeta{alpha, beta},
                                                                             workspaceQuery, workspace);
        if (status != NO_KERNEL) {
            return status;
        }
        return dlFloatGemm(stream, m, n, k, alpha, a, b, beta, c, workspaceQuery, workspace);
    } else {
        status = beta == 0.0f ? tryRun<XdlHalf<T, ck::Tuple<>, AlphaScale>>(stream, a, b, none, c, m, n, k, k, n, noStrides, n, AlphaScale{alpha}, workspaceQuery, workspace)
                              : tryRun<XdlHalf<T, ck::Tuple<T>, AlphaBeta>>(stream, a, b, source, c, m, n, k, k, n, sourceStride, n, AlphaBeta{alpha, beta},
                                                                             workspaceQuery, workspace);
        if (status == NO_KERNEL) {
            status = beta == 0.0f ? tryRun<WmmaHalf<T, ck::Tuple<>, AlphaScale>>(stream, a, b, none, c, m, n, k, k, n, noStrides, n, AlphaScale{alpha}, workspaceQuery,
                                                                                  workspace)
                                  : tryRun<WmmaHalf<T, ck::Tuple<T>, AlphaBeta>>(stream, a, b, source, c, m, n, k, k, n, sourceStride, n, AlphaBeta{alpha, beta},
                                                                                 workspaceQuery, workspace);
        }
        return status;
    }
}

/** D = act(A B + bias), FP16, the length-n bias broadcast over the rows (a D operand with row stride 0). */
template <typename Act>
int gemmBiasAct(hipStream_t stream, int m, int n, int k, const void *a, const void *b, const void *bias, void *d, size_t *workspaceQuery, void *workspace) {
    std::array<const void *, 1> ds{bias};
    std::array<ck::index_t, 1> dStrides{0};
    int status = tryRun<XdlHalf<F16, ck::Tuple<F16>, BiasAct<Act>>>(stream, a, b, ds, d, m, n, k, k, n, dStrides, n, BiasAct<Act>{}, workspaceQuery, workspace);
    if (status == NO_KERNEL) {
        status = tryRun<WmmaHalf<F16, ck::Tuple<F16>, BiasAct<Act>>>(stream, a, b, ds, d, m, n, k, k, n, dStrides, n, BiasAct<Act>{}, workspaceQuery, workspace);
    }
    return status;
}

/* Defined one per translation unit, so the kernels compile in parallel. */
int gemmF32(hipStream_t stream, int m, int n, int k, float alpha, const void *a, const void *b, float beta, void *c, size_t *workspaceQuery, void *workspace);
int gemmF16(hipStream_t stream, int m, int n, int k, float alpha, const void *a, const void *b, float beta, void *c, size_t *workspaceQuery, void *workspace);
int gemmBF16(hipStream_t stream, int m, int n, int k, float alpha, const void *a, const void *b, float beta, void *c, size_t *workspaceQuery, void *workspace);
int gemmBiasRelu(hipStream_t stream, int m, int n, int k, const void *a, const void *b, const void *bias, void *d, size_t *workspaceQuery, void *workspace);
int gemmBiasGelu(hipStream_t stream, int m, int n, int k, const void *a, const void *b, const void *bias, void *d, size_t *workspaceQuery, void *workspace);
int gemmBiasSilu(hipStream_t stream, int m, int n, int k, const void *a, const void *b, const void *bias, void *d, size_t *workspaceQuery, void *workspace);
int gemmBiasSigmoid(hipStream_t stream, int m, int n, int k, const void *a, const void *b, const void *bias, void *d, size_t *workspaceQuery, void *workspace);
int gemmBiasTanh(hipStream_t stream, int m, int n, int k, const void *a, const void *b, const void *bias, void *d, size_t *workspaceQuery, void *workspace);
int gemmBiasHardSwish(hipStream_t stream, int m, int n, int k, const void *a, const void *b, const void *bias, void *d, size_t *workspaceQuery, void *workspace);

} // namespace tornado_ck
