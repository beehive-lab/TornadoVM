.. _under-the-hood:

Under the Hood: From Java Bytecode to a GPU Kernel
==================================================

TornadoVM is not a library that calls into a hand-written GPU kernel. It is a
*JIT compiler* that takes the bytecode of a plain Java method and emits a real
GPU kernel for each backend it supports. This article follows one tiny method
all the way down and shows the **OpenCL C**, **NVIDIA PTX**, **SPIR-V** and
**Apple Metal** that TornadoVM generates for it.

Every kernel shown below is checked into this repository under
``tornado-assembly/src/examples/generated/`` (``add.cl``, ``add.ptx``,
``add.spv``, ``add.metal``). Nothing here requires a GPU to reproduce — see
`Reproduce this yourself`_ at the end.

The Java we start from
----------------------

A textbook parallel vector addition. The only thing that is not ordinary Java is
the ``@Parallel`` annotation, which marks the loop as safe to run as a parallel
kernel:

.. code:: java

   public static void add(IntArray a, IntArray b, IntArray c) {
       for (@Parallel int i = 0; i < c.getSize(); i++) {
           c.set(i, a.get(i) + b.get(i));
       }
   }

We wire it into a ``TaskGraph`` and execute it:

.. code:: java

   TaskGraph taskGraph = new TaskGraph("s0")
       .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)
       .task("t0", VectorAdd::add, a, b, c)
       .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

   try (TornadoExecutionPlan plan =
            new TornadoExecutionPlan(taskGraph.snapshot())) {
       plan.execute();
   }

That ``.task(...)`` reference is the entry point the compiler picks up. From the
same bytecode, TornadoVM produces the four kernels below.

OpenCL C
--------

.. code:: c

   __kernel void add(__global long *_kernel_context, __constant uchar *_constant_region,
                     __local uchar *_local_region, __global int *_atomics,
                     __global uchar *a, __global uchar *b, __global uchar *c)
   {
     ulong ul_8, ul_10, ul_1, ul_0, ul_2, ul_12;
     long l_5, l_7, l_6;
     int i_11, i_9, i_15, i_14, i_13, i_3, i_4;

     // BLOCK 0
     ul_0  =  a;
     ul_1  =  b;
     ul_2  =  c;
     i_3  =  get_global_id(0);
     // BLOCK 1 MERGES [0 2 ]
     i_4  =  i_3;
     for(;i_4 < 8;)  {
       // BLOCK 2
       l_5  =  (long) i_4;
       l_6  =  l_5 << 2;
       l_7  =  l_6 + 16L;
       ul_8  =  ul_0 + l_7;
       i_9  =  *((__global int *) ul_8);
       ul_10  =  ul_1 + l_7;
       i_11  =  *((__global int *) ul_10);
       ul_12  =  ul_2 + l_7;
       i_13  =  i_9 + i_11;
       *((__global int *) ul_12)  =  i_13;
       i_14  =  get_global_size(0);
       i_15  =  i_14 + i_4;
       i_4  =  i_15;
     }
     // BLOCK 3
     return;
   }

NVIDIA PTX
----------

.. code:: text

    .visible .entry add(.param .u64 .ptr .global .align 8 kernel_context,
                        .param .u64 .ptr .global .align 8 a,
                        .param .u64 .ptr .global .align 8 b,
                        .param .u64 .ptr .global .align 8 c) {
        .reg .s64 rsd<3>;
        .reg .pred rpb<2>;
        .reg .u32 rui<5>;
        .reg .s32 rsi<9>;
        .reg .u64 rud<9>;

     BLOCK_0:
        ld.param.u64	rud1, [a];
        ld.param.u64	rud2, [b];
        ld.param.u64	rud3, [c];
        mov.u32	rui0, %nctaid.x;          // number of blocks
        mov.u32	rui1, %ntid.x;            // threads per block
        mul.wide.u32	rud4, rui0, rui1;     // global size = blocks * blockDim
        cvt.s32.u64	rsi0, rud4;
        mov.u32	rui2, %tid.x;             // thread index in block
        mov.u32	rui3, %ctaid.x;           // block index
        mad.lo.s32	rsi1, rui3, rui1, rui2;   // global id = ctaid*ntid + tid

     BLOCK_1:
        mov.s32	rsi2, rsi1;
     LOOP_COND_1:
        setp.lt.s32	rpb0, rsi2, 8;
        @!rpb0 bra	BLOCK_3;              // exit loop when i >= 8

     BLOCK_2:
        add.s32	rsi3, rsi2, 4;            // (i + 4) ...
        cvt.s64.s32	rsd0, rsi3;
        shl.b64	rsd1, rsd0, 2;            // ... << 2  -> byte offset incl. header
        add.u64	rud5, rud1, rsd1;
        ld.global.s32	rsi4, [rud5];         // a[i]
        add.u64	rud6, rud2, rsd1;
        ld.global.s32	rsi5, [rud6];         // b[i]
        add.u64	rud7, rud3, rsd1;
        add.s32	rsi6, rsi4, rsi5;         // a[i] + b[i]
        st.global.s32	[rud7], rsi6;         // c[i] = ...
        add.s32	rsi7, rsi0, rsi2;         // i += global size  (grid-stride)
        mov.s32	rsi2, rsi7;
        bra.uni	LOOP_COND_1;

     BLOCK_3:
        ret;
     }

SPIR-V
------

