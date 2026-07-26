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
 * Warp-vote intrinsics on {@link KernelContext}: {@code simdAny}, {@code simdAll} and
 * {@code simdBallot}, lowered on the CUDA backend to {@code __any_sync} / {@code __all_sync} /
 * {@code __ballot_sync}.
 *
 * <p>Every test asserts a value that a single lane could not produce on its own, so a missing
 * lowering (the {@link KernelContext} default answers for one lane) fails rather than silently
 * returning the caller's own predicate.
 *
 * <p>How to run:
 *
 * <pre>
 * tornado-test --printKernel -V uk.ac.manchester.tornado.unittests.kernelcontext.api.TestWarpVote
 * </pre>
 */
public class TestWarpVote extends TornadoTestBase {

    private static final int WARP_SIZE = 32;
    private static final int WARPS = 4;
    private static final int SIZE = WARP_SIZE * WARPS;

    /** Lane mask of the even lanes of a 32-lane warp: 0x55555555. */
    private static final int EVEN_LANES = 0x55555555;

    /**
     * Each lane votes on "my lane index is even". Every lane of the warp must see the same mask, so
     * the result is the even-lane mask regardless of which lane wrote it.
     */
    private static void ballotKernel(KernelContext ctx, IntArray out) {
        int globalIdx = ctx.globalIdx;
        int localIdx = ctx.localIdx;
        out.set(globalIdx, ctx.simdBallot(localIdx % 2 == 0));
    }

    /**
     * One lane per warp holds a true predicate. {@code simdAny} must be true for every lane of the
     * warp - including the 31 whose own predicate is false - and {@code simdAll} false for all of
     * them. Encoded as {@code any * 2 + all} so one buffer covers both.
     */
    private static void anyAllKernel(KernelContext ctx, IntArray out) {
        int globalIdx = ctx.globalIdx;
        int localIdx = ctx.localIdx;
        boolean predicate = localIdx == 7;
        int any = ctx.simdAny(predicate) ? 1 : 0;
        int all = ctx.simdAll(predicate) ? 1 : 0;
        out.set(globalIdx, any * 2 + all);
    }

    /** Predicate true on every lane: {@code simdAll} must hold. */
    private static void allTrueKernel(KernelContext ctx, IntArray out) {
        int globalIdx = ctx.globalIdx;
        out.set(globalIdx, ctx.simdAll(globalIdx >= 0) ? 1 : 0);
    }

    /**
     * Warp-aggregated counting, the reason ballot exists: each lane derives its rank among the
     * passing lanes of its warp from the ballot mask, with no atomics and no shared memory.
     */
    private static void laneRankKernel(KernelContext ctx, IntArray out) {
        int globalIdx = ctx.globalIdx;
        int localIdx = ctx.localIdx;
        int lane = localIdx % 32;
        int mask = ctx.simdBallot(localIdx % 3 == 0);
        int rank = Integer.bitCount(mask & ((1 << lane) - 1));
        out.set(globalIdx, rank);
    }

    private static IntArray runKernel(String name, KernelTask task) throws TornadoExecutionPlanException {
        IntArray out = new IntArray(SIZE);
        out.init(-1);
        KernelContext context = new KernelContext();
        WorkerGrid worker = new WorkerGrid1D(SIZE);
        worker.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler grid = new GridScheduler("warpVote." + name, worker);

        TaskGraph taskGraph = new TaskGraph("warpVote") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, out) //
                .task(name, task, context, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(immutableTaskGraph)) {
            plan.withGridScheduler(grid).execute();
        }
        return out;
    }

    /** Signature of the kernels above, so {@link #runKernel} can build the task graph for each. */
    private interface KernelTask extends uk.ac.manchester.tornado.api.common.TornadoFunctions.Task2<KernelContext, IntArray> {
    }

    private void skipUnsupportedBackends() {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        assertNotBackend(TornadoVMBackendType.METAL);
        assertNotBackend(TornadoVMBackendType.PTX);
    }

    @Test
    public void testBallotEvenLanes() throws TornadoExecutionPlanException {
        skipUnsupportedBackends();
        IntArray out = runKernel("ballot", TestWarpVote::ballotKernel);
        for (int i = 0; i < SIZE; i++) {
            assertEquals("lane " + i, EVEN_LANES, out.get(i));
        }
    }

    @Test
    public void testAnyAndAll() throws TornadoExecutionPlanException {
        skipUnsupportedBackends();
        IntArray out = runKernel("anyAll", TestWarpVote::anyAllKernel);
        // any = true (lane 7 passes), all = false: encoded as 1 * 2 + 0.
        for (int i = 0; i < SIZE; i++) {
            assertEquals("lane " + i, 2, out.get(i));
        }
    }

    @Test
    public void testAllTrue() throws TornadoExecutionPlanException {
        skipUnsupportedBackends();
        IntArray out = runKernel("allTrue", TestWarpVote::allTrueKernel);
        for (int i = 0; i < SIZE; i++) {
            assertEquals("lane " + i, 1, out.get(i));
        }
    }

    @Test
    public void testWarpAggregatedLaneRank() throws TornadoExecutionPlanException {
        skipUnsupportedBackends();
        IntArray out = runKernel("laneRank", TestWarpVote::laneRankKernel);
        for (int i = 0; i < SIZE; i++) {
            int lane = i % WARP_SIZE;
            // Lanes 0, 3, 6, ... pass; a lane's rank is how many passing lanes precede it.
            int expected = (lane + 2) / 3;
            assertEquals("lane " + i, expected, out.get(i));
        }
    }
}
