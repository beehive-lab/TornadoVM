.. _ide-integration:

IDE Integration
===============================

IntelliJ
--------

Download and install the latest IntelliJ IDEA Community Edition: https://www.jetbrains.com/idea/download/

Change the IntelliJ maximum memory to 2 GB or more `(instructions) <https://www.jetbrains.com/help/idea/increasing-memory-heap.html#d1366197e127>`__.

For Intellij to pickup the required Tornado dependencies from the poms, go to **View > Tool Windows > Maven** and select **jdk-8, ptx-backend,
opencl-backend** under profiles.

Required Plugins:
~~~~~~~~~~~~~~~~~~~~~~~~~~

Open IntelliJ and go to **Preferences > Plugins > Browse Repositories**.
Install the following plugins:

1. `Eclipse Code Formatter: <https://plugins.jetbrains.com/plugin/6546-eclipse-code-formatter>`__
   Formats source code according to eclipse standards (.xml). After the
   installation of the plugin, go to: **File > Settings > Other Settings
   > Ecliple Code Formatter**, and select **“Use the Eclipse code
   formatter”**. Finally, load the TornadoVM code formatter
   (**./scripts/templates/eclipse-settings/Tornado.xml**) as the Eclipse
   Java Formatter config file, and click **Apply**.

2. `Save Actions: <https://plugins.jetbrains.com/plugin/7642-save-actions>`__
   Allows post-save actions (e.g. code formating on save).

   -  To enable the auto-formatter with save-actions, go to **Settings
      -> Other Settings -> Save Actions**, and mark the following:

      -  Activate save actions on save
      -  Activate save actions in shortcut
      -  Reformat file

3. `Python Plugin (Optional): <https://plugins.jetbrains.com/plugin/631-python>`__
   Allows Python scripting.

4. `CheckStyle-IDEA Plugin (Optional): <https://plugins.jetbrains.com/plugin/1065-checkstyle-idea>`__
    Checks project for compliance with custom checkstyle rules.

Run and Debug TornadoVM with Intellij
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Normal maven lifecycle goals like *package* and *install* will not
result a succefull build for TornadoVM.

Two different configurations are needed for **Build** and **Debug**.

Build/Run Configuration
~~~~~~~~~~~~~~~~~~~~~~~~~~

1. Go to **File > Project Structure** and ensure that:

-  **In the Project Tab:**

   -  The Project SDK uses the same java version as the project (e.g. Java 17).
   -  The Project language level is using the same java version (e.g. Java 17 with Lambdas, type annotations etc.).

-  **In the Modules Tab:**

   -  The module SDK of every module uses the same java version
      (e.g. 1.8.0_131).

1. In the right vertical bar in Intellij: **Maven Projects > tornadovm
   (root) > Lifecycle > package > right click > create tornadovm
   [package]**

2. Then: **Run > Edit Configurations > Maven > tornadovm Package**. You
   need to manually add and check the following information:

-  In the **Parameters** tab :

   -  In **Command line**, add the following:

      -  ``-Dcmake.root.dir=/home/michalis/opt/cmake-3.10.2-Linux-x86_64/ clean package``

         -  In case that you need to reduce the amount of maven warnings
            add also on the above line the command **–quiet**, which
            constraints maven verbose to only errors.

   -  In **Profiles (separated with space)**, add the following:

      -  ``jdk-8``
      -  at least one backend depending on what you want to build
         ``{opencl-backend, ptx-backend}``

-  In the **Runner** tab: Ensure that the selected **JRE** corresponds
   to ``Use Project JDK`` (e.g.1.8.0_131).

Finally, one the top right corner drop-down menu select the adove
custome ``tornadovm [package]`` configuration. To build either press the
**play button** on the top right corner or **Shift+F10**.

Debug/Run Configuration
~~~~~~~~~~~~~~~~~~~~~~~~~~

In order to Run and Debug Java code running through TornadoVM another
custom configuration is needed. For this configuration the TornadoVM
``JAVA_FLAGS`` and ``CLASSPATHS`` are needed.

**Firstly, you need to obtain the ``JAVA_FLAGS`` used by TornadoVM. Use
the following commands to print the flags:**

.. code:: bash 

   $ make BACKENDS={opencl,ptx,spirv}
   $ tornado --printJavaFlags

Output should be something similar to this:

.. code:: bash

   /PATH_TO_JDK/jdk1.8.0_131/bin/java
   -server -XX:-UseJVMCIClassLoader -XX:-UseCompressedOops -Djava.ext.dirs=/home/michalis/Tornado/tornado/bin/sdk/share/java/tornado -Djava.library.path=/home/michalis/Tornado/tornado/bin/sdk/lib -Dtornado.load.api.implementation=uk.ac.manchester.tornado.runtime.tasks.TornadoTaskGraph -Dtornado.load.runtime.implementation=uk.ac.manchester.tornado.runtime.TornadoCoreRuntime -Dtornado.load.tornado.implementation=uk.ac.manchester.tornado.runtime.common.Tornado

You need to copy from ``-server`` to end.

**Now, introduce a new Run Configuration**

Again, **Run > Edit Configurations > Application > Add new (e.g. plus
sign)**

Then, add your own parameters similar to the following:

-  **Main Class:**
   uk.ac.manchester.tornado.examples.compute.MatrixMultiplication1D
-  **VM Options:** What you copied from ``-server`` and on
-  **Working Directory:** ``/home/tornadovm``
-  **JRE:** Default (Should point to the 1.8.0_131)
-  **Use classpath of module** Select from drop-down menu e.g
   ``tornado-examples``

Finally, you can select the new custom configuration by selecting the
configuration from the right top drop-down menu. Now, you can run it by
pressing the **play button** on the top right corner or **Shift+F10**.

CheckStyle-IDEA Configuration
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
First, add the custom checkstyle file to enable its rules go to  **IntelliJ > Settings > Tools > CheckStyle** then,
under configuration file click plus then add the configuration file which is under `tornado-assembly/src/etc/checkstyle.xml`.

Then, on the side on enabled plugins click on checkstyle and then in `rules` topdown menu click the custom rules file.
