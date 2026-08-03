/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.unittests.kernelcontext.api;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Regression test for a CUDA-backend codegen bug: {@code KernelContext.atomicAdd(int[]
 * localArray, dynamicIndex, val)} -- atomicAdd into a LOCAL/shared-memory array at a
 * RUNTIME-COMPUTED index -- silently dropped the index and always updated element 0.
 *
 * <p>
 * Root cause: {@code CUDAAddressNode#setMemoryAccess} handles local/private memory differently
 * from global memory. For global arrays the index is folded into the base pointer via explicit
 * pointer arithmetic ({@code emitArithmetic().emitAdd(base, index)}) before the address is built,
 * so by the time it reaches a consumer the offset is already baked in. For local/private arrays
 * the base and index are instead kept SEPARATE on the {@code MemoryAccess} object (intended to be
 * rendered as an array subscript, {@code base[index]}) -- and {@code LoadStmt} (regular array
 * reads) already renders that subscript correctly via its own dedicated code path. But
 * {@code CUDAUnary.AtomOperation} (the atomic intrinsic emitter) only ever called
 * {@code address.emit(crb, asm)}, which renders the bare base value and never consulted
 * {@code address.getIndex()} at all -- so every dynamically-indexed local-array atomic silently
 * collapsed onto element 0, with no error, no exception, just a wrong answer (confirmed via
 * {@code --printKernel}: the generated CUDA computed the byte offset for the index but the
 * emitted {@code atomicAdd(...)} call never referenced it).
 * <p>
 * Fixed in {@code CUDAUnary.AtomOperation#emit} by appending the array-subscript form
 * (mirroring {@code LoadStmt#emitIntegerBasedIndexCode}'s handling of the same
 * {@code MemoryAccess} shape) whenever {@code address.getIndex()} is non-null.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.api.TestLocalArrayDynamicIndexAtomics
 * </code>
 */
public class TestLocalArrayDynamicIndexAtomics extends TornadoTestBase {

    private static final int NUM_BINS = 16;
    private static final int THREADS = 256;
    private static final int BLOCKS = 8;
    private static final int SIZE = THREADS * BLOCKS;

    /**
     * The canonical two-level (per-block-local, then merged into global) histogram: every
     * thread's bucket is a RUNTIME value (derived from input data), so this exercises the exact
     * shape that was broken -- a dynamic index into a local/shared array passed to atomicAdd.
     */
    private static void twoLevelHistogram(KernelContext ctx, IntArray input, IntArray globalHist) {
        int[] localHist = ctx.allocateIntLocalArray(NUM_BINS);
        if (ctx.localIdx < NUM_BINS) {
            localHist[ctx.localIdx] = 0;
        }
        ctx.localBarrier();

        int bin = input.get(ctx.globalIdx) % NUM_BINS;
        ctx.atomicAdd(localHist, bin, 1);
        ctx.localBarrier();

        if (ctx.localIdx < NUM_BINS) {
            ctx.atomicAdd(globalHist, ctx.localIdx, localHist[ctx.localIdx]);
        }
    }

    @Test
    public void testTwoLevelHistogramWithDynamicLocalIndex() throws TornadoExecutionPlanException {
        // Metal has the same dropped-index defect, but fixing it is a bigger change than it was
        // for CUDA and OpenCL. MetalUnary.AtomOperation hardcodes the `device` address space in
        // its atomic casts (e.g. `atomic_fetch_add_explicit((device atomic_int *) ...)`), which
        // is already wrong for a threadgroup array regardless of the index -- MSL enforces strict
        // address-space segregation, so local-memory atomics need the qualifier to vary too.
        // That is separate from the index arithmetic fixed here, and it cannot be validated on
        // this machine, so the test is guarded rather than the fix guessed at.
        assertNotBackend(TornadoVMBackendType.METAL, "MetalUnary.AtomOperation hardcodes the `device` address space, so threadgroup atomics need an address-space fix beyond the index arithmetic");

        Random r = new Random(9);
        IntArray input = new IntArray(SIZE);
        int[] expected = new int[NUM_BINS];
        for (int i = 0; i < SIZE; i++) {
            int value = r.nextInt(100000);
            input.set(i, value);
            expected[value % NUM_BINS]++;
        }

        IntArray globalHist = new IntArray(NUM_BINS);
        globalHist.init(0);
        KernelContext context = new KernelContext();

        WorkerGrid worker = new WorkerGrid1D(SIZE);
        worker.setLocalWork(THREADS, 1, 1);
        GridScheduler grid = new GridScheduler("s0.t0", worker);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, globalHist) //
                .task("t0", TestLocalArrayDynamicIndexAtomics::twoLevelHistogram, context, input, globalHist) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, globalHist);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(grid).execute();
        }

        int total = 0;
        for (int b = 0; b < NUM_BINS; b++) {
            assertEquals("bin " + b, expected[b], globalHist.get(b));
            total += globalHist.get(b);
        }
        assertEquals(SIZE, total);
    }

}
