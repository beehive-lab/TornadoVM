# Gate results

Each gate is go/no-go. Evidence and the profiling decision review are recorded per gate.

| Gate | Status | Date | Evidence |
|---|---|---|---|
| C0 repo + builds + devices | **passed** | 2026-10-09 | private repo (`gh repo view` → PRIVATE); `make BACKEND=opencl,cuda` on JDK 21.0.2 and `make jdk25 BACKEND=opencl,cuda` on JDK 25.0.2: BUILD SUCCESS; `tornado --devices` (both): 0:0 OpenCL RTX 4090, 0:1 OpenCL UHD 770, 1:0 CUDA RTX 4090 |
| C1 baseline reproducible | pending | | |
| C2 ⚑ dependency + phase matrix | pending | | |
| C3 ⚑ end-to-end design test | **design test passed**; debug-infra part (JFR events, `-Dtornado.tc.dump`, HTML report) pending; user review pending | 2026-10-09 | 3/3 kernels (grid-stride vector add, if/else diamond with block-argument merge and `sqrt`, KernelContext shared-memory tree reduction) compiled by TC only and run on the RTX 4090 via the prebuilt-task path, results equal to Java references; TC compile 4–21 ms per kernel; outputs in [c3/](c3/) |
| C4a/b/c frontend, passes, Access[] | pending | | |
| C5a/b ⚑ structurizer, vertical slice | pending | | |
| C6 differential parity | pending | | |
| C7 performance + stability | pending | | |
| C8 Graal removed | pending | | |
| C10 Babylon adapter | pending | | |

## C3 notes (2026-10-09)

Run: `tornado -cp <tc classes> uk.ac.manchester.tornado.tc.gpu.DesignTest --params "1:0"` (module
`tornado-compiler/tc-gpu`). Pipeline per kernel: parse → verify (pre-ABI) → ABI lowering → verify
(post-ABI) → structure → emit CUDA → NVRTC through TornadoVM's prebuilt path.

| Kernel | Result | TC compile | First run (NVRTC + launch) |
|---|---|---:|---:|
| vector-add (grid-stride, 2 iterations/thread) | PASS | 21.4 ms (includes TC class warm-up) | 81.3 ms |
| branchy (diamond, block argument, `sqrtf`) | PASS | 4.0 ms | 13.8 ms |
| block-reduce (`__shared__`, `__syncthreads`, tree loop) | PASS | 4.7 ms | 14.6 ms |

**Finding (ABI):** the first vector-add run read the loop bound from the header of the write-only
output array and got 0: on the device, the header of an array that is not copied to the device is not
initialised. The Graal path never reads it because it folds array lengths into constants during task
specialisation. Consequence for TC: T1 (value specialisation of lengths) is required before the
frontend path goes live, and until then TC must not read the header of an array with no
host-to-device transfer. Recorded in `compatibility-contract.md` §1.

**Design decisions confirmed by C3:** block-argument SSA lowers to clean C (`while` loops, merges as
plain assignments); `array<T>` until ABI lowering keeps the frontend-facing IR readable and the ABI in
one pass; the four-region kernel signature and 16-byte header match the runtime.
