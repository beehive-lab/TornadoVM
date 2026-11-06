# Consuming host storage from unified memory

On devices that share memory with the host, a task graph can read an existing native array
in place instead of copying it into a device buffer. This suits large read-only inputs, such
as model weights, that would otherwise exist twice.

Use `consumeFromUnifiedMemory` for eligible host-backed TornadoVM arrays. Use
`bindDeviceBuffer` for GPU buffers owned by another library, including shared Metal buffers
that the CPU can access. These are separate API contracts: shared physical memory does not
change which API applies. A device view exposes an existing device buffer to kernels, not a
host `MemorySegment` to the consumption API.

```java
TornadoDevice device = TornadoRuntimeProvider.getTornadoRuntime().getDefaultDevice();

TaskGraph graph = new TaskGraph("inference");
if (device.isUnifiedMemorySupported()) {
    graph.consumeFromUnifiedMemory(weights);
} else {
    graph.transferToDevice(DataTransferMode.FIRST_EXECUTION, weights);
}
graph.transferToDevice(DataTransferMode.EVERY_EXECUTION, input)
        .task("infer", Kernels::infer, weights, input, output)
        .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
```

`consumeFromUnifiedMemory(Object...)` is the unified-memory counterpart of
`consumeFromDevice`: it prevents the upload, and its source is the caller's allocation
rather than another graph's device buffer. Ordinary transfers of other objects are unchanged.

## Queries

- `TornadoDevice.isUnifiedMemorySupported()` returns true only when the device shares memory
  with the host and its backend implements the feature.
- `TornadoDevice.isOnUnifiedMemory(array)` returns true when, in addition, the array's storage
  meets every requirement below, so this device can consume that array in place. It binds
  nothing and does not check task accesses or the execution mode.
  For a device view it returns `false` because views are excluded from the consumption API,
  even when the underlying buffer uses shared Metal storage. This result does not establish
  whether that foreign buffer is physically shared or CPU-accessible.

A graph whose device or storage fails these checks is rejected when the plan compiles with
`TornadoUnifiedMemoryNotSupported`, whose message names the requirement that is not met.

| Backend | `isUnifiedMemorySupported()` | Mechanism |
| --- | --- | --- |
| Metal | true on devices with unified memory (Apple silicon) | `newBufferWithBytesNoCopy` shared buffer over the caller's pages |
| CUDA | false | Not implemented yet; the device and allocation hooks are in place |
| OpenCL and others | false | Not supported |

TornadoVM never emulates unified memory with driver-managed page migration: on hardware
without real shared memory, use ordinary transfers.

## Storage requirements

Checked by `isOnUnifiedMemory` and when the plan compiles:

- The object is a host-backed `TornadoNativeArray`, and every task only reads it. Device views
  are refused; bind their existing device buffers with `bindDeviceBuffer` instead.
- Its memory is owned by `Arena.ofAuto()`. The device buffer keeps the segment reachable,
  so the memory cannot be freed while a device uses it. Closeable arenas and foreign or
  global segments are refused.
- Metal: the memory is a mapped file (for example `FileChannel.map(..., Arena.ofAuto())`),
  or it starts on a page boundary and contains whole pages. Metal maps whole pages, and
  TornadoVM cannot prove the page-rounded extent of other allocations. The rounded extent
  must lie in one readable VM region and fit the device's maximum buffer length.

The automatic-arena check recognises the JDK's `jdk.internal.foreign.ImplicitSession`
scope; unknown implementations are refused. Revalidate this when changing JDKs.

## Lifetime and access

The storage, including the array header, must stay unchanged from the first execution until
every consuming plan is closed. CPU reads are allowed; the caller must prevent writes through
other aliases. Plan closure waits for device work and releases the device binding, never the
caller's storage. Each consuming graph creates its own binding.

## Restrictions

Within a plan, these conflict with the declaration: transfers or persistence of the same
array, consumption through another graph's device buffer, device writes, and ordinary buffers
that overlap the consumed bytes. Memory chunking (`withBatch`), reduction rewriting,
execution-graph capture, intra-plan concurrency and per-task device selection are not
supported; a graph must use one device. Errors never fall back to copying or to sequential
Java.

The overlap check covers host storage only. A device view exposes no host `MemorySegment`
to compare, so the caller must ensure that the device buffer bound to a view does not overlap
consumed bytes, including when the buffer uses shared Metal storage.

## Accounting

`XPUBuffer.size()` remains the logical binding size. `getAllocatedSize()` and
`TornadoExecutionPlan.getCurrentDeviceMemoryUsage()` exclude consumed storage, which belongs
to the caller and still occupies system memory.

## Tests

```sh
tornado-test -V uk.ac.manchester.tornado.unittests.memory.TestConsumeFromUnifiedMemory
```

On backends without support, the tests that need unified memory report as unsupported, and
`testUnsupportedDeviceRejected` checks the compile-time rejection.
