/*
 * Copyright (c) 2013-2020, 2022, 2024, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.unittests.tasks;

import static org.junit.Assert.assertEquals;

import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoBackend;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Testing the TornadoVM API with one task in the same device. The
 * {@link TaskGraph} contains a single task. This task is executed on either on
 * the default device of the one selected.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tasks.TestSingleTaskSingleDevice
 * </code>
 *
 */
public class TestSingleTaskSingleDevice extends TornadoTestBase {

    public static void simpleTask(FloatArray a, FloatArray b, FloatArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    @Test
    public void testSimpleTask() throws TornadoExecutionPlanException {
        final int numElements = 4096;
        FloatArray a = new FloatArray(numElements);
        FloatArray b = new FloatArray(numElements);
        FloatArray c = new FloatArray(numElements);

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, (float) Math.random());
            b.set(i, (float) Math.random());
        });

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)//
                .task("t0", TestSingleTaskSingleDevice::simpleTask, a, b, c)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i), 0.001);
        }
    }

    @Test
    public void testSimpleTaskOnDevice0() throws TornadoExecutionPlanException {
        final int numElements = 4096;
        FloatArray a = new FloatArray(numElements);
        FloatArray b = new FloatArray(numElements);
        FloatArray c = new FloatArray(numElements);

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, (float) Math.random());
            b.set(i, (float) Math.random());
        });

        TornadoBackend driver = TornadoRuntimeProvider.getTornadoRuntime().getBackend(0);
        final int deviceNumber = 0;

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)//
                .task("t0", TestSingleTaskSingleDevice::simpleTask, a, b, c)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withDevice(driver.getDevice(deviceNumber)) //
                    .execute();
        }
        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i), 0.001);
        }
    }

    @Test
    public void testSimpleTaskOnDevice1() throws TornadoExecutionPlanException {
        final int numElements = 4096;
        FloatArray a = new FloatArray(numElements);
        FloatArray b = new FloatArray(numElements);
        FloatArray c = new FloatArray(numElements);

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, (float) Math.random());
            b.set(i, (float) Math.random());
        });

        TornadoBackend driver = TornadoRuntimeProvider.getTornadoRuntime().getBackend(0);

        // select device 1 it is available
        int deviceNumber = 0;
        if (driver.getNumDevices() > 1) {
            deviceNumber = 1;
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)//
                .task("t0", TestSingleTaskSingleDevice::simpleTask, a, b, c)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withDevice(driver.getDevice(deviceNumber)) //
                    .execute();
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i), 0.001);
        }
    }

}
