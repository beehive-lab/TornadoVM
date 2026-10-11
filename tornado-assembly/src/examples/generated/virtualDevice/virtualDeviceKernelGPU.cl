#pragma OPENCL EXTENSION cl_khr_fp64 : enable
#pragma OPENCL EXTENSION cl_khr_fp16 : enable
#pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable

static inline float java_fmaxf(float a, float b) {
  if (isnan(a) || isnan(b)) return as_float(0x7fc00000);
  if (a == 0.0f && b == 0.0f) return as_float(as_int(a) & as_int(b));
  return a > b ? a : b;
}

__kernel void TestVirtualDeviceKernel_maxReduction(__global long *_kernel_context, __constant uchar *_constant_region, __local uchar *_local_region, __global int *_atomics, __global unsigned char *input, __global unsigned char *result)
{
  __local float reduce_scratch[1024];
  int i, lid_x, stride;
  __global float *input_data, *result_data;
  float partial;
  i = (int) get_global_id(0);  // TestVirtualDeviceKernel.java:56
  input_data = (__global float *) (input + 16L);  // TestVirtualDeviceKernel.java:57
  lid_x = (int) get_local_id(0);  // TestVirtualDeviceKernel.java:57
  reduce_scratch[lid_x] = input_data[i];  // TestVirtualDeviceKernel.java:57
  barrier(CLK_LOCAL_MEM_FENCE);  // TestVirtualDeviceKernel.java:57
  stride = ((int) get_local_size(0)) >> 1;
  while (stride > 0) {
    if (lid_x < stride) {
      reduce_scratch[lid_x] = java_fmaxf(reduce_scratch[lid_x], reduce_scratch[lid_x + stride]);  // TestVirtualDeviceKernel.java:57
    }
    barrier(CLK_LOCAL_MEM_FENCE);  // TestVirtualDeviceKernel.java:57
    stride = stride >> 1;
  }
  partial = reduce_scratch[0];  // TestVirtualDeviceKernel.java:57
  if (lid_x == 0) {
    result_data = (__global float *) (result + 16L);  // TestVirtualDeviceKernel.java:57
    result_data[((int) get_group_id(0)) + 1] = partial;  // TestVirtualDeviceKernel.java:57
  }
}

#pragma OPENCL EXTENSION cl_khr_fp64 : enable
#pragma OPENCL EXTENSION cl_khr_fp16 : enable
#pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable

static inline float java_fmaxf(float a, float b) {
  if (isnan(a) || isnan(b)) return as_float(0x7fc00000);
  if (a == 0.0f && b == 0.0f) return as_float(as_int(a) & as_int(b));
  return a > b ? a : b;
}

__kernel void ReduceFactory_rMax(__global long *_kernel_context, __constant uchar *_constant_region, __local uchar *_local_region, __global int *_atomics, __global unsigned char *array, int size)
{
  __global unsigned char *array_data;
  float acc, acc_1, acc_2, acc_3, acc_4, acc_5, acc_6, acc_7, acc_8;
  array_data = array + 16L;  // ReduceFactory.java:182
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

