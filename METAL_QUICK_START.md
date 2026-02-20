# TornadoVM Metal Backend - Quick Start

## Prerequisites

- macOS with Apple Silicon (M1/M2/M3/M4)
- JDK 21 (e.g., OpenJDK, Corretto, Zulu)
- `JAVA_HOME` set and `java` on PATH

## Build

```bash
# Metal-only build
make BACKEND=metal

# Metal + OpenCL (uses Apple's OpenCL implementation)
make metal
```

This runs `bin/compile --jdk jdk21 --backend metal`, which:
1. Cleans previous artifacts
2. Builds all modules via Maven (including CMake compilation of the native JNI library)
3. Assembles the SDK under `dist/tornado-sdk/`
4. Writes `etc/tornado.backend` and generates the `tornado-argfile`

## Setup Environment

After every build (or new terminal session):

```bash
source setvars.sh
```

This sets `TORNADO_SDK`, `PATH`, and other variables needed by the `tornado` and `tornado-test` commands.

## Verify Installation

```bash
tornado --devices
```

Expected output:

```
Number of Tornado drivers: 1
Driver: Metal
  Total number of Metal devices  : 1
  Tornado device=0:0  (DEFAULT)
    METAL --  [Apple Metal] -- Apple M3 Pro
        Global Memory Size: 28.1 GB
        Local Memory Size: 32.0 KB
        ...
```

## Run Tests

```bash
# Single test class (verbose)
tornado-test -V uk.ac.manchester.tornado.unittests.foundation.TestIntegers

# Single test method
tornado-test -V uk.ac.manchester.tornado.unittests.foundation.TestIntegers#test01

# All unit tests with assertions
tornado-test --ea --verbose

# Quick pass (skips heavy/memory tests, logs to file)
tornado-test --ea --verbose --quickPass
```

### Useful tornado-test flags

| Flag | Description |
|------|-------------|
| `-V` / `--verbose` | Verbose output with per-test stats |
| `--ea` | Enable assertions |
| `--quickPass` / `-qp` | Skip heavy tests |
| `-pk` / `--printKernel` | Print generated Metal (MSL) kernel source |
| `--device "s0.t0.device=0:1"` | Target a specific device |
| `-J"-Dprop=val"` | Pass JVM system properties |
| `--enableProfiler console` | Print profiling info |

## Run Your Own Code

```bash
# Run a class from a module
tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixMultiplication1D

# Run with kernel printing
tornado --printKernel -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixMultiplication1D
```

## Common Issues

### `Module tornado.drivers.opencl not found`

The `tornado-argfile` references a backend that wasn't built. Fix:

```bash
# 1. Set the correct backend in the config file
echo "tornado.backends=metal-backend" > "$TORNADO_SDK/etc/tornado.backend"

# 2. Regenerate the argfile
python3 "$TORNADO_SDK/bin/gen-tornado-argfile.py" metal

# 3. Re-source the environment
source setvars.sh
```

### `NoClassDefFoundError: org/graalvm/compiler/...`

GraalVM compiler JARs are missing from the SDK. The build script copies them automatically, but if it fails:

```bash
mkdir -p "$TORNADO_SDK/share/java/graalJars"
cp graalJars/*.jar "$TORNADO_SDK/share/java/graalJars/"
```

The `graalJars/` directory in the project root must contain the pre-built Graal compiler JARs for JDK 21.

### `setvars.sh` points to a stale SDK path

The SDK directory name includes the git commit hash (e.g., `tornado-sdk-1.1.2-dev-b0d8cd7`). After `mvn clean` or a new commit, the hash changes. Re-run the build or manually update `setvars.sh`.

### FP64 (double precision) test failures

Apple GPUs do not support double-precision floating point. Tests that use `double` arrays will fail with an unsupported-feature error. This is a hardware limitation, not a bug.

### `UnsatisfiedLinkError` for Metal JNI functions

The native library wasn't compiled or isn't on the library path. Verify:

```bash
# Check the dylib exists in the SDK
ls "$TORNADO_SDK/lib/libtornado-objc-metal.dylib"

# Check it has the expected symbols
nm -g "$TORNADO_SDK/lib/libtornado-objc-metal.dylib" | grep Java_uk_ac_manchester
```

If missing, rebuild with `make BACKEND=metal`.

### Build succeeds but post-install script fails

The `bin/compile` script's post-installation step may fail writing `tornado.backend` if the SDK path format doesn't match expectations. The Maven build itself has already succeeded. Manually fix:

```bash
# Find the actual SDK directory
ls dist/tornado-sdk/

# Write the backend file
echo "tornado.backends=metal-backend" > dist/tornado-sdk/<actual-sdk-dir>/etc/tornado.backend

# Copy graalJars
cp graalJars/*.jar dist/tornado-sdk/<actual-sdk-dir>/share/java/graalJars/

# Regenerate argfile
source setvars.sh
python3 "$TORNADO_SDK/bin/gen-tornado-argfile.py" metal
```

## Quick Smoke Test

After building and setting up the environment, run this one-liner to confirm everything works:

```bash
tornado-test -V uk.ac.manchester.tornado.unittests.foundation.TestIntegers
```

All 7 tests should pass. If they do, the Metal backend is fully operational.
