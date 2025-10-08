.. _programming:

Core Programming
===================

TornadoVM exposes to the programmer task-level, data-level and pipeline-level parallelism via a light Application Programming Interface (API).
In addition, TornadoVM uses single-source property, in which the code to be accelerated and the host code live in the same Java program.


Programming in TornadoVM involves the development of four parts:

1. **Data Representation:** TornadoVM offers an API to efficiently allocate data off-heap. These data is automatically managed by the TornadoVM Runtime and the compiler. 
2. **Expressing parallelism within Java methods:** TornadoVM offers two APIs: one for loop parallelization using Java annotations; and a second one for low-level programming using a Kernel API.
   Developers can choose which one to use. The loop API is recommended for non-expert GPU/FPGA programmers.
   The kernel API is recommended for experts GPU programmers than want more control (access to GPU's local memory, barriers, etc.).
3. **Selecting the methods to be accelerated using a Task-Graph API:** once Java methods have been identified for acceleration (either using the loop parallel API or kernel API), Java methods can be grouped together in a graph.
   TornadoVM offers an API to define the data as well as the Java methods to be accelerated.
4. Building an **Execution Plan**: From the task-graphs, developers can accelerate all methods that are indicate in that graph on an accelerator. Additionally, through an execution plan in TornadoVM, developers can change the way TornadoVM offloads and runs the code (e.g., by selecting a specific GPU, enabling the profiler, etc.).

1. Data Representation
-------------------------
TornadoVM offers a set of off-heap types that encapsulate a `Memory Segment <https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/foreign/MemorySegment.html>`_, a contiguous region of memory outside the Java heap. Below is a list of the native array types, with an arrow pointing from the on-heap primitive array types to their off-heap equivalent in TornadoVM.


* ``int[]`` -> ``IntArray``
* ``float[]`` -> ``FloatArray``
* ``double[]`` -> ``DoubleArray``
* ``long[]`` -> ``LongArray``
* ``char[]`` -> ``CharArray``
* ``short[]`` -> ``ShortArray``
* ``byte[]`` -> ``ByteArray``


To allocate off-heap memory using the TornadoVM API, each type offers a constructor with one argument that indicates the number of elements that the Memory Segment will contain.

E.g.:

.. code:: java

   // allocate an off-heap memory segment that will contain 16 int values
   IntArray intArray = new IntArray(16);

Additionally, developers can create an instance of a TornadoVM native array by invoking factory methods for different data representations. In the following examples we will demonstrate the API functions for the ``FloatArray`` type, but the same methods apply for all support native array types. 

.. code:: java

   // from on-heap array to TornadoVM native array
   public static FloatArray fromArray(float[] values);
   // from individual items to TornadoVM native array
   public static FloatArray fromElements(float... values);
   // from Memory Segment to TornadoVM native array
   public static FloatArray fromSegment(MemorySegment segment);
   // from multiple TornadoVM native arrays to single TornadoVM native array
   public static FloatArray concat(FloatArray... arrays);
   // from a slice of a TornadoVM native array
   public FloatArray slice(int offset,int length);
   // from a FloatBuffer to a TornadoVM native array
   public FloatArray fromFloatBuffer(FloatBuffer buffer);

The main methods that the off-heap types expose to manage the Memory Segment of each type are presented in the list below. 

.. code:: java

   public void set(int index, float value) // sets a value at a specific index
      E.g.:
          FloatArray floatArray = new FloatArray(16);
          floatArray.set(0, 10.0f); // at index 0 the value is 10.0f
   public float get(int index) // returns the value of a specific index
      E.g.:
          FloatArray floatArray = FloatArray.fromArray(new float[] {2.0f, 1.0f, 2.0f, 5.0f});
          float floatValue = floatArray.get(3); // returns 5.0f
   public void clear() // sets the values of the segment to 0
      E.g.:
          FloatArray floatArray = new FloatArray(1024);
          floatArray.clear(); // the floatArray contains 1024 zeros
   public void init(float value) // initializes the segment with a specific value
      E.g.:
   	  FloatArray floatArray = new FloatArray(1024);
          floatArray.init(1.0f); // the floatArray contains 1024 ones
   public int getSize() // returns the number of elements in the segment
      E.g.:
          FloatArray floatArray = new FloatArray(16);
          int size = floatArray.getSize(); // returns 16
   public float[] toHeapArray(); // Converts the data from off-heap to on-heap
   public long getNumBytesOfSegment(); // Returns the total number of bytes the underlying Memory Segment occupies, including the header bytes
   public long getNumBytesWithoutHeader(); // Returns the total number of bytes the underlying Memory Segment occupies, excluding the header bytes
   
**NOTE:** The methods ``init()`` and ``clear()`` are essential because, contrary to their counterpart primitive arrays which are initialized by default with 0, the new types contain garbage values when first created.

2. Expressing Parallelism within Java Methods
------------------------------------------------


TornadoVM offloads Java methods to heterogeneous hardware such as GPUs and FPGAs for parallel execution.
Those Java methods usually represents the sequential (single thread) implementation of the work to perform on the accelerator.
However, TornadoVM does not auto-parallelize Java methods.

Thus, TornadoVM needs a hint about how to parallelize the code.
TornadoVM has two APIs to achieve this goal: one for loop parallelization using Java annotations; and a second one for low-level programming using a Kernel API.
Developers can choose which one to use. The loop API is recommended for non-expert GPU/FPGA programmers.


Loop Parallel API
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Compute kernels are written in a sequential form (tasks programmed for a single thread execution).
To express parallelism, TornadoVM exposes two annotations that can be used in loops and parameters:
a) ``@Parallel`` for annotating parallel loops; and b) ``@Reduce`` for annotating parameters used in reductions.

