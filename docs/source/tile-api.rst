.. _tile_api:

CUDA Tile Programming (TileContext)
###################################

TornadoVM can compile a task through **NVIDIA CUDA Tile** instead of the SIMT path. A tile kernel
describes what *one tile block* does over tiles of a tensor, and the tile compiler decides how many
threads back it, which tensor-core instruction to issue, and how tiles move through shared memory.
Nothing in the API names a thread, a warp or a fragment.

This path is CUDA-only and additive: a tile task shares a ``TaskGraph``, a stream and its device
buffers with ordinary JIT tasks and with native library tasks.

Requirements
************

.. list-table::
   :widths: 30 70
   :header-rows: 0

   * - CUDA Toolkit
     - **13.3 or newer** for the CUDA Tile C++ surface. A userspace install is enough:
       ``pip install --user 'cuda-tile[tileiras]'``, then point ``-Dtornado.cuda.nvcc`` at it if the
       system CUDA is older.
   * - Driver
     - **R580 or newer** to load a CUDA 13 cubin.
   * - GPU
     - Compute capability **8.0 or newer**.

Below any of these, a tile task fails with ``TornadoDeviceTileNotSupported`` naming the missing
requirement, and the unit tests report ``[UNSUPPORTED]`` rather than failing.

``TileContext`` in one example
******************************

A task is a tile task when its kernel method takes a ``TileContext`` as its first parameter, in the
same way a leading ``KernelContext`` selects the kernel-parallel API.

.. code-block:: java

    public static void gemm(TileContext tc, HalfFloatArray a, HalfFloatArray b, FloatArray c,
                            int m, int n, int k) {
        PartitionView av = tc.partition(tc.view(a, m, k), 64, 32);
        PartitionView bv = tc.partition(tc.view(b, k, n), 32, 64);
        PartitionView cv = tc.partition(tc.view(c, m, n), 64, 64);

        Tile acc = tc.zeros(DType.F32, 64, 64);
        for (int step = 0; step < k / 32; step++) {
            acc = tc.mma(av.load(tc.bidX(), step), bv.load(step, tc.bidY()), acc);
        }
        cv.store(acc, tc.bidX(), tc.bidY());
    }

    // The worker grid counts TILE BLOCKS, not threads.
    WorkerGrid2D worker = new WorkerGrid2D(M / 64, N / 64);
    GridScheduler grid = new GridScheduler("s0.gemm", worker);

    TaskGraph graph = new TaskGraph("s0")
        .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
        .task("gemm", Gemm::gemm, new TileContext(), a, b, c, M, N, K)
        .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

    try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
        plan.withGridScheduler(grid).execute();
    }

Three rules
***********

1. **Tile shapes are compile-time constants and powers of two.** A shape is part of the kernel's
   type, so a differently shaped variant is a differently compiled kernel. Passing a shape as a
   method parameter is rejected at sketch time with a message naming the argument. Write a literal
   or a ``static final int``, and note that a constant passed into a helper does *not* count,
   because inlining happens after parsing.
2. **Extents are not constrained.** Views take runtime extents, so the problem size does not
   specialise the kernel; only the tile shape does.
3. **The worker grid counts tile blocks** and the block is pinned to ``1x1x1`` by
   ``CUDATileScheduler``. Do not set local work yourself.

API reference
*************

.. list-table::
   :widths: 42 34 24
   :header-rows: 1

   * - ``TileContext`` / ``PartitionView``
     - CUDA Tile C++
     - Notes
   * - ``view(array, n)`` / ``view(array, rows, cols)``
     - ``ct::tensor_span``
     - extents may be runtime values
   * - ``partition(view, tile...)``
     - ``ct::partition_view``
     - tile shape: power-of-two constant
   * - ``bidX() bidY() bidZ()``
     - ``ct::bid().x/.y/.z``
     - index of this tile block
   * - ``numBlocksX()`` ...
     - ``ct::num_blocks().x``
     - grid size, for persistent kernels
   * - ``load(bx[, by])``
     - ``view.load(...)``
     - caller guarantees in-bounds
   * - ``loadMasked(...)`` / ``storeMasked(...)``
     - ``load_masked`` / ``store_masked``
     - zero-pads a partial tile
   * - ``store(tile, ...)``
     - ``view.store(...)``
     -
   * - ``zeros`` / ``full`` / ``iota``
     - ``ct::full`` / ``ct::iota``
     - ``iota`` has a rank-2 form for ``(1, n)`` index rows
   * - ``add sub mul div``
     - ``+ - * /``
     - broadcasting: a dimension of 1 stretches
   * - ``maximum(a, b)``
     - ``ct::max``
     - elementwise, two operands
   * - ``max(tile, axis)``
     - ``ct::reduce_max``
     - reduction; keeps the reduced dimension
   * - ``sum(tile, axis)``
     - ``ct::sum``
     - keeps the reduced dimension
   * - ``scale(tile, s)``
     - ``tile * (E) s``
     - no named function in CUDA Tile
   * - ``mma(a, b, acc)``
     - ``ct::mma``
     - tensor cores; instruction chosen by the compiler
   * - ``exp exp2 log log2 tanh sqrt rsqrt sin cos abs floor``
     - ``ct::exp`` ...
     -
   * - ``transpose(tile)``
     - ``ct::transpose``
     -
   * - ``cast(tile, dtype)``
     - ``ct::element_cast<E>``
     - any scalar pair, narrowing included
   * - a counted ``for`` loop
     - ``ct::irange``
     - no iterator API, which would allocate

