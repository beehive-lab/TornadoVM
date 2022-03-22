#pragma OPENCL EXTENSION cl_khr_fp64 : enable
#pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable
__kernel void maxReduction(__global long *_kernel_context, __constant uchar *_constant_region, __local uchar *_local_region, __global int *_atomics, __global uchar *input, __global uchar *result)
{
  long l_17, l_18, l_16, l_13, l_11, l_12;
  float f_21, f_20, f_15;
  ulong ul_19, ul_14;
  int i_1, i_2, i_0, i_5, i_6, i_22, i_3, i_4, i_10, i_7, i_8;
  bool z_9;

  // BLOCK 0
  i_0  =  get_global_size(0);
  i_1  =  i_0 + 8191;
  i_2  =  i_1 / i_0;
  i_3  =  get_global_id(0);
  i_4  =  i_2 * i_3;
  i_5  =  i_4 + i_2;
  i_6  =  min(i_5, 8192);
  // BLOCK 1 MERGES [0 5 ]
  i_7  =  i_4;
  for(;i_7 < i_6;)
  {
    // BLOCK 2
    barrier(CLK_LOCAL_MEM_FENCE);
    i_8  =  i_7 + 1;
    z_9  =  i_7 < 0;
    if(z_9)
    {
      // BLOCK 3
    }  // B3
    else
    {
      // BLOCK 4
      i_10  =  i_3 + 1;
      l_11  =  (long) i_10;
      l_12  =  l_11 << 2;
      l_13  =  l_12 + 24L;
      ul_14  =  result + l_13;
      f_15  =  *((__global float *) ul_14);
      l_16  =  i_7;
      l_17  =  l_16 << 2;
      l_18  =  l_17 + 24L;
      ul_19  =  input + l_18;
      f_20  =  *((__global float *) ul_19);
      f_21  =  fmax(f_15, f_20);
      *((__global float *) ul_14)  =  f_21;
    }  // B4
    // BLOCK 5 MERGES [3 4 ]
    i_22  =  i_8;
    i_7  =  i_22;
  }  // B5
  // BLOCK 6
  return;
}  //  kernel

#pragma OPENCL EXTENSION cl_khr_fp64 : enable
#pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable
__kernel void rMax(__global long *_kernel_context, __constant uchar *_constant_region, __local uchar *_local_region, __global int *_atomics, __global uchar *array, __private int size)
{
  float f_37, f_36, f_35, f_34, f_1, f_9, f_7, f_5, f_3, f_17, f_15, f_13, f_11, f_25, f_23, f_21, f_19, f_33, f_32, f_31, f_30, f_29, f_28, f_27, f_26;
  ulong ul_2, ul_0, ul_6, ul_4, ul_24, ul_18, ul_16, ul_22, ul_20, ul_10, ul_8, ul_14, ul_12;

  // BLOCK 0
  ul_0  =  array + 72L;
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
  ul_18  =  array + 56L;
  f_19  =  *((__global float *) ul_18);
  ul_20  =  array + 60L;
  f_21  =  *((__global float *) ul_20);
  ul_22  =  array + 64L;
  f_23  =  *((__global float *) ul_22);
  ul_24  =  array + 68L;
  f_25  =  *((__global float *) ul_24);
  f_26  =  fmax(f_3, f_5);
  f_27  =  fmax(f_26, f_7);
  f_28  =  fmax(f_27, f_9);
  f_29  =  fmax(f_28, f_11);
  f_30  =  fmax(f_29, f_13);
  f_31  =  fmax(f_30, f_15);
  f_32  =  fmax(f_31, f_17);
  f_33  =  fmax(f_32, f_19);
  f_34  =  fmax(f_33, f_21);
  f_35  =  fmax(f_34, f_23);
  f_36  =  fmax(f_35, f_25);
  f_37  =  fmax(f_36, f_1);
  *((__global float *) ul_2)  =  f_37;
  return;
}  //  kernel