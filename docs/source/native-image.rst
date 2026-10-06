.. _native-image:

Native Images (GraalVM)
=======================

TornadoVM does not need JVMCI or any other JVM support: it reads class metadata through reflection and
compiles kernels with its own copy of the Graal compiler. A TornadoVM application can therefore be
compiled ahead of time into a single native executable with GraalVM ``native-image``. The executable
still JIT-compiles kernels for the GPU at run time, but TornadoVM itself, including its compiler, starts
already compiled.

Requirements
------------

- A GraalVM for JDK 22 or newer, with ``native-image`` (GraalVM Community or Oracle GraalVM).
  ``JAVA_HOME`` must point at it. Use GraalVM 25.1 or newer (a GraalVM 25 Innovation release) when the
  image contains ``jdk.incubator.vector``; see *Shared arenas* below. Profile-guided optimization
  (``--pgo``) needs Oracle GraalVM.
- A ``jdk22plus`` TornadoVM SDK. ``TORNADOVM_HOME`` must point at it.
- The usual native toolchain for ``native-image`` (on Linux: ``gcc``, ``zlib`` headers).

Building an image
-----------------

``tornado-native-image`` (in the SDK's ``bin`` directory) runs the application on the JVM under GraalVM's
tracing agent to record the reachability metadata TornadoVM needs (kernel classes and class files,
Graal's services, the foreign calls into the GPU drivers), then calls ``native-image`` with the SDK's
module flags:

.. code-block:: bash

   export JAVA_HOME=/path/to/graalvm-25.1-or-newer
   export TORNADOVM_HOME=/path/to/tornadovm-sdk
   export PATH=$TORNADOVM_HOME/bin:$PATH

   tornado-native-image -m tornado.examples/uk.ac.manchester.tornado.examples.VectorAddInt -o vectoradd \
       --trace "-Ds0.t0.device=0:0 -- 1024" \
       --trace "-Ds0.t0.device=1:0 -- 1024" \
       -- -H:-VectorAPISupport

   ./vectoradd 1048576

Each ``--trace`` is one run of the application on the JVM: JVM options (such as the device) before a
``--``, application arguments after it. Metadata from all traces is merged into
``<output>-native-config``, which can be reused with ``--no-trace``.

A class-path application uses ``--class-path`` and ``--main-class`` instead of ``-m``, and extra modules
are added with ``--add-modules``:

.. code-block:: bash

   tornado-native-image --class-path app.jar --main-class org.example.Main \
       --add-modules jdk.incubator.vector -o app --trace "-- --model model.gguf"

Arguments after a lone ``--`` are passed to ``native-image``, for example ``-- --pgo-instrument`` with
Oracle GraalVM.

The executable needs no TornadoVM launcher flags. Applications that use TornadoVM's JNI-based library
tasks (cuDNN, cuDF, CUTLASS) also need ``-Djava.library.path=$TORNADOVM_HOME/lib``.

Limitations
-----------

- **Trace every path the image will run.** Metadata is recorded only for what the traces executed: every
  backend and device, and every kernel. A kernel or feature that no trace exercised can fail at run time
  with a missing-metadata error; add a trace that runs it.
- **Host-side Java is not JIT-compiled.** Kernels run as before, but Java code on the - **Shared arenas and the Vector API.** The CUDA and OpenCL backends allocate host memory from
  ``Arena.ofShared()`` and free it by closing the arena, which Native Image supports only with
  ``-H:+SharedArenaSupport``; ``tornado-native-image`` passes that option. GraalVM does not support it together
  with Vector API support:

  - GraalVM 25.1 and newer refuse the combination. Add ``-- -H:-VectorAPISupport`` whenever the image contains
    ``jdk.incubator.vector`` (``tornado.examples`` requires it). The Vector API still works without SIMD
    intrinsics, but a vector load or store on a shared-arena ``MemorySegment`` throws at run time.
  - GraalVM 25.0.x builds images that only require the module, but stops with ``GraalError: ... could access a
    session`` when application code uses the Vector API on a ``MemorySegment``
    (`oracle/graal#13321 <https://github.com/oracle/graal/issues/13321>`__).
MemorySegment``: ``native-image`` stops with ``GraalError: ... was not
  inlined and could access a session``. Requiring the module is fine; only vector loads and stores on memory
  segments trigger it.
- **Oracle GraalVM 25 PGO and the OpenCL backend.** Building a PGO-instrumented image fails inside
  ``native-image`` on the upcall stub of the OpenCL context error callback (``GraalError: mismatched
  definition``). Build PGO images from a CUDA-only SDK until this is fixed in GraalVM.