Two spellings are easy to get wrong: ``ct::max`` is the *elementwise* two-operand form and the
reduction is ``ct::reduce_max``; and there is no ``ct::cast`` at all.

A reduction keeps its dimension, so it broadcasts back against the tile it came from, but it does
**not** store into the view it came from: a ``32x1`` tile against a ``32x32`` partition view is
refused by CUDA Tile's ``same_shape`` constraint.

Element types
*************

``DType`` covers ``F16 BF16 F32 F64 TF32 FP8_E4M3 FP8_E5M2 S8 S32``. Views exist today for
``FloatArray``, ``HalfFloatArray``, ``BFloat16Array`` and ``IntArray``. ``mma`` operand and
accumulator pairs are validated against the CUDA Tile ``mmaf``/``mmai`` tables, where the
accumulator type must equal the result type.

Mixing tile tasks with everything else
**************************************

A tile kernel is an ordinary module launch on the execution plan's stream, so it composes without
any extra synchronisation:

.. code-block:: java

    TaskGraph graph = new TaskGraph("chain")
        .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, matrix)
        .task("scale", Chain::scale, input, scaled, 3.0f)                       // JIT SIMT
        .task("tile",  Chain::tileDouble, new TileContext(), scaled, doubled, N) // CUDA Tile
        .libraryTask("gemv", CuBlas::cublasSgemv, /* ... */)                     // native cuBLAS
        .task("bias",  Chain::bias, projected, result, 0.25f)                    // JIT SIMT
        .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

The intermediates never leave the device. ``withCUDAGraph()`` captures the whole pipeline into one
CUDA graph, tile task included, and ``withIntraPlanConcurrency()`` works as usual.

Known limitations
*****************

* ``atomicAdd`` through a ``PartitionView``, ``permute``, ``reshape`` and ``select`` are declared in
  the API but not yet lowered. The missing ``select`` is the one with a visible consequence: it
  rules out causal masking and ragged key tails in attention, because a masked-out score has to
  become ``-inf`` before the exponential. A zero-padded ``loadMasked`` is not a substitute — a
  padded key contributes ``exp(0 - m)`` to the softmax denominator rather than nothing.
* Batch processing (``withBatch``) is rejected for tile tasks.
* Tile intrinsics are not yet handled in ``TornadoCUDAIntrinsicsReplacements``, so a reflectively
  resolved tile kernel fails with an explanation instead of miscompiling.
* An ``@Reduce`` task cannot share a ``TaskGraph`` with a tile task: the reduction rewrite renames
  the graph, so a ``GridScheduler`` keyed on the original task id stops matching and the tile task
  silently runs as one block. Use ``tc.sum`` inside the tile instead.
* TMA does not appear in the generated SASS on Ada (sm_89) because TMA is a Hopper unit; the
  alignment hint only pays off on sm_90 and newer.

Verifying and profiling
***********************

.. code-block:: bash

    # the generated CUDA Tile C++ (expect ct:: calls and no inline PTX)
    tornado --printKernel -m tornado.examples/...TileVectorAdd

    # tensor cores in the cached cubin
    cuobjdump -sass $TORNADOVM_HOME/var/cuda-codecache/device-0-0/<kernel>-*.cubin | grep HMMA

    # the unit tests: elementwise, GEMM, task chaining, the row kernels and the
    # kernels ported from NVIDIA's TileGym suite
    tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileMatmul
    tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileAttention
    tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileGemmVariants

Profiling
=========

Profile the JVM directly rather than the launcher. ``nsys profile $(which tornado) ...`` produces a
report with **no GPU rows**, because the launcher execs a child JVM that the injection does not
follow. Build a Java ``@argfile`` from ``tornado --printJavaFlags`` and profile that instead. Two
details are required, or ``java @argfile`` fails immediately:

* drop the leading ``java`` binary, or it is taken as the main class;
* inline the nested ``@.../exportLists/...`` files, because Java does not expand an ``@argfile``
  recursively and passes the reference through as a literal argument.

.. code-block:: bash

    tornado --printJavaFlags > flags.raw
    python3 - flags.raw > tornadovm.args <<'EOF'
    import sys
    out = []
    for token in open(sys.argv[1]).read().split():
        if token.endswith("/bin/java"):
            continue
        if token.startswith("@"):                  # Java will not expand this itself
            out.extend(open(token[1:]).read().split())
            continue
        out.append(token)
    if "-m" in out:                                # drop a trailing -m <module/Main>
        out = out[:out.index("-m")]
    print("\n".join(out))
    EOF

    nsys profile --trace=cuda,nvtx --cuda-graph-trace=node -o tilechain \
        java @tornadovm.args \
        -m tornado.unittests/uk.ac.manchester.tornado.unittests.tile.TileChainBenchmark 2000

    nsys stats --report cuda_gpu_kern_sum --report cuda_gpu_mem_time_sum tilechain.nsys-rep

``TileChainBenchmark`` also reports CUDA Graph capture against plain execution for the mixed
pipeline, and verifies the result against a CPU reference afterwards, so a timing run doubles as a
correctness check. Application arguments go after the main class, not in the argfile.
