extern "C" __global__ void ParallelKernels_vectorAdd(long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *a, unsigned char *b, unsigned char *c)
{
  int gsize_x, i;
  float *a_data, *b_data, *c_data;
  gsize_x = (int) (gridDim.x * blockDim.x);  // ParallelKernels.java:33
  a_data = (float *) (a + 16LL);  // ParallelKernels.java:34
  b_data = (float *) (b + 16LL);  // ParallelKernels.java:34
  c_data = (float *) (c + 16LL);  // ParallelKernels.java:34
  i = (int) (blockIdx.x * blockDim.x + threadIdx.x);
  while (i < 1048576) {
    c_data[i] = a_data[i] + b_data[i];  // ParallelKernels.java:34
    i = i + gsize_x;  // ParallelKernels.java:33
  }
}
