/*
 * Copyright (c) 2022, APT Group, Department of Computer Science,
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

import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.arrays.TestArrays;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestIO
 * </code>
 */
public class TestIO extends TornadoTestBase {
    // CHECKSTYLE:OFF

    private FloatArray createAndInitializeArray(int size) {
        FloatArray array = new FloatArray(size);
        IntStream.range(0, size).parallel().forEach(idx -> {
            array.set(idx, idx);
        });

        return array;
    }

    /**
     * This test case uses the forceCopyIn method of the
     * {@link uk.ac.manchester.tornado.api.TaskGraph} API to pass input data to a
     * targeted device.
     *
     * <p>
     * This method is used to copy data once and reuse it in the next invocations.
     * </p>
     */
    @Test
    public void testForceCopyIn() throws TornadoExecutionPlanException {
        final int N = 128;

        FloatArray arrayA = createAndInitializeArray(N);
        FloatArray arrayB = createAndInitializeArray(N);
        FloatArray arrayC = new FloatArray(N);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, arrayA, arrayB) //
                .task("t0", TestArrays::vectorAddFloat, arrayA, arrayB, arrayC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            for (int i = 0; i < 4; i++) {
                executionPlan.execute();
            }
        }

        for (int i = 0; i < N; i++) {
            assertEquals(2 * i, arrayC.get(i), 0.0f);
        }
    }

    /**
     * This test case uses the streamIn method of the
     * {@link uk.ac.manchester.tornado.api.TaskGraph} API to pass input data to a
     * targeted device.
     *
     * This method is used to stream data every time a method is launched for
     * execution.
     */
    @Test
    public void testStreamIn() throws TornadoExecutionPlanException {
        final int N = 128;

        FloatArray arrayA = createAndInitializeArray(N);
        FloatArray arrayB = createAndInitializeArray(N);
        FloatArray arrayC = new FloatArray(N);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, arrayA, arrayB) //
                .task("t0", TestArrays::vectorAddFloat, arrayA, arrayB, arrayC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            for (int i = 0; i < 4; i++) {
                executionPlan.execute();
            }
        }

        for (int i = 0; i < N; i++) {
            assertEquals(2 * i, arrayC.get(i), 0.0f);
        }
    }

    /**
     * This test case uses the streamIn method of the
     * {@link uk.ac.manchester.tornado.api.TaskGraph} API to pass input data to a
     * targeted device.
     *
     * <p>
     * Additionally, the lockObjectsInMemory method is used to pin buffers used for
     * streaming data to a device. Buffers used for locked arguments will be created
     * and allocated once and will be reused in the next invocations. The pinned
     * buffers are released by the unlockObjectsFromMemory method.
     * </p>
     */
    @Test
    public void testLockObjectsInMemory() throws TornadoExecutionPlanException {
        final int N = 128;

        FloatArray arrayA = createAndInitializeArray(N);
        FloatArray arrayB = createAndInitializeArray(N);
        FloatArray arrayC = new FloatArray(N);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, arrayA, arrayB) //
                .task("t0", TestArrays::vectorAddFloat, arrayA, arrayB, arrayC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            for (int i = 0; i < 4; i++) {
                executionPlan.execute();
            }

        }

        for (int i = 0; i < N; i++) {
            assertEquals(2 * i, arrayC.get(i), 0.0f);
        }
    }

    /**
     * This test case uses the streamIn method of the
     * {@link uk.ac.manchester.tornado.api.TornadoExecutionPlan} API to execute on specific device.
     *
     * <p>
     * The following test reproduces a bug, that when the user sets an array as DataTransferMode.FIRST_EXECUTION
     * via the withDevice method, the runtime overrides this setting and copies all arrays on every execution.
     * </p>
     */
    @Test
    public void testCopyInWithDevice() throws TornadoExecutionPlanException {
        final int N = 16384;
        final int ITERATIONS = 40;

        FloatArray arrayA = createAndInitializeArray(N);
        FloatArray arrayB = createAndInitializeArray(N);
        FloatArray arrayC = new FloatArray(N);

        // Enable profiler
        System.setProperty("tornado.profiler", "True");

        TaskGraph taskGraph = new TaskGraph("foo") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, arrayA) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, arrayB) //
                .task("bar", TestArrays::vectorAddFloat, arrayA, arrayB, arrayC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            long copyInSumSimpleExec = 0L;
            for (int i = 0; i < ITERATIONS; i++) {
                TornadoExecutionResult executionResult = executionPlan.execute();
                copyInSumSimpleExec += executionResult.getProfilerResult().getDeviceWriteTime();
            }
            executionPlan.freeDeviceMemory();

            executionPlan.withDevice(TornadoExecutionPlan.getDevice(0, 0));

            long copyInSumSimpleExecWithDev = 0L;
            for (int i = 0; i < ITERATIONS; i++) {
                TornadoExecutionResult executionResult = executionPlan.execute();
                copyInSumSimpleExecWithDev += executionResult.getProfilerResult().getDeviceWriteTime();
            }
            // Generous assertions with delta of 25%
            assertEquals(copyInSumSimpleExec, copyInSumSimpleExecWithDev, (float) copyInSumSimpleExec / 4);
        }
    }
    // CHECKSTYLE:ON
}
