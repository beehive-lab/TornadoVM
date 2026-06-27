.. _cuda-backend:

NVIDIA CUDA Devices (CUDA backend)
==============================================

TornadoVM targets NVIDIA GPUs through the **CUDA backend**, which generates
**CUDA C** source at runtime, compiles it with **NVRTC** to PTX, and executes
it through the CUDA Driver API.

The prerequisites (NVIDIA driver + CUDA Toolkit) are described below. The
internal design of the CUDA backend is documented in its own section further
down this page.

Prerequisites
----------------------------------------------

In order to use the CUDA backend of TornadoVM, you will need a CUDA compatible device (NVIDIA GPUs with CUDA support).

Driver Installation
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

| **Step 1:**
| You will need to setup the ``CUDA Toolkit``. If you don’t have it
  installed already, you can follow `this
  guide <https://docs.nvidia.com/cuda/cuda-installation-guide-linux/index.html>`__.

| **Step 2:**
| Make sure you follow the `environment setup <https://docs.nvidia.com/cuda/cuda-installation-guide-linux/index.html#environment-setup>`__
  to add the required environment variables.
| Depending on the installation, you might also have to expand your
  ``C_INCLUDE_PATH`` and ``LD_LIBRARY_PATH`` variables to include the
  CUDA headers.

.. code:: bash

   $ export C_INCLUDE_PATH=/usr/local/cuda/include:${C_INCLUDE_PATH}
   $ export LD_LIBRARY_PATH=/usr/local/cuda/lib64:${LD_LIBRARY_PATH}

To ensure that the installation has been successful, you can run the following commands: ``nvidia-smi`` and ``nvcc --version``.

The output of ``nvidia-smi`` should be similar to:


.. code:: bash

   +-----------------------------------------------------------------------------+
    | NVIDIA-SMI 440.100      Driver Version: 440.100      CUDA Version: 10.2     |
    |-------------------------------+----------------------+----------------------+
    | GPU  Name        Persistence-M| Bus-Id        Disp.A | Volatile Uncorr. ECC |
    | Fan  Temp  Perf  Pwr:Usage/Cap|         Memory-Usage | GPU-Util  Compute M. |
    |===============================+======================+======================|
    |   0  GeForce GTX 1650    Off  | 00000000:01:00.0 Off |                  N/A |
    | N/A   51C    P8     1W /  N/A |     73MiB /  3914MiB |      0%      Default |
    +-------------------------------+----------------------+----------------------+

    +-----------------------------------------------------------------------------+
    | Processes:                                                       GPU Memory |
    |  GPU       PID   Type   Process name                             Usage      |
    |=============================================================================|
    |    0      1095      G   /usr/lib/xorg/Xorg                            36MiB |
    |    0      1707      G   /usr/lib/xorg/Xorg                            36MiB |
    +-----------------------------------------------------------------------------+

The output of ``nvcc --version`` should be similar to:

.. code:: bash

   nvcc: NVIDIA (R) Cuda compiler driver
   Copyright (c) 2005-2019 NVIDIA Corporation
   Built on Wed_Oct_23_19:24:38_PDT_2019
   Cuda compilation tools, release 10.2, V10.2.89


TornadoVM Installation
----------------------------------------------

| **Step 3:**
| Install TornadoVM as described here: :ref:`installation`.
| Build TornadoVM with the ``CUDA`` backend selected and run ``tornado --devices``.

The output of the TornadoVM build containing both backends (CUDA and OpenCL) should look like this:

.. code:: bash

      Number of Tornado drivers: 2
      Total number of devices  : 1
      Tornado device=0:0
       NVIDIA CUDA -- GeForce GTX 1650
           Global Memory Size: 3.8 GB
           Local Memory Size: 48.0 KB
           Workgroup Dimensions: 3
           Max WorkGroup Configuration: [1024, 1024, 64]
           Device OpenCL C version: N/A

      Total number of devices  : 2
      Tornado device=1:0
       NVIDIA CUDA -- GeForce GTX 1650
           Global Memory Size: 3.8 GB
           Local Memory Size: 48.0 KB
           Workgroup Dimensions: 3
           Max WorkGroup Configuration: [1024, 1024, 64]
           Device OpenCL C version: OpenCL C 1.2

      Tornado device=1:1
       Intel(R) OpenCL HD Graphics -- Intel(R) Gen9 HD Graphics NEO
           Global Memory Size: 24.8 GB
           Local Memory Size: 64.0 KB
           Workgroup Dimensions: 3
           Max WorkGroup Configuration: [256, 256, 256]
           Device OpenCL C version: OpenCL C 2.0

