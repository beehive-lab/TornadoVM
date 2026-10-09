extern "C" __global__ void C3Kernels_compute(long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *in, unsigned char *out)
{
  int i;
  long long v0;
  float x;
  float v11;
  float v_2;
  i = (int) (blockIdx.x * blockDim.x + threadIdx.x);  // C3Kernels.java:40
  v0 = *((long long *) in);  // C3Kernels.java:41
  if (i < (int) v0) {
    x = *((float *) (in + ((long long) i * 4LL + 16LL)));  // C3Kernels.java:42
    if (x > 0.0f) {
      v11 = sqrtf(x);  // C3Kernels.java:45
      v_2 = v11 * 2.0f;
    } else {
      v_2 = -x;
    }
    *((float *) (out + ((long long) i * 4LL + 16LL))) = v_2;  // C3Kernels.java:49
  }
  return;
}
