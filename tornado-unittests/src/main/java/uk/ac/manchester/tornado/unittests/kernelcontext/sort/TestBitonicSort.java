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
package uk.ac.manchester.tornado.unittests.kernelcontext.sort;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
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
 * Bitonic sort entirely in local (shared) memory, within a single work-group -- one thread per
 * element, the standard XOR-indexed compare-exchange network: for each stage {@code k} and pass
 * {@code j}, thread {@code tid} pairs with {@code tid ^ j} and swaps according to the ascending/
 * descending direction implied by bit {@code k} of {@code tid}. No sort of any kind existed
 * anywhere in {@code kernelcontext/} before this -- it's a new local-memory + barrier shape
 * distinct from the existing tiled-matmul and tree-reduction tests (many small compare-exchange
 * barriers instead of one accumulation loop).
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.sort.TestBitonicSort
 * </code>
 */
public class TestBitonicSort extends TornadoTestBase {

    private static final int SIZE = 256; // power of 2, fits one work-group

    private static void bitonicSort(KernelContext ctx, IntArray data) {
        int[] shared = ctx.allocateIntLocalArray(SIZE);
        int tid = ctx.localIdx;
        shared[tid] = data.get(ctx.globalIdx);
        ctx.localBarrier();

        for (int k = 2; k <= SIZE; k <<= 1) {
            for (int j = k >> 1; j > 0; j >>= 1) {
                int partner = tid ^ j;
                if (partner > tid) {
                    boolean ascending = (tid & k) == 0;
                    int a = shared[tid];
                    int b = shared[partner];
                    if (ascending == (a > b)) {
                        shared[tid] = b;
                        shared[partner] = a;
                    }
                }
                ctx.localBarrier();
            }
        }

        data.set(ctx.globalIdx, shared[tid]);
    }

    @Test
    public void testBitonicSortSingleBlock() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);

        Random r = new Random(42);
        IntArray data = new IntArray(SIZE);
        int[] expected = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            int value = r.nextInt(100000);
            data.set(i, value);
            expected[i] = value;
        }
        Arrays.sort(expected);

        KernelContext context = new KernelContext();
        WorkerGrid worker = new WorkerGrid1D(SIZE);
        worker.setLocalWork(SIZE, 1, 1);
        GridScheduler grid = new GridScheduler("s0.t0", worker);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, data) //
                .task("t0", TestBitonicSort::bitonicSort, context, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(grid).execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals("mismatch at index " + i, expected[i], data.get(i));
        }
    }

    @Test
    public void testBitonicSortAlreadySortedAndReversed() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);

        IntArray data = new IntArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            data.set(i, SIZE - i); // strictly descending: worst case for a sort
        }

        KernelContext context = new KernelContext();
        WorkerGrid worker = new WorkerGrid1D(SIZE);
        worker.setLocalWork(SIZE, 1, 1);
        GridScheduler grid = new GridScheduler("s0.t0", worker);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, data) //
                .task("t0", TestBitonicSort::bitonicSort, context, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(grid).execute();
        }

        for (int i = 0; i < SIZE - 1; i++) {
            assertTrue("not sorted at index " + i, data.get(i) <= data.get(i + 1));
        }
        assertEquals(1, data.get(0));
        assertEquals(SIZE, data.get(SIZE - 1));
    }

}
