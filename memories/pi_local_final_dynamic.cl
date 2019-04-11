__kernel void computePi(__global uchar *_heap_base, ulong _frame_base, __constant uchar *_constant_region, __local uchar *_local_region, __global uchar *_private_region)
{
  long l_9, l_41, l_42, l_7, l_8, l_40, l_53, l_51, l_52, l_17, l_15, l_16; 
  bool z_49, z_37; 
  ulong ul_54, ul_18, ul_1, ul_0, ul_10, ul_43; 
  int i_22, i_19, i_23, i_30, i_29, i_28, i_2, i_6, i_5, i_4, i_3, i_14, i_13, i_12, i_48, i_47, i_55, i_34, i_33, i_32, i_31, i_36, i_35, i_39, i_46; 
  float f_26, f_11, f_27, f_44, f_45, f_50, f_38; 
  double d_25, d_24, d_21, d_20; 

  __global ulong *_frame = (__global ulong *) &_heap_base[_frame_base];


  // BLOCK 0
  ul_0  =  (ulong) _frame[6];
  ul_1  =  (ulong) _frame[7];
  i_2  =  get_global_id(0);
  i_3  =  i_2 + 1;

  __local float localBuffer[1024];

  // BLOCK 1 MERGES [0 7 ]
  i_4  =  i_3;
    // BLOCK 2
    i_5  =  get_local_id(0);
    i_6  =  get_local_size(0);
    l_7  =  (long) i_4;
    l_8  =  l_7 << 2;
    l_9  =  l_8 + 24L;
    ul_10  =  ul_0 + l_9;
    //f_11  =  *((__global float *) ul_10);
    i_12  =  get_group_id(0);
    i_13  =  i_6 * i_12;
    i_14  =  i_13 + i_5;
    l_15  =  (long) i_14;
    l_16  =  l_15 << 2;
    l_17  =  l_16 + 24L;
    ul_18  =  ul_0 + l_17;
    i_19  =  i_4 + 1;
    d_20  =  (double) i_19;
    d_21  =  pow(-1.0, d_20);
    i_22  =  i_4 << 1;
    i_23  =  i_22 + -1;
    d_24  =  (double) i_23;
    d_25  =  d_21 / d_24;
    f_26  =  (float) d_25;
    
    //f_27  =  f_26 + f_11;
    
    localBuffer[i_5] = f_26  + *((__global float *) ul_10);
    
    i_28  =  i_6 >> 31;
    i_29  =  i_28 >> 31;
    i_30  =  i_29 + i_6;
    i_31  =  i_30 >> 1;
    // BLOCK 3 MERGES [2 11 ]
    i_32  =  i_31;
    for(;i_32 >= 1;)    {
      // BLOCK 8
      barrier(CLK_LOCAL_MEM_FENCE);
      i_33  =  i_32 >> 31;
      i_34  =  i_33 >> 31;
      i_35  =  i_34 + i_32;
      i_36  =  i_35 >> 1;
      z_37  =  i_5 < i_32;
      if(z_37)
      {
        // BLOCK 9
        f_38 = localBuffer[i_5];
        i_39  =  i_14 + i_32;
        l_40  =  (long) i_39;
        l_41  =  l_40 << 2;
        l_42  =  l_41 + 24L;
        ul_43  =  ul_0 + l_42;
        localBuffer[get_local_id(0)] = localBuffer[i_5] + localBuffer[i_5 + i_32];
      }
      else
      {
        // BLOCK 10
      }
      // BLOCK 11 MERGES [10 9 ]
      i_46  =  i_36;
      i_32  =  i_46;
    }
    // BLOCK 4
    barrier(CLK_LOCAL_MEM_FENCE);
    i_47  =  get_global_size(0);
    i_48  =  i_47 + i_4;
    z_49  =  i_5 == 0;
    if(z_49)
    {
      // BLOCK 5
      f_50 = localBuffer[get_local_id(0)];
      l_51  =  (long) i_12;
      l_52  =  l_51 << 2;
      l_53  =  l_52 + 24L;
      ul_54  =  ul_1 + l_53;
      *((__global float *) ul_54)  =  f_50;
    }
    else
    {
      // BLOCK 6
    }
    // BLOCK 7 MERGES [6 5 ]
    i_55  =  i_48;
    i_4  =  i_55;
    
  // BLOCK 12
  return;
}