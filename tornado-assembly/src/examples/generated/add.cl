#pragma OPENCL EXTENSION cl_khr_fp64 : enable  
__kernel void add(__global long *_kernel_context, __constant uchar *_constant_region, __local uchar *_local_region, __global int *_atomics, __global uchar *a, __global uchar *b, __global uchar *c)
{
  int i_3, i_4, i_15, i_14, i_12, i_10, i_5; 
  ulong ul_13, ul_11, ul_1, ul_2, ul_0, ul_9; 
  long l_7, l_6, l_8; 

  // BLOCK 0
  ul_0  =  (ulong) a;
  ul_1  =  (ulong) b;
  ul_2  =  (ulong) c;
  i_3  =  get_global_size(0);
  i_4  =  get_global_id(0);
  // BLOCK 1 MERGES [0 2 ]
  i_5  =  i_4;
  for(;i_5 < 16;)
  {
    // BLOCK 2
    l_6  =  (long) i_5;
    l_7  =  l_6 << 2;
    l_8  =  l_7 + 16L;
    ul_9  =  ul_0 + l_8;
    i_10  =  *((__global int *) ul_9);
    ul_11  =  ul_1 + l_8;
    i_12  =  *((__global int *) ul_11);
    ul_13  =  ul_2 + l_8;
    i_14  =  i_10 + i_12;
    *((__global int *) ul_13)  =  i_14;
    i_15  =  i_3 + i_5;
    i_5  =  i_15;
  }  // B2
  // BLOCK 3
  return;
}  //  kernel

