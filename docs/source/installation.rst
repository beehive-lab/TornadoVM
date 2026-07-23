Installation & Configuration
#############################

.. _installation:

Quick Install (recommended)
****************************

Most users do not need to build TornadoVM from source. Prebuilt SDKs (OpenCL, PTX, CUDA, SPIR-V, Metal, or a full bundle) are available from the official website:

`tornadovm.org/downloads <https://www.tornadovm.org/downloads>`__

Via SDKMAN!:

.. code-block:: bash

   sdk install tornadovm                              # default: latest version, JDK 21, OpenCL backend
   sdk install tornadovm <version>-<jdk-version>-<backend>
   # e.g.:
   sdk install tornadovm 5.0.0-jdk21-cuda
   sdk install tornadovm 5.0.0-jdk25-metal

To install a specific JDK and/or backend combination, pass the candidate version as ``<version>-<jdk-version>-<backend>`` (e.g. ``opencl``, ``ptx``, ``cuda``, ``spirv``, ``metal``, or ``full`` for all backends). Run ``sdk list tornadovm`` to see all available combinations.

The TornadoVM API is also published on Maven Central, so you can add it directly to an existing Java project without installing the SDK at all. The artifact version's suffix must match the JDK you run on: ``-jdk21`` for JDK 21, ``-jdk25`` for JDK 25.

.. code-block:: xml

   <!-- JDK 21 -->
   <dependency>
      <groupId>io.github.beehive-lab</groupId>
      <artifactId>tornado-api</artifactId>
      <version>5.0.0-jdk21</version>
   </dependency>

   <!-- JDK 25 -->
   <dependency>
      <groupId>io.github.beehive-lab</groupId>
      <artifactId>tornado-api</artifactId>
      <version>5.2.0-jdk25</version>
   </dependency>

Docker images and cloud (AWS) images are also available; see :ref:`docker` and :ref:`cloud`.

If you want to **build TornadoVM from source** — to contribute to the project, run the latest ``develop`` branch, or build a custom backend combination — see :ref:`build-from-source` in the Developer Guidelines.
