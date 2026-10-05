Installation & Configuration
#############################

.. _installation:

Quick Install (recommended)
****************************

Most users do not need to build TornadoVM from source. Prebuilt SDKs (OpenCL, CUDA, Metal, or a full bundle) are available from the official website:

`tornadovm.org/downloads <https://www.tornadovm.org/downloads>`__

Via SDKMAN!:

.. code-block:: bash

   sdk install tornadovm                              # default: latest version, JDK 22+, OpenCL backend
   sdk install tornadovm <version>-<jdk-version>-<backend>
   # e.g.:
   sdk install tornadovm 7.1.0-jdk22plus-cuda
   sdk install tornadovm 7.1.0-jdk22plus-metal
   sdk install tornadovm 7.1.0-jdk21-opencl

To install a specific JDK and/or backend combination, pass the candidate version as ``<version>-<jdk-version>-<backend>`` (``<backend>`` is ``opencl``, ``cuda``, ``metal``, or ``full`` for all backends). Run ``sdk list tornadovm`` to see all available combinations.

There are two SDK families, and ``<jdk-version>`` selects between them:

.. list-table::
   :header-rows: 1
   :widths: 20 20 60

   * - ``<jdk-version>``
     - Runs on
     - Notes
   * - ``jdk21``
     - JDK 21 only
     - Compiled with ``--enable-preview`` (FFM is a preview API on JDK 21), which pins the class files to exactly JDK 21.
   * - ``jdk22plus``
     - JDK 22 and newer
     - One SDK for every JDK from 22 up, including 27. No preview features, so the class files stay forward compatible.

Only the JDK 21 SDK is tied to a single JDK. The ``jdk22plus`` SDK is built once and runs unchanged on any JDK from 22 onwards — switching JDK needs no reinstall and no rebuild.

The TornadoVM API is also published on Maven Central, so you can add it directly to an existing Java project without installing the SDK at all. The artifact version's suffix must match the SDK family you run on: ``-jdk21`` or ``-jdk22plus``.

.. code-block:: xml

   <!-- JDK 22 and newer, including 27 -->
   <dependency>
      <groupId>io.github.beehive-lab</groupId>
      <artifactId>tornado-api</artifactId>
      <version>7.0.1-jdk22plus</version>
   </dependency>

   <!-- JDK 21 -->
   <dependency>
      <groupId>io.github.beehive-lab</groupId>
      <artifactId>tornado-api</artifactId>
      <version>7.1.0-jdk21</version>
   </dependency>

Docker images and cloud (AWS) images are also available; see :ref:`docker` and :ref:`cloud`.

If you want to **build TornadoVM from source** — to contribute to the project, run the latest ``develop`` branch, or build a custom backend combination — see :ref:`build-from-source` in the Developer Guidelines.
