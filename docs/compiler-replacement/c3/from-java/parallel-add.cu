extern "C" __global__ void ParallelKernels_vectorAdd(long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *a, unsigned char *b, unsigned char *c)
{
  int v1;
  int i;
  long long v8;
  float v10;
  float v12;
  v1 = (int) (gridDim.x * blockDim.x);  // ParallelKernels.java:33
  i = (int) (blockIdx.x * blockDim.x + threadIdx.x);
  while (i < 1048576) {
    v8 = (long long) i * 4LL + 16LL;  // ParallelKernels.java:34
    v10 = *((float *) (a + v8));  // ParallelKernels.java:34
    v12 = *((float *) (b + v8));  // ParallelKernels.java:34
    *((float *) (c + v8)) = v10 + v12;  // ParallelKernels.java:34
    i = i + v1;
  }
  return;
}