Note that the first Tornado driver will always correspond to the CUDA device detected by the CUDA backend.

Addressing Possible issues
----------------------------------------------

In some cases, running ``nvidia-smi`` might show the error ``NVIDIA-SMI has failed because it couldn't communicate with the NVIDIA driver``.
This can happen because the driver module is not loaded due to a
`blacklist file <https://forums.developer.nvidia.com/t/nvidia-driver-is-not-loaded-ubuntu-18-10/70495/2>`__.

You can remove this by running: ``sudo rm /etc/modprobe.d/blacklist-nvidia.conf``

On Ubuntu, the driver can also fail to load if it is not selected in ``prime-select``. In order to select it, you can run
``prime-select nvidia`` or ``prime-select on-demand``.

| For older versions of the driver, you might have to point your
  ``LIBRARY_PATH`` variable to the ``libcuda`` library in order to build
  TornadoVM.
| Example:
  ``export LIBRARY_PATH=$LIBRARY_PATH:/usr/local/cuda/lib64/stubs``

After these changes, a reboot might be required for the driver module to
be loaded.

Testing the CUDA Backend of TornadoVM
----------------------------------------------

We have tested the CUDA backend of TornadoVM on the following configurations:

+----------+----------+----------+---------+----------+----------+----------+
|| GPU     || Arch    || PTX ISA || Target || Driver  || CUDA    || Status  |
||         ||         || Version ||        || version || version ||         |
+==========+==========+==========+=========+==========+==========+==========+
|| RTX 3070|| Ampere  || 8.6     || sm_86  || 510.54  || 11.8    || OK      |
+----------+----------+----------+---------+----------+----------+----------+
|| RTX 2060|| Turing  || 7.5     || sm_75  || 510.54  || 11.6    || OK      |
+----------+----------+----------+---------+----------+----------+----------+
|| Quadro  || Pascal  || 6.0     || sm_60  || 384.111 || 9.0     || Fun     |
|| GP100   ||         ||         ||        ||         ||         || ctional |
+----------+----------+----------+---------+----------+----------+----------+
|| GeForce || Turing  || 6.5     || sm_75  || 440.100 || 10.2    || OK      |
|| GTX     ||         ||         ||        ||         ||         ||         |
|| 1650    ||         ||         ||        ||         ||         ||         |
+----------+----------+----------+---------+----------+----------+----------+
|| GeForce || Maxwell || 6.4     || sm_50  || 418.56  || 10.1    || OK      |
|| 930MX   ||         ||         ||        ||         ||         ||         |
+----------+----------+----------+---------+----------+----------+----------+
|| GeForce || Maxwell || 6.5     || sm_50  || 450.36  || 11.0    || OK      |
|| 930MX   ||         ||         ||        ||         ||         ||         |
+----------+----------+----------+---------+----------+----------+----------+

**DISCLAIMER:**

The CUDA backend might fail with the ``Quadro GP100``, driver ``384.111``, with segmentation faults for some of the unit test due to driver issues.


.. _cuda_c_backend:

The CUDA C Backend
==============================================

What it is
----------------------------------------------

The **CUDA backend** is a new backend that generates **CUDA C** source at
runtime, compiles it with **NVRTC** to PTX, and executes it through the
**CUDA Driver API** (``cuModuleLoadDataEx`` / ``cuLaunchKernel``).

It uses an OpenCL-C-style source-generation front end that emits textual CUDA C
(``extern "C" __global__ void ...``, ``blockIdx.x * blockDim.x + threadIdx.x``
for the global thread id, ``__shared__`` for local memory, and
``__syncthreads()`` for barriers).

**Status:** Work-in-progress. Core functionality works end-to-end on real
hardware; several feature buckets are still pending (see
`Supported and in-progress features`_).

Compilation and execution flow
----------------------------------------------

1. Graal lowers the Java task to LIR and emits **CUDA C** text.
2. ``CUDAProgram`` (JNI) runs **NVRTC** (``nvrtcCompileProgram`` with
   ``--gpu-architecture=compute_<major><minor>`` queried from the device, plus
   ``--include-path`` for the CUDA headers), producing PTX.
