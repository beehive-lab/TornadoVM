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
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.WorkerGrid3D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Combines a 2D/3D {@link WorkerGrid} with local-memory allocation, a barrier, and a read-modify-write
 * atomic (as {@code TestAtomicRmw} does over {@code WorkerGrid1D}) -- every prior local-memory/barrier/
 * atomic test in this package only used a 1D grid, so this exercises the interaction between multi-
 * dimensional block/thread indexing ({@code groupIdx}/{@code groupIdy}/{@code groupIdz},
 * {@code localIdx}/{@code localIdy}/{@code localIdz}) and the same local-array + atomic machinery.
 *
 * <p>How to run:
 *
 * <pre>
 * tornado-test --printKernel -V uk.ac.manchester.tornado.unittests.kernelcontext.api.TestAtomicRmw2D
 * </pre>
 */
public class TestAtomicRmw2D extends TornadoTestBase {

    private static final int WIDTH = 64;
    private static final int HEIGHT = 64;
    private static final int LOCAL_WIDTH = 16;
    private static final int LOCAL_HEIGHT = 16;
    private static final int BLOCKS_X = WIDTH / LOCAL_WIDTH;
    private static final int BLOCKS_Y = HEIGHT / LOCAL_HEIGHT;

    private static final int DEPTH = 4;
    private static final int LOCAL_DEPTH = 4;
    private static final int BLOCKS_Z = DEPTH / LOCAL_DEPTH;

    /** Per-2D-block maximum, computed with one contended shared accumulator per block. */
    private static void blockMax2DKernel(KernelContext ctx, IntArray in, IntArray out) {
        int[] shared = ctx.allocateIntLocalArray(1);
        if (ctx.localIdx == 0 && ctx.localIdy == 0) {
            shared[0] = Integer.MIN_VALUE;
        }
        ctx.localBarrier();
        int linear = ctx.globalIdy * WIDTH + ctx.globalIdx;
        ctx.atomicMax(shared, 0, in.get(linear));
        ctx.localBarrier();
        if (ctx.localIdx == 0 && ctx.localIdy == 0) {
            int blockIndex = ctx.groupIdy * BLOCKS_X + ctx.groupIdx;
            out.set(blockIndex, shared[0]);
        }
    }

    /** Same shape, extended to 3D: per-3D-block maximum. */
    private static void blockMax3DKernel(KernelContext ctx, IntArray in, IntArray out) {
        int[] shared = ctx.allocateIntLocalArray(1);
        if (ctx.localIdx == 0 && ctx.localIdy == 0 && ctx.localIdz == 0) {
            shared[0] = Integer.MIN_VALUE;
        }
        ctx.localBarrier();
        int linear = (ctx.globalIdz * HEIGHT + ctx.globalIdy) * WIDTH + ctx.globalIdx;
        ctx.atomicMax(shared, 0, in.get(linear));
        ctx.localBarrier();
        if (ctx.localIdx == 0 && ctx.localIdy == 0 && ctx.localIdz == 0) {
            int blockIndex = (ctx.groupIdz * BLOCKS_Y + ctx.groupIdy) * BLOCKS_X + ctx.groupIdx;
            out.set(blockIndex, shared[0]);
        }
    }

    private void skipUnsupportedBackends() {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);
    }

    @Test
    public void testBlockMax2D() throws TornadoExecutionPlanException {
        skipUnsupportedBackends();

        IntArray in = new IntArray(WIDTH * HEIGHT);
        for (int i = 0; i < in.getSize(); i++) {
            // Non-monotonic, so the per-block maxima aren't simply the first/last element.
            in.set(i, (i * 7919) % 10007);
        }
        IntArray out = new IntArray(BLOCKS_X * BLOCKS_Y);

        KernelContext context = new KernelContext();
        WorkerGrid worker = new WorkerGrid2D(WIDTH, HEIGHT);
        worker.setLocalWork(LOCAL_WIDTH, LOCAL_HEIGHT, 1);
        GridScheduler grid = new GridScheduler("s0.t0", worker);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("t0", TestAtomicRmw2D::blockMax2DKernel, context, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(grid).execute();
        }

        for (int by = 0; by < BLOCKS_Y; by++) {
            for (int bx = 0; bx < BLOCKS_X; bx++) {
                int expected = Integer.MIN_VALUE;
                for (int ly = 0; ly < LOCAL_HEIGHT; ly++) {
                    for (int lx = 0; lx < LOCAL_WIDTH; lx++) {
                        int gx = bx * LOCAL_WIDTH + lx;
                        int gy = by * LOCAL_HEIGHT + ly;
                        expected = Math.max(expected, in.get(gy * WIDTH + gx));
                    }
                }
                assertEquals(expected, out.get(by * BLOCKS_X + bx));
            }
        }
    }

    @Test
    public void testBlockMax3D() throws TornadoExecutionPlanException {
        skipUnsupportedBackends();

        IntArray in = new IntArray(WIDTH * HEIGHT * DEPTH);
        for (int i = 0; i < in.getSize(); i++) {
            in.set(i, (i * 7919) % 10007);
        }
        IntArray out = new IntArray(BLOCKS_X * BLOCKS_Y * BLOCKS_Z);

        KernelContext context = new KernelContext();
        WorkerGrid worker = new WorkerGrid3D(WIDTH, HEIGHT, DEPTH);
        worker.setLocalWork(LOCAL_WIDTH, LOCAL_HEIGHT, LOCAL_DEPTH);
        GridScheduler grid = new GridScheduler("s0.t0", worker);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("t0", TestAtomicRmw2D::blockMax3DKernel, context, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(grid).execute();
        }

        for (int bz = 0; bz < BLOCKS_Z; bz++) {
            for (int by = 0; by < BLOCKS_Y; by++) {
                for (int bx = 0; bx < BLOCKS_X; bx++) {
                    int expected = Integer.MIN_VALUE;
                    for (int lz = 0; lz < LOCAL_DEPTH; lz++) {
                        for (int ly = 0; ly < LOCAL_HEIGHT; ly++) {
                            for (int lx = 0; lx < LOCAL_WIDTH; lx++) {
                                int gx = bx * LOCAL_WIDTH + lx;
                                int gy = by * LOCAL_HEIGHT + ly;
                                int gz = bz * LOCAL_DEPTH + lz;
                                expected = Math.max(expected, in.get((gz * HEIGHT + gy) * WIDTH + gx));
                            }
                        }
                    }
                    int blockIndex = (bz * BLOCKS_Y + by) * BLOCKS_X + bx;
                    assertEquals(expected, out.get(blockIndex));
                }
            }
        }
    }

}
