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
 * Fused scaled-dot-product attention (flash attention) via the cuDNN graph
 * API, built with the header-only cudnn-frontend (the NVIDIA-recommended
 * interface; the raw backend descriptor API is impractical to bind directly).
 *
 * One sdpa_plan_t bundles the finalized execution plan and the tensor handles
 * for the variant pack. Plans are created per shape on the Java side (via the
 * provider prepare() hook, before CUDA graph capture) and replayed with
 * executeSdpaPlan. Layout: packed BHSD (batch, heads, sequence, head-dim),
 * FP16 I/O, FP32 intermediates/compute, inference only (no stats output).
 *
 * Toolkit guard: recent cudnn-frontend releases transitively include
 * experimental sm90/sm100 prefill engine headers that reference CUDA 12.x
 * driver types (CUlibrary, CUkernel, CUtensorMap, ...). Those types do not
 * exist on older toolkits, so on CUDA < 12 we cannot even parse the frontend
 * headers. To keep the JNI library building (and loading) everywhere, the
 * fused-SDPA implementation is compiled only when CUDA_VERSION >= 12000;
 * otherwise the four JNI entry points are provided as stubs that report the
 * feature as unavailable. createSdpaPlan returning 0 is already handled by the
 * Java provider (CuDnnLibraryProvider) as "SDPA plan creation failed".
 */

#include <jni.h>
#include <cstdio>

#include <cuda.h> // CUDA_VERSION

#if defined(CUDA_VERSION) && CUDA_VERSION >= 12000
#define TORNADO_CUDNN_SDPA_SUPPORTED 1
#else
#define TORNADO_CUDNN_SDPA_SUPPORTED 0
#endif

#if TORNADO_CUDNN_SDPA_SUPPORTED

#include <memory>
#include <unordered_map>

#include <cudnn.h>
#include <cudnn_frontend.h>

namespace fe = cudnn_frontend;

