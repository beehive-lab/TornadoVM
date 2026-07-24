---
name: tornadovm
description: Build, test, and contribute to TornadoVM's OpenCL/CUDA/Metal backends — make BACKEND targets, tornado-test (quickPass/single-class), checkstyle-before-PR, one-line commits, the develop-branch PR flow, and the recurring graalJars/setvars build gotchas. Use when building the project, running or adding unit tests, or editing the OpenCL/CUDA/Metal code generators. PTX and SPIRV backends are intentionally out of scope.
---

# Working on TornadoVM

TornadoVM JIT-compiles Java bytecode to GPU code (OpenCL C, CUDA/NVRTC, Apple Metal MSL) at runtime. This skill is the operational guide for building, testing, and contributing.

**Backend scope:** this skill covers **OpenCL, CUDA, and Metal** only. **PTX and SPIRV are out of scope** (both are slated for removal in the 6.0.0 roadmap). Never suggest `make BACKEND=ptx` or `make BACKEND=spirv`.

For backend internals (where codegen lives, how to add a node/intrinsic), see `references/codegen-map.md`.

## Build

Requires **JDK 21+** (`JAVA_HOME` must point at a JDK 21 build). Source the env once per shell, then build with `make`:

```bash
source setvars.sh                 # exports JAVA_HOME, TORNADOVM_HOME; prepends $TORNADOVM_HOME/bin to PATH
make BACKEND=opencl,cuda          # comma-separated list; values in scope: opencl, cuda, metal
```

Convenience targets: `make cuda` (= `--backend cuda`), `make metal` (= `metal,opencl`). Default `BACKEND` is `opencl`.

- **Incremental build ≈ 3 min.** Only a clean rebuild (Graal-jar pull + full recompile) is long — don't assume every build is slow.
- Under the hood `make` calls `bin/compile --jdk jdk21 --backend <list>`. Useful flags: `--rebuild` (rebuild deps), `--sdk`, `--polyglot`, `--mvn_single_threaded`.
- The built SDK lands in `dist/tornadovm-<version>-jdk21-dev-<variant>-<os>-<arch>/...`; `TORNADOVM_HOME` and the `bin/tornado*` launchers point into it.

### Build gotchas
1. **Stale `graalJars/`** — switching between branch families that vendor Graal differently leaves incompatible jars in the git-ignored `graalJars/` dir. Symptom: the *first* module (`tornado-api`) fails with `error: cannot access module-info` (not a code or JDK error). Fix: `rm -rf graalJars` then rebuild — `bin/pull_graal_jars.py` re-fetches the correct set.
2. **Stale `setvars.sh` `JAVA_HOME`** — an out-of-date `setvars.sh` can export the wrong JDK (e.g. JDK 27 → `invalid source release 21 with --enable-preview`). After sourcing, verify `java -version`; pin `JAVA_HOME` to a JDK 21 explicitly if it's wrong.

## Test

`tornado-test` (in `$TORNADOVM_HOME/bin`, source `tornado-assembly/src/bin/tornado-test`). The backend is fixed by **what you built** (recorded in `$TORNADOVM_HOME/etc/tornado.backend`) — there is no `--backend` test flag.

```bash
tornado-test --quickPass                                              # fast sweep (~152s); skips heavy/memory tests
tornado-test uk.ac.manchester.tornado.unittests.arrays.TestArrays -V # a single class, verbose
tornado-test 'uk.ac...TestArrays#testInitByteArray' -V               # a single method
```

Useful flags: `-qp/--quickPass`, `-V/--verbose`, `--printExec` (execution times — the flag is `--printExec`, **not** `--printExecutionTimes`), `-pk/--printKernel`, `--device s0.t0.device=0:1` (pick device), `-J/--jvm "-Dtornado.<opt>=..."` (extra JVM flags), `--ea` (assertions).

### Adding a unit test
- Place it under `tornado-unittests/src/main/java/uk/ac/manchester/tornado/unittests/<feature>/` (tests live in `src/main/java`, not `src/test`).
- Class `Test<Feature> extends TornadoTestBase` (`.../unittests/common/TornadoTestBase.java`) with JUnit `@Test` methods; validate against a sequential Java reference.
- To include it in the full "test the world" sweep, add a `TestEntry(...)` to the `__TEST_THE_WORLD__` list in `tornado-assembly/src/bin/tornado-test`. Running a single class by name needs no registration.

## Checkstyle — run before every commit / PR

```bash
make checkstyle          # = ./mvnw checkstyle:check
```

Config: `tornado-assembly/src/etc/checkstyle.xml` (severity = error, extends GraalVM rules). Suppress a block with `// CHECKSTYLE.OFF: <Rule>` … `// CHECKSTYLE.ON: <Rule>`. Most common failure after edits: `UnusedImports` (remove imports orphaned by deleted code). A green checkstyle is required — CI enforces it.

## Commit & PR conventions

- **Commit message: a single line, no body, and NO `Co-Authored-By` / AI-attribution trailer.** The project keeps a clean human-authored history.
- **Branch from and PR into `develop`** (not `master`/`main`). Open with `gh pr create --base develop`. A signed CLA is required — see `CONTRIBUTING.md`.
- Auto-format with the project's Eclipse/IntelliJ config before committing (`CONTRIBUTING.md`; `make intellijinit` after `source setvars.sh`).

## Backends (in scope)

| Backend | Module | Maven profile | Build |
|---|---|---|---|
| OpenCL | `tornado-drivers/opencl` | `opencl-backend` | `make BACKEND=opencl` |
| CUDA (CUDA-C / NVRTC) | `tornado-drivers/cuda` | `cuda-backend` | `make cuda` |
| Metal (macOS only) | `tornado-drivers/metal` | `metal-backend` | `make metal` |

Shared compiler phases/utilities live in `tornado-drivers/drivers-common`. Note: `tornado-drivers/cuda` (the standalone CUDA-C backend, classes `CUDA*`) is distinct from the PTX backend — "CUDA" here always means the `cuda` module.
