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
 */

// Native CUDA reference for HostMemoryBenchmark (TornadoVM): the same cases, the same CSV rows
// (impl,label,case,bytes,median_ms,GBps), for pageable, registered, pinned and mapped host memory.
//
//   nvcc -O3 -arch=native -o host_memory_bandwidth host_memory_bandwidth.cu
//   ./host_memory_bandwidth nopin|registered|pinned|mapped
//
// Label correspondence with TornadoVM: nopin = pageable with -Dtornado.cuda.host.pinning=false,
// registered = TornadoVM's default pageable arrays (registered on first use), pinned = PINNED,
// mapped = MAPPED.

#include <algorithm>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#define CK(x)                                                                                                   \
    do {                                                                                                        \
        cudaError_t e = (x);                                                                                    \
        if (e != cudaSuccess) {                                                                                 \
            std::fprintf(stderr, "%s:%d %s: %s\n", __FILE__, __LINE__, #x, cudaGetErrorString(e));             \
            std::exit(1);                                                                                       \
        }                                                                                                       \
    } while (0)

static const int WARMUP = 5;
static const int ITERATIONS = 20;
static const int REREADS = 16;
static const int REDUCE_CHUNK = 1024;
static const int SPARSE_STRIDE = 1024;
static std::string label;
static bool registered, pinned, mapped;

__global__ void touch(const float *x, float *out, int n) {
    if (blockIdx.x * blockDim.x + threadIdx.x == 0) out[0] = x[0] + x[n - 1];
}
__global__ void fill(float *out, int n) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i < n) out[i] = (float) i;
}
__global__ void stream(const float *x, float *y, int n) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i < n) y[i] = x[i] * 2.0f;
}
__global__ void saxpy(const float *x, const float *y, float *out, int n) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i < n) out[i] = 2.0f * x[i] + y[i];
}
__global__ void reduce_once(const float *x, float *partial, int chunks) {
    int c = blockIdx.x * blockDim.x + threadIdx.x;
    if (c < chunks) {
        float sum = 0.0f;
        for (int k = 0; k < REDUCE_CHUNK; k++) sum += x[k * chunks + c];  // coalesced across threads
        partial[c] = sum;
    }
}
__global__ void sparse_read(const float *x, float *out, int count) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i < count) out[i] = x[(long long) i * SPARSE_STRIDE] * 2.0f;
}
__global__ void reread(const float *x, float *out, int n) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    int stride = n / REREADS;
    if (i < n) {
        float acc = 0.0f;
        for (int k = 0; k < REREADS; k++) acc += x[(i + k * stride) % n];
        out[i] = acc;
    }
}

static dim3 blocks(long n) { return dim3((unsigned) ((n + 255) / 256)); }

// Host buffer of the benchmark's memory type.
static float *host_alloc(size_t bytes, bool init = true) {
    float *p = nullptr;
    if (pinned || mapped) {
        CK(cudaHostAlloc((void **) &p, bytes, cudaHostAllocPortable | (mapped ? cudaHostAllocMapped : 0)));
    } else {
        p = (float *) std::malloc(bytes);
        if (registered) CK(cudaHostRegister(p, bytes, cudaHostRegisterPortable));
    }
    if (init) {
        for (size_t i = 0; i < bytes / sizeof(float); i++) p[i] = (float) (i % 97);
    }
    return p;
}
static void host_free(float *p) {
    if (pinned || mapped) {
        CK(cudaFreeHost(p));
    } else {
        if (registered) CK(cudaHostUnregister(p));
        std::free(p);
    }
}
// The device address a kernel uses for a host buffer: the buffer itself when mapped (zero-copy),
// otherwise a device copy.
static float *device_view(float *host, size_t bytes, bool upload) {
    if (mapped) {
        float *d;
        CK(cudaHostGetDevicePointer((void **) &d, host, 0));
        return d;
    }
    float *d;
    CK(cudaMalloc((void **) &d, bytes));
    if (upload) CK(cudaMemcpy(d, host, bytes, cudaMemcpyHostToDevice));
    return d;
}
static void device_release(float *d) {
    if (!mapped) CK(cudaFree(d));
}

static void row(const char *name, long long bytes, double ms) {
    std::printf("CSV,nvcc,%s,%s,%lld,%.4f,%.2f\n", label.c_str(), name, bytes, ms, ms > 0 ? bytes / (ms * 1e6) : 0.0);
}

template <typename F> static double median_event_ms(F body) {
    cudaEvent_t a, b;
    CK(cudaEventCreate(&a));
    CK(cudaEventCreate(&b));
    std::vector<double> samples;
    for (int i = 0; i < WARMUP + ITERATIONS; i++) {
        CK(cudaEventRecord(a));
        body();
        CK(cudaEventRecord(b));
        CK(cudaEventSynchronize(b));
        float ms;
        CK(cudaEventElapsedTime(&ms, a, b));
        if (i >= WARMUP) samples.push_back(ms);
    }
    std::sort(samples.begin(), samples.end());
    return samples[ITERATIONS / 2];
}
template <typename F> static double median_wall_ms(F body) {
    std::vector<double> samples;
    for (int i = 0; i < WARMUP + ITERATIONS; i++) {
        auto start = std::chrono::steady_clock::now();
        body();
        CK(cudaDeviceSynchronize());
        double ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - start).count();
        if (i >= WARMUP) samples.push_back(ms);
    }
    std::sort(samples.begin(), samples.end());
    return samples[ITERATIONS / 2];
}

