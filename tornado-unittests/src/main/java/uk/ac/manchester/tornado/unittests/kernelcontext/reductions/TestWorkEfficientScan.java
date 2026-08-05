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
 * Work-efficient (Blelloch) exclusive prefix-sum scan entirely in local (shared) memory, one
 * thread per pair of elements: an up-sweep (reduce) pass builds partial sums in a balanced binary
 * tree, then a down-sweep pass distributes them back out. Distinct from every reduction test in
 * the suite: a reduction collapses N values to 1; a scan keeps all N partial sums.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.reductions.TestWorkEfficientScan
 * </code>
 */
public class TestWorkEfficientScan extends TornadoTestBase {

    private static final int SIZE = 256; // power of 2, fits one work-group

    private static void exclusiveScan(KernelContext ctx, IntArray data) {
        int[] shared = ctx.allocateIntLocalArray(SIZE);
        int tid = ctx.localIdx;
        shared[tid] = data.get(ctx.globalIdx);
        ctx.localBarrier();

        int offset = 1;
        for (int d = SIZE >> 1; d > 0; d >>= 1) {
            ctx.localBarrier();
            if (tid < d) {
                int ai = offset * (2 * tid + 1) - 1;
                int bi = offset * (2 * tid + 2) - 1;
                shared[bi] += shared[ai];
            }
            offset *= 2;
        }

        if (tid == 0) {
            shared[SIZE - 1] = 0;
        }

        for (int d = 1; d < SIZE; d *= 2) {
            offset >>= 1;
            ctx.localBarrier();
            if (tid < d) {
                int ai = offset * (2 * tid + 1) - 1;
                int bi = offset * (2 * tid + 2) - 1;
                int temp = shared[ai];
                shared[ai] = shared[bi];
                shared[bi] += temp;
            }
        }
        ctx.localBarrier();

        data.set(ctx.globalIdx, shared[tid]);
    }

    @Test
    public void testExclusivePrefixSum() throws TornadoExecutionPlanException {
        Random r = new Random(21);
        IntArray data = new IntArray(SIZE);
        int[] input = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            int value = r.nextInt(100);
            data.set(i, value);
            input[i] = value;
        }

        int[] expected = new int[SIZE];
        int running = 0;
        for (int i = 0; i < SIZE; i++) {
            expected[i] = running;
            running += input[i];
        }

        KernelContext context = new KernelContext();
        WorkerGrid worker = new WorkerGrid1D(SIZE);
        worker.setLocalWork(SIZE, 1, 1);
        GridScheduler grid = new GridScheduler("s0.t0", worker);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, data) //
                .task("t0", TestWorkEfficientScan::exclusiveScan, context, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(grid).execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals("mismatch at index " + i, expected[i], data.get(i));
        }
    }

}
