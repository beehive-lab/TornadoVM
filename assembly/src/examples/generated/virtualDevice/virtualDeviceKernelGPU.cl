#pragma OPENCL EXTENSION cl_khr_fp64 : enable
#pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable
__kernel void maxReduction(__global long *_kernel_context, __constant uchar *_constant_region, __local uchar *_local_region, __global int *_atomics, __global uchar *input, __global uchar *result)
{
  int i_20, i_17, i_13, i_14, i_25, i_26, i_21, i_22, i_3, i_4, i_1, i_2, i_31, i_11, i_12, i_10;
  float f_24, f_9, f_19, f_16, f_18;
  ulong ul_30, ul_8;
  long l_28, l_29, l_27, l_5, l_6, l_7;
  bool z_15, z_23;

  // BLOCK 0
  __local float f_0[1024];
  i_1  =  get_global_id(0);
  // BLOCK 1 MERGES [0 7 ]
  i_2  =  i_1;
  for(;i_2 < 8192;)
  {
    // BLOCK 2
    i_3  =  get_local_id(0);
    i_4  =  get_local_size(0);
    l_5  =  (long) i_2;
    l_6  =  l_5 << 2;
    l_7  =  l_6 + 24L;
    ul_8  =  input + l_7;
    f_9  =  *((__global float *) ul_8);
    f_0[i_3]  =  f_9;
    i_10  =  i_4 >> 31;
    i_11  =  i_10 + i_4;
    i_12  =  i_11 >> 1;
    // BLOCK 3 MERGES [2 11 ]
    i_13  =  i_12;
    for(;i_13 >= 1;)
    {
      // BLOCK 8
      barrier(CLK_LOCAL_MEM_FENCE);
      i_14  =  i_13 >> 1;
      z_15  =  i_3 < i_13;
      if(z_15)
      {
        // BLOCK 9
        f_16  =  f_0[i_3];
        i_17  =  i_13 + i_3;
        f_18  =  f_0[i_17];
        f_19  =  fmax(f_16, f_18);
        f_0[i_3]  =  f_19;
      }  // B9
      else
      {
        // BLOCK 10
      }  // B10
      // BLOCK 11 MERGES [10 9 ]
      i_20  =  i_14;
      i_13  =  i_20;
    }  // B11
    // BLOCK 4
    barrier(CLK_GLOBAL_MEM_FENCE);
    i_21  =  get_global_size(0);
    i_22  =  i_21 + i_2;
    z_23  =  i_3 == 0;
    if(z_23)
    {
      // BLOCK 5
      f_24  =  f_0[0];
      i_25  =  get_group_id(0);
      i_26  =  i_25 + 1;
      l_27  =  (long) i_26;
      l_28  =  l_27 << 2;
      l_29  =  l_28 + 24L;
      ul_30  =  result + l_29;
      *((__global float *) ul_30)  =  f_24;
    }  // B5
    else
    {
      // BLOCK 6
    }  // B6
    // BLOCK 7 MERGES [6 5 ]
    i_31  =  i_22;
    i_2  =  i_31;
  }  // B7
  // BLOCK 12
  return;
}  //  kernel

#pragma OPENCL EXTENSION cl_khr_fp64 : enable
#pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable
__kernel void rMax(__global long *_kernel_context, __constant uchar *_constant_region, __local uchar *_local_region, __global int *_atomics, __global uchar *array, __private int size)
{
  float f_11, f_13, f_7, f_9, f_20, f_19, f_22, f_21, f_15, f_18, f_17, f_24, f_23, f_25, f_3, f_5, f_1;
  ulong ul_4, ul_2, ul_0, ul_16, ul_14, ul_12, ul_10, ul_8, ul_6;

  // BLOCK 0
  ul_0  =  array + 56L;
  f_1  =  *((__global float *) ul_0);
  ul_2  =  array + 24L;
  f_3  =  *((__global float *) ul_2);
  ul_4  =  array + 28L;
  f_5  =  *((__global float *) ul_4);
  ul_6  =  array + 32L;
  f_7  =  *((__global float *) ul_6);
  ul_8  =  array + 36L;
  f_9  =  *((__global float *) ul_8);
  ul_10  =  array + 40L;
  f_11  =  *((__global float *) ul_10);
  ul_12  =  array + 44L;
  f_13  =  *((__global float *) ul_12);
  ul_14  =  array + 48L;
  f_15  =  *((__global float *) ul_14);
  ul_16  =  array + 52L;
  f_17  =  *((__global float *) ul_16);
  f_18  =  fmax(f_3, f_5);
  f_19  =  fmax(f_18, f_7);
  f_20  =  fmax(f_19, f_9);
  f_21  =  fmax(f_20, f_11);
  f_22  =  fmax(f_21, f_13);
  f_23  =  fmax(f_22, f_15);
  f_24  =  fmax(f_23, f_17);
  f_25  =  fmax(f_24, f_1);
  *((__global float *) ul_2)  =  f_25;
  return;
}  //  kernel