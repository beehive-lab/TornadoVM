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
package uk.ac.manchester.tornado.cudnn.provider;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;

/**
 * JNI bindings to libtornado-cudnn (FP32/NCHW legacy cuDNN API). Convolutions
 * use opaque plans (descriptors + algorithm + workspace size) created once per
 * shape; simple ops create their host-side descriptors inline.
 */
final class CuDnnNativeLib {

    /** cudnnActivationMode_t values. */
    static final int CUDNN_ACTIVATION_SIGMOID = 0;
    static final int CUDNN_ACTIVATION_RELU = 1;
    static final int CUDNN_ACTIVATION_TANH = 2;

    private static boolean loaded = false;

    private CuDnnNativeLib() {
    }

    static synchronized void load() {
        if (!loaded) {
            try {
                System.loadLibrary("tornado-cudnn");
                loaded = true;
            } catch (UnsatisfiedLinkError e) {
                throw new TornadoRuntimeException("[ERROR] Unable to load libtornado-cudnn. Build TornadoVM with the CUDA backend and ensure cuDNN is installed: " + e.getMessage());
            }
        }
    }

    static native long cudnnCreateHandle();

    static native int cudnnSetStream(long handle, long streamPtr);

    static native void cudnnDestroyHandle(long handle);

    static native long cudnnGetVersionNative();

    static native int softmaxForward(long handle, int n, int c, int h, int w, long dIn, long dOut);

    static native int activationForward(long handle, int mode, long size, long dIn, long dOut);

    static native int poolingMaxForward(long handle, int n, int c, int h, int w, int window, int stride, long dIn, long dOut);

    /** Returns an opaque plan pointer, or 0 on failure. */
    static native long createConvPlan(long handle, int n, int c, int h, int w, int k, int r, int s, int pad, int stride);

    static native long convPlanWorkspaceBytes(long planPtr);

    static native int convForward(long handle, long planPtr, long dIn, long dFilter, long dOut, long workspacePtr, long workspaceBytes);

    static native void destroyConvPlan(long planPtr);

    static native long allocateDeviceMemory(long bytes);

    static native void freeDeviceMemory(long ptr);

    static String decodeStatus(int status) {
        return switch (status) {
            case 0 -> "CUDNN_STATUS_SUCCESS";
            case 1 -> "CUDNN_STATUS_NOT_INITIALIZED";
            case 2 -> "CUDNN_STATUS_ALLOC_FAILED";
            case 3 -> "CUDNN_STATUS_BAD_PARAM";
            case 4 -> "CUDNN_STATUS_INTERNAL_ERROR";
            case 5 -> "CUDNN_STATUS_INVALID_VALUE";
            case 6 -> "CUDNN_STATUS_ARCH_MISMATCH";
            case 7 -> "CUDNN_STATUS_MAPPING_ERROR";
            case 8 -> "CUDNN_STATUS_EXECUTION_FAILED";
            case 9 -> "CUDNN_STATUS_NOT_SUPPORTED";
            default -> "UNKNOWN_CUDNN_STATUS (" + status + ")";
        };
    }

    static void checkStatus(int status, String function) {
        if (status != 0) {
            throw new TornadoRuntimeException("[ERROR] " + function + " failed with status: " + decodeStatus(status));
        }
    }
}
