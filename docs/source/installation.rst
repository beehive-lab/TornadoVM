Installation & Configuration
#############################

.. _installation:

Quick Install (recommended)
****************************

Most users do not need to build TornadoVM from source. Prebuilt SDKs (OpenCL, CUDA, Metal, or a full bundle) are available from the official website:

`tornadovm.org/downloads <https://www.tornadovm.org/downloads>`__

Via SDKMAN!:

.. code-block:: bash

   sdk install tornadovm                              # default: latest version, OpenCL backend
   sdk install tornadovm <version>-jdk22plus-<backend>
   # e.g.:
   sdk install tornadovm 7.0.1-jdk22plus-cuda
   sdk install tornadovm 7.0.1-jdk22plus-metal
   sdk install tornadovm 7.0.1-jdk22plus-opencl

To install a specific backend, pass the candidate version as ``<version>-jdk22plus-<backend>`` (``<backend>`` is ``opencl``, ``cuda``, ``metal``, or ``full`` for all backends). Run ``sdk list tornadovm`` to see all available combinations.

TornadoVM requires JDK 22 or newer. The ``jdk22plus`` SDK is built once and runs unchanged on any JDK from 22 onwards, including 27 — switching JDK needs no reinstall and no rebuild, only ``tornado --generate-argfile`` if you launch via the argfile rather than the ``tornado`` command. JDK 21 is no longer supported; use an earlier release for a ``jdk21`` SDK.

The TornadoVM API is also published on Maven Central, so you can add it directly to an existing Java project without installing the SDK at all. Use the ``-jdk22plus`` artifact version:

.. code-block:: xml

   <dependency>
      <groupId>io.github.beehive-lab</groupId>
      <artifactId>tornado-api</artifactId>
      <version>7.0.1-jdk22plus</version>
   </dependency>

Docker images and cloud (AWS) images are also available; see :ref:`docker` and :ref:`cloud`.

If you want to **build TornadoVM from source** — to contribute to the project, run the latest ``develop`` branch, or build a custom backend combination — see :ref:`build-from-source` in the Developer Guidelines.
