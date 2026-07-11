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
package uk.ac.manchester.tornado.cutensor.provider;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;

/**
 * JNI bindings to libtornado-cutensor (NVIDIA cuTENSOR v2 contraction API).
 * Contractions use opaque plans (tensor descriptors + algorithm + workspace
 * size) created once per shape and cached on the Java side, so the workspace
 * can be allocated before CUDA graph capture starts. Status codes are
 * {@code cutensorStatus_t} ordinals ({@code 0 == CUTENSOR_STATUS_SUCCESS}).
 */
final class CutensorNativeLib {

    private static boolean loaded = false;

    private CutensorNativeLib() {
    }

    static synchronized void load() {
        if (!loaded) {
            try {
                System.loadLibrary("tornado-cutensor");
                loaded = true;
            } catch (UnsatisfiedLinkError e) {
                throw new TornadoRuntimeException("[ERROR] Unable to load libtornado-cutensor. Build TornadoVM with the CUDA backend and install cuTENSOR (set CUTENSOR_ROOT): " + e.getMessage());
            }
        }
    }

    static native long cutensorCreateHandle();

    static native void cutensorDestroyHandle(long handle);

    /**
     * Builds a row-major FP32 contraction plan. {@code modes*} are int mode
     * labels, {@code extent*} the matching extents. Returns an opaque plan
     * pointer, or 0 on failure.
     */
    static native long createContractionPlan(long handle, int[] modesA, long[] extentA, int[] modesB, long[] extentB, int[] modesC, long[] extentC);

    static native long planWorkspaceBytes(long planPtr);

    /** D = alpha*(A contract B) + beta*C (C and D aliased). Returns cutensorStatus_t. */
    static native int contract(long handle, long planPtr, float alpha, long dA, long dB, float beta, long dC, long workspace, long workspaceSize, long stream);

    static native void destroyPlan(long planPtr);

    static native long allocateDeviceMemory(long bytes);

    static native int freeDeviceMemory(long ptr);

    static native String statusString(int status);

    static void checkStatus(int status, String function) {
        if (status != 0) {
            throw new TornadoRuntimeException("[ERROR] " + function + " failed with cuTENSOR status: " + statusString(status));
        }
    }
}
