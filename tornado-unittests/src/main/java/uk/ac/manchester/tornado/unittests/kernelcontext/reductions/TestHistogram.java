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
 * Global-memory histogram: every thread computes its data-dependent bucket and
 * {@code atomicAdd}s directly into a global {@link IntArray} -- many concurrently-contended
 * buckets across the whole grid, distinct from every other atomics test (one contended local slot
 * in {@code TestAtomicRmw}; a fixed thread-index-derived bucket in {@code TestGlobalAtomics}; this
 * one buckets by actual DATA value with 16 real bins).
 *
 * <p>
 * This is intentionally NOT the textbook two-level (per-block-local, then merged into global)
 * histogram. That version was tried first and hit a real TornadoVM CUDA-codegen bug:
 * {@code KernelContext.atomicAdd(int[] localArray, dynamicIndex, val)} silently drops the
 * data-dependent index and always operates on element 0 of the local array -- confirmed via
 * {@code --printKernel}, the generated CUDA computes the byte offset for the dynamic index but
 * then calls {@code atomicAdd((int*) adi_2, 1)} on the bare base pointer, never applying the
 * offset. Every existing local-array atomic example in the suite (`TestAtomicRmw`) only ever uses
 * a literal {@code 0} index, so this never surfaced before. The GLOBAL-array {@code atomicAdd}
 * overload does NOT have this bug (confirmed working in {@code TestGlobalAtomics}), so this test
 * is scoped to the confirmed-correct path rather than encoding the local-array bug as a passing
 * assertion.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.reductions.TestHistogram
 * </code>
 */
public class TestHistogram extends TornadoTestBase {

    private static final int NUM_BINS = 16;
    private static final int THREADS = 256;
    private static final int BLOCKS = 8;
    private static final int SIZE = THREADS * BLOCKS;

    private static void histogramKernel(KernelContext ctx, IntArray input, IntArray globalHist) {
        int bin = input.get(ctx.globalIdx) % NUM_BINS;
        ctx.atomicAdd(globalHist, bin, 1);
    }

    @Test
    public void testHistogramBinCounts() throws TornadoExecutionPlanException {
        Random r = new Random(9);
        IntArray input = new IntArray(SIZE);
        int[] expected = new int[NUM_BINS];
        for (int i = 0; i < SIZE; i++) {
            int value = r.nextInt(100000);
            input.set(i, value);
            expected[value % NUM_BINS]++;
        }

        IntArray globalHist = new IntArray(NUM_BINS);
        globalHist.init(0);
        KernelContext context = new KernelContext();

        WorkerGrid worker = new WorkerGrid1D(SIZE);
        worker.setLocalWork(THREADS, 1, 1);
        GridScheduler grid = new GridScheduler("s0.t0", worker);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, globalHist) //
                .task("t0", TestHistogram::histogramKernel, context, input, globalHist) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, globalHist);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(grid).execute();
        }

        int totalCounted = 0;
        for (int b = 0; b < NUM_BINS; b++) {
            assertEquals("bin " + b, expected[b], globalHist.get(b));
            totalCounted += globalHist.get(b);
        }
        assertEquals(SIZE, totalCounted);
    }

}
