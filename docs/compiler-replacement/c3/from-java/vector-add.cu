extern "C" __global__ void C3Kernels_add(long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *a, unsigned char *b, unsigned char *c)
{
  long long v0;
  int v1;
  int v2;
  int i_1;
  long long v8;
  float v10;
  float v12;
  v0 = *((long long *) a);  // C3Kernels.java:34
  v1 = (int) v0;  // C3Kernels.java:34
  v2 = (int) (gridDim.x * blockDim.x);  // C3Kernels.java:34
  i_1 = (int) (blockIdx.x * blockDim.x + threadIdx.x);
  while (i_1 < v1) {
    v8 = (long long) i_1 * 4LL + 16LL;  // C3Kernels.java:35
    v10 = *((float *) (a + v8));  // C3Kernels.java:35
    v12 = *((float *) (b + v8));  // C3Kernels.java:35
    *((float *) (c + v8)) = v10 + v12;  // C3Kernels.java:35
    i_1 = i_1 + v2;
  }
  return;
}
