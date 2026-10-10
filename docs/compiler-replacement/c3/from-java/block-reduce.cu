extern "C" __global__ void C3Kernels_blockSum(long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *in, unsigned char *out)
{
  __shared__ float scratch[256];
  int gid, lid, grp, lsz, s;
  float *in_data, *out_data;
  gid = (int) (blockIdx.x * blockDim.x + threadIdx.x);  // C3Kernels.java:54
  lid = (int) threadIdx.x;  // C3Kernels.java:55
  grp = (int) blockIdx.x;  // C3Kernels.java:56
  lsz = (int) blockDim.x;  // C3Kernels.java:57
  in_data = (float *) (in + 16LL);  // C3Kernels.java:59
  scratch[lid] = in_data[gid];  // C3Kernels.java:59
  __syncthreads();  // C3Kernels.java:60
  s = lsz / 2;  // C3Kernels.java:61
  while (s > 0) {
    if (lid < s) {
      scratch[lid] = scratch[lid] + scratch[lid + s];  // C3Kernels.java:63
    }
    __syncthreads();  // C3Kernels.java:65
    s = s / 2;  // C3Kernels.java:61
  }
  if (lid == 0) {
    out_data = (float *) (out + 16LL);  // C3Kernels.java:68
    out_data[grp] = scratch[0];  // C3Kernels.java:68
  }
}
