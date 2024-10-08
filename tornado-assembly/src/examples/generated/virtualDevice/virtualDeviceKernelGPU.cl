#pragma OPENCL EXTENSION cl_khr_fp64 : enable
#pragma OPENCL EXTENSION cl_khr_fp16 : enable
#pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable
__kernel void maxReduction(__global long *_kernel_context, __constant uchar *_constant_region, __local uchar *_local_region, __global int *_atomics, __global uchar *input, __global uchar *result)
{
  float f_26, f_12, f_21, f_22, f_19;
  bool b_18, b_25;
  int i_27, i_28, i_23, i_24, i_5, i_6, i_3, i_4, i_33, i_13, i_14, i_7, i_20, i_17, i_15, i_16;
  ulong ul_11, ul_0, ul_32, ul_1;
  long l_10, l_9, l_8, l_31, l_30, l_29;

  // BLOCK 0
  ul_0  =  (ulong) input;
  ul_1  =  (ulong) result;
  __local float adf_2[1024];
  i_3  =  get_global_size(0);
  i_4  =  get_global_id(0);
  // BLOCK 1 MERGES [0 11 ]
  i_5  =  i_4;
  for(;i_5 < 8192;)
  {
    // BLOCK 2
    i_6  =  get_local_id(0);
    i_7  =  get_local_size(0);
    l_8  =  (long) i_5;
    l_9  =  l_8 << 2;
    l_10  =  l_9 + 24L;
    ul_11  =  ul_0 + l_10;
    f_12  =  *((__global float *) ul_11);
    adf_2[i_6]  =  f_12;
    i_13  =  i_7 >> 31;
    i_14  =  i_13 + i_7;
    i_15  =  i_14 >> 1;
    // BLOCK 3 MERGES [2 7 ]
    i_16  =  i_15;
    for(;i_16 >= 1;)
    {
      // BLOCK 4
      barrier(CLK_LOCAL_MEM_FENCE);
      i_17  =  i_16 >> 1;
      b_18  =  i_6 < i_16;
      if(b_18)
      {
        // BLOCK 5
        f_19  =  adf_2[i_6];
        i_20  =  i_16 + i_6;
        f_21  =  adf_2[i_20];
        f_22  =  fmax(f_19, f_21);
        adf_2[i_6]  =  f_22;
      }  // B5
      else
      {
        // BLOCK 6
      }  // B6
      // BLOCK 7 MERGES [6 5 ]
      i_23  =  i_17;
      i_16  =  i_23;
    }  // B7
    // BLOCK 8
    barrier(CLK_GLOBAL_MEM_FENCE);
    i_24  =  i_3 + i_5;
    b_25  =  i_6 == 0;
    if(b_25)
    {
      // BLOCK 9
      f_26  =  adf_2[0];
      i_27  =  get_group_id(0);
      i_28  =  i_27 + 1;
      l_29  =  (long) i_28;
      l_30  =  l_29 << 2;
      l_31  =  l_30 + 24L;
      ul_32  =  ul_1 + l_31;
      *((__global float *) ul_32)  =  f_26;
    }  // B9
    else
    {
      // BLOCK 10
    }  // B10
    // BLOCK 11 MERGES [10 9 ]
    i_33  =  i_24;
    i_5  =  i_33;
  }  // B11
  // BLOCK 12
  return;
}  //  kernel

#pragma OPENCL EXTENSION cl_khr_fp64 : enable
#pragma OPENCL EXTENSION cl_khr_fp16 : enable
#pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable
__kernel void rMax(__global long *_kernel_context, __constant uchar *_constant_region, __local uchar *_local_region, __global int *_atomics, __global uchar *array, __private int size)
{
  float f_25, f_26, f_21, f_22, f_23, f_24, f_2, f_4, f_10, f_12, f_6, f_8, f_18, f_19, f_20, f_14, f_16;
  ulong ul_11, ul_9, ul_15, ul_13, ul_3, ul_0, ul_1, ul_17, ul_7, ul_5;

  // BLOCK 0
  ul_0  =  (ulong) array;
  ul_1  =  ul_0 + 24L;
  f_2  =  *((__global float *) ul_1);
  ul_3  =  ul_0 + 28L;
  f_4  =  *((__global float *) ul_3);
  ul_5  =  ul_0 + 32L;
  f_6  =  *((__global float *) ul_5);
  ul_7  =  ul_0 + 36L;
  f_8  =  *((__global float *) ul_7);
  ul_9  =  ul_0 + 40L;
  f_10  =  *((__global float *) ul_9);
  ul_11  =  ul_0 + 44L;
  f_12  =  *((__global float *) ul_11);
  ul_13  =  ul_0 + 48L;
  f_14  =  *((__global float *) ul_13);
  ul_15  =  ul_0 + 52L;
  f_16  =  *((__global float *) ul_15);
  ul_17  =  ul_0 + 56L;
  f_18  =  *((__global float *) ul_17);
  f_19  =  fmax(f_2, f_4);
  f_20  =  fmax(f_19, f_6);
  f_21  =  fmax(f_20, f_8);
  f_22  =  fmax(f_21, f_10);
  f_23  =  fmax(f_22, f_12);
  f_24  =  fmax(f_23, f_14);
  f_25  =  fmax(f_24, f_16);
  f_26  =  fmax(f_25, f_18);
  *((__global float *) ul_1)  =  f_26;
  return;
}  //  kernel