/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * Phase 0 placeholder so the cuda-jni shared library builds and packages.
 * Phase 1 replaces this with the real CUDA Driver API + NVRTC JNI sources
 * (CUDA.cpp, CUDAPlatform.cpp, CUDADevice.cpp, CUDAContext.cpp, CUDAStream.cpp,
 * CUDAModule.cpp, CUDAEvent.cpp, cuda_utils.cpp).
 */

extern "C" {

// Intentionally empty translation unit. Kept so CMake's source glob is
// non-empty and libtornado-cuda links cleanly during Phase 0.
int tornado_cuda_jni_placeholder() {
    return 0;
}

}
