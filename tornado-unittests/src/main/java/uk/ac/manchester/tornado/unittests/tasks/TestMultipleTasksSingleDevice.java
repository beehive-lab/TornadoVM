/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Testing Tornado with multiple tasks at the same device. The {@link TaskGraph}
 * contains more than one task.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tasks.TestMultipleTasksSingleDevice
 * </code>
 *
 */
public class TestMultipleTasksSingleDevice extends TornadoTestBase {

    public static void task0Initialization(IntArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, 10);
        }
    }

    public static void task1Multiplication(IntArray a, int alpha) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, a.get(i) * alpha);
        }
    }

    public static void task2Saxpy(IntArray a, IntArray b, IntArray c, int alpha) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            c.set(i, alpha * a.get(i) + b.get(i));
        }
    }

    @Test
    public void testTwoTasks() throws TornadoExecutionPlanException {
        final int numElements = 1024;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)//
                .task("t0", TestMultipleTasksSingleDevice::task0Initialization, a)//
                .task("t1", TestMultipleTasksSingleDevice::task1Multiplication, a, 12)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            assertEquals(120, a.get(i));
        }
    }

    @Test
    public void testThreeTasks() throws TornadoExecutionPlanException {
        final int numElements = 1024;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)//
                .task("t0", TestMultipleTasksSingleDevice::task0Initialization, a)//
                .task("t1", TestMultipleTasksSingleDevice::task1Multiplication, a, 12)//
                .task("t3", TestMultipleTasksSingleDevice::task2Saxpy, a, a, b, 12)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        int val = (12 * 120) + 120;
        for (int i = 0; i < a.getSize(); i++) {
            assertEquals(val, b.get(i));
        }
    }

    @Test
    public void testFourTasks() throws TornadoExecutionPlanException {
        final int numElements = 1024;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)//
                .task("t0", TestMultipleTasksSingleDevice::task0Initialization, a)//
                .task("t1", TestMultipleTasksSingleDevice::task1Multiplication, a, 12)//
                .task("t2", TestMultipleTasksSingleDevice::task0Initialization, b)//
                .task("t3", TestMultipleTasksSingleDevice::task2Saxpy, a, b, c, 12)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        int val = (12 * 120) + 10;
        for (int i = 0; i < a.getSize(); i++) {
            assertEquals(val, c.get(i));
        }
    }

    @Test
    public void testFiveTasks() throws TornadoExecutionPlanException {
        final int numElements = 1024;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)//
                .task("t0", TestMultipleTasksSingleDevice::task0Initialization, a)//
                .task("t1", TestMultipleTasksSingleDevice::task1Multiplication, a, 12)//
                .task("t2", TestMultipleTasksSingleDevice::task0Initialization, b)//
                .task("t3", TestMultipleTasksSingleDevice::task2Saxpy, a, b, b, 12)//
                .task("t4", TestMultipleTasksSingleDevice::task2Saxpy, b, a, c, 12)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        int val = (12 * 120) + 10;
        val = (12 * val) + (120);
        for (int i = 0; i < a.getSize(); i++) {
            assertEquals(val, c.get(i));
        }
    }

}
