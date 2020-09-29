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
  ulong ul_0, ul_1, ul_34, ul_10;
  int i_13, i_14, i_12, i_5, i_6, i_3, i_35, i_4, i_25, i_26, i_24, i_29, i_30, i_17, i_18, i_15, i_16, i_21;
  bool z_27, z_19;
  float f_11, f_23, f_22, f_20, f_28;
  long l_33, l_7, l_8, l_9, l_31, l_32;

  __global ulong *_frame = (__global ulong *) &_heap_base[_frame_base];


  // BLOCK 0
  ul_0  =  (ulong) _frame[3];
  ul_1  =  (ulong) _frame[4];
  __local float ul_2[1024];
  i_3  =  get_global_id(0);
  // BLOCK 1 MERGES [0 7 ]
  i_4  =  i_3;
  for(;i_4 < 8192;)  {
    // BLOCK 2
    i_5  =  get_local_id(0);
    i_6  =  get_local_size(0);
    l_7  =  (long) i_4;
    l_8  =  l_7 << 2;
    l_9  =  l_8 + 24L;
    ul_10  =  ul_0 + l_9;
    f_11  =  *((__global float *) ul_10);
    ul_2[i_5]  =  f_11;
    i_12  =  i_6 >> 31;
    i_13  =  i_6 - i_12;
    i_14  =  i_13 >> 1;
    // BLOCK 3 MERGES [2 11 ]
    i_15  =  i_14;
    for(;i_15 >= 1;)    {
      // BLOCK 8
      barrier(CLK_LOCAL_MEM_FENCE);
      i_16  =  i_15 >> 31;
      i_17  =  i_15 - i_16;
      i_18  =  i_17 >> 1;
      z_19  =  i_5 < i_15;
      if(z_19)
      {
        // BLOCK 9
        f_20  =  ul_2[i_5];
        i_21  =  i_15 + i_5;
        f_22  =  ul_2[i_21];
        f_23  =  fmax(f_20, f_22);
        ul_2[i_5]  =  f_23;
        f_20  =  f_23;
      }  // B9
      else
      {
        // BLOCK 10
      }  // B10
      // BLOCK 11 MERGES [10 9 ]
      i_24  =  i_18;
      i_15  =  i_24;
    }  // B11
    // BLOCK 4
    barrier(CLK_GLOBAL_MEM_FENCE);
    i_25  =  get_global_size(0);
    i_26  =  i_25 + i_4;
    z_27  =  i_5 == 0;
    if(z_27)
    {
      // BLOCK 5
      f_28  =  ul_2[0];
      i_29  =  get_group_id(0);
      i_30  =  i_29 + 1;
      l_31  =  (long) i_30;
      l_32  =  l_31 << 2;
      l_33  =  l_32 + 24L;
      ul_34  =  ul_1 + l_33;
      *((__global float *) ul_34)  =  f_28;
    }  // B5
    else
    {
      // BLOCK 6
    }  // B6
    // BLOCK 7 MERGES [6 5 ]
    i_35  =  i_26;
    i_4  =  i_35;
  }  // B7
  // BLOCK 12
  return;
}  //  kernel

#pragma OPENCL EXTENSION cl_khr_fp64 : enable
#pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable
__kernel void rMax(__global uchar *_heap_base, ulong _frame_base, __constant uchar *_constant_region, __local uchar *_local_region, __global int *_atomics)
{
  ulong ul_0, ul_1, ul_17, ul_3, ul_5, ul_7, ul_9, ul_11, ul_13, ul_15;
  float f_26, f_25, f_24, f_23, f_22, f_21, f_20, f_19, f_18, f_16, f_14, f_12, f_10, f_8, f_6, f_4, f_2;

  __global ulong *_frame = (__global ulong *) &_heap_base[_frame_base];


  // BLOCK 0
  ul_0  =  (ulong) _frame[3];
  ul_1  =  ul_0 + 56L;
  f_2  =  *((__global float *) ul_1);
  ul_3  =  ul_0 + 52L;
  f_4  =  *((__global float *) ul_3);
  ul_5  =  ul_0 + 48L;
  f_6  =  *((__global float *) ul_5);
  ul_7  =  ul_0 + 44L;
  f_8  =  *((__global float *) ul_7);
  ul_9  =  ul_0 + 40L;
  f_10  =  *((__global float *) ul_9);
  ul_11  =  ul_0 + 36L;
  f_12  =  *((__global float *) ul_11);
  ul_13  =  ul_0 + 32L;
  f_14  =  *((__global float *) ul_13);
  ul_15  =  ul_0 + 28L;
  f_16  =  *((__global float *) ul_15);
  ul_17  =  ul_0 + 24L;
  f_18  =  *((__global float *) ul_17);
  f_19  =  fmax(f_18, f_16);
  f_20  =  fmax(f_19, f_14);
  f_21  =  fmax(f_20, f_12);
  f_22  =  fmax(f_21, f_10);
  f_23  =  fmax(f_22, f_8);
  f_24  =  fmax(f_23, f_6);
  f_25  =  fmax(f_24, f_4);
  f_26  =  fmax(f_25, f_2);
  *((__global float *) ul_17)  =  f_26;
  f_18  =  f_26;
  return;
}  //  kernel

