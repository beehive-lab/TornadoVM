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

#ifndef NVML_DYNAMIC_LOADER_H
#define NVML_DYNAMIC_LOADER_H

#ifdef NVML_IS_SUPPORTED

#include <nvml.h>

#ifdef _WIN32
    #include <windows.h>
    #define NVML_LIB_HANDLE HMODULE
    #define NVML_LIB_LOAD(path) LoadLibraryA(path)
    #define NVML_LIB_GET_PROC(handle, name) GetProcAddress(handle, name)
    #define NVML_LIB_CLOSE(handle) FreeLibrary(handle)
    #define NVML_LIB_NAME "nvml.dll"
#elif defined(__APPLE__)
    #include <dlfcn.h>
    #define NVML_LIB_HANDLE void*
    #define NVML_LIB_LOAD(path) dlopen(path, RTLD_NOW | RTLD_LOCAL)
    #define NVML_LIB_GET_PROC(handle, name) dlsym(handle, name)
    #define NVML_LIB_CLOSE(handle) dlclose(handle)
    #define NVML_LIB_NAME "libnvidia-ml.dylib"
#else
    #include <dlfcn.h>
    #define NVML_LIB_HANDLE void*
    #define NVML_LIB_LOAD(path) dlopen(path, RTLD_NOW | RTLD_LOCAL)
    #define NVML_LIB_GET_PROC(handle, name) dlsym(handle, name)
    #define NVML_LIB_CLOSE(handle) dlclose(handle)
    #define NVML_LIB_NAME "libnvidia-ml.so.1"
#endif

// Function pointer types for NVML functions
typedef nvmlReturn_t (*nvmlInit_t)(void);
typedef nvmlReturn_t (*nvmlDeviceGetHandleByIndex_t)(unsigned int index, nvmlDevice_t *device);
typedef nvmlReturn_t (*nvmlDeviceGetPowerUsage_t)(nvmlDevice_t device, unsigned int *power);
typedef nvmlReturn_t (*nvmlShutdown_t)(void);

// Structure to hold NVML function pointers and library handle
struct NVMLLoader {
    NVML_LIB_HANDLE handle;
    nvmlInit_t nvmlInit;
    nvmlDeviceGetHandleByIndex_t nvmlDeviceGetHandleByIndex;
    nvmlDeviceGetPowerUsage_t nvmlDeviceGetPowerUsage;
    nvmlShutdown_t nvmlShutdown;
    bool loaded;
};

// Global NVML loader instance
extern NVMLLoader g_nvmlLoader;

// Initialize the NVML loader (load library and resolve function pointers)
bool initNVMLLoader();

// Check if NVML is available
inline bool isNVMLAvailable() {
    return g_nvmlLoader.loaded;
}

// Clean up NVML loader
void cleanupNVMLLoader();

#endif // NVML_IS_SUPPORTED

#endif // NVML_DYNAMIC_LOADER_H
