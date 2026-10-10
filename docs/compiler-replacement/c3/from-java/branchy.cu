extern "C" __global__ void C3Kernels_compute(long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *in, unsigned char *out)
{
  int i;
  float *in_data, *out_data;
  float x, v;
  i = (int) (blockIdx.x * blockDim.x + threadIdx.x);  // C3Kernels.java:40
  if (i < (int) *((long long *) in)) {
    in_data = (float *) (in + 16LL);  // C3Kernels.java:42
    x = in_data[i];  // C3Kernels.java:42
    if (x > 0.0f) {
      v = sqrtf(x) * 2.0f;  // C3Kernels.java:45
    } else {
      v = -x;  // C3Kernels.java:47
    }
    out_data = (float *) (out + 16LL);  // C3Kernels.java:49
    out_data[i] = v;  // C3Kernels.java:49
  }
}
