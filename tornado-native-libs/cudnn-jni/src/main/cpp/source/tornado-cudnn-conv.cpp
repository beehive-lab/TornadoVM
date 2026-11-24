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
 * Channels-last (NHWC) convolution forward with its epilogue fused, through the cuDNN graph API
 * (cudnn-frontend): y = relu(conv(x, w) + bias + residual), each of bias, residual and ReLU
 * optional. One kernel replaces the convolution and the separate pass that adds the bias, adds
 * the residual and applies the ReLU.
 *
 * x, w, residual and y are FP32 or FP16 (FP32 accumulation); the bias is FP32, one value per
 * output channel. The filter is KRSC (k, r, s, c).
 *
 * cuDNN offers many engines for a fused convolution and its heuristics rank the fastest one
 * poorly, so creating a plan times the first `plans` candidates (all of them when 0) on scratch
 * buffers and keeps the fastest. Plans are created before CUDA graph capture (the provider's
 * prepare hook); executing one is capture-safe.
 *
 * Built only against CUDA 12.0 or newer, as the SDPA path (see tornado-cudnn-sdpa.cpp); older
 * toolkits get stubs that report the feature as unavailable.
 */

#include <jni.h>
#include <cstdio>

#include <cuda.h> // CUDA_VERSION

#if defined(CUDA_VERSION) && CUDA_VERSION >= 12000

#include <memory>
#include <unordered_map>
#include <vector>

#include <cuda_runtime.h>
#include <cudnn.h>
#include <cudnn_frontend.h>

namespace fe = cudnn_frontend;

