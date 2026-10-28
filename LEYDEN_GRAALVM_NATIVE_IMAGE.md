# Fast start-up: Project Leyden, AppCDS and GraalVM native image

TornadoVM has no JVMCI or Graal dependency. Its kernel compiler, TC
([`tornado-compiler/`](tornado-compiler/README.md)), is plain Java. So a TornadoVM application can
use every start-up feature of the JDK:

- a Project Leyden **AOT cache**;
- **AppCDS**, class-data sharing for application classes;
- a **GraalVM native image** of the whole application.

This guide gives the exact commands for each, which JDK each needs, and what each gains.

Kernels are still compiled when the application runs, in every mode. TC reads the kernel's class
files and specialises the kernel on the actual arguments, as it does on the JVM. What changes is
the cost around the compile: starting the JVM, loading and linking classes, and warming up TC and
the drivers.

## What you gain

Measurements:
- **Workload:** saxpy on 2^20 floats on an RTX 4090. The process compiles one kernel, runs it once
  and exits.
- **Time:** wall time of the whole process, median of 10 cold runs (Linux, NVIDIA driver 610.57,
  CUDA 13.3), measured on `develop` on 2026-10-23.
- **Default:** two backends installed (OpenCL and CUDA); the run uses CUDA.
- **Set-up:** the `java` rows use the argfile prepared in Step 0.

| Mode | JDK 25 | JDK 26 | JDK 27-ea |
|---|---:|---:|---:|
| `java @argfile` | 616 ms | 619 ms | 638 ms |
| + `-Dtornado.enabled.backends=cuda` | 465 ms | 467 ms | 483 ms |
| + AppCDS archive | 519 ms | 527 ms | 532 ms |
| + AppCDS + `enabled.backends=cuda` | 370 ms | 378 ms | 381 ms |
| + Leyden AOT cache | 455 ms | 452 ms | 449 ms |
| + AOT cache + `enabled.backends=cuda` | **310 ms** | **309 ms** | **301 ms** |
| `tornado` launcher (Python), for comparison | 695 ms | | |
| `tornado --aotCache`¹ | 637 ms | | |

¹ The launcher always puts the whole SDK module directory on the module path, so its cache loses
the module graph (see Step 0). The argfile row with the same cache is 455 ms.

GraalVM native image (GraalVM CE for JDK 25), the whole application as one 32 MB executable:

| Native image | Time |
|---|---:|
| `./saxpy -Dtornado.backend=1` (CUDA, both backends initialised) | 471 ms |
| `./saxpy -Dtornado.enabled.backends=cuda` | **324 ms** |
| `./saxpy` (OpenCL, the default backend) | 334 ms |

TC's own compile time for the kernel:

| Mode | TC compile time |
|---|---:|
| JVM | about 30 ms |
| AOT cache | about 11 ms |
| Native image | about 0.9 ms |

The rest of each run is the JVM, the drivers and the GPU. Creating a CUDA context alone takes about
50 ms, and the driver takes 35–60 ms at exit to tear contexts down.

**Which to use:**

| Your setup | Use |
|---|---|
| Any JDK, a machine with more than one backend installed | `-Dtornado.enabled.backends=<backend>`: no build step, saves about 150 ms |
| Any JDK | the Leyden AOT cache: one training run, saves another 150 ms (AppCDS also works) |
| Smallest start-up and no JDK at run time | a GraalVM native image |
| CLIs, serverless functions, tests started many times | combine `enabled.backends` with any of the above |
| Long-running services | these mostly matter for start-up; they change little once warm |

## Which SDK and JDK

| SDK (`make` target) | Runs on | `--enable-preview` | Leyden AOT cache | AppCDS | Native image |
|---|---|---|---|---|---|
| `jdk25plus` (`make BACKEND=...`) | JDK 25–27 | no | yes | yes | GraalVM for JDK 25+ |

Build the SDK once:

```bash
# JAVA_HOME must point at JDK 25 or newer while building
export JAVA_HOME=/path/to/jdk-25
make BACKEND=opencl,cuda                  # or BACKEND=opencl, cuda, metal, ...
source setvars.sh                         # sets TORNADOVM_HOME, JAVA_HOME and PATH
```

## Step 0: one argfile instead of the launcher

