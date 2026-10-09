# Baseline: performance (Phase 1)

Reference revision `ae409166a`, SDK `make BACKEND=opencl,cuda`, OpenJDK 21.0.2, RTX 4090 (driver
610.57.04) and Intel UHD 770. Commands: `docs/compiler-replacement/baseline/run-perf.sh 1`
(`tornado-benchmarks.py --iterations 10`, then jitLLM `bench`). Raw data:
[baseline/bench-tornado-run1.json](baseline/bench-tornado-run1.json),
[baseline/bench-jitllm-*-run1.json](baseline/).

## jitLLM (CUDA RTX 4090, 5 repetitions)

`./jitllm bench -m <model> --gpu --pp 128,512 --tg 64 --repetitions 5 --output json` from
`~/work/jitllm-jev` against this repository's SDK.

| Model | Test | Tokens/s (mean ± sd) |
|---|---|---:|
| Llama-3.2-1B-Instruct Q8_0 | pp128 | 347.6 ± 2.0 |
| Llama-3.2-1B-Instruct Q8_0 | pp512 | 266.6 ± 0.3 |
| Llama-3.2-1B-Instruct Q8_0 | tg64 | 365.5 ± 3.3 |
| Qwen3-0.6B F16 | pp128 | 319.8 ± 1.2 |
| Qwen3-0.6B F16 | pp512 | 312.8 ± 5.0 |
| Qwen3-0.6B F16 | tg64 | 313.1 ± 0.9 |

Run-to-run spread is 0.1–1.6% of the mean, which sets the C7 jitLLM threshold at the 3% floor of
max(2·CV, 3%).

## tornado-benchmarks (default sizes, 10 iterations)

"First iteration" includes the Graal compilation and the driver (NVRTC / OpenCL) build; the
difference to the median is the compile-and-first-launch cost TC must match or beat at C7.

