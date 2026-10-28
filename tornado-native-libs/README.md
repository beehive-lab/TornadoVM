# TornadoVM native library tasks

This directory holds the modules that let a TornadoVM `TaskGraph` call vendor libraries
(cuBLAS, cuDNN, CLBlast, Apple MLX, NCCL, ...) as **library tasks**, next to JIT-compiled Java
tasks, on the same device buffers and the same stream or command queue:

```java
TaskGraph taskGraph = new TaskGraph("cuBLAS")
    .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix, vector)
    .task("preprocess", MyClass::preprocess, matrix)                  // JIT-compiled kernel
    .libraryTask("sgemv", CuBlas::cublasSgemv,                        // native cuBLAS call
            CuBlasOperation.CUBLAS_OP_T.operation(),
            m, n, alpha, matrix, lda, vector, incx, beta, output, incy)
    .task("postprocess", MyClass::postprocess, output)                // JIT-compiled kernel
    .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
```

Each module is a provider discovered through `java.util.ServiceLoader`, so the core runtime does
not depend on any of them. See [`HYBRID_API_GUIDE.md`](HYBRID_API_GUIDE.md) for the API and
[`docs/source/hybrid-api.rst`](../docs/source/hybrid-api.rst) for the SPI internals.

## Modules

| Directory | Maven artifactId | Java module | Backend | Native library | Docs |
|---|---|---|---|---|---|
| [`tornado-cublas`](tornado-cublas) | `tornado-cublas` | `tornado.cublas` | CUDA | cuBLAS, cuBLASLt | [README](tornado-cublas/README.md), [ROADMAP](tornado-cublas/ROADMAP.md) |
| [`tornado-curand`](tornado-curand) | `tornado-curand` | `tornado.curand` | CUDA | cuRAND | |
| [`tornado-cufft`](tornado-cufft) | `tornado-cufft` | `tornado.cufft` | CUDA | cuFFT | [README](tornado-cufft/README.md) |
| [`tornado-cudnn`](tornado-cudnn) | `tornado-cudnn` | `tornado.cudnn` | CUDA | cuDNN + `libtornado-cudnn` shim | [README](tornado-cudnn/README.md), [ROADMAP](tornado-cudnn/ROADMAP.md) |
| [`tornado-cusparse`](tornado-cusparse) | `tornado-cusparse` | `tornado.cusparse` | CUDA | cuSPARSE | [README](tornado-cusparse/README.md) |
| [`tornado-cudf`](tornado-cudf) | `tornado-cudf` | `tornado.cudf` | CUDA | RAPIDS libcudf via the `libtornado-cudf` shim | [README](tornado-cudf/README.md) |
| [`tornado-cutlass`](tornado-cutlass) | `tornado-cutlass` | `tornado.cutlass` | CUDA | CUTLASS, compiled into the `libtornado-cutlass` shim | [README](tornado-cutlass/README.md) |
| [`tornado-cuvs`](tornado-cuvs) | `tornado-cuvs` | `tornado.cuvs` | CUDA | cuVS (`libcuvs_c`) | [README](tornado-cuvs/README.md) |
| [`tornado-nccl`](tornado-nccl) | `tornado-nccl` | `tornado.nccl` | CUDA (multi-GPU) | NCCL | [README](tornado-nccl/README.md), [ARCHITECTURE](tornado-nccl/ARCHITECTURE.md) |
| [`tornado-clblast`](tornado-clblast) | `tornado-clblast` | `tornado.clblast` | OpenCL | CLBlast | |
| [`tornado-mlx`](tornado-mlx) | `tornado-mlx` | `tornado.mlx` | Metal | Apple MLX kernels (`mlx.metallib`) | [README](tornado-mlx/README.md) |

The Java modules are pure Java. They bind the vendor library with `java.lang.foreign` when the
class initialises. If the library is not installed, the provider reports its tasks as
`UNSUPPORTED` instead of failing to load.

