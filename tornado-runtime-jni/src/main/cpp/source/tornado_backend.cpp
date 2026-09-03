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

#include "tornado_backend.h"

#include <stddef.h>
#include <stdint.h>

#ifdef _WIN32
#include <windows.h>
#else
#include <dlfcn.h>
#endif

namespace {

// Resolves the driver symbol called by the equivalent Java JNI wrapper.
void *lookup(const char *name) {
#ifdef _WIN32
    const char *modules[] = { "OpenCL.dll", "nvcuda.dll", "tornado-cuda.dll", "tornado-objc-metal.dll", nullptr };
    for (int i = 0; modules[i] != nullptr; i++) {
        HMODULE module = GetModuleHandleA(modules[i]);
        if (module != nullptr) {
            void *symbol = reinterpret_cast<void *>(GetProcAddress(module, name));
            if (symbol != nullptr) {
                return symbol;
            }
        }
    }
    return nullptr;
#else
    return dlsym(RTLD_DEFAULT, name);
#endif
}

// Mirrors the Access-to-buffer-flags mapping in the Java OpenCL buffer provider.
uint64_t opencl_flags(int32_t access) {
    switch (access) {
        case 1: return 1ULL << 2; // READ_ONLY
        case 2: return 1ULL << 1; // WRITE_ONLY
        default: return 1ULL << 0; // NONE / READ_WRITE
    }
}

class OpenCLBackendOperations final : public TornadoBackendOperations {
    using CreateBuffer = void *(*)(void *, uint64_t, size_t, void *, int *);
    using ReleaseBuffer = int (*)(void *);
    using EnqueueBuffer = int (*)(void *, void *, unsigned, size_t, size_t, void *, unsigned, const void *, void *);
    using SetKernelArgument = int (*)(void *, unsigned, size_t, const void *);
    using EnqueueKernel = int (*)(void *, void *, unsigned, const size_t *, const size_t *, const size_t *, unsigned, const void *, void *);
    using Flush = int (*)(void *);

    int64_t queue_;
    int64_t context_;
    CreateBuffer create_ = nullptr;
    ReleaseBuffer release_ = nullptr;
    EnqueueBuffer write_ = nullptr;
    EnqueueBuffer read_ = nullptr;
    SetKernelArgument setArgument_ = nullptr;
    EnqueueKernel launch_ = nullptr;
    Flush flush_ = nullptr;

public:
    // Binds the native equivalent of Java's OpenCL context and command queue.
    OpenCLBackendOperations(int64_t queue, int64_t context) : queue_(queue), context_(context) {
        static CreateBuffer create = reinterpret_cast<CreateBuffer>(lookup("clCreateBuffer"));
        static ReleaseBuffer release = reinterpret_cast<ReleaseBuffer>(lookup("clReleaseMemObject"));
        static EnqueueBuffer write = reinterpret_cast<EnqueueBuffer>(lookup("clEnqueueWriteBuffer"));
        static EnqueueBuffer read = reinterpret_cast<EnqueueBuffer>(lookup("clEnqueueReadBuffer"));
        static SetKernelArgument setArgument = reinterpret_cast<SetKernelArgument>(lookup("clSetKernelArg"));
        static EnqueueKernel launch = reinterpret_cast<EnqueueKernel>(lookup("clEnqueueNDRangeKernel"));
        static Flush flush = reinterpret_cast<Flush>(lookup("clFlush"));
        create_ = create;
        release_ = release;
        write_ = write;
        read_ = read;
        setArgument_ = setArgument;
        launch_ = launch;
        flush_ = flush;
    }

    // Java counterpart: OCLBufferProvider.allocateBuffer().
    int allocate(int64_t bytes, int32_t access, int64_t *handle) override {
        if (create_ == nullptr || context_ == 0 || bytes <= 0 || handle == nullptr) {
            return TORNADO_COPY_UNSUPPORTED;
        }
        int status = 0;
        void *buffer = create_(reinterpret_cast<void *>(static_cast<uintptr_t>(context_)), opencl_flags(access), static_cast<size_t>(bytes), nullptr, &status);
        if (status != 0 || buffer == nullptr) {
            return TORNADO_COPY_FAILED;
        }
        *handle = static_cast<int64_t>(reinterpret_cast<uintptr_t>(buffer));
        return TORNADO_COPY_OK;
    }

    // Java OpenCL buffer reuse needs no extra driver operation.
    int prepareReusedAllocation(int64_t, int64_t, int32_t) override { return TORNADO_COPY_OK; }

