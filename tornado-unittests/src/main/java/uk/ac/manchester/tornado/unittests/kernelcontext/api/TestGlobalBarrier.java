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
 * {@code KernelContext.globalBarrier()} (a memory fence with {@code CLK_GLOBAL_MEM_FENCE}
 * semantics on OpenCL / {@code barrier.sync} on CUDA) had zero coverage anywhere in the suite --
 * every local-memory/barrier/atomic test in {@code kernelcontext/} uses {@code localBarrier()}
 * only. This mirrors {@code TestAtomicRmw#blockMaxKernel} but synchronizes with
 * {@code globalBarrier()} instead, to confirm it still correctly orders the shared-array
 * load/atomic/read-back sequence.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.api.TestGlobalBarrier
 * </code>
 */
public class TestGlobalBarrier extends TornadoTestBase {

    private static final int THREADS = 256;
    private static final int BLOCKS = 4;
    private static final int SIZE = THREADS * BLOCKS;

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

}
