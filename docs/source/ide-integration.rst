Build and Run with IDE
##################################


JetBrains IntelliJ Installation
*******************************

Download and install the latest IntelliJ IDEA Community Edition:
https://www.jetbrains.com/idea/download/

Change the IntelliJ maximum memory to 2 GB or more (follow `these instructions <https://www.jetbrains.com/help/idea/increasing-memory-heap.html#d1366197e127>`__).

For IntelliJ to pick up the required TornadoVM dependencies from the `pom.xml` file, go to **View > Tool Windows > Maven**, and select the following profiles:

- **graal-jdk-21**
- **ptx-backend**
- **opencl-backend**
- **spirv-backend**

.. _ide_plugins:

Required JetBrains Plugins
**************************

Open IntelliJ and go to **Preferences > Plugins > Browse Repositories**.
Install the following plugins:

1. **Eclipse Code Formatter**
   Install it from:
   https://plugins.jetbrains.com/plugin/6546-eclipse-code-formatter
   This plugin formats source code according to Eclipse standards (.xml).

   After installation:

   - Go to: **File > Settings > Other Settings > Eclipse Code Formatter**
   - Select **Use the Eclipse code formatter**
   - Load the TornadoVM code formatter from: `./scripts/templates/eclipse-settings/Tornado.xml`
   - Click **Apply**

2. **Save Actions**
   Install it from:
   https://plugins.jetbrains.com/plugin/7642-save-actions
   This plugin allows post-save actions (e.g. code formatting on save).

   - To enable auto-formatter with save-actions:
     - Go to: **Settings > Other Settings > Save Actions**
     - Check the following options:
     - Activate save actions on save
     - Activate save actions in shortcut
     - Reformat file

3. **Python Plugin (Optional)**
   Install it from:
   https://plugins.jetbrains.com/plugin/631-python
   Allows Python scripting in IntelliJ.

4. **CheckStyle-IDEA Plugin (Optional)**
   Install it from:
   https://plugins.jetbrains.com/plugin/1065-checkstyle-idea
   Checks your project for compliance with custom checkstyle rules.

.. _ide_tornadovm_build:

Building TornadoVM with IntelliJ
********************************

Follow these steps to build TornadoVM with IntelliJ.

Prerequisites
=============

Ensure that **cmake** is installed and available in your system's PATH.

1. Check if cmake is in the PATH
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

   Verify that your system recognizes `cmake` by running:

   .. code:: bash

      $ cmake --version

   If it is recognized, skip the next step. Otherwise, you need to add cmake to your system's PATH.

2. Add cmake to PATH (macOS/Linux)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

You can add `cmake` to your PATH by updating your shell configuration file.

a. Open your shell configuration file (e.g. `.bashrc`, `.zshrc`):

   .. code:: bash

      $ vim ~/.zshrc  # or ~/.bashrc depending on your shell

b. Add the following line, replacing `<custom-path>` with the path to your cmake installation:

   .. code:: bash

      $ export PATH=<custom-path>/cmake-3.25.2-macos-universal/CMake.app/Contents/bin:$PATH

c. Save and apply the changes:

   .. code:: bash

      $ source ~/.zshrc  # or source ~/.bashrc

3. Add cmake and pyInstaller to PATH (Windows)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

a. Find the path of the `cmake` and `pyInstaller` commands:
Open your shell configuration (e.g. x64 Native Tools Command Prompt for VS 2022) and initialize the environment:

   .. code:: bash

      $ cd <path-to-TornadoVM-directory>
      $ .\bin\windowsMicrosoftStudioTools2022.cmd

Verify that your system recognizes `cmake` and `pyInstaller` by running:

   .. code:: bash

      $ where cmake
      $ where pyInstaller

b. Update the PATH:
You can add the variables to your PATH by searching **Edit the system environment variables**, clicking **Environment Variables...**, and editing the **PATH** with your cmake directory.

**Important:** It is recommended to use the python interpreter under the virtual environment (.venv) as the Python SDK for your TornadoVM project, since it contains all dependent modules (i.e., PyInstaller, psutil) to build TornadoVM and run the tests from IntelliJ.

   **Examples**:

   .. code:: bash

      C:\Program Files\Microsoft Visual Studio\2022\Community\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\

   .. code:: bash

      <path-to-TornadoVM-directory>\.venv\Scripts

Configuring the Project Structure
*********************************

1. Go to **File > Project Structure** and apply the following configurations:

a. In the **Project** tab:

   - The *SDK* uses a valid Java version (e.g. OpenJDK 21, GraalVM JDK 21, etc.).
   - The *Language level* is set to match the Java version (e.g. Java 21).

