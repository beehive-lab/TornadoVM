.. _changelog:

TornadoVM Changelog
===================

This file summarizes the new features and major changes for each *TornadoVM* version.

CHANGELOG

TornadoVM 1.1.1
---------------
07/07/25

Improvements
~~~~~~~~~~~~

- `#657 <https://github.com/beehive-lab/TornadoVM/pull/657>`_: Optimize to reuse the allocated buffers for batch processing.
- `#659 <https://github.com/beehive-lab/TornadoVM/pull/659>`_: Fixed object state to be the one from the last executed TaskGraph.
- `#660 <https://github.com/beehive-lab/TornadoVM/pull/660>`_: New ``PERSIST`` bytecode to improve object lifecycle tracking.
- `#661 <https://github.com/beehive-lab/TornadoVM/pull/661>`_: Saving the TornadoVM Bytecodes in a log file.
- `#660 <https://github.com/beehive-lab/TornadoVM/pull/669>`_: Distinguish the data transfer mode when logging the execution of the ``TRANSFER_TO_DEVICE_ONCE`` Bytecode.
- `#667 <https://github.com/beehive-lab/TornadoVM/pull/667>`_: Update documentation of the TornadoVM flags.
- `#670 <https://github.com/beehive-lab/TornadoVM/pull/670>`_: Refactoring of the ``Matrix4x4Float`` type.
- `#674 <https://github.com/beehive-lab/TornadoVM/pull/674>`_: Updated project links in ``README``.
- `#675 <https://github.com/beehive-lab/TornadoVM/pull/675>`_: Avoid rescheduling ``IfNodes`` used for loop-bound evaluation.
- `#676 <https://github.com/beehive-lab/TornadoVM/pull/676>`_: Added unit-tests for Transformer Compute Kernels.
- `#679 <https://github.com/beehive-lab/TornadoVM/pull/679>`_: Added Matrix-Vector Row-Major compute example.
- `#683 <https://github.com/beehive-lab/TornadoVM/pull/683>`_: Mark flash attention unittest unsupported for SPIR-V.
- `#684 <https://github.com/beehive-lab/TornadoVM/pull/684>`_: Performance improvements for processing with Dynamic Reconfiguration.
- `#685 <https://github.com/beehive-lab/TornadoVM/pull/685>`_: Dynamic reconfiguration refactored.
- `#686 <https://github.com/beehive-lab/TornadoVM/pull/686>`_: New API Functions for warmup.
- `#693 <https://github.com/beehive-lab/TornadoVM/pull/693>`_: Disabling fast math to support FMA in PTX.
- `#695 <https://github.com/beehive-lab/TornadoVM/pull/695>`_: Update ``tornadovm-installer`` script to be interactive.
- `#696 <https://github.com/beehive-lab/TornadoVM/pull/696>`_:  Increase sizes for auxiliary data structures related with the number of Tasks in a TaskGraph.
- `#697 <https://github.com/beehive-lab/TornadoVM/pull/697>`_: Added auto-deps mode in ``tornadovm-installer`` and restored backend and jdk console arguments.
- `#698 <https://github.com/beehive-lab/TornadoVM/pull/698>`_: Update tornadovm-installer changes in README.


Compatibility
~~~~~~~~~~~~
- `#668 <https://github.com/beehive-lab/TornadoVM/pull/668>`_: Updated build instructions for RISC-V systems.


Bug Fixes
~~~~~~~~~~~~

- `#664 <https://github.com/beehive-lab/TornadoVM/pull/664>`_:  Fix kernel name in PTX with sanitizer check.
- `#666 <https://github.com/beehive-lab/TornadoVM/pull/666>`_: Fix GridScheduler for execution plans that have multiple TaskGraphs.
- `#671 <https://github.com/beehive-lab/TornadoVM/pull/671>`_: Fix ANSI espace characters for logging TornadoVM Bytecodes.
- `#677 <https://github.com/beehive-lab/TornadoVM/pull/677>`_: Fix 1.0/sqrt(x) replacement with native rsqrt(x) function.
- `#678 <https://github.com/beehive-lab/TornadoVM/pull/678>`_: Fix profiling on macOS systems, regarding accessing UPS metrics.
- `#681 <https://github.com/beehive-lab/TornadoVM/pull/681>`_: Fix closing bracket for flash attention.
- `#688 <https://github.com/beehive-lab/TornadoVM/pull/688>`_: Fix state after warmup phase.

TornadoVM 1.1.0
---------------
31/03/25

Improvements
~~~~~~~~~~~~

- `#620 <https://github.com/beehive-lab/TornadoVM/pull/620>`_: Support of computation with mixed precision ``FP16`` to  ``FP32`` for matrix operations.
- `#622 <https://github.com/beehive-lab/TornadoVM/pull/622>`_: New API to allow buffer mapping between two different buffers on the hardware accelerator.
- `#624 <https://github.com/beehive-lab/TornadoVM/pull/624>`_: Enhanced TornadoVM profiler with correct information for the ``UNDER_DEMAND`` transfer to host data.
- `#627 <https://github.com/beehive-lab/TornadoVM/pull/627>`_: New feature to persist data on the hardware accelerator, and consume data already allocated on the hardware accelerator.
- `#630 <https://github.com/beehive-lab/TornadoVM/pull/630>`_: Support for atomics using the kernel API for OpenCL and PTX backends.
- `#636 <https://github.com/beehive-lab/TornadoVM/pull/636>`_: TornadoVM bytecode logging improved.
- `#642 <https://github.com/beehive-lab/TornadoVM/pull/642>`_: Math functions extended: ``acosh`` and ``asinh`` supported for OpenCL and SPIR-V.
- `#645 <https://github.com/beehive-lab/TornadoVM/pull/645>`_: Memory deallocations improved. Action by default when closing the ``TornadoExecutionPlan`` resource.


Compatibility
~~~~~~~~~~~~

- `#625 <https://github.com/beehive-lab/TornadoVM/pull/625>`_: Documentation to build on RISC-V updated.
- `#632 <https://github.com/beehive-lab/TornadoVM/pull/632>`_: Add maven build with Single thread.
- `#633 <https://github.com/beehive-lab/TornadoVM/pull/633>`_: Add tests for running multiple task graphs with different grid schedulers.
- `#638 <https://github.com/beehive-lab/TornadoVM/pull/638>`_: Add tests to check force copy in buffers and persist buffers on the hardware accelerator.
- `#640 <https://github.com/beehive-lab/TornadoVM/pull/640>`_: Rename XPUFuffer to FieldBuffer for all backends.
- `#649 <https://github.com/beehive-lab/TornadoVM/pull/649>`_: Update the fast mode to live mode for testing.
- `#654 <https://github.com/beehive-lab/TornadoVM/pull/654>`_: Add loop condition test in white list.


Bug Fixes
~~~~~~~~~~~~

- `#626 <https://github.com/beehive-lab/TornadoVM/pull/626>`_: Fix data accessors when using the ``UNDER_DEMAND`` transfer to host innovation from the task-graph.
- `#628 <https://github.com/beehive-lab/TornadoVM/pull/628>`_: Device filtering API fixed to use device type and device names.
- `#635 <https://github.com/beehive-lab/TornadoVM/pull/635>`_: Update nodes for local memory to be subtype of ``ValueNode`` instead of ``ConstantNode`` in the TornadoVM IR.
- `#639 <https://github.com/beehive-lab/TornadoVM/pull/639>`_: Fix subgraph execution when combining with the ``GridScheduler``.
- `#644 <https://github.com/beehive-lab/TornadoVM/pull/644>`_: Fix TornadoVM execution frame setter.
- `#646 <https://github.com/beehive-lab/TornadoVM/pull/646>`_: Fix shared memory buffers across task-graphs when no new allocation is present as new parameters for the following task-graphs.
- `#647 <https://github.com/beehive-lab/TornadoVM/pull/647>`_: Fix ``UNDER_DEMAND`` invocation for the batch processor mode and read-write arrays.
- `#651 <https://github.com/beehive-lab/TornadoVM/pull/651>`_: Fix memory mapping regions for the PTX Backend.
- `#653 <https://github.com/beehive-lab/TornadoVM/pull/653>`_: Object repetition with shared buffers on ``ON_DEVICE`` bytecodes.


