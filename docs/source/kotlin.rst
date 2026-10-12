.. _kotlin_support:

Kotlin
######

TornadoVM compiles JVM bytecode, so kernels written in Kotlin run on the same backends (OpenCL,
CUDA, Metal) as Java kernels. The ``tornado-kotlin`` modules add a Kotlin API on top of the Java
one, together with examples, benchmarks and unit tests.

Kotlin support is opt-in: the default build does not use the Kotlin toolchain, and the runtime
support only applies to classes compiled by kotlinc.

Building
********

.. code-block:: bash

   make KOTLIN=1 BACKEND=opencl                  # or: make jdk22plus BACKEND=metal,opencl KOTLIN=1

``KOTLIN=1`` adds the ``kotlin`` Maven profile (``bin/compile --kotlin``). The Kotlin modules are
built and added to the SDK's ``share/java/tornado`` together with ``kotlin-stdlib``.

.. list-table::
   :widths: 30 25 45
   :header-rows: 1

   * - Module
     - JPMS module
     - Contents
   * - ``tornado-kotlin-api``
     - ``tornado.kotlin.api``
     - Kotlin API over ``tornado-api``
   * - ``tornado-kotlin-examples``
     - ``tornado.kotlin.examples``
     - Runnable examples
   * - ``tornado-kotlin-benchmarks``
     - ``tornado.kotlin.benchmarks``
     - Kotlin ports of seven Java benchmarks
   * - ``tornado-kotlin-unittests``
     - ``tornado.kotlin.unittests``
     - Unit tests, run through ``tornado-test``

Loop-parallel kernels
*********************

kotlinc does not keep ``@Parallel`` on loop variables, so Kotlin kernels mark parallel loops with
``parallelFor``. Nest two or three calls for 2D and 3D kernels. As with ``@Parallel`` in Java, the
start of the loop must be a constant.

.. code-block:: kotlin

   import uk.ac.manchester.tornado.api.enums.DataTransferMode
   import uk.ac.manchester.tornado.kotlin.api.*

   fun vectorAdd(a: TFloatArray, b: TFloatArray, c: TFloatArray) {
       parallelFor(0, c.size) { i ->
           c[i] = a[i] + b[i]
       }
   }

   fun main() {
       val n = 1 shl 20
       val a = TFloatArray(n) { it.toFloat() }
       val b = TFloatArray(n) { 2f * it }
       val c = TFloatArray(n)

       val graph = taskGraph("s0") {
           transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)
           task("t0", ::vectorAdd, a, b, c)
           transferToHost(DataTransferMode.EVERY_EXECUTION, c)
       }
       graph.toExecutionPlan().use { it.execute() }
   }

Reductions work as in Java, with ``@Reduce`` on the output parameter:

.. code-block:: kotlin

   fun sum(input: TFloatArray, @Reduce result: TFloatArray) {
       result[0] = 0f
       parallelFor(0, input.size) { i -> result[0] += input[i] }
   }

Kernel-parallel kernels
***********************

``KernelContext`` kernels work unchanged. The API adds ``Int`` accessors for the thread indices
(``globalIdX``, ``localIdX``, ``groupIdX``, ``globalSizeX``, ``localSizeX``, and ``Y``/``Z``), and
builders for grids:

.. code-block:: kotlin

   fun matrixMultiply(context: KernelContext, a: TFloatArray, b: TFloatArray, c: TFloatArray, n: Int) {
       val row = context.globalIdY
       val col = context.globalIdX
       var sum = 0f
       for (k in 0 until n) {
           sum += a[row * n + k] * b[k * n + col]
       }
       c[row * n + col] = sum
   }

   executionPlan(graph.snapshot()) {
       withDevice(0, 0)
       withGridScheduler(gridScheduler("s0.t0" to workerGrid(n, n, localX = 16, localY = 16)))
   }.use { it.execute() }

API summary
***********

.. list-table::
   :widths: 45 55
   :header-rows: 1

   * - API
     - What it does
   * - ``taskGraph(name) { ... }``
     - Builds a ``TaskGraph``; the block has the graph as receiver, so the whole Java API is available
   * - ``executionPlan(graphs) { ... }``, ``TaskGraph.toExecutionPlan()``
     - Create (via ``TornadoExecutionPlan.of``) and configure an execution plan
   * - ``withDevice(backend, device)``
     - Selects a device by index, as listed by ``tornado --devices``
   * - ``gridScheduler("g.t" to grid)``, ``workerGrid(...)``
     - Grid scheduler and 1D/2D/3D worker grids with optional local sizes
   * - ``parallelFor(start, end) { i -> ... }``
     - A parallel loop, the equivalent of a ``@Parallel`` loop variable
   * - ``TFloatArray``, ``TIntArray``, ...
     - Aliases for TornadoVM's off-heap arrays, whose names clash with Kotlin's arrays
   * - ``TFloatArray(n) { init }``, ``tFloatArrayOf(...)``, ``toTornado()``, ``toFloatArray()``
     - Creation and conversion helpers

TornadoVM arrays support ``a[i]``, ``a[i] = v`` and ``a.size`` directly.

Rules for kernels
*****************

