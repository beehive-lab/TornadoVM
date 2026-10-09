extern "C" __global__ void Branchy_compute(long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *in, unsigned char *out)
{
  int i;
  long long v0;
  float x;
  float r;
  float v;
  i = (int) (blockIdx.x * blockDim.x + threadIdx.x);  // Branchy.java:30
  v0 = *((long long *) in);
  if (i < (int) v0) {
    x = *((float *) (in + ((long long) i * 4LL + 16LL)));  // Branchy.java:31
    if (x > 0.0f) {
      r = sqrtf(x);  // Branchy.java:33
      v = r * 2.0f;
    } else {
      v = -x;
    }
    *((float *) (out + ((long long) i * 4LL + 16LL))) = v;  // Branchy.java:37
  }
  return;
}