int main(int argc, char **argv) {
    label = argc > 1 ? argv[1] : "nopin";
    registered = label == "registered";
    pinned = label == "pinned";
    mapped = label == "mapped";
    CK(cudaSetDeviceFlags(cudaDeviceMapHost));
    CK(cudaFree(0));
    std::printf("CSV,impl,label,case,bytes,median_ms,GBps\n");

    const long long sizes_mb[] = {1, 4, 16, 64, 256, 1024};
    for (long long mb : sizes_mb) {
        size_t bytes = (size_t) mb << 20;
        int n = (int) (bytes / sizeof(float));
        float *x = host_alloc(bytes);
        float *d_small, *d_y;
        CK(cudaMalloc((void **) &d_small, sizeof(float)));
        CK(cudaMalloc((void **) &d_y, bytes));
        if (!mapped) {
            float *d_x;
            CK(cudaMalloc((void **) &d_x, bytes));
            row("h2d", bytes, median_event_ms([&] { CK(cudaMemcpyAsync(d_x, x, bytes, cudaMemcpyHostToDevice)); }));
            row("d2h", bytes, median_event_ms([&] { CK(cudaMemcpyAsync(x, d_x, bytes, cudaMemcpyDeviceToHost)); }));
            CK(cudaFree(d_x));
        }
        float *view = device_view(x, bytes, true);
        row("zc_read", bytes, median_event_ms([&] { stream<<<blocks(n), 256>>>(view, d_y, n); }));
        device_release(view);
        CK(cudaFree(d_small));
        CK(cudaFree(d_y));
        host_free(x);
    }

    size_t bytes = (size_t) 256 << 20;
    int n = (int) (bytes / sizeof(float));
    int chunks = n / REDUCE_CHUNK;
    float *x = host_alloc(bytes), *y = host_alloc(bytes), *out = host_alloc(bytes), *partial = host_alloc(chunks * sizeof(float));
    // End to end, per iteration: upload the inputs, run, read the output back (nothing to copy when mapped).
    float *dx = device_view(x, bytes, false), *dy = device_view(y, bytes, false), *dout = device_view(out, bytes, false);
    float *dpartial = device_view(partial, chunks * sizeof(float), false);
    row("saxpy", 3LL * bytes, median_wall_ms([&] {
        if (!mapped) {
            CK(cudaMemcpyAsync(dx, x, bytes, cudaMemcpyHostToDevice));
            CK(cudaMemcpyAsync(dy, y, bytes, cudaMemcpyHostToDevice));
        }
        saxpy<<<blocks(n), 256>>>(dx, dy, dout, n);
        if (!mapped) CK(cudaMemcpyAsync(out, dout, bytes, cudaMemcpyDeviceToHost));
    }));
    row("reduce_once", bytes, median_wall_ms([&] {
        if (!mapped) CK(cudaMemcpyAsync(dx, x, bytes, cudaMemcpyHostToDevice));
        reduce_once<<<blocks(chunks), 256>>>(dx, dpartial, chunks);
        if (!mapped) CK(cudaMemcpyAsync(partial, dpartial, chunks * sizeof(float), cudaMemcpyDeviceToHost));
    }));
    row("reread16", 2LL * bytes, median_wall_ms([&] {
        if (!mapped) CK(cudaMemcpyAsync(dx, x, bytes, cudaMemcpyHostToDevice));
        reread<<<blocks(n), 256>>>(dx, dout, n);
        if (!mapped) CK(cudaMemcpyAsync(out, dout, bytes, cudaMemcpyDeviceToHost));
    }));
    device_release(dx);
    device_release(dy);
    device_release(dout);
    device_release(dpartial);

    // Sparse access to a large array: copies move all of it, zero-copy only what is read.
    {
        size_t big_bytes = (size_t) 1024 << 20;
        int count = (int) (big_bytes / sizeof(float) / SPARSE_STRIDE);
        float *big = host_alloc(big_bytes), *picked = (float *) std::malloc(count * sizeof(float));
        float *dbig = device_view(big, big_bytes, false), *dpicked;
        CK(cudaMalloc((void **) &dpicked, count * sizeof(float)));
        row("sparse_read", big_bytes, median_wall_ms([&] {
            if (!mapped) CK(cudaMemcpyAsync(dbig, big, big_bytes, cudaMemcpyHostToDevice));
            sparse_read<<<blocks(count), 256>>>(dbig, dpicked, count);
            CK(cudaMemcpy(picked, dpicked, count * sizeof(float), cudaMemcpyDeviceToHost));
        }));
        device_release(dbig);
        CK(cudaFree(dpicked));
        std::free(picked);
        host_free(big);
    }

    // Allocation and first touch (the same memset as the Java side's fill).
    std::vector<double> samples;
    for (int i = 0; i < ITERATIONS; i++) {
        auto start = std::chrono::steady_clock::now();
        float *a = host_alloc(bytes, false);
        std::memset(a, 1, bytes);
        double ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - start).count();
        samples.push_back(ms);
        host_free(a);
    }
    std::sort(samples.begin(), samples.end());
    row("alloc", bytes, samples[ITERATIONS / 2]);
    host_free(x);
    host_free(y);
    host_free(out);
    host_free(partial);
    return 0;
}