3. The PTX is loaded via ``cuModuleLoadDataEx``, the kernel is fetched with
   ``cuModuleGetFunction``, and launched with ``cuLaunchKernel``. Memory and
   streams use ``cuMemAlloc`` / ``cuMemcpy*Async`` / ``CUstream``.

The backend is implemented in two modules:

- ``tornado-drivers/cuda`` — the backend (Graal compiler integration + runtime),
  Java package ``uk.ac.manchester.tornado.drivers.cuda``.
- ``tornado-drivers/cuda-jni`` — the native JNI layer (CUDA Driver API + NVRTC),
  which builds ``libtornado-cuda.so``.

Prerequisites
----------------------------------------------

- **JDK 21** (set ``JAVA_HOME`` accordingly).
- **CUDA Toolkit** including **NVRTC** and ``libcuda`` (see
  `Driver Installation`_ above).
- **GCC** and **CMake** to build the native JNI layer.

Verified on an NVIDIA RTX 3070 (compute capability 8.6) with CUDA 11.x.

Building the CUDA backend
----------------------------------------------

.. important::

   **Build the CUDA backend on its own** (not together with OpenCL). With both
   the CUDA and OpenCL backends installed, the unit tests can silently run on
   the OpenCL device and report false positives. A CUDA-only build exposes
   exactly one backend (Tornado device ``0:0``), so the results are
   unambiguous.

.. code:: bash

   # JDK 21 is required
   export JAVA_HOME=/path/to/jdk21

   # Build the CUDA backend (tornado-drivers/cuda + cuda-jni) and the SDK
   make BACKEND=cuda

The build maps to the Maven ``cuda-backend`` profile, which activates the
``tornado-drivers/cuda`` and ``tornado-drivers/cuda-jni`` modules.

Selecting the CUDA device
----------------------------------------------

After a CUDA-only build, ``tornado --devices`` lists a single
``CUDADriver`` device:

.. code:: bash

   $ tornado --devices

   Number of Tornado drivers: 1
   Driver: CUDADriver
      Total number of devices  : 1
      Tornado device=0:0  (DEFAULT)
        CUDA C 1.0 -- <your NVIDIA GPU>

Because there is a single backend, the CUDA device is ``0:0``. To pin the unit
tests to it, pass ``-Dtornado.unittests.device=0:0``:

.. code:: bash

   # Run a kernel and dump the generated CUDA C source
   tornado-test --printKernel --jvm="-Dtornado.unittests.device=0:0" \
     uk.ac.manchester.tornado.unittests.arrays.TestArrays#testVectorAdditionFloat

   # Run a unit-test class on the CUDA device
   tornado-test --ea -V --jvm="-Dtornado.unittests.device=0:0" <test.class>

.. _Supported and in-progress features:

Supported and in-progress features
----------------------------------------------

Supported
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

- Scalar ``@Parallel`` kernels end-to-end (``int`` / ``float`` / ``double`` /
  ``long``), loops, conditionals / branching, and grids.
- Global-memory **reductions** and ``KernelContext`` local-memory reductions
  (``__shared__``, ``__syncthreads``).
- **Math** built-ins (``sin`` / ``cos`` / ``sqrt`` / …, ``signum`` including
  NaN semantics, ``clamp``, ``radians``, relational ``isequal`` / …).
- Local and private **scalar** arrays, and the native CUDA scalar types.
- **FP16 / ``__half``** scalar add / sub / mul / div (via ``cuda_fp16.h``).
- NVRTC runtime compilation with a device-matched ``--gpu-architecture`` and
  include paths.

In progress / pending
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

- **Vector types** (``float2/3/4``, ``int*``, ``double*``): width ≤ 4 with
  native CUDA vector types and component-wise ops. Widths 8 and 16 have no
  native CUDA equivalent and are marked unsupported.
- **Object fields / instance kernels** (the ``this`` receiver and object
  parameter marshalling).
- **Private array copies**.
- **FP16 conversions** and half-precision matrix multiply.
- **Atomics** (a subset of CUDA ``atomicAdd`` / ``atomicSub`` / ``atomicCAS``).
- **Two-stage local reductions**.
- **CUDA Graph API** support (``withCUDAGraph``).

Not applicable (other-backend specific)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

- Prebuilt SPIR-V / OpenCL binary tests.