namespace {

// Epilogue flags, as CuDnn.CONV_BIAS, CONV_RESIDUAL and CONV_RELU.
constexpr int EPILOGUE_BIAS = 1;
constexpr int EPILOGUE_RESIDUAL = 2;
constexpr int EPILOGUE_RELU = 4;

// Candidates needing more workspace than this are skipped.
constexpr int64_t MAX_WORKSPACE = 512LL << 20;

typedef std::shared_ptr<fe::graph::Tensor_attributes> TensorPtr;

typedef struct conv_plan_s {
    std::shared_ptr<fe::graph::Graph> graph;
    TensorPtr x;
    TensorPtr w;
    TensorPtr bias;
    TensorPtr residual;
    TensorPtr y;
    int64_t planIndex;
    int64_t workspaceBytes;
} conv_plan_t;

std::vector<int64_t> nhwcStrides(int64_t c, int64_t h, int64_t w) {
    return {c * h * w, 1, w * c, c};
}

struct DeviceBuffer {
    void *ptr = nullptr;
    ~DeviceBuffer() {
        if (ptr != nullptr) {
            cudaFree(ptr);
        }
    }
    bool allocate(size_t bytes) {
        if (ptr != nullptr) {
            cudaFree(ptr);
            ptr = nullptr;
        }
        if (cudaMalloc(&ptr, bytes == 0 ? 1 : bytes) != cudaSuccess) {
            ptr = nullptr;
            return false;
        }
        cudaMemset(ptr, 0, bytes);
        return true;
    }
};

// Mean time of one run of a plan: one warm-up, then three timed runs.
float timePlan(cudnnHandle_t handle, conv_plan_t *plan, int64_t index, std::unordered_map<TensorPtr, void *> &pack, void *workspace, cudaStream_t stream) {
    if (plan->graph->execute_plan_at_index(handle, pack, workspace, index).is_bad() || cudaStreamSynchronize(stream) != cudaSuccess) {
        cudaGetLastError();
        return -1;
    }
    cudaEvent_t start, stop;
    cudaEventCreate(&start);
    cudaEventCreate(&stop);
    cudaEventRecord(start, stream);
    bool ok = true;
    for (int i = 0; i < 3 && ok; i++) {
        ok = !plan->graph->execute_plan_at_index(handle, pack, workspace, index).is_bad();
    }
    cudaEventRecord(stop, stream);
    float ms = -1;
    if (ok && cudaEventSynchronize(stop) == cudaSuccess) {
        cudaEventElapsedTime(&ms, start, stop);
        ms /= 3;
    } else {
        cudaGetLastError();
        ms = -1;
    }
    cudaEventDestroy(start);
    cudaEventDestroy(stop);
    return ms;
}

conv_plan_t *buildConvPlan(cudnnHandle_t handle, int64_t n, int64_t c, int64_t h, int64_t w, int64_t k, int64_t r, int64_t s, int64_t pad, int64_t stride, bool half,
        int epilogue, int plans) {
    int64_t oh = (h + 2 * pad - r) / stride + 1;
    int64_t ow = (w + 2 * pad - s) / stride + 1;
    fe::DataType_t io = half ? fe::DataType_t::HALF : fe::DataType_t::FLOAT;

    auto plan = std::make_unique<conv_plan_t>();
    plan->graph = std::make_shared<fe::graph::Graph>();
    auto &graph = *plan->graph;
    graph.set_io_data_type(io).set_intermediate_data_type(fe::DataType_t::FLOAT).set_compute_data_type(fe::DataType_t::FLOAT);

    plan->x = graph.tensor(fe::graph::Tensor_attributes().set_name("X").set_dim({n, c, h, w}).set_stride(nhwcStrides(c, h, w)));
    plan->w = graph.tensor(fe::graph::Tensor_attributes().set_name("W").set_dim({k, c, r, s}).set_stride(nhwcStrides(c, r, s)));
    auto y = graph.conv_fprop(plan->x, plan->w, fe::graph::Conv_fprop_attributes().set_name("conv").set_padding({pad, pad}).set_stride({stride, stride}).set_dilation({1, 1}));
    if (epilogue & EPILOGUE_BIAS) {
        plan->bias = graph.tensor(fe::graph::Tensor_attributes().set_name("B").set_dim({1, k, 1, 1}).set_stride({k, 1, k, k}).set_data_type(fe::DataType_t::FLOAT));
        y = graph.pointwise(y, plan->bias, fe::graph::Pointwise_attributes().set_name("bias").set_mode(fe::PointwiseMode_t::ADD));
    }
    if (epilogue & EPILOGUE_RESIDUAL) {
        plan->residual = graph.tensor(fe::graph::Tensor_attributes().set_name("R").set_dim({n, k, oh, ow}).set_stride(nhwcStrides(k, oh, ow)));
        y = graph.pointwise(y, plan->residual, fe::graph::Pointwise_attributes().set_name("residual").set_mode(fe::PointwiseMode_t::ADD));
    }
    if (epilogue & EPILOGUE_RELU) {
        y = graph.pointwise(y, fe::graph::Pointwise_attributes().set_name("relu").set_mode(fe::PointwiseMode_t::RELU_FWD));
    }
    y->set_output(true).set_dim({n, k, oh, ow}).set_stride(nhwcStrides(k, oh, ow)).set_data_type(io);
    plan->y = y;

    auto status = graph.validate();
    if (status.is_good()) {
        status = graph.build_operation_graph(handle);
    }
    if (status.is_good()) {
        status = graph.create_execution_plans({fe::HeurMode_t::A, fe::HeurMode_t::FALLBACK});
    }
    if (status.is_good()) {
        status = graph.check_support(handle);
    }
    if (status.is_bad()) {
        fprintf(stderr, "[tornado-cudnn] fused convolution graph failed: %s\n", status.get_message().c_str());
        return nullptr;
    }

    cudaStream_t stream = nullptr;
    cudnnGetStream(handle, &stream);
    size_t elem = half ? 2 : 4;
    DeviceBuffer dx, dw, db, dr, dy, dws;
    if (!dx.allocate(n * c * h * w * elem) || !dw.allocate(k * c * r * s * elem) || !db.allocate(k * 4) || !dy.allocate(n * k * oh * ow * elem)
            || ((epilogue & EPILOGUE_RESIDUAL) && !dr.allocate(n * k * oh * ow * elem))) {
        cudaGetLastError();
        fprintf(stderr, "[tornado-cudnn] fused convolution: no device memory to time the candidates\n");
        return nullptr;
    }
    std::unordered_map<TensorPtr, void *> pack = {{plan->x, dx.ptr}, {plan->w, dw.ptr}, {plan->y, dy.ptr}};
    if (plan->bias) {
        pack[plan->bias] = db.ptr;
    }
    if (plan->residual) {
        pack[plan->residual] = dr.ptr;
    }

    int64_t count = graph.get_execution_plan_count();
    int64_t tried = 0;
    int64_t workspaceAllocated = -1;
    float best = -1;
    plan->planIndex = -1;
    for (int64_t i = 0; i < count && (plans <= 0 || tried < plans); i++) {
        int64_t workspace = 0;
        if (graph.build_plan_at_index(handle, i).is_bad() || graph.get_workspace_size_plan_at_index(i, workspace).is_bad() || workspace > MAX_WORKSPACE) {
            continue;
        }
        if (workspace > workspaceAllocated) {
            if (!dws.allocate(workspace)) {
                cudaGetLastError();
                continue;
            }
            workspaceAllocated = workspace;
        }
        tried++;
        float ms = timePlan(handle, plan.get(), i, pack, dws.ptr, stream);
        if (ms >= 0 && (best < 0 || ms < best)) {
            best = ms;
            plan->planIndex = i;
            plan->workspaceBytes = workspace;
        }
    }
    if (plan->planIndex < 0) {
        fprintf(stderr, "[tornado-cudnn] fused convolution: none of %ld candidate plans ran\n", (long) count);
        return nullptr;
    }
    return plan.release();
}

} // namespace

