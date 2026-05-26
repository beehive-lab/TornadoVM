#include <metal_stdlib>
using namespace metal;

typedef ulong tornado_ptr_t;

kernel void add(
    device long *_kernel_context [[buffer(0)]],
    constant uchar *_constant_region [[buffer(1)]],
    threadgroup uchar *_local_region [[threadgroup(2)]],
    device int *_atomics [[buffer(3)]],
    device uchar *a [[buffer(4)]],
    device uchar *b [[buffer(5)]],
    device uchar *c [[buffer(6)]],
    uint3 _thread_position_in_grid [[thread_position_in_grid]],
    uint3 _thread_position_in_threadgroup [[thread_position_in_threadgroup]],
    uint3 _threadgroup_position_in_grid [[threadgroup_position_in_grid]],
    uint3 _local_size [[threads_per_threadgroup]],
    device uint* _global_sizes [[buffer(7)]]
)
{
  int i_7, i_6, i_9, i_3, i_5, i_4, i_14, i_11, i_13;
  tornado_ptr_t ul_10, ul_8, ul_12, ul_2, ul_1, ul_0;

  // BLOCK 0
  ul_0  =  (tornado_ptr_t) a;
  ul_1  =  (tornado_ptr_t) b;
  ul_2  =  (tornado_ptr_t) c;
  i_3  =  _global_sizes[0];
  i_4  =  _thread_position_in_grid.x;
  // BLOCK 1 MERGES [0 2 ]
  i_5  =  i_4;
  for(;i_5 < 8;)
  {
    // BLOCK 2
    i_6  =  i_5 + 4;
    i_7  =  i_6 << 2;
    ul_8  =  ul_0 + i_7;
    i_9  =  *((device int *) ul_8);
    ul_10  =  ul_1 + i_7;
    i_11  =  *((device int *) ul_10);
    ul_12  =  ul_2 + i_7;
    i_13  =  i_9 + i_11;
    *((device int *) ul_12)  =  i_13;
    i_14  =  i_3 + i_5;
    i_5  =  i_14;
  }  // B2
  // BLOCK 3
  return;
}  //  kernel
