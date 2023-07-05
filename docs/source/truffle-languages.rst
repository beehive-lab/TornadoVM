Polyglot Programming
=============================
TornadoVM can be used with the GraalVM Truffle Polyglot API to invoke Task-Graphs from guest programming languages such as Python, R, etc.. This guide will describe how to execute TornadoVM programs through code written in Python, JavaScript, and R. 

1. Prerequisites
----------------------------------------------

A) Configuration of the JAVA_HOME Variable
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
To enable polyglot support, the ``JAVA_HOME`` variable must be set to the GraalVM path. 
Instructions on how to install TornadoVM with GraalVM can be found here: :ref:`installation_graalvm`. 

B) GraalVM Polyglot Dependencies
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
As detailed in the `GraalVM Reference Manuals <https://www.graalvm.org/latest/reference-manual/>`_, the following dependencies must be downloaded for each of the programming languages supported:

* **Python**

.. code-block:: bash

  $JAVA_HOME/bin/gu install python

* **JavaScript**

.. code-block:: bash

  $JAVA_HOME/bin/install js

* **R**

.. code-block:: bash

  $JAVA_HOME/bin/gu install r

2. Executing a TornadoVM program through Graal's Polyglot API
---------------
In the following example, we will iterate over the necessary steps to invoke a TornadoVM computation from Python, JavaScript and R programs, using the ``MyCompute`` class from the TornadoVM examples module. However, users can create their own Java classes with the code to be accelerated following the TornadoVM API guidelines :ref:`programming`. 

* Step 1: 
    Create a variable that is of the Java class type. 

    **Python**

    .. code-block:: bash

        myclass = java.type('uk.ac.manchester.tornado.examples.polyglot.MyCompute')
        
    
    **JavaScript**

    .. code-block:: bash

        var myclass = Java.type('uk.ac.manchester.tornado.examples.polyglot.MyCompute')
    
    **R**

    .. code-block:: bash

        myclass <- java.type('uk.ac.manchester.tornado.examples.polyglot.MyCompute')

* Step 2: 
    Use this variable to invoke the Java function that contains the Task-Graph. In this example, the function is named ``compute()``. 
    

    **Python**

    .. code-block:: bash

        myclass.compute()
        
    
    **JavaScript**

    .. code-block:: bash

        myclass.compute()
    
    **R**

    .. code-block:: bash

        myclass$compute()

* Step 3:
    Execute the R/JavaScript/Python program through TornadoVM. 
    The polyglot program can be executed using the ``tornado`` command, followed by the ``--truffle`` option and the language of the program. 
    
    E.g., 
    
    .. code-block:: bash
    
        $ tornado --truffle r|python|js <path/to/polyglot/program>


All of the existing TornadoVM options (e.g., ``--printKernel``, etc.) can be used as always.  

3. Testing
---------------

The ``tornado-assembly/scr/example/polyglotTruffle`` directory contains three examples, one for each of the supported languages.  
These examples can be executed using the ``polyglotTests.sh`` script. 

.. code-block:: bash

  $ ./scripts/polyglotTests.sh 
