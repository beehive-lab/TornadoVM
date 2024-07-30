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

package uk.ac.manchester.tornado.unittests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.Debug;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run.
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.TestHello
 * </code>
 */
public class TestHello extends TornadoTestBase {

    private static void printHello(final int n) {
        for (@Parallel int i = 0; i < n; i++) {
            Debug.printf("hello\n");
        }
    }

    private static void printIntArray(IntArray a) {
        int firstValue = a.get(0);
        int secondValue = a.get(1);

        if (a.getSize() > 1) {
            Debug.printf("First value %d, second value %d\n", firstValue, secondValue);
        }
    }

    private static void printIntArray2(IntArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            Debug.printf("value a[%d] = %d\n", i, a.get(i));
        }
    }

    public static void add(IntArray a, IntArray b, IntArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    public static void simple(IntArray a, IntArray b) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            b.set(i, a.get(i) + 1);
        }
    }

    public static void compute(IntArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, a.get(i) * 2);
        }
    }

    public void compute(IntArray a, IntArray b) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            b.set(i, a.get(i) * 2);
        }
    }

    public void compute(int[] a, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            b[i] = a[i] * 2;
        }
    }

    @Test
    public void testHello() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.SPIRV);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestHello::printHello, 8);
        assertNotNull(taskGraph);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
    }

    @Test
    public void testPrintIntArray() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.SPIRV);
        assertNotBackend(TornadoVMBackendType.PTX);

        int numElements = 16;
        IntArray a = new IntArray(numElements);
        a.set(0, 1);
        a.set(1, 2);

        TaskGraph taskGraph = new TaskGraph("s0")
            .transferToDevice(DataTransferMode.FIRST_EXECUTION, a)
            .task("t0", TestHello::printIntArray, a);
        assertNotNull(taskGraph);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
    }

    @Test
    public void testPrintIntArray2() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.SPIRV);
        assertNotBackend(TornadoVMBackendType.PTX);

        int numElements = 16;
        IntArray a = new IntArray(numElements);
        for (int i = 0; i < numElements; i++) {
            a.set(i, i + 1);
        }

        TaskGraph taskGraph = new TaskGraph("s0")
            .transferToDevice(DataTransferMode.FIRST_EXECUTION, a)
            .task("t0", TestHello::printIntArray2, a);
        assertNotNull(taskGraph);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
    }

    @Test
    public void testVectorAddition() throws TornadoExecutionPlanException {
        int numElements = 16;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        a.init(1);
        b.init(2);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHello::add, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i), 0.001);
        }
    }

    /**
     * How to test.
     *
     * <code>
     * $ tornado-test -V -J"-Dtornado.print.bytecodes=True" uk.ac.manchester.tornado.unittests.TestHello#testSimpleCompute
     * </code>
     */
    @Test
    public void testSimpleCompute() throws TornadoExecutionPlanException {
        int numElements = 256;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);

        a.init(10);

        TestHello t = new TestHello();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", t::compute, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < b.getSize(); i++) {
            assertEquals(a.get(i) * 2, b.get(i));
        }
    }

    @Test
    public void testSimpleCompute2() throws TornadoExecutionPlanException {
        int numElements = 256;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);

        a.init(10);

        TestHello t = new TestHello();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", t::compute, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < b.getSize(); i++) {
            assertEquals(a.get(i) * 2, b.get(i));
        }
    }

    @Test
    public void testSimpleInOut() throws TornadoExecutionPlanException {
        int numElements = 256;
        IntArray a = new IntArray(numElements);

        a.init(10);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestHello::compute, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            assertEquals(20, a.get(i));
        }
    }

}
