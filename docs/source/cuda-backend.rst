.. _cuda-backend:

CUDA Devices
==========================================

Prerequisites
----------------------------------------------

In order to use the PTX backend of TornadoVM, you will need a CUDA compatible device (NVIDIA GPUs with CUDA support).

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
| Build TornadoVM with the ``PTX`` backend selected and run ``tornado --devices``.

The output of the TornadoVM build containing both backends (PTX and OpenCL) should look like this:

.. code:: bash

      Number of Tornado drivers: 2
      Total number of devices  : 1
      Tornado device=0:0
       CUDA-PTX -- GeForce GTX 1650
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

Note that the first Tornado driver will always correspond to the CUDA device detected by the PTX backend.

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

We have tested the PTX backend of TornadoVM on the following configurations:

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
           
The PTX backend might fail with the ``Quadro GP100``, driver ``384.111``, with segmentation faults for some of the unit test due to driver issues. 