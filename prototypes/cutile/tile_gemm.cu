#include "cuda_tile.h"
#include <cuda_fp16.h>

// TornadoVM's CUDA ABI: 4 reserved slots, then buffers as unsigned char*, then primitives.
__tile_global__ void gemm(unsigned char *_kernel_context, unsigned char *_constant_region,
                          unsigned char *_local_region,   unsigned char *_atomics,
                          unsigned char *a, unsigned char *b, unsigned char *c,
                          int M, int N, int K) {
    namespace ct = cuda::tiles;
    using namespace ct::literals;

    const int HDR = 32;  // TornadoVM array header + 6.1.0 payload padding

    auto *pa = ct::assume_aligned(reinterpret_cast<__half *>(a + HDR), 16_ic);
    auto *pb = ct::assume_aligned(reinterpret_cast<__half *>(b + HDR), 16_ic);
    auto *pc = ct::assume_aligned(reinterpret_cast<float  *>(c + HDR), 16_ic);

    auto av = ct::partition_view{ct::tensor_span{pa, ct::extents{M, K}}, ct::shape{64_ic, 32_ic}};
    auto bv = ct::partition_view{ct::tensor_span{pb, ct::extents{K, N}}, ct::shape{32_ic, 64_ic}};
    auto cv = ct::partition_view{ct::tensor_span{pc, ct::extents{M, N}}, ct::shape{64_ic, 64_ic}};

    auto acc = ct::full<ct::tile<float, ct::shape<64, 64>>>(0.0f);
    for (auto k : ct::irange(0, K / 32)) {
        acc = ct::mma(av.load_masked(ct::bid().x, k), bv.load_masked(k, ct::bid().y), acc);
    }
    cv.store_masked(acc, ct::bid().x, ct::bid().y);
}
