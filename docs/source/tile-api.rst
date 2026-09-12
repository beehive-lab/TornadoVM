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

Every operation below is a real method on ``TileContext`` or ``PartitionView``, and every one has
a JVM implementation as well as a lowering, so a tile kernel also runs as ordinary Java.

That dual life is worth knowing when a test passes unexpectedly. If a tile kernel fails to
compile and ``tornado.recover.bailout`` is enabled, the task quietly runs the JVM implementation
on the host and produces the right answer, so nothing fails. ``tornado-test`` sets
``-Dtornado.recover.bailout=False`` for this reason; outside it, confirm with ``--printKernel``
that a kernel was generated at all.

Block indices
=============

.. list-table::
   :widths: 34 26 40
   :header-rows: 1

   * - Java
     - CUDA Tile C++
     - Notes
   * - ``bidX() bidY() bidZ()``
     - ``ct::bid().x/.y/.z``
     - index of this tile block
   * - ``numBlocksX() numBlocksY() numBlocksZ()``
     - ``ct::num_blocks().x/.y/.z``
     - grid size; what a persistent kernel strides by

``setBlockIndex`` and ``setBlockCount`` exist for the JVM path only - they let a plain Java caller
place a kernel invocation when running it as ordinary code, and have no device meaning.

Views, partitions and memory
============================

.. list-table::
   :widths: 34 26 40
   :header-rows: 1

   * - Java
     - CUDA Tile C++
     - Notes
   * - ``view(array, extents...)``
     - ``ct::tensor_span``
     - 1, 2 or 3 extents; extents may be runtime values
   * - ``view(fp8Array, format, extents...)``
     - ``ct::tensor_span``
     - fp8 only: the format (``FP8_E4M3`` or ``FP8_E5M2``) is an argument, because the buffer
       carries both accessors and no record of which it holds. Rank 1 and 2
   * - ``partition(view, tile...)``
     - ``ct::partition_view``
     - 1, 2 or 3 tile extents, each a power-of-two constant
   * - ``load(blocks...)``
     - ``view.load(...)``
     - rank 1, 2 or 3; caller guarantees in bounds
   * - ``loadMasked(blocks...)``
     - ``view.load_masked(...)``
     - zero-pads a partial tile
   * - ``store(tile, blocks...)``
     - ``view.store(...)``
     - rank 1, 2 or 3
   * - ``storeMasked(tile, blocks...)``
     - ``view.store_masked(...)``
     - writes only the in-bounds elements
   * - ``atomicAdd(tile, blocks...)``
     - ``ct::atomic_add`` over a pointer tile
     - rank 1 and 2; relaxed order, device scope. See *Atomic accumulation*

The buffer types a view accepts, and the element type each maps to, are listed under
*Element types* below.

Creating tiles
==============

.. list-table::
   :widths: 34 26 40
   :header-rows: 1

   * - Java
     - CUDA Tile C++
     - Notes
   * - ``zeros(dtype, shape...)``
     - ``ct::zeros``
     - rank 1 or 2
   * - ``full(dtype, value, shape...)``
     - ``ct::full``
     - the fill value must be a compile-time constant: it is emitted into the source
   * - ``iota(dtype, shape...)``
     - ``ct::iota``
     - 0, 1, 2, ... in row-major order. The rank-2 forms ``(rows, 1)`` and ``(1, cols)`` are the
       index tiles a mask is built from

Elementwise arithmetic
======================

.. list-table::
   :widths: 34 26 40
   :header-rows: 1

   * - Java
     - CUDA Tile C++
     - Notes
   * - ``add sub mul div``
     - ``+ - * /``
     - broadcasting: a dimension of extent 1 stretches
   * - ``maximum(a, b)`` / ``minimum(a, b)``
     - ``ct::max`` / ``ct::min``
     - two operands, elementwise - not reductions
   * - ``scale(tile, s)``
     - ``tile * (E) s``
     - no named function in CUDA Tile. ``s`` may be a runtime value, unlike a ``full`` fill
   * - ``fma(a, b, c)``
     - ``ct::fma``
     - elementwise, unlike ``mma``