b. In the **Modules** tab:

   - Ensure that the *Language level* of every module matches the project level (e.g. Java 21).

Configuring IntelliJ for TornadoVM
**********************************

1. Initializing the IntelliJ Project Files
==========================================

To initialize IDE project files for building and running TornadoVM from IntelliJ, you must have first built TornadoVM and loaded the file with the environment variables (``setvars.sh``, ``setvars.cmd``), as explained in the :ref:`installation`.

Then you can execute the command to generate the IDE project files based on your built TornadoVM instance (i.e., with the JAVA_HOME and the backends), as follows:

   .. code:: bash

      $ tornado --intellijinit

2. Configuring the TornadoVM Python Build Utility
=================================================

a. Navigate to the Python configuration
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Go to **Run > Edit Configurations > Python > TornadoVM-Build**

b. Configure the Python interpreter
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

In the **Use specified interpreter** field, select a valid Python interpreter installed on your system.

c. Update environment variables for selected backends
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The **Environmental variables** section has been populated based on your built TornadoVM instance.
If you change ``JAVA_HOME`` or built with different backends, you will need to run the ``tornado --intellijinit`` command.

d. Build TornadoVM from IntelliJ
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Run a new build by clicking **Run TornadoVM-Build**.

Configuring Applications to Debug/Run
*************************************

1. Obtain the TornadoVM Java flags
==================================

To run and debug Java applications with TornadoVM on IntelliJ, you need to obtain the TornadoVM `JAVA_FLAGS`. Open a terminal and run:

- **macOS/Linux:**

   .. code:: bash

      $ cd <path-to-TornadoVM-directory>
      $ source setvars.sh
      $ tornado --printJavaFlags

- **Windows:**

   .. code:: bash

      $ cd <path-to-TornadoVM-directory>
      $ .\bin\windowsMicrosoftStudioTools2022.cmd
      $ setvars.cmd
      $ tornado --printJavaFlags

The output will differ depending on the backends you've built. For example, if you build with all backends, it should be similar to this:

   .. code:: bash

      <path-to-TornadoVM-directory>/etc/dependencies/TornadoVM-graal-jdk-21/graalvm-community-openjdk-21.0.1+12.1/bin/java
      -server -XX:-UseCompressedOops -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -XX:-UseCompressedClassPointers --enable-preview -Djava.library.path=<path-to-TornadoVM-directory>/bin/sdk/lib  --module-path .:<path-to-TornadoVM-directory>/bin/sdk/share/java/tornado
      -Dtornado.load.api.implementation=uk.ac.manchester.tornado.runtime.tasks.TornadoTaskGraph -Dtornado.load.runtime.implementation=uk.ac.manchester.tornado.runtime.TornadoCoreRuntime -Dtornado.load.tornado.implementation=uk.ac.manchester.tornado.runtime.common.Tornado
      -Dtornado.load.annotation.implementation=uk.ac.manchester.tornado.annotation.ASMClassVisitor -Dtornado.load.annotation.parallel=uk.ac.manchester.tornado.api.annotations.Parallel  -XX:+UseParallelGC
      @<path-to-TornadoVM-directory>/bin/sdk/etc/exportLists/common-exports
      @<path-to-TornadoVM-directory>/bin/sdk/etc/exportLists/opencl-exports
      @<path-to-TornadoVM-directory>/bin/sdk/etc/exportLists/spirv-exports
      @<path-to-TornadoVM-directory>/bin/sdk/etc/exportLists/ptx-exports --add-modules ALL-SYSTEM,tornado.runtime,tornado.annotation,tornado.drivers.common,tornado.drivers.opencl,tornado.drivers.opencl,tornado.drivers.ptx

Copy the flags starting from `-server` to the end.

2. Configure new Applications
=============================

a. Add new configurations:

Go to **Run > Edit Configurations > Application > Add new run configuration...**

   Add your own parameters, for example:

   - **Name:** MatrixMultiplication2D
   - **VM Options:** Add the flags you copied earlier
   - **Main class:** e.g. `uk.ac.manchester.tornado.examples.compute.MatrixMultiplication2D`
   - **Program arguments:** e.g. `128`

b. Apply and run the application.


Configuring the IDEA CheckStyle
*******************************

1. Go to **File > Settings > Tools > CheckStyle**.

2. Under **Configuration File**, click the *plus* sign to add a new configuration.

3. Set the description to "TornadoVM Checkstyle".

4. **Use a local Checkstyle file** and point to:
   `<path-to-TornadoVM-directory>/tornado-assembly/src/etc/checkstyle.xml`.

5. Click **Next**, then **Finish**.

6. Enable the new CheckStyle configuration in the list of active configurations.