The following code snippet shows a full example to accelerate Matrix-Multiplication using TornadoVM and the loop-parallel API:
The two outermost loops can be parallelizable because there are no data dependencies across different iterations.
Therefore, we can annotate these two loops.
Note that, since TornadoVM maps parallel loops to Parallel ND-Range for OpenCL, CUDA and SPIR-V, developers can benefit
from 1D (annotating one parallel loop), 2D (annotating two consecutive parallel loops) and 3D (annotating 3 consecutive parallel loops) in their Java methods.


.. include:: codesamples/Compute.java
   :code: java


The code snippet shows a complete example, using the Loop Parallel annotations, the Task Graphs and the execution plan.
This document explains each part.


.. _kernel-context-api:

Kernel API
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Another way to express compute-kernels in TornadoVM is via the kernel API.
To do so, TornadoVM exposes a ``KernelContext`` with which the application can directly access
the thread-id, allocate memory in local memory (shared memory on NVIDIA devices), and insert barriers.
This model is similar to programming compute-kernels in OpenCL and CUDA.
Therefore, this API is more suitable for GPU/FPGA expert programmers that want more control or want to port existing CUDA/OpenCL compute kernels into TornadoVM.

The following code-snippet shows the Matrix Multiplication example using the kernel-parallel API:


.. include:: codesamples/ComputeKernel.java
   :code: java


Kernel Context
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

``KernelContext`` is a Java object exposed by the TornadoVM API to the developers in order to leverage Kernel Parallel Programming using the
existing ``TaskGraph`` API.
An instance of the ``KernelContext`` object is passed to each task that uses the kernel-parallel API.

Additionally, for all tasks using the ``KernelContext`` object, the user must provide a Grid of execution threads to run on the parallel device.
This grid of threads is similar to the number of threads to be launched using CUDA or OpenCL (Number of threads per block and number of blocks).
Examples can be found in the ``Grid``
`unit-tests <https://github.com/beehive-lab/TornadoVM/tree/master/tornado-unittests/src/main/java/uk/ac/manchester/tornado/unittests/grid>`__.

KernelContext Features
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The following table presents the available features that TornadoVM exposes in Java along with the respective OpenCL and CUDA PTX terminology.

.. code:: java

   // Note:
   kc = new KernelContext();