    // Java counterpart: OCLBufferProvider.releaseBuffer().
    int release(int64_t handle) override {
        if (release_ == nullptr || handle == 0) {
            return TORNADO_COPY_UNSUPPORTED;
        }
        return release_(reinterpret_cast<void *>(static_cast<uintptr_t>(handle))) == 0 ? TORNADO_COPY_OK : TORNADO_COPY_FAILED;
    }

    // Java counterpart: OCLDeviceContext enqueueWriteBuffer/enqueueReadBuffer.
    int copy(bool toDevice, bool blocking, int64_t buffer, int64_t deviceOffset, int64_t bytes,
             int64_t hostPointer, int64_t hostOffset) override {
        EnqueueBuffer fn = toDevice ? write_ : read_;
        if (fn == nullptr || queue_ == 0 || buffer == 0 || hostPointer == 0 || bytes <= 0) {
            return TORNADO_COPY_UNSUPPORTED;
        }
        void *host = reinterpret_cast<void *>(static_cast<uintptr_t>(hostPointer + hostOffset));
        int status = fn(reinterpret_cast<void *>(static_cast<uintptr_t>(queue_)),
                        reinterpret_cast<void *>(static_cast<uintptr_t>(buffer)), blocking ? 1u : 0u,
                        static_cast<size_t>(deviceOffset), static_cast<size_t>(bytes), host, 0, nullptr, nullptr);
        return status == 0 ? TORNADO_COPY_OK : TORNADO_COPY_FAILED;
    }

    int setKernelArgument(int64_t kernel, int32_t index, int32_t kind, const void *value, int64_t bytes) override {
        if (setArgument_ == nullptr || kernel == 0 || index < 0 || bytes <= 0) return TORNADO_COPY_UNSUPPORTED;
        const void *argument = kind == TORNADO_KERNEL_ARG_LOCAL ? nullptr : value;
        return setArgument_(reinterpret_cast<void *>(static_cast<uintptr_t>(kernel)), static_cast<unsigned>(index), static_cast<size_t>(bytes), argument) == 0
                ? TORNADO_COPY_OK : TORNADO_COPY_FAILED;
    }

    int launchKernel(int64_t kernel, int32_t dimensions, const int64_t *globalOffset, const int64_t *globalWork, const int64_t *localWork) override {
        if (launch_ == nullptr || queue_ == 0 || kernel == 0 || dimensions < 1 || dimensions > 3 || globalWork == nullptr) return TORNADO_COPY_UNSUPPORTED;
        size_t offset[3] = { 0, 0, 0 };
        size_t global[3] = { 1, 1, 1 };
        size_t local[3] = { 1, 1, 1 };
        for (int32_t i = 0; i < dimensions; i++) {
            offset[i] = globalOffset == nullptr ? 0 : static_cast<size_t>(globalOffset[i]);
            global[i] = static_cast<size_t>(globalWork[i]);
            if (localWork != nullptr) local[i] = static_cast<size_t>(localWork[i]);
        }
        const int status = launch_(reinterpret_cast<void *>(static_cast<uintptr_t>(queue_)), reinterpret_cast<void *>(static_cast<uintptr_t>(kernel)), static_cast<unsigned>(dimensions), offset, global,
                localWork == nullptr ? nullptr : local, 0, nullptr, nullptr);
        return status == 0 ? TORNADO_COPY_OK : TORNADO_COPY_FAILED;
    }

    int flush() override {
        if (flush_ == nullptr || queue_ == 0) return TORNADO_COPY_UNSUPPORTED;
        return flush_(reinterpret_cast<void *>(static_cast<uintptr_t>(queue_))) == 0 ? TORNADO_COPY_OK : TORNADO_COPY_FAILED;
    }
};

class CUDABackendOperations final : public TornadoBackendOperations {
    using SetContext = int (*)(void *);
    using Allocate = int (*)(uint64_t *, size_t);
    using Release = int (*)(uint64_t);
    using CopyHtoD = int (*)(uint64_t, const void *, size_t, void *);
    using CopyDtoH = int (*)(void *, uint64_t, size_t, void *);
    using Synchronize = int (*)(void *);
    using Memset = int (*)(uint64_t, unsigned char, size_t);
    using SetKernelArgument = int (*)(int64_t, int32_t, int32_t, const void *, int64_t);
    using LaunchKernel = int (*)(int64_t, int64_t, int64_t, int32_t, const int64_t *, const int64_t *, const int64_t *);

