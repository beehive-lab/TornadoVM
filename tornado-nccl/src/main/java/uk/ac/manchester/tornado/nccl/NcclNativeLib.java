/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
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
package uk.ac.manchester.tornado.nccl;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;

/**
 * JNI bindings to libtornado-nccl (NVIDIA NCCL multi-GPU collectives, FP32).
 * Collectives are host-staged: the flat per-rank {@code float[]} is copied to
 * per-rank device buffers, the collective runs inside an {@code ncclGroup} on
 * per-rank streams, and results are copied back. Status codes are
 * {@code ncclResult_t} ordinals ({@code 0 == ncclSuccess}).
 */
final class NcclNativeLib {

    private static boolean loaded = false;

    private NcclNativeLib() {
    }

    static synchronized void load() {
        if (!loaded) {
            try {
                System.loadLibrary("tornado-nccl");
                loaded = true;
            } catch (UnsatisfiedLinkError e) {
                throw new TornadoRuntimeException("[ERROR] Unable to load libtornado-nccl. Build TornadoVM with the CUDA backend and install NCCL (set NCCL_ROOT): " + e.getMessage());
            }
        }
    }

    /** ncclCommInitAll over the given CUDA device ids; returns an opaque handle, 0 on failure. */
    static native long create(int[] deviceIds);

    static native void destroy(long handle);

    static native int allReduce(long handle, float[] send, float[] recv, int count, int op);

    static native int broadcast(long handle, float[] buffers, int count, int root);

    static native int reduce(long handle, float[] send, float[] recv, int count, int root, int op);

    static native int allGather(long handle, float[] send, float[] recv, int count);

    static native int reduceScatter(long handle, float[] send, float[] recv, int count, int op);

    static native String errorString(int status);

    static void check(int status, String op) {
        if (status != 0) {
            throw new TornadoRuntimeException("[ERROR] NCCL " + op + " failed: " + errorString(status));
        }
    }
}
