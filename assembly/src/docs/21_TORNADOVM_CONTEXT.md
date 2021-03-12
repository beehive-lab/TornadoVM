# TornadoVMContext: Advanced programming features on top of the TaskSchedule

**Definition**
TornadoVMContext is a Java object exposed by the TornadoVM API in order to expose the low-level programming features provided by heterogeneous programming frameworks (e.g. OpenCL, CUDA) to the developers. TornadoVMContext provides a Java API that is transparently translated to both OpenCL and PTX by the TornadoVM JIT compiler. The main difference with the TaskSchedule API is that the tasks within a TaskSchedule that use TornadoVMContext must be GridTasks.

## TornadoVMContext Features

The following table presents the available features that TornadoVM exposes in Java along with the respective OpenCL and CUDA PTX terminology for kernel indexing.

| TornadoVMContext    | OpenCL           | PTX                             |
| ------------------- | ---------------- | ------------------------------- |
| getX() | get_global_id(0) | blockIdx * blockDim.x + threadIdx |
| getY() | get_global_id(1) | blockIdy * blockDim.y + threadIdy |
| getZ() | get_global_id(2) | blockIdz * blockDim.z + threadIdz |
| getLocalGroupSize() | get_local_size() | blockDim |
| localBarrier | barrier(CLK_LOCAL_MEM_FENCE) | barrier.sync |
| globalBarrier | barrier(CLK_GLOBAL_MEM_FENCE) | barrier.sync |
| int[] array = allocateIntLocalArray(size) | __local int array[size] | .shared .s32 array[size] |
| float[] array = allocateFloatLocalArray(size) | __local float array[size] | .shared .s32 array[size] |
| long[] array = allocateLongLocalArray(size) | __local long array[size] | .shared .s64 array[size] |
| float[] array = allocateFloatLocalArray(size) | __local double array[size] | .shared .s64 array[size] |

## Examples
The following [example](https://github.com/beehive-lab/TornadoVM/blob/feature/new-api/examples/src/main/java/uk/ac/manchester/tornado/examples/compute_tornadovmcontext/MatrixMultiplication2DV2.java) is the Matrix Multiplication implementation that uses Thread Attributes, Local Memory allocation and Loop Tilling.

1. The TornadoVMContext object is passed as an argument in the method that will be accelerated.
This implementation follows the OpenCL implementation description provided in [https://github.com/cnugteren/myGEMM](https://github.com/cnugteren/myGEMM).
```java
public static void matrixMultiplicationNewApi(TornadoVMContext context, final float[] A, final float[] B, final float[] C, final int size) {
        int row = context.localIdx;
        int col = context.localIdy;
        int globalRow = TS * context.groupIdx + row;
        int globalCol = TS * context.groupIdy + col;

        float[] aSub = context.allocateFloatLocalArray(TS * TS);
        float[] bSub = context.allocateFloatLocalArray(TS * TS);

        float sum = 0;

        // Loop over all tiles
        int numTiles = size / TS;
        for (int t = 0; t < numTiles; t++) {

            // Load one tile of A and B into local memory
            int tiledRow = TS * t + row;
            int tiledCol = TS * t + col;
            aSub[col * TS + row] = A[tiledCol * size + globalRow];
            bSub[col * TS + row] = B[globalCol * size + tiledRow];

            // Synchronise to make sure the tile is loaded
            context.localBarrier();

            // Perform the computation for a single tile
            for (int k = 0; k < TS; k++) {
                sum += aSub[k * TS + row] * bSub[col * TS + k];
            }
            // Synchronise before loading the next tile
            context.globalBarrier();
        }

        // Store the final result in C
        C[(globalCol * size) + globalRow] = sum;
    }
```

2. A TornadoVM program that uses the TornadoVMContext features, it must link the TornadoVMContext with a WorkerGrid (1D/2D). This is necessary in order to obtain awareness about the dimensions and the sizes of the threads that will be deployed. Therefore, TornadoVMContext can only work with GridTasks.
```java
WorkerGrid workerGrid = new WorkerGrid2D(size, size);
GridTask gridTask = new GridTask("s0.t0", workerGrid);
TornadoVMContext context = new TornadoVMContext(workerGrid);
workerGrid.setGlobalWork(size, size, 1);
workerGrid.setLocalWork(32, 32, 1);
```

3. Define the TaskSchedule and execute.
```java
TaskSchedule t = new TaskSchedule("s0") //
                .task("t0", MatrixMultiplication2DV2::matrixMultiplicationNewApi, context, matrixA, matrixB, matrixC, size) //
                .streamOut(matrixC);
t.execute(gridTask);
```

## Combined Tasks in a TaskSchedule
The TornadoVM TaskSchedule can be composed of multiple tasks which can either exploit the TornadoVMContext features or adhere to the original TornadoVM annotations (@Parallel/@Reduce). Such scenarios exist in the `TestCobinedTaskSchedule` [unit-tests](https://github.com/beehive-lab/TornadoVM/blob/feature/new-api/unittests/src/main/java/uk/ac/manchester/tornado/unittests/api/TestCombinedTaskSchedule.java).
For instance, the `combinedApiDifferentWorkerGrids` test case executes one TaskSchedule (`s0`) composed of three tasks: 
* `t0`: The `vectorAddV2` method adds two vectors by utilizing the thread attributes provided by TornadoVMContext.
* `t1`: The `vectorMulV2` method multiplies the result of the first task with a second vector, while also utilizing the thread attributes provided by TornadoVMContext.
* `t2`: The `vectorSubV1` method performs the subtraction the result of the second task with a third vector.

```java
WorkerGrid workerT0 = new WorkerGrid1D(size);
        WorkerGrid workerT1 = new WorkerGrid1D(size);
        GridTask gridTask = new GridTask();
        gridTask.setWorkerGrid("s0.t0", workerT0);
        gridTask.setWorkerGrid("s0.t1", workerT1);
        TornadoVMContext context = new TornadoVMContext(workerT1);

        TaskSchedule s0 = new TaskSchedule("s0").streamIn(a, b).task("t0", TestCombinedTaskSchedule::vectorAddV2, context, a, b, cTornado)
                .task("t1", TestCombinedTaskSchedule::vectorMulV2, context, cTornado, b, cTornado).task("t2", TestCombinedTaskSchedule::vectorSubV1, cTornado, b, cTornado).streamOut(cTornado);
        // Change the Grid
        workerT0.setGlobalWork(size, 1, 1);
        workerT0.setLocalWork(size / 2, 1, 1);
        workerT1.setGlobalWork(size, 1, 1);
        workerT1.setLocalWorkToNull();
        s0.execute(gridTask);
  ```

In this test case, each of the first two tasks uses a separate WorkerGrid. On the other hand, the third task does not use a WorkerGrid, but it relies on the TornadoVM Runtime for the scheduling of the threads. The execution of the whole TaskSchedule is invoked by `s0.execute(gridTask);`.