Every mode below runs `java` directly. The `tornado` launcher is a Python script; it costs about
90 ms per start (Python itself plus a `java -version` probe). Its flags fit in one argfile:

```bash
tornado --generate-argfile                # writes $TORNADOVM_HOME/tornado-argfile
java @$TORNADOVM_HOME/tornado-argfile -cp app.jar com.example.Main
```

The argfile has 11 flag lines and is the same on every JDK the SDK supports. For a
`jdk25plus` SDK with OpenCL and CUDA it reads:

```
-server
-Djava.library.path=$TORNADOVM_HOME/lib
--module-path .:$TORNADOVM_HOME/share/java/tornado
-Dtornado.load.api.implementation=uk.ac.manchester.tornado.runtime.tasks.TornadoTaskGraph
-Dtornado.load.runtime.implementation=uk.ac.manchester.tornado.runtime.TornadoCoreRuntime
-Dtornado.load.tornado.implementation=uk.ac.manchester.tornado.runtime.common.Tornado
--add-opens java.base/java.lang=tornado.runtime
--enable-native-access=tornado.runtime
--add-opens java.base/java.lang=tornado.drivers.opencl
--add-opens java.base/java.lang=tornado.drivers.cuda
--add-modules jdk.unsupported,java.management,java.logging,jdk.jfr,java.naming,jdk.management,tornado.runtime,tornado.drivers.common,tornado.drivers.opencl,tornado.drivers.cuda,tornado.cublas,...
```

No `-XX:+EnableJVMCI`, `--patch-module` or
`--upgrade-module-path` is needed on any JDK.

**Give the module path only the runtime's modules** when you use an AOT cache or AppCDS. Two
things on the default module path get in the way:

- **`.`**: both caches require the class and module paths to match the training run, and an AOT
  cache refuses non-empty directories on them. `.` also makes the JVM treat any jar in the current
  directory as a module. If you run from a directory holding your application jar, that fails with
  `FindException: Unable to derive module descriptor`.
- **The SDK's test and example modules.** `share/java/tornado` also holds `tornado-unittests` and
  `tornado-examples`. `tornado.unittests` provides a `TornadoLibraryProvider`, so service binding
  resolves it in every application, and with it the incubator module `jdk.incubator.vector` (the
  `WARNING: Using incubator modules` line). With an incubator module in the boot layer, the JDK does
  not store the module graph in an AOT cache: saxpy then starts in 558 ms from its cache instead
  of 455 ms (JDK 25, CUDA).

Point the argfile at a directory that holds every SDK module except those two:

```bash
mkdir -p ~/.tornado-app-modules
for j in $TORNADOVM_HOME/share/java/tornado/*.jar; do
  case $(basename $j) in tornado-unittests-*|tornado-examples-*) ;; *) ln -sf $j ~/.tornado-app-modules/ ;; esac
done
sed -i "s#^--module-path .*#--module-path $HOME/.tornado-app-modules#" $TORNADOVM_HOME/tornado-argfile
```

Redo this after rebuilding or updating the SDK: the jar names carry the version.

## Step 1: load only the backend you use

TornadoVM initialises every installed backend at start-up. On a machine with an NVIDIA GPU, an
OpenCL backend that a CUDA application never uses costs about 170 ms: loading the OpenCL library,
discovering devices and creating a context. Name the backends to load:

```bash
java @$TORNADOVM_HOME/tornado-argfile -Dtornado.enabled.backends=cuda -cp app.jar com.example.Main
tornado --jvm="-Dtornado.enabled.backends=cuda" --classpath app.jar com.example.Main
./app -Dtornado.enabled.backends=cuda                     # a native image reads it at run time
```

- **Values:** `opencl`, `cuda` or `metal`, or a comma-separated list. An empty value, the default,
  loads all installed backends.
- **Backend indices count only the loaded backends.** With `enabled.backends=cuda`, CUDA is backend
  0, so `-Dtornado.backend` and `-Dtornado.device` change with it.
- **A name that matches no installed backend fails at start-up** with `TornadoBackendNotFound`, and
  the error lists the installed backends.
- **Don't use `--limit-modules` instead.** It also drops the OpenCL module, but it switches off
  class-data sharing and the AOT cache.

## Step 2a: Project Leyden AOT cache (JDK 25+)

