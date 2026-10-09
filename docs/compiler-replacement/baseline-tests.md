# Baseline: functional tests (Phase 1)

Reference revision `ae409166a` (beehive-lab `develop`), SDK built with `make BACKEND=opencl,cuda`,
OpenJDK 21.0.2, driver 610.57.04. Command per device:
`docs/compiler-replacement/baseline/run-tests.sh <driver:device> <label> <run>`
(= `tornado-test --verbose -J"-Dtornado.unittests.device=<d>"`). Per-test results:
[baseline/tests-run1.json](baseline/tests-run1.json); summaries from `baseline/summarize.py`.

## Run 1 (2026-10-09)

| Device | Tests | Pass | Unsupported (OpenCL cfg) | Unsupported (CUDA cfg) | Failed | Wall time |
|---|---:|---:|---:|---:|---:|---:|
| CUDA RTX 4090 (`1:0`) | 1671 | 1524 | — | 140 | 7 | 37 min |
| OpenCL RTX 4090 (`0:0`) | 1675 | 1035 | 353 | 280 | 7 | 35 min |
| OpenCL UHD 770 (`0:1`) | 1578 | 940 | 353 | 280 | 5 | 37 min |

"CUDA configuration unsupported" on OpenCL devices are the CUDA-only library and tile tests
(cuBLAS, cuDF, cuDNN, cuFFT, cuRAND, cuSPARSE, CUTLASS, cuVS, NVTX, TileContext).

## Pre-existing failures (Graal reference compiler)

These fail **before** any TC change. They are excluded from parity regressions but tracked: TC
must not be blamed for them, and a TC pass on them is reported separately.

| Test | CUDA 4090 | OCL 4090 | OCL UHD 770 | Reason (run 1) |
|---|---|---|---|---|
| `reductions.TestReductionsFloats#testComputePi` | FAIL | FAIL | pass | expected 3.14, got 6.75 |
| `kernelcontext.matrices.TestMatrixMultiplicationKernelContext#mxm2DKernelContext01` | FAIL | FAIL | FAIL | wrong value |
| `kernelcontext.matrices.TestMatrixMultiplicationKernelContext#mxm2DKernelContext02` | FAIL | FAIL | FAIL | wrong value |
| `compute.ComputeTests#testMandelbrot` | FAIL | FAIL | FAIL | expected 8.0, got 9.0 |
| `compute.ComputeTests#testJuliaSets` | FAIL | FAIL | FAIL | wrong value |
| `compute.TransformerKernelsTest#testReductionOneBlockWithLayer` | FAIL | FAIL | pass | wrong value |
| `api.TestDevices#test06` | FAIL | FAIL | FAIL | `No value present` |

## Run 2 (2026-10-09): determinism

| Device | Result vs run 1 |
|---|---|
| CUDA RTX 4090 | **identical**: 1671 tests, same 1524 passes, same 7 failures |
| OpenCL RTX 4090 | same results for every test that ran; the run stopped early (see below) |

OpenCL run 2 stopped at `memory.leak.TestMemoryLeak#test_no_cached_hot_loop`: the JVM exceeded the
test's 4096 MB monitor limit (4472 MB; it passed in run 1), and `tornado-test`'s kill path then
crashed with `NameError: name 'args' is not defined` (`tornado-test:711`), ending the whole run. Two
pre-existing upstream findings: a memory-sensitive leak test and a runner bug. OpenCL run 2 is
repeated after the phase trace. Per-test results: [baseline/tests-run2.json](baseline/tests-run2.json).
