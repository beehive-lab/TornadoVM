/*
 * Copyright (c) 2021, 2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.foundation;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to test?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.foundation.TestDoubles
 * </code>
 */
public class TestDoubles extends TornadoTestBase {

    @Test
    public void testDoublesCopy() throws TornadoExecutionPlanException {
        final int numElements = 256;
        DoubleArray a = new DoubleArray(numElements);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestKernels::testDoublesCopy, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertEquals(a.get(0), 50.0, 0.01);
    }

    @Test
    public void testDoublesAdd() throws TornadoExecutionPlanException {

        final int numElements = 256;
        DoubleArray a = new DoubleArray(numElements);
        DoubleArray b = new DoubleArray(numElements);
        DoubleArray c = new DoubleArray(numElements);

        b.init(100);
        c.init(200);

        DoubleArray expected = new DoubleArray(numElements);
        for (int i = 0; i < numElements; i++) {
            expected.set(i, b.get(i) + c.get(i));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, b, c) //
                .task("t0", TestKernels::vectorAddDoubleCompute, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < numElements; i++) {
            assertEquals(expected.get(i), a.get(i), 0.01f);
        }
    }

    @Test
    public void testDoublesSub() throws TornadoExecutionPlanException {

        final int numElements = 256;
        DoubleArray a = new DoubleArray(numElements);
        DoubleArray b = new DoubleArray(numElements);
        DoubleArray c = new DoubleArray(numElements);

        b.init(2.2);
        c.init(3.5);

        DoubleArray expected = new DoubleArray(numElements);
        for (int i = 0; i < numElements; i++) {
            expected.set(i, b.get(i) - c.get(i));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, b, c) //
                .task("t0", TestKernels::vectorSubDoubleCompute, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < numElements; i++) {
            assertEquals(expected.get(i), a.get(i), 0.01f);
        }
    }

    @Test
    public void testDoublesMul() throws TornadoExecutionPlanException {

        final int numElements = 256;
        DoubleArray a = new DoubleArray(numElements);
        DoubleArray b = new DoubleArray(numElements);
        DoubleArray c = new DoubleArray(numElements);

        b.init(2.2);
        c.init(3.5);

        DoubleArray expected = new DoubleArray(numElements);
        for (int i = 0; i < numElements; i++) {
            expected.set(i, b.get(i) * c.get(i));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, b, c) //
                .task("t0", TestKernels::vectorMulDoubleCompute, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < numElements; i++) {
            assertEquals(expected.get(i), a.get(i), 0.01f);
        }
    }

    @Test
    public void testDoublesDiv() throws TornadoExecutionPlanException {

        final int numElements = 256;
        DoubleArray a = new DoubleArray(numElements);
        DoubleArray b = new DoubleArray(numElements);
        DoubleArray c = new DoubleArray(numElements);

        b.init(10.2);
        c.init(2.0);

        DoubleArray expected = new DoubleArray(numElements);
        for (int i = 0; i < numElements; i++) {
            expected.set(i, b.get(i) / c.get(i));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, b, c) //
                .task("t0", TestKernels::vectorDivDoubleCompute, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
        for (int i = 0; i < numElements; i++) {
            assertEquals(expected.get(i), a.get(i), 0.01f);
        }
    }

}
