# Reductions support

Tornado now supports basic reductions for `int`, `float` and `double` data types for the operators `+` and `*`.
This wiki shows how to program with reduction in Tornado.

More examples can be found in `examples/src/main/java/uk/ac/manchester/tornado/unittests/reductions` directory of the Tornado SDK.

## Example

```java
public static void reductionAddFloats(float[] input, @Reduce float[] result) {
	result[0] = 0.0f;
 	for (@Parallel int i = 0; i < input.length; i++) {
		result[0] += input[i];
 	}
 }
```

The code is very similar to a Java sequential reduction but with `@Reduce` and `@Parallel` annotations.
The `@Reduce` annotation is associated with a variable, in this case, with the `result` float array.
Then, we annotate the loop with `@Parallel`.


The OpenCL JIT compiler generates OpenCL parallel version for this code that can on GPU and CPU.


### Create the TaskSchedule

Tornado generates different OpenCL code depending on target device. 
If the target is the GPU, it performs full reductions within work-groups. 
If the target is the CPU, it performs full reduction within the same thread-id.
Therefore, the size of the output varies depending on the device. 
Line 3 obtains the size of the output when the target device is a GPU.


```java
float[] input = new float[SIZE];
// We specify the target device
int numGroups = TornadoUtils.getSizeReduction(SIZE, TornadoDeviceType.GPU); 

float[] result = new float[numGroups];

Random r = new Random();
IntStream.range(0, SIZE).sequential().forEach(i -> {
    input[i] = r.nextFloat();
});

TaskSchedule task = new TaskSchedule("s0")
	.streamIn(input)
	.task("t0", TestReductionsFloats::reductionAddFloats, input, result)
	.streamOut(result)
	.execute();
```



### Generated Code

The generated GPU-OpenCL C code that corresponds to the previous Java code is as follows:


```c
#pragma OPENCL EXTENSION cl_khr_fp64 : enable  
__kernel void reductionAddFloats(__global uchar *_heap_base, ulong _frame_base, __constant uchar *_constant_region, __local uchar *_local_region, __global uchar *_private_region)
{
  float f_31, f_30, f_36, f_24; 
  int i_21, i_20, i_19, i_18, i_17, i_16, i_15, i_14, i_9, i_41, i_8, i_7, i_6, i_5, i_4, i_3, i_34, i_33, i_32, i_25, i_22; 
  bool z_23, z_35; 
  ulong ul_40, ul_0, ul_13, ul_29, ul_1, ul_2; 
  long l_38, l_39, l_37, l_12, l_28, l_10, l_26, l_11, l_27; 

  __global ulong *_frame = (__global ulong *) &_heap_base[_frame_base];


  // BLOCK 0
  ul_0  =  (ulong) _frame[6];
  ul_1  =  (ulong) _frame[7];
  ul_2  =  ul_1 + 24L;
  *((__global float *) ul_2)  =  0.0F;
  i_3  =  get_global_id(0);
  // BLOCK 1 MERGES [0 7 ]
  i_4  =  i_3;
  for(;i_4 < 8192;)  {
    // BLOCK 2
    i_5  =  get_local_id(0);
    i_6  =  get_local_size(0);
    i_7  =  get_group_id(0);
    i_8  =  i_6 * i_7;
    i_9  =  i_8 + i_5;
    l_10  =  (long) i_9;
    l_11  =  l_10 << 2;
    l_12  =  l_11 + 24L;
    ul_13  =  ul_0 + l_12;
    i_14  =  i_6 >> 31;
    i_15  =  i_14 >> 31;
    i_16  =  i_15 + i_6;
    i_17  =  i_16 >> 1;
    // BLOCK 3 MERGES [2 11 ]
    i_18  =  i_17;
    for(;i_18 >= 1;) {     // OpenCL reduction
      // BLOCK 8
      barrier(CLK_LOCAL_MEM_FENCE);
      i_19  =  i_18 >> 31;
      i_20  =  i_19 >> 31;
      i_21  =  i_20 + i_18;
      i_22  =  i_21 >> 1;
      z_23  =  i_5 < i_18;
      if(z_23)
      {
        // BLOCK 9
        f_24  =  *((__global float *) ul_13);
        i_25  =  i_9 + i_18;
        l_26  =  (long) i_25;
        l_27  =  l_26 << 2;
        l_28  =  l_27 + 24L;
        ul_29  =  ul_0 + l_28;
        f_30  =  *((__global float *) ul_29);
        f_31  =  f_24 + f_30;
        *((__global float *) ul_13)  =  f_31;
      }
      else
      {
        // BLOCK 10
      }
      // BLOCK 11 MERGES [10 9 ]
      i_32  =  i_22;
      i_18  =  i_32;
    }
    // BLOCK 4
    barrier(CLK_GLOBAL_MEM_FENCE);
    i_33  =  get_global_size(0);
    i_34  =  i_33 + i_4;
    z_35  =  i_5 == 0;
    if(z_35)    // final sync
    {
      // BLOCK 5
      f_36  =  *((__global float *) ul_13);
      l_37  =  (long) i_7;
      l_38  =  l_37 << 2;
      l_39  =  l_38 + 24L;
      ul_40  =  ul_1 + l_39;
      *((__global float *) ul_40)  =  f_36;
    }
    else
    {
      // BLOCK 6
    }
    // BLOCK 7 MERGES [6 5 ]
    i_41  =  i_34;
    i_4  =  i_41;
  }
  // BLOCK 12
  return;
}

```

