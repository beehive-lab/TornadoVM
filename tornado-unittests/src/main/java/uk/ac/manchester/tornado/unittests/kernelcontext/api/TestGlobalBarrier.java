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
 * {@link KernelContext#globalBarrier()} had zero coverage anywhere in the suite -- every
 * local-memory/barrier/atomic test in {@code kernelcontext/} uses {@code localBarrier()} only.
 *
 * <p>The barrier synchronises the work-group and orders that group's <em>global</em> memory accesses
 * ({@code barrier(CLK_GLOBAL_MEM_FENCE)} on OpenCL, {@code __syncthreads()} on CUDA). Only the
 * global-memory ordering is guaranteed across backends: CUDA's {@code __syncthreads()} additionally
 * makes prior shared-memory accesses visible block-wide, but that is a CUDA-specific strengthening
 * and is not implied by the OpenCL lowering. {@code testGlobalBarrierOrdersSharedArrayAccess} relies
 * on that stronger guarantee and so is CUDA-only; the global-memory tests are not.
 *
 * <p>The scope is the work-group, not the whole grid: nothing here assumes ordering between blocks.
 *
 * <p>How to run?
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.api.TestGlobalBarrier
 * </pre>
 */
public class TestGlobalBarrier extends TornadoTestBase {

    private static final int THREADS = 256;
    private static final int BLOCKS = 4;
    private static final int SIZE = THREADS * BLOCKS;

    /**
     * Mirrors {@code TestAtomicRmw#blockMaxKernel} but synchronises with {@code globalBarrier()}
     * instead, to confirm it still correctly orders the shared-array load/atomic/read-back sequence.
     */
    private static void blockMaxWithGlobalBarrier(KernelContext ctx, IntArray in, IntArray out) {
        int[] shared = ctx.allocateIntLocalArray(1);
        if (ctx.localIdx == 0) {
            shared[0] = Integer.MIN_VALUE;
        }
        ctx.globalBarrier();
        ctx.atomicMax(shared, 0, in.get(ctx.globalIdx));
        ctx.globalBarrier();
        if (ctx.localIdx == 0) {
            out.set(ctx.groupIdx, shared[0]);
        }
    }

    /**
     * Every thread writes one global element, then thread 0 of each block reads back the whole block's
     * writes. Without the barrier the reads may observe stale values. This is the case the barrier's
     * cross-backend contract actually covers, so it is not restricted to CUDA.
     */
    public static void globalFenceKernel(KernelContext ctx, IntArray scratch, IntArray out) {
        scratch.set(ctx.globalIdx, ctx.globalIdx);
        ctx.globalBarrier();
        if (ctx.localIdx == 0) {
            int sum = 0;
            int base = ctx.groupIdx * THREADS;
            for (int i = 0; i < THREADS; i++) {
                sum += scratch.get(base + i);
            }
            out.set(ctx.groupIdx, sum);
        }
    }

    /** Same shape through local memory, so the two barriers are exercised the same way. */
    public static void localFenceKernel(KernelContext ctx, IntArray out) {
        int[] tile = ctx.allocateIntLocalArray(THREADS);
        tile[ctx.localIdx] = ctx.globalIdx;
        ctx.globalBarrier();
        if (ctx.localIdx == 0) {
            int sum = 0;
            for (int i = 0; i < THREADS; i++) {
                sum += tile[i];
            }
            out.set(ctx.groupIdx, sum);
        }
    }

    private static int expectedBlockSum(int block) {
        int base = block * THREADS;
        int sum = 0;
        for (int i = 0; i < THREADS; i++) {
            sum += base + i;
        }
        return sum;
    }

    private static GridScheduler gridFor(String taskId) {
        WorkerGrid worker = new WorkerGrid1D(SIZE);
        worker.setLocalWork(THREADS, 1, 1);
        return new GridScheduler("barrier." + taskId, worker);
    }

    @Test
    public void testGlobalBarrierOrdersSharedArrayAccess() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);

        IntArray in = new IntArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            in.set(i, (i * 7919) % 10007);
        }
        IntArray out = new IntArray(BLOCKS);

        KernelContext context = new KernelContext();
        WorkerGrid worker = new WorkerGrid1D(SIZE);
        worker.setLocalWork(THREADS, 1, 1);
        GridScheduler grid = new GridScheduler("s0.t0", worker);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("t0", TestGlobalBarrier::blockMaxWithGlobalBarrier, context, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(grid).execute();
        }

        for (int block = 0; block < BLOCKS; block++) {
            int expected = Integer.MIN_VALUE;
            for (int lane = 0; lane < THREADS; lane++) {
                expected = Math.max(expected, in.get(block * THREADS + lane));
            }
            assertEquals(expected, out.get(block));
        }
    }

    @Test
    public void testGlobalBarrierOrdersGlobalWrites() throws TornadoExecutionPlanException {
        IntArray scratch = new IntArray(SIZE);
        IntArray out = new IntArray(BLOCKS);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("barrier") //
                .task("global", TestGlobalBarrier::globalFenceKernel, context, scratch, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(gridFor("global")).execute();
        }

        for (int block = 0; block < BLOCKS; block++) {
            assertEquals(expectedBlockSum(block), out.get(block));
        }
    }

    @Test
    public void testGlobalBarrierOrdersLocalWrites() throws TornadoExecutionPlanException {
        IntArray out = new IntArray(BLOCKS);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("barrier") //
                .task("local", TestGlobalBarrier::localFenceKernel, context, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(gridFor("local")).execute();
        }

        for (int block = 0; block < BLOCKS; block++) {
            assertEquals(expectedBlockSum(block), out.get(block));
        }
    }
}
