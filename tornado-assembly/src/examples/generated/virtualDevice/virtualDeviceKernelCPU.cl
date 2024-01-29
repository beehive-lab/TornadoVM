#pragma OPENCL EXTENSION cl_khr_fp64 : enable
#pragma OPENCL EXTENSION cl_khr_fp16 : enable
#pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable
__kernel void maxReduction(__global long *_kernel_context, __constant uchar *_constant_region, __local uchar *_local_region, __global int *_atomics, __global uchar *input, __global uchar *result)
{
  long l_35, l_34, l_36, l_15, l_14, l_13;
  ulong ul_1, ul_0, ul_16, ul_37;
  bool b_30, b_23;
  float f_31, f_17, f_27, f_24, f_26;
  int i_12, i_11, i_10, i_9, i_8, i_7, i_6, i_38, i_5, i_4, i_3, i_33, i_32, i_29, i_28, i_25, i_22, i_21, i_20, i_19, i_18;

  // BLOCK 0
  ul_0  =  (ulong) input;
  ul_1  =  (ulong) result;
  __local float adf_2[2048];
  i_3  =  get_global_size(0);
  i_4  =  i_3 + 8191;
  i_5  =  i_4 / i_3;
  i_6  =  get_global_id(0);
  i_7  =  i_5 * i_6;
  i_8  =  i_7 + i_5;
  i_9  =  min(i_8, 8192);
  // BLOCK 1 MERGES [0 11 ]
  i_10  =  i_7;
  for(;i_10 < i_9;)
  {
    // BLOCK 2
    i_11  =  get_local_id(0);
    i_12  =  get_local_size(0);
    l_13  =  (long) i_10;
    l_14  =  l_13 << 2;
    l_15  =  l_14 + 24L;
    ul_16  =  ul_0 + l_15;
    f_17  =  *((__global float *) ul_16);
    adf_2[i_11]  =  f_17;
    i_18  =  i_12 >> 31;
    i_19  =  i_18 + i_12;
    i_20  =  i_19 >> 1;
    // BLOCK 3 MERGES [2 7 ]
    i_21  =  i_20;
    for(;i_21 >= 1;)
    {
      // BLOCK 4
      barrier(CLK_LOCAL_MEM_FENCE);
      i_22  =  i_21 >> 1;
      b_23  =  i_11 < i_21;
      if(b_23)
      {
        // BLOCK 5
        f_24  =  adf_2[i_11];
        i_25  =  i_21 + i_11;
        f_26  =  adf_2[i_25];
        f_27  =  fmax(f_24, f_26);
        adf_2[i_11]  =  f_27;
      }  // B5
      else
      {
        // BLOCK 6
      }  // B6
      // BLOCK 7 MERGES [6 5 ]
      i_28  =  i_22;
      i_21  =  i_28;
    }  // B7
    // BLOCK 8
    barrier(CLK_GLOBAL_MEM_FENCE);
    i_29  =  i_10 + 1;
    b_30  =  i_11 == 0;
    if(b_30)
    {
      // BLOCK 9
      f_31  =  adf_2[0];
      i_32  =  get_group_id(0);
      i_33  =  i_32 + 1;
      l_34  =  (long) i_33;
      l_35  =  l_34 << 2;
      l_36  =  l_35 + 24L;
      ul_37  =  ul_1 + l_36;
      *((__global float *) ul_37)  =  f_31;
    }  // B9
    else
    {
      // BLOCK 10
    }  // B10
    // BLOCK 11 MERGES [10 9 ]
    i_38  =  i_29;
    i_10  =  i_38;
  }  // B11
  // BLOCK 12
  return;
}  //  kernel

#pragma OPENCL EXTENSION cl_khr_fp64 : enable
#pragma OPENCL EXTENSION cl_khr_fp16 : enable
#pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable
__kernel void rMax(__global long *_kernel_context, __constant uchar *_constant_region, __local uchar *_local_region, __global int *_atomics, __global uchar *array, __private int size)
{
  ulong ul_1, ul_0, ul_21, ul_19, ul_25, ul_23, ul_13, ul_11, ul_17, ul_15, ul_5, ul_3, ul_9, ul_7;
  float f_4, f_6, f_2, f_12, f_14, f_8, f_10, f_36, f_35, f_38, f_37, f_32, f_31, f_34, f_33, f_20, f_22, f_16, f_18, f_28, f_27, f_30, f_29, f_24, f_26;

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
  ul_19  =  ul_0 + 60L;
  f_20  =  *((__global float *) ul_19);
  ul_21  =  ul_0 + 64L;
  f_22  =  *((__global float *) ul_21);
  ul_23  =  ul_0 + 68L;
  f_24  =  *((__global float *) ul_23);
  ul_25  =  ul_0 + 72L;
  f_26  =  *((__global float *) ul_25);
  f_27  =  fmax(f_2, f_4);
  f_28  =  fmax(f_27, f_6);
  f_29  =  fmax(f_28, f_8);
  f_30  =  fmax(f_29, f_10);
  f_31  =  fmax(f_30, f_12);
  f_32  =  fmax(f_31, f_14);
  f_33  =  fmax(f_32, f_16);
  f_34  =  fmax(f_33, f_18);
  f_35  =  fmax(f_34, f_20);
  f_36  =  fmax(f_35, f_22);
  f_37  =  fmax(f_36, f_24);
  f_38  =  fmax(f_37, f_26);
  *((__global float *) ul_1)  =  f_38;
  return;
}  //  kernel
