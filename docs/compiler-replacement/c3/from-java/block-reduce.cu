extern "C" __global__ void C3Kernels_blockSum(long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *in, unsigned char *out)
{
  __shared__ float scratch[256];
  int lid;
  int grp;
  float v6;
  unsigned char *v9;
  int s;
  int s_1;
  float v14;
  float v19;
  int s_2;
  float v22;
  lid = (int) threadIdx.x;  // C3Kernels.java:55
  grp = (int) blockIdx.x;  // C3Kernels.java:56
  v6 = *((float *) (in + ((long long) (int) (blockIdx.x * blockDim.x + threadIdx.x) * 4LL + 16LL)));  // C3Kernels.java:59
  v9 = (unsigned char *) scratch + (long long) lid * 4LL;  // C3Kernels.java:59
  *((float *) v9) = v6;  // C3Kernels.java:59
  __syncthreads();  // C3Kernels.java:60
  s = (int) blockDim.x / 2;  // C3Kernels.java:61
  s_1 = s;
  while (s_1 > 0) {
    if (lid < s_1) {
      v14 = *((float *) v9);  // C3Kernels.java:63
      v19 = *((float *) ((unsigned char *) scratch + (long long) (lid + s_1) * 4LL));  // C3Kernels.java:63
      *((float *) v9) = v14 + v19;  // C3Kernels.java:63
    }
    __syncthreads();  // C3Kernels.java:65
    s_2 = s_1 / 2;  // C3Kernels.java:61
    s_1 = s_2;
  }
  if (lid == 0) {
    v22 = *((float *) (unsigned char *) scratch);  // C3Kernels.java:68
    *((float *) (out + ((long long) grp * 4LL + 16LL))) = v22;  // C3Kernels.java:68
  }
  return;
}
