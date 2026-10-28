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
   sdk install tornadovm <version>-<jdk-version>-<backend>
   # e.g.:
   sdk install tornadovm 8.0.0-jdk25plus-cuda
   sdk install tornadovm 5.2.1-jdk25plus-metal
   sdk install tornadovm 5.2.1-jdk25plus-opencl

To install a specific JDK and/or backend combination, pass the candidate version as ``<version>-<jdk-version>-<backend>`` (``<backend>`` is ``opencl``, ``cuda``, ``metal``, or ``full`` for all backends). Run ``sdk list tornadovm`` to see all available combinations.

TornadoVM requires JDK 25 or newer. ``<jdk-version>`` is ``jdk25plus``: one SDK for every JDK from 25 up, including 27. It uses no preview features, so it runs unchanged on any JDK from 25 onwards — switching JDK needs no reinstall and no rebuild, only ``tornado --generate-argfile`` if you launch via the argfile rather than the ``tornado`` command. Releases up to 8.0 also shipped a ``jdk21`` SDK for JDK 21.

The TornadoVM API is also published on Maven Central, so you can add it directly to an existing Java project without installing the SDK at all. Use the ``-jdk25plus`` version suffix.

.. code-block:: xml

   <!-- JDK 25 and newer -->
   <dependency>
      <groupId>io.github.beehive-lab</groupId>
      <artifactId>tornado-api</artifactId>
      <version>5.2.1-jdk25plus</version>
   </dependency>

Docker images and cloud (AWS) images are also available; see :ref:`docker` and :ref:`cloud`.

If you want to **build TornadoVM from source** — to contribute to the project, run the latest ``develop`` branch, or build a custom backend combination — see :ref:`build-from-source` in the Developer Guidelines.
