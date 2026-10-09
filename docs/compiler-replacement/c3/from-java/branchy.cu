extern "C" __global__ void C3Kernels_compute(long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *in, unsigned char *out)
{
  int i;
  long long v0;
  long long v7;
  float x;
  float v_2;
  i = (int) (blockIdx.x * blockDim.x + threadIdx.x);  // C3Kernels.java:40
  v0 = *((long long *) in);  // C3Kernels.java:41
  if (i < (int) v0) {
    v7 = (long long) i * 4LL + 16LL;  // C3Kernels.java:42
    x = *((float *) (in + v7));  // C3Kernels.java:42
    if (x > 0.0f) {
      v_2 = sqrtf(x) * 2.0f;
    } else {
      v_2 = -x;
    }
    *((float *) (out + v7)) = v_2;  // C3Kernels.java:49
  }
  return;
}
