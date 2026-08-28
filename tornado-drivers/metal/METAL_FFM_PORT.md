# Metal backend: JNI to FFM

Handoff notes for finishing the Metal backend's move off JNI onto `java.lang.foreign`. The
foundation is written and compiles; none of it has been run, because it was written on a Linux
box. This document says what exists, what is left, and where the traps are.

## Where this fits

PR [#1058](https://github.com/beehive-lab/TornadoVM/pull/1058) removed the JNI libraries from the
OpenCL and CUDA backends and from the cuBLAS, cuBLASLt, cuFFT, cuSPARSE and cuDNN library-task
providers. Metal is the last backend still on JNI. Nothing in this branch changes the Metal
backend's behaviour: `metal-jni` is untouched and still in the build, and the two new classes are
additive and currently unreferenced, so Metal cannot regress from this work until it is wired up.

## Why Metal is different from the other backends

OpenCL and CUDA are C libraries. Their ports were mechanical: one `Linker.downcallHandle` per
entry point, and the Java layer already spoke the right vocabulary.

**Metal has no C API.** `MTLDevice`, `MTLCommandQueue`, `MTLBuffer` and the rest are Objective-C
protocols, and Apple ships no C wrapper (`metal-cpp` is C++, which FFM cannot call either). What
*is* a C ABI is the Objective-C runtime itself, so the port goes:

```
Java  →  objc_msgSend(receiver, selector, args...)  →  Objective-C method
```

That is a supported, well-trodden technique, but it has rules that the C-library ports did not.

## What exists

Both under `uk.ac.manchester.tornado.drivers.metal.ffm`:

### `ObjCRuntime`

The runtime layer, and the part worth reading carefully.

- `objc_getClass(name)` and `sel(name)` — interned lookups of classes and selectors.
- `msgSend(FunctionDescriptor)` — a cache of `objc_msgSend` downcall handles, one per signature.
- `send` / `sendVoid` / `sendBoolean` convenience overloads for the common shapes.
- `newNSString` / `toJavaString` — `NSString` conversion.
- `retain` / `release` and `AutoreleasePool` — manual reference counting.
- `MTL_SIZE` — the `MTLSize` struct layout, three 64-bit fields.

### `MetalAPI`

One method per message send the backend needs, each paired with the signature that send must be
made through: device discovery and properties, command queues, buffers, MSL compilation, pipeline
state, encoders, dispatch, blit copies, GPU timestamps.

## The rules

### 1. `objc_msgSend` is not variadic

It is declared variadic in the headers, but it is a trampoline: it must be called through a
prototype matching the **target method's** signature so the arguments land in the registers that
method reads. In C you cast the symbol at each call site. Here the equivalent is one downcall
handle per signature, which is what `ObjCRuntime.msgSend(FunctionDescriptor)` hands out.

Reusing a handle whose descriptor does not match the selector is **undefined behaviour, not a type
error**. It will not throw; it will read garbage or corrupt memory. This is the single most likely
source of a mysterious crash while finishing this work, which is why every wrapper in `MetalAPI`
names its descriptor next to the selector it goes with — keep that convention.

### 2. There is no ARC

Automatic reference counting is compiler-inserted, and there is no Objective-C compiler in this
path. So:

- Anything returned by a method whose name begins `new`, `alloc`, `copy` or `mutableCopy` is
  **owned by you** — `release` it.
- Anything else is **autoreleased** — `retain` it if it must outlive the enclosing pool.
- `commandBuffer`, `computeCommandEncoder` and `blitCommandEncoder` are all autoreleased. On a
  per-dispatch path with no pool, those accumulate for the life of the process. Wrap the dispatch
  in `try (var pool = new ObjCRuntime.AutoreleasePool())`.

### 3. Architecture

Written for **arm64**. On Apple silicon every message send goes through `objc_msgSend`, including
ones returning a struct or a float. On x86_64 the ABI splits these:

| return | arm64 | x86_64 |
|---|---|---|
| object / integer | `objc_msgSend` | `objc_msgSend` |
| large struct | `objc_msgSend` (return slot in `x8`) | `objc_msgSend_stret` |
| float / double | `objc_msgSend` | `objc_msgSend_fpret` |

`ObjCRuntime.IS_APPLE_SILICON` records which host this is, and `msgSendStret` is already routed so
there is one place to change if an Intel Mac ever matters. The `double`-returning path
(`GPUStartTime` / `GPUEndTime`) is arm64-correct and would need `_fpret` on x86_64.

## What is left

1. **Verify the foundation.** Smallest useful probe, before touching the backend:
   `MTLCreateSystemDefaultDevice()` → `MetalAPI.deviceName(device)`. If that prints your GPU's
   name, class lookup, selector interning, message sending and `NSString` conversion all work.
2. **Port the backend classes**, mirroring what was done for OpenCL in this same branch — the
   OpenCL commits are the closest model, since the Metal Java layer was cloned from it:
   `Metal`, `MetalPlatform`, `MetalDevice`, `MetalContext`, `MetalCommandQueue`, `MetalProgram`,
   `MetalKernel`, `MetalEvent`.
3. **Delete `metal-jni`**: remove the module from `tornado-drivers/pom.xml` and its dependency from
   `tornado-assembly/pom.xml`, drop `System.loadLibrary` from `Metal.java`, and retarget the
   `tornado.py` diagnostics the way the OpenCL and CUDA ones were.
4. **Check parity**: build a same-commit `develop` SDK, run `tornado-test --quickPass` on both, and
   compare ran/failed/unsupported plus the failing class list. That is how the other two backends
   were signed off, and the numbers are in the PR description.

## Known gaps in `MetalAPI`

- **`addCompletedHandler:` is not covered.** It takes an Objective-C *block*, which is a struct
  with an `isa` field and a function pointer, not a plain callback — building one from a Panama
  upcall stub means laying out `_NSConcreteGlobalBlock` by hand. The synchronous
  `waitUntilCompleted` path is there and matches what the JNI shim did. If async completion is
  wanted, a Java thread parked on `waitUntilCompleted` is far cheaper than a real block.
- **`MTLSize` by value.** `dispatchThreadgroups:threadsPerThreadgroup:` takes two `MTLSize`
  structs *by value*. Panama passes them per the platform ABI when the descriptor names
  `ObjCRuntime.MTL_SIZE`, which is why the wrapper takes `MemorySegment`s and not pointers.
  Allocate with `MTL_SIZE` and write width/height/depth at offsets 0, 8, 16. Verify this one
  early — it is the argument-passing case most likely to be subtly wrong.
- **Struct return.** `maxThreadsPerThreadgroup` returns an `MTLSize` by value and so takes a
  `SegmentAllocator` as its first handle argument (`invoke`, not `invokeExact`).
- **Reflection.** `MetalInstalledCode` uses `MTLComputePipelineReflection` and `MTLArgument` to
  discover threadgroup-memory argument indices. Those are ordinary Objective-C objects, so they
  port the same way, but no wrappers are written for them yet.

## Running it

Native access is granted per module and everything restricted funnels through `tornado.runtime`,
so `bin/tornado` already passes what is needed:

```
--enable-native-access=tornado.runtime
```

If a lookup fails with `IllegalCallerException: Illegal native access from: module ...`, the cause
is a restricted call made from outside `tornado.runtime` — route it through `FFMSupport` rather
than widening the grant. That already happened once during the OpenCL port, with
`Linker::upcallStub`, and the fix was to add a helper rather than add a module to the flag.
