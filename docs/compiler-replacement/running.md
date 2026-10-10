# Running TC

TC is part of the normal SDK: the `tc-*` jars sit in `share/java/tornado` next to the other
TornadoVM jars, and `tornado.drivers.common` declares `requires transitive tornado.tc.runtime`, so
the module system picks TC up from the existing `--module-path`. **The argfile does not change.**
Neither `--add-modules` nor `--add-exports` needs a new entry, and TC needs no JVMCI exports.
TC is selected per run with system properties.

```bash
source setvars.sh
java @$TORNADOVM_HOME/tornado-argfile \
     -Ds0.t0.device=1:0 \
     -Dtornado.compiler=tc \
     -m tornado.examples/uk.ac.manchester.tornado.examples.arrays.ArrayAddInt
```

TC compiles for the CUDA backend only. On an OpenCL or Metal device the task still goes through
Graal. On this machine `0:0` is OpenCL on the RTX 4090, so select `1:0` (CUDA) with
`-D<graph>.<task>.device=1:0`, or with `-Dtornado.unittests.device=1:0` for `tornado-test`.
The `tornado` launcher accepts the same properties, for example `tornado --jvm="-Dtornado.compiler=tc" ...`.

## Properties

| Property | Default | Effect |
|---|---|---|
| `tornado.compiler` | `graal` | `tc` selects TC for CUDA tasks |
| `tornado.compiler.fallback` | `true` | when TC refuses a kernel, compile it with Graal instead; `false` throws the TC exception (see `ErrorCodes`) |
| `tornado.printKernel` | `False` | prints the emitted CUDA source, for TC as for Graal (`--printKernel`) |
| `tornado.tc.dump` | `none` | `all`, or a comma-separated list of stages to write as files |
| `tornado.tc.dumpDir` | `tc-dumps` | directory for dumps and reports |
| `tornado.tc.only` | (all) | dump only kernels whose name contains this text |
| `tornado.tc.report` | (off) | `html` writes `<dumpDir>/<Class>_<method>.report.html` per kernel, refused ones included: bytecode, IR after each stage, structured IR, source |
| `tornado.tc.verify` | `each` | `end` runs the IR verifier only before emission |
| `tornado.tc.verbose` | `false` | one line per kernel: compiled, or refused with its error code |
| `tornado.tc.stats` | `false` | compile and refusal counts at exit |
| `tornado.tc.compileTimes` | `false` | one `[compile-times]` line per compiled task; needs the profiler on (`-Dtornado.profiler=True`), otherwise it prints 0 |

Dump stages, in pipeline order (one file each, named `<kernel>.NN-<stage>.<ext>`):
`input`, `specialise`, `atomics`, `reduce`, `batch`, `parallel`, `optimize`, `local-arrays`, `abi`,
`optimize.post-abi` (`.tcir`, TC-SSA), then `structure` (`.tcs`, structured IR) and `emit` (`.cu`).
A stage that does not apply to a kernel writes no file. The bytecode listing appears in the HTML report.

## Kernel source and IR in one run

```bash
java @$TORNADOVM_HOME/tornado-argfile -Ds0.t0.device=1:0 \
     -Dtornado.compiler=tc -Dtornado.compiler.fallback=false \
     -Dtornado.printKernel=True \
     -Dtornado.tc.dump=input,abi,structure,emit -Dtornado.tc.dumpDir=tcd \
     -m tornado.examples/uk.ac.manchester.tornado.examples.arrays.ArrayAddInt
ls tcd
# ArrayAddInt_add.02-input.tcir  ArrayAddInt_add.07-abi.tcir  ArrayAddInt_add.10-structure.tcs  ArrayAddInt_add.11-emit.cu
```

`-Dtornado.tc.report=html` (with `tornado.tc.dumpDir`) writes all stages side by side in one HTML file.

## Profiler

With `--enableProfiler console` (`-Dtornado.profiler=True`) TC compilations are reported under
their own keys: `TASK_COMPILE_TC_TIME` and `TOTAL_TC_COMPILE_TIME`. For TC they replace the Graal keys.
NVRTC time stays in `TASK_COMPILE_DRIVER_TIME`. `getCompileTime()` adds Graal + TC + driver, and
`getTornadoCompilerTime()` adds Graal + TC.

## Tests

```bash
tornado-test --verbose -J"-Dtornado.unittests.device=1:0 -Dtornado.compiler=tc -Dtornado.compiler.fallback=false" <TestClass>
python3 docs/compiler-replacement/baseline/tc-diff.py run --families <family> --out ~/tc-diff/<name>   # Graal vs TC
```
