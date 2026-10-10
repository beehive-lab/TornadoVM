extern "C" __global__ void ParallelKernels_matrixAdd(long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *a, unsigned char *b, unsigned char *c, int rows, int cols)
{
  int gid_x, gsize_x, gsize_y, i, i_off, j;
  float *a_data, *b_data, *c_data;
  gid_x = (int) (blockIdx.x * blockDim.x + threadIdx.x);  // ParallelKernels.java:40
  gsize_x = (int) (gridDim.x * blockDim.x);  // ParallelKernels.java:40
  gsize_y = (int) (gridDim.y * blockDim.y);  // ParallelKernels.java:39
  a_data = (float *) (a + 16LL);  // ParallelKernels.java:41
  b_data = (float *) (b + 16LL);  // ParallelKernels.java:41
  c_data = (float *) (c + 16LL);  // ParallelKernels.java:41
  i = (int) (blockIdx.y * blockDim.y + threadIdx.y);
  while (i < 512) {
    i_off = i * 1024;  // ParallelKernels.java:41
    j = gid_x;
    while (j < 1024) {
      c_data[i_off + j] = a_data[i_off + j] + b_data[i_off + j];  // ParallelKernels.java:41
      j = j + gsize_x;  // ParallelKernels.java:40
    }
    i = i + gsize_y;  // ParallelKernels.java:39
  }
}