+----------------------------------------------------+-------------------------------+------------------------------------+
| TornadoVM KernelContext                            | OpenCL                        | PTX                                |
+====================================================+===============================+====================================+
| kc.globalIdx                                       | get_global_id(0)              | blockIdx \* blockDim.x + threadIdx |
+----------------------------------------------------+-------------------------------+------------------------------------+
| kc.globalIdy                                       | get_global_id(1)              | blockIdy \* blockDim.y + threadIdy |
+----------------------------------------------------+-------------------------------+------------------------------------+
| kc.globalIdz                                       | get_global_id(2)              | blockIdz \* blockDim.z + threadIdz |
+----------------------------------------------------+-------------------------------+------------------------------------+
| kc.getLocalGroupSize()                             | get_local_size()              | blockDim                           |
+----------------------------------------------------+-------------------------------+------------------------------------+
| kc.localBarrier()                                  | barrier(CLK_LOCAL_MEM_FENCE)  | barrier.sync                       |
+----------------------------------------------------+-------------------------------+------------------------------------+
| kc.globalBarrier()                                 | barrier(CLK_GLOBAL_MEM_FENCE) | barrier.sync                       |
+----------------------------------------------------+-------------------------------+------------------------------------+
| int[] array = kc.allocateIntLocalArray(size)       | \__local int array[size]      | .shared .s32 array[size]           |
+----------------------------------------------------+-------------------------------+------------------------------------+
| float[] array = kc.allocateFloatLocalArray(size)   | \__local float array[size]    | .shared .s32 array[size]           |
+----------------------------------------------------+-------------------------------+------------------------------------+
| long[] array = kc.allocateLongLocalArray(size)     | \__local long array[size]     | .shared .s64 array[size]           |
+----------------------------------------------------+-------------------------------+------------------------------------+
| double[] array = kc.allocateDoubleLocalArray(size) | \__local double array[size]   | .shared .s64 array[size]           |
+----------------------------------------------------+-------------------------------+------------------------------------+

Example
~~~~~~~~~~~~~~~~~~~~~~~~~~

The following
`example <https://github.com/beehive-lab/TornadoVM/tree/master/tornado-examples/src/main/java/uk/ac/manchester/tornado/examples/kernelcontext/compute/MatrixMultiplication2DV2.java>`__
is the Matrix Multiplication implementation using the ``KernelContext`` object for indexing threads and access to local memory. The following
example also makes use of loop tiling. There are three main steps to leverage the features of the ``KernelContext``:

1. The ``KernelContext`` object is passed as an argument in the method that will be accelerated. This implementation follows the OpenCL
   implementation description provided in https://github.com/cnugteren/myGEMM.

.. code:: java

   public static void matrixMultiplication(KernelContext context,
                                          final FloatArray A, final FloatArray B,
                                          final FloatArray C, final int size) {

       // Index thread in the first dimension ( get_global_id(0) )
       int row = context.localIdx;

       // Index thread in the second dimension ( get_global_id(1) )
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
           aSub[col * TS + row] = A.get(tiledCol * size + globalRow);
           bSub[col * TS + row] = B.get(globalCol * size + tiledRow]);

           // Synchronise to make sure the tile is loaded
           context.localBarrier();

           // Perform the computation for a single tile
           for(int k = 0; k < TS; k++) {
               sum += aSub[k* TS + row] * bSub[col * TS + k];
           }
           // Synchronise before loading the next tile
           context.localBarrier();
       }

       // Store the final result in C
       C.set((globalCol * size) + globalRow, sum);
   }

2. A TornadoVM program that uses the ``KernelContext`` must use the context with a ``WorkerGrid`` (1D/2D/3D). This is necessary in order
   to obtain awareness about the dimensions and the sizes of the threads that will be deployed.
   Therefore, ``KernelContext`` can only work with tasks that are linked with a ``GridScheduler``.

.. code:: java

   // Create 2D Grid of Threads with a 2D Worker
   WorkerGrid workerGrid = new WorkerGrid2D(size, size);

   // Create a GridScheduler that associates a task-ID with a worker grid
   GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);

   // Create the TornadoVM Context
   KernelContext context = new KernelContext();

   // [Optional] Set the local work size
   workerGrid.setLocalWork(32, 32, 1);

3. Create a ``TornadoExecutionPlan`` and execute:

.. code:: java

   TaskGraph tg = new TaskGraph("s0")
         .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrixA, matrixB)
         .task("t0",MxM::compute,context, matrixA, matrixB, matrixC, size)
         .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

   ImmutableTaskGraph immutableTaskGraph = tg.snapshot();
   TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

   executionPlan.withGridScheduler(gridScheduler)  // Pass the GridScheduler in the execute method
                .execute();



