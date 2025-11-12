/*
 * MIT License
 *
 * Copyright (c) 2025, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#include "nvml_dynamic_loader.h"

#ifdef NVML_IS_SUPPORTED

#include <iostream>
#include <cstring>

// Global NVML loader instance
NVMLLoader g_nvmlLoader = {nullptr, nullptr, nullptr, nullptr, nullptr, false};

bool initNVMLLoader() {
    // Already loaded?
    if (g_nvmlLoader.loaded) {
        return true;
    }

    // Try to load the NVML library
    g_nvmlLoader.handle = NVML_LIB_LOAD(NVML_LIB_NAME);
    if (!g_nvmlLoader.handle) {
        std::cerr << "TornadoVM-OpenCL: Could not load " << NVML_LIB_NAME
                  << " - NVIDIA power metrics will not be available" << std::endl;

        #ifdef _WIN32
        // On Windows, try alternative paths
        g_nvmlLoader.handle = NVML_LIB_LOAD("C:\\Windows\\System32\\nvml.dll");
        if (!g_nvmlLoader.handle) {
            // Try CUDA toolkit path
            const char* cudaPath = getenv("CUDA_PATH");
            if (cudaPath) {
                char fullPath[512];
                snprintf(fullPath, sizeof(fullPath), "%s\\bin\\nvml.dll", cudaPath);
                g_nvmlLoader.handle = NVML_LIB_LOAD(fullPath);
            }
        }
        #elif defined(__linux__)
        // On Linux, try alternative library names
        if (!g_nvmlLoader.handle) {
            g_nvmlLoader.handle = NVML_LIB_LOAD("libnvidia-ml.so");
        }
        #endif

        // Still couldn't load?
        if (!g_nvmlLoader.handle) {
            return false;
        }
    }

    // Resolve function pointers
    g_nvmlLoader.nvmlInit = (nvmlInit_t)NVML_LIB_GET_PROC(g_nvmlLoader.handle, "nvmlInit_v2");
    if (!g_nvmlLoader.nvmlInit) {
        // Try older version
        g_nvmlLoader.nvmlInit = (nvmlInit_t)NVML_LIB_GET_PROC(g_nvmlLoader.handle, "nvmlInit");
    }

    g_nvmlLoader.nvmlDeviceGetHandleByIndex = (nvmlDeviceGetHandleByIndex_t)
        NVML_LIB_GET_PROC(g_nvmlLoader.handle, "nvmlDeviceGetHandleByIndex_v2");
    if (!g_nvmlLoader.nvmlDeviceGetHandleByIndex) {
        // Try older version
        g_nvmlLoader.nvmlDeviceGetHandleByIndex = (nvmlDeviceGetHandleByIndex_t)
            NVML_LIB_GET_PROC(g_nvmlLoader.handle, "nvmlDeviceGetHandleByIndex");
    }

    g_nvmlLoader.nvmlDeviceGetPowerUsage = (nvmlDeviceGetPowerUsage_t)
        NVML_LIB_GET_PROC(g_nvmlLoader.handle, "nvmlDeviceGetPowerUsage");

    g_nvmlLoader.nvmlShutdown = (nvmlShutdown_t)
        NVML_LIB_GET_PROC(g_nvmlLoader.handle, "nvmlShutdown");

    // Check if all required functions were loaded
    if (!g_nvmlLoader.nvmlInit ||
        !g_nvmlLoader.nvmlDeviceGetHandleByIndex ||
        !g_nvmlLoader.nvmlDeviceGetPowerUsage) {
        std::cerr << "TornadoVM-OpenCL: Failed to resolve NVML functions - "
                  << "NVIDIA power metrics will not be available" << std::endl;
        NVML_LIB_CLOSE(g_nvmlLoader.handle);
        g_nvmlLoader.handle = nullptr;
        return false;
    }

    g_nvmlLoader.loaded = true;
    std::cout << "TornadoVM-OpenCL: NVML loaded successfully - "
              << "NVIDIA power metrics are available" << std::endl;
    return true;
}

void cleanupNVMLLoader() {
    if (g_nvmlLoader.handle) {
        // Shutdown NVML if it was initialized
        if (g_nvmlLoader.loaded && g_nvmlLoader.nvmlShutdown) {
            g_nvmlLoader.nvmlShutdown();
        }

        NVML_LIB_CLOSE(g_nvmlLoader.handle);
        g_nvmlLoader.handle = nullptr;
        g_nvmlLoader.loaded = false;
    }
}

#endif // NVML_IS_SUPPORTED