An AOT cache (JEP 483, JEP 514, JEP 515) stores what a training run did: classes already loaded and
linked, resolved method handles and lambdas, and method profiles. Later runs start from it. For
TornadoVM it covers the runtime, the drivers' FFM bindings and TC itself.

**With the launcher:**

```bash
tornado --aotCache app.aot --classpath app.jar com.example.Main    # first run: trains and writes app.aot
tornado --aotCache app.aot --classpath app.jar com.example.Main    # later runs start from it
```

The launcher passes `-XX:AOTCacheOutput=app.aot` when the file does not exist and
`-XX:AOTCache=app.aot` when it does. It also leaves `.` off the module path, but it keeps the test
modules on it (Step 0), so for the full gain use the argfile.

**With the argfile:**

```bash
# uses the argfile prepared in Step 0

# training run (JDK 25+: one step); give it a workload that compiles and launches the kernels
java @$TORNADOVM_HOME/tornado-argfile -Dtornado.enabled.backends=cuda \
     -XX:AOTCacheOutput=app.aot -cp app.jar com.example.Main

# production runs: the same flags, the same class path and the same JDK
java @$TORNADOVM_HOME/tornado-argfile -Dtornado.enabled.backends=cuda \
     -XX:AOTCache=app.aot -cp app.jar com.example.Main
```

**Rules:**
- **Same JDK build** for training and use.
- **Same module and class paths,** with the application on the class path as **jars**. Directories
  of classes are refused.
- **Retrain after changing the application or the SDK.** A cache that doesn't match is ignored, so
  start-up falls back to normal, unless you add `-XX:AOTMode=on` to make a mismatch an error.
- **Check that the cache is used:** add `-Xlog:aot`. A cache in use logs `Opened AOT cache app.aot`.
  It should also log `full module graph: enabled`; `disabled` means the test modules are on the
  module path (Step 0).
- **Train once per backend selection.** A cache trained with `enabled.backends=cuda` holds CUDA's
  classes, not OpenCL's.
- **On JDK 24 the cache needs two steps** (JEP 483: `-XX:AOTMode=record -XX:AOTConfiguration=app.conf`,
  then `-XX:AOTMode=create -XX:AOTCache=app.aot`). That combination is untested with TornadoVM;
  use JDK 25 or newer.

## Step 2b: AppCDS

Class-data sharing for the application's classes. It stores classes already parsed and verified,
but not lambdas, linked call sites or profiles. It works on every JDK the SDK supports.

```bash
# uses the argfile prepared in Step 0

# dump the classes a run loads, at exit
java @$TORNADOVM_HOME/tornado-argfile -Dtornado.enabled.backends=cuda \
     -XX:ArchiveClassesAtExit=app.jsa -cp app.jar com.example.Main

# later runs
java @$TORNADOVM_HOME/tornado-argfile -Dtornado.enabled.backends=cuda \
     -XX:SharedArchiveFile=app.jsa -cp app.jar com.example.Main
```

**Other ways to make the archive:**
- `-XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=app.jsa` writes it on the first run and keeps
  it up to date (JDK 19+).
- A warning such as `Skipping jdk/proxy1/$Proxy15: Unsupported location` while dumping is harmless.

## Step 3: GraalVM native image of the application (GraalVM for JDK 25+)

`tornado-native-image` builds the whole application, including the TornadoVM runtime, the drivers
(through FFM) and TC, into one executable that needs no JVM.

```bash
source setvars.sh                                         # a jdk25plus SDK
export GRAALVM_HOME=/path/to/graalvm-jdk-25               # GraalVM CE or Oracle GraalVM, JDK 25+

tornado-native-image -cp app.jar -o app com.example.Main [args for the tracing runs]

./app                                                     # the default backend
./app -Dtornado.backend=1                                 # another backend
./app -Dtornado.enabled.backends=cuda                     # load only CUDA
./app -Dtornado.tc.verbose=true                           # TornadoVM and TC options work as on the JVM
```

The build of saxpy takes about 16 s (2.5 GB of memory at peak), tracing included. It runs two steps:

