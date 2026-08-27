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
package uk.ac.manchester.tornado.unittests.profiler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * The profiler's transferred-byte counters have to account for a transfer whether or not that
 * transfer produced an event to time. A copy-out issued with dependency tracking off returns no
 * event, and the size of the buffer is known regardless.
 *
 * <p>
 * How to test?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.profiler.TestProfilerTransferBytes
 * </code>
 */
public class TestProfilerTransferBytes extends TornadoTestBase {

    private static final int SIZE = 8192;

    public static void scale(FloatArray input, FloatArray output) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            output.set(i, input.get(i) * 2.0f);
        }
    }

    @Test
    public void testCopyOutBytesAreCounted() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        input.init(1.0f);
        FloatArray output = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("copyOutBytes") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t", TestProfilerTransferBytes::scale, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            TornadoExecutionResult result = plan.withProfiler(ProfilerMode.SILENT).execute();

            // The data really did come back.
            for (int i = 0; i < SIZE; i++) {
                assertEquals(2.0f, output.get(i), 0.001f);
            }

            long copyIn = result.getProfilerResult().getTotalBytesCopyIn();
            long copyOut = result.getProfilerResult().getTotalBytesCopyOut();

            assertTrue("copy-in bytes should be counted, got " + copyIn, copyIn > 0);
            assertTrue("copy-out bytes should be counted, got " + copyOut, copyOut > 0);
        }
    }

    @Test
    public void testCopyOutBytesAccumulateAcrossExecutions() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        input.init(1.0f);
        FloatArray output = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("copyOutRepeat") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t", TestProfilerTransferBytes::scale, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withProfiler(ProfilerMode.SILENT);
            long previous = 0;
            for (int iteration = 0; iteration < 3; iteration++) {
                TornadoExecutionResult result = plan.execute();
                long copyOut = result.getProfilerResult().getTotalBytesCopyOut();
                assertTrue("copy-out bytes should be counted on execution " + iteration + ", got " + copyOut, copyOut > 0);
                previous = copyOut;
            }
            assertTrue(previous > 0);
        }
    }
}
