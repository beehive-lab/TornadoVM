/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package uk.ac.manchester.tornado.curand.provider;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;

final class CuRandNativeLib {

    private static boolean loaded = false;

    private CuRandNativeLib() {
    }

    static synchronized void load() {
        if (!loaded) {
            try {
                System.loadLibrary("tornado-curand");
                loaded = true;
            } catch (UnsatisfiedLinkError e) {
                throw new TornadoRuntimeException(
                        "[ERROR] Unable to load libtornado-curand. Build TornadoVM with the CUDA backend and ensure cuRAND is installed: " + e.getMessage());
            }
        }
    }

    /** Returns the generator handle, or 0 on failure. */
    static native long curandCreateGenerator(int rngType);

    static native void curandDestroyGenerator(long generator);

    static native int curandSetStream(long generator, long streamPtr);

    static native int curandSetPseudoRandomGeneratorSeed(long generator, long seed);

    static native int curandSetGeneratorOffset(long generator, long offset);

    static native int curandGenerateNormal(long generator, long devicePtr, long elementOffset, long n, float mean, float stddev);

    static native int curandGenerateNormalDouble(long generator, long devicePtr, long elementOffset, long n, double mean, double stddev);

    static native int curandGenerateUniform(long generator, long devicePtr, long elementOffset, long n);

    static native int curandGenerateUniformDouble(long generator, long devicePtr, long elementOffset, long n);

    static String decodeStatus(int status) {
        return switch (status) {
            case 0 -> "CURAND_STATUS_SUCCESS";
            case 100 -> "CURAND_STATUS_VERSION_MISMATCH";
            case 101 -> "CURAND_STATUS_NOT_INITIALIZED";
            case 102 -> "CURAND_STATUS_ALLOCATION_FAILED";
            case 103 -> "CURAND_STATUS_TYPE_ERROR";
            case 104 -> "CURAND_STATUS_OUT_OF_RANGE";
            case 105 -> "CURAND_STATUS_LENGTH_NOT_MULTIPLE";
            case 106 -> "CURAND_STATUS_DOUBLE_PRECISION_REQUIRED";
            case 201 -> "CURAND_STATUS_LAUNCH_FAILURE";
            case 202 -> "CURAND_STATUS_PREEXISTING_FAILURE";
            case 203 -> "CURAND_STATUS_INITIALIZATION_FAILED";
            case 204 -> "CURAND_STATUS_ARCH_MISMATCH";
            case 999 -> "CURAND_STATUS_INTERNAL_ERROR";
            default -> "CURAND_STATUS_" + status;
        };
    }

    static void checkStatus(int status, String what) {
        if (status != 0) {
            throw new TornadoRuntimeException("[ERROR] " + what + " failed: " + decodeStatus(status));
        }
    }
}
