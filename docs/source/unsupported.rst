.. _unsupported:

Unsupported Java features
=================================

TornadoVM currently supports a subset of Java. Most limitations are due to the underlying programming model (OpenCL) that TornadoVM uses.
This document summarizes each of the limitations and their explanations.

1. No Recursion
'''''''''''''''

TornadoVM does not support recursion. This is also a current limitation of OpenCL, CUDA and SPIR-V.

2. No Object Support (*)
''''''''''''''''''''''''''''''''''''

TornadoVM currently supports off-heap memory allocation via custom data types (e.g., ``IntArray``, ``FloatArray``, ``DoubleArray``, ``LongArray``, ``CharArray``, ``ShortArray``, and ``ByteArray``) and arrays of primitive Java types (e.g., ``int``, ``float``, ``double``, ``short``, ``char``, and ``long``).
Furthermore, TornadoVM offers support for some object types, such as ``VectorFloat``, ``VectorFloat4`` and all variations with types as well as matrices types.

The full list of data structures supported is in this `link <https://github.com/beehive-lab/TornadoVM/tree/master/tornado-api/src/main/java/uk/ac/manchester/tornado/api/collections/types>`__.

Those are the objects that TornadoVM knows their memory layout.
TornadoVM generates specialized OpenCL code for those data structures.
For example, ``VectorFloat4`` generates accesses using OpenCL vector types (``float4``).
This might speed-up user code if the target device contains explicit vector units, such as AVX on Intel CPUs or vector register on AMD GPUs.

3. No Dynamic Memory Allocation (*)
''''''''''''''''''''''''''''''''''''''''''''''''

In general, TornadoVM cannot allocate memory on demand since it must know the size of the buffers in advanced.
However, TornadoVM performs a sort of partial evaluation (PE), in which the JIT compiler evaluates
expressions and “materializes” values at runtime. Therefore, if the JIT
compiler can obtain, for example, the size of an array, it will generate
code based on the values seen at runtime.

Note that other alternatives, such as Aparapi, cannot perform these type
of operations due to lack of support for runtime optimizations before
generating the OpenCL C code.

4. No Support for Traps/Exceptions (*)
'''''''''''''''''''''''''''''''''''''''''''''''''''''

On GPUs there is little support for exceptions.
For example, on a division by 0 scenario, the CPU sets a flag in one of the special registers.
Then the Operating System can query those special registers and pass that value to the application runtime (in this case, the Java runtime).
Then Java runtime handles the exception.

However, there is no such mechanisms on GPUs
(`link <https://docs.nvidia.com/cuda/floating-point/index.html#differences-from-x86>`__),
which means that TornadoVM must insert extra control-flow to guarantee those exceptions never happen.
Currently, since TornadoVM compiles at runtime, many of those checks can be assured at runtime.
However, we plan to integrate exception support for TornadoVM in the future.

5. No Support for static TaskGraphs and Tasks
'''''''''''''''''''''''''''''''''''''''''''''''''''''

TornadoVM currently does not support static ``TaskGraph`` and Tasks. For
example, the code below is not considered valid:

.. code-block:: java

       public static void testMethod(IntArray in) {
           // ... some code ...
       }

       static IntArray inTor = IntArray.fromElements(0);
       static TaskGraph taskGraph = new TaskGraph("g0");
       static {
           taskGraph.task("t0", Main::testMethod, inTor);
       }

The reason for not supporting this is that a deadlock might occur between the user thread running class initialization and the Tornado
compiler thread performing JIT compilation of the Task method.
Note that detecting such a deadlock is not trivial and therefore currently,
TornadoVM will not issue any error or warning.

Note
~~~~~

Note that if a particular code segment cannot be accelerated with TornadoVM due to unsupported features, then execution falls back to the
host JVM which will execute your code on the CPU as it would normally do.
