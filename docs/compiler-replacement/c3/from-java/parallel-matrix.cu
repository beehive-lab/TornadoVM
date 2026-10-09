extern "C" __global__ void ParallelKernels_matrixAdd(long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *a, unsigned char *b, unsigned char *c, int rows, int cols)
{
  int v1;
  int v2;
  int v3;
  int i;
  int v5;
  int j;
  long long v12;
  float v14;
  float v16;
  v1 = (int) (blockIdx.x * blockDim.x + threadIdx.x);  // ParallelKernels.java:40
  v2 = (int) (gridDim.x * blockDim.x);  // ParallelKernels.java:40
  v3 = (int) (gridDim.y * blockDim.y);  // ParallelKernels.java:39
  i = (int) (blockIdx.y * blockDim.y + threadIdx.y);
  while (i < 512) {
    v5 = i * 1024;  // ParallelKernels.java:41
    j = v1;
    while (j < 1024) {
      v12 = (long long) (v5 + j) * 4LL + 16LL;  // ParallelKernels.java:41
      v14 = *((float *) (a + v12));  // ParallelKernels.java:41
      v16 = *((float *) (b + v12));  // ParallelKernels.java:41
      *((float *) (c + v12)) = v14 + v16;  // ParallelKernels.java:41
      j = j + v2;
    }
    i = i + v3;
  }
  return;
}
