/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * A kernel launch the driver rejects must be reported, not silently skipped.
 *
 * <p>The CUDA JNI layer used to log every driver error and carry on, so a rejected
 * {@code cuLaunchKernel} left the output buffer untouched and the caller read stale data as if the
 * kernel had run - a wrong answer with no exception anywhere. Launch and transfer failures now raise
 * {@code CUDAException}, which the queue wrappers translate into a bailout.
 *
 * <p>How to run:
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.api.TestLaunchFailureReporting
 * </pre>
 */
public class TestLaunchFailureReporting extends TornadoTestBase {

    private static final int SIZE = 256;

    /**
     * Blocks per grid in Y. Every CUDA compute capability caps {@code gridDim.y} at 65535, so a launch
     * asking for more is rejected with {@code CUDA_ERROR_INVALID_VALUE} on any NVIDIA GPU, whatever its
     * shared memory, register file or driver version. The kernel itself compiles and loads normally -
     * only the launch fails, which is exactly the path under test.
     */
    private static final int EXCESSIVE_GRID_Y = 100_000_000;

    private static void fill(KernelContext ctx, FloatArray out) {
        out.set(ctx.globalIdx, 1.0f);
    }

    @Test
    public void testRejectedLaunchIsReported() {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);

        FloatArray out = new FloatArray(SIZE);
        out.init(-1.0f);

        KernelContext context = new KernelContext();
        // Local work of 1 in Y turns the Y extent straight into that many blocks.
        WorkerGrid worker = new WorkerGrid2D(SIZE, EXCESSIVE_GRID_Y);
        worker.setLocalWork(64, 1, 1);
        GridScheduler grid = new GridScheduler("badLaunch.t0", worker);

        TaskGraph taskGraph = new TaskGraph("badLaunch") //
                .task("t0", TestLaunchFailureReporting::fill, context, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid).execute();
            fail("a launch the driver rejects must not be reported as success (output stayed " + out.get(0) + ")");
        } catch (TornadoExecutionPlanException | RuntimeException expected) {
            // Expected: the driver error travels out as an exception rather than being logged and dropped.
        }

        // And the output must not look like a successful run either.
        assertEquals(-1.0f, out.get(0), 0.0f, "the rejected kernel must not have written anything");
    }
}
