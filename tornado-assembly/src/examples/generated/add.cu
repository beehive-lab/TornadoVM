extern "C" __global__ void add(long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *a, unsigned char *b, unsigned char *c)
{
  int i_11, i_9, i_15, i_14, i_13, i_3, i_4;
  long l_5, l_7, l_6;
  unsigned long ul_8, ul_10, ul_1, ul_0, ul_2, ul_12;
  // BLOCK 0
  ul_0  =  (unsigned long) a;
  ul_1  =  (unsigned long) b;
  ul_2  =  (unsigned long) c;
  i_3  =  (blockIdx.x*blockDim.x+threadIdx.x);
  // BLOCK 1 MERGES [0 2 ]
  i_4  =  i_3;
  for(;i_4 < 8;)
  {
    // BLOCK 2
    l_5  =  (long) i_4;
    l_6  =  l_5 << 2;
    l_7  =  l_6 + 16L;
    ul_8  =  ul_0 + l_7;
    i_9  =  *(( int *) ul_8);
    ul_10  =  ul_1 + l_7;
    i_11  =  *(( int *) ul_10);
    ul_12  =  ul_2 + l_7;
    i_13  =  i_9 + i_11;
    *(( int *) ul_12)  =  i_13;
    i_14  =  (gridDim.x*blockDim.x);
    i_15  =  i_14 + i_4;
    i_4  =  i_15;
  }  // B1
  // BLOCK 3
  return;
}  //  kernel