extern "C" {

typedef struct sdpa_plan_s {
    std::shared_ptr<fe::graph::Graph> graph;
    std::shared_ptr<fe::graph::Tensor_attributes> q;
    std::shared_ptr<fe::graph::Tensor_attributes> k;
    std::shared_ptr<fe::graph::Tensor_attributes> v;
    std::shared_ptr<fe::graph::Tensor_attributes> o;
    int64_t workspaceBytes;
} sdpa_plan_t;

/*
 * Class:     uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib
 * Method:    createSdpaPlan
 * Signature: (JIIIIIFZ)J
 *
 * Builds and finalizes the fused SDPA graph for Q[b,h,s_q,d], K[b,h,s_kv,d],
 * V[b,h,s_kv,d] -> O[b,h,s_q,d], packed BHSD strides. Returns an opaque plan
 * pointer, or 0 on failure (the frontend error is printed to stderr).
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_createSdpaPlan
        (JNIEnv *env, jclass clazz, jlong handle, jint b, jint h, jint s_q, jint s_kv, jint d,
         jfloat scale, jboolean causal) {

    auto plan = new sdpa_plan_t();
    plan->graph = std::make_shared<fe::graph::Graph>();
    plan->graph->set_io_data_type(fe::DataType_t::HALF)
               .set_intermediate_data_type(fe::DataType_t::FLOAT)
               .set_compute_data_type(fe::DataType_t::FLOAT);

    auto dim_q = std::vector<int64_t>{b, h, s_q, d};
    auto stride_q = std::vector<int64_t>{(int64_t) h * s_q * d, (int64_t) s_q * d, d, 1};
    auto dim_kv = std::vector<int64_t>{b, h, s_kv, d};
    auto stride_kv = std::vector<int64_t>{(int64_t) h * s_kv * d, (int64_t) s_kv * d, d, 1};

    plan->q = plan->graph->tensor(fe::graph::Tensor_attributes().set_name("Q").set_dim(dim_q).set_stride(stride_q));
    plan->k = plan->graph->tensor(fe::graph::Tensor_attributes().set_name("K").set_dim(dim_kv).set_stride(stride_kv));
    plan->v = plan->graph->tensor(fe::graph::Tensor_attributes().set_name("V").set_dim(dim_kv).set_stride(stride_kv));

    auto sdpa_options = fe::graph::SDPA_attributes()
            .set_name("sdpa")
            .set_generate_stats(false) // inference-only: don't emit softmax stats (replaces deprecated set_is_inference(true))
            .set_attn_scale(scale);
    if (causal) {
        sdpa_options.set_causal_mask(true);
    }

    auto [o, stats] = plan->graph->sdpa(plan->q, plan->k, plan->v, sdpa_options);
    (void) stats; // null in inference mode
    plan->o = o;
    plan->o->set_output(true).set_dim(dim_q).set_stride(stride_q);

    auto status = plan->graph->build((cudnnHandle_t) handle, {fe::HeurMode_t::A});
    if (status.is_bad()) {
        fprintf(stderr, "[tornado-cudnn] SDPA plan build failed: %s\n", status.get_message().c_str());
        delete plan;
        return 0;
    }

    int64_t workspace = 0;
    auto wsStatus = plan->graph->get_workspace_size(workspace);
    if (wsStatus.is_bad()) {
        fprintf(stderr, "[tornado-cudnn] SDPA workspace query failed: %s\n", wsStatus.get_message().c_str());
        delete plan;
        return 0;
    }
    plan->workspaceBytes = workspace;
    return (jlong) plan;
}

/*
 * Class:     uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib
 * Method:    sdpaPlanWorkspaceBytes
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_sdpaPlanWorkspaceBytes
        (JNIEnv *env, jclass clazz, jlong plan_ptr) {
    return (jlong) ((sdpa_plan_t *) plan_ptr)->workspaceBytes;
}

/*
 * Class:     uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib
 * Method:    executeSdpaPlan
 * Signature: (JJJJJJJ)I
 *
 * Returns 0 on success, non-zero on failure (frontend error to stderr).
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_executeSdpaPlan
        (JNIEnv *env, jclass clazz, jlong handle, jlong plan_ptr, jlong d_q, jlong d_k, jlong d_v, jlong d_o, jlong workspace_ptr) {
    sdpa_plan_t *plan = (sdpa_plan_t *) plan_ptr;
    std::unordered_map<std::shared_ptr<fe::graph::Tensor_attributes>, void *> variant_pack = {
        {plan->q, (void *) d_q},
        {plan->k, (void *) d_k},
        {plan->v, (void *) d_v},
        {plan->o, (void *) d_o},
    };
    auto status = plan->graph->execute((cudnnHandle_t) handle, variant_pack, (void *) workspace_ptr);
    if (status.is_bad()) {
        fprintf(stderr, "[tornado-cudnn] SDPA execute failed: %s\n", status.get_message().c_str());
        return 1;
    }
    return 0;
}

/*
 * Class:     uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib
 * Method:    destroySdpaPlan
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_destroySdpaPlan
        (JNIEnv *env, jclass clazz, jlong plan_ptr) {
    delete (sdpa_plan_t *) plan_ptr;
}

} // extern "C"

#else // !TORNADO_CUDNN_SDPA_SUPPORTED

/*
 * Fallback stubs for CUDA toolkits older than 12.0, where cudnn-frontend's
 * fused-SDPA headers do not compile. The symbols are kept so the JNI library
 * links and loads; the feature simply reports itself as unavailable.
 */

extern "C" {

JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_createSdpaPlan
        (JNIEnv *env, jclass clazz, jlong handle, jint b, jint h, jint s_q, jint s_kv, jint d,
         jfloat scale, jboolean causal) {
    (void) env; (void) clazz; (void) handle;
    (void) b; (void) h; (void) s_q; (void) s_kv; (void) d; (void) scale; (void) causal;
    fprintf(stderr, "[tornado-cudnn] fused SDPA unavailable: built against CUDA %d.%d "
                    "(requires CUDA >= 12.0 for the cudnn-frontend flash-attention path)\n",
            CUDA_VERSION / 1000, (CUDA_VERSION % 1000) / 10);
    return 0;
}

JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_sdpaPlanWorkspaceBytes
        (JNIEnv *env, jclass clazz, jlong plan_ptr) {
    (void) env; (void) clazz; (void) plan_ptr;
    return 0;
}

JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_executeSdpaPlan
        (JNIEnv *env, jclass clazz, jlong handle, jlong plan_ptr, jlong d_q, jlong d_k, jlong d_v, jlong d_o, jlong workspace_ptr) {
    (void) env; (void) clazz; (void) handle; (void) plan_ptr;
    (void) d_q; (void) d_k; (void) d_v; (void) d_o; (void) workspace_ptr;
    return 1;
}

JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_destroySdpaPlan
        (JNIEnv *env, jclass clazz, jlong plan_ptr) {
    (void) env; (void) clazz; (void) plan_ptr;
}

} // extern "C"

#endif // TORNADO_CUDNN_SDPA_SUPPORTED
