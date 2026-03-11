# TornadoVM `tornado-api` Deep Dive Reference

> **Module:** `tornado.api` | **Package root:** `uk.ac.manchester.tornado.api`
> **Source analysed:** `tornado-api/src/main/java/` — every public `.java` file (150+ files)

---

## Table of Contents

1. [Quick Start Patterns](#1-quick-start-patterns)
2. [Modern JVM Feature Integration](#2-modern-jvm-feature-integration)
3. [Type System & Data Types](#3-type-system--data-types)
4. [TaskGraph API](#4-taskgraph-api)
5. [TornadoExecutionPlan](#5-tornadoexecutionplan)
6. [Annotations](#6-annotations)
7. [KernelContext API](#7-kernelcontext-api)
8. [Grid Scheduling](#8-grid-scheduling)
9. [Device Selection & Backend API](#9-device-selection--backend-api)
10. [Memory Management Patterns](#10-memory-management-patterns)
11. [Execution Policies & Profiling](#11-execution-policies--profiling)
12. [Module System](#12-module-system)
13. [Prebuilt Tasks & Interop](#13-prebuilt-tasks--interop)
14. [Math Utilities](#14-math-utilities)
15. [Exception Hierarchy](#15-exception-hierarchy)
16. [Type Compatibility Matrix](#16-type-compatibility-matrix)

---

## 1. Quick Start Patterns

### Pattern A — `@Parallel` Loop with try-with-resources

```java
import uk.ac.manchester.tornado.api.*;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

public class VectorAdd {
    // Task method — must be static, loop var annotated with @Parallel
    private static void vectorAdd(FloatArray a, FloatArray b, FloatArray c) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    public static void main(String[] args) {
        int size = 1024;
        FloatArray a = new FloatArray(size);
        FloatArray b = new FloatArray(size);
        FloatArray c = new FloatArray(size);
        a.init(1.0f);
        b.init(2.0f);

        TaskGraph tg = new TaskGraph("graph")
            .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)
            .task("add", VectorAdd::vectorAdd, a, b, c)
            .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph itg = tg.snapshot();

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.execute(); // c now contains 3.0f in every element
        }
    }
}
```

### Pattern B — KernelContext with GridScheduler

```java
import uk.ac.manchester.tornado.api.*;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

public class KernelContextExample {
    private static void matVecMul(KernelContext ctx, FloatArray matrix,
                                   FloatArray vector, FloatArray result, int N) {
        int row = ctx.globalIdx;
        float sum = 0.0f;
        for (int col = 0; col < N; col++) {
            sum += matrix.get(row * N + col) * vector.get(col);
        }
        result.set(row, sum);
    }

    public static void main(String[] args) {
        int N = 256;
        FloatArray matrix = new FloatArray(N * N);
        FloatArray vector = new FloatArray(N);
        FloatArray result = new FloatArray(N);
        KernelContext ctx = new KernelContext();

        TaskGraph tg = new TaskGraph("s0")
            .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix, vector)
            .task("mv", KernelContextExample::matVecMul, ctx, matrix, vector, result, N)
            .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph itg = tg.snapshot();

        WorkerGrid1D grid = new WorkerGrid1D(N);
        grid.setLocalWork(64, 1, 1);
        GridScheduler scheduler = new GridScheduler("s0.mv", grid);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.withGridScheduler(scheduler).execute();
        }
    }
}
```

### Pattern C — Native Arrays with Panama MemorySegment

```java
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import java.lang.foreign.MemorySegment;

// Allocating off-heap (no GC pressure)
FloatArray data = new FloatArray(1_000_000);       // off-heap via Panama MemorySegment
data.init(0.0f);

// From existing on-heap array
float[] heapArray = {1.0f, 2.0f, 3.0f};
FloatArray fromHeap = FloatArray.fromArray(heapArray);

// From an existing MemorySegment
MemorySegment seg = MemorySegment.ofArray(new float[]{4.0f, 5.0f});
FloatArray fromSeg = FloatArray.fromSegment(seg);

// Zero-copy wrapping (segment must include TornadoVM header)
FloatArray shallow = FloatArray.fromSegmentShallow(existingSegmentWithHeader);

// Convert back to on-heap
float[] back = data.toHeapArray();

// Access the raw MemorySegment (without header)
MemorySegment raw = data.getSegment();
```

---

## 2. Modern JVM Feature Integration

| JVM Feature | JDK Version | TornadoVM Usage | API Classes Involved | Required vs Optional |
|---|---|---|---|---|
| **AutoCloseable / try-with-resources** | 7+ | `TornadoExecutionPlan` implements `AutoCloseable`; `close()` calls `freeDeviceMemory()` to release GPU buffers | `TornadoExecutionPlan` | **Required pattern** — prevents device memory leaks |
| **Panama Foreign Memory API (`MemorySegment`)** | 21+ (finalized) | All `TornadoNativeArray` subtypes allocate off-heap via `MemorySegment` for zero-copy GPU transfers | `FloatArray`, `IntArray`, `DoubleArray`, `LongArray`, `ShortArray`, `ByteArray`, `CharArray`, `HalfFloatArray`, `Int8Array`, `TornadoMemorySegment` | **Recommended** — native arrays are the preferred data type |
| **Panama SegmentScope / Arena** | 21+ | `TornadoMemorySegment` manages lifecycle of off-heap memory internally; `Arena.ofAuto()` used for allocation | `TornadoMemorySegment` (internal) | Internal — not exposed to users |
| **Sealed classes** | 17+ | `TornadoNativeArray` is `sealed permits ByteArray, CharArray, DoubleArray, FloatArray, HalfFloatArray, IntArray, LongArray, ShortArray, Int8Array`; `PrimitiveStorage` is sealed; `TornadoVectorsInterface`, `TornadoImagesInterface`, `TornadoMatrixInterface`, `TornadoVolumesInterface`, `TornadoCollectionInterface` are all sealed interfaces | All type hierarchy roots | Structural — ensures type safety at compile time |
| **Sealed classes (ExecutionPlan)** | 17+ | `TornadoExecutionPlan` is `sealed permits ExecutionPlanType`; the plan type chain uses sealed hierarchy | `TornadoExecutionPlan`, `ExecutionPlanType`, all `With*`/`Off*` plan types | Structural |
| **Records** | 16+ | `AccessorParameters.PairAccessor` is a `record PairAccessor(Object object, Access access)` | `AccessorParameters` | Used in Prebuilt Task API |
| **Functional interfaces / method references** | 8+ | `Task` through `Task15` are `@FunctionalInterface`; `TaskGraph.task()` accepts method references as lambda expressions | `TornadoFunctions.Task*`, `TaskGraph` | **Core API** — primary way to define tasks |
| **JPMS (Java Module System)** | 9+ | `tornado-api` is the named module `tornado.api`; exports 16 packages; requires `java.management` | `module-info.java` | **Required** — user code must `requires tornado.api` |
| **`Float.floatToFloat16()` / `float16ToFloat()`** | 21+ (JDK 20 preview, 21 stable) | `HalfFloat` class uses these methods for FP16 conversion | `HalfFloat` | Required for half-precision support |
| **`@FunctionalInterface`** | 8+ | All 16 Task interfaces in `TornadoFunctions` | `TornadoFunctions` | Core |
| **Generics with bounded type params** | 5+ | `Task1<T1>` through `Task15<T1,...,T15>`; `PrimitiveStorage<T extends Buffer>` | Throughout | Core |
| **ConcurrentHashMap** | 5+ | Used in `GridScheduler` for thread-safe task-to-grid mapping | `GridScheduler` | Internal |
| **AtomicLong** | 5+ | Global execution plan counter in `TornadoExecutionPlan` | `TornadoExecutionPlan` | Internal |
| **Stream API** | 8+ | `TornadoDeviceMap` uses streams for device filtering; `FloatArray.concat()` uses `Arrays.stream()` | `TornadoDeviceMap`, native arrays | Convenience API |

### Key Takeaways for Developers

- **JDK 21+ is the practical minimum** — Panama `MemorySegment` (finalized in JDK 21) is used throughout the native array system. `Float.floatToFloat16()` also requires JDK 20+.
- **Always use try-with-resources** with `TornadoExecutionPlan` to ensure GPU memory is freed.
- **Sealed classes** mean you cannot extend `TornadoNativeArray` or the type interfaces — the type system is closed.
- **No Virtual Threads (Loom) integration** was found in the API. TaskGraph execution is synchronous; `withConcurrentDevices()` uses its own threading model.

---

## 3. Type System & Data Types

### 3.1 Native Arrays (`TornadoNativeArray` hierarchy)

```
abstract sealed class TornadoNativeArray
  permits ByteArray, CharArray, DoubleArray, FloatArray,
          HalfFloatArray, IntArray, LongArray, ShortArray, Int8Array
```

**Base class methods:**

| Method | Return | Description |
|---|---|---|
| `getSize()` | `int` | Number of elements |
| `getSegment()` | `MemorySegment` | Off-heap data segment (no header) |
| `getSegmentWithHeader()` | `MemorySegment` | Full segment including TornadoVM header |
| `getNumBytesOfSegment()` | `long` | Data bytes excluding header |
| `getNumBytesOfSegmentWithHeader()` | `long` | Total bytes including header |
| `getElementSize()` | `int` | Bytes per element |
| `clear()` | `void` | Zero out contents |

**Constant:** `ARRAY_HEADER` — header size in bytes (default 16 with compressed oops, 24 without; configurable via `tornado.panama.objectHeader`).

#### Concrete Native Array Types

| Class | Element Type | Element Size | Annotation |
|---|---|---|---|
| `FloatArray` | `float` | 4 bytes | `@SegmentElementSize(size = 4)` |
| `DoubleArray` | `double` | 8 bytes | `@SegmentElementSize(size = 8)` |
| `IntArray` | `int` | 4 bytes | `@SegmentElementSize(size = 4)` |
| `LongArray` | `long` | 8 bytes | `@SegmentElementSize(size = 8)` |
| `ShortArray` | `short` | 2 bytes | `@SegmentElementSize(size = 2)` |
| `ByteArray` | `byte` | 1 byte | `@SegmentElementSize(size = 1)` |
| `CharArray` | `char` | 2 bytes | `@SegmentElementSize(size = 2)` |
| `HalfFloatArray` | `HalfFloat` | 2 bytes | `@SegmentElementSize(size = 2)` |
| `Int8Array` | `byte` (signed 8-bit int) | 1 byte | `@SegmentElementSize(size = 1)` |

**Common methods on all concrete types** (using `FloatArray` as example):

```java
// Construction
new FloatArray(int numberOfElements)
FloatArray.fromArray(float[] values)
FloatArray.fromElements(float... values)
FloatArray.fromSegment(MemorySegment segment)
FloatArray.fromSegmentShallow(MemorySegment segmentWithHeader) // zero-copy wrap
FloatArray.fromFloatBuffer(FloatBuffer buffer)

// Element access
void set(int index, float value)
float get(int index)

// Bulk operations
void init(float value)          // fill all elements
void clear()                    // zero all elements
float[] toHeapArray()           // copy to on-heap array
FloatArray slice(int offset, int length)
static FloatArray concat(FloatArray... arrays)

// Task-graph compatible factory
static void initialize(FloatArray array, float value) // uses @Parallel
```

### 3.2 HalfFloat (FP16)

```java
@HalfType
public class HalfFloat {
    // Constructors
    HalfFloat(float value)    // converts float32 → float16 via Float.floatToFloat16()
    HalfFloat(short rawBits)  // direct from short representation

    // Accessors
    short getHalfFloatValue() // raw FP16 bits as short
    float getFloat32()        // convert back to float32

    // Static arithmetic (promote to float32, compute, demote)
    static HalfFloat add(HalfFloat a, HalfFloat b)
    static HalfFloat sub(HalfFloat a, HalfFloat b)
    static HalfFloat mult(HalfFloat a, HalfFloat b)
    static HalfFloat div(HalfFloat a, HalfFloat b)
}
```

### 3.3 Vector Types

Small fixed-size vectors for GPU-style SIMD programming. All implement `TornadoVectorsInterface<T extends Buffer>` which extends `PrimitiveStorage<T>`.

```
sealed interface TornadoVectorsInterface<T extends Buffer> extends PrimitiveStorage<T>
  permits Byte3, Byte4, Double2..Double16, Float2..Float16,
          Half2..Half16, Int2..Int16, Short2, Short3
```

| Family | Sizes | Storage | Buffer Type |
|---|---|---|---|
| `Float2..Float16` | 2, 3, 4, 8, 16 | `float[]` | `FloatBuffer` |
| `Double2..Double16` | 2, 3, 4, 8, 16 | `double[]` | `DoubleBuffer` |
| `Int2..Int16` | 2, 3, 4, 8, 16 | `int[]` | `IntBuffer` |
| `Half2..Half16` | 2, 3, 4, 8, 16 | `short[]` (FP16 bits) | `ShortBuffer` |
| `Short2, Short3` | 2, 3 | `short[]` | `ShortBuffer` |
| `Byte3, Byte4` | 3, 4 | `byte[]` | `ByteBuffer` |

**Common methods** (e.g., `Float4`):

```java
Float4()                                    // zero-initialized
Float4(float x, float y, float z, float w)
float getX(), getY(), getZ(), getW()
void setX(float), setY(float), ...
float get(int index)
void set(int index, float value)
static Float4 add(Float4 a, Float4 b)      // element-wise arithmetic
static Float4 sub(Float4 a, Float4 b)
static Float4 mult(Float4 a, Float4 b)
static Float4 div(Float4 a, Float4 b)
static Float4 min(Float4 a, Float4 b)
static Float4 max(Float4 a, Float4 b)
static float dot(Float4 a, Float4 b)
Float4 duplicate()
int size()                                  // always 4
long getNumBytes()                          // always 16
float[] toArray()
FloatBuffer asBuffer()
```

### 3.4 Collection Types (Vector of Vectors)

Collections store arrays of vector types. All implement `TornadoCollectionInterface<T extends Buffer>` which extends `PrimitiveStorage<T>`.

| Class | Element Type | Backing Storage |
|---|---|---|
| `VectorFloat` | scalar `float` | `FloatArray` |
| `VectorFloat2` | `Float2` | `FloatArray` |
| `VectorFloat3` | `Float3` | `FloatArray` |
| `VectorFloat4` | `Float4` | `FloatArray` |
| `VectorFloat8` | `Float8` | `FloatArray` |
| `VectorFloat16` | `Float16` | `FloatArray` |
| `VectorDouble..VectorDouble16` | `Double*` | `DoubleArray` |
| `VectorInt..VectorInt16` | `Int*` / `int` | `IntArray` |
| `VectorHalf..VectorHalf16` | `Half*` | `HalfFloatArray` |

### 3.5 Image Types

2D image containers. All implement `TornadoImagesInterface<T extends Buffer>`.

| Class | Element Type | Dimensions |
|---|---|---|
| `ImageFloat` | `float` | width × height |
| `ImageFloat3` | `Float3` | width × height |
| `ImageFloat4` | `Float4` | width × height |
| `ImageFloat8` | `Float8` | width × height |
| `ImageByte3` | `Byte3` | width × height |
| `ImageByte4` | `Byte4` | width × height |

```java
ImageFloat img = new ImageFloat(width, height);
img.set(x, y, value);
float v = img.get(x, y);
float mean = img.mean();
float min = img.min();
```

### 3.6 Matrix Types

2D and 3D matrix containers. All implement `TornadoMatrixInterface<T extends Buffer>`.

| Class | Element Type | Dimensions | Base Class |
|---|---|---|---|
| `Matrix2DFloat` | `float` | rows × cols | `Matrix2DType` |
| `Matrix2DDouble` | `double` | rows × cols | `Matrix2DType` |
| `Matrix2DInt` | `int` | rows × cols | `Matrix2DType` |
| `Matrix2DFloat4` | `Float4` | rows × cols | `Matrix2DType` |
| `Matrix3DFloat` | `float` | rows × cols × depth | `Matrix3DType` |
| `Matrix3DDouble` | `double` | rows × cols × depth | `Matrix3DType` |
| `Matrix3DInt` | `int` | rows × cols × depth | `Matrix3DType` |
| `Matrix3DLong` | `long` | rows × cols × depth | `Matrix3DType` |
| `Matrix3DShort` | `short` | rows × cols × depth | `Matrix3DType` |
| `Matrix3DFloat4` | `Float4` | rows × cols × depth | `Matrix3DType` |
| `Matrix4x4Float` | `float` | 4 × 4 (fixed) | — |

```java
Matrix2DFloat mat = new Matrix2DFloat(rows, cols);
mat.set(i, j, value);
float v = mat.get(i, j);
Matrix2DFloat t = mat.transpose();
```

### 3.7 Volume Types

3D volumetric data.

| Class | Element Type |
|---|---|
| `VolumeShort2` | `Short2` |

```java
VolumeShort2 vol = new VolumeShort2(width, height, depth);
vol.set(x, y, z, new Short2(a, b));
Short2 v = vol.get(x, y, z);
```

### 3.8 Type Hierarchy Overview

```
Serializable
  └─ PrimitiveStorage<T extends Buffer>  (sealed interface)
       ├─ TornadoCollectionInterface<T>  (sealed) — VectorFloat, VectorFloat2, ...
       ├─ TornadoImagesInterface<T>      (sealed) — ImageFloat, ImageByte3, ...
       ├─ TornadoMatrixInterface<T>      (sealed) — Matrix2DFloat, Matrix3DFloat, ...
       ├─ TornadoVectorsInterface<T>     (sealed) — Float2, Float4, Int4, ...
       └─ TornadoVolumesInterface<T>     (sealed) — VolumeShort2

TornadoNativeArray (abstract sealed class)
  ├─ FloatArray, DoubleArray, IntArray, LongArray
  ├─ ShortArray, ByteArray, CharArray
  ├─ HalfFloatArray, Int8Array
```

---

## 4. TaskGraph API

**Class:** `uk.ac.manchester.tornado.api.TaskGraph` implements `TaskGraphInterface`

A `TaskGraph` is the mutable builder for defining a computation graph: data transfers in, task lambdas, data transfers out. Once defined, it is frozen via `snapshot()`.

### 4.1 Constructor

```java
public TaskGraph(String name)
```

The `name` identifies this graph and is used as a prefix for task names (e.g., `"graph"` → tasks referenced as `"graph.taskId"`).

### 4.2 Data Transfer Methods

```java
// Transfer objects to device
TaskGraph transferToDevice(int mode, Object... objects)

// Transfer objects from device to host
TaskGraph transferToHost(int mode, Object... objects)

// Persist objects on device (no automatic copy-out)
TaskGraph persistOnDevice(Object... objects)

// Consume data already on device from a previous task-graph
TaskGraph consumeFromDevice(String uniqueTaskGraphName, Object... objects)
TaskGraph consumeFromDevice(Object... objects)  // uses this graph's name
```

**`DataTransferMode` constants (class with `static final int`):**

| Constant | Value | Semantics |
|---|---|---|
| `FIRST_EXECUTION` | `0` | Transfer only on first `execute()`. Data stays on device as read-only. |
| `EVERY_EXECUTION` | `1` | Transfer on every `execute()` call. |
| `UNDER_DEMAND` | `2` | No automatic transfer-out. Developer must call `TornadoExecutionResult.transferToHost()`. |

### 4.3 Task Methods

The `task()` method has 16 overloads accepting 0 to 15 parameters:

```java
TaskGraph task(String id, Task code)
<T1> TaskGraph task(String id, Task1<T1> code, T1 arg)
<T1, T2> TaskGraph task(String id, Task2<T1, T2> code, T1 arg1, T2 arg2)
// ... up to
<T1..T15> TaskGraph task(String id, Task15<T1..T15> code, T1 arg1, ... T15 arg15)
```

Each `Task*` is a `@FunctionalInterface` defined in `TornadoFunctions`:

```java
@FunctionalInterface interface Task   { void apply(); }
@FunctionalInterface interface Task1<T1> { void apply(T1 arg1); }
// ... through Task15
```

**Usage with method references:**

```java
tg.task("saxpy", MyClass::saxpy, alpha, x, y);
// where saxpy is: static void saxpy(float alpha, FloatArray x, FloatArray y) { ... }
```

**Task name uniqueness:** Each task ID must be unique within a `TaskGraph`. Duplicates throw `TornadoTaskRuntimeException`.

**Task naming convention:** The fully-qualified task name is `"<graphName>.<taskId>"` (e.g., `"s0.saxpy"`). This is the name used in `GridScheduler`.

### 4.4 Prebuilt Task Methods

```java
TaskGraph prebuiltTask(String id, String entryPoint, String filename,
                       AccessorParameters accessorParameters)

TaskGraph prebuiltTask(String id, String entryPoint, String filename,
                       AccessorParameters accessorParameters, int[] atomics)
```

### 4.5 Snapshot

```java
ImmutableTaskGraph snapshot()
```

Creates an **immutable** copy of the task graph. The original `TaskGraph` can be further mutated without affecting the snapshot. Returns `ImmutableTaskGraph` which is consumed by `TornadoExecutionPlan`.

### 4.6 Other Methods

```java
String getTaskGraphName()
Set<Object> getArgumentsLookup()
Collection<?> getOutputs()
boolean isGridRegistered()
TaskGraph addTask(TaskPackage taskPackage) // low-level alternative
```

### 4.7 Chaining Pattern (Multiple Tasks)

```java
TaskGraph tg = new TaskGraph("pipeline")
    .transferToDevice(DataTransferMode.EVERY_EXECUTION, inputA, inputB)
    .task("step1", MyClass::preprocess, inputA, tempBuffer)
    .task("step2", MyClass::compute, tempBuffer, inputB, outputC)
    .transferToHost(DataTransferMode.EVERY_EXECUTION, outputC);
```

---

## 5. TornadoExecutionPlan

**Class:** `uk.ac.manchester.tornado.api.TornadoExecutionPlan implements AutoCloseable`

`sealed class TornadoExecutionPlan implements AutoCloseable permits ExecutionPlanType`

The execution plan wraps one or more `ImmutableTaskGraph` instances and provides a fluent API for configuring execution. All `with*()` methods return `TornadoExecutionPlan` for chaining.

### 5.1 Constructor & Static Methods

```java
// Primary constructor — takes one or more frozen task graphs
public TornadoExecutionPlan(ImmutableTaskGraph... immutableTaskGraphs)

// Static device lookup
public static TornadoDevice getDevice(int driverIndex, int deviceIndex)
public static TornadoDeviceMap getTornadoDeviceMap()
public static int getTotalPlans()

// Default device constant
public static TornadoDevice DEFAULT_DEVICE
```

### 5.2 Core Execution

```java
TornadoExecutionResult execute()         // run the plan, return result with profiler data
```

### 5.3 Device Configuration

```java
TornadoExecutionPlan withDevice(TornadoDevice device)                    // all tasks → one device
TornadoExecutionPlan withDevice(String taskName, TornadoDevice device)   // specific task → device
TornadoDevice getDevice(int immutableTaskGraphIndex)                     // query current device
```

### 5.4 Grid & Scheduling

```java
TornadoExecutionPlan withGridScheduler(GridScheduler gridScheduler)
TornadoExecutionPlan withDefaultScheduler()
```

### 5.5 Warm-Up

```java
TornadoExecutionPlan withWarmUpTime(long milliseconds) throws InterruptedException
TornadoExecutionPlan withWarmUpIterations(int iterations)
TornadoExecutionPlan withPreCompilation()  // JIT compile without execution
```

### 5.6 Memory Control

```java
TornadoExecutionPlan freeDeviceMemory()           // mark buffers for reuse
TornadoExecutionPlan resetDevice()                // full device context reset
TornadoExecutionPlan withMemoryLimit(String limit) // e.g., "512MB", "1GB"
TornadoExecutionPlan withoutMemoryLimit()
long getCurrentDeviceMemoryUsage()
```

### 5.7 Multi-Graph Control

```java
TornadoExecutionPlan withGraph(int graphIndex)    // select specific graph
TornadoExecutionPlan withAllGraphs()              // select all graphs
TornadoExecutionPlan withConcurrentDevices()      // tasks run in parallel on devices
TornadoExecutionPlan withoutConcurrentDevices()
```

### 5.8 Profiling

```java
TornadoExecutionPlan withProfiler(ProfilerMode profilerMode) // CONSOLE or SILENT
TornadoExecutionPlan withoutProfiler()
TornadoExecutionPlan clearProfiles()
```

### 5.9 Debugging & Compiler

```java
TornadoExecutionPlan withThreadInfo()              // print thread-block info
TornadoExecutionPlan withoutThreadInfo()
TornadoExecutionPlan withPrintKernel()             // print generated kernel source
TornadoExecutionPlan withoutPrintKernel()
TornadoExecutionPlan withCompilerFlags(TornadoVMBackendType backend, String flags)
TornadoExecutionPlan withCUDAGraph()               // CUDA graph optimization
```

### 5.10 Device Memory Mapping

```java
void mapOnDeviceMemoryRegion(Object destArray, Object srcArray,
                              long offset, int fromGraphIndex, int toGraphIndex)
```

Avoids device→host→device transfers between task graphs by mapping device pointers directly.

### 5.11 Result Access

```java
TornadoExecutionResult getPlanResult(int index) // get result from previous executions
long getId()
void printTraceExecutionPlan()
String getTraceExecutionPlan()
```

### 5.12 AutoCloseable

```java
@Override
public void close() throws TornadoExecutionPlanException {
    tornadoExecutor.freeDeviceMemory();
}
```

---

## 6. Annotations

### 6.1 `@Parallel`

```java
@Target({LOCAL_VARIABLE, TYPE, TYPE_USE, TYPE_PARAMETER})
@Retention(RUNTIME)
public @interface Parallel {}
```

**Semantics:** Annotates a `for`-loop induction variable to indicate that iterations are independent and can execute in parallel on the accelerator.

```java
for (@Parallel int i = 0; i < size; i++) { ... }
```

**Constraints:**
- Must annotate an `int` loop variable
- Loop must have simple bounds (start, end, stride)
- Nested `@Parallel` loops create 2D/3D parallelism
- Method containing `@Parallel` loops should be `static`

### 6.2 `@Reduce`

```java
@Target({PARAMETER, LOCAL_VARIABLE, FIELD})
@Retention(RUNTIME)
public @interface Reduce {}
```

**Semantics:** Marks a variable for parallel reduction. TornadoVM generates reduction code (tree-based or atomic) automatically.

```java
private static void reduce(FloatArray input, @Reduce FloatArray result) {
    result.set(0, 0.0f);
    for (@Parallel int i = 0; i < input.getSize(); i++) {
        result.set(0, result.get(0) + input.get(i));
    }
}
```

### 6.3 Internal Annotations

| Annotation | Target | Purpose |
|---|---|---|
| `@CompilerIntrinsic` | `METHOD` | Marks methods replaced by intrinsics at compile time |
| `@HalfType` | `TYPE` | Marks FP16 types |
| `@Payload` | `FIELD` | Marks the payload field of a type |
| `@SegmentElementSize(size)` | `TYPE` | Specifies element size for native arrays (default 4) |
| `@TornadoVMIntrinsic` | `TYPE` | Marks TornadoVM intrinsic types |
| `@Vector` | `TYPE` | Marks vector types |

---

## 7. KernelContext API

**Class:** `uk.ac.manchester.tornado.api.KernelContext implements ExecutionContext`

`KernelContext` provides GPU kernel-style programming within Java. Unlike `@Parallel`, it gives explicit control over thread IDs, local memory, and barriers.

### 7.1 Thread Identification Fields

| Field | Type | Description | OpenCL Equiv. | CUDA Equiv. |
|---|---|---|---|---|
| `globalIdx` | `Integer` | Global thread ID, dim 0 | `get_global_id(0)` | `blockIdx.x * blockDim.x + threadIdx.x` |
| `globalIdy` | `Integer` | Global thread ID, dim 1 | `get_global_id(1)` | `blockIdx.y * blockDim.y + threadIdx.y` |
| `globalIdz` | `Integer` | Global thread ID, dim 2 | `get_global_id(2)` | `blockIdx.z * blockDim.z + threadIdx.z` |
| `localIdx` | `Integer` | Local thread ID, dim 0 | `get_local_id(0)` | `threadIdx.x` |
| `localIdy` | `Integer` | Local thread ID, dim 1 | `get_local_id(1)` | `threadIdx.y` |
| `localIdz` | `Integer` | Local thread ID, dim 2 | `get_local_id(2)` | `threadIdx.z` |
| `groupIdx` | `Integer` | Work-group ID, dim 0 | `get_group_id(0)` | `blockIdx.x` |
| `groupIdy` | `Integer` | Work-group ID, dim 1 | `get_group_id(1)` | `blockIdx.y` |
| `groupIdz` | `Integer` | Work-group ID, dim 2 | `get_group_id(2)` | `blockIdx.z` |
| `globalGroupSizeX` | `Integer` | Total threads, dim 0 | `get_global_size(0)` | `gridDim.x * blockDim.x` |
| `globalGroupSizeY` | `Integer` | Total threads, dim 1 | `get_global_size(1)` | `gridDim.y * blockDim.y` |
| `globalGroupSizeZ` | `Integer` | Total threads, dim 2 | `get_global_size(2)` | `gridDim.z * blockDim.z` |
| `localGroupSizeX` | `Integer` | Local group size, dim 0 | `get_local_size(0)` | `blockDim.x` |
| `localGroupSizeY` | `Integer` | Local group size, dim 1 | `get_local_size(1)` | `blockDim.y` |
| `localGroupSizeZ` | `Integer` | Local group size, dim 2 | `get_local_size(2)` | `blockDim.z` |

### 7.2 Local Memory Allocation

```java
int[]       allocateIntLocalArray(int size)
long[]      allocateLongLocalArray(int size)
float[]     allocateFloatLocalArray(int size)
double[]    allocateDoubleLocalArray(int size)
byte[]      allocateByteLocalArray(int size)
HalfFloat[] allocateHalfFloatLocalArray(int size)
```

These allocate in device local/shared memory. On CPU fallback, they create regular Java arrays.

### 7.3 Barriers

```java
void localBarrier()   // CLK_LOCAL_MEM_FENCE / barrier.sync
void globalBarrier()  // CLK_GLOBAL_MEM_FENCE / barrier.sync
```

### 7.4 Atomic Operations

```java
void atomicAdd(IntArray array, int index, int val)
void atomicAdd(int[] array, int index, int val)
void atomicAdd(LongArray array, int index, long val)
void atomicAdd(FloatArray array, int index, float val)
void atomicAdd(DoubleArray array, int index, double val)
```

### 7.5 Metal SIMD Intrinsics

```java
float simdSum(float val)                  // simd_sum(val)
float simdShuffleDown(float val, int delta) // simd_shuffle_down(val, delta)
float simdBroadcastFirst(float val)       // simd_broadcast_first(val)
```

### 7.6 When to Use KernelContext vs @Parallel

| Feature | `@Parallel` | `KernelContext` |
|---|---|---|
| Ease of use | Simple — annotate loop | More code — explicit thread IDs |
| Local memory | Not available | `allocate*LocalArray()` |
| Barriers | Not available | `localBarrier()`, `globalBarrier()` |
| Grid control | Automatic | Requires `GridScheduler` |
| Thread ID access | Implicit | Explicit via `globalIdx`, etc. |
| Atomics | Via `@Reduce` | Explicit `atomicAdd()` |
| Best for | Data-parallel loops | Complex kernels, tiling, reductions |

---

## 8. Grid Scheduling

### 8.1 WorkerGrid Interface

```java
public interface WorkerGrid {
    int dimension();
    long[] getGlobalWork();
    long[] getLocalWork();
    long[] getNumberOfWorkgroups();
    long[] getGlobalOffset();
    void setGlobalWork(long x, long y, long z);
    void setLocalWork(long x, long y, long z);
    void setGlobalOffset(long x, long y, long z);
    void setLocalWorkToNull();
    void setNumberOfWorkgroupsToNull();
}
```

### 8.2 AbstractWorkerGrid

Base implementation storing `globalWork`, `localWork`, `numOfWorkgroups`, `globalOffset` as `long[3]` arrays. Automatically computes `numOfWorkgroups` when `setLocalWork()` is called.

### 8.3 Concrete Grid Classes

```java
// 1D grid: x threads, y=1, z=1
WorkerGrid1D grid1d = new WorkerGrid1D(1024);
grid1d.setLocalWork(256, 1, 1);

// 2D grid: x × y threads, z=1
WorkerGrid2D grid2d = new WorkerGrid2D(512, 512);
grid2d.setLocalWork(16, 16, 1);

// 3D grid: x × y × z threads
WorkerGrid3D grid3d = new WorkerGrid3D(64, 64, 64);
grid3d.setLocalWork(4, 4, 4);
```

### 8.4 GridScheduler

Binds task names to `WorkerGrid` objects:

```java
// Constructor with initial binding
GridScheduler scheduler = new GridScheduler("graph.taskName", workerGrid);

// Default constructor + addWorkerGrid
GridScheduler scheduler = new GridScheduler();
scheduler.addWorkerGrid("graph.task1", grid1);
scheduler.addWorkerGrid("graph.task2", grid2);

// Query
WorkerGrid grid = scheduler.get("graph.task1");
boolean has = scheduler.contains("graph", "task1");
Set<String> keys = scheduler.keySet();
```

**Usage with execution plan:**

```java
plan.withGridScheduler(scheduler).execute();
```

---

## 9. Device Selection & Backend API

### 9.1 TornadoBackend Interface

```java
public interface TornadoBackend {
    TornadoDevice getDefaultDevice();
    void setDefaultDevice(int index);
    int getNumDevices();
    TornadoDevice getDevice(int index);
    List<TornadoDevice> getAllDevices();
    TornadoDeviceType getTypeDefaultDevice();
    String getName();
    int getNumPlatforms();
    TornadoVMBackendType getBackendType();
}
```

### 9.2 TornadoRuntime Interface

```java
public interface TornadoRuntime {
    TornadoBackend getBackend(int index);
    void setDefaultBackend(int index);
    TornadoVMBackendType getBackendType(int index);
    int getNumBackends();
    TornadoDevice getDefaultDevice();
    boolean isProfilerEnabled();
    boolean isPowerMonitoringEnabled();
    long getPowerMetric();  // Watts
}
```

Access via: `TornadoRuntimeProvider.getTornadoRuntime()`

### 9.3 TornadoDevice Interface

Key methods on `TornadoDevice` (from `uk.ac.manchester.tornado.api.common`):

```java
String getDeviceName()
String getDescription()
String getPlatformName()
TornadoDeviceType getDeviceType()            // GPU, CPU, FPGA, ACCELERATOR
TornadoVMBackendType getTornadoVMBackend()   // OPENCL, PTX, SPIRV, etc.
long getMaxAllocMemory()
long getMaxGlobalMemory()
long getDeviceLocalMemorySize()
long[] getDeviceMaxWorkgroupDimensions()
String getDeviceOpenCLCVersion()
int getBackendIndex()
boolean isSPIRVSupported()
TornadoTargetDevice getPhysicalDevice()
TornadoDeviceContext getDeviceContext()
TornadoMemoryProvider getMemoryProvider()
```

### 9.4 TornadoDeviceType Enum

```java
public enum TornadoDeviceType {
    UNKNOWN, DEFAULT, CPU, GPU, FPGA, ACCELERATOR, CUSTOM, ALL
}
```

### 9.5 TornadoVMBackendType Enum

```java
public enum TornadoVMBackendType {
    OPENCL("OpenCL"),
    METAL("Metal"),
    PTX("PTX"),
    SPIRV("SPIRV"),
    JAVA("Java"),
    VIRTUAL("Virtual");

    String getName();
}
```

### 9.6 TornadoDeviceMap

```java
public class TornadoDeviceMap {
    int getNumBackends();
    List<TornadoBackend> getAllBackends();
    List<TornadoBackend> getBackendsWithPredicate(Predicate<? super TornadoBackend> predicate);
    List<TornadoBackend> getBackendsWithDevicePredicate(Predicate<? super TornadoDevice> predicate);
    Stream<TornadoDevice> getDevicesByName(String deviceName);   // e.g., "NVIDIA"
    Stream<TornadoDevice> getDevicesByType(TornadoDeviceType type); // e.g., GPU
}
```

**Device selection example:**

```java
TornadoDeviceMap deviceMap = TornadoExecutionPlan.getTornadoDeviceMap();

// Find first NVIDIA GPU
TornadoDevice nvidiaGpu = deviceMap.getDevicesByName("NVIDIA").findFirst().orElseThrow();

// Find any GPU device
TornadoDevice gpu = deviceMap.getDevicesByType(TornadoDeviceType.GPU).findFirst().orElseThrow();

// Use specific backend/device index
TornadoDevice device = TornadoExecutionPlan.getDevice(0, 1); // backend 0, device 1

try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
    plan.withDevice(nvidiaGpu).execute();
}
```

---

## 10. Memory Management Patterns

### 10.1 Automatic (Recommended)

```java
try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
    plan.execute();
} // close() → freeDeviceMemory() → GPU buffers released
```

### 10.2 Manual

```java
TornadoExecutionPlan plan = new TornadoExecutionPlan(itg);
plan.execute();
// ... use results ...
plan.freeDeviceMemory();  // explicit release, buffers marked for reuse
plan.resetDevice();       // full device context reset (code cache + events cleared)
```

### 10.3 Transfer Mode Strategies

| Mode | `transferToDevice` | `transferToHost` | Best For |
|---|---|---|---|
| `FIRST_EXECUTION` | Copies once, then read-only on device | N/A | Constants, lookup tables |
| `EVERY_EXECUTION` | Copies before every `execute()` | Copies after every `execute()` | Changing input/output data |
| `UNDER_DEMAND` | N/A | No automatic copy-out; call `result.transferToHost()` | Intermediate data, large outputs |

### 10.4 Native Arrays vs On-Heap Arrays

| Feature | Native Array (`FloatArray`) | On-Heap (`float[]`) |
|---|---|---|
| Memory location | Off-heap (`MemorySegment`) | JVM heap |
| GC pressure | None | Subject to GC |
| Device transfer | Direct mapping possible | Requires copy |
| Allocation | Deterministic | JVM-managed |
| API integration | Full (preferred) | Supported but legacy |

### 10.5 Memory Lifecycle

```
1. Allocate:    FloatArray data = new FloatArray(N);  // off-heap MemorySegment
2. Initialize:  data.init(0.0f);                      // fill values
3. Transfer in: tg.transferToDevice(EVERY_EXECUTION, data)
4. Execute:     plan.execute()                         // kernel runs on device
5. Transfer out: tg.transferToHost(EVERY_EXECUTION, data) // or UNDER_DEMAND
6. Free:        plan.close()  // or plan.freeDeviceMemory()
```

### 10.6 Partial Transfers

```java
DataRange range = new DataRange(myArray)
    .withOffset(1000)    // start from element 1000
    .withSize(500);      // copy 500 elements
range.materialize();

TornadoExecutionResult result = plan.execute();
result.transferToHost(range);  // partial copy-out
```

### 10.7 Memory Limit

```java
plan.withMemoryLimit("512MB");  // cap device memory usage
long used = plan.getCurrentDeviceMemoryUsage(); // query usage in bytes
```

---

## 11. Execution Policies & Profiling

### 11.1 ProfilerMode Enum

```java
public enum ProfilerMode {
    CONSOLE,  // print profiler output to stdout
    SILENT    // store profiler data silently (access via API)
}
```

### 11.2 TornadoExecutionResult

Returned by `execute()`:

```java
public class TornadoExecutionResult {
    TornadoProfilerResult getProfilerResult()
    TornadoExecutionResult transferToHost(Object... objects)   // on-demand transfer
    TornadoExecutionResult transferToHost(DataRange dataRange) // partial transfer
    boolean isReady()                                          // check completion
}
```

### 11.3 TornadoProfilerResult (implements ProfilerInterface)

```java
public class TornadoProfilerResult implements ProfilerInterface {
    long getTotalTime()                  // end-to-end time (ns)
    long getCompileTime()                // total compilation time (ns)
    long getTornadoCompilerTime()        // Graal JIT time (ns)
    long getDriverInstallTime()          // driver binary creation time (ns)
    long getDataTransfersTime()          // total transfer time (ns)
    long getDeviceWriteTime()            // host→device time (ns)
    long getDeviceReadTime()             // device→host time (ns)
    long getDataTransferDispatchTime()   // dispatch overhead (ns)
    long getKernelDispatchTime()         // kernel dispatch overhead (ns)
    long getDeviceKernelTime()           // kernel execution time (ns)
    long getTotalBytesCopyIn()           // bytes sent to device
    long getTotalBytesCopyOut()          // bytes received from device
    long getTotalBytesTransferred()      // total bytes both directions
    long getTotalDeviceMemoryUsage()     // bytes allocated on device
    String getProfileLog()               // JSON format log
    void dumpProfiles()                  // print to stdout
}
```

### 11.4 ProfilerType Enum

All profiler metric categories:

```java
public enum ProfilerType {
    METHOD, IP,
    TOTAL_DISPATCH_KERNEL_TIME,
    TOTAL_DISPATCH_DATA_TRANSFERS_TIME,
    COPY_IN_TIME, COPY_OUT_TIME, COPY_OUT_TIME_SYNC,
    COPY_OUT_SIZE_BYTES_SYNC,
    DEVICE_ID, DEVICE,
    ALLOCATION_BYTES,
    TOTAL_COPY_IN_SIZE_BYTES, TOTAL_COPY_OUT_SIZE_BYTES,
    TASK_COMPILE_DRIVER_TIME, TASK_COMPILE_GRAAL_TIME,
    TASK_CODE_GENERATION_TIME, TASK_KERNEL_TIME,
    TOTAL_BYTE_CODE_GENERATION,
    TOTAL_DRIVER_COMPILE_TIME, TOTAL_GRAAL_COMPILE_TIME,
    TOTAL_CODE_GENERATION_TIME, TOTAL_KERNEL_TIME,
    TOTAL_TASK_GRAPH_TIME,
    POWER_USAGE_mW,
    SYSTEM_POWER_CONSUMPTION_W, SYSTEM_VOLTAGE_V, SYSTEM_CURRENT_A,
    BACKEND
}
```

### 11.5 DRMode (Dynamic Reconfiguration)

```java
public enum DRMode {
    SERIAL,   // evaluate devices sequentially, then switch to best
    PARALLEL  // evaluate devices in parallel (one Java thread per device)
}
```

### 11.6 Profiling Example

```java
try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
    plan.withProfiler(ProfilerMode.SILENT);

    TornadoExecutionResult result = plan.execute();
    TornadoProfilerResult profiler = result.getProfilerResult();

    System.out.println("Total time:   " + profiler.getTotalTime() + " ns");
    System.out.println("Kernel time:  " + profiler.getDeviceKernelTime() + " ns");
    System.out.println("Write time:   " + profiler.getDeviceWriteTime() + " ns");
    System.out.println("Read time:    " + profiler.getDeviceReadTime() + " ns");
    System.out.println("Bytes in:     " + profiler.getTotalBytesCopyIn());
    System.out.println("Bytes out:    " + profiler.getTotalBytesCopyOut());
    System.out.println("Device mem:   " + profiler.getTotalDeviceMemoryUsage());

    // JSON log
    System.out.println(profiler.getProfileLog());
}
```

### 11.7 Warm-Up Patterns

```java
// Time-based warm-up (at least N ms of repeated execution)
plan.withWarmUpTime(5000).execute(); // 5 seconds warm-up, then one more execute

// Iteration-based warm-up
plan.withWarmUpIterations(10).execute(); // 10 warm-up runs, then one more execute

// JIT-only (no data execution)
plan.withPreCompilation();
```

---

## 12. Module System

### `module-info.java`

```java
module tornado.api {
    requires java.management;

    // Core API packages
    exports uk.ac.manchester.tornado.api;
    exports uk.ac.manchester.tornado.api.annotations;
    exports uk.ac.manchester.tornado.api.common;
    exports uk.ac.manchester.tornado.api.enums;
    exports uk.ac.manchester.tornado.api.exceptions;
    exports uk.ac.manchester.tornado.api.memory;
    exports uk.ac.manchester.tornado.api.profiler;
    exports uk.ac.manchester.tornado.api.runtime;
    exports uk.ac.manchester.tornado.api.internal.annotations;
    exports uk.ac.manchester.tornado.api.utils;
    exports uk.ac.manchester.tornado.api.math;

    // Type system packages
    exports uk.ac.manchester.tornado.api.types;
    exports uk.ac.manchester.tornado.api.types.arrays;
    exports uk.ac.manchester.tornado.api.types.collections;
    exports uk.ac.manchester.tornado.api.types.matrix;
    exports uk.ac.manchester.tornado.api.types.images;
    exports uk.ac.manchester.tornado.api.types.utils;
    exports uk.ac.manchester.tornado.api.types.common;
    exports uk.ac.manchester.tornado.api.types.volumes;
    exports uk.ac.manchester.tornado.api.types.vectors;

    // Execution plan types
    exports uk.ac.manchester.tornado.api.plan.types;

    // Opens for reflection (runtime access)
    opens uk.ac.manchester.tornado.api;
    opens uk.ac.manchester.tornado.api.types.arrays;
    opens uk.ac.manchester.tornado.api.types.collections;
    opens uk.ac.manchester.tornado.api.types.matrix;
    opens uk.ac.manchester.tornado.api.types.images;
    opens uk.ac.manchester.tornado.api.types.utils;
    opens uk.ac.manchester.tornado.api.types.common;
    opens uk.ac.manchester.tornado.api.types.volumes;
    opens uk.ac.manchester.tornado.api.types.vectors;
    opens uk.ac.manchester.tornado.api.types;
    opens uk.ac.manchester.tornado.api.runtime;
    opens uk.ac.manchester.tornado.api.plan.types;
}
```

### For User Code

```java
module my.app {
    requires tornado.api;
}
```

This single `requires` gives access to all exported packages.

---

## 13. Prebuilt Tasks & Interop

### 13.1 API Overview

Load pre-compiled OpenCL C, PTX, or SPIR-V kernels:

```java
TaskGraph prebuiltTask(String id, String entryPoint, String filename,
                       AccessorParameters accessorParameters)
```

### 13.2 AccessorParameters

```java
public class AccessorParameters {
    AccessorParameters(int numParameters)
    void set(int index, Object object, Access access)
    int numAccessors()
    PairAccessor getAccessor(int index)

    // Record for parameter binding
    record PairAccessor(Object object, Access access) {}
}
```

### 13.3 Access Enum

```java
public enum Access {
    NONE       ((byte) 0b00),
    READ_ONLY  ((byte) 0b01),
    WRITE_ONLY ((byte) 0b10),
    READ_WRITE ((byte) 0b11);

    public final byte position;
}
```

### 13.4 PrebuiltTaskPackage

Extends `TaskPackage` with:

```java
String getEntryPoint()
String getFilename()
Object[] getArgs()
Access[] getAccesses()
int[] getAtomics()
PrebuiltTaskPackage withAtomics(int[] atomics)
```

### 13.5 Example

```java
FloatArray input = new FloatArray(1024);
FloatArray output = new FloatArray(1024);

AccessorParameters params = new AccessorParameters(2);
params.set(0, input, Access.READ_ONLY);
params.set(1, output, Access.WRITE_ONLY);

TaskGraph tg = new TaskGraph("prebuilt")
    .transferToDevice(DataTransferMode.EVERY_EXECUTION, input)
    .prebuiltTask("kernel", "myKernelEntryPoint", "/path/to/kernel.cl", params)
    .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

ImmutableTaskGraph itg = tg.snapshot();
try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
    plan.execute();
}
```

---

## 14. Math Utilities

### 14.1 TornadoMath

`uk.ac.manchester.tornado.api.math.TornadoMath` — all methods are `public static` and are recognized by the TornadoVM compiler as intrinsics.

**Scalar functions (float and double overloads):**

| Function | Signature(s) |
|---|---|
| `min` | `float/double/long/int/short/byte/HalfFloat` |
| `max` | `float/double/long/int/short/byte/HalfFloat` |
| `abs` | `float/double/long/int/short/byte` |
| `sqrt` | `float/double` |
| `exp` | `float/double` |
| `log` | `float/double` |
| `log2` | `float/double` |
| `pow` | `(float,float)/(double,double)` |
| `floor` | `float/double` |
| `ceil` | `float/double` |
| `fract` | `float/double` |
| `clamp` | `float/double/long/int/short/byte` |
| `sin`, `cos`, `tan` | `float/double` |
| `asin`, `acos`, `atan` | `float/double` |
| `atan2` | `(float,float)/(double,double)` |
| `tanh` | `float/double` |
| `asinh`, `acosh` | `float/double` |
| `sinpi`, `cospi` | `float/double` |
| `signum` | `float/double` |
| `toRadians` | `float/double` |
| `floatPI()`, `PI()` | Returns π as float/double |

**Vector functions:**

```java
static Float2 cos(Float2 f)
static Float4 cos(Float4 f)
static Float8 cos(Float8 f)
static Float16 cos(Float16 f)
static Float2 sin(Float2 f)
static Float4 sin(Float4 f)
// ... etc.
static Float3 rotate(Matrix4x4Float m, Float3 x)
```

**Array comparison utilities:**

```java
static boolean isEqual(float[] a, float[] b)
static boolean isEqual(FloatArray a, FloatArray b)
static boolean isEqual(IntArray a, IntArray b)
static boolean isEqual(DoubleArray a, DoubleArray b)
static boolean isEqual(HalfFloatArray a, HalfFloatArray b)
static boolean isEqualULP(float[] value, float[] expected, float numULP)
static float findULPDistance(FloatArray value, FloatArray expected)
```

### 14.2 Quantization Utilities

`uk.ac.manchester.tornado.api.utils.QuantizationUtils` — utilities for quantization operations.

---

## 15. Exception Hierarchy

```
Error
  └─ TornadoInternalError
       Methods: unimplemented(), shouldNotReachHere(), guarantee()

RuntimeException
  ├─ TornadoRuntimeException
  │    ├─ TornadoBackendNotFound
  │    └─ TornadoDeviceNotFound
  ├─ TornadoAPIException
  ├─ TornadoBailoutRuntimeException     (red-colored messages)
  ├─ TornadoTaskRuntimeException        (red-colored messages)
  ├─ TornadoCompilationException
  ├─ TornadoFailureException
  ├─ TornadoInliningException
  ├─ TornadoMemoryException
  ├─ TornadoOutOfMemoryException
  ├─ TornadoDeviceFP16NotSupported
  ├─ TornadoDeviceFP64NotSupported
  ├─ TornadoNoOpenCLPlatformException
  └─ TornadoNoMetalPlatformException

Exception (checked)
  └─ TornadoExecutionPlanException      (thrown by close())
```

---

## 16. Type Compatibility Matrix

### Types That Can Flow Through `TaskGraph.task()` Parameters

| Java Type | As Task Parameter | As `transferToDevice` | As `transferToHost` | Notes |
|---|---|---|---|---|
| `FloatArray` | Yes | Yes | Yes | **Preferred** — off-heap, zero-copy |
| `IntArray` | Yes | Yes | Yes | Preferred |
| `DoubleArray` | Yes | Yes | Yes | Preferred |
| `LongArray` | Yes | Yes | Yes | Preferred |
| `ShortArray` | Yes | Yes | Yes | Preferred |
| `ByteArray` | Yes | Yes | Yes | Preferred |
| `CharArray` | Yes | Yes | Yes | Preferred |
| `HalfFloatArray` | Yes | Yes | Yes | Requires FP16 device support |
| `Int8Array` | Yes | Yes | Yes | Signed 8-bit integer array |
| `float[]` | Yes | Yes | Yes | On-heap, requires copy |
| `int[]` | Yes | Yes | Yes | On-heap |
| `double[]` | Yes | Yes | Yes | On-heap |
| `long[]` | Yes | Yes | Yes | On-heap |
| `short[]` | Yes | Yes | Yes | On-heap |
| `byte[]` | Yes | Yes | Yes | On-heap |
| `char[]` | Yes | Yes | Yes | On-heap |
| `VectorFloat*` | Yes | Yes | Yes | Collection of float vectors |
| `VectorInt*` | Yes | Yes | Yes | Collection of int vectors |
| `VectorDouble*` | Yes | Yes | Yes | Collection of double vectors |
| `VectorHalf*` | Yes | Yes | Yes | Collection of half vectors |
| `ImageFloat*` | Yes | Yes | Yes | 2D image data |
| `ImageByte*` | Yes | Yes | Yes | 2D byte image data |
| `Matrix2D*` | Yes | Yes | Yes | 2D matrices |
| `Matrix3D*` | Yes | Yes | Yes | 3D matrices |
| `Matrix4x4Float` | Yes | Yes | Yes | Fixed 4×4 matrix |
| `VolumeShort2` | Yes | Yes | Yes | 3D volume data |
| `KernelContext` | Yes (1st arg) | No | No | Thread context, not data |
| `int`, `float`, `double`, `long` | Yes (scalar) | No | No | Passed by value |
| `Integer`, `Float`, `Double`, `Long` | Yes (scalar) | No | No | Unboxed at compile time |
| Arbitrary objects | Limited | No | No | Only if all fields are primitives |

### Scalar Type Support in Kernels

| Scalar Type | In `@Parallel` loops | In `KernelContext` tasks | Reduction (`@Reduce`) |
|---|---|---|---|
| `int` | Yes | Yes | Yes |
| `float` | Yes | Yes | Yes |
| `double` | Yes (if device supports FP64) | Yes | Yes |
| `long` | Yes | Yes | Yes |
| `short` | Yes | Yes | Limited |
| `byte` | Yes | Yes | Limited |
| `HalfFloat` | Yes (if device supports FP16) | Yes | Limited |

---

*Document generated from exhaustive analysis of all 150+ Java source files in `tornado-api/src/main/java/`. Every public class, interface, enum, annotation, and method signature was verified directly from source.*