TornadoVM 1.0.10
---------------
31/01/25

Improvements
~~~~~~~~~~~~

- `#608 <https://github.com/beehive-lab/TornadoVM/pull/608>`_: Selective execution with multiple SPIR-V runtimes (either OpenCL, Intel Level Zero, or both) to unlock execution on RISC-V systems.
- `#611 <https://github.com/beehive-lab/TornadoVM/pull/611>`_: Support of ``HalfFloat`` for Matrix Types (``FP16`` -> ``FP16``).

Compatibility
~~~~~~~~~~~~

- `#607 <https://github.com/beehive-lab/TornadoVM/pull/607>`_: WSL installation and configuration updated for WSL Ubuntu 24 LTS and Windows 11.
- `#609 <https://github.com/beehive-lab/TornadoVM/pull/609>`_: Documentation and patch for RISC-V64 updated.
- `#610 <https://github.com/beehive-lab/TornadoVM/pull/610>`_: Maven dependency updated
- `#612 <https://github.com/beehive-lab/TornadoVM/pull/612>`_: Re-enable colours in maven builds on Linux.

Bug Fixes
~~~~~~~~~~~~

- `#606 <https://github.com/beehive-lab/TornadoVM/pull/606>`_: Fix data sizes in benchmark suite.
- `#613 <https://github.com/beehive-lab/TornadoVM/pull/613>`_: Fix code formatter.
- `#614 <https://github.com/beehive-lab/TornadoVM/pull/614>`_: Fix flags for the benchmark pipeline in Jenkins.
- `#615 <https://github.com/beehive-lab/TornadoVM/pull/615>`_: Fix code style based on the formatter.
- `#616 <https://github.com/beehive-lab/TornadoVM/pull/616>`_: Fix atomics for the Kernel API and the OpenCL backend.


TornadoVM 1.0.9
---------------
20th December 2024

Improvements
~~~~~~~~~~~~

- `#573 <https://github.com/beehive-lab/TornadoVM/pull/573>`_: Enhanced output of unit-tests with a summary  of pass-rates and fail-rates.
- `#576 <https://github.com/beehive-lab/TornadoVM/pull/576>`_: Extended support for 3D matrices.
- `#580 <https://github.com/beehive-lab/TornadoVM/pull/580>`_: Extended debug information for execution plans.
- `#584 <https://github.com/beehive-lab/TornadoVM/pull/584>`_: Added helper menu for the ``tornado`` launcher script when no arguments are passed.
- `#589 <https://github.com/beehive-lab/TornadoVM/pull/589>`_: Enable partial loop unrolling for all backends.
- `#594 <https://github.com/beehive-lab/TornadoVM/pull/594>`_: Added RISC-V 64 CPU port support to run OpenCL with vector instructions RVV 1.0 (using the Codeplay OCK Toolkit).
- `#598 <https://github.com/beehive-lab/TornadoVM/pull/598>`_: OpenCL low-level buffers tagged as read, write and read/write based on the data dependency analysis.
- `#601 <https://github.com/beehive-lab/TornadoVM/pull/601>`_: Feature to select an immutable task graph to execute from a multi-task graph execution plan.


Compatibility
~~~~~~~~~~~~~

- `#570 <https://github.com/beehive-lab/TornadoVM/pull/570>`_:  Extended timeout for all suite of unit-tests.
- `#579 <https://github.com/beehive-lab/TornadoVM/pull/579>`_: Removed legacy JDK 8 and JDK11 build options from the TornadoVM installer.
- `#582 <https://github.com/beehive-lab/TornadoVM/pull/582>`_: Restored tornado runner scripts for IntellIJ.
- `#583 <https://github.com/beehive-lab/TornadoVM/pull/583>`_: Automatic generation of IDE IntelliJ configuration runner files from the TornadoVM command.
- `#597 <https://github.com/beehive-lab/TornadoVM/pull/597>`_: Updated white-list of unit-test and checkstyle improved.


Bug Fixes
~~~~~~~~~

- `#571 <https://github.com/beehive-lab/TornadoVM/pull/571>`_: Fix issues with bracket closing for if/loops conditions.
- `#572 <https://github.com/beehive-lab/TornadoVM/pull/572>`_: Fix for printing default execution plans (execution plans with default parameters).
- `#575 <https://github.com/beehive-lab/TornadoVM/pull/575>`_: Fix the Level Zero version used for building the SPIR-V backend.
- `#577 <https://github.com/beehive-lab/TornadoVM/pull/577>`_: Fix checkstyle.
- `#587 <https://github.com/beehive-lab/TornadoVM/pull/587>`_: Fix thread scheduler for new NVIDIA Drivers.
- `#592 <https://github.com/beehive-lab/TornadoVM/pull/592>`_: Fix ``Float.POSITIVE_INFINITY`` and ``Float.NEGATIVE_INFINITIVE`` constants for the OpenCL, CUDA and SPIR-V backends.
- `#596 <https://github.com/beehive-lab/TornadoVM/pull/596>`_: Fix extra closing bracket during the code-generation for the FPGAs.
- Remove the intermediate CUDA pinned memory regions in the JNI code: `link <https://github.com/beehive-lab/TornadoVM/commit/9c3f8ce7eb917f30788710b390c07a072ecc49fb>`_
- Fix bitwise negation operations for the PTX backend:  `link <https://github.com/beehive-lab/TornadoVM/commit/0db1cd3e7fd90accd737ca2bfd6d2450c40f3713>`_
- ``GetBackendImpl::getAllDevices`` thread-safe: `link <https://github.com/beehive-lab/TornadoVM/commit/0d4425264ffe0633ea79c8aba91233591059d3fd>`_
- Check size elements for memory segments: `link <https://github.com/beehive-lab/TornadoVM/commit/4360385156236bb2397debeea65fedea349c6bca>`_.


TornadoVM 1.0.8
---------------
30th September 2024

Improvements
~~~~~~~~~~~~

- `#565 <https://github.com/beehive-lab/TornadoVM/pull/565>`_: New API call in the Execution Plan to log/trace the executed configuration plans.
- `#563 <https://github.com/beehive-lab/TornadoVM/pull/563>`_: Expand the TornadoVM profiler with Level Zero Sysman Energy Metrics.
- `#559 <https://github.com/beehive-lab/TornadoVM/pull/559>`_: Refactoring Power Metric handlers for PTX and OpenCL.
- `#548 <https://github.com/beehive-lab/TornadoVM/pull/548>`_: Benchmarking improvements.
- `#549 <https://github.com/beehive-lab/TornadoVM/pull/549>`_: Prebuilt API tests added using multiple backend-setup.
- Add internal tests for monitoring memory management `(link) <https://github.com/beehive-lab/TornadoVM/commit/0644225a641bd859372743b59d46c6c9a4613337>`_.

Compatibility
~~~~~~~~~~~~~
- `#561 <https://github.com/beehive-lab/TornadoVM/pull/561>`_: Build for OSx 14.6 and OSx 15 fixed.

