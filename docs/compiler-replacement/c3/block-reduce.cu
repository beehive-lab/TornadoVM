extern "C" __global__ void Reduce_blockSum(long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *in, unsigned char *out)
{
  __shared__ float scratch[256];
  int gid;
  int lid;
  int grp;
  int lsz;
  float v;
  unsigned char *slot;
  int s;
  float total;
  float mine;
  float other;
  gid = (int) (blockIdx.x * blockDim.x + threadIdx.x);  // Reduce.java:40
  lid = (int) threadIdx.x;  // Reduce.java:41
  grp = (int) blockIdx.x;  // Reduce.java:42
  lsz = (int) blockDim.x;  // Reduce.java:43
  v = *((float *) (in + ((long long) gid * 4LL + 16LL)));  // Reduce.java:45
  slot = (unsigned char *) scratch + (long long) lid * 4LL;
  *((float *) slot) = v;  // Reduce.java:45
  __syncthreads();  // Reduce.java:46
  s = lsz >> 1;
  while (s > 0) {
    if (lid < s) {
      mine = *((float *) slot);  // Reduce.java:49
      other = *((float *) ((unsigned char *) scratch + (long long) (lid + s) * 4LL));  // Reduce.java:49
      *((float *) slot) = mine + other;  // Reduce.java:49
    }
    __syncthreads();  // Reduce.java:51
    s = s >> 1;
  }
  if (lid == 0) {
    total = *((float *) (unsigned char *) scratch);  // Reduce.java:54
    *((float *) (out + ((long long) grp * 4LL + 16LL))) = total;  // Reduce.java:54
  }
  return;
}
