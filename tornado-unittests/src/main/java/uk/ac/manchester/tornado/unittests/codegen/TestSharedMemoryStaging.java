/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2025, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.unittests.codegen;

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
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Correctness tests for kernels that stage global memory into shared memory.
 *
 * <p>
 * A code generator is allowed to reorder a staging sequence — the loads read global
 * memory and the stores write shared memory, so they cannot alias — and the CUDA backend
 * does exactly that to keep several loads in flight (see
 * {@code CUDAGlobalLoadBatching}). These kernels exist to pin down the cases where that
 * reordering must NOT happen, and they are written so that a wrong schedule changes the
 * answer rather than just the speed:
 *
 * <ul>
 * <li>a value staged before a barrier must be visible after it,</li>
 * <li>a value read back from shared memory must be the one just written,</li>
 * <li>two writes to the same shared slot must land in program order,</li>
 * <li>a global store in the middle of a staging sequence must keep its position,</li>
 * <li>an address that depends on a loaded value must still be computed from it.</li>
 * </ul>
 *
 * <p>
 * Everything is integer arithmetic with exact expected values, so a failure is a real
 * scheduling bug and never floating-point drift.
 *
 * <p>
 * These are ordinary device tests: they pass on any backend. Run the CUDA backend twice,
 * with {@code -Dtornado.cuda.batchGlobalLoads} true and false, to A/B the reordering.
 *
 * <p>
 * How to run?
 *
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.codegen.TestSharedMemoryStaging
 * </code>
 */
public class TestSharedMemoryStaging extends TornadoTestBase {

    private static final int GROUP = 64;
    private static final int GROUPS = 16;
    private static final int SIZE = GROUP * GROUPS;

    /**
     * One global load, one shared store, a barrier, then a read of a slot another thread
     * wrote. A store sunk past the barrier would read an unwritten slot.
     */
    public static void stageAndReverse(KernelContext context, IntArray in, IntArray out) {
        int gid = context.globalIdx;
        int lid = context.localIdx;
        int[] tile = context.allocateIntLocalArray(GROUP);
        tile[lid] = in.get(gid);
        context.localBarrier();
        out.set(gid, tile[GROUP - 1 - lid]);
    }

    /**
     * Four independent staging pairs in one straight-line run: the shape the batching
     * targets. The sum afterwards depends on all four having landed.
     */
    public static void stageFourPerThread(KernelContext context, IntArray in, IntArray out) {
        int gid = context.globalIdx;
        int lid = context.localIdx;
        int base = (gid - lid) * 4;
        int[] tile = context.allocateIntLocalArray(GROUP * 4);
        tile[lid] = in.get(base + lid);
        tile[lid + GROUP] = in.get(base + lid + GROUP);
        tile[lid + 2 * GROUP] = in.get(base + lid + 2 * GROUP);
        tile[lid + 3 * GROUP] = in.get(base + lid + 3 * GROUP);
        context.localBarrier();
        int sum = 0;
        for (int i = 0; i < 4; i++) {
            sum += tile[lid * 4 + i];
        }
        out.set(gid, sum);
    }

    /**
     * A shared read sits between two staging pairs, with no barrier. The value it reads
     * was written by this same thread one statement earlier, so the store cannot move
     * below it.
     */
    public static void readBackBetweenStages(KernelContext context, IntArray in, IntArray out) {
        int gid = context.globalIdx;
        int lid = context.localIdx;
        int[] tile = context.allocateIntLocalArray(GROUP * 2);
        tile[lid] = in.get(gid);
        int justWritten = tile[lid];
        tile[lid + GROUP] = in.get(gid) * 2 + justWritten;
        context.localBarrier();
        out.set(gid, tile[lid] * 100 + tile[lid + GROUP]);
    }

    /**
     * Two writes to the same shared slot, with a global load between them. The second must
     * win, so the stores have to keep their order even when the loads move above them.
     */
    public static void overwriteSameSlot(KernelContext context, IntArray in, IntArray out, int size) {
        int gid = context.globalIdx;
        int lid = context.localIdx;
        int[] tile = context.allocateIntLocalArray(GROUP);
        tile[lid] = in.get(gid);
        tile[lid] = in.get((gid + 1) % size) * 7;
        context.localBarrier();
        out.set(gid, tile[lid]);
    }

