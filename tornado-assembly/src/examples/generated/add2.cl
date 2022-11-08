#pragma OPENCL EXTENSION cl_khr_fp64 : enable  
__kernel void add(__global uchar *_heap_base, 
                ulong _frame_base, 
                __constant uchar *_constant_region, 
                __local uchar *_local_region, 
                __global int *_atomics) 
{
    __global ulong *frame = (__global ulong *) &_heap_base[_frame_base];
    ulong4 args = vload4(0, &frame[3]);

    __global double* inputA = (__global int *) (args.x + 24);
    __global double* inputB = (__global int *) (args.y + 24);
    __global double* output = (__global int *) (args.z + 24);

    size_t idx = get_global_id(0);
    output[idx] = inputA[idx] + inputB[idx];
}

