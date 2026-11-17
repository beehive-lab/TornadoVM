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

#include "fmha_fwd.hpp"
#include "tornado-ck-arch.hpp"

#include <hip/hip_runtime.h>

namespace {

int sdpaForward(hipStream_t stream, const void* q, const void* k, const void* v, void* o, int b, int h, int sQ, int sKv, int d, float scale, int causal,
                const long long* strides) {
    if (b <= 0 || h <= 0 || sQ <= 0 || sKv <= 0 || d <= 0) {
        return 2;
    }
    for (int i = 0; i < 16; i++) {
        if (strides[i] < 0 || strides[i] > INT32_MAX) {
            return 2;
        }
    }
    if (strides[3] != 1 || strides[7] != 1 || strides[11] != 1 || strides[15] != 1) {
        return 2;
    }
    if (d % 8 != 0 || d > 256) {
        return 2;
    }
    if (!tornado_ck::currentDeviceIsCompiledTarget()) {
        return 1;
    }
    fmha_fwd_traits traits{};
    traits.hdim_q = d;
    traits.hdim_v = d;
    traits.data_type = "fp16";
    traits.is_group_mode = false;
    traits.is_v_rowmajor = true;
    traits.has_logits_soft_cap = false;
    // cuDNN's causal mask is the top-left aligned one: query i sees keys 0..i
    traits.mask_type = causal ? mask_enum::mask_top_left : mask_enum::no_mask;
    traits.bias_type = bias_enum::no_bias;
    traits.has_lse = false;
    traits.has_dropout = false;
    traits.qscale_type = quant_scale_enum::no_scale;
    traits.has_sink = false;

    fmha_fwd_args args{};
    args.q_ptr = q;
    args.k_ptr = k;
    args.v_ptr = v;
    args.o_ptr = o;
    args.seqlen_q = sQ;
    args.seqlen_k = sKv;
    args.batch = b;
    args.max_seqlen_q = sQ;
    args.hdim_q = d;
    args.hdim_v = d;
    args.nhead_q = h;
    args.nhead_k = h;
    args.scale_s = scale;
    args.logits_soft_cap = 0.0f;
    args.batch_stride_q = (ck_tile::index_t) strides[0];
    args.nhead_stride_q = (ck_tile::index_t) strides[1];
    args.stride_q = (ck_tile::index_t) strides[2];
    args.batch_stride_k = (ck_tile::index_t) strides[4];
    args.nhead_stride_k = (ck_tile::index_t) strides[5];
    args.stride_k = (ck_tile::index_t) strides[6];
    args.batch_stride_v = (ck_tile::index_t) strides[8];
    args.nhead_stride_v = (ck_tile::index_t) strides[9];
    args.stride_v = (ck_tile::index_t) strides[10];
    args.batch_stride_o = (ck_tile::index_t) strides[12];
    args.nhead_stride_o = (ck_tile::index_t) strides[13];
    args.stride_o = (ck_tile::index_t) strides[14];
    args.window_size_left = -1;
    args.window_size_right = causal ? 0 : -1;
    args.sink_size = 0;
    args.mask_type = static_cast<ck_tile::index_t>(traits.mask_type);
    args.min_seqlen_q = 0;
    args.p_drop = 0.0f;
    args.s_randval = false;
    args.drop_seed_offset = std::make_pair(uint64_t{0}, uint64_t{0});

    ck_tile::stream_config config{stream, false, 0};
    return fmha_fwd(traits, args, config) < 0 ? 1 : 0;
}

} // namespace

/*
 * Fused FP16 attention forward with CK's tile FMHA kernels: O = softmax(Q K^T * scale [+ causal
 * mask]) V. strides: 16 element strides, (batch, head, sequence, dim) of Q, K, V and then O; the
 * dim strides must be 1. Returns 0 on success, 1 when no kernel covers the shape on this GPU, 2
 * for an invalid argument, which includes a head dimension d that is not a multiple of 8 or is
 * above 256: cuDNN refuses those too, and the kernels compute them wrong. Returns 3 when the
 * launch fails (CK reports that with an exception, which must not cross the C boundary).
 */
extern "C" int tornado_ck_sdpa_forward(hipStream_t stream, const void* q, const void* k, const void* v, void* o, int b, int h, int sQ,
                                       int sKv, int d, float scale, int causal, const long long* strides) {
    try {
        return sdpaForward(stream, q, k, v, o, b, h, sQ, sKv, d, scale, causal, strides);
    } catch (...) {
        return 3;
    }
}