Comparisons, masks and select
=============================

.. list-table::
   :widths: 34 26 40
   :header-rows: 1

   * - Java
     - CUDA Tile C++
     - Notes
   * - ``lessThan lessOrEqual greaterThan greaterOrEqual equalTo notEqualTo``
     - ``< <= > >= == !=``
     - each takes ``(Tile, Tile)`` or ``(Tile, double)``. The result is a ``DType.PRED`` tile
   * - ``logicalAnd(m1, m2)`` / ``logicalOr(m1, m2)``
     - ``m1 & m2`` / ``m1 | m2``
     - combines two predicate tiles; non-predicate operands are rejected at compile time
   * - ``select(mask, whenTrue, whenFalse)``
     - ``ct::select``
     - the only consumer of a predicate tile

The scalar form is not a convenience. A mask usually compares against a runtime extent or block
offset, and the tile-to-tile form would need a ``full(...)`` whose fill value has to fold.

Math
====

.. list-table::
   :widths: 34 26 40
   :header-rows: 1

   * - Java
     - CUDA Tile C++
     - Notes
   * - ``exp exp2 log log2``
     - ``ct::exp ct::exp2 ct::log ct::log2``
     - ``exp2``/``log2`` are the cheaper pair, and what NVIDIA's own softmax kernels use
   * - ``sqrt rsqrt tanh sin cos abs floor``
     - ``ct::sqrt`` ...
     - elementwise, shape preserved

Reductions
==========

.. list-table::
   :widths: 34 26 40
   :header-rows: 1

   * - Java
     - CUDA Tile C++
     - Notes
   * - ``sum(tile, axis)``
     - ``ct::sum``
     - rank-2 tiles, axis 0 or 1
   * - ``max(tile, axis)`` / ``min(tile, axis)``
     - ``ct::reduce_max`` / ``ct::reduce_min``
     - note the spelling: ``ct::max`` is the elementwise form
   * - ``prod(tile, axis)``
     - ``ct::prod``
     - a product leaves the range of a narrow element type quickly; ``TestTileMasking`` keeps its
       inputs near one for that reason

A reduction keeps the reduced dimension, so the result broadcasts back against the tile it came
from. It cannot be stored into the view it came from, though: a ``32x1`` tile against a
``32x32`` partition view is refused by CUDA Tile's ``same_shape`` constraint. Reduce, use the
result, and store a tile of the view's own shape.

Matrix multiply
===============

.. list-table::
   :widths: 34 26 40
   :header-rows: 1

   * - Java
     - CUDA Tile C++
     - Notes
   * - ``mma(a, b, acc)``
     - ``ct::mma``
     - ``a * b + acc``. Tensor cores; the instruction is the tile compiler's choice. Operand and
       accumulator types are validated against the ``mmaf``/``mmai`` tables

Shape and type
==============

.. list-table::
   :widths: 34 26 40
   :header-rows: 1

   * - Java
     - CUDA Tile C++
     - Notes
   * - ``broadcast(tile, shape...)``
     - ``ct::broadcast``
     - stretches dimensions of extent 1; rank 1 or 2
   * - ``reshape(tile, shape...)``
     - ``ct::reshape``
     - same element count, row-major; rank 1, 2 or 3, and the only way to change rank
   * - ``extract(tile, shape..., blocks...)``
     - ``ct::extract``
     - a sub-tile, indexed in **sub-tile units** like a view addresses blocks; rank 1 or 2
   * - ``transpose(tile)``
     - ``ct::transpose``
     - rank 2
   * - ``cast(tile, dtype)``
     - ``ct::element_cast<E>``
     - any scalar pair, narrowing included. There is no ``ct::cast``

Control flow
============

