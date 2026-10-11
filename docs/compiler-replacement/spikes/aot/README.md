# Spike: Leyden AOT cache and GraalVM native image for TC

First made on branch `compiler/spike-aot`; the SDK now ships the results (README, "Fast start-up"). Measured 2026-10-11 on the RTX-4090
host, headless: one TC compile of a TornadoVM benchmark kernel per fresh process, median of 10.

## Results

TC compile time inside the process (ms):

| variant | blackscholes | nBody | saxpy | sgemm | data |
|---|---|---|---|---|---|
| JDK 21, compiler/main before 69e7f37 | 155.8 | 154.5 | 114.3 | 125.5 | `data/ab1.txt` (baseline) |
| JDK 21, inline string concat (on main) | 142.6 | 140.2 | 101.0 | 114.0 | `data/ab3.txt` |
| JDK 21 + dynamic AppCDS (`-XX:ArchiveClassesAtExit`) | 105.7 | 103.1 | 65.9 | 79.0 | `data/ab2.txt` |
| JDK 25 | 128.6 | 124.8 | 90.7 | 102.1 | `data/ab-jdk25.txt` |
| JDK 25 + Leyden AOT cache | 65.4 | 64.9 | 37.2 | 46.3 | `data/ab-jdk25-aot.txt` |
| JDK 26 | 133.6 | 131.7 | 94.8 | 107.6 | `data/ab-jdk26.txt` |
| JDK 26 + AOT cache | 71.9 | 69.8 | 38.9 | 51.4 | `data/ab-jdk26-aot.txt` |
| GraalVM CE 25 native image | 18.5 | 20.0 | 7.8 | 15.7 | run log below |

Whole process, start-up included, `blackscholes` (ms): JDK 21 before 243.6; inline concat 231.4;
AppCDS 196.3; JDK 25 219.2; JDK 25 + AOT cache 124.7; native image 25.6.

The AOT caches and the CDS archive were trained on `blackscholes` only and still help the other
three kernels (the classes, lambdas and method handles are the compiler's, not the kernel's).

## Correctness

The native image emits byte-identical CUDA to the JVM build for all four kernels (`cmp` of the emitted
sources). The training-cache variants run the same bytecode as the plain JVM.

## What had to change

- **Nothing in TC for the AOT cache or AppCDS.** Only the classpath must be jars (CDS rejects
  non-empty directories), and the JDK 25 run needs TC built for 25 without `--enable-preview`
  (`build-jdk25.sh`: TC compiles unchanged at `--release 25`).
- **Native image: nothing in TC either**, when class bytes come from `BytecodeSource.classpath`
  (directories and jars read as bytes). No reflection or resource configuration was needed; the build
  takes about 10 s and gives a 17.5 MB binary. The JFR event classes are in the image; recording them
  in a native image (`--enable-monitoring=jfr`) was not tried.
- **Not covered by the native image:** the runtime hook's own class loading. `DeviceKernels.resolve`
  uses `Class.forName` and `SerializedLambda` to find a `KernelContext.launch` child, and
  `BytecodeSource.classLoader` reads through a live class loader; both are JVM-only. A native `tc`
  CLI therefore compiles everything except device launches, and TC inside TornadoVM stays a JVM
  library.

## Recommendation

1. Keep the inline string concatenation (on main).
2. Ship an AOT cache with the TornadoVM SDK once the floor is JDK 25: half the cold compile, no code
   change; a dynamic AppCDS archive gives a quarter on JDK 21 today.
3. A native image suits a standalone `tc` CLI (headless emit/explain/diff), at 18 ms per cold
   compile, not the in-process compiler.

## Reproduce

```
./build-jdk25.sh ~/tc-perf/coldstart/jdk25          # TC jar for JDK 25
./ab.sh <label> <cp-file> [jvm flags]               # 10 fresh JVMs x 4 kernels; summ.py <file>
./aot-cache.sh <tc-jdk25.jar> <harness.jar>         # train and use a Leyden AOT cache
./native-image.sh <tc-jdk25.jar>                    # GraalVM CE 25 native image
```
