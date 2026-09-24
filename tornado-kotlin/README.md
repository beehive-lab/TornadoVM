# TornadoVM for Kotlin

Kotlin support for TornadoVM. Kotlin compiles to JVM bytecode, which TornadoVM already compiles for
GPUs, so Kotlin kernels run on the same backends (OpenCL, CUDA, Metal) as Java kernels. This
directory adds a Kotlin API on top of the Java one, plus Kotlin unit tests.

Kotlin support is opt-in. The default build does not use the Kotlin toolchain.

## Building

```bash
make KOTLIN=1 BACKEND=opencl          # or: make jdk22plus BACKEND=metal,opencl KOTLIN=1
```

`KOTLIN=1` adds the `kotlin` Maven profile (`bin/compile --kotlin`). The Kotlin modules are then
built and added to the SDK's `share/java/tornado`, together with `kotlin-stdlib`.

## Modules

| Module | JPMS module | Contents |
|---|---|---|
| `tornado-kotlin-api` | `tornado.kotlin.api` | Kotlin API over `tornado-api` |
| `tornado-kotlin-unittests` | `tornado.kotlin.unittests` | Kotlin unit tests, run through `tornado-test` |

## Using the API

```kotlin
import uk.ac.manchester.tornado.api.KernelContext
import uk.ac.manchester.tornado.api.enums.DataTransferMode
import uk.ac.manchester.tornado.kotlin.api.*

fun vectorAdd(context: KernelContext, a: TFloatArray, b: TFloatArray, c: TFloatArray) {
    val i = context.globalIdX
    c[i] = a[i] + b[i]
}

fun main() {
    val n = 1 shl 20
    val a = TFloatArray(n) { it.toFloat() }
    val b = TFloatArray(n) { 2f * it }
    val c = TFloatArray(n)
    val context = KernelContext()

    val graph = taskGraph("s0") {
        transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)
        task("t0", ::vectorAdd, context, a, b, c)
        transferToHost(DataTransferMode.EVERY_EXECUTION, c)
    }

    executionPlan(graph.snapshot()) {
        withDevice(0, 0)
        withGridScheduler(gridScheduler("s0.t0" to workerGrid(n, local = 256)))
    }.use { it.execute() }
}
```

| API | What it does |
|---|---|
| `taskGraph(name) { ... }` | Builds a `TaskGraph`; the block has the graph as receiver, so the whole Java API is available |
| `executionPlan(graphs) { ... }` | Creates and configures a `TornadoExecutionPlan` (via `TornadoExecutionPlan.of`) |
| `TaskGraph.toExecutionPlan()` | Snapshot plus plan in one call |
| `withDevice(backend, device)` | Selects a device by index, as listed by `tornado --devices` |
| `gridScheduler("g.t" to grid)`, `workerGrid(...)` | Grid scheduler and 1D/2D/3D worker grids with optional local sizes |
| `context.globalIdX`, `localIdX`, `groupIdX`, `globalSizeX`, `localSizeX` (and `Y`, `Z`) | `KernelContext` thread indices as `Int` |
| `TFloatArray`, `TIntArray`, ... | Aliases for TornadoVM's off-heap arrays, which share their names with Kotlin's arrays |
| `TFloatArray(n) { init }`, `tFloatArrayOf(...)`, `toTornado()`, `toFloatArray()` | Creation and conversion helpers |

TornadoVM arrays support `a[i]`, `a[i] = v` and `a.size` directly, because Kotlin maps the Java
`get`/`set`/`getSize` methods to them.

## Writing kernels

- **Kernels must be static methods.** Write them as top-level functions, and pass them as direct
  references (`::vectorAdd`) or lambdas at the `task` call site. A function reference stored in a
  variable first is wrapped by Kotlin and cannot be compiled.
- **Use the `Int` accessors of `KernelContext`** (`context.globalIdX`, not `context.globalIdx`).
  The Java fields are boxed `Integer`s, and a boxed index used several times does not compile.
- **Compile kernels without Kotlin's null-check assertions**:
  `-Xno-param-assertions -Xno-call-assertions -Xno-receiver-assertions`. The modules here already
  do this.
- **Loop-parallel kernels (`@Parallel`) are not supported from Kotlin**: `kotlinc` does not keep
  the annotation, so the loop runs sequentially. Use `KernelContext` kernels.

## Testing

```bash
tornado-test --kotlin -V                                                     # all Kotlin tests
tornado-test -V uk.ac.manchester.tornado.kotlin.unittests.api.TestKotlinApi  # one class
```

Kotlin test classes need their module added at run time; `tornado-test` does this for any class
under `uk.ac.manchester.tornado.kotlin.`.