Bug Fixes
~~~~~~~~~

- `#564 <https://github.com/beehive-lab/TornadoVM/pull/564>`_: Jenkins configuration fixed to run KFusion per backend.
- `#562 <https://github.com/beehive-lab/TornadoVM/pull/562>`_: Warmup action from the Execution Plan fixed to run with correct internal IDs.
- `#557 <https://github.com/beehive-lab/TornadoVM/pull/557>`_: Shared Execution Plans Context fixed.
- `#553 <https://github.com/beehive-lab/TornadoVM/pull/553>`_: OpenCL compiler flags for Intel Integrated GPUs fixed.
- `#552 <https://github.com/beehive-lab/TornadoVM/pull/552>`_: Fixed runtime to select any device among multiple SPIR-V devices.
- Fixed zero extend arithmetic operations: `link <https://github.com/beehive-lab/TornadoVM/commit/ea7b60263072ba0299da205cb920d0c68b3d1749>`_


TornadoVM 1.0.7
----------------
30th August 2024

Improvements
~~~~~~~~~~~~

- `#468 <https://github.com/beehive-lab/TornadoVM/pull/468>`_: Cleanup Abstract Metadata Class.
- `#473 <https://github.com/beehive-lab/TornadoVM/pull/473>`_: Add maven plugin to build TornadoVM source for the releases.
- `#474 <https://github.com/beehive-lab/TornadoVM/pull/474>`_: Refactor <X>TornadoDevice to place common methods in the ``TornadoXPUInterface``.
- `#482 <https://github.com/beehive-lab/TornadoVM/pull/482>`_: Help messages improved when an out-of-memory exception is raised.
- `#484 <https://github.com/beehive-lab/TornadoVM/pull/484>`_: Double-type for the trigonometric functions added in the ``TornadoMath`` class.
- `#487 <https://github.com/beehive-lab/TornadoVM/pull/487>`_: Prebuilt API simplified.
- `#494 <https://github.com/beehive-lab/TornadoVM/pull/494>`_: Add test to trigger unsupported features related to direct use of Memory Segments.
- `#509 <https://github.com/beehive-lab/TornadoVM/pull/509>`_: Add a quick pass configuration to skip the heavy tests during active development.
- `#532 <https://github.com/beehive-lab/TornadoVM/pull/532>`_: Improve thread scheduler to support RISC-V Accelerators from Codeplay.
- `#533 <https://github.com/beehive-lab/TornadoVM/pull/533>`_: Support for scalar values to be passed via lambda expressions as tasks.
- `#538 <https://github.com/beehive-lab/TornadoVM/pull/538>`_: ``README`` file updated.
- `#539 <https://github.com/beehive-lab/TornadoVM/pull/539>`_: Refactor core classes and add new API methods to pass compilation flags to the low-level driver compilers (OpenCL, PTX and Level Zero).
- `#542 <https://github.com/beehive-lab/TornadoVM/pull/542>`_: Tagged LevelZero JNI and Beehive Toolkit dependencies added in the build and installer.

Compatibility
~~~~~~~~~~~~~

- `#465 <https://github.com/beehive-lab/TornadoVM/pull/465>`_: Support for JDK 22 and GraalVM 24.0.2.
- `#486 <https://github.com/beehive-lab/TornadoVM/pull/486>`_: Temurin for Windows added in the list of supported JDKs.
- `#525 <https://github.com/beehive-lab/TornadoVM/pull/525>`_: Revert usage of String Templates in preparation for JDK 23.
- `#527 <https://github.com/beehive-lab/TornadoVM/pull/527>`_: SPIR-V version parameter added. TornadoVM may run previous SPIR-V versions (e.g., ComputeAorta from Codeplay).
- `#513 <https://github.com/beehive-lab/TornadoVM/pull/531>`_: LevelZero JNI Library updated to v0.1.4.

Bug Fixes
~~~~~~~~~~~~~~~~~~

- `#470 <https://github.com/beehive-lab/TornadoVM/pull/470>`_: README documentation fixed.
- `#478 <https://github.com/beehive-lab/TornadoVM/pull/478>`_: Fix the test names that are present in the white list.
- `#488 <https://github.com/beehive-lab/TornadoVM/pull/488>`_: FP64 Kind for radian operations and the PTX backend fixed.
- `#493 <https://github.com/beehive-lab/TornadoVM/pull/493>`_: Tests Whitelist for PTX backend fixed.
- `#502 <https://github.com/beehive-lab/TornadoVM/pull/502>`_: Fix barrier type in the documentation regarding programmability of reductions.
- `#514 <https://github.com/beehive-lab/TornadoVM/pull/514>`_: Installer script fixed.
- `#540 <https://github.com/beehive-lab/TornadoVM/pull/540>`_: Fix  issue with clean-up execution IDs function.
- `#541 <https://github.com/beehive-lab/TornadoVM/pull/541>`_: Fix Data Accessors for the prebuilt API.
- `#543 <https://github.com/beehive-lab/TornadoVM/pull/543>`_: Fix checkstyle condition and FP16 error message improved.



TornadoVM 1.0.6
----------------
27th June 2024

Improvements
~~~~~~~~~~~~~~~~~~

- `#442 <https://github.com/beehive-lab/TornadoVM/pull/442>`_: Support for multiple SPIR-V device versions (>= 1.2).
- `#444 <https://github.com/beehive-lab/TornadoVM/pull/444>`_: Enabling automatic device memory clean-up after each run from the execution plan.
- `#448 <https://github.com/beehive-lab/TornadoVM/pull/448>`_: API extension to query device memory consumption at the TaskGraph granularity.
- `#451 <https://github.com/beehive-lab/TornadoVM/pull/451>`_: Option to select the default SPIR-V runtime.
- `#455 <https://github.com/beehive-lab/TornadoVM/pull/455>`_: Refactoring the API and documentation updated.
- `#460 <https://github.com/beehive-lab/TornadoVM/pull/460>`_: Refactoring all examples to use try-with-resources execution plans by default.
- `#462 <https://github.com/beehive-lab/TornadoVM/pull/462>`_: Support for copy array references from private to private memory on the hardware accelerator.


Compatibility
~~~~~~~~~~~~~~~~~~

- `#438 <https://github.com/beehive-lab/TornadoVM/pull/438>`_: No writes for intermediate files to avoid permissions issues with Jenkins.
- `#440 <https://github.com/beehive-lab/TornadoVM/pull/440>`_: Update Jenkinsfile  for CI/CD testing.
- `#443 <https://github.com/beehive-lab/TornadoVM/pull/443>`_: Level Zero and OpenCL runtimes for SPIR-V included in the Jenkins CI/CD.
- `#450 <https://github.com/beehive-lab/TornadoVM/pull/450>`_: TornadoVM benchmark script improved to report dimensions and sizes.
- `#453 <https://github.com/beehive-lab/TornadoVM/pull/453>`_: Update Jenkinsfile with regards to the runtime for SPIR-V.


Bug Fixes
~~~~~~~~~~~~~~~~~~

- `#434 <https://github.com/beehive-lab/TornadoVM/pull/434>`_: Fix for building TornadoVM on OSx after integration with SPIR-V binaries for OpenCL.
- `#441 <https://github.com/beehive-lab/TornadoVM/pull/441>`_: Fix PTX unit-tests.
- `#446 <https://github.com/beehive-lab/TornadoVM/pull/446>`_: Fix NVIDIA thread-block scheduler for new GPU drivers.
- `#447 <https://github.com/beehive-lab/TornadoVM/pull/447>`_: Fix recompilation when batch processing is not triggered.
- `#463 <https://github.com/beehive-lab/TornadoVM/pull/463>`_: Fix unit-tests for CPU virtual devices.