### Native shims

Some libraries have no C API or need compiled device code, so they get a small C/C++ shim. The
shims are CMake builds in the `cuda-backend` profile. They need the CUDA toolkit, ship only in
the TornadoVM SDK (`lib/`), and are never published to Maven Central.

| Directory | Maven artifactId | Builds | Used by |
|---|---|---|---|
| [`cudnn-jni`](cudnn-jni) | `tornado-drivers-cudnn-jni` | `libtornado-cudnn` (cudnn-frontend SDPA) | `tornado-cudnn` |
| [`cudf-jni`](cudf-jni) | `tornado-drivers-cudf-jni` | `libtornado-cudf` (`extern "C"` shim over libcudf) | `tornado-cudf` |
| [`cutlass-jni`](cutlass-jni) | `tornado-drivers-cutlass-jni` | `libtornado-cutlass` (`nvcc`-compiled CUTLASS GEMMs) | `tornado-cutlass` |

Each shim skips itself with a CMake warning when its library (cuDNN, libcudf, CUDA 12+) is missing.
The matching tasks then report `UNSUPPORTED` at run time.

## Using a library from Maven

The library modules are published to Maven Central under `io.github.beehive-lab`, with one
coordinate per JDK line. Pick the suffix that matches your TornadoVM SDK:

```xml
<dependency>
    <groupId>io.github.beehive-lab</groupId>
    <artifactId>tornado-cublas</artifactId>
    <version>8.0.0-jdk21</version>  <!-- or 8.0.0-jdk22plus -->
</dependency>
```

The TornadoVM SDK already has every library jar in `share/java/tornado`. The `tornado` launcher
adds each backend's library modules to `--add-modules`: CLBlast with OpenCL, MLX with Metal,
and the CUDA libraries with CUDA.

## Building and testing

These modules build with the rest of TornadoVM (`make` or `bin/compile`). The native shims build
only with `-Pcuda-backend`. To build or test one module on its own:

```bash
./mvnw -Pjdk21 -pl tornado-native-libs/tornado-clblast -am install -DskipTests
./mvnw -Pjdk21 -pl tornado-native-libs/tornado-clblast -am test -DskipTests=false \
    -Dtest=CLBlastTest,CLBlastBindingsTest -Dsurefire.failIfNoSpecifiedTests=false
```

The device tests are in `tornado-unittests` (`uk.ac.manchester.tornado.unittests.<library>`):

```bash
tornado-test -V uk.ac.manchester.tornado.unittests.cublas.TestCuBlas
```

## Adding a library

Follow [section 9 of the hybrid API guide](HYBRID_API_GUIDE.md#9-write-your-own-provider) for
the provider code, and use `tornado-cudnn` as the template. Then wire the module in:

1. Create `tornado-native-libs/tornado-<lib>/`. Set `io.github.beehive-lab:tornado` as its
   `<parent>`, with `<relativePath>../../pom.xml</relativePath>`, and list it in
   [`pom.xml`](pom.xml) here.
2. Add it to `tornado-assembly/pom.xml` (dependencies) and to `tornado-assembly/assembly.xml`
   (the `share/java/tornado` dependency set).
3. Add its Java module to the backend's `--add-modules` list in
   `tornado-assembly/src/bin/tornado.py`.
4. Add it to the `-pl` list in `.github/workflows/deploy-maven-central.yml` so that it is
   published to Maven Central.
5. Add tests under `tornado-unittests` and the module to `tornado-unittests/pom.xml`.
6. Native shim (only if the library needs compiled C/C++): add `tornado-native-libs/<lib>-jni/`
   with `tornado-drivers` as its parent (`<relativePath>../../tornado-drivers/pom.xml</relativePath>`),
   list it in the `cuda-backend` profile of [`pom.xml`](pom.xml), and include its `libs` jar
   in `tornado-assembly`.
