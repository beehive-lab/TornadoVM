#include "cuda_tile.h"

__tile_global__ void saxpy(unsigned char *_kernel_context, unsigned char *_constant_region,
                           unsigned char *_local_region,   unsigned char *_atomics,
                           unsigned char *a, unsigned char *b, unsigned char *c,
                           int n, float alpha) {
    namespace ct = cuda::tiles;
    using namespace ct::literals;
    const int HDR = 16;  // TornadoNativeArray.ARRAY_HEADER on this build; the runtime pads the allocation so base + HDR is 32B aligned
    auto *pa = ct::assume_aligned(reinterpret_cast<float *>(a + HDR), 16_ic);
    auto *pb = ct::assume_aligned(reinterpret_cast<float *>(b + HDR), 16_ic);
    auto *pc = ct::assume_aligned(reinterpret_cast<float *>(c + HDR), 16_ic);
    auto av = ct::partition_view{ct::tensor_span{pa, ct::extents{n}}, ct::shape{256_ic}};
    auto bv = ct::partition_view{ct::tensor_span{pb, ct::extents{n}}, ct::shape{256_ic}};
    auto cv = ct::partition_view{ct::tensor_span{pc, ct::extents{n}}, ct::shape{256_ic}};
    int bx = ct::bid().x;
    cv.store_masked(alpha * av.load_masked(bx) + bv.load_masked(bx), bx);
}
