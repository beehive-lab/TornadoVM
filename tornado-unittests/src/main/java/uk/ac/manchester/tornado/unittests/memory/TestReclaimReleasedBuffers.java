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
package uk.ac.manchester.tornado.unittests.memory;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Closing a plan releases the device buffers of every object its tasks use, including the ones
 * that never cross to the host: an intermediate one task writes and the next reads, on the device
 * only. Those used to stay allocated -- in device memory and against {@code tornado.device.memory}
 * -- after the plan was closed, so a process that runs plans one after another ran out of device
 * memory.
 *
 * <p>
 * How to run (the budget is what makes the test meaningful):
 *
 * <code>
 * tornado-test -V -J"-Dtornado.device.memory=1GB" uk.ac.manchester.tornado.unittests.memory.TestReclaimReleasedBuffers
 * </code>
 */
public class TestReclaimReleasedBuffers extends TornadoTestBase {

    private static final int ELEMENTS = 50_000_000; // 200 MB a buffer
    private static final int PLANS = 10; // 2 GB of intermediates in total, under a 1 GB budget

    public static void produce(IntArray input, IntArray intermediate) {
        for (@Parallel int i = 0; i < intermediate.getSize(); i++) {
            intermediate.set(i, input.get(0) + i);
        }
    }

    public static void consume(IntArray intermediate, IntArray samples) {
        for (@Parallel int j = 0; j < samples.getSize(); j++) {
            samples.set(j, intermediate.get(j * 1000));
        }
    }

    /** Ten plans in turn, each with a 200 MB device-only intermediate, under a 1 GB budget. */
    @Test
    public void testDeviceOnlyIntermediatesAreReleasedOnClose() throws TornadoExecutionPlanException {
        for (int p = 0; p < PLANS; p++) {
            IntArray input = IntArray.fromElements(p);
            IntArray intermediate = new IntArray(ELEMENTS); // never transferred
            IntArray samples = new IntArray(1024);
            TaskGraph graph = new TaskGraph("plan" + p) //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                    .task("produce", TestReclaimReleasedBuffers::produce, input, intermediate) //
                    .task("consume", TestReclaimReleasedBuffers::consume, intermediate, samples) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, samples);
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
                plan.execute();
            }
            for (int j = 0; j < samples.getSize(); j++) {
                assertEquals("plan " + p + ", sample " + j, p + j * 1000, samples.get(j));
            }
        }
    }
}
