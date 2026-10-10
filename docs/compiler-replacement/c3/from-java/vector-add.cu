extern "C" __global__ void C3Kernels_add(long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *a, unsigned char *b, unsigned char *c)
{
  int a_len, gsize_x, i;
  float *a_data, *b_data, *c_data;
  i = (int) (blockIdx.x * blockDim.x + threadIdx.x);  // C3Kernels.java:34
  a_len = (int) *((long long *) a);  // C3Kernels.java:34
  gsize_x = (int) (gridDim.x * blockDim.x);  // C3Kernels.java:34
  a_data = (float *) (a + 16LL);  // C3Kernels.java:35
  b_data = (float *) (b + 16LL);  // C3Kernels.java:35
  c_data = (float *) (c + 16LL);  // C3Kernels.java:35
  while (i < a_len) {
    c_data[i] = a_data[i] + b_data[i];  // C3Kernels.java:35
    i = i + gsize_x;  // C3Kernels.java:34
  }
}
