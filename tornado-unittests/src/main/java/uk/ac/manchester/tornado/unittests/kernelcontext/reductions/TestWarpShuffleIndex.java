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
package uk.ac.manchester.tornado.unittests.kernelcontext.reductions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * {@code KernelContext.simdShuffle}: each lane reads a value from any lane of its SIMD group,
 * lower or higher than its own.
 *
 * <p>How to run:
 *
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.reductions.TestWarpShuffleIndex
 * </code>
 */
public class TestWarpShuffleIndex extends TornadoTestBase {

    private static final int WARP = 32;
    private static final int SIZE = 256;

    /** Lane {@code L} reads lane {@code 31 - L}: every lane reads across the warp. */
    public static void reverseInt(KernelContext ctx, IntArray in, IntArray out) {
        int i = ctx.globalIdx;
        int lane = ctx.localIdx & 31;
        out.set(i, ctx.simdShuffle(in.get(i), 31 - lane));
    }

    /** {@link #reverseInt} for floats. */
    public static void reverseFloat(KernelContext ctx, FloatArray in, FloatArray out) {
        int i = ctx.globalIdx;
        int lane = ctx.localIdx & 31;
        out.set(i, ctx.simdShuffle(in.get(i), 31 - lane));
    }

    /**
     * Groups of four lanes, each holding two words: lane {@code t} of a group ends up with words
     * {@code t} and {@code 4 + t} of the group's eight. Two shuffles, each source lane offering its
     * low or high word by which half of the group it is in. This is the 8-byte-load redistribution
     * of a quantized MMA kernel's B operand.
     */
    public static void regroup(KernelContext ctx, IntArray in, IntArray out) {
        int i = ctx.globalIdx;
        int lane = ctx.localIdx & 31;
        int t = lane & 3;
        int base = lane & ~3;
        int lo = in.get(2 * i);
        int hi = in.get(2 * i + 1);
        int first = ctx.simdShuffle(t < 2 ? lo : hi, base + (t >> 1) + 2 * (t & 1));
        int second = ctx.simdShuffle(t < 2 ? hi : lo, base + (t >> 1) + 2 * (1 - (t & 1)));
        out.set(2 * i, (t & 1) == 0 ? first : second);
        out.set(2 * i + 1, (t & 1) == 0 ? second : first);
    }

    private static GridScheduler grid(String name) {
        WorkerGrid worker = new WorkerGrid1D(SIZE);
        worker.setLocalWork(128, 1, 1);
        return new GridScheduler(name, worker);
    }

    @Test
    public void testReverseInt() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        IntArray in = new IntArray(SIZE);
        IntArray out = new IntArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            in.set(i, 1000 * i + 7);
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("t0", TestWarpShuffleIndex::reverseInt, new KernelContext(), in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("s0.t0")).execute();
        }
        for (int i = 0; i < SIZE; i++) {
            int source = (i & ~31) + 31 - (i & 31);
            assertEquals(1000 * source + 7, out.get(i), "lane " + i);
        }
    }

    @Test
    public void testReverseFloat() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        FloatArray in = new FloatArray(SIZE);
        FloatArray out = new FloatArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            in.set(i, 0.5f * i - 3.0f);
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("t0", TestWarpShuffleIndex::reverseFloat, new KernelContext(), in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("s0.t0")).execute();
        }
        for (int i = 0; i < SIZE; i++) {
            int source = (i & ~31) + 31 - (i & 31);
            assertEquals(0.5f * source - 3.0f, out.get(i), 0.0f, "lane " + i);
        }
    }

    @Test
    public void testRegroupWordsInFourLaneGroups() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        IntArray in = new IntArray(2 * SIZE);
        IntArray out = new IntArray(2 * SIZE);
        for (int i = 0; i < 2 * SIZE; i++) {
            in.set(i, 0x10000 * i + 0x55);
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("t0", TestWarpShuffleIndex::regroup, new KernelContext(), in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("s0.t0")).execute();
        }
        for (int i = 0; i < SIZE; i++) {
            // Words of the group of thread i, in order: two per lane.
            int group = 2 * (i & ~3);
            int t = i & 3;
            assertEquals(in.get(group + t), out.get(2 * i), "first word, thread " + i);
            assertEquals(in.get(group + 4 + t), out.get(2 * i + 1), "second word, thread " + i);
        }
    }
}