    int64_t queue_;
    int64_t context_;
    SetContext setContext_ = nullptr;
    Allocate allocate_ = nullptr;
    Release release_ = nullptr;
    CopyHtoD h2d_ = nullptr;
    CopyDtoH d2h_ = nullptr;
    Synchronize synchronize_ = nullptr;
    Memset memset_ = nullptr;
    SetKernelArgument setArgument_ = nullptr;
    LaunchKernel launch_ = nullptr;

    // Mirrors CUDAContext.enableContext() before a Java CUDA driver call.
    bool setContext() const {
        return context_ == 0 || (setContext_ != nullptr && setContext_(reinterpret_cast<void *>(static_cast<uintptr_t>(context_))) == 0);
    }

public:
    // Binds the native equivalent of Java's CUDA context and stream.
    CUDABackendOperations(int64_t queue, int64_t context) : queue_(queue), context_(context) {
        static SetContext setContext = reinterpret_cast<SetContext>(lookup("cuCtxSetCurrent"));
        static Allocate allocate = [] {
            Allocate function = reinterpret_cast<Allocate>(lookup("cuMemAlloc_v2"));
            return function != nullptr ? function : reinterpret_cast<Allocate>(lookup("cuMemAlloc"));
        }();
        static Release release = [] {
            Release function = reinterpret_cast<Release>(lookup("cuMemFree_v2"));
            return function != nullptr ? function : reinterpret_cast<Release>(lookup("cuMemFree"));
        }();
        static CopyHtoD h2d = [] {
            CopyHtoD function = reinterpret_cast<CopyHtoD>(lookup("cuMemcpyHtoDAsync_v2"));
            return function != nullptr ? function : reinterpret_cast<CopyHtoD>(lookup("cuMemcpyHtoDAsync"));
        }();
        static CopyDtoH d2h = [] {
            CopyDtoH function = reinterpret_cast<CopyDtoH>(lookup("cuMemcpyDtoHAsync_v2"));
            return function != nullptr ? function : reinterpret_cast<CopyDtoH>(lookup("cuMemcpyDtoHAsync"));
        }();
        static Synchronize synchronize = reinterpret_cast<Synchronize>(lookup("cuStreamSynchronize"));
        static Memset memset = [] {
            Memset function = reinterpret_cast<Memset>(lookup("cuMemsetD8_v2"));
            return function != nullptr ? function : reinterpret_cast<Memset>(lookup("cuMemsetD8"));
        }();
        static SetKernelArgument setArgument = reinterpret_cast<SetKernelArgument>(lookup("tornado_cuda_set_kernel_argument"));
        static LaunchKernel launch = reinterpret_cast<LaunchKernel>(lookup("tornado_cuda_launch_kernel"));
        setContext_ = setContext;
        allocate_ = allocate;
        release_ = release;
        h2d_ = h2d;
        d2h_ = d2h;
        synchronize_ = synchronize;
        memset_ = memset;
        setArgument_ = setArgument;
        launch_ = launch;
    }

    // Java counterpart: CUDABufferProvider.allocateBuffer().
    int allocate(int64_t bytes, int32_t access, int64_t *handle) override {
        if (allocate_ == nullptr || bytes <= 0 || handle == nullptr || !setContext()) {
            return TORNADO_COPY_UNSUPPORTED;
        }
        uint64_t buffer = 0;
        if (allocate_(&buffer, static_cast<size_t>(bytes)) != 0 || buffer == 0) {
            return TORNADO_COPY_FAILED;
        }
        if (access == 2) {
            if (memset_ == nullptr || memset_(buffer, 0, static_cast<size_t>(bytes)) != 0) {
                if (release_ != nullptr) {
                    release_(buffer);
                }
                return TORNADO_COPY_FAILED;
            }
        }
        *handle = static_cast<int64_t>(buffer);
        return TORNADO_COPY_OK;
    }

    // Mirrors CUDABufferProvider zeroing a reused WRITE_ONLY buffer.
    int prepareReusedAllocation(int64_t handle, int64_t bytes, int32_t access) override {
        if (access != 2) return TORNADO_COPY_OK;
        if (handle == 0 || bytes <= 0 || memset_ == nullptr || !setContext()) return TORNADO_COPY_UNSUPPORTED;
        return memset_(static_cast<uint64_t>(handle), 0, static_cast<size_t>(bytes)) == 0 ? TORNADO_COPY_OK : TORNADO_COPY_FAILED;
    }

