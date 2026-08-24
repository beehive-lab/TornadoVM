# Standalone probes

A probe is a single Java file in the scratchpad that drives the built SDK directly. It is the campaign's inner loop: ~10 s per run against ~3.5 min for a test sweep, and it can time phases a unit test cannot.

## Harness

```bash
SDK=/path/to/TornadoVM/dist/tornadovm-<ver>-jdk21-dev-cuda-linux-amd64/tornadovm-<ver>-jdk21-dev-cuda

javac -g --enable-preview --release 21 \
      --module-path $SDK/share/java/tornado --add-modules tornado.api \
      -d . MyProbe.java

source /path/to/TornadoVM/setvars.sh
tornado --jvm="-Dtornado.recover.bailout=False" -cp $PWD MyProbe
```

Four gotchas, each of which costs a confusing hour:

1. **`-g` is mandatory.** Without debug info the Graal front end dies with `NullPointerException: ... LocalVariableTable.getLocalsAt(int)`. It looks like a compiler bug; it is a missing `-g`.
2. **`--enable-preview --release 21`** — the API's native array classes are compiled with preview features on.
3. **`-Dtornado.recover.bailout=False`** or a failed compilation silently runs sequential Java and you time the CPU.
4. **`DataTransferMode.EVERY_EXECUTION` is an `int` constant**, not an enum: a helper parameter must be `int`. `new TornadoExecutionPlan(...)` throws `TornadoExecutionPlanException`, so `main` declares `throws Exception`.

## Probe shapes that earned their keep

**Dispatch-bound loop** — per-execution median, the shape most runtime overhead shows up in:

```java
try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
    for (int i = 0; i < WARMUP; i++) plan.execute();
    for (int i = 0; i < ITERS; i++) {
        long t0 = System.nanoTime();
        plan.execute();
        samples[i] = System.nanoTime() - t0;
    }
}
// report median and p10 — the mean hides a bimodal distribution
```

**Phase-split probe** — proves *where* a call blocks, which a total cannot:

```java
long t0 = System.nanoTime();
TornadoExecutionResult r = plan.execute();
long t1 = System.nanoTime();       // submit
sink += hostWork(micros);          // busy-wait, never Thread.sleep
long t2 = System.nanoTime();       // host work
r.await();
long t3 = System.nanoTime();       // wait
```

This is what showed that `execute()` was synchronous because of `waitOn()` at the API layer, not because of the blocking copy-out bytecode — the opposite of the initial hypothesis.

**Cardinality sweep** — vary one structural parameter (number of outputs, number of transferred objects, number of graphs) and read the slope. A cost that grows with N is a per-object cost; a flat one is fixed. This turned "multi-output graphs feel slow" into "each extra output costs ~2.4 µs of host round trip".

**Upper-bound probe** — remove the thing entirely (e.g. declare the output `UNDER_DEMAND` so no copy-out happens) to bound what optimising it could ever buy. Cheap, and it kills bad ideas before any runtime edit. Read it as a **bound, not a target**: removing a copy also removes its PCIe time, which a real optimisation would not.

**Correctness inside the probe.** Every probe checks its own results (`mismatches` counter). A performance probe that does not validate will happily report a huge win for a kernel that never ran — exactly the 35x "speed-up" that turned out to be a swallowed launch failure.

## Where probes live

Scratchpad only (`/tmp/claude-.../scratchpad/probe/`). If a probe finds something worth keeping, it becomes a **unit test** under `tornado-unittests/.../unittests/<feature>/` plus a `TestEntry(...)` line in `tornado-assembly/src/bin/tornado-test` — the probe itself is never committed.

## From probe to test

The translation is usually mechanical, with two additions:

- assert against a sequential Java reference, not against a previously observed number;
- make the test **fail on the bug**: run it against unmodified `develop` first and record the failure text in the PR. A regression test that never failed proves nothing.
