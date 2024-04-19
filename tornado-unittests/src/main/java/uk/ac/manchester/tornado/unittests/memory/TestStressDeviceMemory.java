/*
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to test?
 *
 * <p>
 * <code>
 * tornado-test --jvm="-Dtornado.device.memory=4GB" -V uk.ac.manchester.tornado.unittests.memory.TestStressDeviceMemory
 * </code>
 * </p>
 */
public class TestStressDeviceMemory extends TornadoTestBase {

    public static void moveData(FloatArray inputArray, FloatArray outputArray) {
        for (@Parallel int i = 0; i < inputArray.getSize(); i++) {
            outputArray.set(i, inputArray.get(i));
        }
    }

    public static void stressDataAllocationTest(int dataSizeFactor) throws TornadoExecutionPlanException {
        System.out.println("Allocating size: " + (dataSizeFactor * 4) + " (bytes)");
        FloatArray inputArray = new FloatArray(dataSizeFactor);
        FloatArray outputArray = new FloatArray(dataSizeFactor);
        inputArray.init(0.1f);
        TaskGraph taskGraph = new TaskGraph("memory" + dataSizeFactor) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, inputArray) //
                .task("stress", TestStressDeviceMemory::moveData, inputArray, outputArray) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputArray);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
    }

    @Test
    public void test01() {
        final int minSize = 1024 * 1024 * 256;  // ~1GB of FP32 values
        for (int i = 0; i < 10; i++) {
            try {
                stressDataAllocationTest(minSize);
                assertTrue(true);
            } catch (TornadoExecutionPlanException e) {
                fail();
            }
        }
    }

    /**
     * Depending on the device, this test is expected to fail when using the
     * OpenCL backend.
     */
    @Test
    public void test02() {
        // Starting in ~1.5GB and move up to ~2GB
        for (int i = 400; i < 500; i += 10) {
            int size = 1024 * 1024 * i;
            try {
                stressDataAllocationTest(size);
                assertTrue(true);
            } catch (TornadoExecutionPlanException e) {
                fail();
            }
        }
    }
}