    // Java counterpart: CUDABufferProvider.releaseBuffer().
    int release(int64_t handle) override {
        if (release_ == nullptr || handle == 0 || !setContext()) {
            return TORNADO_COPY_UNSUPPORTED;
        }
        return release_(static_cast<uint64_t>(handle)) == 0 ? TORNADO_COPY_OK : TORNADO_COPY_FAILED;
    }

    // Java counterpart: CUDADeviceContext enqueueWriteBuffer/enqueueReadBuffer.
    int copy(bool toDevice, bool blocking, int64_t buffer, int64_t deviceOffset, int64_t bytes,
             int64_t hostPointer, int64_t hostOffset) override {
        if (queue_ == 0 || buffer == 0 || hostPointer == 0 || bytes <= 0 || !setContext()) {
            return TORNADO_COPY_UNSUPPORTED;
        }
        void *stream = reinterpret_cast<void *>(static_cast<uintptr_t>(queue_));
        void *host = reinterpret_cast<void *>(static_cast<uintptr_t>(hostPointer + hostOffset));
        const uint64_t device = static_cast<uint64_t>(buffer + deviceOffset);
        int status = toDevice ? (h2d_ == nullptr ? -1 : h2d_(device, host, static_cast<size_t>(bytes), stream))
                              : (d2h_ == nullptr ? -1 : d2h_(host, device, static_cast<size_t>(bytes), stream));
        if (status != 0) return TORNADO_COPY_FAILED;
        if (blocking) {
            if (synchronize_ == nullptr) return TORNADO_COPY_UNSUPPORTED;
            if (synchronize_(stream) != 0) return TORNADO_COPY_FAILED;
        }
        return TORNADO_COPY_OK;
    }

    int setKernelArgument(int64_t kernel, int32_t index, int32_t kind, const void *value, int64_t bytes) override {
        if (setArgument_ == nullptr || kernel == 0 || !setContext()) return TORNADO_COPY_UNSUPPORTED;
        return setArgument_(kernel, index, kind, value, bytes) == 0 ? TORNADO_COPY_OK : TORNADO_COPY_FAILED;
    }

    int launchKernel(int64_t kernel, int32_t dimensions, const int64_t *globalOffset, const int64_t *globalWork, const int64_t *localWork) override {
        if (launch_ == nullptr || queue_ == 0 || kernel == 0 || globalWork == nullptr || !setContext()) return TORNADO_COPY_UNSUPPORTED;
        return launch_(queue_, context_, kernel, dimensions, globalOffset, globalWork, localWork) == 0 ? TORNADO_COPY_OK : TORNADO_COPY_FAILED;
    }

    int flush() override {
        if (synchronize_ == nullptr || queue_ == 0 || !setContext()) return TORNADO_COPY_UNSUPPORTED;
        return synchronize_(reinterpret_cast<void *>(static_cast<uintptr_t>(queue_))) == 0 ? TORNADO_COPY_OK : TORNADO_COPY_FAILED;
    }
};

class MetalBackendOperations final : public TornadoBackendOperations {
    using Allocate = int (*)(int64_t, int64_t, int64_t *);
    using Release = int (*)(int64_t);
    using Copy = int (*)(int, int64_t, int64_t, int64_t, int64_t, int64_t);
    using SetKernelArgument = int (*)(int64_t, int32_t, int32_t, const void *, int64_t);
    using LaunchKernel = int (*)(int64_t, int64_t, int32_t, const int64_t *, const int64_t *, const int64_t *);

    int64_t queue_;
    int64_t context_;
    Allocate allocate_ = nullptr;
    Release release_ = nullptr;
    Copy copy_ = nullptr;
    SetKernelArgument setArgument_ = nullptr;
    LaunchKernel launch_ = nullptr;

public:
    // Binds the native equivalent of Java's Metal context.
    MetalBackendOperations(int64_t queue, int64_t context) : queue_(queue), context_(context) {
        static Allocate allocate = reinterpret_cast<Allocate>(lookup("tornado_metal_allocate"));
        static Release release = reinterpret_cast<Release>(lookup("tornado_metal_release"));
        static Copy copy = reinterpret_cast<Copy>(lookup("tornado_metal_copy"));
        static SetKernelArgument setArgument = reinterpret_cast<SetKernelArgument>(lookup("tornado_metal_set_kernel_argument"));
        static LaunchKernel launch = reinterpret_cast<LaunchKernel>(lookup("tornado_metal_launch_kernel"));
        allocate_ = allocate;
        release_ = release;
        copy_ = copy;
        setArgument_ = setArgument;
        launch_ = launch;
    }

