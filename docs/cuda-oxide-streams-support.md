# cuda-oxide Stream Support: What Exists and How It Works

**Purpose.** This note records what cuda-oxide currently supports around CUDA streams, based on its local documentation and implementation under `~/cuda-oxide`. It is descriptive only: it does not propose TornadoVM changes.

**Sources checked.**

- Public docs: `https://nvlabs.github.io/cuda-oxide/async-programming/scheduling-and-streams.html` and `https://nvlabs.github.io/cuda-oxide/async-programming/concurrent-execution.html`.
- Local docs: `cuda-oxide-book/async-programming/the-device-operation-model.md`, `scheduling-and-streams.md`, `concurrent-execution.md`, `projects/async-mlp-pipeline.md`, and `gpu-programming/memory-and-data-movement.md`.
- Local code: `crates/cuda-core/src/{context,stream,event,memory,device_buffer,pinned_host_buffer}.rs` and `crates/cuda-async/src/{device_context,scheduling_policies,device_operation,device_future,device_box,launch}.rs`.

---

## 1. Layering

cuda-oxide has two relevant host-side layers:

1. **`cuda-core`** exposes explicit CUDA context, stream, event, memory, transfer, and kernel-launch wrappers.
2. **`cuda-async`** builds an async scheduling layer on top of `cuda-core` with lazy `DeviceOperation`s, scheduling policies, `DeviceFuture`, and async-friendly device memory (`DeviceBox`).

The split matters:

- In `cuda-core`, callers generally pass an explicit `CudaStream`.
- In `cuda-async`, operations are stream-agnostic until a `SchedulingPolicy` chooses a stream at execution time.

---

## 2. Explicit stream support in `cuda-core`

### 2.1 Context ownership and thread binding

`CudaContext` retains the CUDA **primary context** for a device ordinal using `cuDevicePrimaryCtxRetain` and releases it on drop. Driver calls are thread-local with respect to the current CUDA context, so `CudaContext::bind_to_thread()` checks the current context and calls `cuCtxSetCurrent` when needed.

Relevant code:

- `CudaContext::new(ordinal)` creates the context and binds it.
- `CudaContext::bind_to_thread()` is called by stream/event/module operations before driver calls.
- `CudaContext::default_stream()` returns stream 0.
- `CudaContext::new_stream()` creates a new non-blocking stream with `CU_STREAM_NON_BLOCKING`.

### 2.2 `CudaStream`

`CudaStream` is an RAII wrapper around `CUstream`. It stores:

- the raw `CUstream`;
- an `Arc<CudaContext>` so the owning context outlives the stream.

Supported operations:

- `cu_stream()` returns the raw `CUstream`.
- `context()` returns the owning context.
- `synchronize()` calls `cuStreamSynchronize`.
- `fork()` creates a new non-blocking stream and makes it wait on the parent stream's current position.
- `join(other)` records an event on `other` and waits on it from `self`.
- `record_event(flags)` creates and records a `CudaEvent` on this stream.
- `wait(event)` calls `cuStreamWaitEvent`.
- `launch_host_function(f)` enqueues a `cuLaunchHostFunc` callback after prior work in the stream.

The ordering model is conventional CUDA:

- operations on the same stream are FIFO ordered;
- operations on different streams may overlap;
- cross-stream ordering is explicit through events, `fork`, or `join`.

### 2.3 Default stream vs non-blocking streams

`CudaContext::default_stream()` returns a `CudaStream` with a null `CUstream` pointer, representing CUDA stream 0.

`CudaContext::new_stream()` and `CudaStream::fork()` create streams with `CU_STREAM_NON_BLOCKING`. The docs state that non-blocking streams avoid implicit synchronization with the default stream, and the code uses that flag directly.

### 2.4 Event support

`CudaEvent` wraps `CUevent` and keeps an `Arc<CudaContext>`.

Supported operations:

- `CudaContext::new_event(flags)` creates an event.
- `CudaEvent::record(stream)` records it on a stream.
- `CudaEvent::synchronize()` calls `cuEventSynchronize`.
- `CudaEvent::query()` wraps `cuEventQuery`.
- `CudaEvent::elapsed_ms(end)` synchronizes both events and queries elapsed time.

Default event creation uses `CU_EVENT_DISABLE_TIMING` when `flags` is `None`; timing must be requested explicitly with e.g. `CU_EVENT_DEFAULT`.

### 2.5 Fork/join implementation

`CudaStream::fork()`:

1. creates a new non-blocking stream;
2. calls `stream.join(self)`;
3. returns the new stream.

`CudaStream::join(other)`:

1. records an event on `other`;
2. calls `self.wait(event)`.

So fork/join is implemented with ordinary CUDA events (`cuEventRecord` + `cuStreamWaitEvent`), not with host synchronization.

---

## 3. Memory, transfer, and stream-ordered operations

### 3.1 Raw memory API

`cuda-core/src/memory.rs` exposes both stream-ordered and synchronous primitives:

- `malloc_async(stream, bytes)` wraps `cuMemAllocAsync`.
- `free_async(ptr, stream)` wraps `cuMemFreeAsync`.
- `malloc_sync(bytes)` wraps `cuMemAlloc`.
- `free_sync(ptr)` wraps `cuMemFree`.
- `memcpy_htod_async(dst, src, bytes, stream)` wraps `cuMemcpyHtoDAsync`.
- `memcpy_dtoh_async(dst, src, bytes, stream)` wraps `cuMemcpyDtoHAsync`.
- `memcpy_dtod_async(dst, src, bytes, stream)` wraps `cuMemcpyDtoDAsync`.
- `memset_d8_async(ptr, value, bytes, stream)` wraps `cuMemsetD8Async`.
- `malloc_host(bytes)` / `free_host(ptr)` wrap `cuMemAllocHost` / `cuMemFreeHost`.

The async raw functions enqueue work on the supplied stream and return immediately. Their safety comments explicitly require pointers and host buffers to remain valid until the stream reaches the operation.

### 3.2 `DeviceBuffer<T>`

`DeviceBuffer<T>` is the explicit `cuda-core` device-memory owner. It does **not** store a stream for normal operation; stream parameters are explicit on transfer methods.

Important behavior:

- `from_host(stream, data)` allocates device memory, enqueues H2D, then synchronizes the stream before returning. This is safe for borrowed host slices.
- `from_host_async_unchecked(stream, data)` allocates and enqueues H2D without synchronizing. It is unsafe because the host slice must outlive the in-flight copy.
- `from_pinned_host(stream, pinned)` enqueues H2D from a `PinnedHostBuffer` without synchronizing. It is unsafe for the same lifetime reason.
- `to_host_vec(stream)` enqueues D2H and synchronizes before returning the host vector.
- `copy_to_host(stream, dst)` enqueues D2H and synchronizes before returning.
- `copy_to_pinned_host_async(stream, dst)` enqueues D2H to pinned host memory and returns without synchronizing.
- `copy_from_pinned_host_async(stream, src)` enqueues H2D from pinned host memory into an existing device buffer without synchronizing.
- `uninitialized_async(stream, len)` allocates with `cuMemAllocAsync`; the buffer records the owning stream for matching async free on drop.
- `drop_async(stream)` explicitly frees with `cuMemFreeAsync`.

So cuda-oxide supports both blocking safe transfer helpers and non-blocking helpers, but non-blocking host transfers are marked unsafe unless ownership/lifetime is encoded elsewhere.

### 3.3 Pinned host buffers

`PinnedHostBuffer<T>` owns page-locked host memory allocated with `cuMemAllocHost`.

Supported operations:

- `zeroed(ctx, len)`;
- `from_slice(ctx, data)`;
- slice accessors and raw pointer accessors;
- free with `cuMemFreeHost` on drop.

The docs and code comments state pinned memory is useful for higher transfer bandwidth and is required for host-device copies intended to overlap with GPU work. The pinned buffer is associated with the creating `CudaContext`; debug assertions check context equality when used with stream transfer helpers.

---

## 4. Kernel launch on streams

### 4.1 Explicit launches

The sync/explicit path uses stream-aware launch helpers from `cuda-core`. `cuda-async/src/launch.rs` shows `AsyncKernelLaunch::launch(stream)` eventually calling one of:

- `launch_kernel_on_stream`;
- `launch_kernel_cooperative_on_stream`;
- `launch_kernel_ex_on_stream`;
- `launch_kernel_ex_cooperative_on_stream`.

The selected helper depends on whether cluster dimensions and/or cooperative launch mode are set.

### 4.2 Lazy async launches

`AsyncKernelLaunch` implements `DeviceOperation`. It stores:

- the CUDA function;
- boxed kernel argument storage;
- launch config;
- optional cluster dimensions;
- cooperative-launch flag.

It does not choose a stream when constructed. Its `execute(context)` path uses the stream inside `ExecutionContext`.

---

## 5. `cuda-async`: stream-agnostic operations