Running Multiple Tasks with the Kernel Context
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The TornadoVM Task-Graph can be composed of multiple tasks which can either exploit the ``KernelContext`` features or adhere to the original
TornadoVM annotations (``@Parallel``, ``@Reduce``).

The following code snippet shows an example of how to combine tasks that require a ``KernelContext`` object with tasks that do not need a ``KernelContext``.

.. code:: java

   WorkerGrid worker = new WorkerGrid1D(size);

   GridScheduler gridScheduler = new GridScheduler();
   gridScheduler.setWorkerGrid("s02.t0", worker);
   gridScheduler.setWorkerGrid("s02.t1", worker);

   // Create a Kernel Context object
   KernelContext context = new KernelContext();

   TaskGraph taskGraph = new TaskGraph("s02") //
      .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
      .task("t0", TestCombinedTaskGraph::vectorAddV2, context, a, b, cTornado) //
      .task("t1", TestCombinedTaskGraph::vectorMulV2, context, cTornado, b, cTornado) //
      .task("t2", TestCombinedTaskGraph::vectorSubV2, context, cTornado, b) //
      .transferToHost(DataTransferMode.EVERY_EXECUTION, cTornado);

   ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
   TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph);

   executor.withGridScheduler(gridScheduler) //
           .execute();


In this test case, each of the first two tasks uses a separate ``WorkerGrid``.
The third task does not use a ``WorkerGrid``, and it relies on the TornadoVM Runtime for the scheduling of the threads.

You can see more examples on `GitHub <https://github.com/beehive-lab/TornadoVM/tree/master/tornado-examples/src/main/java/uk/ac/manchester/tornado/examples/kernelcontext>`_.



3. Selecting the methods to be accelerated using a Task-Graph API
-----------------------------------------------------------------

A ``TaskGraph`` is an TornadoVM object that defines and identify which Java methods to be accelerated and the data involved.
Task-graph defines data to be copied in, and out of the accelerator as well as all tasks (Java methods) to be accelerated.
Note that a ``TaskGraph`` object does not compute/move data, but rather annotates what to do when the computation in launched.
As we will see in Step 3, a task-graph is only executed through an execution plan.


The following code snippet shows how to instantiate a ``TaskGraph`` TornadoVM object.

.. code:: java

   TaskGraph taskGraph = new TaskGraph(""name");



A. Defining copies from the host (main CPU) to the device (accelerator).
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


The Task-Graph API also defines a method, named ``transferToDevice`` to set which arrays need to be copied to the target accelerator.
This method receives two types of arguments:

1. Data Transfer Mode:
   a. ``EVERY_EXECUTION``: Data is copied from host to device every time a task-graph is executed by an execution plan.
   b. ``FIRST_EXECUTION``: Data is only copied the first time a task-graph is executed by an execution plan.
2. All input arrays needed to be copied from the host to the device.


The following code snippet sets two arrays (a, b) to be copied from the host to the device every time a task-graph is executed.


.. code:: java

   taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b);


Note that this call is only used for the definition of the data flow across multiple tasks in a task-graph, and there are no data copies involved.
The TornadoVM runtime stores which data are associated with each data transfer mode and the actual data transfers take place only during the execution by the execution plan.


B. Code definition
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

To identify which Java methods, from all existing Java methods in a Java program, to accelerate.
This is performed using the ``task`` API call as follows:


.. code:: java

   taskGraph.task("sample", Class::method, param1, param2);


- The first parameter sets an ID to the task. This is useful if developers want to change device, or other runtime parameters, from the command line.
- The second parameter is a reference (or a Java lambda expression), to an existing Java method.
- The rest of the parameters correspond to the function call parameters, as if the method were invoked.


Developers can add as many tasks as needed.
The maximum number of tasks depends on the amount of code that can be shipped to the accelerator.
Usually, FPGAs are more limited than GPUs.


C. Copy out from the device (accelerator) to the host (main CPU).
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Similar to ``transferToDevice``, the ``TaskGraph`` API also offers a call to sync the data back from the device to the host.
The API call is ``transferToHost`` with the following parameters:

