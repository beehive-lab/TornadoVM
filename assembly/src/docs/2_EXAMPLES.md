# Examples in TornadoVM

In the TornadoVM SDK you can find a number of examples in the `examples` directory.

This document describes how to program and run a full example in TornadoVM.


## 1. Run a simple example within TornadoVM: Vector Addition


Below you can find a snapshot of the `TestArrays` example code in TornadoVM (full code listing can be found in the `examples` directory).  

In this example, we will run the `vectorAdd` method on a heterogeneous device. TornadoVM will dynamically compile and run the Java code (of the `vectorAdd` method) to an OpenCL/CUDA device. During the execution process, the code will be compiled from Java bytecode to OpenCL C/PTX and afterwards it will run on the OpenCL/CUDA compatible device, transparently.

As you can see in the example below, the accelerated `vectorAdd` method performs a double vector addition. Furthermore, it does not differ at all from a vanilla sequential Java implementation of the method. The only difference is the addition of the `@Parallel` annotation that instructs TornadoVM that the loop has to be computed in parallel (i.e. using the global identifier in OpenCL).

The `testVectorAddition` method prepares the input data and creates a TornadoVM `task`. TornadoVM `tasks` cannot execute directly; instead they must be part of a `TaskSchedule`. This is a design choice allowing a number of optimizations, such as task pipelining and parallelism, to be performed. Furthermore, `TaskSchedules` define which parameters are copied in and out from a device.

Once the method `execute` is invoked, TornadoVM builds the data dependency graph, compiles the referenced Java method to OpenCL C/PTX, and executes the generated application on the available OpenCL/CUDA device.


```java
import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

public class VectorAddFloat {

 private static void vectorAdd(float[] a, float[] b, float[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void main(String[] args) {
        int size = Integer.parseInt(args[0]);

        float[] a = new float[size];
        float[] b = new float[size];
        float[] c = new float[size];
        float[] result = new float[size];

        Arrays.fill(a, 450f);
        Arrays.fill(b, 20f);

        //@formatter:off
        TaskSchedule task = new TaskSchedule("s0")
                .task("t0", VectorAddFloat::vectorAdd, a, b, c)
        .streamOut(c);
        //@formatter:on

        task.execute();
        vectorAdd(a, b, result);
        boolean wrongResult = false;
        for (int i = 0; i < c.length; i++) {
            if (c[i] != result[i]) {
                wrongResult = true;
                break;
            }
        }
        if (!wrongResult) {
            System.out.println("Test success");
        } else {
            System.out.println("Result is wrong");
        }
    }
}


```

## 2. Compiling and Running with TornadoVM SDK

The example above is provided in the `examples` directory.
To compile with TornadoVM SDK, there is a utility command that sets all the `CLASSPATHs` to use TornadoVM.
Alternatively, you can use the standard JDK 1.8 and define all jars in `share/java/tornado` into your `CLASSPATHs`.


```bash
$ javac.py examples/TestTornado.java
```

To run, just execute `tornado`. If you want to see the auto-generated OpenCL C/PTX code, you can run with the following option:


```bash
$ tornado --printKernel --debug examples/TestTornado
```

The `--debug` option will print the device on which the kernel was executed (e.g. GPU or CPU).

Use the following option to print the ids of the Tornado devices:

```bash
tornado --devices
```
Tornado device output corresponds to:
```bash
Tornado device=<driverNumber>:<deviceNumber>
```
You will see an example output similar to the following:
```bash
Number of Tornado drivers: 1
Number of devices: 3

Tornado device=0:0
    NVIDIA CUDA -- GeForce GTX 1050
Tornado device=0:1
    Intel(R) OpenCL -- Intel(R) HD Graphics
Tornado device=0:2
    Intel(R) OpenCL -- Intel(R) Core(TM) i7-7700HQ CPU @ 2.80GHz
```

To run on a specific device, use the following option:

```bash
 -D<s>.<t>.device=<driverNumber>:<deviceNumber>
```


In order to run your code on a device of your choice you can issue:

```bash
$ tornado -Ds0.device=0:1 --debug examples/TestTornado
```

This will run TaskSchedule (s0) on the device 1 (Intel HD Graphics).
Similarly, you can execute the code on the rest of the devices.


## 3. Vector Addition using Vector Types

The TornadoVM API exposes a set of data structures to developers to use specific vector operations such as addition, multiplication, etc.
The simple algorithm of vector addition can be rewritten to use the TornadoVM vector types. The TornadoVM JIT compiler will generate OpenCL vector types that match the TornadoVM vector types.


**Note**: Regarding the generated PTX code, TornadoVM lowers vector operations to individual PTX variables. This is due to an issue with the memory alignment in PTX.

The following snippet shows the vector addition example using the TornadoVM vector types.


