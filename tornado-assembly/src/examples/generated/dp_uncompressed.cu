// CUDA Dynamic Parallelism: one parent thread (n = 1024 elements) launches a child grid that doubles every element, then
// a tail-launched grid that adds one once the parent grid (and so the child) has completed.
extern "C" __global__ void dp_child(long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *a)
{
  const int n = 1024;
  int i = blockIdx.x * blockDim.x + threadIdx.x;
  int *data = (int *) (a + 24L);
  if (i < n) data[i] = data[i] * 2;
}

extern "C" __global__ void dp_tail(long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *a)
{
  const int n = 1024;
  int i = blockIdx.x * blockDim.x + threadIdx.x;
  int *data = (int *) (a + 24L);
  if (i < n) data[i] = data[i] + 1;
}

extern "C" __global__ void dp(long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *a)
{
  const int n = 1024;
  if (blockIdx.x == 0 && threadIdx.x == 0) {
    dim3 grid((n + 127) / 128), block(128);
    dp_child<<<grid, block>>>(_kernel_context, _constant_region, _local_region, _atomics, a);
    dp_tail<<<grid, block, 0, cudaStreamTailLaunch>>>(_kernel_context, _constant_region, _local_region, _atomics, a);
  }
}
