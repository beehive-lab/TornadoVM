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

    /**
     * A block of four warps, where the predicate holds for every lane of the odd warps and no lane
     * of the even ones. A warp-wide vote therefore answers differently for warps of the <b>same
     * block</b>: 3 (any and all) against 0 (neither). A vote lowered to a block-wide reduction
     * would answer 2 (any but not all) everywhere, and a per-lane no-op would answer 3 in the odd
     * warps and 0 in the even ones only by accident of the predicate - so the companion
     * {@link #voteInLoopKernel} covers that case.
     */
    private static void multiWarpBlockKernel(KernelContext ctx, IntArray out) {
        int globalIdx = ctx.globalIdx;
        int localIdx = ctx.localIdx;
        int warpId = localIdx / WARP_SIZE;
        boolean predicate = (warpId % 2) == 1;
        int any = ctx.simdAny(predicate) ? 1 : 0;
        int all = ctx.simdAll(predicate) ? 1 : 0;
        out.set(globalIdx, any * 2 + all);
    }

    /**
     * Ballot over a predicate the compiler cannot fold: it comes from device memory, so the mask
     * has to be built at runtime. Each lane reports the population count of its warp's mask, which
     * every lane of a warp must agree on.
     */
    private static void dataBallotKernel(KernelContext ctx, IntArray input, IntArray out) {
        int globalIdx = ctx.globalIdx;
        int mask = ctx.simdBallot(input.get(globalIdx) > 0);
        out.set(globalIdx, Integer.bitCount(mask));
    }

    /**
     * Votes inside a loop, with one result driving a branch and another feeding arithmetic. Over
     * the four steps every residue class is covered, so {@code simdAny} holds on each step and
     * {@code simdAll} never does, and each ballot selects exactly a quarter of the warp.
     *
     * <p>The {@code simdAll} branch is the discriminator: a lowering that returned the calling
     * lane's own predicate would fire it on the one step where the lane passes, adding 100.
     */
    private static void voteInLoopKernel(KernelContext ctx, IntArray out) {
        int globalIdx = ctx.globalIdx;
        int localIdx = ctx.localIdx;
        int acc = 0;
        for (int step = 0; step < 4; step++) {
            boolean predicate = (localIdx % 4) == step;
            if (ctx.simdAny(predicate)) {
                acc += 1;
            }
            if (ctx.simdAll(predicate)) {
                acc += 100;
            }
            acc += Integer.bitCount(ctx.simdBallot(predicate));
        }
        out.set(globalIdx, acc);
    }

    /**
     * Warp leader election over a compound predicate: the ballot argument is {@code &&} of a
     * data-dependent test and a lane test, so it has to be materialised as a single boolean rather
     * than folded per operand.
     *
     * <p>The leader is the lowest passing lane. {@code Integer.numberOfTrailingZeros} is not
     * registered as an intrinsic on this backend, so it is derived from the lowest set bit with
     * {@code bitCount}, which is.
     */
    private static void leaderElectionKernel(KernelContext ctx, IntArray input, IntArray out) {
        int globalIdx = ctx.globalIdx;
        int localIdx = ctx.localIdx;
        boolean predicate = (input.get(globalIdx) > 0) && ((localIdx & 1) == 0);
        int mask = ctx.simdBallot(predicate);
        int leader = -1;
        if (mask != 0) {
            int lowestSetBit = mask & (-mask);
            leader = Integer.bitCount(lowestSetBit - 1);
        }
        out.set(globalIdx, leader);
    }

    private static IntArray runKernel(String name, KernelTask task) throws TornadoExecutionPlanException {
        return runKernel(name, task, SIZE, WARP_SIZE);
    }

    private static IntArray runKernel(String name, KernelTask task, int size, int localSize) throws TornadoExecutionPlanException {
        IntArray out = new IntArray(size);
        out.init(-1);
        KernelContext context = new KernelContext();
        WorkerGrid worker = new WorkerGrid1D(size);
        worker.setLocalWork(localSize, 1, 1);
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

    private static IntArray runKernel(String name, InputKernelTask task, IntArray input) throws TornadoExecutionPlanException {
        IntArray out = new IntArray(input.getSize());
        out.init(-1);
        KernelContext context = new KernelContext();
        WorkerGrid worker = new WorkerGrid1D(input.getSize());
        worker.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler grid = new GridScheduler("warpVote." + name, worker);

        TaskGraph taskGraph = new TaskGraph("warpVote") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, out) //
                .task(name, task, context, input, out) //
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

    /** Same, for the kernels that vote on a value read from device memory. */
    private interface InputKernelTask extends uk.ac.manchester.tornado.api.common.TornadoFunctions.Task3<KernelContext, IntArray, IntArray> {
    }

    private void skipUnsupportedBackends() {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);
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

    /**
     * Four warps per block, voting differently from one another. Confirms the vote is warp-wide and
     * not block-wide, which a single-warp block cannot distinguish.
     */
    @Test
    public void testMultipleWarpsPerBlock() throws TornadoExecutionPlanException {
        skipUnsupportedBackends();
        final int blockSize = WARP_SIZE * WARPS;
        final int size = blockSize * 2;
        IntArray out = runKernel("multiWarpBlock", TestWarpVote::multiWarpBlockKernel, size, blockSize);
        for (int i = 0; i < size; i++) {
            int warpInBlock = (i % blockSize) / WARP_SIZE;
            // Odd warps: every lane passes, so any and all hold (1 * 2 + 1). Even warps: neither.
            int expected = (warpInBlock % 2 == 1) ? 3 : 0;
            assertEquals("lane " + i, expected, out.get(i));
        }
    }

    /** Ballot over values read from device memory, so the mask cannot be constant-folded. */
    @Test
    public void testDataDependentBallot() throws TornadoExecutionPlanException {
        skipUnsupportedBackends();
        IntArray input = new IntArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            input.set(i, (i % 5 == 0) ? 1 : 0);
        }

        IntArray out = runKernel("dataBallot", TestWarpVote::dataBallotKernel, input);

        for (int i = 0; i < SIZE; i++) {
            int warpStart = (i / WARP_SIZE) * WARP_SIZE;
            int expected = 0;
            for (int lane = 0; lane < WARP_SIZE; lane++) {
                if (input.get(warpStart + lane) > 0) {
                    expected++;
                }
            }
            assertEquals("lane " + i, expected, out.get(i));
        }
    }

    /** Votes issued from inside a loop, one of them driving a branch. */
    @Test
    public void testVoteInsideLoop() throws TornadoExecutionPlanException {
        skipUnsupportedBackends();
        IntArray out = runKernel("voteInLoop", TestWarpVote::voteInLoopKernel);
        // Per step: simdAny holds (+1), simdAll does not (+0), and the ballot selects a quarter of
        // the warp (+8). Four steps: 4 * (1 + 8) = 36.
        for (int i = 0; i < SIZE; i++) {
            assertEquals("lane " + i, 36, out.get(i));
        }
    }

    /** Leader election from a ballot over a compound, data-dependent predicate. */
    @Test
    public void testWarpLeaderElection() throws TornadoExecutionPlanException {
        skipUnsupportedBackends();
        IntArray input = new IntArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            input.set(i, (i % 7 == 0) ? 1 : 0);
        }

        IntArray out = runKernel("leaderElection", TestWarpVote::leaderElectionKernel, input);

        for (int i = 0; i < SIZE; i++) {
            int warpStart = (i / WARP_SIZE) * WARP_SIZE;
            int expected = -1;
            for (int lane = 0; lane < WARP_SIZE; lane++) {
                if (input.get(warpStart + lane) > 0 && (lane % 2 == 0)) {
                    expected = lane;
                    break;
                }
            }
            assertEquals("lane " + i, expected, out.get(i));
        }
    }
}
