# Loading TornadoVM into IntelliJ

**Tested with** IntelliJ IDEA 2018.2.2 and IntelliJ IDEA 2017.3.5

## Intellij 

Download and install the latest IntelliJ IDEA Community Edition: [https://www.jetbrains.com/idea/download/](https://www.jetbrains.com/idea/download/)

Change the IntelliJ maximum memory to 2 GB or more [(instructions)](https://www.jetbrains.com/help/idea/increasing-memory-heap.html#d1366197e127).



## Required Plugins:

Open IntelliJ and go to **Preferences > Plugins > Browse Repositories**. Install the following plugins:

1. [Eclipse Code Formatter:](https://plugins.jetbrains.com/plugin/6546-eclipse-code-formatter) Formats source code according to eclipse standards (.xml)
2. [Save Actions:](https://plugins.jetbrains.com/plugin/7642-save-actions) Allows post-save actions (e.g. code formating on save)
3. [Python Plugin:](https://plugins.jetbrains.com/plugin/631-python) Allows Python scripting 

## Run and Debug TornadoVM with Intellij 
Normal maven lifecycle goals like *package* and *install* will not result a succefull build for TornadoVM.

Two different configurations are needed for **Build** and **Debug**


### Build/Run Configuration 

Now, in Intellij:
***Maven Projects > tornadovm (root) > Lifecycle > package > right click > create tornadovm [package]***

Then: ***Run > Edit Configurations > Maven > tornadovm Package***

You need to manually add and check the following information:

* **Parameters** : **Command line**: add the following
    * `-Dcmake.root.dir=/home/michalis/opt/cmake-3.10.2-Linux-x86_64/ clean package`
        * In case that you need to reduce the amount of maven warnings add also on the above line the command **--quiet**, which constraints maven verbose to only errors

* **Runner**: Ensure that in **JRE** tab the selected JRE corresponds to `Use Project JDK` (e.g.1.8.0_131)

Finally, one the top right corner drop-down menu select the adove custome `tornadovm [package]` configuration.
To  build either press the **play button** on the top right corner or **Shift+F10**

### Debug/Run Configuration 

In order to Run and Debug Java code running through TornadoVM another custom configuration is needed.
For this configuration the TornadoVM `JAVA_FLAGS` and `CLASSPATHS` are needed.

**Firstly, you need to obtain the `JAVA_FLAGS` used by TornadoVM. Use the following commands to print the flags:**


```
$ make
$ tornado --printFlags
```

Output should be something similar to this:
```
/PATH_TOJ_DK/jdk1.8.0_131/bin/java
-server -XX:-UseJVMCIClassLoader -XX:-UseCompressedOops -Djava.ext.dirs=/home/michalis/Tornado/tornado/bin/sdk/share/java/tornado -Djava.library.path=/home/michalis/Tornado/tornado/bin/sdk/lib -Dtornado.load.api.implementation=uk.ac.manchester.tornado.runtime.tasks.TornadoTaskSchedule -Dtornado.load.runtime.implementation=uk.ac.manchester.tornado.runtime.TornadoCoreRuntime -Dtornado.load.tornado.implementation=uk.ac.manchester.tornado.runtime.common.Tornado -Dtornado.load.device.implementation=uk.ac.manchester.tornado.drivers.opencl.runtime.TornadoDeviceFactory

```
You need to copy from `-server` to end.

**Now, introduce a new Run Configuration**

Again, ***Run > Edit Configurations > Application > Add new (e.g. plus sign)***

Then, add your own paraters similar to the following:

* **Main Class:** uk.ac.manchester.tornado.examples.HelloWord
* **VM Options:** What you copied from `-server` and on
* **Working Directory:** `/home/tornadovm`
* **JRE:** Default (Should point to the 1.8.0_131)
* **Use classpath of module** Select from drop-down menu e.g `tornado-examples`

Finally, you can select the  new custom configuration by selecting the configuration from the right top drop-down menu. Now, you can run it by pressing the **play button** on the top right corner or **Shift+F10**
