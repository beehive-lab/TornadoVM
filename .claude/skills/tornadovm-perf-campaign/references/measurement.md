# Measurement recipes

Reference box for every number quoted here: NVIDIA RTX 4090, Ubuntu 24.04, JDK 21, CUDA backend.

## 1. Host time — JFR

```bash
tornado --jvm="-Dtornado.recover.bailout=False \
  -XX:StartFlightRecording=settings=profile,filename=run.jfr \
  -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints" \
  -cp $PWD MyProbe
```

`settings=profile` samples both `jdk.ExecutionSample` (Java frames) and `jdk.NativeMethodSample` (threads in native — where a GPU runtime actually sits). A run of a few seconds is the minimum for usable counts.

Aggregate with `jfr print`, **not** with the JMC UI:

```bash
JFR=$(dirname $(readlink -f $(which java)))/jfr
$JFR print --stack-depth 96 --events ExecutionSample,NativeMethodSample run.jfr
```

Two gotchas that produce wrong conclusions:

- **`--stack-depth` is required.** The default truncates the stack to ~5 frames with `...`, so any phase filter ("only samples under `generateTokensGPU`") matches almost nothing and you conclude the phase is idle.
- **Do not split the print output on the string `jdk.`** — frames such as `jdk.internal.misc.Unsafe` split events in half. Split on lines matching `^jdk\.(ExecutionSample|NativeMethodSample) \{`.

Phase attribution: filter samples whose stack contains a marker method (`generateTokensGPU` for decode, `initializeTornadoVMPlan` for start-up) and rank the **leaf** frame within the phase.

## 2. Driver and device time — Nsight Systems

```bash
nsys profile -t cuda --sample=none --cpuctxsw=none -f true -o out <command>
nsys stats --report cuda_api_sum          out.nsys-rep   # host-side driver calls
nsys stats --report cuda_gpu_kern_sum     out.nsys-rep   # kernel time
nsys stats --report cuda_gpu_mem_time_sum out.nsys-rep   # H2D/D2H time
```

**Call counts are the robust signal**; total times include start-up. Counts per execution answer "how many driver calls does one task graph cost" — the number most dispatch work is really about.

Useful denominators: divide `cuLaunchKernel` count by the number of executions to get kernels/execution; a big gap between that and 1 means the plan is many small graphs.

## 3. Steady-state per-execution cost — the benchmark runner

```bash
tornado --jvm="-Dtornado.benchmarks.skipserial=True" \
  -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner \
  --params="saxpy 100000 512"
```

Prints `median(ns)=...`; the harness already discards warm-up iterations. `saxpy <iters> 512` is the canonical **dispatch-bound** shape (one kernel, one H2D, one blocking D2H, 2 KB moved, ~1.1 µs kernel). Large sizes (`saxpy 2000 16777216`, `nbody 500 16384`) are the **controls** — they must not move.

## 4. A/B protocol

```bash
# with the change
make BACKEND=cuda && <measure> > after.txt
# baseline, same tree, same build system
git stash push -m ab -- <paths> && make BACKEND=cuda && <measure> > before.txt
git stash pop && make BACKEND=cuda
```

- 3 runs minimum, compare medians.
- Same JVM flags, same device memory, same model/input.
- Include one control workload in both runs.
- For anything under 10%, this protocol is mandatory — run-to-run variance on a warm GPU is a few percent.

**Fixed vs per-iteration cost:** repeat the A/B at two iteration counts (e.g. 128 and 512 tokens). A gap that shrinks with more iterations is a one-off cost you *moved*; a gap that holds is work you *removed*.

## 5. Test baselines (reference box)

| Sweep | Unmodified `develop` | Use |
|---|---|---|
| CUDA `tornado-test --quickPass` | **85 failures** | Aggregate comparison is valid |
| OpenCL `tornado-test --quickPass` | **271 failures** (e.g. `TestArrays` 4/23) | Aggregate is meaningless — compare per class, same device |
| Known flaky | `compute.MMwithBytes`, `compute.TransformerKernelsTest` | Re-run in isolation before blaming a change |

Passing extra JVM flags to `tornado-test` needs the `=` form **and** a leading space, because the argument parser treats a leading `-` as a new flag:

```bash
tornado-test --jvm=" -Dtornado.vm.deps=True" uk.ac.manchester.tornado.unittests.arrays.TestArrays   # works
tornado-test --jvm "-Dtornado.vm.deps=True" ...                                                     # "expected one argument"
```

## 6. Knobs worth knowing

| Flag | Effect |
|---|---|
| `-Dtornado.recover.bailout=False` | Fail instead of silently running sequential Java. **Always on when measuring.** |
| `-Dtornado.vm.deps=True` | Enable dependency tracking (a different, slower code path — measure both) |
| `-Dtornado.device.memory=NGB` | Device heap; a plan of many graphs can need noticeably more than the sequential path |
| `-Dtornado.profiler=True` | TornadoVM's own profiler — perturbs; never the default measurement |
| `-Dtornado.max.events` / `-Dtornado.eventpool.maxwaitevents` | Two *different* knobs with similar names; the first sizes wait-list rows |
| `--printBytecodes` | What the graph compiler actually emitted (blocking vs non-blocking copy-out, per-chunk ALLOC/DEALLOC) |

Strip ANSI colour before grepping `--printBytecodes` output: `sed 's/\x1b\[[0-9;]*m//g'`.

## 7. Application-level measurement (LLM decode as the example)

`llama-tornado --show-command` prints the raw `java` line; capture it once into a shell script in the scratchpad, then vary one flag per run. That keeps every A/B on an identical command line, which `llama-tornado` itself does not guarantee across invocations.

Split reporting into **start-up** and **steady state** — on a short LLM run, start-up (host-memory pinning, NVRTC) dominates whole-run wall time, so a decode-loop win can be invisible in the total.