    // Java counterpart: MetalBufferProvider.allocateBuffer().
    // Prefer the plan queue; Metal's "context" handle is also a command queue.
    int allocate(int64_t bytes, int32_t, int64_t *handle) override {
        const int64_t source = queue_ != 0 ? queue_ : context_;
        if (allocate_ == nullptr || source == 0 || bytes <= 0 || handle == nullptr) return TORNADO_COPY_UNSUPPORTED;
        return allocate_(source, bytes, handle) == 0 ? TORNADO_COPY_OK : TORNADO_COPY_FAILED;
    }

    // Java Metal buffer reuse needs no extra driver operation.
    int prepareReusedAllocation(int64_t, int64_t, int32_t) override { return TORNADO_COPY_OK; }

    // Java counterpart: MetalBufferProvider.releaseBuffer().
    int release(int64_t handle) override {
        if (release_ == nullptr || handle == 0) return TORNADO_COPY_UNSUPPORTED;
        return release_(handle) == 0 ? TORNADO_COPY_OK : TORNADO_COPY_FAILED;
    }

    // Java counterpart: MetalDeviceContext writeBuffer/readBuffer.
    int copy(bool toDevice, bool, int64_t buffer, int64_t deviceOffset, int64_t bytes,
             int64_t hostPointer, int64_t hostOffset) override {
        if (copy_ == nullptr || buffer == 0 || hostPointer == 0 || bytes <= 0) return TORNADO_COPY_UNSUPPORTED;
        return copy_(toDevice ? 1 : 0, buffer, deviceOffset, bytes, hostPointer, hostOffset) == 0 ? TORNADO_COPY_OK : TORNADO_COPY_FAILED;
    }

    int setKernelArgument(int64_t kernel, int32_t index, int32_t kind, const void *value, int64_t bytes) override {
        if (setArgument_ == nullptr || kernel == 0) return TORNADO_COPY_UNSUPPORTED;
        return setArgument_(kernel, index, kind, value, bytes) == 0 ? TORNADO_COPY_OK : TORNADO_COPY_FAILED;
    }

    int launchKernel(int64_t kernel, int32_t dimensions, const int64_t *globalOffset, const int64_t *globalWork, const int64_t *localWork) override {
        if (launch_ == nullptr || kernel == 0 || queue_ == 0 || globalWork == nullptr) return TORNADO_COPY_UNSUPPORTED;
        return launch_(queue_, kernel, dimensions, globalOffset, globalWork, localWork) == 0 ? TORNADO_COPY_OK : TORNADO_COPY_FAILED;
    }

    // Java Metal flush is intentionally a no-op; kernel launch itself is synchronous today.
    int flush() override { return TORNADO_COPY_OK; }
};

// Mirrors Java rejecting a backend that has no native interpreter implementation.
class UnsupportedBackendOperations final : public TornadoBackendOperations {
public:
    int allocate(int64_t, int32_t, int64_t *) override { return TORNADO_COPY_UNSUPPORTED; }
    int prepareReusedAllocation(int64_t, int64_t, int32_t) override { return TORNADO_COPY_UNSUPPORTED; }
    int release(int64_t) override { return TORNADO_COPY_UNSUPPORTED; }
    int copy(bool, bool, int64_t, int64_t, int64_t, int64_t, int64_t) override { return TORNADO_COPY_UNSUPPORTED; }
    int setKernelArgument(int64_t, int32_t, int32_t, const void *, int64_t) override { return TORNADO_COPY_UNSUPPORTED; }
    int launchKernel(int64_t, int32_t, const int64_t *, const int64_t *, const int64_t *) override { return TORNADO_COPY_UNSUPPORTED; }
    int flush() override { return TORNADO_COPY_UNSUPPORTED; }
};

} // namespace

// Java chooses the concrete TornadoDevice; this creates its native equivalent.
std::unique_ptr<TornadoBackendOperations> tornado_create_backend(int32_t backend, int64_t queue, int64_t context) {
    switch (backend) {
        case TORNADO_BACKEND_OPENCL: return std::unique_ptr<TornadoBackendOperations>(new OpenCLBackendOperations(queue, context));
        case TORNADO_BACKEND_METAL: return std::unique_ptr<TornadoBackendOperations>(new MetalBackendOperations(queue, context));
        case TORNADO_BACKEND_CUDA: return std::unique_ptr<TornadoBackendOperations>(new CUDABackendOperations(queue, context));
        default: return std::unique_ptr<TornadoBackendOperations>(new UnsupportedBackendOperations());
    }
}
