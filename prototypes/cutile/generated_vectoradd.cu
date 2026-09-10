#include "cuda_tile.h"
namespace ct = cuda::tiles;
using namespace ct::literals;
extern "C" __tile_global__ void vectorAdd(long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *arg1, unsigned char *arg2, unsigned char *arg3, int arg4)
{
  unsigned long long ul_0, ul_1, ul_2; 
  int i_6; 

  // BLOCK 0
  ul_0  =  (unsigned long long) arg1;
  ul_1  =  (unsigned long long) arg2;
  ul_2  =  (unsigned long long) arg3;
  auto tview_3 = ct::partition_view{ct::tensor_span{ct::assume_aligned(reinterpret_cast<float *>(ul_0 + 16), 16_ic), ct::extents{4096}}, ct::shape{256_ic}};
  auto tview_4 = ct::partition_view{ct::tensor_span{ct::assume_aligned(reinterpret_cast<float *>(ul_1 + 16), 16_ic), ct::extents{4096}}, ct::shape{256_ic}};
  auto tview_5 = ct::partition_view{ct::tensor_span{ct::assume_aligned(reinterpret_cast<float *>(ul_2 + 16), 16_ic), ct::extents{4096}}, ct::shape{256_ic}};
  i_6 = ct::bid().x;
  auto tile_7 = tview_3.load(i_6);
  auto tile_8 = tview_4.load(i_6);
  auto tile_9 = tile_7 + tile_8;
  tview_5.store(tile_9, i_6);
  return;
}  //  kernel