```java
public static void addVectorFloat4(VectorFloat4 a, VectorFloat4 b,
                                   VectorFloat4 results) {
  for (@Parallel int i = 0; i < a.getLength(); i++) {
    results.set(i, Float4.add(a.get(i), b.get(i)));
  }
}
```

The type `VectorFloat4` is a collection in TornadoVM, that contains a list of `Float4` element types.
When TornadoVM compiles this code to OpenCL, it will use the OpenCL type `float4`.
Note that `Float4` provides a static method called `add`.
These are intrinsics to the compiler.


TornadoVM exposes `Float2`, `Float3`, `Float4`, `Float6` and `Float8` vector types.
Vector operations are also exposed for `int` and `double` types (e.g. `Double8`, `Int4`).


The following code shows a snippet of the generated OpenCL C code using the vector types.
First, it loads the data from global memory to local memory for the two input arrays.
Then, it performs the addition and finally stores the result in the new position in global memory.

```c
v4f_18 = vload4(0, (__global float *) ul_17);  // <- float4 load
ul_19  =  ul_1 + 24L;
ul_20  =  *((__global ulong *) ul_19);
ul_21  =  ul_20 + l_16;
v4f_22  =  vload4(0, (__global float *) ul_21);
ul_23  =  ul_2 + 24L;
ul_24  =  *((__global ulong *) ul_23);
ul_25  =  ul_24 + l_16;
f_26  =  v4f_18.s0 + v4f_22.s0;                // <- float4 computation
f_27  =  v4f_18.s1 + v4f_22.s1;
f_28  =  v4f_18.s2 + v4f_22.s2;
f_29  =  v4f_18.s3 + v4f_22.s3;
v4f_30  =  (float4)(f_26, f_27, f_28, f_29);   
vstore4(v4f_30, 0, (__global float *) ul_25);  // <- float4 store
```



## 4. Mandelbrot

TornadoVM allows nested `@Parallel` loops as follows:

```java
private static void mandelbrotTornado(int size, short[] output) {
    final int iterations = 10000;
    float space = 2.0f / size;

    // This will be mapped to 2D kernel in OpenCL
    for (@Parallel int i = 0; i < size; i++) {
        for (@Parallel int j = 0; j < size; j++) {
            float Zr = 0.0f;
            float Zi = 0.0f;
            float Cr = (1 * j * space - 1.5f);
            float Ci = (1 * i * space - 1.0f);

            float ZrN = 0;
            float ZiN = 0;
            int y = 0;

            for (y = 0; y < iterations; y++) {
                float s = ZiN + ZrN;
                if (s > 4.0f) {
                    break;
                } else {
                Zi = 2.0f * Zr * Zi + Ci;
                Zr = 1 * ZrN - ZiN + Cr;
                ZiN = Zi * Zi;
                ZrN = Zr * Zr;

                }

            }
            short r = (short) ((y * 255) / iterations);
            output[i * size + j] = r;
        }
    }
}

```


## 5. Parallel Breadth-First Search (BFS) within TornadoVM

The following code shows the core method for the parallel BFS using TornadoVM.
Note that the only two annotations needed are in the loops to indicate a 2D kernel on the GPU.

This algorithm receives an input adjacency matrix and an array with the current depth (depth per level in a graph)
and updates the depth of the current node.
This is also an iterative algorithm that will keep computing until the variable `h_true` does not change.


```java
private static void runBFS(int[] vertices, int[] adjacencyMatrix, int numNodes,
                           int[] h_true, int[] currentDepth) {

  for (@Parallel int from = 0; from < numNodes; from++) {

      for (@Parallel int to = 0; to < numNodes; to++) {
          int elementAccess = from * numNodes + to;

          if (adjacencyMatrix[elementAccess] == 1) {
              int dfirst = vertices[from];
              int dsecond = vertices[to];
              if ((currentDepth[0] == dfirst) && (dsecond == -1)) {
                  vertices[to] = dfirst + 1;
                  h_true[0] = 0;
              }

              if (BIDIRECTIONAL) {
              	if ((currentDepth[0] == dsecond) && (dfirst == -1)) {
              		vertices[from] = dsecond + 1;
              		h_true[0] = 0;
              	}
              }
          }
      }
  }
}

```

The following Java snippet shows the data preparation, task definition, and invocation in TornadoVM.


