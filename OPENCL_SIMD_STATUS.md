# OpenCL SIMD Sub-Group Support — Handoff Document

## Branch

`feature/opencl-simd-shuffle` (branched from `feature/ptx-simd-shuffle`)

## What's Done

OpenCL backend support for `KernelContext.simdShuffleDown`, `simdSum`, and `simdBroadcastFirst` using the `cl_khr_subgroups` extension. The implementation is complete and compiles cleanly.

### Mapping

| KernelContext API         | OpenCL Function                  |
|---------------------------|----------------------------------|
| `simdShuffleDown(val, d)` | `sub_group_shuffle_down(val, d)` |
| `simdBroadcastFirst(val)` | `sub_group_broadcast(val, 0)`    |
| `simdSum(val)`            | `sub_group_reduce_add(val)`      |

### Files Created (3 new Graal IR nodes)

| File | Description |
|------|-------------|
| `tornado-drivers/opencl/.../nodes/OCLSubGroupReduceAddNode.java` | `FixedWithNextNode` -> `AssignStmt(result, OCLUnary.Intrinsic(SUB_GROUP_REDUCE_ADD, val))` |
| `tornado-drivers/opencl/.../nodes/OCLSubGroupShuffleDownNode.java` | `FixedWithNextNode` -> `AssignStmt(result, OCLBinary.Intrinsic(SUB_GROUP_SHUFFLE_DOWN, val, delta))` |
| `tornado-drivers/opencl/.../nodes/OCLSubGroupBroadcastNode.java` | `FixedWithNextNode` -> `AssignStmt(result, OCLBinary.Intrinsic(SUB_GROUP_BROADCAST, val, 0))` |

### Files Modified (6)

| File | Change |
|------|--------|
| `OCLAssembler.java` | Added `SUB_GROUP_REDUCE_ADD` (unary), `SUB_GROUP_SHUFFLE_DOWN` + `SUB_GROUP_BROADCAST` (binary) intrinsic constants. Conditional pragma: `#pragma OPENCL EXTENSION cl_khr_subgroups : enable` |
| `OCLTargetDescription.java` | Added `supportsSubgroups` field + `supportsSubgroups()` method (checks `extensions.contains("cl_khr_subgroups")`) |
| `OCLHotSpotBackendFactory.java` | Threads `OCLTargetDescription target` through `createGraphBuilderPlugins()` -> `registerInvocationPlugins()` |
| `OCLGraphBuilderPlugins.java` | Added `registerSIMDPlugins(Registration r)` with 3 `InvocationPlugin`s. Only registered when `target.supportsSubgroups()` is true |
| `TestSIMDGroupReductions.java` | Tests still have `assertNotBackend(OPENCL)` — needs removal once verified on a device with `cl_khr_subgroups` |
| `KernelContext.java` | Updated javadoc with OpenCL equivalents |

### Conditional Check Chain

1. `OCLTargetDescription` reads device extensions at init -> sets `supportsSubgroups`
2. `OCLHotSpotBackendFactory` passes `target` to plugin registration
3. `OCLGraphBuilderPlugins.registerKernelContextPlugins()` only calls `registerSIMDPlugins(r)` if `target.supportsSubgroups()` is true
4. `OCLAssembler` constructor only emits the pragma if `target.supportsSubgroups()`

On devices without `cl_khr_subgroups` (e.g. NVIDIA OpenCL), plugins are never registered and no sub-group code is generated.

## What Needs Testing on Apple (Metal machine with OpenCL)

### 1. Verify extension support

```bash
clinfo | grep -i subgroup
```

Expect `cl_khr_subgroups` in device extensions.

### 2. Build

```bash
make BACKEND=opencl
```

### 3. Remove the test exclusion

In `TestSIMDGroupReductions.java`, temporarily remove all 5 occurrences of:
```java
// OpenCL support requires cl_khr_subgroups (available on Intel/AMD/ARM, not NVIDIA)
assertNotBackend(TornadoVMBackendType.OPENCL);
```

Then rebuild:
```bash
make BACKEND=opencl
```

### 4. Run tests

```bash
source setvars.sh
tornado-test --threadInfo --printKernel -V \
  uk.ac.manchester.tornado.unittests.kernelcontext.reductions.TestSIMDGroupReductions
```

Expected:
- All 5 tests PASS
- `--printKernel` output shows `sub_group_reduce_add(...)`, `sub_group_shuffle_down(...)`, `sub_group_broadcast(..., 0)` in the generated OpenCL C

### 5. If tests pass

- Remove the `assertNotBackend(OPENCL)` lines permanently (keep only `assertNotBackend(SPIRV)`)
- Update the test javadoc to say OpenCL is supported
- Commit

### 6. Optional: run the benchmark

```bash
tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.SIMDReductionComparison
```

## What Was Already Verified

- Code **compiles cleanly** (`make BACKEND=opencl` succeeds)
- Generated kernels contain correct OpenCL sub-group function calls (verified via `--printKernel` on NVIDIA, where they're syntactically correct but unsupported at runtime)
- Tests **skip gracefully** on NVIDIA OpenCL (reports `OPENCL CONFIGURATION UNSUPPORTED`, 0 failures)
- PTX backend SIMD support is fully working (branch `feature/ptx-simd-shuffle`, all 5 tests pass, 1.3-1.4x speedup over threadgroup memory)
