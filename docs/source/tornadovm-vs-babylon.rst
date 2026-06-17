.. _tornadovm-vs-babylon:

TornadoVM and Project Babylon / HAT: How They Compare
=====================================================

"Write Java, run it on a GPU" is suddenly a crowded idea. TornadoVM has shipped
that capability for years; OpenJDK's **Project Babylon** — and its
**HAT (Heterogeneous Accelerator Toolkit)** sub-project — is now building a
foundation for it inside the JDK itself. This article explains what each one is,
how their approaches differ, and why they are more complementary than
competitive.

.. note::

   This is a technical comparison written for the TornadoVM community. It cites
   public sources and aims to be fair to both projects; it is not an official
   statement from either. Project Babylon and HAT are evolving rapidly, so check
   the primary sources linked below for the current state.

What each project is
--------------------

**TornadoVM** is a mature heterogeneous programming framework and JIT compiler,
developed openly since the mid-2010s (originating from research at the
University of Manchester). You annotate ordinary Java with ``@Parallel`` /
``@Reduce``, compose work into a ``TaskGraph``, and TornadoVM compiles the
*bytecode* of your methods into GPU/accelerator kernels at run time. It ships
multiple production backends — **OpenCL C**, **NVIDIA PTX**, and **SPIR-V**
(via Intel Level Zero) — plus an experimental **Apple Metal** backend, and runs
on CPUs, integrated and discrete GPUs (Intel, NVIDIA, AMD, Apple, ARM Mali) and
FPGAs.

**Project Babylon** is an OpenJDK project whose central deliverable is *code
reflection*: a standard way to obtain, analyse and transform a symbolic model
(a "code model") of Java code, from inside Java. The motivating example is
extending Java's reach to foreign programming models — GPUs, differentiable
programming, SQL — without leaving the language. **HAT** is the Babylon
sub-project that demonstrates this for accelerators: it turns the code model of
a Java method into GPU kernels, targeting backends such as OpenCL, CUDA, HIP and
SPIR-V.

The core difference: bytecode JIT vs. code reflection
-----------------------------------------------------

The cleanest way to understand the two projects is *what they compile from*.

- **TornadoVM compiles from bytecode.** Its JIT is built on the Graal compiler.
  It takes the already-compiled bytecode of your task method, builds a
  compiler graph, specialises it for the target device, and emits a kernel.
  This means it works on today's JDKs as a plug-in, with no language or JVM
  changes required.

- **HAT compiles from a code model.** Babylon's code reflection exposes a
  high-level, source-faithful representation of the method — closer to the
  original Java semantics than bytecode, and designed to be analysed and
  transformed in user code. HAT consumes that model to build kernels.

Each approach has trade-offs. Bytecode is universally available and stable, but
some source-level intent (loop structure, types) has to be *recovered* from it.
A code model preserves that intent directly, which is attractive for building
new accelerator front-ends — but it depends on the code-reflection machinery
landing in the JDK, which is still in development.

Maturity and breadth today
---------------------------

This is where the projects are at very different points on the timeline.

**Backends.** TornadoVM has three production backends (OpenCL, PTX, SPIR-V) and
a fourth in progress (Metal). HAT is a younger, demonstrative toolkit; its
backends are evolving alongside Babylon and are best treated as a preview.

**Runtime features.** TornadoVM is not only a set of JIT compilers — it is also
a runtime. It provides:

- a **TaskGraph** API with explicit host/device data-transfer modes;
- **dynamic reconfiguration and live task migration** across devices at run time;
- **multi-device and multi-backend** execution of concurrent kernels;
- **batch processing** for data larger than device memory;
- a **live profiler** across platforms, including FPGAs.

These are the result of years of work and are not things a foundational JDK
feature provides on its own.

**Ecosystem and AI.** The TornadoVM ecosystem includes a working GPU LLM
inference stack — ``GPULlama3.java`` runs Llama-family models on the GPU in pure
Java on top of TornadoVM. That is a concrete, runnable demonstration of the
"Java + GPU" story for AI workloads available now.

Where each shines
-----------------

- **Choose TornadoVM today** when you want to take real Java code and run it on a
  GPU, FPGA or multi-core CPU *now*, across vendors, with profiling, multi-device
  scheduling and a path to GPU-accelerated AI — all on a standard JDK.

- **Watch Babylon / HAT** if you care about Java's *long-term* foundations for
  heterogeneous computing: a standard, in-language way to reflect on and
  transform code that many tools (not just GPU compilers) can build on. It is a
  platform capability rather than a ready-to-ship accelerator runtime.

How they could coexist
----------------------

These projects are not mutually exclusive, and the most interesting outcome is
one where they reinforce each other. Code reflection could give front-ends a
cleaner, source-faithful entry point into Java programs; a mature optimising
runtime like TornadoVM — with its multi-backend code generation, device
scheduling and profiling — is exactly the kind of layer that could *consume* such
models and turn them into fast kernels on real hardware. In other words, Babylon
could standardise the front door while TornadoVM continues to provide a hardened
back end and runtime. Standardisation in the JDK tends to *grow* the audience for
everything in a space, not shrink it.

The short version: Babylon/HAT is laying foundations in the JDK; TornadoVM is a
mature, multi-backend system you can run today, with a working AI stack on top.
They are answering related questions at different layers of the stack.

Sources and further reading
---------------------------

- Project Babylon (OpenJDK): `https://openjdk.org/projects/babylon/ <https://openjdk.org/projects/babylon/>`__
- HAT (Heterogeneous Accelerator Toolkit), in the Babylon repository:
  `https://github.com/openjdk/babylon <https://github.com/openjdk/babylon>`__
- TornadoVM project site: `https://www.tornadovm.org <https://www.tornadovm.org>`__
- TornadoVM source: `https://github.com/beehive-lab/TornadoVM <https://github.com/beehive-lab/TornadoVM>`__
- GPULlama3.java: `https://github.com/beehive-lab/GPULlama3.java <https://github.com/beehive-lab/GPULlama3.java>`__