SPIR-V (dispatched through the Intel Level Zero API) is **not** a text format —
it is a binary module. ``add.spv`` in this repository is 3,600 bytes and begins
with the SPIR-V magic word ``0x07230203``:

.. code:: text

   00000000: 0302 2307 0000 0100 0000 0700 6100 0000  ..#.........a...
   00000010: 0000 0000 1100 0200 0400 0000 1100 0200  ................
   ...
   00000040: 0100 0000 4f70 656e 434c 2e73 7464 0000  ....OpenCL.std..
   00000060: 0600 0000 0200 0000 6164 6400 0300 0000  ........add.....

Even in the raw bytes you can see the ``OpenCL.std`` extended-instruction-set
import and the ``add`` entry-point name. To read it as human-friendly assembly,
disassemble it with the SPIR-V tools (``spirv-dis add.spv``); the structure
mirrors the OpenCL C above — the same grid-stride loop expressed in SSA form.

Apple Metal
-----------

The Metal backend targets Apple Silicon GPUs (M1/M2/M3/M4). Note the explicit
``[[buffer(N)]]`` / ``[[threadgroup(N)]]`` binding attributes and the
``thread_position_in_grid`` system value standing in for ``get_global_id``:

.. code:: c

   kernel void add(
       device long *_kernel_context [[buffer(0)]],
       constant uchar *_constant_region [[buffer(1)]],
       threadgroup uchar *_local_region [[threadgroup(2)]],
       device int *_atomics [[buffer(3)]],
       device uchar *a [[buffer(4)]],
       device uchar *b [[buffer(5)]],
       device uchar *c [[buffer(6)]],
       uint3 _thread_position_in_grid [[thread_position_in_grid]],
       device uint* _global_sizes [[buffer(7)]])
   {
     // BLOCK 0
     i_3  =  _global_sizes[0];
     i_4  =  _thread_position_in_grid.x;     // == get_global_id(0)
     i_5  =  i_4;
     for(;i_5 < 8;)
     {
       // BLOCK 2
       i_6  =  i_5 + 4;
       i_7  =  i_6 << 2;                      // (i + 4) << 2  byte offset
       ul_8  =  ul_0 + i_7;
       i_9  =  *((device int *) ul_8);        // a[i]
       i_11 =  *((device int *) (ul_1 + i_7));// b[i]
       i_13 =  i_9 + i_11;                    // a[i] + b[i]
       *((device int *) (ul_2 + i_7))  =  i_13;
       i_5  =  i_3 + i_5;                      // grid-stride
     }
     return;
   }

Reading the generated code
--------------------------

The four kernels look different on the surface but encode the **same three
decisions** the compiler made from the Java loop. Once you see them, every
backend reads the same way.

**1. The loop index becomes the thread id.**
``int i = 0`` does not become a counter that starts at zero. It becomes the
global thread index:

- OpenCL: ``i_3 = get_global_id(0)``
- PTX: ``mad.lo.s32 rsi1, %ctaid.x, %ntid.x, %tid.x`` — i.e. ``blockIdx*blockDim + threadIdx``
- Metal: ``_thread_position_in_grid.x``

Each GPU thread runs the loop body for *its own* ``i``. Thousands of threads
cover the iteration space in parallel instead of one thread looping.

**2. The** ``IntArray`` **becomes a raw buffer + an offset.**
``a``, ``b``, ``c`` arrive as ``__global uchar *`` (OpenCL) / ``.ptr .global``
(PTX) / ``device uchar *`` (Metal) — byte pointers, not typed int arrays. To
read element ``i`` the compiler computes ``(i << 2)`` (4 bytes per ``int``) and
adds a **16-byte header offset** (the ``+ 16L`` in OpenCL, ``(i + 4) << 2`` in
PTX/Metal) that skips the on-device array header before the element data. The
``get``/``set`` calls disappear entirely — they are pointer loads and stores.

**3. The loop turns into a grid-stride loop.**
After processing element ``i``, each thread jumps ahead by the *total* number of
threads (``get_global_size(0)`` / the precomputed global size / ``i_3``) rather
than by one. This lets a launch with fewer threads than elements still cover the
whole array, and keeps the kernel correct for any work-group configuration. The
``i < 8`` bound is the array size, which was a compile-time constant when these
kernels were generated; for a runtime size it would be a kernel argument.

The leading ``_kernel_context``, ``_constant_region``, ``_local_region`` and
``_atomics`` parameters are TornadoVM's calling-convention slots (work-group
metadata, constant memory, scratch local memory, and atomics), present on every
generated kernel regardless of whether a given kernel uses them.

Reproduce this yourself
-----------------------

You do **not** need a GPU to see the generated code. Two options:

**Print the kernel from any run.** Add ``--printKernel`` (or set
``-Dtornado.print.kernel=True``) when you launch a TornadoVM program and the
generated backend code is printed to stdout:

.. code:: bash

   tornado --printKernel -m tornado.examples/uk.ac.manchester.tornado.examples.VectorAddInt

**Use virtual-device mode** to emit kernel code with no hardware at all.
TornadoVM ships pre-generated virtual-device kernels under
``tornado-assembly/src/examples/generated/virtualDevice/`` and a virtual OpenCL
device that produces code without a physical accelerator. This is exactly how
the artifacts in this article are kept in the repository.

The same Java, four targets, one compiler — that is the moat: a mature,
multi-backend Java-to-GPU JIT, not a binding to a single vendor's runtime.
