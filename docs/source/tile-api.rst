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
   * - ``minimum(a, b)`` / ``maximum(a, b)``
     - ``ct::min`` / ``ct::max``
     - elementwise, two operands
   * - ``sum(t, axis)`` ``max`` ``min`` ``prod``
     - ``ct::sum`` ``ct::reduce_max`` ``ct::reduce_min`` ``ct::prod``
     - the reduced dimension is kept
   * - ``lessThan(a, b)`` and the other five comparisons
     - ``a < b`` ...
     - result is a ``DType.PRED`` tile; the right operand may be a runtime scalar
   * - ``logicalAnd(m1, m2)`` / ``logicalOr``
     - ``m1 & m2`` / ``m1 | m2``
     - combines two predicate tiles
   * - ``view.atomicAdd(tile, blocks...)``
     - ``ct::atomic_add`` over a pointer tile
     - relaxed order, device scope; see below
   * - ``select(mask, a, b)``
     - ``ct::select``
     - the only consumer of a predicate tile
   * - ``fma(a, b, c)``
     - ``ct::fma``
     - elementwise, unlike ``mma``
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

Atomic accumulation
*******************

``view.atomicAdd(tile, blockIndices...)`` accumulates a tile into the view so that every
concurrent tile block's contribution survives. It is what lets a split-K matmul finish in one
kernel instead of writing per-split partials and reducing them in a second pass.

CUDA Tile has no atomic read-modify-write on a ``partition_view``: the view offers only
``atomic_load`` and ``atomic_store``, while ``ct::atomic_add`` takes a **tile of pointers**. The
code generator therefore computes that pointer tile from the view's base pointer and extents.
Rank 2 needs the row and column index within the tile separately, and neither ``/`` nor ``%`` is
defined on a tile, so the indices come from two ``iota`` tiles broadcast to the tile shape.

The order is ``memory_order_relaxed`` and the scope is ``thread_scope_device``: an accumulator
in device memory needs each element's update to be atomic, not ordered against the others.
Observed lowering on sm_89: ``ATOMG.E.ADD.F32.FTZ.RN.STRONG.GPU`` — device scope matters, since
the default system scope emits the more expensive ``.SYS`` form.

Element types
*************

``DType`` names ``F16``, ``BF16``, ``F32``, ``F64``, ``TF32``, ``FP8_E4M3``, ``FP8_E5M2``,
``S8``, ``S32`` and the comparison-only ``PRED``. Buffers that can be viewed:
``FloatArray``, ``HalfFloatArray``, ``BFloat16Array``, ``IntArray``, ``Int8Array``,
``DoubleArray`` and ``FP8Array`` (whose view takes the format as an argument).

An ``mma`` validates its operand and accumulator pair against the CUDA Tile ``mmaf``/``mmai``
tables, where the accumulator type must equal the result type. Observed lowerings on Ada
(sm_89): fp16 operands with an fp32 accumulator give ``HMMA.16816.F32``, int8 operands with an
int32 accumulator give ``IMMA.16832.S8.S8.SAT``, and fp64 gives scalar ``DADD``/``DMUL``
because Ada has no fp64 tensor core.

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

The table above is the covered surface. What CUDA Tile has and this API does not, in rough
order of how much it costs:

.. list-table::
   :header-rows: 1
   :widths: 30 70

   * - Missing
     - Consequence
   * - fp8 tiles below compute capability 9.0
     - ``FP8Array`` can be viewed (the format is an argument, since the buffer carries both an
       e4m3 and an e5m2 accessor), but ``tileiras`` rejects an fp8 tile for sm_89 with
       "unsupported type 'f8E4M3FN'". The compiler gate refuses it first with a message naming
       the requirement. Hopper and newer compile it.
   * - fp64 tensor cores
     - an fp64 ``mma`` is correct but lowers to scalar ``DADD``/``DMUL`` rather than ``DMMA`` on
       Ada, so doubles work without being accelerated.
   * - ``TF32`` views
     - ``DType.TF32`` is accepted by ``mma`` and has no buffer type of its own; a tf32 operand
       has to come from a cast, which is how CUDA Tile treats it too.
   * - Rank 3 and above
     - ``view``/``partition`` are rank 1 and 2. A batch or head dimension has to be folded into
       the row index, which every attention kernel in the test suite does; it costs arithmetic,
       not expressiveness.
   * - the atomic family beyond ``atomicAdd``
     - ``PartitionView.atomicAdd`` exists (see below); ``atomic_sub``, ``atomic_min``,
       ``atomic_max``, the bitwise atomics, ``atomic_xchg`` and ``atomic_compare_exchange`` do
       not, nor do the masked forms or a choice of memory order and scope.
   * - ``permute`` ``reshape`` ``broadcast`` ``extract``
     - a rank-2 tile can be reshaped only by ``transpose``, and broadcasting happens implicitly
       in elementwise ops rather than on demand.
   * - ``partial_sum`` / ``partial_prod``
     - no scans, so a cumulative softmax or a prefix sum needs a different formulation.
   * - ``tan sinh cosh atan2 isnan isinf mulhi remainder`` and ``element_bitcast``
     - individually cheap to add; nothing in the ported kernels has needed them.
   * - ``view_padding`` modes other than zero, and a masked load with an explicit pad value
     - ``loadMasked`` zero-pads. A softmax over a ragged tail therefore needs a comparison and
       a ``select`` to keep the padding out of the denominator, as
       ``TestTileMasking#raggedKeyTail`` shows.
   * - rounding and NaN-propagation modes
     - CUDA Tile takes per-operation rounding tags; the generated code uses the defaults.

Beyond the operation set:

* Batch processing (``withBatch``) is rejected for tile tasks.
* Tile intrinsics are not yet handled in ``TornadoCUDAIntrinsicsReplacements``, so a
  reflectively resolved tile kernel fails with an explanation instead of miscompiling.
* An ``@Reduce`` task cannot share a ``TaskGraph`` with a tile task: the reduction rewrite
  renames the graph, so a ``GridScheduler`` keyed on the original task id stops matching and
  the tile task silently runs as one block. Use ``tc.sum`` inside the tile instead.
* TMA does not appear in the generated SASS on Ada (sm_89) because TMA is a Hopper unit; the
  alignment hint only pays off on sm_90 and newer.
* No kernel emits CUDA Tile's ``num_ctas``, ``occupancy`` or ``latency`` hints yet, although a
  JIT knows the shapes that would inform them.

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