| Benchmark | Device | Median (ms) | First iteration (ms) | Speedup vs Java (median) |
|---|---|---:|---:|---:|
| add-image-101-2048-2048 | OpenCL RTX 4090 | 14.194 | 27.6 | 1.1x |
| add-image-101-2048-2048 | OpenCL UHD 770 | 14.671 | 39.9 | 1.1x |
| add-image-101-2048-2048 | CUDA RTX 4090 | 13.464 | 25.9 | 1.2x |
| add-image-101-2048-2048 | Java (sequential) | 15.760 | 50.1 |  |
| blackscholes-100-8192 | OpenCL RTX 4090 | 0.140 | 109.9 | 3.6x |
| blackscholes-100-8192 | OpenCL UHD 770 | 0.207 | 124.6 | 2.4x |
| blackscholes-100-8192 | CUDA RTX 4090 | 0.081 | 86.3 | 6.3x |
| blackscholes-100-8192 | Java (sequential) | 0.505 | 2.6 |  |
| blurFilter-11-512 | OpenCL RTX 4090 | 1.532 | 13.4 | 548.4x |
| blurFilter-11-512 | OpenCL UHD 770 | 47.494 | 53.0 | 17.7x |
| blurFilter-11-512 | CUDA RTX 4090 | 0.832 | 11.7 | 1010.2x |
| blurFilter-11-512 | Java (sequential) | 840.273 | 1086.0 |  |
| convolve-array-100-2048-2048-5 | OpenCL RTX 4090 | 2.461 | 13.1 | 96.7x |
| convolve-array-100-2048-2048-5 | OpenCL UHD 770 | 15.406 | 22.1 | 15.4x |
| convolve-array-100-2048-2048-5 | CUDA RTX 4090 | 1.860 | 14.1 | 128.0x |
| convolve-array-100-2048-2048-5 | Java (sequential) | 238.012 | 302.6 |  |
| convolve-image-100-2048-2048-5 | OpenCL RTX 4090 | 2.576 | 14.6 | 51.5x |
| convolve-image-100-2048-2048-5 | OpenCL UHD 770 | 13.905 | 36.2 | 9.5x |
| convolve-image-100-2048-2048-5 | CUDA RTX 4090 | 2.466 | 12.0 | 53.8x |
| convolve-image-100-2048-2048-5 | Java (sequential) | 132.564 | 398.3 |  |
| dft-15-8192 | OpenCL RTX 4090 | 0.718 | 9.1 | 2822.7x |
| dft-15-8192 | OpenCL UHD 770 | 3.978 | 8.1 | 509.2x |
| dft-15-8192 | CUDA RTX 4090 | 1.209 | 8.3 | 1675.8x |
| dft-15-8192 | Java (sequential) | 2025.723 | 2029.2 |  |
| dgemm-20-512-512 | OpenCL RTX 4090 | 0.912 | 9.1 | 183.2x |
| dgemm-20-512-512 | Java (sequential) | 167.139 | 196.3 |  |
| euler-16-128 | OpenCL RTX 4090 | 15.992 | 24.2 | 20.5x |
| euler-16-128 | OpenCL UHD 770 | 189.013 | 192.1 | 1.7x |
| euler-16-128 | CUDA RTX 4090 | 15.424 | 22.8 | 21.3x |
| euler-16-128 | Java (sequential) | 328.415 | 563.0 |  |
| juliaset-50-8192-8192 | OpenCL RTX 4090 | 43.227 | 53.2 | 222.0x |
| juliaset-50-8192-8192 | OpenCL UHD 770 | 459.101 | 513.1 | 20.9x |
| juliaset-50-8192-8192 | CUDA RTX 4090 | 31.976 | 55.5 | 300.1x |
| juliaset-50-8192-8192 | Java (sequential) | 9595.527 | 9526.9 |  |
| mandelbrot-131-512 | OpenCL RTX 4090 | 0.956 | 9.2 | 1761.3x |
| mandelbrot-131-512 | OpenCL UHD 770 | 44.833 | 48.6 | 37.6x |
| mandelbrot-131-512 | CUDA RTX 4090 | 0.926 | 8.0 | 1819.0x |
| mandelbrot-131-512 | Java (sequential) | 1683.614 | 2588.2 |  |
| montecarlo-41-8192 | OpenCL RTX 4090 | 10.055 | 18.6 | 105.7x |
| montecarlo-41-8192 | OpenCL UHD 770 | 42.453 | 45.9 | 25.0x |
| montecarlo-41-8192 | CUDA RTX 4090 | 5.150 | 12.4 | 206.4x |
| montecarlo-41-8192 | Java (sequential) | 1063.057 | 1070.5 |  |
| nbody-51-16384 | OpenCL RTX 4090 | 1.934 | 11.2 | 342.1x |
| nbody-51-16384 | OpenCL UHD 770 | 25.447 | 29.2 | 26.0x |
| nbody-51-16384 | CUDA RTX 4090 | 1.466 | 8.8 | 451.2x |
| nbody-51-16384 | Java (sequential) | 661.487 | 2871.7 |  |
| renderTrack-51-8192 | OpenCL RTX 4090 | 68.582 | 101.6 | 11.6x |
| renderTrack-51-8192 | OpenCL UHD 770 | 72.378 | 173.8 | 11.0x |
| renderTrack-51-8192 | CUDA RTX 4090 | 66.788 | 88.7 | 11.9x |
| renderTrack-51-8192 | Java (sequential) | 793.797 | 1076.2 |  |
| saxpy-101-16777216 | OpenCL RTX 4090 | 9.445 | 20.9 | 0.6x |
| saxpy-101-16777216 | OpenCL UHD 770 | 11.911 | 30.7 | 0.5x |
| saxpy-101-16777216 | CUDA RTX 4090 | 7.254 | 21.5 | 0.8x |
| saxpy-101-16777216 | Java (sequential) | 5.760 | 23.9 |  |
| sgemm-20-512-512 | OpenCL RTX 4090 | 0.500 | 8.7 | 208.1x |
| sgemm-20-512-512 | OpenCL UHD 770 | 11.601 | 15.0 | 9.0x |
| sgemm-20-512-512 | CUDA RTX 4090 | 0.279 | 7.8 | 372.9x |
| sgemm-20-512-512 | Java (sequential) | 104.144 | 141.8 |  |
| stencil-101-1048576 | OpenCL RTX 4090 | 0.173 | 12.2 | 4.3x |
| stencil-101-1048576 | OpenCL UHD 770 | 0.271 | 6.4 | 2.7x |
| stencil-101-1048576 | CUDA RTX 4090 | 0.096 | 9.4 | 7.6x |
| stencil-101-1048576 | Java (sequential) | 0.735 | 6.2 |  |

Gaps in this baseline:
- `dgemm` aborted the whole benchmark when it reached the UHD 770 (no FP64:
  `TornadoDeviceFP64NotSupported`, then an NPE in `BenchmarkDriver.getAverage`), so CUDA and OpenCL
  4090 `dgemm` numbers are missing. Re-run with `--skipDevices 0:1` before C7.
- Separate Graal-compile vs driver-compile timing (`--enableProfiler`) is still to be collected for
  the C7 compile-latency comparison.
