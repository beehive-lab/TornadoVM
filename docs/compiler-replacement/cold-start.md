# Cold-start compile time (design review Phase D)

Measured 2026-10-11, headless (no GPU), RTX-4090 host, one TC compile per fresh JVM, median of 10.
Kernels: `ComputeKernels.blackscholes`, `nBody`, `LinearAlgebraArrays.saxpy`, `sgemm`.

## Where a cold compile goes

A cold `blackscholes` compile costs 156 ms inside TC on JDK 21 against 5.6 ms warm (same JVM, 50th
compile). Interpreted only (`-Xint`) it is 650 ms. It loads 384 TC classes and spins up 167 TC lambda
classes plus 182 method-handle forms; `Bindings` alone took 21 ms to initialise, mostly linking its
string-concatenation `invokedynamic` sites (hundreds in its static tables).

## Fixed on compiler/main

`-XDstringConcat=inline` for every TC module: concatenation compiles to `StringBuilder` code with no
bootstrap on first use. `Bindings` initialises in 8 ms instead of 21.

| kernel | before (ms) | after (ms) |
|---|---|---|
| blackscholes | 155.8 | 140.9 |
| nBody | 154.5 | 139.7 |
| saxpy | 114.3 | 101.0 |
| sgemm | 125.5 | 112.8 |

## Deployment options (spike, branch `compiler/spike-aot`)

Process wall time for one cold `blackscholes` compile, JVM start included (median of 10):

| variant | ms |
|---|---|
| JDK 21, before | 243.6 |
| JDK 21, inline concat | 231.4 |
| JDK 21 + dynamic AppCDS archive | 196.3 |
| JDK 25 | 219.2 |
| JDK 25 + Leyden AOT cache (`-XX:AOTCacheOutput` training run) | 124.7 |
| GraalVM CE 25 native image of the headless compiler | 25.6 |

TC compile time alone (inside the process): JDK 21 140.9 ms, AppCDS 105.7, JDK 25 128.6, JDK 25 + AOT
cache 65.4, native image 18.5. The native image emits byte-identical CUDA to the JVM build on all four
kernels. Code, scripts and raw data are on `compiler/spike-aot` (`docs/compiler-replacement/spikes/aot/`).
