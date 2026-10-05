# How TornadoVM gets class metadata without JVMCI

TornadoVM compiles Java bytecode with a frozen copy of the Graal 23.1.0 compiler. Graal is written
against the JVM Compiler Interface (JVMCI) types: `ResolvedJavaMethod`, `ResolvedJavaType`,
`JavaKind`, `JavaConstant`, `Register`, `TargetDescription` and so on. TornadoVM keeps those types but
not JVMCI itself: it needs nothing from the JDK beyond plain Java, so one SDK runs on every JDK from 22
up, including JDK 27 (which removed JVMCI) and GraalVM.

## The pieces

| Module | What it is | License |
|---|---|---|
| `tornado.meta` (`tornado-meta/`) | The JVMCI API types, taken from OpenJDK 21.0.2 and renamed `jdk.vm.ci.*` -> `tornado.meta.*`. Plain interfaces and value classes; no native code, no HotSpot. See `tornado-meta/README.md`. | GPLv2 |
| `tornado.graal` (`graalJars/tornado-graal-<ver>.jar`) | Graal 23.1.0 relocated off the GraalVM namespaces (`org.graalvm.compiler.*` -> `tornado.graal.compiler.*`), with `jdk.vm.ci.*` pointed at `tornado.meta.*` and the GraalVM SDK jars (word, collections, truffle-compiler) folded in. Built by `bin/build_graal_module.py` before the Maven reactor runs. | GPLv2 + CPE, UPL |
| `tornado.runtime` reflection providers (`uk.ac.manchester.tornado.runtime.jvmci.reflection`) | Implement the `tornado.meta` interfaces from `java.lang.reflect`, the class-file bytes (`ClassfileParser`) and `Unsafe` (`TornadoVMConfigAccess`). `TornadoMetaAccessProvider` and `TornadoConstantReflectionProvider` hand them to Graal. | GPLv2 + CPE |

Neither module shares a module or package name with anything a JDK ships, so the launcher needs no
`-XX:+EnableJVMCI`, `--patch-module`, `--upgrade-module-path` or JVMCI `--add-exports`. Everything is on
the plain `--module-path`.

## What Graal still expects from "JVMCI"

Graal reaches into the HotSpot-only JVMCI packages (`jdk.vm.ci.hotspot`, `.runtime`, `.services`) on its
HotSpot-JIT paths. TornadoVM never executes those paths, so the relocated references simply have no
target. Two things are on the paths TornadoVM does execute:

- `tornado.meta.services.Services`: a TornadoVM-written class with the three members Graal reads
  (`IS_IN_NATIVE_IMAGE`, `IS_BUILDING_NATIVE_IMAGE`, `getSavedProperties()`).
- `tornado.meta` is an `open` module. Graal checks that its JVMCI module is open to the modules it
  serves and otherwise calls into the JVMCI runtime.

If a future change makes Graal load another HotSpot-only class, it fails with
`NoClassDefFoundError: tornado/meta/hotspot/...` (or `.../runtime/...`). Find the caller in the relocated
jar (`javap -c -p`) and either avoid the path in TornadoVM or add the minimal member to
`tornado.meta.services`.

## Build flow

1. `bin/pull_graal_jars.py` downloads the Graal 23.1.0 compiler jar.
2. `bin/build_graal_module.py` compiles `tornado-meta` from source, shades the compiler (plus word,
   collections and truffle-compiler) through `bin/graal-relocate/pom.xml`, regenerates the module
   descriptor with `jdeps`, and installs `tornado.graal:tornado-graal:23.1.0` into the local Maven
   repository.
3. The Maven reactor builds `tornado-meta`, then everything that `requires transitive tornado.meta`.

## Writing backend code

Backends see only `tornado.meta` types. On the reflection path:

- Metadata objects are `Reflection*` implementations, never `HotSpot*`. Do not cast to HotSpot types
  or guard with `instanceof HotSpotResolvedJavaType`; call the `tornado.meta` interface methods.
- Use `uk.ac.manchester.tornado.drivers.common.code.TornadoCallingConventionType` for calling
  conventions.
- `InvocationPlugin`s that a JVMCI-backed graph builder would have applied may miss at sketch time.
  Calls such as `KernelContext.*`, the backend's intrinsics class and the native-array accessors
  (`IntArray.get`/`set`, ...) must be intrinsified at the call site or lowered by a high-tier phase.
  The OpenCL backend is the reference: `OCLGraphBuilderPlugins.registerNativeArrayAccessPlugins` and
  `TornadoOpenCLIntrinsicsReplacements`.
- Object layout comes from `TornadoVMConfigAccess` (Unsafe-probed header size, compact object headers)
  and Panama arrays use `TornadoOptions.PANAMA_OBJECT_HEADER_SIZE`.
- Class files must stay at version 66 (`--release 22`) or older: the frozen Graal class-file reader
  rejects newer ones.