.. list-table::
   :widths: 34 26 40
   :header-rows: 1

   * - Java
     - CUDA Tile C++
     - Notes
   * - a counted ``for`` loop
     - ``ct::irange``
     - write an ordinary loop; no iterator API, which would allocate
   * - ``if`` on a scalar
     - ``if``
     - per-element conditions are ``select``, not control flow

Query methods
=============

``Tile`` exposes ``getDType``, ``getRank``, ``getDimension`` and ``getElement`` (JVM path only, for
tests and host debugging); ``TensorView`` exposes ``getDType``, ``getRank``, ``getExtent``;
``PartitionView`` adds ``getTileDimension`` and ``getBlockCount``.

Rank support at a glance
========================

Rank coverage is deliberately uneven: rank 3 exists for *addressing*, and arithmetic stays rank 2.

.. list-table::
   :widths: 50 50
   :header-rows: 1

   * - Ranks
     - Operations
   * - 1, 2, 3
     - ``view`` (except fp8), ``partition``, ``load``, ``loadMasked``, ``store``,
       ``storeMasked``, ``reshape``
   * - 1, 2
     - ``zeros``, ``full``, ``iota``, ``atomicAdd``, ``broadcast``, ``extract``, fp8 ``view``
   * - 2 only
     - ``mma``, ``sum``, ``max``, ``min``, ``prod``, ``transpose``
   * - any
     - the elementwise operations, the comparisons, ``select``, ``scale``, ``cast``, the math
       functions

Launch hints
************

``-Dtornado.cuda.tile.hints=occupancy=2,num_cta_in_cga=1`` puts a
``[[ using cutile : hint(0, ...) ]]`` attribute on every generated tile kernel. The keys are
CUDA Tile's own; ``hint(0, ...)`` applies to all architectures.

The hint reaches the tile compiler and changes its resource decisions. Measured on sm_89 for the
FP16 GEMM of ``demos/22`` at ``n = 1024``:

