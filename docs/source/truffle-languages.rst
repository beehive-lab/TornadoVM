Polyglot Programming
=============================
TornadoVM can be used with the GraalVM Truffle Polyglot API to invoke Task-Graphs from guest programming languages such as Python, Ruby, etc. This guide will describe how to execute TornadoVM programs through code written in Python, JavaScript, and Ruby.

1. Prerequisites
----------------------------------------------

A) Configuration of the JAVA_HOME Variable
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
To enable polyglot support, the ``JAVA_HOME`` variable must be set to the GraalVM path.
Instructions on how to install TornadoVM with GraalVM can be found here: :ref:`installation_graalvm`.

.. code-block:: bash

   $ export JAVA_HOME=<path to GraalVM jdk 21>

B) GraalVM Polyglot Dependencies
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
The implementations of the programming languages (e.g., Python, JavaScript, Ruby) that are supported by the GraalVM polyglot API are now shipped as standalone distributions. And they can either be used as any other Java library from :ref:`Maven Central <graalvm-mvn-dependencies>`, or as :ref:`standalone toolkits <build-standalone-toolkits>`.

.. _graalvm-mvn-dependencies:

2. Using the GraalVM Polyglot Dependencies from Maven Central
-----------------------------------------------------------------

A) Build TornadoVM with the GraalVM Polyglot Dependencies from Maven Central
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
To build TornadoVM and utilize the `maven dependencies for GraalPy, GraalVM JavaScript and TruffleRuby <https://central.sonatype.com/namespace/org.graalvm.polyglot/>`_, you can build TornadoVM and use the ``graalvm-polyglot`` profile.

.. code-block:: bash

   $ make graal-jdk-21 polyglot

B) Run the Examples
~~~~~~~~~~~~~~~~~~~~~~
Now, that TornadoVM is built with the polyglot dependencies, you can run the available examples that exhibit the interoperability of Python, JavaScript and Ruby code from Java.

.. code-block:: bash

   $ tornado --debug -m tornado.examples/uk.ac.manchester.tornado.examples.polyglot.HelloPython
   $ tornado --debug -m tornado.examples/uk.ac.manchester.tornado.examples.polyglot.HelloJS
   $ tornado --debug -m tornado.examples/uk.ac.manchester.tornado.examples.polyglot.HelloRuby

.. _build-standalone-toolkits:

3. Using the GraalVM Polyglot Dependencies as Standalone Toolkits
------------------------------------------------------------------------
However, to interoperate from programs written in those programming languages and invoke a Java method, users would use the standalone distributions.
However, an aftermath of the last change is that the dedicated builds of GraalVM implemented languages, such as GraalPy, GraalVM JavaScript and TruffleRuby, do not work out-of-the-box with TornadoVM. Instead, users must build those frameworks from source.

A) Build GraalVM Polyglot Dependencies from Source
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
GraalVM implementations of the programming languages that can interoperate with Java are provided as standalone distributions, e.g., `GraalPy <https://github.com/oracle/graalpython.git/>`_, `GraalVM JavaScript <https://github.com/oracle/graaljs.git/>`_, `TruffleRuby <https://github.com/oracle/truffleruby.git/>`_.
As detailed in the `GraalVM Reference Manuals <https://www.graalvm.org/latest/reference-manual/>`_, the following dependencies must be downloaded for each of the programming languages supported:

To ease users, we outline beneath two steps in order to build each of the programming languages supported and to interoperate with TornadoVM.

**Step 1: Build GraalPy, GraalVM JavaScript and TruffleRuby, from source.**

* **Python**

.. code-block:: bash

   $ git clone https://github.com/oracle/graalpython.git && cd graalpython && git checkout graal-23.1.0
   $ git clone https://github.com/graalvm/mx.git mx
   $ export PATH=$PWD/mx:$PATH
   $ mx fetch-jdk
   $ export JAVA_HOME=~/.mx/jdks/labsjdk-ce-21.0.1-jvmci-23.1-b22
   $ mx --dy /compiler python-gvm

* **JavaScript**

.. code-block:: bash

   $ git clone https://github.com/oracle/graaljs.git && cd graaljs && git checkout graal-23.1.0
   $ git clone https://github.com/graalvm/mx.git mx
   $ export PATH=$PWD/mx:$PATH
   $ mx fetch-jdk
   $ export JAVA_HOME=~/.mx/jdks/labsjdk-ce-21.0.1-jvmci-23.1-b22
   $ mx --dynamicimports /compiler build

* **Ruby**

