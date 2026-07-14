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
 * JNI bindings for uk.ac.manchester.tornado.nccl.NcclNativeLib.
 *
 * NVIDIA NCCL multi-GPU collectives (FP32). One process manages all ranks; each
 * rank maps to a CUDA device (single-process, multi-GPU). Collectives are
 * host-staged: the flat per-rank Java float[] is copied to per-rank device
 * buffers, the NCCL collective runs inside an ncclGroup on per-rank streams, and
 * the results are copied back. Per-rank device buffers are cached grow-only in
 * the context. (Device-buffer sharing with TornadoVM's task-graph buffers is a
 * future zero-copy optimization.)
 */

#include <jni.h>
#include <cuda_runtime_api.h>
#include <nccl.h>

#include <vector>

extern "C" {

typedef struct nccl_ctx_s {
    int nRanks;
    std::vector<int> devices;
    std::vector<ncclComm_t> comms;
    std::vector<cudaStream_t> streams;
    std::vector<void *> sendBuf;
    std::vector<void *> recvBuf;
    std::vector<size_t> sendCap;
    std::vector<size_t> recvCap;
} nccl_ctx_t;

static ncclRedOp_t redOp(jint op) {
    switch (op) {
        case 1: return ncclProd;
        case 2: return ncclMax;
        case 3: return ncclMin;
        default: return ncclSum;
    }
}

static bool ensureSend(nccl_ctx_t *ctx, int r, size_t bytes) {
    if (bytes <= ctx->sendCap[r]) return true;
    cudaSetDevice(ctx->devices[r]);
    if (ctx->sendBuf[r]) cudaFree(ctx->sendBuf[r]);
    if (cudaMalloc(&ctx->sendBuf[r], bytes) != cudaSuccess) { ctx->sendBuf[r] = nullptr; ctx->sendCap[r] = 0; return false; }
    ctx->sendCap[r] = bytes;
    return true;
}

static bool ensureRecv(nccl_ctx_t *ctx, int r, size_t bytes) {
    if (bytes <= ctx->recvCap[r]) return true;
    cudaSetDevice(ctx->devices[r]);
    if (ctx->recvBuf[r]) cudaFree(ctx->recvBuf[r]);
    if (cudaMalloc(&ctx->recvBuf[r], bytes) != cudaSuccess) { ctx->recvBuf[r] = nullptr; ctx->recvCap[r] = 0; return false; }
    ctx->recvCap[r] = bytes;
    return true;
}

/*
 * Class:     uk_ac_manchester_tornado_nccl_NcclNativeLib
 * Method:    create
 * Signature: ([I)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_nccl_NcclNativeLib_create
        (JNIEnv *env, jclass clazz, jintArray deviceIds) {
    jint n = env->GetArrayLength(deviceIds);
    if (n <= 0) return 0;
    jint *devs = env->GetIntArrayElements(deviceIds, nullptr);

    nccl_ctx_t *ctx = new nccl_ctx_t();
    ctx->nRanks = n;
    ctx->devices.assign(devs, devs + n);
    ctx->comms.resize(n);
    ctx->streams.resize(n, nullptr);
    ctx->sendBuf.resize(n, nullptr);
    ctx->recvBuf.resize(n, nullptr);
    ctx->sendCap.resize(n, 0);
    ctx->recvCap.resize(n, 0);
    env->ReleaseIntArrayElements(deviceIds, devs, JNI_ABORT);

    bool ok = true;
    for (int r = 0; r < n && ok; r++) {
        if (cudaSetDevice(ctx->devices[r]) != cudaSuccess) ok = false;
        else if (cudaStreamCreate(&ctx->streams[r]) != cudaSuccess) ok = false;
    }
    if (ok && ncclCommInitAll(ctx->comms.data(), n, ctx->devices.data()) != ncclSuccess) {
        ok = false;
    }
    if (!ok) {
        for (int r = 0; r < n; r++) {
            if (ctx->streams[r]) cudaStreamDestroy(ctx->streams[r]);
        }
        delete ctx;
        return 0;
    }
    return (jlong) ctx;
}

/*
 * Class:     uk_ac_manchester_tornado_nccl_NcclNativeLib
 * Method:    destroy
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_nccl_NcclNativeLib_destroy
        (JNIEnv *env, jclass clazz, jlong handle) {
    if (handle == 0) return;
    nccl_ctx_t *ctx = (nccl_ctx_t *) handle;
    for (int r = 0; r < ctx->nRanks; r++) {
        if (ctx->comms[r]) ncclCommDestroy(ctx->comms[r]);
        cudaSetDevice(ctx->devices[r]);
        if (ctx->streams[r]) cudaStreamDestroy(ctx->streams[r]);
        if (ctx->sendBuf[r]) cudaFree(ctx->sendBuf[r]);
        if (ctx->recvBuf[r]) cudaFree(ctx->recvBuf[r]);
    }
    delete ctx;
}

// Copies each rank's slice of `flat` (elements [r*elems, r*elems+elems)) to that
// rank's device send buffer.
static bool scatterToSend(JNIEnv *env, nccl_ctx_t *ctx, jfloat *flat, int elemsPerRank) {
    size_t bytes = (size_t) elemsPerRank * sizeof(float);
    for (int r = 0; r < ctx->nRanks; r++) {
        if (!ensureSend(ctx, r, bytes)) return false;
        cudaSetDevice(ctx->devices[r]);
        if (cudaMemcpy(ctx->sendBuf[r], flat + (size_t) r * elemsPerRank, bytes, cudaMemcpyHostToDevice) != cudaSuccess) return false;
    }
    return true;
}

// Copies each rank's device recv buffer (elemsPerRank elements) back to `flat`.
static bool gatherFromRecv(JNIEnv *env, nccl_ctx_t *ctx, jfloat *flat, int elemsPerRank) {
    size_t bytes = (size_t) elemsPerRank * sizeof(float);
    for (int r = 0; r < ctx->nRanks; r++) {
        cudaSetDevice(ctx->devices[r]);
        if (cudaMemcpy(flat + (size_t) r * elemsPerRank, ctx->recvBuf[r], bytes, cudaMemcpyDeviceToHost) != cudaSuccess) return false;
    }
    return true;
}

static void syncAll(nccl_ctx_t *ctx) {
    for (int r = 0; r < ctx->nRanks; r++) {
        cudaSetDevice(ctx->devices[r]);
        cudaStreamSynchronize(ctx->streams[r]);
    }
}

/*
 * allReduce: recv[r] = op over all ranks of send[*], for each of `count` elements.
 * send/recv flat length = nRanks * count.
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_nccl_NcclNativeLib_allReduce
        (JNIEnv *env, jclass clazz, jlong handle, jfloatArray send, jfloatArray recv, jint count, jint op) {
    nccl_ctx_t *ctx = (nccl_ctx_t *) handle;
    jfloat *s = env->GetFloatArrayElements(send, nullptr);
    jfloat *d = env->GetFloatArrayElements(recv, nullptr);
    jint status = (jint) ncclInternalError;
    if (scatterToSend(env, ctx, s, count)) {
        for (int r = 0; r < ctx->nRanks; r++) ensureRecv(ctx, r, (size_t) count * sizeof(float));
        ncclGroupStart();
        for (int r = 0; r < ctx->nRanks; r++)
            ncclAllReduce(ctx->sendBuf[r], ctx->recvBuf[r], count, ncclFloat, redOp(op), ctx->comms[r], ctx->streams[r]);
        status = (jint) ncclGroupEnd();
        syncAll(ctx);
        gatherFromRecv(env, ctx, d, count);
    }
    env->ReleaseFloatArrayElements(send, s, JNI_ABORT);
    env->ReleaseFloatArrayElements(recv, d, 0);
    return status;
}

/*
 * broadcast: in place; the `root` rank's `count` elements are copied to all
 * ranks. buffers flat length = nRanks * count.
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_nccl_NcclNativeLib_broadcast
        (JNIEnv *env, jclass clazz, jlong handle, jfloatArray buffers, jint count, jint root) {
    nccl_ctx_t *ctx = (nccl_ctx_t *) handle;
    jfloat *b = env->GetFloatArrayElements(buffers, nullptr);
    jint status = (jint) ncclInternalError;
    if (scatterToSend(env, ctx, b, count)) {
        ncclGroupStart();
        for (int r = 0; r < ctx->nRanks; r++)
            ncclBroadcast(ctx->sendBuf[r], ctx->sendBuf[r], count, ncclFloat, root, ctx->comms[r], ctx->streams[r]);
        status = (jint) ncclGroupEnd();
        syncAll(ctx);
        // Copy each rank's (now-broadcast) send buffer back in place.
        for (int r = 0; r < ctx->nRanks; r++) {
            cudaSetDevice(ctx->devices[r]);
            cudaMemcpy(b + (size_t) r * count, ctx->sendBuf[r], (size_t) count * sizeof(float), cudaMemcpyDeviceToHost);
        }
    }
    env->ReleaseFloatArrayElements(buffers, b, 0);
    return status;
}

/*
 * reduce: recv[root] = op over all ranks; other ranks' recv is undefined.
 * send/recv flat length = nRanks * count.
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_nccl_NcclNativeLib_reduce
        (JNIEnv *env, jclass clazz, jlong handle, jfloatArray send, jfloatArray recv, jint count, jint root, jint op) {
    nccl_ctx_t *ctx = (nccl_ctx_t *) handle;
    jfloat *s = env->GetFloatArrayElements(send, nullptr);
    jfloat *d = env->GetFloatArrayElements(recv, nullptr);
    jint status = (jint) ncclInternalError;
    if (scatterToSend(env, ctx, s, count)) {
        for (int r = 0; r < ctx->nRanks; r++) ensureRecv(ctx, r, (size_t) count * sizeof(float));
        ncclGroupStart();
        for (int r = 0; r < ctx->nRanks; r++)
            ncclReduce(ctx->sendBuf[r], ctx->recvBuf[r], count, ncclFloat, redOp(op), root, ctx->comms[r], ctx->streams[r]);
        status = (jint) ncclGroupEnd();
        syncAll(ctx);
        gatherFromRecv(env, ctx, d, count);
    }
    env->ReleaseFloatArrayElements(send, s, JNI_ABORT);
    env->ReleaseFloatArrayElements(recv, d, 0);
    return status;
}

/*
 * allGather: each rank contributes `count` elements; every rank receives the
 * concatenation of all ranks (nRanks * count). send flat length = nRanks*count;
 * recv flat length = nRanks * (nRanks*count).
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_nccl_NcclNativeLib_allGather
        (JNIEnv *env, jclass clazz, jlong handle, jfloatArray send, jfloatArray recv, jint count) {
    nccl_ctx_t *ctx = (nccl_ctx_t *) handle;
    jfloat *s = env->GetFloatArrayElements(send, nullptr);
    jfloat *d = env->GetFloatArrayElements(recv, nullptr);
    jint status = (jint) ncclInternalError;
    int total = ctx->nRanks * count;
    if (scatterToSend(env, ctx, s, count)) {
        for (int r = 0; r < ctx->nRanks; r++) ensureRecv(ctx, r, (size_t) total * sizeof(float));
        ncclGroupStart();
        for (int r = 0; r < ctx->nRanks; r++)
            ncclAllGather(ctx->sendBuf[r], ctx->recvBuf[r], count, ncclFloat, ctx->comms[r], ctx->streams[r]);
        status = (jint) ncclGroupEnd();
        syncAll(ctx);
        gatherFromRecv(env, ctx, d, total);
    }
    env->ReleaseFloatArrayElements(send, s, JNI_ABORT);
    env->ReleaseFloatArrayElements(recv, d, 0);
    return status;
}

/*
 * reduceScatter: each rank contributes nRanks*count elements; rank r receives
 * the reduction of block r (count elements). send flat length = nRanks*(nRanks*count);
 * recv flat length = nRanks * count.
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_nccl_NcclNativeLib_reduceScatter
        (JNIEnv *env, jclass clazz, jlong handle, jfloatArray send, jfloatArray recv, jint count, jint op) {
    nccl_ctx_t *ctx = (nccl_ctx_t *) handle;
    jfloat *s = env->GetFloatArrayElements(send, nullptr);
    jfloat *d = env->GetFloatArrayElements(recv, nullptr);
    jint status = (jint) ncclInternalError;
    int total = ctx->nRanks * count;
    if (scatterToSend(env, ctx, s, total)) {
        for (int r = 0; r < ctx->nRanks; r++) ensureRecv(ctx, r, (size_t) count * sizeof(float));
        ncclGroupStart();
        for (int r = 0; r < ctx->nRanks; r++)
            ncclReduceScatter(ctx->sendBuf[r], ctx->recvBuf[r], count, ncclFloat, redOp(op), ctx->comms[r], ctx->streams[r]);
        status = (jint) ncclGroupEnd();
        syncAll(ctx);
        gatherFromRecv(env, ctx, d, count);
    }
    env->ReleaseFloatArrayElements(send, s, JNI_ABORT);
    env->ReleaseFloatArrayElements(recv, d, 0);
    return status;
}

/*
 * Class:     uk_ac_manchester_tornado_nccl_NcclNativeLib
 * Method:    errorString
 * Signature: (I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_uk_ac_manchester_tornado_nccl_NcclNativeLib_errorString
        (JNIEnv *env, jclass clazz, jint status) {
    return env->NewStringUTF(ncclGetErrorString((ncclResult_t) status));
}

} // extern "C"
