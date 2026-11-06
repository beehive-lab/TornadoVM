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
package uk.ac.manchester.tornado.unittests.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * An execution plan of several task-graphs replayed as CUDA graphs waits for the device once, at
 * the end of {@code execute()}, instead of after every graph. The graphs must still see each other's
 * results, and {@code execute()} must still return only once the device has finished: the host
 * rewrites the inputs right after it returns.
 *
 * <p>
 * How to run?
 * </p>
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestCudaGraphMultiGraphPlan
 * </code>
 * </p>
 */
public class TestCudaGraphMultiGraphPlan extends TornadoTestBase {

    private static final int SIZE = 256 * 1024;

    private static final int LOCAL = 256;

    private static final int ITERATIONS = 64;

    private static void scale(KernelContext context, FloatArray input, FloatArray output, float factor) {
        int i = context.globalIdx;
        if (i < input.getSize()) {
            output.set(i, input.get(i) * factor);
        }
    }

    private static void addOne(KernelContext context, FloatArray input, FloatArray output) {
        int i = context.globalIdx;
        if (i < input.getSize()) {
            output.set(i, input.get(i) + 1.0f);
        }
    }

    private static void accumulate(KernelContext context, FloatArray input, FloatArray sum) {
        int i = context.globalIdx;
        if (i < input.getSize()) {
            sum.set(i, sum.get(i) + input.get(i));
        }
    }

    private static WorkerGrid grid() {
        WorkerGrid grid = new WorkerGrid1D(SIZE);
        grid.setLocalWork(LOCAL, 1, 1);
        return grid;
    }

    private static void assertAll(FloatArray array, float expected) {
        for (int i = 0; i < SIZE; i += 1021) {
            assertEquals(expected, array.get(i), 0.0f, "element " + i);
        }
    }

    /** Three graphs chained through device buffers, the last one copying its result to the host. */
    @Test
    public void testChainedGraphs() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);

        FloatArray input = new FloatArray(SIZE);
        FloatArray first = new FloatArray(SIZE);
        FloatArray second = new FloatArray(SIZE);
        FloatArray output = new FloatArray(SIZE);

        TaskGraph g0 = new TaskGraph("g0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("scale", TestCudaGraphMultiGraphPlan::scale, new KernelContext(), input, first, 2.0f) //
                .persistOnDevice(first);
        TaskGraph g1 = new TaskGraph("g1") //
                .consumeFromDevice("g0", first) //
                .task("addOne", TestCudaGraphMultiGraphPlan::addOne, new KernelContext(), first, second) //
                .persistOnDevice(second);
        TaskGraph g2 = new TaskGraph("g2") //
                .consumeFromDevice("g1", second) //
                .task("scale", TestCudaGraphMultiGraphPlan::scale, new KernelContext(), second, output, 3.0f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        GridScheduler scheduler = new GridScheduler();
        scheduler.addWorkerGrid("g0.scale", grid());
        scheduler.addWorkerGrid("g1.addOne", grid());
        scheduler.addWorkerGrid("g2.scale", grid());

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g0.snapshot(), g1.snapshot(), g2.snapshot())) {
            plan.withCUDAGraph().withGridScheduler(scheduler);
            for (int it = 0; it < ITERATIONS; it++) {
                float value = it % 7;
                input.init(value);
                output.init(-1.0f);
                plan.execute();
                assertAll(output, (value * 2.0f + 1.0f) * 3.0f);
            }
        }
    }

    /**
     * No graph copies to the host while the plan runs, so nothing but the plan's closing wait keeps
     * the host from rewriting an input before a queued graph has read it: the device-side sum would
     * then miss or repeat values.
     */
    @Test
    public void testExecuteWaitsForTheDevice() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);

        FloatArray input = new FloatArray(SIZE);
        FloatArray scaled = new FloatArray(SIZE);
        FloatArray sum = new FloatArray(SIZE);
        sum.init(0.0f);

        TaskGraph g0 = new TaskGraph("w0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("scale", TestCudaGraphMultiGraphPlan::scale, new KernelContext(), input, scaled, 2.0f) //
                .persistOnDevice(scaled);
        TaskGraph g1 = new TaskGraph("w1") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, sum) //
                .consumeFromDevice("w0", scaled) //
                .task("accumulate", TestCudaGraphMultiGraphPlan::accumulate, new KernelContext(), scaled, sum) //
                .persistOnDevice(sum);

        GridScheduler scheduler = new GridScheduler();
        scheduler.addWorkerGrid("w0.scale", grid());
        scheduler.addWorkerGrid("w1.accumulate", grid());

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g0.snapshot(), g1.snapshot())) {
            plan.withCUDAGraph().withGridScheduler(scheduler);
            float expected = 0.0f;
            TornadoExecutionResult result = null;
            for (int it = 1; it <= ITERATIONS; it++) {
                input.init(it);
                result = plan.execute();
                expected += it * 2.0f;
            }
            result.transferToHost(sum);
            assertAll(sum, expected);
        }
    }
}
