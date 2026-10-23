#pragma OPENCL EXTENSION cl_khr_fp64 : enable
#pragma OPENCL EXTENSION cl_khr_fp16 : enable
#pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable

static inline float java_fmaxf(float a, float b) {
  if ((as_uint(a) & 0x7fffffffu) > 0x7f800000u || (as_uint(b) & 0x7fffffffu) > 0x7f800000u) return as_float(0x7fc00000);
  if (a == 0.0f && b == 0.0f) return as_float(as_int(a) & as_int(b));
  return a > b ? a : b;
}

__kernel void TestVirtualDeviceKernel_maxReduction(__global long *_kernel_context, __constant uchar *_constant_region, __local uchar *_local_region, __global int *_atomics, __global unsigned char *input, __global unsigned char *result)
{
  __local float reduce_scratch[1024];
  long v3, v7, v13;
  __global float *input_data, *result_data;
  int i, lid_x, active, half_;
  float acc, partial;
  v3 = (long) ((int) get_global_size(0));  // TestVirtualDeviceKernel.java:57
  input_data = (__global float *) (input + 24L);  // TestVirtualDeviceKernel.java:57
  i = (int) get_global_id(0);
  acc = as_float(0xff800000);
  while (i < 8192) {
    v7 = (long) i;  // TestVirtualDeviceKernel.java:57
    v13 = v7 + v3;  // TestVirtualDeviceKernel.java:57
    i = (int) (v13 > 2147483647L ? 2147483647L : v13);
    acc = java_fmaxf(acc, input_data[v7]);
  }
  lid_x = (int) get_local_id(0);  // TestVirtualDeviceKernel.java:57
  reduce_scratch[lid_x] = acc;  // TestVirtualDeviceKernel.java:57
  barrier(CLK_LOCAL_MEM_FENCE);  // TestVirtualDeviceKernel.java:57
  active = (int) get_local_size(0);
  while (active > 1) {
    half_ = (int) ((uint) active + (uint) 1) >> 1;  // TestVirtualDeviceKernel.java:57
    if (lid_x < (int) ((uint) active - (uint) half_)) {
      reduce_scratch[lid_x] = java_fmaxf(reduce_scratch[lid_x], reduce_scratch[(int) ((uint) lid_x + (uint) half_)]);  // TestVirtualDeviceKernel.java:57
    }
    barrier(CLK_LOCAL_MEM_FENCE);  // TestVirtualDeviceKernel.java:57
    active = half_;
  }
  partial = reduce_scratch[0];  // TestVirtualDeviceKernel.java:57
  if (lid_x == 0) {
    result_data = (__global float *) (result + 24L);  // TestVirtualDeviceKernel.java:57
    result_data[(int) ((uint) ((int) get_group_id(0)) + (uint) 1)] = partial;  // TestVirtualDeviceKernel.java:57
  }
}

#pragma OPENCL EXTENSION cl_khr_fp64 : enable
#pragma OPENCL EXTENSION cl_khr_fp16 : enable
#pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable

static inline float java_fmaxf(float a, float b) {
  if ((as_uint(a) & 0x7fffffffu) > 0x7f800000u || (as_uint(b) & 0x7fffffffu) > 0x7f800000u) return as_float(0x7fc00000);
  if (a == 0.0f && b == 0.0f) return as_float(as_int(a) & as_int(b));
  return a > b ? a : b;
}

__kernel void ReduceFactory_rMax(__global long *_kernel_context, __constant uchar *_constant_region, __local uchar *_local_region, __global int *_atomics, __global unsigned char *array, int size)
{
  __global unsigned char *array_data;
  float acc, acc_1, acc_2, acc_3, acc_4, acc_5, acc_6, acc_7, acc_8;
  array_data = array + 24L;  // ReduceFactory.java:182
  acc = *((__global float *) array_data);  // ReduceFactory.java:182
  acc_1 = java_fmaxf(acc, *((__global float *) (array_data + 4L)));  // ReduceFactory.java:184
  acc_2 = java_fmaxf(acc_1, *((__global float *) (array_data + 8L)));  // ReduceFactory.java:184
  acc_3 = java_fmaxf(acc_2, *((__global float *) (array_data + 12L)));  // ReduceFactory.java:184
  acc_4 = java_fmaxf(acc_3, *((__global float *) (array_data + 16L)));  // ReduceFactory.java:184
  acc_5 = java_fmaxf(acc_4, *((__global float *) (array_data + 20L)));  // ReduceFactory.java:184
  acc_6 = java_fmaxf(acc_5, *((__global float *) (array_data + 24L)));  // ReduceFactory.java:184
  acc_7 = java_fmaxf(acc_6, *((__global float *) (array_data + 28L)));  // ReduceFactory.java:184
  acc_8 = java_fmaxf(acc_7, *((__global float *) (array_data + 32L)));  // ReduceFactory.java:184
  *((__global float *) array_data) = acc_8;  // ReduceFactory.java:186
}