TornadoVM 1.0.5
----------------
26th May 2024

Improvements
~~~~~~~~~~~~~~~~~~

- `#402 <https://github.com/beehive-lab/TornadoVM/pull/402>`_: Support for TornadoNativeArrays from FFI buffers.
- `#403 <https://github.com/beehive-lab/TornadoVM/pull/403>`_: Clean-up and refactoring for the code analysis of the loop-interchange.
- `#405 <https://github.com/beehive-lab/TornadoVM/pull/405>`_: Disable Loop-Interchange for CPU offloading..
- `#407 <https://github.com/beehive-lab/TornadoVM/pull/407>`_: Debugging OpenCL Kernels builds improved.
- `#410 <https://github.com/beehive-lab/TornadoVM/pull/410>`_: CPU block scheduler disabled by default and option to switch between different thread-schedulers added.
- `#418 <https://github.com/beehive-lab/TornadoVM/pull/418>`_: TornadoOptions and TornadoLogger improved.
- `#423 <https://github.com/beehive-lab/TornadoVM/pull/423>`_: MxM using ns instead of ms to report performance.
- `#425 <https://github.com/beehive-lab/TornadoVM/pull/425>`_: Vector types for ``Float<Width>`` and ``Int<Width>`` supported.
- `#429 <https://github.com/beehive-lab/TornadoVM/pull/429>`_: Documentation of the installation process updated and improved.
- `#432 <https://github.com/beehive-lab/TornadoVM/pull/432>`_: Support for SPIR-V code generation and dispatcher using the TornadoVM OpenCL runtime.


Compatibility
~~~~~~~~~~~~~~~~~~

- `#409 <https://github.com/beehive-lab/TornadoVM/pull/409>`_: Guidelines to build the documentation.
- `#411 <https://github.com/beehive-lab/TornadoVM/pull/411>`_: Windows installer improved.
- `#412 <https://github.com/beehive-lab/TornadoVM/pull/412>`_: Python installer improved to check download all Python dependencies before the main installer.
- `#413 <https://github.com/beehive-lab/TornadoVM/pull/413>`_: Improved documentation for installing all configurations of backends and OS.
- `#424 <https://github.com/beehive-lab/TornadoVM/pull/424>`_: Use Generic GPU Scheduler for some older NVIDIA Drivers for the OpenCL runtime.
- `#430 <https://github.com/beehive-lab/TornadoVM/pull/430>`_: Improved the installer by checking  that the TornadoVM environment is loaded upfront.

Bug Fixes
~~~~~~~~~~~~~~~~~~

- `#400 <https://github.com/beehive-lab/TornadoVM/pull/400>`_: Fix batch computation when the global thread indexes are used to compute the outputs.
- `#414 <https://github.com/beehive-lab/TornadoVM/pull/414>`_: Recover Test-Field unit-tests using Panama types.
- `#415 <https://github.com/beehive-lab/TornadoVM/pull/415>`_: Check style errors fixed.
- `#416 <https://github.com/beehive-lab/TornadoVM/pull/416>`_: FPGA execution with multiple tasks in a task-graph fixed.
- `#417 <https://github.com/beehive-lab/TornadoVM/pull/417>`_: Lazy-copy out fixed for Java fields.
- `#420 <https://github.com/beehive-lab/TornadoVM/pull/420>`_: Fix Mandelbrot example.
- `#421 <https://github.com/beehive-lab/TornadoVM/pull/421>`_: OpenCL 2D thread-scheduler fixed for NVIDIA GPUs.
- `#422 <https://github.com/beehive-lab/TornadoVM/pull/422>`_: Compilation for NVIDIA Jetson Nano fixed.
- `#426 <https://github.com/beehive-lab/TornadoVM/pull/426>`_: Fix Logger for all backends.
- `#428 <https://github.com/beehive-lab/TornadoVM/pull/428>`_: Math cos/sin operations supported for vector types.
- `#431 <https://github.com/beehive-lab/TornadoVM/pull/431>`_: Jenkins files fixed.



TornadoVM 1.0.4
----------------
30th April 2024

Improvements
~~~~~~~~~~~~~~~~~~

- `#369 <https://github.com/beehive-lab/TornadoVM/pull/369>`_: Introduction of Tensor types in TornadoVM API and interoperability with ONNX Runtime.
- `#370 <https://github.com/beehive-lab/TornadoVM/pull/370>`_ : Array concatenation operation for TornadoVM native arrays.
- `#371 <https://github.com/beehive-lab/TornadoVM/pull/371>`_: TornadoVM installer script ported for Windows 10/11.
- `#372 <https://github.com/beehive-lab/TornadoVM/pull/372>`_: Add support for ``HalfFloat`` (``Float16``) in vector types.
- `#374 <https://github.com/beehive-lab/TornadoVM/pull/374>`_: Support for TornadoVM array concatenations from the constructor-level.
- `#375 <https://github.com/beehive-lab/TornadoVM/pull/375>`_: Support for TornadoVM native arrays using slices from the Panama API.
- `#376 <https://github.com/beehive-lab/TornadoVM/pull/376>`_: Support for lazy copy-outs in the batch processing mode.
- `#377 <https://github.com/beehive-lab/TornadoVM/pull/377>`_: Expand the TornadoVM profiler with power metrics for NVIDIA GPUs (OpenCL and PTX backends).
- `#384 <https://github.com/beehive-lab/TornadoVM/pull/384>`_: Auto-closable Execution Plans for automatic memory management.

Compatibility
~~~~~~~~~~~~~~~~~~

- `#386 <https://github.com/beehive-lab/TornadoVM/issues/386>`_: OpenJDK 17 support removed.
- `#390 <https://github.com/beehive-lab/TornadoVM/pull/390>`_: SapMachine OpenJDK 21 supported.
- `#395 <https://github.com/beehive-lab/TornadoVM/issues/395>`_: OpenJDK 22 and GraalVM 22.0.1 supported.
- TornadoVM tested with Apple M3 chips.

Bug Fixes
~~~~~~~~~~~~~~~~~~

- `#367 <https://github.com/beehive-lab/TornadoVM/pull/367>`_: Fix for Graal/Truffle languages in which some Java modules were not visible.
- `#373 <https://github.com/beehive-lab/TornadoVM/pull/373>`_: Fix for data copies of the ``HalfFloat`` types for all backends.
- `#378 <https://github.com/beehive-lab/TornadoVM/pull/378>`_: Fix free memory markers when running multi-thread execution plans.
- `#379 <https://github.com/beehive-lab/TornadoVM/pull/379>`_: Refactoring package of vector api unit-tests.
- `#380 <https://github.com/beehive-lab/TornadoVM/pull/380>`_: Fix event list sizes to accommodate profiling of large applications.
- `#385 <https://github.com/beehive-lab/TornadoVM/pull/385>`_: Fix code check style.
- `#387 <https://github.com/beehive-lab/TornadoVM/pull/387>`_: Fix TornadoVM internal events in OpenCL, SPIR-V and PTX for running multi-threaded execution plans.
- `#388 <https://github.com/beehive-lab/TornadoVM/pull/388>`_: Fix of expected and actual values of tests.
- `#392 <https://github.com/beehive-lab/TornadoVM/pull/392>`_: Fix installer for using existing JDKs.
- `#389 <https://github.com/beehive-lab/TornadoVM/pull/389>`_: Fix ``DataObjectState`` for multi-thread execution plans.
- `#396 <https://github.com/beehive-lab/TornadoVM/pull/396>`_: Fix JNI code for the CUDA NVML library access with OpenCL.


