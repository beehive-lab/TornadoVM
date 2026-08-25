# Running TornadoVM on JDK 27+ (JVMCI-free)

Starting with **JDK 27** the OpenJDK project removed the JVMCI feature entirely
([openjdk/jdk#30834](https://github.com/openjdk/jdk/pull/30834)): there is no
`jdk.internal.vm.ci` module, no built-in `jdk.graal.compiler` module, and the
`-XX:+EnableJVMCI` / `-XX:+UseJVMCICompiler` flags are unrecognized.

This branch makes TornadoVM build and run GPU kernels on such a JVMCI-free JDK by
supplying everything it used to borrow from the JDK as **TornadoVM-owned modules**
and by sourcing all type/metadata through **reflection + `Unsafe`** instead of the
HotSpot JVMCI runtime.

Status: `VectorAddInt` and other `*Array` kernels compile to OpenCL and execute on
the GPU (validated on an NVIDIA RTX 4090) on `27.ea.24-open`, with correct results.

---

## How to run

### 1. Get a JVMCI-free JDK
```bash
sdk install java 27.ea.24-open
export JAVA_HOME="$HOME/.sdkman/candidates/java/27.ea.24-open"
```

### 2. Vendor the JVMCI interface module (one-off)
JDK 27 no longer ships `jdk.internal.vm.ci`, so we repackage it from a JDK 21 image
as a TornadoVM-owned application module of the **same name** (a drop-in; no
module-info edits needed anywhere):
```bash
# needs a JDK 21 available (default: ~/.sdkman/candidates/java/21.0.2-open,
# override with JVMCI_SOURCE_JDK=/path/to/jdk21)
JAVA_HOME="$HOME/.sdkman/candidates/java/27.ea.24-open" python3 bin/build_jvmci_module.py
```

### 3. Build
```bash
make jdk27 BACKEND=opencl        # or: bin/compile --jdk jdk27 --backend opencl
```

### 4. Run a kernel
```bash
export TORNADOVM_HOME="$PWD/bin/sdk"
tornado --enableProfiler console \
  -m tornado.examples/uk.ac.manchester.tornado.examples.VectorAddInt --params=8192
# -> "Result is correct" + a non-zero TOTAL_KERNEL_TIME
```

The launcher auto-detects JDK ≥ 27 and, only then, drops `-XX:+EnableJVMCI` /
`--enable-preview`, puts the vendored `jdk.internal.vm.ci` on the module path, adds
the `--add-exports java.base/jdk.internal.*` the platform module used to receive,
and enables the reflection provider path.

> Packaging note: a clean `bin/compile jdk27` currently needs the vendored jvmci jar
> staged into `graalJars/` (via `bin/build_jvmci_module.py`) before the assembly, and
> ASM 9.7 in `share/java/tornado`. Wiring these into the flow is a pending follow-up.

---

## Key changes

**Vendored platform pieces (no code depends on the JDK's JVMCI/Graal):**
- `bin/build_jvmci_module.py` — extracts JDK 21's `jdk.vm.ci.*` classes and repackages
  them as an application module named `jdk.internal.vm.ci`, installed to the local
  Maven repo. Freezing the SPI at the JDK-21 shape (what Graal 23.1.0 expects) means
  no source changes are needed despite the JVMCI SPI drift in JDK 26.
- Graal itself is already vendored as the relocated `tornado.graal` module (prior work).
- `pom.xml` — new `jdk27` Maven profile; `bin/compile` accepts `--jdk jdk27`.

**Reflection-only runtime (no HotSpot JVMCI):**
- `TornadoCoreRuntime` — on the JVMCI-absent path, skips `JVMCI.getRuntime()` and uses
  the reflection metaAccess.
- `TornadoMetaAccessProvider` / `TornadoConstantReflectionProvider` — null-backing-safe;
  `ReflectionUniverse` canonicalizes methods/fields; `ReflectionResolvedJavaMethod`
  synthesizes a `LocalVariableTable` (JDK-neutral kernel-argument naming).
- `OCLHotSpotBackendFactory` — builds providers from reflection when there is no host
  JVMCI backend.
- `TornadoFunctions` + `TaskUtils` — kernel entry (lambda/method-ref) resolved via
  `SerializedLambda` instead of hidden-class bytecode.

**Panama accessor intrinsics (the enabling fix for kernel codegen):**
- `OCLGraphBuilderPlugins` — intrinsifies the native-array accessors
  (`IntArray/FloatArray/DoubleArray/LongArray/ShortArray/ByteArray/CharArray` `get`/`set`)
  **directly at the call site**, emitting the memory read/write up front. This bypasses
  `MemorySegment.getAtIndex`, which became an **abstract** (bodiless) interface method on
  JDK 22+ and which the sketcher could otherwise not parse.
- `TornadoTaskGraph.reduceAnalysis()` — guarded on the reflection path (the `@Reduce`
  detector builds a host-side Graal graph via HotSpot JVMCI, unavailable on JDK 27).

**Runtime launcher (`tornado-assembly/src/bin/tornado.py`):** JDK-conditional JVM
options, vendored jvmci on the module path, `java.base` internal add-exports, and
`-Djdk.internal.vm.ci.enabled=true` (satisfies the vendored `Services` gate without
patching bytecode). JDK ≤ 26 behavior is unchanged.

---

## Known limitations / follow-ups
- Only the **OpenCL** backend is wired for JDK 27 so far.
- `@Reduce` kernels are not yet supported on the JVMCI-free path.
- Assembly packaging of the vendored jvmci jar + ASM 9.7 is not yet automatic.
