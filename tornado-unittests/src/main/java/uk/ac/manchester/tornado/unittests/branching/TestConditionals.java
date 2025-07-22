/*
 * Copyright (c) 2013-2022, 2025, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.unittests.branching;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to test?
 *
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.branching.TestConditionals
 * </code>
 */
public class TestConditionals extends TornadoTestBase {

    public static void ifStatement(IntArray a) {
        if (a.get(0) > 1) {
            a.set(0, 10);
        }
    }

    public static void ifElseStatement(IntArray a) {
        if (a.get(0) == 1) {
            a.set(0, 5);
        } else {
            a.set(0, 10);
        }
    }

    public static void nestedIfElseStatement(IntArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            if (a.get(i) > 100) {
                if (a.get(i) > 200) {
                    a.set(i, 5);
                } else {
                    a.set(i, 10);
                }
                a.set(i, a.get(i) + 20);
            } else {
                a.set(i, 2);
            }
        }
    }

    public static void switchStatement(IntArray a) {
        int value = a.get(0);
        switch (value) {
            case 10:
                a.set(0, 5);
                break;
            case 20:
                a.set(0, 10);
                break;
            default:
                a.set(0, 20);
        }
    }

    public static void switchStatement2(IntArray a) {
        int value = a.get(0);
        switch (value) {
            case 10:
                a.set(0, 5);
                break;
            case 20:
                a.set(0, 10);
                break;
        }
    }

    public static void switchStatement3(IntArray a) {
        for (int i = 0; i < a.getSize(); i++) {
            int value = a.get(i);
            switch (value) {
                case 10:
                    a.set(i, 5);
                    break;
                case 20:
                    a.set(i, 10);
                    break;
            }
        }
    }

    public static void switchStatement4(IntArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            int value = a.get(i);
            switch (value) {
                case 10:
                    a.set(i, 5);
                    break;
                case 20:
                    a.set(i, 10);
                    break;
            }
        }
    }

    public static void switchStatement5(IntArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            int value = a.get(i);
            switch (value) {
                case 12:
                    a.set(i, 5);
                    break;
                case 22:
                    a.set(i, 10);
                    break;
                case 42:
                    a.set(i, 30);
                    break;
            }
            a.set(i, a.get(i) * 2);
        }
    }

    public static void switchStatement6(IntArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            int value = a.get(i);
            switch (value) {
                case 12:
                case 22:
                    a.set(i, 10);
                    break;
                case 42:
                    a.set(i, 30);
                    break;
            }
        }
    }

    public static void ternaryCondition(IntArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, (a.get(i) == 20) ? 10 : 5);
        }
    }

    public static void ternaryComplexCondition(IntArray a, IntArray b) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            for (int x = 0; x < a.getSize(); x++) {
                if (i == a.getSize()) {
                    a.set(x, (a.get(x) == 20) ? a.get(x) + b.get(x) : 5);
                }
            }
        }
    }

    public static void ternaryComplexCondition2(IntArray a, IntArray b) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, (a.get(i) == 20) ? a.get(i) + b.get(i) : 5);
        }
    }

    public static void integerTestMove(IntArray output, int dimensionSize) {
        for (@Parallel int i = 0; i < dimensionSize; i++) {
            for (@Parallel int j = 0; j < dimensionSize; j++) {
                if ((i % 2 == 0) & (j % 2 == 0)) {
                    output.set(i + j * dimensionSize, 10);
                } else {
                    output.set(i + j * dimensionSize, -1);
                }
            }
        }
    }

    private static void testShortCircuit(KernelContext context, IntArray array) {
        int idx = context.globalIdx;

        int i = 1 - idx;
        int j = 1 + idx;

        boolean isInBounds = i < array.getSize() && j < array.getSize();
        array.set(idx, isInBounds ? 1 : 0);
    }

    @Test
    public void testIfStatement() throws TornadoExecutionPlanException {
        final int size = 10;
        IntArray a = new IntArray(size);
        a.init(5);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::ifStatement, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertEquals(10, a.get(0));
    }

    @Test
    public void testIfElseStatement() throws TornadoExecutionPlanException {
        final int size = 10;
        IntArray a = new IntArray(size);
        a.init(5);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::ifElseStatement, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertEquals(10, a.get(0));
    }

    @Test
    public void testNestedIfElseStatement() throws TornadoExecutionPlanException {
        final int size = 128;
        IntArray a = new IntArray(size);
        IntArray serial = new IntArray(size);

        IntStream.range(0, size).forEach(i -> {
            a.set(i, 50);
            serial.set(i, a.get(i));
        });

        nestedIfElseStatement(serial);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::nestedIfElseStatement, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(serial.get(i), a.get(i));
        }
    }

    @Test
    public void testSwitch() throws TornadoExecutionPlanException {

        final int size = 10;
        IntArray a = new IntArray(size);

        a.init(20);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::switchStatement, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertEquals(10, a.get(0));
    }

    @Test
    public void testSwitchDefault() throws TornadoExecutionPlanException {

        final int size = 10;
        IntArray a = new IntArray(size);

        a.init(23);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::switchStatement, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertEquals(20, a.get(0));
    }

    @Test
    public void testSwitch2() throws TornadoExecutionPlanException {

        final int size = 10;
        IntArray a = new IntArray(size);

        a.init(20);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::switchStatement2, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertEquals(10, a.get(0));
    }

    @Test
    public void testSwitch3() throws TornadoExecutionPlanException {

        final int size = 10;
        IntArray a = new IntArray(size);

        a.init(20);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::switchStatement3, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            assertEquals(10, a.get(i));
        }
    }

    @Test
    public void testSwitch4() throws TornadoExecutionPlanException {

        final int size = 10;
        IntArray a = new IntArray(size);

        a.init(20);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::switchStatement4, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            assertEquals(10, a.get(i));
        }
    }

    @Test
    public void testSwitch5() throws TornadoExecutionPlanException {
        final int size = 10;
        IntArray a = new IntArray(size);

        a.init(12);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::switchStatement5, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
        for (int i = 0; i < a.getSize(); i++) {
            assertEquals(10, a.get(i));
        }
    }

    @Test
    public void testTernaryCondition() throws TornadoExecutionPlanException {

        final int size = 10;
        IntArray a = new IntArray(size);

        a.init(20);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::ternaryCondition, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            assertEquals(10, a.get(i));
        }
    }

    @Test
    public void testComplexTernaryCondition() throws TornadoExecutionPlanException {

        final int size = 8192;
        IntArray a = new IntArray(size);
        IntArray b = new IntArray(size);

        a.init(20);
        b.init(30);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::ternaryComplexCondition, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            assertEquals(20, a.get(i));
        }
    }

    @Test
    public void testComplexTernaryCondition2() throws TornadoExecutionPlanException {
        final int size = 8192;
        IntArray a = new IntArray(size);
        IntArray b = new IntArray(size);

        a.init(20);
        b.init(30);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::ternaryComplexCondition2, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            assertEquals(50, a.get(i));
        }
    }

    @Test
    public void testSwitch6() throws TornadoExecutionPlanException {
        final int size = 8192;
        IntArray a = new IntArray(size);

        a.init(42);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::switchStatement6, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            assertEquals(30, a.get(i));
        }
    }

    @Test
    public void testSwitch7() throws TornadoExecutionPlanException {
        final int size = 8192;
        IntArray a = new IntArray(size);

        a.init(12);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::switchStatement6, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            assertEquals(10, a.get(i));
        }
    }

    @Test
    public void testSwitch8() throws TornadoExecutionPlanException {
        final int size = 8192;
        IntArray a = new IntArray(size);

        a.init(22);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::switchStatement6, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            assertEquals(10, a.get(i));
        }
    }

    @Test
    public void testIntegerTestMove() throws TornadoExecutionPlanException {
        final int size = 1024;

        IntArray output = new IntArray(size * size);
        IntArray sequential = new IntArray(size * size);

        IntStream.range(0, sequential.getSize()).sequential().forEach(i -> sequential.set(i, i));
        IntStream.range(0, output.getSize()).sequential().forEach(i -> output.set(i, i));

        TaskGraph taskGraph = new TaskGraph("s0");

        taskGraph.task("t0", TestConditionals::integerTestMove, output, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        integerTestMove(sequential, size);

        for (int i = 0; i < size * size; i++) {
            assertEquals(sequential.get(i), output.get(i));
        }
    }

    @Test
    public void testConditionalShortCircuit() {
        assertNotBackend(TornadoVMBackendType.SPIRV);
        IntArray testArr = new IntArray(8);

        // When using the kernel-parallel API, we need to create a Grid and a Worker
        WorkerGrid workerGrid = new WorkerGrid1D(8);    // Create a 1D Worker
        GridScheduler gridScheduler = new GridScheduler("testConditionalShortCircuit.kernel", workerGrid);  // Attach the worker to the Grid
        KernelContext context = new KernelContext();             // Create a context

        TaskGraph taskGraph = new TaskGraph("testConditionalShortCircuit").transferToDevice(DataTransferMode.FIRST_EXECUTION, testArr) // Transfer data from host to device only in the first execution
                .task("kernel", TestConditionals::testShortCircuit, context, testArr)   // Each task points to an existing Java method
                .transferToHost(DataTransferMode.EVERY_EXECUTION, testArr);     // Transfer data from device to host

        // Create an immutable task-graph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Create an execution plan from an immutable task-graph
        boolean caughtInternalException = false;
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        } catch (TornadoInternalError e) {
            System.out.println(e.getMessage());
            caughtInternalException = true;
        } catch (TornadoExecutionPlanException e) {
            throw new RuntimeException(e);
        }
        assertFalse(caughtInternalException);
    }
}
