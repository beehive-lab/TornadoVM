Polyglot Programming
=============================
TornadoVM can be used with the GraalVM Truffle Polyglot API to invoke Task-Graphs from guest programming languages such as Python, Ruby, etc. This guide will describe how to execute TornadoVM programs through code written in Python, JavaScript, and Ruby. 

1. Prerequisites
----------------------------------------------

A) Configuration of the JAVA_HOME Variable
~~~~~~~~~~~~~~~~~~~~~~
To enable polyglot support, the ``JAVA_HOME`` variable must be set to the GraalVM path. 
Instructions on how to install TornadoVM with GraalVM can be found here: :ref:`installation_graalvm`. 

B) GraalVM Polyglot Dependencies
~~~~~~~~~~~~~~~~~~~~~~
Since GraalVM 23.1.0, there have been two important changes as described in a `dedicated GraalVM blog post <https://medium.com/graalvm/truffle-unchained-13887b77b62c/>`_:

* The GraalVM Updater is removed from the distribution of GraalVM without replacement.

* More standalone distributions are shipped for languages, such as Node and LLVM, that did not have such a distribution. Additionally, dedicated builds are provided with a pre-installed GraalVM JDK called JVM-standalone for every language. **The GraalVM JDK that is pre-installed does not include the compiler modules of Graal. Instead, it is built with libGraal (i.e., the Graal compiler compiled as a native library) to reduce the disk footprint.**

An aftermath of the last change is that the dedicated builds of GraalVM implemented languages, such as GraalPy, GraalVM JavaScript and TruffleRuby, do not work out-of-the-box with TornadoVM. Instead, developers must build those frameworks manually from source.

C) Build GraalVM Implementations of Languages from Source
~~~~~~~~~~~~~~~~~~~~~~
GraalVM implementations of the programming languages that can interoperate with Java are provided as standalone distributions, e.g., `GraalPy <https://github.com/oracle/graalpython.git/>`_, `GraalVM JavaScript <https://github.com/oracle/graaljs.git/>`_, `TruffleRuby <https://github.com/oracle/truffleruby.git/>`_.
As detailed in the `GraalVM Reference Manuals <https://www.graalvm.org/latest/reference-manual/>`_, the following dependencies must be downloaded for each of the programming languages supported:

To ease programmers, we outline beneath two steps in order to build each of the programming languages supported and to interoperate with TornadoVM.

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

   $ export JAVA_HOME=<path-to-tornadovm>/etc/dependencies/TornadoVM-graalvm-jdk-21/graalvm-community-openjdk-21.0.1+12.1

* For Python, set **GRAALPY_HOME**:

.. code-block:: bash

   $ export GRAALPY_HOME=<path-to-graalpy>/../graal/sdk/mxbuild/linux-amd64/GRAALVM_03DCD25EA1_JAVA21/graalvm-03dcd25ea1-java21-23.1.0-dev

* For JavaScript, set **GRAALJS_HOME**:

.. code-block:: bash

   $ export GRAALJS_HOME=<path-to-graaljs>/../graal/sdk/mxbuild/linux-amd64/GRAALVM_3AF13F6F38_JAVA21/graalvm-3af13f6f38-java21-23.1.0-dev

* For Ruby, set **TRUFFLERUBY_HOME**:

.. code-block:: bash

   $ export TRUFFLERUBY_HOME=<path-to-truffleruby>/../graal/sdk/mxbuild/linux-amd64/GRAALVM_AEA5C30A3B_JAVA21/graalvm-aea5c30a3b-java21-23.1.0-dev

2. Executing a TornadoVM program through Graal's Polyglot API
---------------
In the following example, we will iterate over the necessary steps to invoke a TornadoVM computation from Python, JavaScript and Ruby programs, using the ``MyCompute`` class from the TornadoVM examples module. However, users can create their own Java classes with the code to be accelerated following the TornadoVM API guidelines :ref:`programming`. 

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

In this example, the function is named ``compute()``.

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

The polyglot program can be executed using the ``tornado`` command, followed by the ``--truffle`` option and the language of the program. 
E.g., 
    
.. code-block:: bash
    
   $ tornado --truffle python|ruby|js|node <path/to/polyglot/program>

All of the existing TornadoVM options (e.g., ``--printKernel``, etc.) can be used as always.  

3. Testing
---------------

The ``tornado-assembly/src/examples/polyglotTruffle`` directory contains three examples, one for each of the supported languages.  
These examples can be executed using the ``polyglotTests.sh`` script. 

.. code-block:: bash

   $ ./scripts/polyglotTests.sh 

* **Python**

.. code-block:: bash

   $ tornado --printKernel --truffle python $TORNADO_SDK/examples/polyglotTruffle/mxmWithTornadoVM.py

* **JavaScript**    

.. code-block:: bash

   $ tornado --printKernel --truffle js $TORNADO_SDK/examples/polyglotTruffle/mxmWithTornadoVM.js

* **Ruby**    

.. code-block:: bash

   $ tornado --printKernel --truffle ruby $TORNADO_SDK/examples/polyglotTruffle/mxmWithTornadoVM.rb

