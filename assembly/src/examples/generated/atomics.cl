#pragma OPENCL EXTENSION cl_khr_fp64 : enable  
__kernel void add(__global uchar *_heap_base, 
                  const ulong _frame_base, 
                  __constant uchar *_constant_region, 
                  __local uchar *_local_region,
                  __global int* atomics
                 )
{
  ulong ul_8, ul_10, ul_1, ul_0, ul_2, ul_12; 
  long l_5, l_7, l_6; 
  int i_11, i_9, i_15, i_14, i_13, i_3, i_4; 

  __global ulong *_frame = (__global ulong *) &_heap_base[_frame_base];
  
  int ul_atomic = _frame[0];
  ul_0  =  (ulong) _frame[3];
  ul_1  =  (ulong) _frame[4];
  i_3  =  get_global_id(0);
  i_4  =  i_3;
  
  l_5  =  (long) i_4;
  l_6  =  l_5 << 2;
  l_7  =  l_6 + 24L;
  ul_8  =  ul_0 + l_7;
  i_9  =  *((__global int *) ul_8);
  int base = 24L;
  ul_10  =  ul_1 + base;
  i_11  =  *((__global int *) ul_10);
  
  // This works with 64 base atomics extension
  //int atomicValue = atomic_add(&_frame[0], 1);

  int atomicValue = atomic_add(&atomics[0], 1);

  ul_12  =  ul_2 + l_7;
  i_13  =  i_9 + i_11;
  *((__global int *) ul_8)  =  atomicValue;
}
