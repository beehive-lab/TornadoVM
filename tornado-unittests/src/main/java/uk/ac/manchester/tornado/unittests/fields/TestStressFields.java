/*
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.fields;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to test?
 * </p>
 * <code>
 * tornado-test -pbc -V --enableProfiler silent --fast --jvm="-Dtornado.device.memory=2GB" uk.ac.manchester.tornado.unittests.fields.TestStressFields
 * </code>
 */
public class TestStressFields extends TornadoTestBase {

    FloatArray arrayA;
    FloatArray arrayB;
    FloatArray arrayC;

    private void testStressFields(FloatArray a, FloatArray b, FloatArray c) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            c.set(i, a.get(i) * b.get(i) * b.get(i));
        }
    }

    @Test
    public void testStressMemoryFields() {
        int size = SIZE_OF_512MB / 4;
        arrayA = new FloatArray(size);
        arrayB = new FloatArray(size);
        arrayC = new FloatArray(size);

        arrayA.init(0.1f);
        arrayB.init(0.2f);

        TaskGraph taskGraph = new TaskGraph("testStressMemoryFields") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, arrayA, arrayB) //
                .task("fields", this::testStressFields, arrayA, arrayB, arrayC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayC); //

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            for (int i = 0; i < 100; i++) {
                TornadoExecutionResult execute = executionPlan.execute();
                long totalDeviceMemoryUsage = execute.getProfilerResult().getTotalDeviceMemoryUsage();
                assertEquals(1610612808, totalDeviceMemoryUsage);
            }

            for (int i = 0; i < arrayC.getSize(); i++) {
                assertEquals(arrayA.get(i) * arrayB.get(i) * arrayB.get(i), arrayC.get(i), 0.001f);
            }

        } catch (TornadoExecutionPlanException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testStressMemoryLocalArrays() {
        int size = SIZE_OF_512MB / 4;
        FloatArray a = new FloatArray(size);
        FloatArray b = new FloatArray(size);
        FloatArray c = new FloatArray(size);

        a.init(0.1f);
        b.init(0.2f);
        c.init(0.0f);

        TaskGraph taskGraph = new TaskGraph("testStressMemoryLocalArrays") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("local", this::testStressFields, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c); //

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            for (int i = 0; i < 100; i++) {
                TornadoExecutionResult execute = executionPlan.execute();
                long totalDeviceMemoryUsage = execute.getProfilerResult().getTotalDeviceMemoryUsage();
                assertEquals(1610612808, totalDeviceMemoryUsage);
            }
            for (int i = 0; i < a.getSize(); i++) {
                assertEquals(a.get(i) * b.get(i) * b.get(i), c.get(i), 0.001f);
            }

        } catch (TornadoExecutionPlanException e) {
            throw new RuntimeException(e);
        }
    }
}
