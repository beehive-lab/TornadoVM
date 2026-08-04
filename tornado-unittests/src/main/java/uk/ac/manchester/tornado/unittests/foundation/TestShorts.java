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
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.foundation.TestShorts
 * </code>
 */
public class TestShorts extends TornadoTestBase {

    public static void vectorSubShortCompute(ShortArray a, ShortArray b, ShortArray c) {
        for (int i = 0; i < c.getSize(); i++) {
            a.set(i, (short) (b.get(i) - c.get(i)));
        }
    }

    public static void vectorMulShortCompute(ShortArray a, ShortArray b, ShortArray c) {
        for (int i = 0; i < c.getSize(); i++) {
            a.set(i, (short) (b.get(i) * c.get(i)));
        }
    }

    public static void vectorDivShortCompute(ShortArray a, ShortArray b, ShortArray c) {
        for (int i = 0; i < c.getSize(); i++) {
            a.set(i, (short) (b.get(i) / c.get(i)));
        }
    }

    @Test
    public void testShortAdd() throws TornadoExecutionPlanException {
        final int numElements = 256;
        ShortArray a = new ShortArray(numElements);
        ShortArray b = new ShortArray(numElements);
        ShortArray c = new ShortArray(numElements);

        b.init((short) 1);
        c.init((short) 3);

        ShortArray expectedResult = new ShortArray(numElements);
        expectedResult.init((short) 4);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, b, c) //
                .task("t0", TestKernels::vectorSumShortCompute, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < numElements; i++) {
            assertEquals(expectedResult.get(i), a.get(i));
        }
    }

    @Test
    public void testShortSub() throws TornadoExecutionPlanException {
        final int numElements = 256;
        ShortArray a = new ShortArray(numElements);
        ShortArray b = new ShortArray(numElements);
        ShortArray c = new ShortArray(numElements);

        b.init((short) 10);
        c.init((short) 3);

        ShortArray expectedResult = new ShortArray(numElements);
        expectedResult.init((short) 7);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, b, c) //
                .task("t0", TestShorts::vectorSubShortCompute, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < numElements; i++) {
            assertEquals(expectedResult.get(i), a.get(i));
        }
    }

    @Test
    public void testShortMul() throws TornadoExecutionPlanException {
        final int numElements = 256;
        ShortArray a = new ShortArray(numElements);
        ShortArray b = new ShortArray(numElements);
        ShortArray c = new ShortArray(numElements);

        b.init((short) 5);
        c.init((short) 3);

        ShortArray expectedResult = new ShortArray(numElements);
        expectedResult.init((short) 15);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, b, c) //
                .task("t0", TestShorts::vectorMulShortCompute, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < numElements; i++) {
            assertEquals(expectedResult.get(i), a.get(i));
        }
    }

    @Test
    public void testShortDiv() throws TornadoExecutionPlanException {
        final int numElements = 256;
        ShortArray a = new ShortArray(numElements);
        ShortArray b = new ShortArray(numElements);
        ShortArray c = new ShortArray(numElements);

        b.init((short) 20);
        c.init((short) 4);

        ShortArray expectedResult = new ShortArray(numElements);
        expectedResult.init((short) 5);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, b, c) //
                .task("t0", TestShorts::vectorDivShortCompute, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < numElements; i++) {
            assertEquals(expectedResult.get(i), a.get(i));
        }
    }

}
