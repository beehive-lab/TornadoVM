extern "C" __global__ void C3Kernels_blockSum(long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *in, unsigned char *out)
{
  __shared__ float scratch[256];
  int gid;
  int lid;
  int grp;
  int lsz;
  float v7;
  int s;
  int s_1;
  float v20;
  float v26;
  int s_2;
  float v40;
  gid = (int) (blockIdx.x * blockDim.x + threadIdx.x);  // C3Kernels.java:54
  lid = (int) threadIdx.x;  // C3Kernels.java:55
  grp = (int) blockIdx.x;  // C3Kernels.java:56
  lsz = (int) blockDim.x;  // C3Kernels.java:57
  v7 = *((float *) (in + ((long long) gid * 4LL + 16LL)));  // C3Kernels.java:59
  *((float *) ((unsigned char *) scratch + (long long) lid * 4LL)) = v7;  // C3Kernels.java:59
  __syncthreads();  // C3Kernels.java:60
  s = lsz / 2;  // C3Kernels.java:61
  s_1 = s;
  while (s_1 > 0) {
    if (lid < s_1) {
      v20 = *((float *) ((unsigned char *) scratch + (long long) lid * 4LL));  // C3Kernels.java:63
      v26 = *((float *) ((unsigned char *) scratch + (long long) (lid + s_1) * 4LL));  // C3Kernels.java:63
      *((float *) ((unsigned char *) scratch + (long long) lid * 4LL)) = v20 + v26;  // C3Kernels.java:63
    }
    __syncthreads();  // C3Kernels.java:65
    s_2 = s_1 / 2;  // C3Kernels.java:61
    s_1 = s_2;
  }
  if (lid == 0) {
    v40 = *((float *) ((unsigned char *) scratch + (long long) 0 * 4LL));  // C3Kernels.java:68
    *((float *) (out + ((long long) grp * 4LL + 16LL))) = v40;  // C3Kernels.java:68
  }
  return;
}