TornadoVM 1.0.3
----------------
27th March 2024

Improvements
~~~~~~~~~~~~~~~~~~

- `#344 <https://github.com/beehive-lab/TornadoVM/pull/344>`_: Support for Multi-threaded Execution Plans.
- `#347 <https://github.com/beehive-lab/TornadoVM/pull/347>`_: Enhanced examples.
- `#350 <https://github.com/beehive-lab/TornadoVM/pull/350>`_: Obtain internal memory segment for the Tornado Native Arrays without the object header.
- `#357 <https://github.com/beehive-lab/TornadoVM/pull/357>`_: API extensions to query and apply filters to backends and devices from the ``TornadoExecutionPlan``.
- `#359 <https://github.com/beehive-lab/TornadoVM/pull/359>`_: Support Factory Methods for FFI-based array collections to be used/composed in TornadoVM Task-Graphs.

Compatibility
~~~~~~~~~~~~~~~~~~

- `#351 <https://github.com/beehive-lab/TornadoVM/pull/351>`_: Compatibility of TornadoVM Native Arrays with the Java Vector API.
- `#352 <https://github.com/beehive-lab/TornadoVM/pull/352>`_: Refactor memory limit to take into account primitive types and wrappers.
- `#354 <https://github.com/beehive-lab/TornadoVM/pull/354>`_: Add DFT-sample benchmark in FP32.
- `#356 <https://github.com/beehive-lab/TornadoVM/pull/356>`_: Initial support for Windows 11 using Visual Studio Development tools.
- `#361 <https://github.com/beehive-lab/TornadoVM/pull/361>`_: Compatibility with the SPIR-V toolkit v0.0.4.
- `#366 <https://github.com/beehive-lab/TornadoVM/pull/363>`_: Level Zero JNI Dependency updated to 0.1.3.

Bug Fixes
~~~~~~~~~~~~~~~~~~

- `#346 <https://github.com/beehive-lab/TornadoVM/pull/346>`_: Computation of local-work group sizes for the Level Zero/SPIR-V backend fixed.
- `#360 <https://github.com/beehive-lab/TornadoVM/pull/358>`_: Fix native tests to check the JIT compiler for each backend.
- `#355 <https://github.com/beehive-lab/TornadoVM/pull/355>`_: Fix custom exceptions when a driver/device is not found.


TornadoVM 1.0.2
----------------
29/02/2024

Improvements
~~~~~~~~~~~~~~~~~~

- `#323 <https://github.com/beehive-lab/TornadoVM/pull/323>`_: Set Accelerator Memory Limit per Execution Plan at the API level
- `#328 <https://github.com/beehive-lab/TornadoVM/pull/328>`_: Javadoc API to run with concurrent devices and memory limits
- `#340 <https://github.com/beehive-lab/TornadoVM/pull/340>`_: New API calls to enable ``threadInfo`` and ``printKernel`` from the Execution Plan API.
- `#334 <https://github.com/beehive-lab/TornadoVM/pull/334>`_: Dynamically enable/disable profiler after first run

Compatibility
~~~~~~~~~~~~~~~~~~

- `#337 <https://github.com/beehive-lab/TornadoVM/pull/337>`_ : Initial support for Graal and JDK 21.0.2

Bug Fixes
~~~~~~~~~~~~~~~~~~

- `#322 <https://github.com/beehive-lab/TornadoVM/pull/322>`_: Fix duplicate thread-info debug message when the debug option is also enabled.
- `#325 <https://github.com/beehive-lab/TornadoVM/pull/325>`_: Set/Get accesses for the ``MatrixVectorFloat4`` type fixed
- `#326 <https://github.com/beehive-lab/TornadoVM/pull/326>`_: Fix installation script for running with Python >= 3.12
- `#327 <https://github.com/beehive-lab/TornadoVM/pull/327>`_: Fix Memory Limits for all supported Panama off-heap types.
- `#329 <https://github.com/beehive-lab/TornadoVM/pull/329>`_: Fix timers for the dynamic reconfiguration policies
- `#330 <https://github.com/beehive-lab/TornadoVM/pull/330>`_: Fix the profiler logs when silent mode is enabled
- `#332 <https://github.com/beehive-lab/TornadoVM/pull/332>`_: Fix Batch processing when having multiple task-graphs in a single execution plan.


TornadoVM 1.0.1
----------------
30/01/2024

Improvements
~~~~~~~~~~~~~~~~~~

- `#305 <https://github.com/beehive-lab/TornadoVM/pull/305>`_: Under-demand data transfer for custom data ranges.
- `#313 <https://github.com/beehive-lab/TornadoVM/pull/313>`_: Initial support for Half-Precision (FP16) data types.
- `#311 <https://github.com/beehive-lab/TornadoVM/pull/311>`_: Enable Multi-Task Multiple Device (MTMD) model from the ``TornadoExecutionPlan`` API:
- `#315 <https://github.com/beehive-lab/TornadoVM/pull/315>`_: Math ``Ceil`` function added


Compatibility/Integration
~~~~~~~~~~~~~~~~~~~~~~~~~~~

- `#294 <https://github.com/beehive-lab/TornadoVM/pull/294>`_: Separation of the OpenCL Headers from the code base.
- `#297 <https://github.com/beehive-lab/TornadoVM/pull/297>`_: Separation of the LevelZero JNI API in a separate repository.
- `#301 <https://github.com/beehive-lab/TornadoVM/pull/301>`_: Temurin configuration supported.
- `#304 <https://github.com/beehive-lab/TornadoVM/pull/304>`_: Refactor of the common phases for the JIT compiler.
- `#316 <https://github.com/beehive-lab/TornadoVM/pull/316>`_: Beehive SPIR-V Toolkit version updated.

Bug Fixes
~~~~~~~~~~~~~~~~~~

- `#298 <https://github.com/beehive-lab/TornadoVM/pull/298>`_: OpenCL Codegen fixed open-close brackets.
- `#300 <https://github.com/beehive-lab/TornadoVM/pull/300>`_: Python Dependencies fixed for AWS
- `#308 <https://github.com/beehive-lab/TornadoVM/pull/308>`_: Runtime check for Grid-Scheduler names
- `#309 <https://github.com/beehive-lab/TornadoVM/pull/309>`_: Fix check-style to support STR templates
- `#314 <https://github.com/beehive-lab/TornadoVM/pull/314>`_: emit Vector16 Capability for 16-width vectors


TornadoVM 1.0
----------------
05/12/2023

Improvements
~~~~~~~~~~~~~~~~~~

- Brand-new API for allocating off-heap objects and array collections using the Panama Memory Segment API.
  - New Arrays, Matrix and Vector type objects are allocated using the Panama API.
  - Migration of existing applications to use the new Panama-based types: https://tornadovm.readthedocs.io/en/latest/offheap-types.html
- Handling of the TornadoVM's internal bytecode improved to avoid write-only copies from host to device.
- ``cospi`` and ``sinpi`` math operations supported for OpenCL, PTX and SPIR-V.
- Vector 16 data types supported for ``float``, ``double`` and ``int``.
- Support for Mesa's ``rusticl``.
- Device default ordering improved based on maximum thread size.
- Move all the installation and configuration scripts from Bash to Python.
- The installation process has been improved for Linux and OSx with M1/M2 chips.
- Documentation improved.
- Add profiling information for the testing scripts.


Compatibility/Integration
~~~~~~~~~~~~~~~~~~~~~~~~~

