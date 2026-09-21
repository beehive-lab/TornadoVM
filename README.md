<img src="etc/tornadovm-logo.png" alt="TornadoVM logo" align="left" width="350">


--- 

## Write Java. Run on GPUs. Fast.

TornadoVM is a GPU programming framework for Java that works with JDK 21+ (currently JDK 21-27). It JIT-compiles Java bytecode into **NVIDIA CUDA, OpenCL C, and Apple Metal (MSL)** at runtime, so your existing Java code runs on **NVIDIA GPUs (via CUDA)**, AMD, Intel, and Apple Silicon GPUs, integrated GPUs, and multi-core CPUs. 

On NVIDIA hardware it goes further: beyond generating CUDA, TornadoVM now calls straight into the **NVIDIA library ecosystem (cuBLAS, cuFFT, cuDNN) and exposes Tensor Core `mma.sync` instructions from pure Java**. No CUDA C. No JNI bindings to maintain. No native toolchain in your application. In addition, it has support for Tile Programming [cuTile](https://developer.nvidia.com/cuda/tile) via its **TileContext API**.

[![Build & Test](https://github.com/beehive-lab/TornadoVM/actions/workflows/build-test.yml/badge.svg)](https://github.com/beehive-lab/TornadoVM/actions/workflows/build-test.yml)
[![Tornado API](https://img.shields.io/maven-central/v/io.github.beehive-lab/tornado-api?logo=apache-maven&color=blue&label=Tornado%20API)](https://central.sonatype.com/artifact/io.github.beehive-lab/tornado-api)
[![Install with SDKMAN!](https://img.shields.io/badge/Install%20with-SDKMAN!-2c7a7b?logo=sdkman&logoColor=white)](https://sdkman.io/sdks/tornadovm/)
[![Docs](https://img.shields.io/badge/docs-readthedocs-blue)](https://tornadovm.readthedocs.io/en/latest/)
[![Slack](https://img.shields.io/badge/chat-Slack-4A154B?logo=slack)](https://join.slack.com/t/tornadovmcommunity/shared_invite/zt-3ai2wyqva-bKz~cQRFlaJ~ZnPrbkwIEw)

**Latest release:** TornadoVM 6.0.0 (JDK 21 - JDK 27) — native **NVIDIA library integration** (cuBLAS / cuFFT / cuDNN) and Tensor Core intrinsics, plus a native **Apple Metal backend** for Apple Silicon. [Changelog](https://tornadovm.readthedocs.io/en/latest/CHANGELOG.html) · [Website](https://www.tornadovm.org) · [Documentation](https://tornadovm.readthedocs.io/en/latest/)


---

## This is the whole programming model

TornadoVM gives you three Java APIs for writing GPU kernels, from the least to the most explicit about how work maps onto the hardware. All three build the same `TaskGraph`, run through the same `TornadoExecutionPlan`, and can be mixed in a single graph. Here's one computation, dense matrix multiplication, `C = A × B`  written all three ways:

<table>
<tr>
<th width="50%">Loop Parallel API (<code>@Parallel</code>)</th>
<th width="50%">KernelContext API</th>
</tr>
<tr>
<td valign="top">

```java
void matmul(Matrix2DFloat A,
     Matrix2DFloat B,
     Matrix2DFloat C, int size) {
  for (@Parallel int i=0; i<size; i++) {
    for (@Parallel int j=0; j<size; j++) {
      float sum = 0f;
      for (int k=0; k<size; k++)
        sum += A.get(i,k)*B.get(k,j);
      C.set(i, j, sum);
    }
  }
}

// TornadoVM infers the thread
// mapping from the @Parallel loops.
```

</td>
<td valign="top">

```java
void matmul(KernelContext ctx,
     FloatArray A, FloatArray B,
     FloatArray C, int size) {
  int row = ctx.globalIdx;
  int col = ctx.globalIdy;
  float sum = 0f;
  for (int k=0; k<size; k++)
    sum += A.get(k*size+row)
         * B.get(col*size+k);
  C.set(col*size+row, sum);
}

// You name the thread; TornadoVM
// still owns memory + dispatch.
```

</td>
</tr>
<tr>
<th colspan="2">TileContext API (CuTile) 🆕 — CUDA backend only</th>
</tr>
<tr>
<td colspan="2">

```java
void matmul(TileContext tc, HalfFloatArray A, HalfFloatArray B, FloatArray C,
            int m, int n, int k) {
  PartitionView aView = tc.partition(tc.view(A, m, k), TILE, TILE);
  PartitionView bView = tc.partition(tc.view(B, k, n), TILE, TILE);
  PartitionView cView = tc.partition(tc.view(C, m, n), TILE, TILE);

  Tile acc = tc.zeros(DType.F32, TILE, TILE);
  for (int step = 0; step < k / TILE; step++) {
    acc = tc.mma(aView.load(tc.bidX(), step), bView.load(step, tc.bidY()), acc);
  }
  cView.store(acc, tc.bidX(), tc.bidY());
}

// No thread index, no shared memory, no barrier, no instruction shape —
// the tile compiler picks them.
```

</td>
</tr>
</table>

Pick the one that matches how much control you need: `@Parallel` when TornadoVM should infer the thread mapping for you, `KernelContext` when you want CUDA/OpenCL/SYCL-style explicit thread indexing, and `TileContext` when you'd rather program in **tiles** than threads and let the tile compiler pick the tensor-core instructions. Each is covered in depth below. [Programming guide →](https://tornadovm.readthedocs.io/en/latest/programming.html)

---

## Loop Parallel API (`@Parallel`)

The simplest of the three models. You just annotate the parallel loops with `@Parallel` and TornadoVM infers the whole launch configuration (global bounds, local work size) for you. Because the annotation is the only GPU-specific part, the same method also runs unmodified on the plain sequential JVM path, which makes it the easiest style to write and debug first.

Full runnable example: [MatrixMultiplication2D.java](tornado-examples/src/main/java/uk/ac/manchester/tornado/examples/compute/MatrixMultiplication2D.java). [Programming guide →](https://tornadovm.readthedocs.io/en/latest/programming.html#loop-parallel-api)

---

## KernelContext API

Write the kernel in Java with the same thread-indexing model you'd use in CUDA: global/local thread IDs, local memory, and barriers, with identical semantics across CUDA, OpenCL, and SYCL. TornadoVM still JIT-compiles the bytecode to a GPU kernel at runtime and manages all host↔device data transfers for you; on NVIDIA GPUs that kernel is emitted as **CUDA PTX** and compiled through NVRTC to a native cubin.

`KernelContext` gives you the full GPU programming model while TornadoVM handles memory management and runs the *identical* code across all four backends. Don't need that control? Drop `KernelContext` and use the Loop Parallel API above instead — both styles combine in the same `TaskGraph`. Full runnable example: [MatrixMultiplication2DV1.java](tornado-examples/src/main/java/uk/ac/manchester/tornado/examples/kernelcontext/compute/MatrixMultiplication2DV1.java). [Programming guide →](https://tornadovm.readthedocs.io/en/latest/programming.html)

---

<a id="tilecontext-api-cutile"></a>
## TileContext API (CuTile) 🆕

*CUDA backend only; lives on `develop`, not yet in the 6.0.0 release.* A task whose kernel takes a **`TileContext`** is compiled through **NVIDIA CUDA Tile** (`nvcc -tilecubin --tile-only`) instead of the SIMT path. You write what **one tile block** does; the tile compiler decides how many threads back it, which tensor-core instruction to issue, and how tiles move through shared memory. **Nothing in the API names a thread, a warp or a fragment.**

```java
WorkerGrid2D worker = new WorkerGrid2D(n / TILE, n / TILE);   // TILE BLOCKS, not threads
```

- **It composes with everything else.** A `@Parallel` kernel, a `KernelContext` kernel, a `TileContext` kernel and a native cuBLAS call sit in one `TaskGraph`, share device buffers on one stream, and are captured into **a single CUDA Graph** replayed with one launch — see [`TestTileChaining`](tornado-unittests/src/main/java/uk/ac/manchester/tornado/unittests/tile/TestTileChaining.java).
- **It is still ordinary Java.** Every `TileContext` operation has a JVM implementation, so the same method runs and debugs on the CPU.
- **Tile shapes are compile-time constants; extents are not.** The shape specialises the kernel, the problem size does not.

[Tile API guide →](docs/source/tile-api.rst) · [runnable examples](tornado-examples/src/main/java/uk/ac/manchester/tornado/examples/tile) (matmul three ways, softmax, attention, quantized projection)

---

## 🟩 The NVIDIA ecosystem, native to Java

On NVIDIA hardware, TornadoVM is an open-source on-ramp to the whole CUDA software stack, callable from the same `TaskGraph` you already use. Generated kernels and native library calls **share TornadoVM-managed device buffers on one CUDA stream**, so a JIT-compiled kernel can feed a cuBLAS call and consume its output with no extra copies and no host synchronization.

| Capability | What it gives Java developers |
|---|---|
| **CUDA backend** | Java bytecode → Graal IR → **CUDA PTX → NVRTC → cubin**, JIT-compiled and specialized to your data sizes and GPU at runtime. |
| **cuBLAS / cuBLASLt** library tasks | SGEMV, SGEMM, strided-batched, TF32 and FP16 GemmEx on Tensor Cores, and cuBLASLt with plan caching and fused `BIAS` / `GELU_BIAS` epilogues — one library task replaces a GEMM plus a separate activation kernel. |
| **cuFFT** library tasks | C2C, R2C / C2R, and Z2Z transforms (1D and 2D) with capture-safe plan caching; FFT-filter pipelines mix with JIT kernels in one graph. |
| **cuDNN** library tasks | Deep-learning primitives through the cuDNN graph API, including fused scaled-dot-product (flash) attention via cudnn-frontend. |
| **Tensor Core MMA intrinsics** | `mma.sync` exposed through `KernelContext` (`mmaLoadA/B`, `mma`, `mmaStore`) — FP16 (`m16n8k16` → FP32) and INT8 (`m16n8k32` → INT32), with swizzled shared-memory staging. Not a binding: real CUDA generated from Java. |
| **CUDA Graphs** | `executionPlan.withCUDAGraph()` records kernels, library calls, and transfers into a captured graph and replays them with a single `cuGraphLaunch`. |
| **CUDA Tile** (`TileContext`) 🆕 | A second compilation path: kernels written over *tiles* go through `nvcc -tilecubin`, and the tile compiler picks the threads, the tensor-core instruction, and the shared-memory staging. [Details](#tilecontext-api-cutile). |

Mixing your own kernels with NVIDIA's tuned libraries looks like this:

```java
TaskGraph tg = new TaskGraph("hybrid")
    .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix, vector)
    .task("preprocess", MyKernels::preprocess, matrix)              // JIT-compiled Java kernel
    .libraryTask("gemv", CuBlas::cublasSgemv,                       // native cuBLAS call
            CUBLAS_OP_T, m, n, alpha, matrix, lda, vector, incx, beta, output, incy)
    .task("postprocess", MyKernels::activate, output)              // JIT-compiled Java kernel
    .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
    plan.withCUDAGraph().execute();   // captured once, replayed each iteration
}
```

Library bindings are discovered via Java `ServiceLoader`, bind the calls through `java.lang.foreign` (a JNI module only if the library genuinely needs compiled C/C++), and any native library joins the graph with no core runtime changes. TornadoVM is a member of the **NVIDIA Inception Program** and has presented this work at **NVIDIA GTC**. [Hybrid API guide →](https://tornadovm.readthedocs.io/en/latest/)


---

## What people build with it

| Project | What it shows |
|---|---|
| 🦙 [**GPULlama3.java**](https://github.com/beehive-lab/GPULlama3.java) | LLM inference (Llama 3, Qwen 3, Mistral, Phi-3, Granite, DeepSeek distills) in pure Java — **117 tok/s on an RTX 5090**, official GPU engine for [LangChain4j](https://docs.langchain4j.dev/integrations/language-models/gpullama3-java) and [Quarkus](https://docs.quarkiverse.io/quarkus-langchain4j/dev/gpullama3-chat-model.html) |
| 🔆 [**TornadoVM-Ray-Tracer**](https://github.com/Vinhixus/TornadoVM-Ray-Tracer) | Real-time ray tracing in Java, interactive frame rates on consumer GPUs |
| 📷 [**kfusion-tornadovm**](https://github.com/beehive-lab/kfusion-tornadovm) | KinectFusion 3D reconstruction — a full computer-vision pipeline on integrated and discrete GPUs |
| 📷 Gaia Mission (ESA) | High-performance algorithms for space exploration at the European Space Agency use TornadoVM |


TornadoVM is used to accelerate machine learning and deep learning, computer vision, physics simulations, financial applications, computational photography, and signal processing. Building something with TornadoVM? [Tell us](https://github.com/beehive-lab/TornadoVM/discussions) — we feature community projects.

<!-- TODO: add a "Used in production by" line once 2–3 adopters agree to be named -->

---

## ⚡ Quick start

### Prerequisites

- min **JDK 21** — `JAVA_HOME` must point to it
- GCC/G++ ≥ 13, plus the driver for your target (OpenCL runtime, CUDA Toolkit, or macOS for Metal)
- For the NVIDIA library tasks (cuBLAS / cuFFT / cuDNN): the **CUDA Toolkit** with the corresponding libraries; on systems with multiple toolkits, `/usr/local/cuda` (or `$CUDA_PATH`) is preferred

### Install via SDKMAN!

```bash
sdk install tornadovm
```

Pick a backend-specific build if you prefer a smaller install. Candidate versions are per-JDK (swap `jdk21` below for `jdk22plus` to install a build for that JDK line):

| Backend | SDKMAN! version | Targets |
|---|---|---|
| OpenCL *(default)* | `6.0.0-opencl` | NVIDIA / AMD / Intel GPUs, multi-core CPUs, FPGAs |
| CUDA | `6.0.0-cuda` | **NVIDIA GPUs (CUDA) — codegen, Tensor Cores, cuBLAS/cuFFT/cuDNN library tasks** |
| Metal 🆕 | `6.0.0-metal` | Apple Silicon GPUs (M1–M4), natively via MSL |
| All backends | `6.0.0-full` | Everything above |

Binaries are also on the [official website](https://www.tornadovm.org/downloads). For [Docker](https://github.com/beehive-lab/docker-tornado#docker-for-tornadovm) and [AWS (CPUs/GPUs)](https://tornadovm.readthedocs.io/en/latest/cloud.html) see the linked guides.

### Verify your devices

```bash
tornado --devices
```

### Run your first program

```bash
# Unix (Linux/macOS)
java @$TORNADOVM_HOME/tornado-argfile \
  -cp $TORNADOVM_HOME/share/java/tornado/tornado-examples-6.0.0.jar \
  uk.ac.manchester.tornado.examples.compute.MatrixVectorRowMajor

# Windows 10+
java @%TORNADOVM_HOME%\tornado-argfile ^
  -cp %TORNADOVM_HOME%\share\java\tornado\tornado-examples-6.0.0.jar ^
  uk.ac.manchester.tornado.examples.compute.MatrixVectorRowMajor
```

More examples — NBody, DFT, KMeans, matrix kernels, reductions: [tornado-examples](tornado-examples/src/main/java/uk/ac/manchester/tornado/examples)

---

## 📦 Use TornadoVM in your project

Maven Central coordinates are per-JDK — pin the `-jdk21` / `-jdk22plus` version matching the JDK you build with:

```xml
<dependencies>
  <dependency>
    <groupId>io.github.beehive-lab</groupId>
    <artifactId>tornado-api</artifactId>
    <version>6.0.0-jdk21</version>
  </dependency>
  <dependency>
    <groupId>io.github.beehive-lab</groupId>
    <artifactId>tornado-runtime</artifactId>
    <version>6.0.0-jdk21</version>
  </dependency>
</dependencies>
```

---

## ❓ FAQ


<details>
<summary><b>What can TornadoVM do on NVIDIA GPUs specifically?</b></summary>

On NVIDIA hardware TornadoVM JIT-compiles your Java kernels to **CUDA PTX** and compiles them through NVRTC to native cubins. On top of that, it integrates the NVIDIA software ecosystem directly into the `TaskGraph`: **cuBLAS / cuBLASLt** (including TF32 and FP16 GemmEx on Tensor Cores and fused epilogues), **cuFFT**, and **cuDNN** (including fused flash attention) are available as *library tasks* that share device buffers and a CUDA stream with your generated kernels. You can also target **Tensor Cores** directly from Java via `mma.sync` intrinsics (FP16 and INT8), and capture the whole pipeline into a **CUDA Graph** for single-launch replay. On `develop` there is a second compilation path as well: a kernel taking a **`TileContext`** is compiled through **NVIDIA CUDA Tile**, where a tile rather than a thread is the unit of work. See [The NVIDIA ecosystem, native to Java](#-the-nvidia-ecosystem-native-to-java).
</details>

<details>
<summary><b>What's the licensing situation for commercial use?</b></summary>

The parts your application links against — **Tornado-API** and all the modules you code with — are **Apache 2.0**. The runtime and drivers are **GPLv2 with Classpath Exception**, the same license as OpenJDK itself: it does not impose copyleft obligations on your application, exactly as running on OpenJDK doesn't. Full per-module breakdown [below](#licenses-per-module).
</details>

<details>
<summary><b>Does it replace my JVM?</b></summary>

No. TornadoVM is a plug-in to your existing OpenJDK or GraalVM installation. It complements the JVM with the ability to offload selected tasks to accelerators, handling device memory management and kernel orchestration. Everything else in your application runs on the JVM as normal.
</details>

<details>
<summary><b>Which hardware is supported?</b></summary>

Multi-core CPUs; dedicated GPUs from NVIDIA, AMD, and Intel; and integrated GPUs (Apple Silicon M1–M4, Intel HD Graphics, ARM Mali). Backends can be installed individually or together, and tasks can migrate between devices at runtime. NVIDIA GPUs can be driven either through the **CUDA** backend (with the library and Tensor Core features above) or through the OpenCL backend.
</details>

<details>
<summary><b>How fast is it?</b></summary>

It depends on the workload — data-parallel kernels with enough arithmetic intensity see order-of-magnitude speedups over sequential Java; see the [benchmarking guide](https://tornadovm.readthedocs.io/en/latest/benchmarking.html) and the [GPULlama3.java performance tables](https://github.com/beehive-lab/GPULlama3.java#-performance) for end-to-end numbers on real applications (e.g. **117 tok/s for Llama-3 inference on an RTX 5090**). For compute-bound linear algebra, the cuBLAS library tasks reach the tuned vendor throughput — including full FP16 Tensor Core rates — from Java.
</details>

---

## 🤝 Contributing & community

- 💬 Questions and ideas: [GitHub Discussions](https://github.com/beehive-lab/TornadoVM/discussions) or the [TornadoVM Slack](https://join.slack.com/t/tornadovmcommunity/shared_invite/zt-3ai2wyqva-bKz~cQRFlaJ~ZnPrbkwIEw)
- 🛠️ Building from source: [INSTALL_FROM_SOURCE.md](INSTALL_FROM_SOURCE.md)
- 📋 How to contribute: [CONTRIBUTING.md](CONTRIBUTING.md) · [`good first issue`](https://github.com/beehive-lab/TornadoVM/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22)
- 🤖 Working with an AI coding agent? The repo ships a [TornadoVM developer skill](tornadovm-skill.skill) (build, test, debug workflows) for Claude.
- 🏛️ Academic & industrial collaborations: [contact us](https://www.tornadovm.org/contact-us)

---

## 📚 Resources & publications

Videos, presentations, and articles: [resources](https://tornadovm.readthedocs.io/en/latest/resources.html). Selected academic publications: [publications](https://tornadovm.readthedocs.io/en/latest/publications.html).

If you use **TornadoVM ≥ 0.2** in research, please cite:

```bibtex
@inproceedings{Fumero:DARHH:VEE:2019,
 author    = {Fumero, Juan and Papadimitriou, Michail and Zakkak, Foivos S. and
              Xekalaki, Maria and Clarkson, James and Kotselidis, Christos},
 title     = {{Dynamic Application Reconfiguration on Heterogeneous Hardware}},
 booktitle = {Proceedings of the 15th ACM SIGPLAN/SIGOPS International
              Conference on Virtual Execution Environments},
 series    = {VEE '19},
 year      = {2019},
 doi       = {10.1145/3313808.3313819},
 publisher = {Association for Computing Machinery}
}
```

For **Tornado 0.1** (initial release), cite [Clarkson et al., ManLang '18](https://doi.org/10.1145/3237009.3237016).

## Licenses per module

Link the **Tornado-API** (Apache 2.0) into your application.

| Modules | License |
|---|---|
| Tornado-API, Tornado-Assembly, Tornado-scripts, Tornado-Annotation, Tornado-Unittests, Tornado-Benchmarks, Tornado-Examples, Tornado-Matrices, Tornado-Drivers-OpenCL-Headers | [Apache 2.0](LICENSE_APACHE2) |
| Tornado-Runtime, Tornado-Drivers | [GPLv2 with Classpath Exception](LICENSE_GPLv2CE) |

## Acknowledgments

Partially funded by [Intel Corporation](https://www.intel.com/) and by EU & UKRI grants (most recent first): [AERO](https://aero-project.eu/) (101092850), [P2CODE](https://p2code-project.eu/) (101093069), [ENCRYPT](https://encrypt-project.eu) (101070670), [TANGO](https://tango-project.eu) (101070052), [ELEGANT](https://www.elegant-h2020.eu/) (957286), [E2Data](https://e2data.eu) (780245), [ACTiCLOUD](https://acticloud.eu) (732366); and EPSRC grants [PAMELA](http://apt.cs.manchester.ac.uk/projects/PAMELA/) (EP/K008730/1) and [AnyScale Apps](https://gow.epsrc.ukri.org/NGBOViewGrant.aspx?GrantRef=EP/L000725/1) (EP/L000725/1).

Meet the [team](https://www.tornadovm.org/about-us).
