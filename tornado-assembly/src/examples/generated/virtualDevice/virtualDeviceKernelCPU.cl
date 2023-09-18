#pragma OPENCL EXTENSION cl_khr_fp64 : enable  
#pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable  
__kernel void maxReduction(__global long *_kernel_context, __constant uchar *_constant_region, __local uchar *_local_region, __global int *_atomics, __global uchar *input, __global uchar *result)
{
  bool b_11; 
  float f_17, f_22, f_23; 
  ulong ul_1, ul_0, ul_16, ul_21; 
  int i_2, i_4, i_3, i_10, i_9, i_12, i_6, i_5, i_8, i_24, i_7; 
  long l_15, l_18, l_14, l_13, l_20, l_19; 

  // BLOCK 0
  ul_0  =  (ulong) input;
  ul_1  =  (ulong) result;
  i_2  =  get_global_size(0);
  i_3  =  i_2 + 8191;
  i_4  =  i_3 / i_2;
  i_5  =  get_global_id(0);
  i_6  =  i_4 * i_5;
  i_7  =  i_6 + i_4;
  i_8  =  min(i_7, 8192);
  // BLOCK 1 MERGES [0 5 ]
  i_9  =  i_6;
  for(;i_9 < i_8;)
  {
    // BLOCK 2
    barrier(CLK_LOCAL_MEM_FENCE);
    i_10  =  i_9 + 1;
    b_11  =  i_9 < 0;
    if(b_11)
    {
      // BLOCK 3
    }  // B3
    else
    {
      // BLOCK 4
      i_12  =  i_5 + 1;
      l_13  =  (long) i_12;
      l_14  =  l_13 << 2;
      l_15  =  l_14 + 24L;
      ul_16  =  ul_1 + l_15;
      f_17  =  *((__global float *) ul_16);
      l_18  =  i_9;
      l_19  =  l_18 << 2;
      l_20  =  l_19 + 24L;
      ul_21  =  ul_0 + l_20;
      f_22  =  *((__global float *) ul_21);
      f_23  =  fmax(f_17, f_22);
      *((__global float *) ul_16)  =  f_23;
    }  // B4
    // BLOCK 5 MERGES [3 4 ]
    i_24  =  i_10;
    i_9  =  i_24;
  }  // B5
  // BLOCK 6
  return;
}  //  kernel

#pragma OPENCL EXTENSION cl_khr_fp64 : enable  
#pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable  
__kernel void rMax(__global long *_kernel_context, __constant uchar *_constant_region, __local uchar *_local_region, __global int *_atomics, __global uchar *array, __private int size)
{
  float f_10, f_8, f_14, f_12, f_18, f_16, f_22, f_20, f_2, f_6, f_4, f_26, f_27, f_24, f_30, f_31, f_28, f_29, f_34, f_35, f_32, f_33, f_38, f_36, f_37; 
  ulong ul_3, ul_1, ul_0, ul_11, ul_9, ul_7, ul_5, ul_19, ul_17, ul_15, ul_13, ul_25, ul_23, ul_21; 

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

