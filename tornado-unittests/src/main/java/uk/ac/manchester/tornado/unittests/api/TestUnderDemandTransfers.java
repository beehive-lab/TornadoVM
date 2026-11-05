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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.DataRange;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Copies back on demand ({@code UNDER_DEMAND} outputs, {@link TornadoExecutionResult#transferToHost}).
 *
 * <p>
 * How to run:
 *
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestUnderDemandTransfers
 * </code>
 */
public class TestUnderDemandTransfers extends TornadoTestBase {

    public static void increment(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, a.get(i) + 1);
        }
    }

    /** A whole copy back after partial ones copies the whole array, not the last partial range again. */
    @Test
    public void testWholeCopyAfterPartialCopies() throws TornadoExecutionPlanException {
        final int n = 256;
        FloatArray a = new FloatArray(n);
        a.init(0f);
        TaskGraph graph = new TaskGraph("partial") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t", TestUnderDemandTransfers::increment, a) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, a);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            TornadoExecutionResult result = null;
            for (int r = 0; r < 5; r++) {
                result = plan.execute();
                result.transferToHost(new DataRange(a).withOffset(0).withSize(1));
                assertEquals(r + 1, a.get(0), 0.0f, "a[0] after execution " + (r + 1));
            }
            result.transferToHost(a);
        }
        for (int i = 0; i < n; i++) {
            assertEquals(5f, a.get(i), 0.0f, "a[" + i + "]");
        }
    }

    /**
     * The task runs on another device than the graph's ({@code withDevice("graph.task", device)}, or
     * {@code -D<graph>.<task>.device}): copies back come from there, not from the graph's device.
     */
    @Test
    public void testCopyBackFromTheTaskDevice() throws TornadoExecutionPlanException {
        TornadoDevice graphDevice = TornadoExecutionPlan.getDefaultDevice();
        TornadoDevice other = null;
        var map = TornadoExecutionPlan.getTornadoDeviceMap();
        for (int b = 0; b < map.getNumBackends() && other == null; b++) {
            var backend = map.getAllBackends().get(b);
            for (int d = 0; d < backend.getNumDevices() && other == null; d++) {
                if (!backend.getDevice(d).toString().equals(graphDevice.toString())) {
                    other = backend.getDevice(d);
                }
            }
        }
        assumeTrue(other != null, "needs a second device");
        final int n = 256;
        FloatArray a = new FloatArray(n);
        a.init(0f);
        TaskGraph graph = new TaskGraph("onTask") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t", TestUnderDemandTransfers::increment, a) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, a);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withDevice("onTask.t", other).execute().transferToHost(a);
        }
        for (int i = 0; i < n; i++) {
            assertEquals(1f, a.get(i), 0.0f, "a[" + i + "]");
        }
    }
}
