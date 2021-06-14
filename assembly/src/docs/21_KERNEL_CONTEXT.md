# Expressing Kernel Parallelism within TornadoVM

Applications for acceleration via TornadoVM can be programmed using both:

- **loop-parallelism** by annotating loops uging the `@Parallel` TornadoVM annotation, and
- **kernel-parallelism** by using a new object called `KernelContext` that is passed as a parameter.

## Definition

`KernelContext` is a Java object exposed by the TornadoVM API to the developers in order to leverage Kernel Parallel Programming using the existing `TaskSchedule` API. An instance of the `KernelContext` object is passed to each task that uses the kernel-parallel API. Additionally, for all tasks using the `KernelContext` object, the user must provide a Grid of execution threads to run on the parallel device. This grid of threads is similar to the number of threads to be launched using CUDA or OpenCL (Number of threads per block and number of blocks). Examples can be found in
the `Grid` [unit-tests](https://github.com/beehive-lab/TornadoVM/tree/master/unittests/src/main/java/uk/ac/manchester/tornado/unittests/grid).

## KernelContext Features

The following table presents the available features that TornadoVM exposes in Java along with the respective OpenCL and CUDA PTX terminology.

```java
// Note: 
kc = new KernelContext();
```

| TornadoVM KernelContext    | OpenCL           | PTX                             |
| ------------------- | ---------------- | ------------------------------- |
| kc.globalIdx | get_global_id(0) | blockIdx * blockDim.x + threadIdx |
| kc.globalIdy | get_global_id(1) | blockIdy * blockDim.y + threadIdy |
| kc.globalIdz | get_global_id(2) | blockIdz * blockDim.z + threadIdz |
| kc.getLocalGroupSize() | get_local_size() | blockDim |
| kc.localBarrier() | barrier(CLK_LOCAL_MEM_FENCE) | barrier.sync |
| kc.globalBarrier() | barrier(CLK_GLOBAL_MEM_FENCE) | barrier.sync |
| int[] array = kc.allocateIntLocalArray(size) | __local int array[size] | .shared .s32 array[size] |
| float[] array = kc.allocateFloatLocalArray(size) | __local float array[size] | .shared .s32 array[size] |
| long[] array = kc.allocateLongLocalArray(size) | __local long array[size] | .shared .s64 array[size] |
| double[] array = kc.allocateDoubleLocalArray(size) | __local double array[size] | .shared .s64 array[size] |

## Examples

The following [example](https://github.com/beehive-lab/TornadoVM/tree/master/examples/src/main/java/uk/ac/manchester/tornado/examples/kernelContext/compute/MatrixMultiplication2DV2.java) is the Matrix Multiplication implementation using the `KernelContext` object for indexing threads and access to local memory. The following example also makes use of loop tiling.  There are three main steps to leverage the features of the `KernelContext`:

1. The `KernelContext` object is passed as an argument in the method that will be accelerated. This implementation
   follows the OpenCL implementation description provided
   in [https://github.com/cnugteren/myGEMM](https://github.com/cnugteren/myGEMM).

```java
public static void matrixMultiplication(KernelContext context, 
                                       final float[] A, final float[] B, 
                                       final float[] C, final int size) {

    // Index thread in the first dimension ( get_global_id(0) )
    int row = context.localIdx;

    // Index thread in the seconbd dimension ( get_global_id(1) )
    int col = context.localIdy;

    int globalRow = TS * context.groupIdx + row;
    int globalCol = TS * context.groupIdy + col;

    // Create Local Memory via the context
    float[] aSub = context.allocateFloatLocalArray(TS * TS);
    float[] bSub = context.allocateFloatLocalArray(TS * TS);

    float sum = 0;

    // Loop over all tiles
    int numTiles = size/TS;
    for(int t = 0; t < numTiles; t++){

        // Load one tile of A and B into local memory
        int tiledRow = TS * t + row;
        int tiledCol = TS * t + col;
        aSub[col * TS + row] = A[tiledCol * size + globalRow];
        bSub[col * TS + row] = B[globalCol * size + tiledRow];

        // Synchronise to make sure the tile is loaded
        context.localBarrier();

        // Perform the computation for a single tile
        for(int k = 0; k < TS; k++) {
            sum += aSub[k* TS + row] * bSub[col * TS + k];
        }
        // Synchronise before loading the next tile
        context.globalBarrier();
    }

    // Store the final result in C
    C[(globalCol * size) + globalRow] = sum;
}
```

2. A TornadoVM program that uses the `KernelContext` must use the context with a `WorkerGrid` (1D/2D/3D). This is
   necessary in order to obtain awareness about the dimensions and the sizes of the threads that will be deployed.
   **Therefore, `KernelContext` can only work with tasks that are linked with a `GridScheduler`.**

```java
// Create 2D Grid of Threads with a 2D Worker
WorkerGrid workerGrid = new WorkerGrid2D(size, size);

// Create a GridScheduler that associates a task-ID with a worker grid
GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);

// Create the TornadoVM Context
KernelContext context = new KernelContext();

// [Optional] Set the local work size 
workerGrid.setLocalWork(32, 32, 1);
```

3. Define the `TaskSchedule` and execute:

```java
TaskSchedule t = new TaskSchedule("s0")
        .task("t0",MxM::compute,context, matrixA, matrixB, matrixC, size)
        .streamOut(matrixC);

t.execute(gridScheduler);    // Pass the GridScheduler in the execute method
```

## Multiple Tasks in a TaskSchedule with a `WorkerGrid` and `KernelContext`

The TornadoVM Task-Schedule can be composed of multiple tasks which can either exploit the KernelContext features or adhere to the original TornadoVM annotations (`@Parallel`/`@Reduce`). Examples can be found in the `TestCombinedTaskSchedule` [unit-tests](https://github.com/beehive-lab/TornadoVM/tree/master/unittests/src/main/java/uk/ac/manchester/tornado/unittests/KernelContext/api/TestCombinedTaskSchedule.java). For instance, the `combinedApiDifferentWorkerGrids` test case executes one TaskSchedule (`s0`) composed of three tasks:

* `t0`: The `vectorAddV2` method adds two vectors by utilizing the thread attributes provided by KernelContext.
* `t1`: The `vectorMulV2` method multiplies the result of the first task with a second vector, while also utilizing the
  thread attributes provided by KernelContext.
* `t2`: The `vectorSubV1` method performs the subtraction of the result of the second task with a third vector.

```java
// Create Worker 1D Grids 
WorkerGrid workerT0 = new WorkerGrid1D(size);
WorkerGrid workerT1 = new WorkerGrid1D(size);

// Create a unique GridScheduler per TaskSchedule
GridScheduler gridScheduler = new GridScheduler();

// Associate a worker per task within the task-scheduler
gridScheduler.setWorkerGrid("s0.t0", workerT0);
gridScheduler.setWorkerGrid("s0.t1", workerT1);

// Create the KernelContext
KernelContext context = new KernelContext();

// Build the TornadoVM Task-Scheduler
TaskSchedule s0 = new TaskSchedule("s0")
        .streamIn(a, b)
        .task("t0", TestCombinedTaskSchedule::vectorAddV2, context, a, b, cTornado)
        .task("t1", TestCombinedTaskSchedule::vectorMulV2, context, cTornado, b, cTornado)
        .task("t2", TestCombinedTaskSchedule::vectorSubV1, cTornado, b, cTornado)
        .streamOut(cTornado);

// Execute the application
s0.execute(gridScheduler);

// Change the Grid for the next round
workerT0.setGlobalWork(size, 1, 1);
workerT0.setLocalWork(size/2, 1, 1);
workerT1.setGlobalWork(size, 1, 1);
workerT1.setLocalWorkToNull();

s0.execute(gridScheduler);
```

In this test case, each of the first two tasks uses a separate `WorkerGrid`. The third task does not use a `WorkerGrid`, and it relies on the TornadoVM Runtime for the scheduling of the threads. The execution of the whole TaskSchedule is invoked by `s0.execute(gridScheduler);`.