### 5.1 `DeviceOperation`

`DeviceOperation` is the core abstraction for lazy async GPU work. Code and docs agree on the core contract:

- a `DeviceOperation` describes work without binding to a stream;
- scheduling provides an `ExecutionContext`;
- `ExecutionContext` carries the device id, `Arc<CudaStream>`, and `Arc<CudaContext>`;
- `execute(context)` submits work to the assigned stream;
- GPU work may still be in flight when `execute` returns.

Execution methods:

- `schedule(policy)` pairs the operation with a policy-selected stream and returns `DeviceFuture`.
- `.sync()` uses the default device policy and blocks until the chosen stream is idle.
- `.sync_on(stream)` executes on a caller-provided stream and synchronizes it.
- `unsafe .async_on(stream)` executes on a caller-provided stream without synchronizing.
- `.await` works through the `IntoFuture` implementations, which schedule through the thread-local default policy.

### 5.2 Combinators and ordering

Implemented combinators include:

- `and_then`: run operation A, pass its output to a closure that returns operation B, then run B.
- `and_then_with_context`: like `and_then`, but the closure also receives `ExecutionContext`.
- `arc`: wrap the output in `Arc<T>` for sharing.
- `value`: return a host-side value without GPU work.
- `with_context`: defer construction until the stream/context is known.
- `zip!`: combine one, two, or three operations into a tuple result.

Important code-confirmed ordering:

- `AndThen` executes the second operation on the **same `ExecutionContext`**, therefore the same stream.
- `Zip` currently executes its operands sequentially on the **same stream** in code (`a.execute(context)` then `b.execute(context)`). The docs describe `zip!` as bundling independent operations, but this implementation does not schedule zipped operands onto separate streams.
- `Select`/`unzip` internals assume execution is sequential within a single stream; comments explicitly warn that concurrent calls from different threads would race on the internal `UnsafeCell`s.

### 5.3 Consequence

A dependent pipeline built from `and_then` is submitted as one `DeviceOperation`. The scheduling policy chooses one stream for the whole pipeline, and all stages run on that stream. This matches the public docs' guidance that an `and_then` chain preserves strict stage ordering without cross-stream synchronization.

Independent pipelines must be separate scheduled operations/futures if they are to land on different streams.

---

## 6. Scheduling policies and stream pools

### 6.1 `SchedulingPolicy`

The trait has three methods:

- `init(ctx)` creates streams or initializes policy state;
- `schedule(op)` assigns a stream and returns a `DeviceFuture`;
- `sync(op)` assigns a stream, executes the operation, and blocks until idle.

Implementations must be `Sync`.

### 6.2 `StreamPoolRoundRobin`

`StreamPoolRoundRobin` stores:

- `device_id`;
- `next_stream_idx: AtomicUsize`;
- `num_streams`;
- `stream_pool: Option<Vec<Arc<CudaStream>>>`.

Behavior:

- `init(ctx)` creates `num_streams` CUDA streams using `ctx.new_stream()`.
- `schedule(op)` chooses `idx = next_stream_idx.fetch_add(1, Relaxed) % num_streams` and returns a `DeviceFuture` bound to that stream.
- `sync(op)` chooses the next stream the same way and calls `op.sync_on(&pool[idx])`.

The default number of streams is `DEFAULT_ROUND_ROBIN_STREAM_POOL_SIZE = 4` in `device_context.rs`.

### 6.3 `SingleStream`

`SingleStream` is implemented in `scheduling_policies.rs`:

- `init(ctx)` creates one stream;
- `schedule(op)` binds every operation to that stream;
- `sync(op)` executes on that stream.

However, `GlobalSchedulingPolicy` currently has only one variant: `RoundRobin(StreamPoolRoundRobin)`. The local docs also note that `SingleStream` is implemented in scheduling internals but not wired into `GlobalSchedulingPolicy` or exposed through `init_device_contexts`.

### 6.4 Device context storage

`cuda-async/src/device_context.rs` uses thread-local `DEVICE_CONTEXTS`.

Each `AsyncDeviceContext` contains:

- `CudaContext`;
- `GlobalSchedulingPolicy`;
- dedicated deallocator stream;
- kernel function cache.

Important behavior:

- `init_device_contexts(default_device_id, num_devices)` initializes the thread-local device map and may be called at most once per thread.
- Device contexts are lazily initialized on first use.
- `init_with_default_policy` constructs `StreamPoolRoundRobin` with the default stream count of four.
- `with_default_device_policy` returns the default device's policy for `IntoFuture` implementations.

