#pragma OPENCL EXTENSION cl_khr_fp64 : enable
#pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable
__kernel void lookupBufferAddress(__global uchar *_heap_base, ulong _frame_base, __constant uchar *_constant_region, __local uchar *_local_region, __global int *_atomics)
{

  __global ulong *_frame = (__global ulong *) &_heap_base[_frame_base];


  // BLOCK 0
  _frame[0]  =  (ulong) _heap_base;
}  //  kernel

#pragma OPENCL EXTENSION cl_khr_fp64 : enable
#pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable
__kernel void maxReduction(__global uchar *_heap_base, ulong _frame_base, __constant uchar *_constant_region, __local uchar *_local_region, __global int *_atomics)
{
  bool z_17, z_25;
  ulong ul_1, ul_0, ul_32, ul_10;
  float f_18, f_21, f_20, f_11, f_26;
  int i_19, i_16, i_14, i_15, i_12, i_13, i_27, i_24, i_22, i_23, i_3, i_33, i_28, i_6, i_4, i_5;
  long l_8, l_9, l_7, l_29, l_30, l_31;

  __global ulong *_frame = (__global ulong *) &_heap_base[_frame_base];


  // BLOCK 0
  ul_0  =  (ulong) _frame[3];
  ul_1  =  (ulong) _frame[4];
  __local float f_2[1024];
  i_3  =  get_global_id(0);
  // BLOCK 1 MERGES [0 7 ]
  i_4  =  i_3;
  for(;i_4 < 8192;)
  {
    // BLOCK 2
    i_5  =  get_local_id(0);
    i_6  =  get_local_size(0);
    l_7  =  (long) i_4;
    l_8  =  l_7 << 2;
    l_9  =  l_8 + 24L;
    ul_10  =  ul_0 + l_9;
    f_11  =  *((__global float *) ul_10);
    f_2[i_5]  =  f_11;
    i_12  =  i_6 >> 31;
    i_13  =  i_12 + i_6;
    i_14  =  i_13 >> 1;
    // BLOCK 3 MERGES [2 11 ]
    i_15  =  i_14;
    for(;i_15 >= 1;)
    {
      // BLOCK 8
      barrier(CLK_LOCAL_MEM_FENCE);
      i_16  =  i_15 >> 1;
      z_17  =  i_5 < i_15;
      if(z_17)
      {
        // BLOCK 9
        f_18  =  f_2[i_5];
        i_19  =  i_15 + i_5;
        f_20  =  f_2[i_19];
        f_21  =  fmax(f_18, f_20);
        f_2[i_5]  =  f_21;
      }  // B9
      else
      {
        // BLOCK 10
      }  // B10
      // BLOCK 11 MERGES [10 9 ]
      i_22  =  i_16;
      i_15  =  i_22;
    }  // B11
    // BLOCK 4
    barrier(CLK_GLOBAL_MEM_FENCE);
    i_23  =  get_global_size(0);
    i_24  =  i_23 + i_4;
    z_25  =  i_5 == 0;
    if(z_25)
    {
      // BLOCK 5
      f_26  =  f_2[0];
      i_27  =  get_group_id(0);
      i_28  =  i_27 + 1;
      l_29  =  (long) i_28;
      l_30  =  l_29 << 2;
      l_31  =  l_30 + 24L;
      ul_32  =  ul_1 + l_31;
      *((__global float *) ul_32)  =  f_26;
    }  // B5
    else
    {
      // BLOCK 6
    }  // B6
    // BLOCK 7 MERGES [6 5 ]
    i_33  =  i_24;
    i_4  =  i_33;
  }  // B7
  // BLOCK 12
  return;
}  //  kernel

#pragma OPENCL EXTENSION cl_khr_fp64 : enable
#pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable
__kernel void rMax(__global uchar *_heap_base, ulong _frame_base, __constant uchar *_constant_region, __local uchar *_local_region, __global int *_atomics)
{
  ulong ul_15, ul_13, ul_3, ul_1, ul_17, ul_0, ul_7, ul_5, ul_11, ul_9;
  float f_6, f_8, f_2, f_4, f_26, f_23, f_22, f_25, f_24, f_19, f_18, f_21, f_20, f_14, f_16, f_10, f_12;

  __global ulong *_frame = (__global ulong *) &_heap_base[_frame_base];


  // BLOCK 0
  ul_0  =  (ulong) _frame[3];
  ul_1  =  ul_0 + 56L;
  f_2  =  *((__global float *) ul_1);
  ul_3  =  ul_0 + 24L;
  f_4  =  *((__global float *) ul_3);
  ul_5  =  ul_0 + 28L;
  f_6  =  *((__global float *) ul_5);
  ul_7  =  ul_0 + 32L;
  f_8  =  *((__global float *) ul_7);
  ul_9  =  ul_0 + 36L;
  f_10  =  *((__global float *) ul_9);
  ul_11  =  ul_0 + 40L;
  f_12  =  *((__global float *) ul_11);
  ul_13  =  ul_0 + 44L;
  f_14  =  *((__global float *) ul_13);
  ul_15  =  ul_0 + 48L;
  f_16  =  *((__global float *) ul_15);
  ul_17  =  ul_0 + 52L;
  f_18  =  *((__global float *) ul_17);
  f_19  =  fmax(f_4, f_6);
  f_20  =  fmax(f_19, f_8);
  f_21  =  fmax(f_20, f_10);
  f_22  =  fmax(f_21, f_12);
  f_23  =  fmax(f_22, f_14);
  f_24  =  fmax(f_23, f_16);
  f_25  =  fmax(f_24, f_18);
  f_26  =  fmax(f_25, f_2);
  *((__global float *) ul_3)  =  f_26;
  return;
}  //  kernel