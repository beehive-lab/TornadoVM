#pragma OPENCL EXTENSION cl_khr_fp64 : enable
__kernel void matrixMultiplication(__global uchar *_heap_base, ulong _frame_base, __constant uchar *_constant_region, __local uchar *_local_region, __global uchar *_private_region)
{
  ulong ul_22, ul_0, ul_15, ul_2, ul_1, ul_30;
  int i_11, i_17, i_18, i_26, i_33, i_34, i_31, i_32, i_5, i_6, i_3, i_4, i_9, i_10, i_7, idx_local_0, idx_local_1, local_group_0, local_group_1;
  float f_16, f_23, f_8, f_24, f_25;
  long l_12, l_28, l_27, l_14, l_13, l_29, l_20, l_19, l_21;

  __global ulong *_frame = (__global ulong *) &_heap_base[_frame_base];


  // BLOCK 0
  ul_0  =  (ulong) _frame[6];
  ul_1  =  (ulong) _frame[7];
  ul_2  =  (ulong) _frame[8];
  i_3  =  get_global_id(1);
  idx_local_1 = get_local_id(1);
  // BLOCK 1 MERGES [0 8 ]
  i_4  =  i_3;
  for(;i_4 < 512;)  {
    // BLOCK 2
    i_5  =  get_global_id(0);
	idx_local_0 = get_local_id(0);
    // BLOCK 3 MERGES [2 7 ]
    i_6  =  i_5;
    for(;i_6 < 512;)    {
      // BLOCK 4
      i_7  =  i_4 << 9;
      // BLOCK 5 MERGES [4 6 ]
      f_8  =  0.0F;
      i_9  =  0;
      for(;i_9 < 512;)      {
        // BLOCK 6
        i_10  =  i_9 + 1;
        i_11  =  i_7 + i_9;
        l_12  =  (long) i_11;
        l_13  =  l_12 << 2;
        l_14  =  l_13 + 24L;
        ul_15  =  ul_0 + l_14;
        f_16  =  *((__global float *) ul_15);
        i_17  =  i_9 << 9;
        i_18  =  i_17 + i_6;
        l_19  =  (long) i_18;
        l_20  =  l_19 << 2;
        l_21  =  l_20 + 24L;
        ul_22  =  ul_1 + l_21;
        f_23  =  *((__global float *) ul_22);
        f_24  =  f_16 * f_23;
        f_25  =  f_8 + f_24;
        f_8  =  f_25;
        i_9  =  i_10;
      }
      // BLOCK 7
      i_26  =  i_6 + i_7;
      l_27  =  (long) i_26;
      l_28  =  l_27 << 2;
      l_29  =  l_28 + 24L;
      ul_30  =  ul_2 + l_29;
      *((__global float *) ul_30)  =  f_8;
      i_31  =  get_global_size(0);
      i_32  =  i_31 + i_6;
      i_6  =  i_32;
    }
    // BLOCK 8
    i_33  =  get_global_size(1);
    i_34  =  i_33 + i_4;
    i_4  =  i_34;
  }
  // BLOCK 9
  return;
}
