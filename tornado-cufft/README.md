# TornadoVM Hybrid API — cuFFT Library Tasks

NVIDIA cuFFT as TornadoVM library tasks: FFTs share TornadoVM-managed device buffers
with JIT-compiled tasks and run on the same CUDA stream. This is the second library
provider after [tornado-cublas](../tornado-cublas/README.md), added as a self-contained
module pair (`tornado-cufft` + `tornado-drivers/cufft-jni`) with **zero core-runtime
changes** — see `docs/source/hybrid-api.rst` for the provider SPI.

```java
// Forward FFT, then a JIT task consuming the spectrum on the device:
TaskGraph taskGraph = new TaskGraph("fft")
    .transferToDevice(DataTransferMode.EVERY_EXECUTION, signal)
    .libraryTask("fft", CuFft::cufftForwardC2C, signal, spectrum, n, 1)
    .task("postprocess", MyClass::filterSpectrum, spectrum)
    .transferToHost(DataTransferMode.EVERY_EXECUTION, spectrum);
```

## Supported operations

| Factory | cuFFT call | Semantics |
|---|---|---|
| `cufftForwardC2C(in, out, n, batch)` | `cufftExecC2C(FORWARD)` | 1D FP32 complex-to-complex forward, `batch` contiguous transforms of length `n` |
| `cufftInverseC2C(in, out, n, batch)` | `cufftExecC2C(INVERSE)` | 1D FP32 C2C inverse, unnormalized (inverse(forward(x)) = n·x) |
| `cufftForwardR2C(in, out, n, batch)` | `cufftExecR2C` | 1D FP32 real-to-complex: `n·batch` reals in, `(n/2+1)·batch` complex out (Hermitian half) |
| `cufftInverseC2R(in, out, n, batch)` | `cufftExecC2R` | 1D FP32 complex-to-real inverse (Hermitian input), unnormalized |
| `cufftForwardZ2Z` / `cufftInverseZ2Z` | `cufftExecZ2Z` | 1D FP64 complex-to-complex (`DoubleArray`) |
| `cufftForward2dC2C(in, out, nx, ny)` / `cufftInverse2dC2C` | `cufftExecC2C` on a 2D plan | 2D FP32 C2C of an `nx x ny` row-major grid |

Complex data is interleaved `(re, im)`. FFT plans are created once per (transform,
shape), bound to the execution plan's CUDA stream, cached in the per-(device,
execution plan) context, and destroyed when the execution plan closes.

**CUDA Graphs:** supported — plans (whose creation allocates a device work area) are
built in the provider's `prepare()` hook, which the runtime invokes before capture
starts, so the FFT pipeline is captured and replayed like any other library task (see
`TestCuFft#testRoundTripWithCudaGraph`).

Not bound yet (see the cuFFT docs): `cufftPlanMany` advanced strided/embedded layouts,
3D plans, D2Z/Z2D, FP16/BF16 via `cufftXtMakePlanMany`, LTO callbacks
(`cufftXtSetJITCallback`), explicit workspace control (`cufftSetWorkArea`), and the
multi-GPU `cufftXt` API.

## Build & run

```bash
make BACKEND=cuda
source setvars.sh

# Unit tests (skip automatically without CUDA backend / libtornado-cufft)
tornado-test -V uk.ac.manchester.tornado.unittests.cufft.TestCuFft

# Benchmark: sequential Java DFT vs TornadoVM JIT DFT kernel vs cuFFT
# (RTX 4090, n=65536: 228,819 ms vs 63.4 ms vs 0.080 ms -> 793x vs the JIT kernel)
tornado -m tornado.cufft/uk.ac.manchester.tornado.cufft.tests.BenchmarkFft 65536 20

# Example: GPU-only low-pass filter pipeline
# (cuFFT R2C -> JIT kernel zeroing high bins -> cuFFT C2R -> JIT normalize)
tornado -m tornado.cufft/uk.ac.manchester.tornado.cufft.tests.FrequencyFilterExample 4096 16
```
