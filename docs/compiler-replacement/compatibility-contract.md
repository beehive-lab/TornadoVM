# Compatibility contract

Status: **accepted** (user, 2026-10-09).

What TC must reproduce so that the TornadoVM runtime, drivers and user code cannot tell which
compiler produced a kernel. Derived from black-box behaviour and TornadoVM-authored code
(see [compiler-pipeline.md](compiler-pipeline.md) §3–4); generated text is **not** part of the
contract.

## 1. Kernel ABI (CUDA)

| Item | Contract |
|---|---|
| Entry | `extern "C" __global__ void <entry>(…)`; tile kernels `extern "C" __tile_global__`; device-launched kernels `__global__` under a derived name |
| Leading parameters | exactly four, in order: `long long *_kernel_context`, `unsigned char *_constant_region`, `unsigned char *_local_region`, `int *_atomics` |
| Kernel context | 4 × 64-bit slots: 0 = return/exception slot (early return when non-zero if `tornado.exceptions`), 3 = device-launch report flag; under `GridScheduler` with dynamic bounds, loop-bound scalars are read from context slots |
| Local region | placeholder on CUDA; shared memory is `__shared__` declared in the body, sized at compile time |
| Atomics | `AtomicInteger` parameters map to slots in `_atomics`; TC returns the slot map instead of the static map on a Graal node |
| User parameters | Java order; primitives and `HalfFloat` by value; every reference as `unsigned char *`; `KernelContext`, `TileContext`, `AtomicInteger` dropped |
| Names | from the local-variable table; clashes with CUDA built-ins get a `_` prefix |
| Native arrays | header precedes data; element-count at offset 0; header size from `TargetCaps` (measured in `--printKernel` output: `int` element `i` at byte `(i + 4) << 2`, i.e. a 16-byte header) |

OpenCL: same four regions with `__global`, `__constant`, `__local`, `__global` qualifiers and
`__kernel`; `_local_region` is real dynamic local memory. Metal: `[[buffer(i)]]` /
`[[threadgroup(i)]]` attributes plus four trailing `uint3` thread-position parameters.

## 2. Metadata returned to the runtime

| Item | Contract | Gate |
|---|---|---|
| `Access[]` | per parameter READ / WRITE / READ_WRITE / NONE, computed after inlining and API replacement, OR-merged across callees and device-launched kernels (launch cycles force READ_WRITE); primitives NONE | C4c (TC = Graal for every unittest task) |
| Parallel domain | dimensions, ranges, strides as `TornadoShapeAnalysis` reports them | C5b |
| Specialisation | kernels may depend on argument values (folded boxed primitives, array lengths); recompilation follows `task.shouldCompile()`; TC includes the values in its cache key | C5b |
| Batch offset | batched runs add `batchNumber × batchSize` to index values written as data | C6 |

## 3. Numeric semantics

Java semantics unless a TornadoVM option relaxes them (`tornado.enable.fma`,
`tornado.enable.mathOptimizations`, both default on today; TC keeps the same defaults and records
the relaxation in the kernel's remarks).

| Construct | Required behaviour | Known Graal-path issue (issue #1149) |
|---|---|---|
| `>>>` on `int`/`long` | logical shift, count masked to 5/6 bits | yes |
| `<<`, `>>` | count masked | yes (masking) |
| `(int)`/`(long)` of float/double | saturating, NaN → 0 | yes (`(long)float`) |
| `Math.min`/`max` (fp) | NaN propagates; `-0.0 < +0.0` | yes |
| Integer `/`, `%` | truncating toward zero; `MIN_VALUE / -1 = MIN_VALUE` | to test |
| `float` arithmetic | IEEE single, no implicit double promotion | to test |

**Decision (user, 2026-10-09):** where the Graal path deviates from Java semantics, TC implements
Java semantics. The parity matrix records both behaviours and lists the deviation as an accepted
difference, never as a TC regression.

## 4. Diagnostics

| Situation | Graal path today | TC contract |
|---|---|---|
| Unsupported construct | bailout exception, often a Graal stack trace | `TC-E1xxx`/`E3xxx` with Java source line, then fallback to Graal while fallback is on |
| Target capability missing (fp64, fp16, MMA, tile, CDP) | `TornadoDeviceFP64NotSupported` etc. from low-tier phases | same exception types (user code catches them), message gains a `TC-E4xxx` code |
| Internal error | Graal exception | `TC-E9xxx` with a dump of the failing IR when `-Dtornado.tc.dump` is set |

## 5. Pre-existing failures

The seven tests failing on the Graal path at the reference revision
([baseline-tests.md](baseline-tests.md)) are outside the parity requirement. A TC result on them
is reported separately and never counted as a regression.