extern "C" {

/*
 * Class:     uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib
 * Method:    createConvFusedPlan
 * Signature: (JIIIIIIIIIZII)J
 *
 * Returns an opaque plan pointer, or 0 when cuDNN takes no plan for the shape (the reason is
 * printed to stderr).
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_createConvFusedPlan
        (JNIEnv *env, jclass clazz, jlong handle, jint n, jint c, jint h, jint w, jint k, jint r, jint s, jint pad, jint stride, jboolean half, jint epilogue, jint plans) {
    return (jlong) buildConvPlan((cudnnHandle_t) handle, n, c, h, w, k, r, s, pad, stride, half, epilogue, plans);
}

/*
 * Class:     uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib
 * Method:    convFusedPlanWorkspaceBytes
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_convFusedPlanWorkspaceBytes
        (JNIEnv *env, jclass clazz, jlong plan_ptr) {
    return (jlong) ((conv_plan_t *) plan_ptr)->workspaceBytes;
}

/*
 * Class:     uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib
 * Method:    executeConvFusedPlan
 * Signature: (JJJJJJJJ)I
 *
 * The bias and residual pointers are ignored when the plan has no bias or residual. Returns 0 on
 * success, non-zero on failure (the reason is printed to stderr).
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_executeConvFusedPlan
        (JNIEnv *env, jclass clazz, jlong handle, jlong plan_ptr, jlong d_x, jlong d_w, jlong d_bias, jlong d_residual, jlong d_y, jlong workspace_ptr) {
    conv_plan_t *plan = (conv_plan_t *) plan_ptr;
    std::unordered_map<TensorPtr, void *> pack = {{plan->x, (void *) d_x}, {plan->w, (void *) d_w}, {plan->y, (void *) d_y}};
    if (plan->bias) {
        pack[plan->bias] = (void *) d_bias;
    }
    if (plan->residual) {
        pack[plan->residual] = (void *) d_residual;
    }
    auto status = plan->graph->execute_plan_at_index((cudnnHandle_t) handle, pack, (void *) workspace_ptr, plan->planIndex);
    if (status.is_bad()) {
        fprintf(stderr, "[tornado-cudnn] fused convolution failed: %s\n", status.get_message().c_str());
        return 1;
    }
    return 0;
}

/*
 * Class:     uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib
 * Method:    destroyConvFusedPlan
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_destroyConvFusedPlan
        (JNIEnv *env, jclass clazz, jlong plan_ptr) {
    delete (conv_plan_t *) plan_ptr;
}

} // extern "C"

#else // CUDA_VERSION < 12000

extern "C" {

JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_createConvFusedPlan
        (JNIEnv *env, jclass clazz, jlong handle, jint n, jint c, jint h, jint w, jint k, jint r, jint s, jint pad, jint stride, jboolean half, jint epilogue, jint plans) {
    fprintf(stderr, "[tornado-cudnn] fused convolution unavailable: built against CUDA %d.%d (requires CUDA >= 12.0)\n", CUDA_VERSION / 1000, (CUDA_VERSION % 1000) / 10);
    return 0;
}

JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_convFusedPlanWorkspaceBytes
        (JNIEnv *env, jclass clazz, jlong plan_ptr) {
    return 0;
}

JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_executeConvFusedPlan
        (JNIEnv *env, jclass clazz, jlong handle, jlong plan_ptr, jlong d_x, jlong d_w, jlong d_bias, jlong d_residual, jlong d_y, jlong workspace_ptr) {
    return 1;
}

JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_cudnn_provider_CuDnnNativeLib_destroyConvFusedPlan
        (JNIEnv *env, jclass clazz, jlong plan_ptr) {
}

} // extern "C"

#endif