- Integration with the Graal 23.1.0 JIT Compiler.
- Integration with OpenJDK 21.
- Integration with Truffle Languages (Python, Ruby and Javascript) using Graal 23.1.0.
- TornadoVM API Refactored.
- Backport bug-fixes for branch using OpenJDK 17: ``master-jdk17``


Bug fixes:
~~~~~~~~~~~~~~~~~

- Multiple SPIR-V Devices fixed.
- Runtime Exception when no SPIR-V devices are present.
- Issue with the kernel context API when invoking multiple kernels fixed.
- MTMD mode is fixed when running multiple backends on the same device.
- ``long`` type as a constant parameter for a kernel fixed.
- FPGA Compilation and Execution fixed for AWS and Xilinx devices.
- Batch processing fixed for different data types of the same size.



TornadoVM 0.15.2
----------------
26/07/2023

Improvements
~~~~~~~~~~~~~~~~~~

- Initial Support for Multi-Tasks on Multiple Devices (MTMD): This mode enables the execution of multiple independent tasks on more than one hardware accelerators. Documentation in link: https://tornadovm.readthedocs.io/en/latest/multi-device.html
- Support for trigonometric ``radian``, ``cospi`` and ``sinpi`` functions for the OpenCL/PTX and SPIR-V backends.
- Clean-up Java modules not being used and TornadoVM core classes refactored.


Compatibility/Integration
~~~~~~~~~~~~~~~~~~~~~~~~~

- Initial integration with ComputeAorta (part of the Codeplay's oneAPI Construction Kit for RISC-V) to run on RISC-V with Vector Instructions (OpenCL backend) in emulation mode.
- Beehive SPIR-V Toolkit dependency updated.
- Tests for prebuilt SPIR-V kernels fixed to dispatch SPIR-V binaries through the Level Zero and OpenCL runtimes.
- Deprecated ``javac.py`` script removed.


Bug fixes:
~~~~~~~~~~~~~~~~~

- TornadoVM OpenCL Runtime throws an exception when the detected hardware does not support FP64.
- Fix the installer for the older Apple with the x86 architecture using AMD GPUs.
- Installer for ARM based systems fixed.
- Installer fixed for Microsoft WSL and NVIDIA GPUs.
- OpenCL code generator fixed to avoid using the reserved OpenCL keywords from Java function parameters.
- Dump profiler option fixed.



TornadoVM 0.15.1
----------------
15/05/2023

Improvements
~~~~~~~~~~~~~~~~~~

- Introduction of a device selection heuristic based on the computing capabilities of devices. TornadoVM selects, as the default device, the fastest device based on its computing capability.
- Optimisation of removing redundant data copies for Read-Only and Write-Only buffers from between the host (CPU) and the device (GPU) based on the Tornado Data Flow Graph.
- New installation script for TornadoVM.
- Option to dump the TornadoVM bytecodes for the unit tests.
- Full debug option improved. Use ``--fullDebug``.


Compatibility/Integration
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

- Integration and compatibility with the Graal 22.3.2 JIT Compiler.
- Improved compatibility with Apple M1 and Apple M2 through the OpenCL Backend.
- GraalVM/Truffle programs integration improved. Use ``--truffle`` in the ``tornado`` script to run guest programs with Truffle.
  Example: ``tornado --truffle python myProgram.py``
  Full documentation: https://tornadovm.readthedocs.io/en/latest/truffle-languages.html

Bug fixes:
~~~~~~~~~~~~~~~~~

- Documentation that resets the device's memory: https://github.com/beehive-lab/TornadoVM/blob/master/tornado-api/src/main/java/uk/ac/manchester/tornado/api/TornadoExecutionPlan.java#L282
- Append the Java ``CLASSPATH`` to the ``cp`` option from the ``tornado`` script.
- Dependency fixed for the ``cmake-maven`` plugin fixed for ARM-64 arch.
- Fixed the automatic installation for Apple M1/M2 and ARM-64 and NVIDIA Jetson nano computing systems.
- Integration with IGV fixed. Use the  ``--igv`` option for the ``tornado`` and ``tornado-test`` scripts.



TornadoVM 0.15
----------------
27/01/2023

Improvements
~~~~~~~~~~~~~~~~~~

- New TornadoVM API:

   - API refactoring (``TaskSchedule`` has been renamed to ``TaskGraph``)

   - Introduction of the Immutable ``TaskGraphs``

   - Introduction of the TornadoVM Execution Plans: (``TornadoExecutionPlan``)

   - The documentation of migration of existing TornadoVM applications to the new API can be found here: https://tornadovm.readthedocs.io/en/latest/programming.html#migration-to-tornadovm-v0-15

- Launch a new website https://tornadovm.readthedocs.io/en/latest/ for the documentation
- Improved documentation
- Initial support for Intel ARC discrete GPUs.
- Improved TornadoVM installer for Linux
- ImprovedTornadoVM launch script with optional parameters
- Support of large buffer allocations with Intel Level Zero. Use: ``tornado.spirv.levelzero.extended.memory=True``


Bug fixes:
~~~~~~~~~~~~~~~~~

- Vector and Matrix types
- TornadoVM Floating Replacement compiler phase fixed
- Fix ``CMAKE`` for Intel ARC GPUs
- Device query tool fixed for the PTX backend
- Documentation for Windows 11 fixed


TornadoVM 0.14.1
----------------

29/09/2022

Improvements
~~~~~~~~~~~~~~~~~~~~~

-  The tornado command is replaced from a Bash to a Python script.

   -  Use ``tornado --help`` to check the new options and examples.

-  Support of native tests for the SPIR-V backend.
-  Improvement of the OpenCL and PTX tests of the internal APIs.

Compatibility/Integration
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

-  Integration and compatibility with the Graal 22.2.0 JIT Compiler.
-  Compatibility with JDK 18 and JDK 19.
-  Compatibility with Apple M1 Pro using the OpenCL backend.

Bug Fixes
~~~~~~~~~~~~~~~~~~~~~

-  CUDA PTX generated header fixed to target NVIDIA 30xx GPUs and CUDA
   11.7.
-  The signature of generated PTX kernels fixed for NVIDIA driver >= 510
   and 30XX GPUs when using the TornadoVM Kernel API.
-  Tests of virtual OpenCL devices fixed.
-  Thread deployment information for the OpenCL backend is fixed.
-  ``TornadoVMRuntimeCI`` moved to ``TornadoVMRutimeInterface``.

TornadoVM 0.14
--------------

15/06/2022

New Features
~~~~~~~~~~~~

-  New device memory management for addressing the memory allocation
   limitations of OpenCL and enabling pinned memory of device buffers.

   -  The execution of task-schedules will still automatically
      allocate/deallocate memory every time a task-schedule is executed,
      unless lock/unlock functions are invoked explicitly at the
      task-schedule level.
   -  One heap per device has been replaced with a device buffer per
      input variable.
   -  A new API call has been added for releasing memory:
      ``unlockObjectFromMemory``
   -  A new API call has been added for locking objects to the device:
      ``lockObjectInMemory`` This requires the user to release memory by
      invoking ``unlockObjectFromMemory`` at the task-schedule level.

-  Enhanced Live Task migration by supporting multi-backend execution
   (PTX <-> OpenCL <-> SPIR-V).

.. _compatibilityintegration-1:

Compatibility/Integration
~~~~~~~~~~~~~~~~~~~~~~~~~

-  Integration with the Graal 22.1.0 JIT Compiler
-  JDK 8 deprecated
-  Azul Zulu JDK supported
-  OpenCL 2.1 as a default target for the OpenCL Backend
-  Single Docker Image for Intel XPU platforms, including the SPIR-V
   backend (using the Intel Integrated Graphics), and OpenCL (using the
   Intel Integrated Graphics, Intel CPU and Intel FPGA in emulation
   mode). Image:
   https://github.com/beehive-lab/docker-tornado#intel-integrated-graphics

Improvements/Bug Fixes
~~~~~~~~~~~~~~~~~~~~~~

-  ``SIGNUM`` Math Function included for all three backends.
-  SPIR-V optimizer enabled by default (3x reduce in binary size).
-  Extended Memory Mode enabled for the SPIR-V Backend via Level Zero.
-  Phi instructions fixed for the SPIR-V Backend.
-  SPIR-V Vector Select instructions fixed.
-  Duplicated IDs for Non-Inlined SPIR-V Functions fixed.
-  Refactoring of the TornadoVM Math Library.
-  FPGA Configuration files fixed.
-  Bitwise operations for OpenCL fixed.
-  Code Generation Times and Backend information are included in the
   profiling info.


TornadoVM 0.13
--------------

21/03/2022

-  Integration with JDK 17 and Graal 21.3.0

   -  JDK 11 is the default version and the support for the JDK 8 has
      been deprecated

-  Support for extended intrinsics regarding math operations
-  Native functions are enabled by default
-  Support for 2D arrays for PTX and SPIR-V backends:

   -  https://github.com/beehive-lab/TornadoVM/commit/2ef32ca97941410672720f9dfa15f0151ae2a1a1

-  Integer Test Move operation supported:

   -  https://github.com/beehive-lab/TornadoVM/pull/177

-  Improvements in the SPIR-V Backend:

   -  Experimental SPIR-V optimizer. Binary size reduction of up to 3x

      -  https://github.com/beehive-lab/TornadoVM/commit/394ca94dcdc3cb58d15a17046e1d22c6389b55b7

   -  Fix malloc functions for Level-Zero
   -  Support for pre-built SPIR-V binary modules using the TornadoVM
      runtime for OpenCL
   -  Performance increase due to cached buffers on GPUs by default
   -  Disassembler option for SPIR-V binary modules. Use
      ``--printKernel``

-  Improved Installation:

   -  Full automatic installer script integrated

-  Documentation about the installation for Windows 11
-  Refactoring and several bug fixes

   -  https://github.com/beehive-lab/TornadoVM/commit/57694186b42ec28b16066fb549ab8fcf9bff9753
   -  Vector types fixed:

      -  https://github.com/beehive-lab/TornadoVM/pull/181/files
      -  https://github.com/beehive-lab/TornadoVM/commit/004d61d6d26945b45ebff66641b60f90f00486be

   -  Fix AtomicInteger get for OpenCL:

      -  https://github.com/beehive-lab/TornadoVM/pull/177

-  Dependencies for Math3 and Lang3 updated


TornadoVM 0.12
--------------

17/11/2021

-  New backend: initial support for SPIR-V and Intel Level Zero

   -  Level-Zero dispatcher for SPIR-V integrated
   -  SPIR-V Code generator framework for Java

-  Benchmarking framework improved to accommodate all three backends
-  Driver metrics, such as kernel time and data transfers included in
   the benchmarking framework
-  TornadoVM profiler improved:

   -  Command line options added: ``--enableProfiler <silent|console>``
      and ``--dumpProfiler <jsonFile>``
   -  Logging improve for debugging purposes. JIT Compiler, JNI calls
      and code generation

-  New math intrinsincs operations supported
-  Several bug fixes:

   -  Duplicated barriers removed. TornadoVM BARRIER bytecode fixed when
      running multi-context
   -  Copy in when having multiple reductions fixed
   -  TornadoVM profiler fixed for multiple context switching (device
      switching)

-  Pretty printer for device information


TornadoVM 0.11
--------------

29/09/2021

-  TornadoVM JIT Compiler upgrade to work with Graal 21.2.0 and JDK 8
   with JVMCI 21.2.0
-  Refactoring of the Kernel Parallel API for Heterogeneous Programming:

   -  Methods ``getLocalGroupSize(index)`` and ``getGlobalGroupSize``
      moved to public fields to keep consistency with the rest of the
      thread properties within the ``KernelContext`` class.

      -  Changeset:
         https://github.com/beehive-lab/TornadoVM/commit/e1ebd66035d0722ca90eb0121c55dbc744840a74

-  Compiler update to register the global number of threads:
   https://github.com/beehive-lab/TornadoVM/pull/133/files
-  Simplification of the TornadoVM events handler:
   https://github.com/beehive-lab/TornadoVM/pull/135/files
-  Renaming the Profiler API method from ``event.getExecutionTime`` to
   ``event.getElapsedTime``:
   https://github.com/beehive-lab/TornadoVM/pull/134
-  Deprecating ``OCLWriteNode`` and ``PTXWriteNode`` and fixing stores
   for bytes: https://github.com/beehive-lab/TornadoVM/pull/131
-  Refactoring of the FPGA IR extensions, from the high-tier to the
   low-tier of the JIT compiler

   -  Utilizing the FPGA Thread-Attributes compiler phase for the FPGA
      execution
   -  Using the ``GridScheduler`` object (if present) or use a default
      value (e.g., 64, 1, 1) for defining the FPGA OpenCL local
      workgroup

-  Several bugs fixed:

   -  Codegen for sequential kernels fixed
   -  Function parameters with non-inlined method calls fixed


TornadoVM 0.10
--------------

29/06/2021

-  TornadoVM JIT Compiler sync with Graal 21.1.0
-  Experimental support for OpenJDK 16
-  Tracing the TornadoVM thread distribution and device information with
   a new option ``--threadInfo`` instead of ``--debug``
-  Refactoring of the new API:

   -  ``TornadoVMExecutionContext`` renamed to ``KernelContext``
   -  ``GridTask`` renamed to ``GridScheduler``

-  AWS F1 AMI version upgraded to 1.10.0 and automated the generation of
   AFI image
-  Xilinx OpenCL backend expanded with:

   - a) Initial integration of Xilinx OpenCL attributes for loop
         pipelining in the TornadoVM compiler

   - b) Support for multiple compute units