1. Data Transfer Mode:
   a. ``EVERY_EXECUTION``: Data is copied from the device to the host every time a task-graph is executed by an execution plan.
   b. ``USER_DEFINED:`` Data is only copied by an execution result under demand. This is an optimization if developers plan to execute the task-graph multiple times and do not want to copy the results every time the execution plan is launched.
2. All output arrays to be copied from the device to the host.


Example:

.. code:: java

   taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, output1, output2);


4. Execution Plans
------------------------------------------------


The last step is the creation of an execution plan. An execution plan receives a list of immutable task graphs ready to be executed, as follows:


.. code:: java

   TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(itg);


What can we do with an execution plan?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


We can execute an execution plan directly, and TornadoVM will apply a list of default optimisations (e.g., it will run on the default device, using the default thread scheduler).


.. code:: java

   executionPlan.execute();



How can we optimize an execution plan?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


The execution plan offers a set of methods that developers can use to optimize different execution plans.
Note that the execution plan operates over all immutable task graphs given in the constructor. Therefore, all immutable task graphs will be executed on the same device in order.

Example:

.. code:: java

   executionPlan.withProfiler(ProfilerMode.SILENT) // Enable Profiling
       .withWarmUp() //  Perform a warmup (compile and code and install it in a code-cache).
       .withDevice(device); Select a specific device


And then:

.. code:: java

   executionPlan.execute();



Obtain the result and the profiler
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Every time an execution plan is executed, a new object of type ``TornadoExecutionResult`` is created.

.. code:: java

   TornadoExecutionResult executionResult = executionPlan.execute();



From the execution result, developers can obtain the result of the TornadoVM profiler:


.. code:: java

   executionResult.getProfilerResult();


And query the values of the profiling report.
Note that the TornadoVM profiler works only if enabled in the execution plan (via the ``withProfiler`` method).



.. _reductions:

Parallel Reductions
------------------------------------

TornadoVM now supports basic reductions for ``int``, ``long``,\ ``float`` and ``double`` data types for the operators ``+`` and ``*``, ``max`` and ``min``.
Examples can be found in the ``examples/src/main/java/uk/ac/manchester/tornado/unittests/reductions`` directory on GitHub.

TornadoVM exposes the Java annotation ``@Reduce`` to represent parallel reductions.
Similarly to the ``@Parallel`` annotation, the ``@Reduce`` annotation is used to identify parallel sections in Java sequential code.
The annotations is used for method parameter in which reductions must be applied.
This is similar to OpenMP and OpenACC.

Example:

.. code:: java

   public static void reductionAddFloats(FloatArray input, @Reduce FloatArray result) {
       for (@Parallel int i = 0; i < input.length; i++) {
           result.set(0, result.get(0) + input.get(i));
       }
    }

The code is very similar to a Java sequential reduction but with ``@Reduce`` and ``@Parallel`` annotations.
The ``@Reduce`` annotation is associated with a variable, in this case, with the ``result`` float
array.
Then, we annotate the loop with ``@Parallel``. The OpenCL/PTX JIT compilers generate OpenCL/PTX parallel version for this code that can
run on GPU and CPU.

Creating reduction tasks
~~~~~~~~~~~~~~~~~~~~~~~~~

TornadoVM generates different OpenCL/SPIR-V code depending on the target device.
Internally, if the target is a GPU, TornadoVM performs full and
parallel reductions using the threads within the same OpenCL work-group.
If the target is a CPU, TornadoVM performs full reductions within the
same thread-id. Besides, TornadoVM automatically resizes the output
variables according to the number of work-groups and threads selected.

For PTX code generation, TornadoVM will always perform full and parallel
reductions using the threads within the same CUDA block.

.. code:: java

   FloatArray input = new FloatArray(SIZE);
   FloatArray result = new FloatArray(1);

   Random r = new Random();
   IntStream.range(0, SIZE).sequential().forEach(i -> {
       input.set(i, r.nextFloat());
   });

   TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsFloats::reductionAddFloats, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

   ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
   TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
   executionPlan.execute();


Map/Reduce
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This section shows an example of how to perform map/reduce operations with TornadoVM.
Each of the operations corresponds to a task as follows
in the next example:

