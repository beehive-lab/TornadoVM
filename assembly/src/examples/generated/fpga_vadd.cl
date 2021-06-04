__attribute__((reqd_work_group_size(64,1,1)))
__kernel void vectorAdd(__global uchar *_heap_base, ulong _frame_base, __constant uchar *_constant_region, __local uchar *_local_region, __global int *_atomics)
{
  ulong ul_10, ul_8, ul_12, ul_2, ul_1, ul_0;
  long l_7, l_5, l_6;
  int i_11, i_13, i_14, i_15, i_3, i_4, i_9;

  __global ulong *_frame = (__global ulong *) &_heap_base[_frame_base];


  // BLOCK 0
  ul_0  =  (ulong) _frame[3];
  ul_1  =  (ulong) _frame[4];
  ul_2  =  (ulong) _frame[5];
  i_3  =  get_global_id(0);
  // BLOCK 1 MERGES [0 2 ]
  i_4  =  i_3;
  // BLOCK 2
  l_5  =  (long) i_4;
  l_6  =  l_5 << 2;
  l_7  =  l_6 + 24L;
  ul_8  =  ul_0 + l_7;
  i_9  =  *((__global int *) &_heap_base[ul_8]);
  ul_10  =  ul_1 + l_7;
  i_11  =  *((__global int *) &_heap_base[ul_10]);
  ul_12  =  ul_2 + l_7;
  i_13  =  i_9 + i_11;
  *((__global int *) &_heap_base[ul_12])  =  i_13;
  i_14  =  get_global_size(0);
  i_15  =  i_14 + i_4;
  i_4  =  i_15;
  // BLOCK 3
  return;
}  //  kernel