-  Logging FPGA compilation option added to dump FPGA HLS compilation to
   a file
-  TornadoVM profiler enhanced for including data transfers for the
   stack-frame and kernel dispatch time
-  Initial support for 2D Arrays added
-  Several bug fixes and stability support for the OpenCL and PTX
   backends


TornadoVM 0.9
-------------

15/04/2021

-  Expanded API for expressing kernel parallelism within Java. It can
   work with the existing loop parallelism in TornadoVM.

   -  Direct access to thread-ids, OpenCL local memory (PTX shared
      memory), and barriers

   -  ``TornadoVMContext`` added:

      See
      https://github.com/beehive-lab/TornadoVM/blob/5bcd3d6dfa2506032322c32d72b7bbd750623a95/tornado-api/src/main/java/uk/ac/manchester/tornado/api/TornadoVMContext.java

   -  Code examples:

      -  https://github.com/beehive-lab/TornadoVM/tree/master/examples/src/main/java/uk/ac/manchester/tornado/examples/tornadovmcontext

   -  Documentation:

      -  https://github.com/beehive-lab/TornadoVM/blob/master/assembly/src/docs/21_TORNADOVM_CONTEXT.md

-  Profiler integrated with Chrome debug:

   -  Use flags:
      ``-Dtornado.chrome.event.tracer.enabled=True -Dtornado.chrome.event.tracer.filename=userFile.json``
   -  See https://github.com/beehive-lab/TornadoVM/pull/41