.. list-table::
   :header-rows: 1

   * - Hint
     - Registers / shared
     - Kernel time
   * - none (compiler's choice)
     - 110 / 36 KB
     - 65.3 us
   * - ``occupancy=2``
     - 110 / 36 KB
     - 63.2 us (-2.9%, reproducible over three runs)
   * - ``occupancy=4``
     - 125 / 20 KB
     - 132.5 us (2.0x slower)
   * - ``occupancy=8``
     - 64 / 8 KB
     - 459.5 us (7.0x slower)

**Nothing derives hints automatically, and this is why.** The compiler's default is already
close to the best of these, and forcing occupancy up costs 2x to 7x: a heuristic that maximised
occupancy would be actively harmful at this shape. Deriving hints from the shapes a JIT knows is
still the most promising thing this integration could do that a hand-written kernel cannot, but
it needs a model of the tradeoff rather than a rule of thumb.

One caveat, verified against 13.3.73: **hint keys are not validated**. An invented key and an
absurd value both compile silently and do nothing, so a hint that appears to have no effect may
simply not exist.

Rank
****

``view`` and ``partition`` take one, two or three extents. Rank 3 is for *addressing*: a batch or
head dimension becomes a block index instead of arithmetic folded into the row index.

Arithmetic stays rank 2 - ``mma``, the reductions and ``transpose`` are rank-2 operations - so a
rank-3 load is reshaped before it is used:

.. code-block:: java

    PartitionView aView = tc.partition(tc.view(a, batch, m, k), 1, TILE, TILE);
    Tile left = tc.reshape(aView.load(batchIndex, rowBlock, step), TILE, TILE);
    acc = tc.mma(left, right, acc);

That is the same shape as NVIDIA's own kernels, which load a rank-4 tile from a ``[B, H, S, D]``
view and reshape it before the first multiply.

View extents may be runtime values; tile extents may not, because a tile shape is part of the
kernel's type. So ``tc.view(in, batch, rows, WIDTH)`` is fine with ``batch`` and ``rows`` as
parameters, while the ``WIDTH`` passed to ``partition`` has to be a literal or a
``static final int``.

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
Observed lowering on sm_89: ``ATOMG.E.ADD.F32.FTZ.RN.STRONG.GPU`` - device scope matters, since
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
   * - rank 4 and above, and rank 3 for some operations
     - ``view``, ``partition``, the loads and stores and ``reshape`` go up to rank 3, which covers
       a batch or head dimension; tile creation, ``atomicAdd``, ``broadcast`` and ``extract`` stop
       at rank 2, and the arithmetic that needs a specific rank (``mma``, the reductions,
       ``transpose``) is rank 2 by nature. *Rank support at a glance* above lists which is which.
       CUDA Tile itself goes beyond rank 3: an attention kernel folding two leading dimensions
       into one index would need rank 4 to stop doing so.
   * - the atomic family beyond ``atomicAdd``
     - ``PartitionView.atomicAdd`` exists (see *Atomic accumulation*); ``atomic_sub``, ``atomic_min``,
       ``atomic_max``, the bitwise atomics, ``atomic_xchg`` and ``atomic_compare_exchange`` do
       not, nor do the masked forms or a choice of memory order and scope.
   * - ``permute``
     - on a rank-2 tile the only non-identity permutation is ``transpose``, which exists, so
       this would add surface without capability. ``reshape``, ``broadcast`` and ``extract`` are
       implemented.
   * - ``partial_sum`` / ``partial_prod``
     - no scans, so a cumulative softmax or a prefix sum needs a different formulation.
   * - ``tan sinh cosh atan2 isnan isinf mulhi remainder``, ``element_bitcast`` and a
       ``logicalNot``
     - individually cheap to add; nothing in the ported kernels has needed them. A mask can be
       inverted today by swapping the operands of ``select`` or reversing the comparison.
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
* Launch hints can be set but are not derived. See *Launch hints* above.

Examples
********

``tornado-examples`` carries four runnable tile examples. Each rewrites a known algorithm with
``TileContext`` beside thread-level versions of the same thing, checks every result against a
sequential Java reference, and prints a table of times:

.. code-block:: bash

    # smallest useful tile kernel
    tornado -m tornado.examples/uk.ac.manchester.tornado.examples.tile.TileVectorAdd

    # matrix multiply: @Parallel, KernelContext shared-memory tiles, TileContext
    tornado -m tornado.examples/uk.ac.manchester.tornado.examples.tile.TileMatrixMultiply 512 20

    # row softmax: the reductions and the broadcast back across the row
    tornado -m tornado.examples/uk.ac.manchester.tornado.examples.tile.TileSoftmax 4096 50

    # attention: thread-per-query against fused flash attention
    tornado -m tornado.examples/uk.ac.manchester.tornado.examples.tile.TileAttention 1024 1024 20

The times are wall clock and include JVM-side dispatch, which dominates at small sizes; the
examples say so, and ``TileExamples`` carries the nsys recipe for kernel time alone.

Verifying and profiling
***********************

.. code-block:: bash

    # the generated CUDA Tile C++ (expect ct:: calls and no inline PTX)
    tornado --printKernel -m tornado.examples/...TileVectorAdd

    # tensor cores in the cached cubin
    cuobjdump -sass $TORNADOVM_HOME/var/cuda-codecache/device-0-0/<kernel>-*.cubin | grep HMMA

    # the unit tests, by area:
    #   Elementwise Matmul Chaining          the core API and mixing with other task kinds
    #   RowKernels LlmKernels                softmax, norms, activations, RoPE, dropout
    #   Attention AttentionVariants          flash attention, GQA, split-KV decode, sinks, soft-cap
    #   GemmVariants Masking                 persistent and transposed GEMM; causal and ragged masks
    #   DTypes Atomics Shapes Rank3          int8/fp64/fp8, atomicAdd, broadcast/reshape/extract
    tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileMatmul
    tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileAttention
    tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileMasking

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
