#pragma OPENCL EXTENSION cl_khr_fp64 : enable
__kernel void vectorAdd(__global uchar *_heap_base, ulong _frame_base, __constant uchar *_constant_region, __local uchar *_local_region, __global uchar *_private_region)
{
  long l_5, l_7, l_6;
  int i_14, i_13, i_15, i_4, i_3, i_9, i_11,idx_local, local_size;
  ulong ul_12, ul_10, ul_8, ul_2, ul_1, ul_0;

  __global ulong *_frame = (__global ulong *) &_heap_base[_frame_base];


		// BLOCK 0
		ul_0  =  (ulong) _frame[6];
		ul_1  =  (ulong) _frame[7];
		ul_2  =  (ulong) _frame[8];
		i_3  =  get_global_id(0);
		idx_local = get_local_id(0);
		local_size = get_local_size(0);
		// BLOCK 1 MERGES [0 2 ]
		i_4  =  i_3;
		// BLOCK 2
		l_5  =  (long) i_4;
		l_6  =  l_5 << 2;
		l_7  =  l_6 + 24L;
		ul_8  =  ul_0 + l_7;
		i_9  =  *((__global int *) ul_8);
		ul_10  =  ul_1 + l_7;
		i_11  =  *((__global int *) ul_10);
		ul_12  =  ul_2 + l_7;
		i_13  =  i_9 + i_11;
		*((__global int *) ul_12)  =  i_13;
		i_14  =  get_global_size(0);
		i_15  =  i_14 + i_4;
		i_4  =  i_15;
		// BLOCK 3
		return;
}