-  Added support for Windows 10:

   -  See
      https://github.com/beehive-lab/TornadoVM/blob/develop/assembly/src/docs/20_INSTALL_WINDOWS_WITH_GRAALVM.md

-  TornadoVM running with Windows JDK 11 supported (Linux & Windows)
-  Xilinx FPGAs workflow supported for Vitis 2020.2
-  Pre-compiled tasks for Xilinx/Intel FPGAs fixed
-  Slambench fixed when compiling for PTX and OpenCL backends
-  Several bug fixes for the runtime, JIT compiler and data management.

--------------

TornadoVM 0.8
-------------

19/11/2020

-  Added PTX backend for NVIDIA GPUs

   -  Build TornadoVM using ``make BACKEND=ptx,opencl`` to obtain the
      two supported backends.

-  TornadoVM JIT Compiler aligned with Graal 20.2.0
-  Support for other JDKs:

   -  Red Hat Mandrel 11.0.9
   -  Amazon Coretto 11.0.9
   -  GraalVM LabsJDK 11.0.8
   -  OpenJDK 11.0.8
   -  OpenJDK 12.0.2
   -  OpenJDK 13.0.2
   -  OpenJDK 14.0.2

-  Support for hybrid (CPU-GPU) parallel reductions
-  New API for generic kernel dispatch. It introduces the concept of
   ``WorkerGrid`` and ``GridTask``

   -  A ``WorkerGrid`` is an object that stores how threads are
      organized on an OpenCL device:
      ``java       WorkerGrid1D worker1D = new WorkerGrid1D(4096);``
   -  A ``GridTask`` is a map that relates a task-name with a
      worker-grid.
      ``java       GridTask gridTask = new GridTask();       gridTask.set("s0.t0", worker1D);``
   -  A TornadoVM Task-Schedule can be executed using a ``GridTask``:
      ``java     ts.execute(gridTask);``
   -  More info:
      `link <https://github.com/beehive-lab/TornadoVM/commit/6191720fd947d3102e784dade9e576ed8af11068>`__

-  TornadoVM profiler improved

   -  Profiler metrics added
   -  Code features per task-graph

-  Lazy device initialisation moved to early initialisation of PTX and
   OpenCL devices
-  Initial support for Atomics (OpenCL backend)

   -  `Link to
      examples <https://github.com/beehive-lab/TornadoVM/blob/master/unittests/src/main/java/uk/ac/manchester/tornado/unittests/atomics/TestAtomics.java>`__

-  Task Schedules with 11-14 parameters supported
-  Documentation improved
-  Bug fixes for code generation, numeric promotion, basic block
   traversal, Xilinx FPGA compilation.

--------------

TornadoVM 0.7
-------------

22/06/2020

-  Support for ARM Mali GPUs.
-  Support parallel reductions on FPGAs
-  Agnostic FPGA vendor compilation via configuration files (Intel & Xilinx)
-  Support for AWS on Xilinx FPGAs
-  Recompilation for different input data sizes supported
-  New TornadoVM API calls:

   a) Update references for re-compilation:
      ``taskSchedule.updateReferences(oldRef, newRef);``
   b) Use the default OpenCL scheduler:
      ``taskSchedule.useDefaultThreadScheduler(true);``

-  Use of JMH for benchmarking
-  Support for Fused Multiply-Add (FMA) instructions
-  Easy-selection of different devices for unit-tests
   ``tornado-test.py -V --debug -J"-Dtornado.unittests.device=0:1"``
-  Bailout mechanism improved from parallel to sequential
-  Improve thread scheduling
-  Support for private memory allocation
-  Assertion mode included
-  Documentation improved
-  Several bug fixes


TornadoVM 0.6
-------------

21/02/2020

-  TornadoVM compatible with GraalVM 19.3.0 using JDK 8 and JDK 11
-  TornadoVM compiler update for using Graal 19.3.0 compiler API
-  Support for dynamic languages on top of Truffle

   -  `examples <https://github.com/beehive-lab/TornadoVM/tree/master/examples/src/main/java/uk/ac/manchester/tornado/examples/polyglot>`__

-  Support for multiple tasks per task-schedule on FPGAs (Intel and
   Xilinx)
-  Support for OSX Mojave and Catalina
-  Task-schedule name handling for FPGAs improved
-  Exception handling improved
-  Reductions for ``long`` type supported
-  Bug fixes for ternary conditions, reductions and code generator
-  Documentation improved


TornadoVM 0.5
-------------

16/12/2019

-  Initial support for Xilinx FPGAs
-  TornadoVM API classes are now ``Serializable``
-  Initial support for local memory for reductions
-  JVMCI built with local annotation patch removed. Now TornadoVM
   requires unmodified JDK8 with JVMCI support
-  Support of multiple reductions within the same ``task-schedules``
-  Emulation mode on Intel FPGAs is fixed
-  Fix reductions on Intel Integrated Graphics
-  TornadoVM driver OpenCL initialization and OpenCL code cache improved
-  Refactoring of the FPGA execution modes (full JIT and emulation modes
   improved).


TornadoVM 0.4
-------------

14/10/2019

-  Profiler supported

   -  Use ``-Dtornado.profiler=True`` to enable profiler
   -  Use ``-Dtornado.profiler=True -Dtornado.profiler.save=True`` to
      dump the profiler logs

-  Feature extraction added

   -  Use ``-Dtornado.feature.extraction=True`` to enable code
      extraction features

-  Mac OSx support
-  Automatic reductions composition (map-reduce) within the same
   task-schedule
-  Bug related to a memory leak when running on GPUs solved
-  Bug fixes and stability improvements



TornadoVM 0.3
-------------

22/07/2019

-  New Matrix 2D and Matrix 3D classes with type specializations.
-  New API-call ``TaskSchedule#batch`` for batch processing. It allows
   programmers to run with more data than the maximum capacity of the
   accelerator by creating batches of executions.
-  FPGA full automatic compilation pipeline.
-  FPGA options simplified:

   -  ``-Dtornado.precompiled.binary=<binary>`` for loading the
      bitstream.
   -  ``-Dtornado.opencl.userelative=True`` for using relative
      addresses.
   -  ``-Dtornado.opencl.codecache.loadbin=True`` *removed*.

-  Reductions support enhanced and fully automated on GPUs and CPUs.
-  Initial support for reductions on FPGAs.
-  Initial API for profiling tasks integrated.


TornadoVM 0.2
-------------

25/02/2019

-  Rename to TornadoVM
-  Device selection for better performance (CPU, multi-core, GPU, FPGA)
   via an API for Dynamic Reconfiguration

   -  Added methods ``executeWithProfiler`` and
      ``executeWithProfilerSequential`` with an input policy.
   -  Policies: ``Policy.PERFORMANCE``, ``Policy.END_2_END``, and
      ``Policy.LATENCY`` implemented.

-  Basic heuristic for predicting the highest performing target device
   with Dynamic Reconfiguration
-  Initial FPGA integration for Altera FPGAs:

   -  Full JIT compilation mode
   -  Ahead of time compilation mode
   -  Emulation/debug mode

-  FPGA JIT compiler specializations
-  Added support for Java reductions:

   -  Compiler specializations for CPU and GPU reductions

-  Performance and stability fixes


Tornado 0.1.0
-------------

07/09/2018

-  Initial Implementation of the Tornado compiler
-  Initial GPU/CPU code generation for OpenCL
-  Initial support in the runtime to execute OpenCL programs generated
   by the Tornado JIT compiler
-  Initial Tornado-API release (``@Parallel`` Java annotation and ``TaskSchedule`` API)
-  Multi-GPU enabled through multiple tasks-schedules
