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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

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
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Every kind of transfer still gives the right data when only the buffers a task-graph transfers on
 * every execution are backed by pinned host memory: a one-shot upload that is later updated on
 * demand, a device-only buffer passed between task-graphs, and per-execution inputs and outputs,
 * while other plans are created and closed around a live one.
 *
 * <p>
 * How to run?
 * </p>
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestPinnedTransfers
 * </code>
 * </p>
 */
public class TestPinnedTransfers extends TornadoTestBase {

    /** 4 MB per array, below the staged-transfer size. */
    private static final int SIZE = 1024 * 1024;

    private static final int LOCAL = 256;

    private static final int ROUNDS = 4;

    private static void scale(KernelContext context, FloatArray input, FloatArray weights, FloatArray scratch) {
        int i = context.globalIdx;
        if (i < input.getSize()) {
            scratch.set(i, input.get(i) * weights.get(i));
        }
    }

    private static void addOne(KernelContext context, FloatArray scratch, FloatArray output) {
        int i = context.globalIdx;
        if (i < scratch.getSize()) {
            output.set(i, scratch.get(i) + 1.0f);
        }
    }

    /** One plan: weights uploaded once, scratch kept on the device, input and output every execution. */
    private static final class Pipeline {
        final FloatArray input = new FloatArray(SIZE);
        final FloatArray weights = new FloatArray(SIZE);
        final FloatArray scratch = new FloatArray(SIZE);
        final FloatArray output = new FloatArray(SIZE);
        final TornadoExecutionPlan plan;

        Pipeline(String name, float weight, boolean cudaGraph) {
            weights.init(weight);
            scratch.init(0.0f);
            String producerName = name + "Producer";
            String consumerName = name + "Consumer";
            TaskGraph producer = new TaskGraph(producerName) //
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights, scratch) //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                    .task("scale", TestPinnedTransfers::scale, new KernelContext(), input, weights, scratch) //
                    .persistOnDevice(scratch);
            TaskGraph consumer = new TaskGraph(consumerName) //
                    .consumeFromDevice(producerName, scratch) //
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                    .task("addOne", TestPinnedTransfers::addOne, new KernelContext(), scratch, output) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
            GridScheduler grid = new GridScheduler();
            grid.addWorkerGrid(producerName + ".scale", workerGrid());
            grid.addWorkerGrid(consumerName + ".addOne", workerGrid());
            plan = new TornadoExecutionPlan(producer.snapshot(), consumer.snapshot());
            plan.withGridScheduler(grid);
            if (cudaGraph) {
                plan.withCUDAGraph();
            }
        }

        void run(float value, float weight) {
            input.init(value);
            output.init(0.0f);
            plan.withGraph(0).execute();
            plan.withGraph(1).execute();
            for (int i = 0; i < SIZE; i += 1021) {
                assertEquals(value * weight + 1.0f, output.get(i), 0.0f);
            }
        }
    }

    private static WorkerGrid workerGrid() {
        WorkerGrid grid = new WorkerGrid1D(SIZE);
        grid.setLocalWork(LOCAL, 1, 1);
        return grid;
    }

    private void runWithPlanChurn(boolean cudaGraph) throws TornadoExecutionPlanException {
        Pipeline live = new Pipeline("live", 2.0f, cudaGraph);
        try {
            live.run(1.0f, 2.0f);
            live.run(2.0f, 2.0f);
            for (int round = 0; round < ROUNDS; round++) {
                Pipeline other = new Pipeline("other" + round, 3.0f, cudaGraph);
                try {
                    other.run(1.0f + round, 3.0f);
                    live.run(3.0f + round, 2.0f);
                } finally {
                    other.plan.close();
                }
                live.run(10.0f + round, 2.0f);
            }
            if (!cudaGraph) {
                // a one-shot upload, updated on demand: the new weights must reach the device
                live.weights.init(5.0f);
                live.plan.transferToDevice(live.weights);
                live.run(4.0f, 5.0f);
            }
        } finally {
            live.plan.close();
        }
    }

    @Test
    public void testTransferModesWithPlanChurn() throws TornadoExecutionPlanException {
        runWithPlanChurn(false);
    }

    @Test
    public void testTransferModesWithPlanChurnUnderCudaGraphs() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);
        runWithPlanChurn(true);
    }
}
