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

#ifndef TORNADO_BACKEND_H
#define TORNADO_BACKEND_H

#include <stdint.h>
#include <memory>

#include "tornado_context.h"

class TornadoBackendOperations {
public:
    virtual ~TornadoBackendOperations() = default;

    virtual int allocate(int64_t bytes, int32_t access, int64_t *handle) = 0;
    virtual int prepareReusedAllocation(int64_t handle, int64_t bytes, int32_t access) = 0;
    virtual int release(int64_t handle) = 0;
    virtual int copy(bool toDevice, bool blocking, int64_t buffer, int64_t deviceOffset,
                     int64_t bytes, int64_t hostPointer, int64_t hostOffset) = 0;
    virtual int setKernelArgument(int64_t kernel, int32_t index, int32_t kind, const void *value, int64_t bytes) = 0;
    virtual int launchKernel(int64_t kernel, int32_t dimensions, const int64_t *globalOffset,
                             const int64_t *globalWork, const int64_t *localWork) = 0;
    virtual int flush() = 0;
};

std::unique_ptr<TornadoBackendOperations> tornado_create_backend(int32_t backend, int64_t queue, int64_t context);

#endif