The public docs describe `init_device_contexts(0, 1)` as setting up round-robin scheduling with four streams by default; the code confirms that default.

---

## 7. `DeviceFuture` completion model

`DeviceFuture` bridges CUDA stream completion to Rust `Future`.

States:

- `Idle`: operation not yet submitted;
- `Executing`: submitted and waiting for callback;
- `Complete`: result delivered or terminal state;
- `Failed`: constructed in failed state.

On first poll:

1. it registers the async task's waker;
2. executes the stored `DeviceOperation` on the assigned stream;
3. enqueues a `cuLaunchHostFunc` callback on the same stream;
4. returns `Poll::Pending`.

When CUDA reaches the host function callback, the callback sets an atomic `complete` flag and wakes the task. A later poll returns the stored result.

Cancellation/drop behavior:

- dropping a future does not cancel submitted GPU work;
- if a submitted result has not been delivered, the future records a CUDA event on the assigned stream and parks the result in a reclaim queue;
- a later sweep drops parked results only after the event reports completion;
- if event recording fails, cleanup falls back to stream synchronization; if that also fails, it leaks the result rather than dropping resources that the GPU may still use.

---

## 8. Concurrent execution model shown by docs/examples

The public and local docs describe a concurrent MLP scenario:

- weights are uploaded once and shared as `Arc<DeviceBox<[f32]>>`;
- each batch builds a lazy pipeline using `DeviceOperation` combinators;
- each batch pipeline is converted to a future with `.into_future()`;
- `tokio::spawn` runs each future independently;
- the round-robin scheduler assigns batch 0 to stream 0, batch 1 to stream 1, etc.;
- each pipeline's internal stages remain ordered on its assigned stream;
- concurrency comes from multiple independent pipelines being scheduled separately.

The code supports this model through:

- stream-agnostic `DeviceOperation`;
- round-robin stream assignment in `StreamPoolRoundRobin::schedule`;
- `AndThen` preserving one stream across a dependent chain;
- `DeviceFuture` using a stream callback rather than blocking a host thread.

The docs also state concurrency is hardware-dependent: overlap is useful when the GPU has spare SMs/copy capacity; very large kernels that saturate the GPU may not benefit.

---

## 9. Async-friendly device memory

`DeviceBox<T>` in `cuda-async` is an owning wrapper around device memory intended for async pipelines.

Behavior:

- stores device id, raw `CUdeviceptr`, and length;
- frees via `cuMemFreeAsync` on a dedicated per-device deallocator stream on drop;
- exposes raw pointer and typed `DevicePointer<T>` for kernel arguments.

Important safety contract:

- all streams that reference a `DeviceBox` allocation must be synchronized before the box is dropped;
- the drop path enqueues async free but cannot prove all user streams are done.

`DeviceFuture`'s deferred reclamation logic helps with futures dropped while work is in flight, but the `DeviceBox` type itself still documents the synchronization requirement.

---

## 10. What cuda-oxide does not appear to provide in this code

The following are not present in the inspected implementation:

- No automatic DAG analysis that splits one `DeviceOperation` across multiple streams.
- No automatic cross-stream scheduling inside `AndThen`; dependent chains stay on one stream.
- No code-confirmed parallel execution of operands inside `zip!`; the current `Zip` implementation executes operands sequentially on one stream.
- No exposed global `SingleStream` policy through `GlobalSchedulingPolicy`; it is implemented but not wired as a global policy variant.
- No automatic pinned staging-ring abstraction was found. There are pinned host buffers and unsafe async pinned transfer methods, but not a higher-level ring scheduler in the inspected files.
- No evidence that pageable host transfers automatically become truly overlapped; comments state pinned/page-locked host memory is needed for guaranteed asynchronous overlap.

---

## 11. Summary

cuda-oxide supports streams at two levels:

- **Manual/explicit:** `CudaContext`, `CudaStream`, `CudaEvent`, explicit stream arguments for transfers and launches, `fork`/`join`, and host callbacks.
- **Automatic scheduling:** lazy `DeviceOperation`s scheduled onto a default four-stream round-robin pool, with `DeviceFuture` using `cuLaunchHostFunc` for non-blocking async completion.

The key stream design pattern is:

1. Keep a dependent pipeline on one assigned stream.
2. Submit independent pipelines as separate futures.
3. Let `StreamPoolRoundRobin` distribute those independent pipelines across streams.
4. Use events/fork/join only when explicit cross-stream ordering is needed.
