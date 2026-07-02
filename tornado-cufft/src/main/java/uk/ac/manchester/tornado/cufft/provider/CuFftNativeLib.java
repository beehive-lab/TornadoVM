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
package uk.ac.manchester.tornado.cufft.provider;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;

/**
 * JNI bindings to libtornado-cufft. Plans are created per (n, batch) shape,
 * bound to the TornadoVM execution stream, and cached by the provider.
 */
final class CuFftNativeLib {

    private static boolean loaded = false;

    private CuFftNativeLib() {
    }

    static synchronized void load() {
        if (!loaded) {
            try {
                System.loadLibrary("tornado-cufft");
                loaded = true;
            } catch (UnsatisfiedLinkError e) {
                throw new TornadoRuntimeException("[ERROR] Unable to load libtornado-cufft. Build TornadoVM with the CUDA backend and ensure cuFFT is installed: " + e.getMessage());
            }
        }
    }

    /** Creates a 1D C2C FP32 plan for {@code batch} transforms of length n; returns 0 on failure. */
    static native long cufftPlan1dC2C(int n, int batch);

    static native int cufftSetStream(long plan, long streamPtr);

    static native int cufftExecC2C(long plan, long dIn, long dOut, int direction);

    static native void cufftDestroy(long plan);

    static String decodeResult(int result) {
        return switch (result) {
            case 0 -> "CUFFT_SUCCESS";
            case 1 -> "CUFFT_INVALID_PLAN";
            case 2 -> "CUFFT_ALLOC_FAILED";
            case 3 -> "CUFFT_INVALID_TYPE";
            case 4 -> "CUFFT_INVALID_VALUE";
            case 5 -> "CUFFT_INTERNAL_ERROR";
            case 6 -> "CUFFT_EXEC_FAILED";
            case 7 -> "CUFFT_SETUP_FAILED";
            case 8 -> "CUFFT_INVALID_SIZE";
            case 9 -> "CUFFT_UNALIGNED_DATA";
            default -> "UNKNOWN_CUFFT_RESULT (" + result + ")";
        };
    }

    static void checkResult(int result, String function) {
        if (result != 0) {
            throw new TornadoRuntimeException("[ERROR] " + function + " failed with result: " + decodeResult(result));
        }
    }
}
