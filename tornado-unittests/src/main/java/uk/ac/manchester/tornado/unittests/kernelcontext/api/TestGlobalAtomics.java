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
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * {@code KernelContext.atomicAdd} on a global (off-heap) {@link IntArray}/{@link FloatArray}
 * spanning MULTIPLE thread blocks -- distinct from {@code TestAtomicRmw}/{@code TestAtomicRmw2D},
 * whose atomics all target one shared-memory slot confined to a SINGLE work-group. Note only
 * {@code atomicAdd} has a global-array overload in {@code KernelContext} today (confirmed by
 * reading the full API surface) -- {@code atomicCAS}/{@code atomicExchange}/{@code atomicMin}/
 * {@code atomicMax} only accept the local-memory {@code int[]} form, so this test is scoped to
 * what's actually reachable rather than assuming parity with the local-memory RMW family.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.api.TestGlobalAtomics
 * </code>
 */
public class TestGlobalAtomics extends TornadoTestBase {

    private static final int THREADS = 256;
    private static final int BLOCKS = 8;
    private static final int TOTAL = THREADS * BLOCKS;
    private static final int NUM_BUCKETS = 4;

    private static void bucketCountKernel(KernelContext ctx, IntArray buckets) {
        int bucket = ctx.globalIdx % NUM_BUCKETS;
        ctx.atomicAdd(buckets, bucket, 1);
    }

    private static void globalFloatSumKernel(KernelContext ctx, FloatArray accumulator) {
        ctx.atomicAdd(accumulator, 0, 1.5f);
    }

    @Test
    public void testGlobalAtomicAddBucketCounts() throws TornadoExecutionPlanException {
        IntArray buckets = new IntArray(NUM_BUCKETS);
        buckets.init(0);
        KernelContext context = new KernelContext();

        WorkerGrid worker = new WorkerGrid1D(TOTAL);
        worker.setLocalWork(THREADS, 1, 1);
        GridScheduler grid = new GridScheduler("s0.t0", worker);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, buckets) //
                .task("t0", TestGlobalAtomics::bucketCountKernel, context, buckets) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, buckets);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(grid).execute();
        }

        int expectedPerBucket = TOTAL / NUM_BUCKETS;
        for (int b = 0; b < NUM_BUCKETS; b++) {
            assertEquals("bucket " + b, expectedPerBucket, buckets.get(b));
        }
    }

    @Test
    public void testGlobalAtomicAddFloatAcrossGrid() throws TornadoExecutionPlanException {
        // OpenCL's atom_add has no floating-point overload, so the OpenCL backend rejects this at
        // sketch time with "In OpenCL, the atom_add function does not support floating point
        // operations" (see OCLGraphBuilderPlugins#registerUnsupportedAtomicAddPlugin). The
        // int-based sibling test above runs on every backend. CUDA has a native
        // atomicAdd(float*, float); Metal emulates it with a CAS-loop helper.
        assertNotBackend(TornadoVMBackendType.OPENCL, "OpenCL's atom_add has no floating-point overload");

        FloatArray accumulator = new FloatArray(1);
        accumulator.init(0.0f);
        KernelContext context = new KernelContext();

        WorkerGrid worker = new WorkerGrid1D(TOTAL);
        worker.setLocalWork(THREADS, 1, 1);
        GridScheduler grid = new GridScheduler("s0.t0", worker);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, accumulator) //
                .task("t0", TestGlobalAtomics::globalFloatSumKernel, context, accumulator) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, accumulator);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(grid).execute();
        }

        assertEquals(TOTAL * 1.5f, accumulator.get(0), 1.0f);
    }

}
