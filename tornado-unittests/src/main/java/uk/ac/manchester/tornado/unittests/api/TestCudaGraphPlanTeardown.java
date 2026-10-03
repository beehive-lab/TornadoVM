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
 * Closing one execution plan must not break the CUDA graphs of another plan that is still alive.
 *
 * <p>
 * A captured CUDA graph copies its per-execution inputs from their pinned host buffers. Tearing
 * down one plan used to unregister the pinned host memory of every plan on the device, so the
 * next graph launch of a live plan crashed in the driver.
 * </p>
 *
 * <p>
 * How to run?
 * </p>
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestCudaGraphPlanTeardown
 * </code>
 * </p>
 */
public class TestCudaGraphPlanTeardown extends TornadoTestBase {

    /** 1 MB per array: below the staged-transfer size, so the inputs are pinned. */
    private static final int SIZE = 256 * 1024;

    private static final int LOCAL = 256;

    private static final int ROUNDS = 4;

    private static void scale(KernelContext context, FloatArray input, FloatArray output, float factor) {
        int i = context.globalIdx;
        if (i < input.getSize()) {
            output.set(i, input.get(i) * factor);
        }
    }

    private static TornadoExecutionPlan graphPlan(String name, FloatArray input, FloatArray output, float factor) {
        TaskGraph graph = new TaskGraph(name) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("scale", TestCudaGraphPlanTeardown::scale, new KernelContext(), input, output, factor) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        WorkerGrid grid = new WorkerGrid1D(SIZE);
        grid.setLocalWork(LOCAL, 1, 1);
        TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
        plan.withCUDAGraph().withGridScheduler(new GridScheduler(name + ".scale", grid));
        return plan;
    }

    private static void run(TornadoExecutionPlan plan, FloatArray input, FloatArray output, float value, float factor) {
        input.init(value);
        output.init(0.0f);
        plan.execute();
        for (int i = 0; i < SIZE; i += 1021) {
            assertEquals(value * factor, output.get(i), 0.0f);
        }
    }

    @Test
    public void testLivePlanSurvivesTeardownOfAnother() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);

        FloatArray liveInput = new FloatArray(SIZE);
        FloatArray liveOutput = new FloatArray(SIZE);
        try (TornadoExecutionPlan live = graphPlan("live", liveInput, liveOutput, 2.0f)) {
            // capture, then replay
            run(live, liveInput, liveOutput, 1.0f, 2.0f);
            run(live, liveInput, liveOutput, 2.0f, 2.0f);
            for (int round = 0; round < ROUNDS; round++) {
                FloatArray input = new FloatArray(SIZE);
                FloatArray output = new FloatArray(SIZE);
                try (TornadoExecutionPlan other = graphPlan("other" + round, input, output, 3.0f)) {
                    run(other, input, output, 1.0f + round, 3.0f);
                    run(live, liveInput, liveOutput, 3.0f + round, 2.0f);
                }
                // the other plan is closed: replaying the live plan's graph must still work
                run(live, liveInput, liveOutput, 10.0f + round, 2.0f);
            }
        }
    }
}
