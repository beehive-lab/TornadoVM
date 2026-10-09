extern "C" __global__ void C3Kernels_add(long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *a, unsigned char *b, unsigned char *c)
{
  int i;
  int i_1;
  long long v0;
  float v9;
  float v16;
  int v24;
  i = (int) (blockIdx.x * blockDim.x + threadIdx.x);  // C3Kernels.java:34
  i_1 = i;
  for (;;) {
    v0 = *((long long *) a);  // C3Kernels.java:34
    if (i_1 >= (int) v0) {
      break;
    }
    v9 = *((float *) (a + ((long long) i_1 * 4LL + 16LL)));  // C3Kernels.java:35
    v16 = *((float *) (b + ((long long) i_1 * 4LL + 16LL)));  // C3Kernels.java:35
    *((float *) (c + ((long long) i_1 * 4LL + 16LL))) = v9 + v16;  // C3Kernels.java:35
    v24 = (int) (gridDim.x * blockDim.x);  // C3Kernels.java:34
    i_1 = i_1 + v24;
  }
  return;
}
