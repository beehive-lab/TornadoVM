# Project Leyden / AOTCache — findings & next steps for TornadoVM

Context: the post-JVMCI reflection path has a longer JIT warm-up ramp than the JVMCI path
(more metadata bytecode → HotSpot needs more iterations to reach steady state). Steady-state
speed and cold-compile are **not** regressed (cold is actually ~20% faster with the WP-A caching).
Project Leyden / the JDK AOT cache is the lever for the warm-up ramp and startup.

## What was measured (jdk27, CUDA, RTX 4090, row-major matvec 8192×2048)

| | no AOT | AOTCache (partial) |
|---|--------|--------------------|
| COLD (startup + first compile) | ~110 ms | **~90 ms (−18%)** |
| WARM (steady exec) | ~0.09 ms (fully warmed) | unchanged |

AOTCache **works** with TornadoVM:

```bash
# create (one-step, JDK 26+):
tornado --jvm="-XX:AOTCacheOutput=app.aot" -m tornado.examples/<Main> <args>
# use:
tornado --jvm="-XX:AOTCache=app.aot" -m tornado.examples/<Main> <args>
```

Note: tornado's `--jvm` needs the `=` form (`--jvm="-XX:..."`) — argparse otherwise treats
`-XX:...` as a separate option.

## Why the win is only partial today

The JVM logs at create/use time:

```
[error][aot] archivedBootLayer not available, disabling full module graph
[error][aot] Using AOT-linked classes: false
[error][aot] Cannot use CDS heap data.
```

Two independent blockers to the **full module graph** archive (which unlocks AOT-linked classes
+ archived heap = the big startup win, and is a prerequisite for AOT-compiled code later):

1. **`--add-modules ALL-SYSTEM`** in `tornado.py`. `ALL-SYSTEM` resolves `java.se` → the entire
   SE module set (60+ modules), which is not archivable as a static graph. **Fixable** — replace
   with an explicit list (this branch: `perf/tornado-explicit-modules`).

2. **`jdk.incubator.vector`** (Panama Vector API, still *incubating*). Incubator modules
   **hard-disable** full-graph CDS/AOT archiving by JDK design. The **examples** and **unittests**
   modules `requires jdk.incubator.vector`, so any example/test always pulls it in and blocks the
   archive. **The core modules (`tornado.api`, `tornado.runtime`, drivers) do NOT require it.**

## Consequence

- A **production TornadoVM app that does not use Panama vector types** + the explicit-module
  `tornado.py` **can** get the full-module-graph AOTCache (bigger startup win than the partial 18%).
- **Examples / unittests cannot** (they require the incubator module) until the Vector API
  graduates from incubator.

## Next steps (in priority order)

1. **Land the explicit-module `tornado.py` change** (branch `perf/tornado-explicit-modules`).
   Removes the `ALL-SYSTEM` blocker. Validated with the CUDA + OpenCL quickPass suites; **PTX,
   SPIRV, Metal still need a quickPass on their own runners** before merge. If any workload throws
   `NoClassDefFoundError` / `module not found`, add the missing platform module to the list.

2. **Make `jdk.incubator.vector` opt-in, not global.** Today `tornado.py` force-adds it for every
   launch. Drop it from the global `--add-modules` and rely on each app/module's own
   `requires jdk.incubator.vector` (examples/unittests already declare it). Then non-vector apps
   stop pulling the incubator module → their full-graph AOTCache works.

3. **Bake an AOT training archive into `bin/compile` / a `tornado --aot` helper.** A training run
   on a representative kernel produces the cache; ship/`--aot`-select it. Document the record/use
   flow above.

4. **Track Panama Vector API finalization.** Once `jdk.incubator.vector` graduates to a standard
   module, blocker #2 disappears for everything, including examples.

5. **AOT-compiled code (full Leyden).** Mainline jdk26/27 AOTCache caches class loading/linking and
   (with method profiling) speeds the JIT ramp, but does **not** ship AOT-compiled machine code.
   When the JDK gains AOT code caching, that flattens the WARM ramp to zero — re-measure then.
