# Testing CUDA Unified Memory on Grace-Hopper

This document explains how to exercise the CUDA Unified Memory (UM) / zero-copy
feature of the TornadoVM CUDA backend on a **Grace-Hopper (GH200)** system, and
what results to expect.

## What the feature does

When Unified Memory is active for a plan, the CUDA backend backs each
`TornadoNativeArray` (e.g. `FloatArray`, `HalfFloatArray`, `Int8Array`) with the
array's own off-heap host segment, pinned and mapped into the device address
space via `cuMemHostRegister`. The kernel then reads/writes that memory directly,
so the host→device and device→host **copies are eliminated**.

This is only a win where the GPU reaches host memory *coherently* without a
discrete PCIe hop. The backend therefore gates the zero-copy path on hardware:

| Hardware | `withCudaUM()` behaviour |
|---|---|
| **Grace-Hopper (GH200)** — NVLink-C2C, ATS | **zero-copy** (no copies), automatic |
| Integrated / Jetson | zero-copy, automatic |
| Discrete PCIe (e.g. RTX 4090, A100-PCIe) | managed memory **with copies** (zero-copy disabled; it would be slower over PCIe) |

Detection uses `CU_DEVICE_ATTRIBUTE_INTEGRATED` or
`CU_DEVICE_ATTRIBUTE_PAGEABLE_MEMORY_ACCESS_USES_HOST_PAGE_TABLES` (true ATS, as on
Grace-Hopper). Plain `PAGEABLE_MEMORY_ACCESS` is intentionally **not** used — with
HMM it reads `1` on a modern discrete GPU even though access still crosses PCIe.

## Prerequisites

- A Grace-Hopper GH200 node with the NVIDIA driver and CUDA toolkit installed.
- TornadoVM built with the CUDA backend:
  ```bash
  make cuda
  source setvars.sh
  ```

## Enabling Unified Memory

There is nothing Grace-Hopper-specific to switch on: activating UM is enough, and
zero-copy turns on automatically because the coherent-host gate passes.

### Per-plan (preferred, in application code)

```java
try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
    plan.withCudaUM().execute();   // -> zero-copy automatically on Grace-Hopper
}
```

### Global flag (no code change)

```bash
# unit tests
tornado-test -V -J"-Dtornado.cuda.memory.unified=true" \
    uk.ac.manchester.tornado.unittests.api.TestCUDAUnifiedMemory

# examples / benchmarks
tornado --jvm="-Dtornado.cuda.memory.unified=true" \
    -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixVectorUnifiedMemory 16384 16384 40
```

## Benchmarks (already call `withCudaUM()` internally)

```bash
# Profiler comparison: default (copy) vs withCudaUM (zero-copy on GH)
tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixVectorUnifiedMemory 16384 16384 40

# Over-subscription: working set larger than VRAM
tornado --jvm="-Dtornado.device.memory=200GB -XX:MaxDirectMemorySize=200g" \
    -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixVectorOversubscription um
```

For over-subscription, raise both caps above the working set: `tornado.device.memory`
(TornadoVM's accounting limit) and `-XX:MaxDirectMemorySize` (JVM off-heap limit).

## What to expect on Grace-Hopper

### Confirming zero-copy is active

1. **No** `[CUDA-UM] Zero-copy disabled on this discrete GPU ...` message. That line
   prints only when the gate falls back to the copy path (i.e. on a discrete GPU).
   Its absence means zero-copy is on.
2. In `MatrixVectorUnifiedMemory`, the UM column shows **Copy-in time ≈ 0** and
   **Bytes copy-in → 24** (just the header), versus a full ~1 GB copy on the default
   path.

### Performance expectation

On Grace-Hopper the kernel accesses host memory over **NVLink-C2C (~900 GB/s,
coherent)**, so eliminating the copies is a net win and the UM kernel time stays
low. Contrast with a **discrete PCIe GPU**, where the same zero-copy path makes the
kernel stream over PCIe (~25–30 GB/s, fine-grained) and is much slower — which is
exactly why the backend disables zero-copy on discrete hardware.

Reference numbers measured on a **discrete RTX 4090 (23.5 GB, PCIe4)** with the
1 GB matrix-vector benchmark (`EVERY_EXECUTION`), to illustrate the contrast:

| Metric | Default (copy) | withCudaUM, discrete (managed+copy) | withCudaUM, **forced** zero-copy on discrete |
|---|---|---|---|
| Copy-in time | 67 ms | 70 ms | **0.014 ms** (eliminated) |
| Kernel time | 6.6 ms | 6.6 ms | **1056 ms** (PCIe-bound) |
| Wall time | 74 ms | 77 ms (+4%) | 1057 ms (~14× slower) |

On Grace-Hopper the "forced zero-copy" column's kernel cost collapses (NVLink vs
PCIe), so the zero-copy path is expected to **beat** the copy path rather than lose
to it. Copies are eliminated *and* the kernel runs fast.

### Over-subscription

The over-subscription demo runs a working set larger than VRAM:

- `... MatrixVectorOversubscription um` — succeeds; data lives in host RAM and the
  GPU pages it over the interconnect (NVLink on GH).
- `... MatrixVectorOversubscription default` — fails fast with a clean
  `TornadoOutOfMemoryException` (run in a separate process; a failed `cuMemAlloc`
  leaves the CUDA context unusable).

## Mechanism note (zero-copy vs managed page-migration)

The implemented path is **zero-copy** (pinned + mapped host memory): the kernel
reads host RAM *in place*. This is distinct from `cudaMallocManaged` **page
migration**, where pages migrate to VRAM on first touch and the kernel then runs at
VRAM speed. The two are different mechanisms (see Fumero et al., *"Unified Shared
Memory: Friend or Foe?"*, MPLR'23). On Grace-Hopper the distinction matters less
because host access is already coherent and fast over NVLink-C2C.

## Troubleshooting

- **Zero-copy not engaging on GH** (you see the "disabled" notice): force it with
  ```bash
  -Dtornado.cuda.memory.zerocopy.force=true
  ```
  and please report the device attributes — on a correctly configured GH200,
  `CU_DEVICE_ATTRIBUTE_PAGEABLE_MEMORY_ACCESS_USES_HOST_PAGE_TABLES` reports `1` and
  the gate passes automatically.
- **Out-of-memory on large working sets**: raise `-Dtornado.device.memory=<X>GB`
  and `-XX:MaxDirectMemorySize=<X>g` above the total array footprint.

## Flags summary

| Flag | Effect |
|---|---|
| `withCudaUM()` (API) | Enable UM for that execution plan |
| `-Dtornado.cuda.memory.unified=true` | Enable UM globally (all plans) |
| `-Dtornado.cuda.memory.zerocopy.force=true` | Force zero-copy even on discrete (testing) |
| `-Dtornado.device.memory=<X>GB` | TornadoVM device-memory accounting cap |
| `-XX:MaxDirectMemorySize=<X>g` | JVM off-heap (host segment) cap |
