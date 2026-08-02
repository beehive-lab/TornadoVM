/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.unittests.kernelcontext.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * {@link KernelContext#globalBarrier()} - a work-group barrier that also orders the group's global
 * memory accesses ({@code barrier(CLK_GLOBAL_MEM_FENCE)} on OpenCL, {@code __syncthreads()} on CUDA,
 * which already makes prior global and shared accesses visible across the block).
 *
 * <p>The scope is the work-group, not the whole grid: nothing here assumes ordering between blocks.
 *
 * <p>How to run:
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.api.TestGlobalBarrier
 * </pre>
 */
public class TestGlobalBarrier extends TornadoTestBase {

    private static final int THREADS = 64;
    private static final int BLOCKS = 4;
    private static final int SIZE = THREADS * BLOCKS;

    /**
     * Every thread writes one global element, then thread 0 of each block reads back the whole block's
     * writes. Without the barrier the reads may observe stale values.
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