The generated reduction computes a partial reduction.
It computes a full reduction per work-group on the GPU. 
The final reduction will be compute on the CPU for all the work-groups into a single scalar value. 

### Final reduction

The following code shows the final reduction on the CPU. 

```java
for (int i = 1; i < numGroups; i++) {
	result[0] += result[i];
}
```

### Complete Java example

```java
public void reductionAddFloats(float[] input, @Reduce float[] result) {
    result[0] = 0.0f;
    for (@Parallel int i = 0; i < input.length; i++) {
        result[0] += input[i];
    }
}

public void testSumFloats() {
    float[] input = new float[SIZE];
    int numGroups = TornadoUtils.getSizeReduction(SIZE, TornadoDeviceType.GPU);

    float[] result = new float[numGroups];
    Random r = new Random();
    IntStream.range(0, SIZE).sequential().forEach(i -> {
        input[i] = r.nextFloat();
    });

    TaskSchedule task = new TaskSchedule("s0")
       .streamIn(input)
       .task("t0", this::reductionAddFloats, input, result)
       .streamOut(result);
       .execute();

    for (int i = 1; i < numGroups; i++) {
        result[0] += result[i];
    }
    System.out.println(Arrays.toString(result[0]));
}
```

## Map/Reduce

This section shows an example of how to perform map/reduce operations within Tornado. 
Each of the operation corresponds to a task as follows in the next example:


```java
public static void map01(int[] a, int[] b, int[] c) {
    for (@Parallel int i = 0; i < a.length; i++) {
        c[i] = a[i] + b[i];
    }
}

public static void reduce01(int[] c, @Reduce int[] result) {
    result[0] = 0;
    for (@Parallel int i = 0; i < c.length; i++) {
        result[0] += c[i];
    }
}

public void testMapReduce() {
    int[] a = new int[BIG_SIZE];
    int[] b = new int[BIG_SIZE];
    int[] c = new int[BIG_SIZE];

    int numGroups = TornadoUtils.getSizeReduction(BIG_SIZE, TornadoDeviceType.GPU);
    int[] result = null;

    OCLDeviceType deviceType = getDefaultDeviceType();
    switch (deviceType) {
        case CL_DEVICE_TYPE_CPU:
            result = new int[Runtime.getRuntime().availableProcessors()];
            break;
        case CL_DEVICE_TYPE_GPU:
            result = new int[numGroups];
            break;
    }

    Random r = new Random();

    IntStream.range(0, BIG_SIZE).parallel().forEach(i -> {
        a[i] = 10;
        b[i] = 2;
    });

    new TaskSchedule("s0")
        .streamIn(a, b, c)
        .task("t0", TestReductionsIntegers::map01, a, b, c)
        .task("t1", TestReductionsIntegers::reduce01, c, result)
        .streamOut(result)
        .execute();        

    for (int i = 1; i < result.length; i++) {
        result[0] += result[i];
    }
}
```

## Reduction with dependencies

Tornado also supports reductions using data dependencies. 
The next example illustrates this use:


```java
public static void reductionAddDoubles3(double[] inputA, double[] inputB, @Reduce double[] result) {
   double error = 2f;
   for (@Parallel int i = 0; i < inputA.length; i++) {
       result[0] += (error * (inputA[i] + inputB[i]));
   }
}

```

### Limitations and known issues

There are a few limitations concerning reductions. 
Reductions have been tested on Intel CPU (using the Intel driver) and NVIDIA GPUs. 
For AMD GPUs, we get different results due to precision errors.
We are currently investigating these cases. 

 