.. code:: java


   public class ReduceExample {
       public static void map01(IntArray a, IntArray b, IntArray c) {
           for (@Parallel int i = 0; i < a.length; i++) {
               c.set(i, a.get(i) + b.get(i));
           }
       }

       public static void reduce01(IntArray c, @Reduce IntArray result) {
           result.set(0, 0);
           for (@Parallel int i = 0; i < c.length; i++) {
               result.set(0, result.get(0) + c.get(i));
           }
       }

       public void testMapReduce() {
            IntArray a = new IntArray(BIG_SIZE);
            IntArray b = new IntArray(BIG_SIZE);
            IntArray c = new IntArray(BIG_SIZE);
            IntArray result = IntArray.fromElements(0);

            IntStream.range(0, BIG_SIZE).parallel().forEach(i -> {
               a[i] = 10;
               b[i] = 2;
            });

            TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, c) //
                .task("t0", TestReductionsIntegers::map01, a, b, c) //
                .task("t1", TestReductionsIntegers::reduce01, c, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
            ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
            TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
            TornadoExecutionResult executionResult = executionPlan.execute();
        }
   }

Reduction with dependencies
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

TornadoVM also supports reductions using data dependencies.
The next example illustrates this case with the ``PI`` computation.

.. code:: java

   public static void computePi(FloatArray input, @Reduce FloatArray result) {
       for (@Parallel int i = 1; i < input.length; i++) {
           float value = (float) (Math.pow(-1, i + 1) / (2 * i - 1));
           result.set(0, result.get(0) + value + input.get(i));
       }
   }


.. _dynamic_reconfiguration:

Dynamic Reconfiguration [DEPRECATED use version =< 1.1.1]
------------------------------


The dynamic configuration in TornadoVM is the capability to migrate tasks at runtime from one device to another (e.g., from one GPU to another, or from one CPU to GPU, etc).
The dynamic reconfiguration is not enabled by default, but it can be easily activated through the execution plan as follows:


.. code:: java

   executionPlan.withDynamicReconfiguration(Policy.PERFORMANCE, DRMode.Parallel)
                .execute();



The `withDynamicReconfiguration` call receives two arguments:

1. Policy: dynamically changes devices based on one of the following policies:

   -  `PERFORMANCE`: after a warmup of all devices (JIT compilation is excluded). The TornadoVM runtime evaluates the execution for all devices before making a decision.

   - `END_2_END`: best performing device including the warm-up phase (JIT compilation and buffer allocations). The TornadoVM runtime evaluates the execution for all devices before making a decision.

   - `LATENCY`: fastest device to return. The TornadoVM runtime does not evaluate the execution for all devices before making a decision, but rather it switches context with the first device that finishes the execution.


.. _batch-processing:

Batch Computing Processing
--------------------------

TornadoVM supports batch processing through the following API call ``withBatch`` of the ``TornadoExecutionPlan`` API.
This was mainly designed to run big data applications in which host data is much larger than the device memory capacity.
In these cases, developers can enable batch processing by simply calling ``withBatch`` in their execution plans.

.. code:: java

   int size = 2110000000;
   FloatArray arrayA = new FloatArray(size); // Array of 8440MB
   FloatArray arrayB = new FloatArray(size); // Array of 8440MB

   TaskGraph taskGraph = new TaskGraph("s0") //
         .transferToDevice(DataTransferMode.FIRST_EXECUTION, arrayA) //
         .task("t0", TestBatches::compute, arrayA, arrayB) //
         .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayB);

   ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
   TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph);
   executor.withBatch("512MB") // Run in blocks of 512MB
           .execute();


The batch method-call receives a Java string representing the batch to be allocated, copied-in and copied-out to/from the heap of the target device.

Examples of allowed sizes are:

.. code:: java

   batch("XMB");   // Express in MB (X is an int number)
   batch("ZGB");   // Express in GB (Z is an int number)

Current Limitations of Batch Processing
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

There is a set of limitations with the current implementation of batch processing.

1. All arrays passed to the input methods to be compiled to the target device have to have the total size and element size
(e.g. combining FloatArray and IntArray is possible).
2. We only support arrays of primitives that are passed as arguments. This means that scope arrays in batches are not currently supported.
3. All bytecodes make use of the same OpenCL command queue / CUDA stream.
4. Matrix or non-regular batch distributions. (E.g., MxM would need to be split by rows in matrix-A and columns in matrix-B).

