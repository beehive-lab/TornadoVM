/*
 * MIT License
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
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

#import <Foundation/Foundation.h>
#import <Metal/Metal.h>

#include <mutex>
#include <string.h>
#include <unordered_map>
#include <vector>

#include "tornado_context.h"

namespace {

struct MetalArg {
    bool set = false;
    int32_t kind = TORNADO_KERNEL_ARG_VALUE;
    std::vector<char> bytes;
    int64_t buffer = 0;
    int64_t size = 0;
};

std::mutex g_argsMutex;
std::unordered_map<int64_t, std::vector<MetalArg>> g_kernelArgs;

std::vector<MetalArg> &slotList(int64_t kernel, int32_t index) {
    std::vector<MetalArg> &slots = g_kernelArgs[kernel];
    if ((int32_t) slots.size() <= index) {
        slots.resize((size_t) index + 1);
    }
    return slots;
}

} // namespace

extern "C" {

int tornado_metal_allocate(int64_t context, int64_t bytes, int64_t *handle) {
    if (context == 0 || bytes <= 0 || handle == nullptr) {
        return -1;
    }
    @autoreleasepool {
        id<MTLCommandQueue> queue = (id<MTLCommandQueue>) (void *) (uintptr_t) context;
        id<MTLBuffer> buffer = [[queue device] newBufferWithLength:(NSUInteger) bytes options:MTLResourceStorageModeShared];
        if (!buffer) {
            return -1;
        }
        *handle = (int64_t) (uintptr_t) buffer;
        return 0;
    }
}

int tornado_metal_release(int64_t handle) {
    if (handle == 0) {
        return -1;
    }
    @autoreleasepool {
        id<MTLBuffer> buffer = (id<MTLBuffer>) (void *) (uintptr_t) handle;
        [buffer release];
        return 0;
    }
}

int tornado_metal_copy(int toDevice, int64_t handle, int64_t deviceOffset, int64_t bytes, int64_t hostPointer, int64_t hostOffset) {
    if (handle == 0 || hostPointer == 0 || bytes <= 0 || deviceOffset < 0) {
        return -1;
    }
    @autoreleasepool {
        id<MTLBuffer> buffer = (id<MTLBuffer>) (void *) (uintptr_t) handle;
        if ((uint64_t) deviceOffset + (uint64_t) bytes > (uint64_t) [buffer length]) {
            return -1;
        }
        char *device = (char *) [buffer contents];
        char *host = (char *) (uintptr_t) hostPointer;
        if (device == nullptr || host == nullptr) {
            return -1;
        }
        if (toDevice) {
            memcpy(device + deviceOffset, host + hostOffset, (size_t) bytes);
        } else {
            memcpy(host + hostOffset, device + deviceOffset, (size_t) bytes);
        }
        return 0;
    }
}

int tornado_metal_set_kernel_argument(int64_t kernelId, int32_t index, int32_t kind, const void *value, int64_t size) {
    if (kernelId == 0 || index < 0 || size < 0 || (kind != TORNADO_KERNEL_ARG_LOCAL && value == nullptr)) {
        return -1;
    }
    std::lock_guard<std::mutex> lock(g_argsMutex);
    MetalArg &arg = slotList(kernelId, index)[(size_t) index];
    arg.set = true;
    arg.kind = kind;
    arg.size = size;
    arg.buffer = 0;
    arg.bytes.clear();
    if (kind == TORNADO_KERNEL_ARG_REFERENCE) {
        arg.buffer = *(const int64_t *) value;
        if (arg.buffer == 0) {
            arg.set = false;
            return -1;
        }
    } else if (kind == TORNADO_KERNEL_ARG_LOCAL) {
        arg.size = size;
    } else {
        arg.bytes.assign((const char *) value, (const char *) value + (size_t) size);
    }
    return 0;
}

int tornado_metal_launch_kernel(int64_t queueId, int64_t kernelId, int32_t dimensions, const int64_t *globalOffset, const int64_t *globalWork, const int64_t *localWork) {
    (void) globalOffset;
    if (queueId == 0 || kernelId == 0 || globalWork == nullptr || dimensions < 1 || dimensions > 3) {
        return -1;
    }

    std::vector<MetalArg> args;
    {
        std::lock_guard<std::mutex> lock(g_argsMutex);
        auto it = g_kernelArgs.find(kernelId);
        if (it != g_kernelArgs.end()) {
            args = it->second;
        }
    }

    @autoreleasepool {
        id<MTLCommandQueue> queue = (id<MTLCommandQueue>) (void *) (uintptr_t) queueId;
        id<MTLComputePipelineState> pipeline = (id<MTLComputePipelineState>) (void *) (uintptr_t) kernelId;
        if (!queue || !pipeline) {
            return -1;
        }

        NSUInteger gx = (NSUInteger) globalWork[0];
        NSUInteger gy = dimensions > 1 ? (NSUInteger) globalWork[1] : 1;
        NSUInteger gz = dimensions > 2 ? (NSUInteger) globalWork[2] : 1;
        if (gx == 0) {
            gx = 1;
        }

        NSUInteger lx;
        NSUInteger ly = 1;
        NSUInteger lz = 1;
        if (localWork != nullptr && localWork[0] > 0) {
            lx = (NSUInteger) localWork[0];
            ly = dimensions > 1 && localWork[1] > 0 ? (NSUInteger) localWork[1] : 1;
            lz = dimensions > 2 && localWork[2] > 0 ? (NSUInteger) localWork[2] : 1;
        } else {
            lx = [pipeline threadExecutionWidth];
        }
        NSUInteger maxPer = [pipeline maxTotalThreadsPerThreadgroup];
        if (lx == 0) {
            lx = 1;
        }
        if (maxPer > 0 && lx * ly * lz > maxPer) {
            lx = maxPer < lx ? maxPer : lx;
            ly = 1;
            lz = 1;
        }

        id<MTLCommandBuffer> commandBuffer = [queue commandBuffer];
        id<MTLComputeCommandEncoder> encoder = [commandBuffer computeCommandEncoder];
        [encoder setComputePipelineState:pipeline];

        for (NSUInteger i = 0; i < args.size(); i++) {
            const MetalArg &arg = args[i];
            if (!arg.set) {
                continue;
            }
            if (arg.kind == TORNADO_KERNEL_ARG_REFERENCE) {
                id<MTLBuffer> buffer = (id<MTLBuffer>) (void *) (uintptr_t) arg.buffer;
                if (buffer) {
                    [encoder setBuffer:buffer offset:0 atIndex:i];
                }
            } else if (arg.kind == TORNADO_KERNEL_ARG_LOCAL) {
                [encoder setThreadgroupMemoryLength:(NSUInteger) arg.size atIndex:i];
            } else if (arg.size > 0 && !arg.bytes.empty()) {
                [encoder setBytes:arg.bytes.data() length:(NSUInteger) arg.size atIndex:i];
            }
        }

        int sizes[3] = { (int) gx, (int) gy, (int) gz };
        id<MTLBuffer> sizesBuffer = [[queue device] newBufferWithBytes:sizes length:sizeof(sizes) options:MTLResourceStorageModeShared];
        if (sizesBuffer) {
            [encoder setBuffer:sizesBuffer offset:0 atIndex:args.size()];
        }

        [encoder dispatchThreads:MTLSizeMake(gx, gy, gz) threadsPerThreadgroup:MTLSizeMake(lx, ly, lz)];
        [encoder endEncoding];
        [commandBuffer commit];
        [commandBuffer waitUntilCompleted];
        [sizesBuffer release];
        return 0;
    }
}

}