* Kernels are **top-level functions**, passed as direct references (``::vectorAdd``) or as lambdas
  at the ``task`` call site. A function reference stored in a variable first cannot be compiled.
* **Modules that contain kernels must be open** (or open their kernel packages), as for Java.
* kotlinc's **default compiler flags are fine**; no flag is needed to turn off its null checks.

Runtime support
***************

The runtime recognises Kotlin kernels by the ``kotlin.Metadata`` annotation that kotlinc puts on
every class. The Kotlin-specific compiler rewrites below only apply to those methods, so Java
kernels are never affected. They are all switched off with ``-Dtornado.kotlin.support=false``.

* Calls to kotlinc's null checks (``kotlin.jvm.internal.Intrinsics.checkNotNull*``) are removed.
* ``Number.intValue()`` (and the other ``xxxValue()`` methods) on a boxed value becomes a plain
  unbox, as ``Integer.intValue()`` does for Java.
* ``KernelContext`` indices read as boxed ``Integer`` values and unboxed several times are lowered
  like Java's single unbox.
* ``parallelFor`` loops become parallel loops.
* ``@Reduce`` is read through the forwarding method kotlinc generates for a ``::kernel`` task
  reference, and a reduction's input size is found when the loop bound is evaluated before the loop.

Hybrid API (library tasks)
**************************

Native library tasks (``libraryTask``, e.g. cuBLAS) work from Kotlin: pass the provider's factory
(``CuBlas::cublasSgemm``) or a factory written in Kotlin, and mix them with Kotlin kernels in the
same graph. As in Java, executing library tasks needs the CUDA backend.

.. code-block:: kotlin

   taskGraph("hybrid") {
       transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
       task("addOne", ::addOne, a)
       libraryTask("sgemm", CuBlas::cublasSgemm, op, op, n, n, n, 1f, b, n, a, n, 0f, c, n)
       task("scale", ::scaleByTwo, c, d)
       transferToHost(DataTransferMode.EVERY_EXECUTION, d)
   }

A Kotlin module that calls ``CuBlasLibraryProvider`` (or another provider class) directly must also
``requires tornado.runtime``, because kotlinc resolves the provider's supertypes.

CUDA Tile (TileContext)
***********************

Tile kernels take the ``TileContext`` as first parameter, as in Java (see :ref:`tile_api`). Inside
``with(tc) { ... }`` the Kotlin API adds operators and functions on ``Tile``: ``+ - * /``,
``* scalar``, unary ``-``, ``matmul``, the comparisons ``lt le gt ge`` with ``select``, the math
functions (``exp``, ``log``, ``sqrt``, ``rsqrt``, ``tanh``, ``sin``, ``cos``, ``abs``, ``maximum``,
``minimum``, ``fma``) and the ``sum``/``max``/``min`` reductions. They receive the context as a
Kotlin context parameter and are inline, so ``a + b`` compiles to exactly ``tc.add(a, b)`` and the
CUDA Tile compiler sees the same calls as for a Java kernel. Tile shapes must be compile-time
constants: use literals or ``const val``.

.. code-block:: kotlin

   const val WIDTH = 1024

   fun softmax(tc: TileContext, input: TFloatArray, output: TFloatArray, rows: Int) = with(tc) {
       val row = bidX()
       val values = partition(view(input, rows, WIDTH), 1, WIDTH).load(row, 0)
       val shifted = exp(values - values.max(1))
       partition(view(output, rows, WIDTH), 1, WIDTH).store(shifted / shifted.sum(1), row, 0)
   }

   taskGraph("s0") {
       transferToDevice(DataTransferMode.FIRST_EXECUTION, input)
       task("softmax", ::softmax, TileContext(), input, output, rows)
       transferToHost(DataTransferMode.EVERY_EXECUTION, output)
   }.toExecutionPlan().use { it.withGridScheduler(tileGrid("s0.softmax", rows)).execute() }

``tileGrid(...)`` builds a grid that counts tile blocks, and ``tc.runOnHost(blocksX, blocksY) { ... }``
runs a tile kernel on the JVM. Tile tasks need the CUDA backend; the examples ``TileVectorAdd``,
``TileMatrixMultiply`` and ``TileSoftmax`` (package ``uk.ac.manchester.tornado.kotlin.examples.tile``)
fall back to the JVM elsewhere.

Examples, benchmarks and tests
******************************

.. code-block:: bash

   tornado -m tornado.kotlin.examples/uk.ac.manchester.tornado.kotlin.examples.VectorAdd
   tornado -m tornado.kotlin.examples/uk.ac.manchester.tornado.kotlin.examples.MatrixMultiplication
   tornado -m tornado.kotlin.examples/uk.ac.manchester.tornado.kotlin.examples.Reduction

   tornado-benchmarks.py --kotlin                              # saxpy, sgemm, blackscholes, nbody,
                                                               # mandelbrot, dft, montecarlo
   tornado-benchmarks.py --kotlin --compareJava --skipSerial   # Kotlin vs Java kernels per device

   tornado-test --kotlin -V

The Kotlin benchmarks are statement-by-statement ports of the Java ones, and generate the same
kernel code: on an Apple M4 Pro (OpenCL and Metal) their run times are within a few percent of
the Java benchmarks.