.. code-block:: bash

   $ git clone https://github.com/oracle/truffleruby.git && cd truffleruby && git checkout graal-23.1.0
   $ git clone https://github.com/graalvm/mx.git mx
   $ export PATH=$PWD/mx:$PATH
   $ mx fetch-jdk
   $ export JAVA_HOME=~/.mx/jdks/labsjdk-ce-21.0.1-jvmci-23.1-b22
   $ mx sforceimports
   $ mx --dynamicimports /compiler build

**Step 2: Set up the suitable variable for each programming language.**

Set the ``JAVA_HOME`` variable to the GraalVM JDK:

.. code-block:: bash

   $ export JAVA_HOME=<path to GraalVM jdk 21>

To enable TornadoVM to employ the standalone built distribution of the GraalVM implementations, users must set the following variables.

**Note:** The following examples show tentantive paths for a Linux environment. If you are using Mac OS X, you should ensure that your path includes the ``</Contents/Home>`` suffix.

* For Python, set **GRAALPY_HOME**:

.. code-block:: bash

   $ export GRAALPY_HOME=<path-to-graalpy>/../graal/sdk/mxbuild/linux-amd64/GRAALVM_03DCD25EA1_JAVA21/graalvm-03dcd25ea1-java21-23.1.0-dev

* For JavaScript, set **GRAALJS_HOME**:

.. code-block:: bash

   $ export GRAALJS_HOME=<path-to-graaljs>/../graal/sdk/mxbuild/linux-amd64/GRAALVM_3AF13F6F38_JAVA21/graalvm-3af13f6f38-java21-23.1.0-dev

* For Ruby, set **TRUFFLERUBY_HOME**:

.. code-block:: bash

   $ export TRUFFLERUBY_HOME=<path-to-truffleruby>/../graal/sdk/mxbuild/linux-amd64/GRAALVM_AEA5C30A3B_JAVA21/graalvm-aea5c30a3b-java21-23.1.0-dev

B) Interoperate between a Polyglot Programming Language and TornadoVM through Graal's Polyglot API
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
In the following example, we will iterate over the necessary steps to invoke a TornadoVM computation from `Python, JavaScript and Ruby programs <https://github.com/beehive-lab/TornadoVM/tree/master/tornado-assembly/src/examples/polyglotTruffle>`_, using the ``MyCompute`` class from the `TornadoVM examples module <https://github.com/beehive-lab/TornadoVM/blob/master/tornado-examples/src/main/java/uk/ac/manchester/tornado/examples/polyglot/MyCompute.java/>`_. However, users can create their own Java classes with the code to be accelerated following the TornadoVM API guidelines :ref:`programming`.

**Step 1: Create a variable that is of the Java class type.**

* **Python**

.. code-block:: bash

   myclass = java.type('uk.ac.manchester.tornado.examples.polyglot.MyCompute')


* **JavaScript**

.. code-block:: bash

   var myclass = Java.type('uk.ac.manchester.tornado.examples.polyglot.MyCompute')

* **Ruby**

.. code-block:: bash

   myclass = Java.type('uk.ac.manchester.tornado.examples.polyglot.MyCompute')

**Step 2: Use this variable to invoke the Java function that contains the Task-Graph.**

In this example, the function is named ``compute()`` and it performs a matrix multiplication.

* **Python**

.. code-block:: bash

   myclass.compute()


* **JavaScript**

.. code-block:: bash

   myclass.compute()

* **Ruby**

.. code-block:: bash

   myclass.compute()

**Step 3: Execute the Ruby/JavaScript/Python program through TornadoVM.**

The polyglot program can be executed using the ``tornado`` command, followed by the ``--truffle`` option and the language of the program, as follows:

.. code-block:: bash

   $ tornado --truffle python|ruby|js|node <path/to/polyglot/program>

All of the existing TornadoVM options (e.g., ``--printKernel``, etc.) can be used as always.

C) Run the Examples
~~~~~~~~~~~~~~~~~~~~~~
The ``tornado-assembly/src/examples/polyglotTruffle`` directory contains three examples, one for each of the supported languages.
These examples can be executed using the ``polyglotTests.sh`` script.

.. code-block:: bash

   $ ./scripts/polyglotTests.sh

* **Python**

.. code-block:: bash

   $ tornado --printKernel --truffle python $TORNADOVM_HOME/examples/polyglotTruffle/mxmWithTornadoVM.py

* **JavaScript**

.. code-block:: bash

   $ tornado --printKernel --truffle js $TORNADOVM_HOME/examples/polyglotTruffle/mxmWithTornadoVM.js

* **Ruby**

.. code-block:: bash

   $ tornado --printKernel --truffle ruby $TORNADOVM_HOME/examples/polyglotTruffle/mxmWithTornadoVM.rb