    /**
     * A global store in the middle of a staging sequence. It is not sinkable, and treating
     * it as one would let a later shared store overtake it.
     */
    public static void globalStoreBetweenStages(KernelContext context, IntArray in, IntArray scratch, IntArray out) {
        int gid = context.globalIdx;
        int lid = context.localIdx;
        int[] tile = context.allocateIntLocalArray(GROUP * 2);
        tile[lid] = in.get(gid);
        scratch.set(gid, in.get(gid) * 3);
        tile[lid + GROUP] = scratch.get(gid) + 1;
        context.localBarrier();
        out.set(gid, tile[lid] + tile[lid + GROUP]);
    }

    /**
     * The address of each staged element comes from a loaded index, so the loads cannot be
     * hoisted above the arithmetic that produces their addresses.
     */
    public static void gatherStaging(KernelContext context, IntArray index, IntArray in, IntArray out, int size) {
        int gid = context.globalIdx;
        int lid = context.localIdx;
        int[] tile = context.allocateIntLocalArray(GROUP * 2);
        int i0 = index.get(gid);
        tile[lid] = in.get(i0);
        int i1 = index.get((gid + 3) % size);
        tile[lid + GROUP] = in.get(i1);
        context.localBarrier();
        out.set(gid, tile[lid] * 1000 + tile[lid + GROUP]);
    }

    /**
     * The tile-loop shape a blocked matrix multiply produces: stage, barrier, consume,
     * barrier, repeat. Every iteration re-stages into the same shared arrays, so a store
     * that escaped its iteration would corrupt the next one.
     */
    public static void tiledLoop(KernelContext context, IntArray a, IntArray b, IntArray out, int tiles) {
        int gid = context.globalIdx;
        int lid = context.localIdx;
        int[] ta = context.allocateIntLocalArray(GROUP);
        int[] tb = context.allocateIntLocalArray(GROUP);
        int acc = 0;
        for (int t = 0; t < tiles; t++) {
            ta[lid] = a.get(t * GROUP + lid);
            tb[lid] = b.get(t * GROUP + lid);
            context.localBarrier();
            acc += ta[GROUP - 1 - lid] * tb[lid];
            context.localBarrier();
        }
        out.set(gid, acc);
    }

    @Test
    public void testStageAndReverse() throws TornadoExecutionPlanException {
        IntArray in = randomInts(SIZE, 1);
        IntArray out = new IntArray(SIZE);
        run("stageAndReverse", g -> g.task("t0", TestSharedMemoryStaging::stageAndReverse, new KernelContext(), in, out).transferToHost(DataTransferMode.EVERY_EXECUTION, out), in);

        for (int g = 0; g < GROUPS; g++) {
            for (int l = 0; l < GROUP; l++) {
                assertEquals(in.get(g * GROUP + (GROUP - 1 - l)), out.get(g * GROUP + l));
            }
        }
    }

    @Test
    public void testStageFourPerThread() throws TornadoExecutionPlanException {
        IntArray in = randomInts(SIZE * 4, 2);
        IntArray out = new IntArray(SIZE);
        run("stageFourPerThread", g -> g.task("t0", TestSharedMemoryStaging::stageFourPerThread, new KernelContext(), in, out).transferToHost(DataTransferMode.EVERY_EXECUTION, out), in);

        for (int grp = 0; grp < GROUPS; grp++) {
            int base = grp * GROUP * 4;
            int[] tile = new int[GROUP * 4];
            for (int l = 0; l < GROUP; l++) {
                for (int q = 0; q < 4; q++) {
                    tile[l + q * GROUP] = in.get(base + l + q * GROUP);
                }
            }
            for (int l = 0; l < GROUP; l++) {
                int expected = 0;
                for (int i = 0; i < 4; i++) {
                    expected += tile[l * 4 + i];
                }
                assertEquals(expected, out.get(grp * GROUP + l));
            }
        }
    }

    @Test
    public void testReadBackBetweenStages() throws TornadoExecutionPlanException {
        IntArray in = randomInts(SIZE, 3);
        IntArray out = new IntArray(SIZE);
        run("readBackBetweenStages", g -> g.task("t0", TestSharedMemoryStaging::readBackBetweenStages, new KernelContext(), in, out).transferToHost(DataTransferMode.EVERY_EXECUTION, out), in);

        for (int i = 0; i < SIZE; i++) {
            int v = in.get(i);
            assertEquals(v * 100 + (v * 2 + v), out.get(i));
        }
    }

