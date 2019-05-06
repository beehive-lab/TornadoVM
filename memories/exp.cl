#pragma OPENCL EXTENSION cl_khr_fp64 : enable  
__kernel void reductionAddFloats(__global uchar *_heap_base, ulong _frame_base, __constant uchar *_constant_region, __local uchar *_local_region, __global uchar *_private_region)
{
  ulong ul_2, ul_1, ul_0, ul_40, ul_13, ul_29; 
  int i_19, i_20, i_21, i_22, i_15, i_16, i_17, i_18, i_14, i_7, i_8, i_9, i_41, i_3, i_4, i_5, i_6, i_32, i_33, i_34, i_25; 
  long l_12, l_28, l_10, l_26, l_11, l_27, l_38, l_39, l_37; 
  bool z_23, z_35; 
  float f_31, f_30, f_24, f_36; 

  __global ulong *_frame = (__global ulong *) &_heap_base[_frame_base];

  int local_id;

  // BLOCK 0
  ul_0  =  (ulong) _frame[6];
  ul_1  =  (ulong) _frame[7];
  ul_2  =  ul_1 + 24L;
  *((__global float *) ul_2)  =  0.0F;
  __local float localBuffer[512];

  i_3  =  get_global_id(0);
  // BLOCK 1 MERGES [0 7 ]
  i_4  =  i_3;
    // BLOCK 2
    i_5  =  get_local_id(0);
    i_6  =  get_local_size(0);
    i_7  =  get_group_id(0);
    i_8  =  i_6 * i_7;
    i_9  =  i_8 + i_5;
    l_10  =  (long) i_9;
    l_11  =  l_10 << 2;
    l_12  =  l_11 + 24L;
    ul_13  =  ul_0 + l_12;
    i_14  =  i_6 >> 31;
    i_15  =  i_14 >> 31;
    i_16  =  i_15 + i_6;
    i_17  =  i_16 >> 1;
    // BLOCK 3 MERGES [2 11 ]
    localBuffer[get_local_id(0)] = *((__global float *) ul_13);
    i_18  =  i_17;
    for(;i_18 >= 1;)    {
      // BLOCK 8
      barrier(CLK_LOCAL_MEM_FENCE);
      local_id = get_local_id(0);

      i_19  =  i_18 >> 31;
      i_20  =  i_19 >> 31;
      i_21  =  i_20 + i_18;
      i_22  =  i_21 >> 1;
      z_23  =  i_5 < i_18;
      if(z_23)
      {
        // BLOCK 9
        //f_24  =  *((__global float *) ul_13);
        i_25  =  i_9 + i_18;
        l_26  =  (long) i_25;
        l_27  =  l_26 << 2;
        l_28  =  l_27 + 24L;
        ul_29  =  ul_0 + l_28;
        // f_30  =  *((__global float *) ul_29);
        f_31  =  f_24 + f_30;
       //  *((__global float *) ul_13)  =  f_31;
        localBuffer[local_id] = localBuffer[local_id]  + localBuffer[local_id + i_18];
      }
      else
      {
        // BLOCK 10
      }
      // BLOCK 11 MERGES [10 9 ]
      i_32  =  i_22;
      i_18  =  i_32;
    }
    // BLOCK 4
    barrier(CLK_GLOBAL_MEM_FENCE);
    i_33  =  get_global_size(0);
    i_34  =  i_33 + i_4;
    z_35  =  i_5 == 0;
    if(z_35)
    {
      // BLOCK 5
     // f_36  =  *((__global float *) ul_13);
      l_37  =  (long) i_7;
      l_38  =  l_37 << 2;
      l_39  =  l_38 + 24L;
      ul_40  =  ul_1 + l_39;
      //*((__global float *) ul_40)  =  f_36;
      *((__global float *) ul_40)  =  localBuffer[0];
    }
    else
    {
      // BLOCK 6
    }
    // BLOCK 7 MERGES [6 5 ]
    i_41  =  i_34;
    i_4  =  i_41;
  
  // BLOCK 12
  return;
}
