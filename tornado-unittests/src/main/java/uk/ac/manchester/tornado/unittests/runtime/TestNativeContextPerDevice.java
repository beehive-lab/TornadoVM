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
package uk.ac.manchester.tornado.unittests.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoBackend;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Checks that every device of a backend with native contexts (CUDA) runs in a context of its own,
 * so that work placed on device N really runs on GPU N.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.runtime.TestNativeContextPerDevice
 * </code>
 */
public class TestNativeContextPerDevice extends TornadoTestBase {

    private static final int SIZE = 4096;
    private static final int LOCAL_SIZE = 128;

    public static void addDeviceIndex(KernelContext context, IntArray input, IntArray output, int deviceIndex) {
        int id = context.globalIdx;
        if (id < input.getSize()) {
            output.set(id, input.get(id) + deviceIndex);
        }
    }

    @Test
    public void testOneContextPerDevice() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);
        TornadoBackend backend = getTornadoRuntime().getBackend(getTornadoRuntime().getDefaultDevice().getBackendIndex());

        Map<Long, Integer> deviceOfContext = new HashMap<>();
        for (int deviceIndex = 0; deviceIndex < backend.getNumDevices(); deviceIndex++) {
            TornadoDevice device = backend.getDevice(deviceIndex);
            IntArray input = new IntArray(SIZE);
            IntArray output = new IntArray(SIZE);
            input.init(7);

            TaskGraph taskGraph = new TaskGraph("s0") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                    .task("t0", TestNativeContextPerDevice::addDeviceIndex, new KernelContext(), input, output, deviceIndex) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

            WorkerGrid worker = new WorkerGrid1D(SIZE);
            worker.setLocalWork(LOCAL_SIZE, 1, 1);
            GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);

            try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                executionPlan.withDevice(device).withGridScheduler(gridScheduler).execute();

                long nativeContext = ((TornadoNativeStreamSupport) device).getNativeContext(executionPlan.getId());
                assertNotEquals("device " + deviceIndex + " has no native context", 0L, nativeContext);
                Integer previous = deviceOfContext.put(nativeContext, deviceIndex);
                assertTrue("devices " + previous + " and " + deviceIndex + " share one native context", previous == null);
            }

            for (int i = 0; i < SIZE; i++) {
                assertEquals(7 + deviceIndex, output.get(i));
            }
        }
    }
}