    @Test
    public void testOverwriteSameSlot() throws TornadoExecutionPlanException {
        IntArray in = randomInts(SIZE, 4);
        IntArray out = new IntArray(SIZE);
        run("overwriteSameSlot", g -> g.task("t0", TestSharedMemoryStaging::overwriteSameSlot, new KernelContext(), in, out, SIZE).transferToHost(DataTransferMode.EVERY_EXECUTION, out), in);

        for (int i = 0; i < SIZE; i++) {
            assertEquals(in.get((i + 1) % SIZE) * 7, out.get(i));
        }
    }

    @Test
    public void testGlobalStoreBetweenStages() throws TornadoExecutionPlanException {
        IntArray in = randomInts(SIZE, 5);
        IntArray scratch = new IntArray(SIZE);
        IntArray out = new IntArray(SIZE);
        run("globalStoreBetweenStages",
                g -> g.task("t0", TestSharedMemoryStaging::globalStoreBetweenStages, new KernelContext(), in, scratch, out).transferToHost(DataTransferMode.EVERY_EXECUTION, out, scratch), in);

        for (int i = 0; i < SIZE; i++) {
            int v = in.get(i);
            assertEquals(v * 3, scratch.get(i));
            assertEquals(v + (v * 3 + 1), out.get(i));
        }
    }

    @Test
    public void testGatherStaging() throws TornadoExecutionPlanException {
        IntArray in = randomInts(SIZE, 6);
        IntArray index = new IntArray(SIZE);
        Random r = new Random(7);
        for (int i = 0; i < SIZE; i++) {
            index.set(i, r.nextInt(SIZE));
        }
        IntArray out = new IntArray(SIZE);
        run("gatherStaging", g -> g.task("t0", TestSharedMemoryStaging::gatherStaging, new KernelContext(), index, in, out, SIZE).transferToHost(DataTransferMode.EVERY_EXECUTION, out), index, in);

        for (int i = 0; i < SIZE; i++) {
            assertEquals(in.get(index.get(i)) * 1000 + in.get(index.get((i + 3) % SIZE)), out.get(i));
        }
    }

    @Test
    public void testTiledLoop() throws TornadoExecutionPlanException {
        final int tiles = 8;
        IntArray a = randomInts(tiles * GROUP, 8);
        IntArray b = randomInts(tiles * GROUP, 9);
        IntArray out = new IntArray(SIZE);
        run("tiledLoop", g -> g.task("t0", TestSharedMemoryStaging::tiledLoop, new KernelContext(), a, b, out, tiles).transferToHost(DataTransferMode.EVERY_EXECUTION, out), a, b);

        for (int i = 0; i < SIZE; i++) {
            int l = i % GROUP;
            int expected = 0;
            for (int t = 0; t < tiles; t++) {
                expected += a.get(t * GROUP + (GROUP - 1 - l)) * b.get(t * GROUP + l);
            }
            assertEquals(expected, out.get(i));
        }
    }

    private interface TaskBuilder {
        TaskGraph build(TaskGraph graph);
    }

    /**
     * Runs one kernel on a fixed 1D grid of {@link #GROUPS} groups of {@link #GROUP} threads.
     * {@code name} identifies the task graph, so a failure in the scheduler or the profiler
     * names the kernel under test rather than a generic "s0".
     */
    private void run(String name, TaskBuilder builder, IntArray... inputs) throws TornadoExecutionPlanException {
        WorkerGrid worker = new WorkerGrid1D(SIZE);
        worker.setLocalWork(GROUP, 1, 1);
        GridScheduler grid = new GridScheduler(name + ".t0", worker);

        TaskGraph graph = builder.build(new TaskGraph(name).transferToDevice(DataTransferMode.EVERY_EXECUTION, (Object[]) inputs));
        ImmutableTaskGraph snapshot = graph.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(snapshot)) {
            plan.withGridScheduler(grid).execute();
        }
    }

    /** Small positive values, so every expected value stays well inside int range. */
    private static IntArray randomInts(int size, long seed) {
        Random r = new Random(seed);
        IntArray a = new IntArray(size);
        for (int i = 0; i < size; i++) {
            a.set(i, 1 + r.nextInt(50));
        }
        return a;
    }
}
