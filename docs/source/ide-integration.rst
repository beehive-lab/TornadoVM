.. _build-run-with-ide:

Build and Run with IDE
======================

IntelliJ
--------

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
==========================

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
   This plugin allows post-save actions (e.g., code formatting on save).

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

Build TornadoVM with IntelliJ
=============================

Follow these steps to build TornadoVM with IntelliJ.

Prerequisites
-------------

Ensure that **cmake** is installed and available in your system's PATH.

1. **Check if cmake is in your PATH:**
   Verify that your system recognizes `cmake` by running:

   .. code:: bash

      $ cmake --version

   If it is recognised, skip the next step. Otherwise, you need to add cmake to your system's PATH.

2. **Add cmake to PATH (macOS/Linux):**
You can add cmake to your PATH by updating your shell configuration file.

a. Open your shell configuration file (e.g., `.bashrc`, `.zshrc`):

   .. code:: bash

      $ vim ~/.zshrc  # or ~/.bashrc depending on your shell

b. Add the following line, replacing `<custom-path>` with the path to your cmake installation:

   .. code:: bash

      $ export PATH=<custom-path>/cmake-3.25.2-macos-universal/CMake.app/Contents/bin:$PATH

c. Save and apply the changes:

   .. code:: bash

      $ source ~/.zshrc  # or source ~/.bashrc

Configure the Project Structure
===============================

1. Go to **File > Project Structure** and apply the following configurations:

a. In the **Project** tab:

   - The *SDK* uses a valid Java version (e.g., OpenJDK 21, GraalVM JDK 21, etc.).
   - The *Language level* is set to match the Java version (e.g., Java 21).

b. In the **Modules** tab:

   - Ensure that the *Language level* of every module matches the project level (e.g., Java 21).

Configuring the TornadoVM Utilities
===================================

1. **Configure the TornadoVM Maven Build**

a. Navigate to the Maven configuration:
   Go to **Run > Edit Configurations > Maven > _internal_TornadoVM_Maven-cleanAndinstall_**

b. Set up the build profiles:
   In the **Profiles** field, list the profiles you want to build, separated by spaces.

   **Examples**:

   - To build with the *graal-jdk-21* and *opencl-backend* profiles, type:

     .. code:: text

        graal-jdk-21 opencl-backend

   - To build with all backends, type:

     .. code:: text

        graal-jdk-21 opencl-backend ptx-backend spirv-backend

c. Check available profiles:
   You can find the available profiles in the right-hand vertical bar in IntelliJ under **Maven > Profiles**.

   **Important:** Even though profiles are listed in the Maven pane, you must explicitly configure them in the **_internal_TornadoVM_Maven-cleanAndinstall_** utility. The enablement/disablement of profiles in the Maven pane does not always reflect in this utility.

2. **Configure the TornadoVM Python Build**

a. Navigate to the Python configuration:
   Go to **Run > Edit Configurations > Python > TornadoVM-Full-Build**

b. Configure the Python interpreter:
   In the **Use specified interpreter** field, select a valid Python interpreter installed on your system.

c. Update environment variables for selected backends:
   In the **Environmental variables** section, locate the `selected_backends` field. Update the list of backends you want to use, separated by commas.

   **Examples**:

   - To use all backends, set the value to:

     .. code:: text

        opencl-backend,ptx-backend,spirv-backend

d. Apply to save your settings and run the build by clicking **Run TornadoVM-Full-Build**.

.. _ide_tornadovm_run:

Configure Applications to Debug/Run
===================================

1. **Obtain the TornadoVM Java flags**

To run and debug Java applications with TornadoVM on IntelliJ, you need to obtain the TornadoVM `JAVA_FLAGS`. Open a terminal and run:

   .. code:: bash

      $ source setvars.sh
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

2. **Configure new Applications**

a. Add new configurations:
   Go to **Run > Edit Configurations > Application > Add new run configuration...**

   Add your own parameters, for example:

   - **Name:** MatrixMultiplication2D
   - **VM Options:** Add the flags you copied earlier
   - **Main class:** e.g., `uk.ac.manchester.tornado.examples.compute.MatrixMultiplication2D`
   - **Program arguments:** e.g., `128`

b. Apply and run the application.

.. _ide_checkstyle:

Configure the IDEA CheckStyle
=============================

1. Go to **File > Settings > Tools > CheckStyle**.

2. Under **Configuration File**, click the *plus* sign to add a new configuration.

3. Set the description to "TornadoVM Checkstyle".

4. **Use a local Checkstyle file** and point to:
   `<path-to-TornadoVM-directory>/tornado-assembly/src/etc/checkstyle.xml`.

5. Click **Next**, then **Finish**.

6. Enable the new CheckStyle configuration in the list of active configurations.