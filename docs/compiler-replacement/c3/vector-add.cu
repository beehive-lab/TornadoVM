extern "C" __global__ void VectorAdd_add(long long *_kernel_context, unsigned char *_constant_region, unsigned char *_local_region, int *_atomics, unsigned char *a, unsigned char *b, unsigned char *c)
{
  long long v0;
  int n;
  int i0;
  int s;
  int i;
  float x;
  float y;
  v0 = *((long long *) a);  // VectorAdd.java:20
  n = (int) v0;  // VectorAdd.java:20
  i0 = (int) (blockIdx.x * blockDim.x + threadIdx.x);
  s = (int) (gridDim.x * blockDim.x);
  i = i0;
  while (i < n) {
    x = *((float *) (a + ((long long) i * 4LL + 16LL)));  // VectorAdd.java:21
    y = *((float *) (b + ((long long) i * 4LL + 16LL)));  // VectorAdd.java:21
    *((float *) (c + ((long long) i * 4LL + 16LL))) = x + y;  // VectorAdd.java:21
    i = i + s;
  }
  return;
}