```java
   public void tornadoBFS(int rootNode, int numNodes) throws IOException {

        vertices = new int[numNodes];
        adjacencyMatrix = new int[numNodes * numNodes];

        if (SAMPLE) {
            initilizeAdjacencyMatrixSimpleGraph(adjacencyMatrix, numNodes);
        } else {
            generateRandomGraph(adjacencyMatrix, numNodes, rootNode);
        }

        // Step 1: vertices initialisation
        initializeVertices(numNodes, vertices, rootNode);
        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", BFS::initializeVertices, numNodes, vertices, rootNode);
        s0.streamOut(vertices).execute();

        modify = new int[] { 1 };
        Arrays.fill(modify, 1);

        currentDepth = new int[] { 0 };

        TornadoDevice device = TornadoRuntime.getTornadoRuntime()
					      .getDefaultDevice();
        TaskSchedule s1 = new TaskSchedule("s1");
        s1.streamIn(vertices, adjacencyMatrix, modify,currentDepth)
					.mapAllTo(device);
        s1.task("t1", BFS::runBFS, vertices, adjacencyMatrix,
			numNodes, modify, currentDepth);
        s1.streamOut(vertices, modify);

        boolean done = false;

        while (!done) {
            // 2. Parallel BFS
            boolean allDone = true;
            System.out.println("Current Depth: " + currentDepth[0]);
            //runBFS(vertices, adjacencyMatrix, numNodes, modify, currentDepth);
            s1.execute();
            currentDepth[0]++;
            for(int i = 0; i < modify.length; i++) {
                if (modify[i] == 0) {
                    allDone &= false;
                    break;
                }
            }

            if (allDone) {
                done = true;
            }
            Arrays.fill(modify, 1);
        }

        if (PRINT_SOLUTION) {
        	System.out.println("Solution: " + Arrays.toString(vertices));
        }
    }
```


## 6. Resizing input data at runtime

TornadoVM supports dynamic recompilation of expressions when the input data is resized.
To do so, TornadoVM exposes an API call (`taskSchedule.updateReference`).
The re-compilation invalidates the code installed in the code cache and installs a new one with the upcoming data size.


The syntax is as follows:

```java
ts.updateReference(oldReference, newReference);
```

The API call `updateReference` updates all the references to the new data. Additionally, it compiles a new sketcher, because the sketcher specializes pre-compilation depending on the input data size. The code cache is erased and the OpenCL/PTX stack is reset to accommodate the new data.

```java
    float[] a = createArray(1024);
    float[] b = createArray(1024);

    TaskSchedule ts = new TaskSchedule("s0") //
            .streamIn(a) //
            .task("t0", Resize::resize02, a, b) //
            .streamOut(b); //
    ts.execute();

    // Resize data
    float[] c = createArray(512);
    float[] d = createArray(512);

    // Update multiple references
    ts.updateReference(a, c);
    ts.updateReference(b, d);

    ts.execute();
```

Multiple updates are also possible:

```java
ts.updateReference(a, b);
ts.updateReference(c, d);
ts.updateReference(e, f);
```
## 7. Execute a TaskSchedule with multiple tasks on multiple devices

TornadoVM allows users to specify different targeted devices on TaskSchedules with multiple tasks.
If the tasks are not independent, then TornadoVM overrides user preferences, and schedules all tasks on the default device.

The following example showcases an example of a TaskSchedule with three independent tasks.
```java
TaskSchedule parallelFilter = new TaskSchedule("blur") //
            .task("red", BlurFilterImage::compute, redChannel, redFilter, w, h, filter, FILTER_WIDTH) //
            .task("green", BlurFilterImage::compute, greenChannel, greenFilter, w, h, filter, FILTER_WIDTH) //
            .task("blue", BlurFilterImage::compute, blueChannel, blueFilter, w, h, filter, FILTER_WIDTH) //
            .streamOut(redFilter, greenFilter, blueFilter) //

parallelFilter.execute();
```

For each task (red, blue and green) of the TaskSchedule (blur) the device can be specified as in:
```bash
tornado -Dblur.red.device=0:0 -Dblur.green.device=0:1 -Dblur.blue.device=0:2 -m tornado.examples/uk.ac.manchester.tornado.examples.compute.BlurFilter
```

Where device ids (0:0, 0:1 and 0:2) correspond to device ids obtained from:
```bash
$ tornado --devices

Number of Tornado drivers: 1
Total number of OpenCL devices  : 3
Tornado device=0:0
	Intel(R) OpenCL -- Intel(R) Core(TM) i7-9750H CPU @ 2.60GHz
		Global Memory Size: 31.0 GB
		Local Memory Size: 32.0 KB
		Workgroup Dimensions: 3
		Max WorkGroup Configuration: [8192, 8192, 8192]
		Device OpenCL C version: OpenCL C 2.0

Tornado device=0:1
	Intel(R) OpenCL HD Graphics -- Intel(R) Gen9 HD Graphics NEO
		Global Memory Size: 24.8 GB
		Local Memory Size: 64.0 KB
		Workgroup Dimensions: 3
		Max WorkGroup Configuration: [256, 256, 256]
		Device OpenCL C version: OpenCL C 2.0

Tornado device=0:2
	NVIDIA CUDA -- GeForce GTX 1650
		Global Memory Size: 3.8 GB
		Local Memory Size: 48.0 KB
		Workgroup Dimensions: 3
		Max WorkGroup Configuration: [1024, 1024, 64]
		Device OpenCL C version: OpenCL C 1.2
```