1. **Tracing.** The application runs on the GraalVM JVM once per installed backend
   (`-Dtornado.backend=N`), with the native-image agent recording the reflection, resources and FFM
   downcalls it uses into `app.native-config/`. Give it arguments that compile and launch every
   kernel the application uses. `--no-trace` reuses an existing `app.native-config/`.
2. **Building.** It runs `native-image` with the metadata and FFM support.

The equivalent manual commands, for a build system:

```bash
J=$TORNADOVM_HOME/share/java/tornado
MODS=tornado.runtime,tornado.drivers.common,tornado.drivers.opencl,tornado.drivers.cuda
JVM="--enable-native-access=ALL-UNNAMED,$MODS -p $J --add-modules jdk.unsupported,java.management,jdk.management,$MODS \
     --add-opens java.base/java.lang=tornado.runtime"

# 1. tracing, once per backend (0, 1, ...)
$GRAALVM_HOME/bin/java -agentlib:native-image-agent=config-merge-dir=app.native-config $JVM \
    -Djava.library.path=$TORNADOVM_HOME/lib -Dtornado.backend=0 -cp app.jar com.example.Main
$GRAALVM_HOME/bin/java -agentlib:native-image-agent=config-merge-dir=app.native-config $JVM \
    -Djava.library.path=$TORNADOVM_HOME/lib -Dtornado.backend=1 -cp app.jar com.example.Main

# 2. build
$GRAALVM_HOME/bin/native-image --no-fallback -H:+UnlockExperimentalVMOptions \
    -H:+ForeignAPISupport -H:+SharedArenaSupport -H:ConfigurationFileDirectories=app.native-config \
    $JVM -cp app.jar -o app com.example.Main
```

**Notes:**
- **Extra modules.** If the application uses one of the library modules (`tornado.cublas`,
  `tornado.cufft`, ...), add it with `TORNADOVM_EXTRA_MODULES=tornado.cublas tornado-native-image ...`.
- **The device libraries** (`libcuda`, `libnvrtc`, the OpenCL ICD, Metal) are loaded at run time,
  exactly as on the JVM. The executable needs the same drivers installed, not the SDK.
- **Kernels are compiled at run time.** TC reads the kernels' class files from the image, and the
  image holds only what the tracing runs used. A kernel the tracing runs never compiled fails at run
  time (`ClassNotFoundException` for its class), and so does a code path they never ran
  (`MissingForeignRegistrationError` for an FFM downcall). Trace every kernel and code path.
- **Not yet covered:** applications that call the JNI-based libraries (cuDNN, cuDF, CUTLASS), and
  Windows.

## The stand-alone compiler: `tc` and `build-tc-native`

`tc emit` compiles one kernel to CUDA C++, OpenCL C or MSL with no GPU and no TornadoVM runtime,
for example in CI or to inspect code:

```bash
tc emit --target opencl --cp app.jar com.example.Kernels saxpy
tc emit --target cuda   --cp app.jar --stats com.example.Kernels saxpy
```

`build-tc-native` builds it as a native image, `$TORNADOVM_HOME/bin/tc-native`, which `tc` then
uses. A cold `tc emit` drops from about 0.15 s to 0.01 s, with byte-identical output:

```bash
GRAALVM_HOME=/path/to/graalvm-jdk-25 build-tc-native
```

`tc` reads class files only, so it cannot inline JDK methods it has no class files for (for example
`Integer.reverse`). The runtime compiler can: it reads them from the running JDK.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `FindException: Unable to derive module descriptor for ./app.jar` | `.` is on the module path and the current directory holds a jar: take `.` off the argfile's module path, or run from another directory |
| `-Xlog:aot` shows `full module graph: disabled`, or `WARNING: Using incubator modules: jdk.incubator.vector` | the SDK's test modules are on the module path: give it only the runtime's modules (Step 0) |
| AOT cache silently not used | different JDK, flags or class path from the training run, or classes in a directory: check with `-Xlog:aot`, force with `-XX:AOTMode=on` |
| `TornadoBackendNotFound: tornado.enabled.backends=... selects none of the installed backends` | the name is not installed in this SDK; the message lists the installed ones |
| A native image fails with a missing reflection or downcall entry | a code path the tracing runs did not take: run `tornado-native-image` again with arguments that reach it |
| `this SDK was built with --enable-preview` from `tornado-native-image` | build a `jdk25plus` SDK |
