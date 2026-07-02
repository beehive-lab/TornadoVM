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

| Factory | cuFFT function | Semantics |
|---|---|---|
| `CuFft.cufftForwardC2C(input, output, n, batch)` | `cufftExecC2C(CUFFT_FORWARD)` | 1D complex-to-complex FP32 forward FFT, `batch` contiguous transforms of length `n` |
| `CuFft.cufftInverseC2C(input, output, n, batch)` | `cufftExecC2C(CUFFT_INVERSE)` | Inverse FFT, unnormalized (inverse(forward(x)) = n·x, per cuFFT semantics) |

Complex data is interleaved `(re, im)` in a `FloatArray` of `2 * n * batch` elements.
FFT plans are created once per `(n, batch)` shape, bound to the execution plan's CUDA
stream, cached in the per-(device, execution plan) context, and destroyed when the
execution plan closes. Note: plan creation allocates a device work area, so the first
execution of a shape must happen outside CUDA graph capture.

## Build & run

```bash
make BACKEND=cuda
source setvars.sh

# Unit tests (skip automatically without CUDA backend / libtornado-cufft)
tornado-test -V uk.ac.manchester.tornado.unittests.cufft.TestCuFft

# Benchmark: sequential Java DFT vs TornadoVM JIT DFT kernel vs cuFFT
tornado -m tornado.cufft/uk.ac.manchester.tornado.cufft.tests.BenchmarkFft 65536 20
```
