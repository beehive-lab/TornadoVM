__kernel void reductionAddFloats(__global uchar *_heap_base, ulong _frame_base, __constant uchar *_constant_region, __local uchar *_local_region, __global uchar *_private_region)
{
  float f_36, f_24, f_30, f_31;
  int i_25, i_22, i_21, i_4, i_3, i_34, i_33, i_32, i_9, i_41, i_8, i_7, i_6, i_5, i_20, i_19, i_18, i_17, i_16, i_15, i_14, local_id;
  long l_11, l_27, l_12, l_28, l_37, l_38, l_10, l_26, l_39;
  ulong ul_40, ul_1, ul_2, ul_13, ul_29, ul_0;
  bool z_23, z_35;

  __global ulong *_frame = (__global ulong *) &_heap_base[_frame_base];

  // BLOCK 0
  ul_0  =  (ulong) _frame[6];
  ul_1  =  (ulong) _frame[7];

  *((__global float *) ul_1 + 24)  =  0.0F;

  __local float localBuffer[1024];

  // BLOCK 1 MERGES [0 7 ]
  // from here top-down everything is parallel
  i_4  =  get_global_id(0);

    // BLOCK 2
    
    ul_13  =  ul_0 + ((( (get_local_size(0) * get_group_id(0)) + get_local_id(0)) << 2) + 24);

    // BLOCK 3 MERGES [2 11 ]
    localBuffer[get_local_id(0)] = *((__global float *) ul_13);

    //i_18 = 128;

    i_18  =  ((( get_local_size(0) >> 31) >> 31) +  get_local_size(0) ) >> 1;
    //((( 1024 >> 31) >> 31) +  256 ) >> 1
    
    for(;i_18 >= 1;)    {
      // BLOCK 8
      barrier(CLK_LOCAL_MEM_FENCE);

      local_id = get_local_id(0);

      i_22  =  (((i_18 >> 31) >> 31) + i_18) >> 1;

      if(get_local_id(0) < i_18)
      {
       // BLOCK 9
       localBuffer[local_id] = localBuffer[local_id]  + localBuffer[local_id + i_18];
      }
      else
      {
        // BLOCK 10
      }
      // BLOCK 11 MERGES [10 9 ]
      i_18  =  i_22;
    }
    // BLOCK 4

    if(get_local_id(0) == 0)
    {
      // BLOCK 5
      ul_40  =  ul_1 + (( get_group_id(0) << 2) + 24);
      *((__global float *) ul_40)  =  localBuffer[0];
    }
    else
    {
      // BLOCK 6
    }
    // BLOCK 7 MERGES [6 5 ]
    i_4  =  get_global_size(0) + i_4;
  // BLOCK 12
  return;
}