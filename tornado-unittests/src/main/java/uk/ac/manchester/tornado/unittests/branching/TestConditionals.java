/*
 * Copyright (c) 2013-2022, 2025-2026, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task4;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DInt;
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

    // Intentional fallthrough: case 10 has no `break`, so it also executes case 20's body.
    public static void switchFallthrough(IntArray a) {
        for (int i = 0; i < a.getSize(); i++) {
            int value = a.get(i);
            int acc = 0;
            switch (value) {
                case 10:
                    acc += 1;
                case 20:
                    acc += 100;
                    break;
                default:
                    acc = -1;
            }
            a.set(i, acc);
        }
    }

    public static void tripleNestedTernary(IntArray a, IntArray out) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            int v = a.get(i);
            int result = (v < 10) ? 1 : (v < 20) ? 2 : (v < 30) ? 3 : 4;
            out.set(i, result);
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
    public void testSwitchFallthrough() throws TornadoExecutionPlanException {
        final int size = 16;
        IntArray a = new IntArray(size);
        // half the values hit case 10 (falls through into case 20), half hit case 20 directly,
        // and one hits default -- exercises all three paths in the same run.
        for (int i = 0; i < size; i++) {
            a.set(i, (i % 3 == 0) ? 10 : (i % 3 == 1) ? 20 : 99);
        }
        IntArray expected = new IntArray(size);
        for (int i = 0; i < size; i++) {
            expected.set(i, a.get(i));
        }
        switchFallthrough(expected);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::switchFallthrough, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(expected.get(i), a.get(i));
        }
    }

    @Test
    public void testTripleNestedTernary() throws TornadoExecutionPlanException {
        final int size = 40;
        IntArray a = new IntArray(size);
        for (int i = 0; i < size; i++) {
            a.set(i, i);
        }
        IntArray out = new IntArray(size);
        IntArray expected = new IntArray(size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::tripleNestedTernary, a, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        tripleNestedTernary(a, expected);
        for (int i = 0; i < size; i++) {
            assertEquals(expected.get(i), out.get(i));
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

    /**
     * Kernel with an if-else where both branches write to the same {@link Matrix2DInt}
     * parameter using 2D {@link KernelContext} indices. This pattern previously
     * triggered a {@code GraalGraphError} in {@code TornadoNativeTypeElimination}
     * because a shared {@code PiNode} was deleted more than once when it had
     * multiple {@code OffsetAddressNode} usages.
     */
    private static void ifElseWriteMatrix2D(KernelContext context, Matrix2DInt mtx, IntArray arr) {
        int row = context.globalIdx;
        int col = context.globalIdy;
        int a = arr.get(0);
        if (a > col) {
            mtx.set(row, col, a);
        } else {
            mtx.set(row, col, -1);
        }
    }

    private static void ifElseWriteMatrix2DSequential(Matrix2DInt mtx, IntArray arr) {
        int a = arr.get(0);
        for (int row = 0; row < mtx.getNumRows(); row++) {
            for (int col = 0; col < mtx.getNumColumns(); col++) {
                if (a > col) {
                    mtx.set(row, col, a);
                } else {
                    mtx.set(row, col, -1);
                }
            }
        }
    }

    @Test
    public void testIfElseBothBranchesWriteMatrix2D() throws TornadoExecutionPlanException {
        final int rows = 5;
        final int cols = 8;

        Matrix2DInt mtxTornado = new Matrix2DInt(rows, cols);
        Matrix2DInt mtxSequential = new Matrix2DInt(rows, cols);
        IntArray arr = IntArray.fromArray(new int[]{3, 1, 2, 4, 5, 6});

        WorkerGrid workerGrid = new WorkerGrid2D(rows, cols);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, arr)
                .task("t0", TestConditionals::ifElseWriteMatrix2D, context, mtxTornado, arr)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, mtxTornado);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        ifElseWriteMatrix2DSequential(mtxSequential, arr);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                assertEquals(mtxSequential.get(row, col), mtxTornado.get(row, col));
            }
        }
    }

    private static final int ROWS_PER_GROUP = 4;

    /**
     * Kernel whose inner guard becomes a compile-time constant: after the early
     * return, {@code first + r < active} is provably true for {@code r = 0}, so once
     * the constant-trip loop is unrolled the corresponding {@code IfNode} has a
     * {@code LogicConstantNode} as its condition. This used to fail on the OpenCL
     * backend with {@code TornadoRuntimeException: logic node (class=...LogicConstantNode)}.
     * See https://github.com/beehive-lab/TornadoVM/issues/1131.
     */
    private static void provablyTrueGuard(KernelContext context, FloatArray x, FloatArray out, int active) {
        int first = context.groupIdx * ROWS_PER_GROUP;
        if (first >= active) {
            return;
        }
        for (int r = 0; r < ROWS_PER_GROUP; r++) {
            if (first + r < active) {
                out.set(first + r, x.get(first + r) * 2.0f);
            }
        }
    }

    private static void provablyTrueGuardSequential(FloatArray x, FloatArray out, int active, int numGroups) {
        for (int group = 0; group < numGroups; group++) {
            int first = group * ROWS_PER_GROUP;
            if (first >= active) {
                continue;
            }
            for (int r = 0; r < ROWS_PER_GROUP; r++) {
                if (first + r < active) {
                    out.set(first + r, x.get(first + r) * 2.0f);
                }
            }
        }
    }

    @Test
    public void testIfConditionFoldedToConstant() throws TornadoExecutionPlanException {
        final int numGroups = 16;
        final int size = numGroups * ROWS_PER_GROUP;
        final int active = 10;

        FloatArray x = new FloatArray(size);
        FloatArray outTornado = new FloatArray(size);
        FloatArray outSequential = new FloatArray(size);
        for (int i = 0; i < size; i++) {
            x.set(i, i + 1.0f);
        }

        WorkerGrid workerGrid = new WorkerGrid1D(numGroups);
        workerGrid.setLocalWork(1, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, x, outTornado) //
                .task("t0", TestConditionals::provablyTrueGuard, context, x, outTornado, active) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outTornado);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        provablyTrueGuardSequential(x, outSequential, active, numGroups);

        for (int i = 0; i < size; i++) {
            assertEquals(outSequential.get(i), outTornado.get(i), 0.0f);
        }
    }

    // --- short-circuit conditions with an else branch (#1137) ------------------------------
    //
    // `if (A || B) { X } else { Y }` is lowered to two ifs whose then-paths meet in a partial merge
    // before X, while Y goes straight to the real join. When the join was guessed as that partial
    // merge, Y fell through into X. Every thread gets its own operands, so each launch takes both
    // branches; the loop in X makes a fall-through visible in the result.

    private static int shortCircuitOrReference(int a, int b) {
        int acc = 0;
        if (a <= 32 || b >= 16) {
            for (int k = 0; k < a; k++) {
                acc += k;
            }
        } else {
            acc = -1000;
        }
        return acc;
    }

    private static int shortCircuitAndReference(int a, int b) {
        int acc = 0;
        if (a > 32 && b < 16) {
            acc = -1000;
        } else {
            for (int k = 0; k < a; k++) {
                acc += k;
            }
        }
        return acc;
    }

    private static int shortCircuitTripleOrReference(int a, int b) {
        int acc = 0;
        if (a <= 16 || b >= 24 || a == b) {
            for (int k = 0; k < a; k++) {
                acc += k;
            }
        } else {
            acc = -1000 - b;
        }
        return acc;
    }

    private static void shortCircuitOr(KernelContext context, IntArray inA, IntArray inB, IntArray out) {
        int i = context.globalIdx;
        int a = inA.get(i);
        int b = inB.get(i);
        int acc = 0;
        if (a <= 32 || b >= 16) {
            for (int k = 0; k < a; k++) {
                acc += k;
            }
        } else {
            acc = -1000;
        }
        out.set(i, acc);
    }

    private static void shortCircuitAnd(KernelContext context, IntArray inA, IntArray inB, IntArray out) {
        int i = context.globalIdx;
        int a = inA.get(i);
        int b = inB.get(i);
        int acc = 0;
        if (a > 32 && b < 16) {
            acc = -1000;
        } else {
            for (int k = 0; k < a; k++) {
                acc += k;
            }
        }
        out.set(i, acc);
    }

    private static void shortCircuitTripleOr(KernelContext context, IntArray inA, IntArray inB, IntArray out) {
        int i = context.globalIdx;
        int a = inA.get(i);
        int b = inB.get(i);
        int acc = 0;
        if (a <= 16 || b >= 24 || a == b) {
            for (int k = 0; k < a; k++) {
                acc += k;
            }
        } else {
            acc = -1000 - b;
        }
        out.set(i, acc);
    }

    /** The shape of the original report: the whole if/else sits inside an outer if and ends the kernel. */
    private static void shortCircuitOrNested(KernelContext context, IntArray inA, IntArray inB, IntArray out) {
        int i = context.globalIdx;
        if (i < out.getSize()) {
            int a = inA.get(i);
            int b = inB.get(i);
            if (a <= 32 || b >= 16) {
                int acc = 0;
                for (int k = 0; k < a; k++) {
                    acc += k;
                }
                out.set(i, acc);
            } else {
                out.set(i, -1000);
            }
        }
    }

    private static void shortCircuitAndNested(KernelContext context, IntArray inA, IntArray inB, IntArray out) {
        int i = context.globalIdx;
        if (i < out.getSize()) {
            int a = inA.get(i);
            int b = inB.get(i);
            if (a > 32 && b < 16) {
                out.set(i, -1000);
            } else {
                int acc = 0;
                for (int k = 0; k < a; k++) {
                    acc += k;
                }
                out.set(i, acc);
            }
        }
    }

    private static void shortCircuitTripleOrNested(KernelContext context, IntArray inA, IntArray inB, IntArray out) {
        int i = context.globalIdx;
        if (i < out.getSize()) {
            int a = inA.get(i);
            int b = inB.get(i);
            if (a <= 16 || b >= 24 || a == b) {
                int acc = 0;
                for (int k = 0; k < a; k++) {
                    acc += k;
                }
                out.set(i, acc);
            } else {
                out.set(i, -1000 - b);
            }
        }
    }

    private static IntArray runShortCircuit(String name, Task4<KernelContext, IntArray, IntArray, IntArray> kernel, IntArray inA, IntArray inB)
            throws TornadoExecutionPlanException {
        IntArray out = new IntArray(inA.getSize());
        out.init(Integer.MIN_VALUE);
        WorkerGrid worker = new WorkerGrid1D(inA.getSize());
        worker.setLocalWork(32, 1, 1);
        TaskGraph taskGraph = new TaskGraph("sc") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, inA, inB) //
                .task(name, kernel, new KernelContext(), inA, inB, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(new GridScheduler("sc." + name, worker)).execute();
        }
        return out;
    }

    /** Operands covering every combination of the two conditions: a in [0, 64), b in [0, 32). */
    private static IntArray[] shortCircuitOperands() {
        final int size = 64 * 32;
        IntArray inA = new IntArray(size);
        IntArray inB = new IntArray(size);
        for (int i = 0; i < size; i++) {
            inA.set(i, i / 32);
            inB.set(i, i % 32);
        }
        return new IntArray[] { inA, inB };
    }

    @Test
    public void testShortCircuitOrElse() throws TornadoExecutionPlanException {
        IntArray[] in = shortCircuitOperands();
        IntArray out = runShortCircuit("or", TestConditionals::shortCircuitOr, in[0], in[1]);
        for (int i = 0; i < out.getSize(); i++) {
            assertEquals("a=" + in[0].get(i) + " b=" + in[1].get(i), shortCircuitOrReference(in[0].get(i), in[1].get(i)), out.get(i));
        }
    }

    @Test
    public void testShortCircuitAndElse() throws TornadoExecutionPlanException {
        IntArray[] in = shortCircuitOperands();
        IntArray out = runShortCircuit("and", TestConditionals::shortCircuitAnd, in[0], in[1]);
        for (int i = 0; i < out.getSize(); i++) {
            assertEquals("a=" + in[0].get(i) + " b=" + in[1].get(i), shortCircuitAndReference(in[0].get(i), in[1].get(i)), out.get(i));
        }
    }

    @Test
    public void testShortCircuitTripleOrElse() throws TornadoExecutionPlanException {
        IntArray[] in = shortCircuitOperands();
        IntArray out = runShortCircuit("or3", TestConditionals::shortCircuitTripleOr, in[0], in[1]);
        for (int i = 0; i < out.getSize(); i++) {
            assertEquals("a=" + in[0].get(i) + " b=" + in[1].get(i), shortCircuitTripleOrReference(in[0].get(i), in[1].get(i)), out.get(i));
        }
    }

    @Test
    public void testShortCircuitOrElseNested() throws TornadoExecutionPlanException {
        IntArray[] in = shortCircuitOperands();
        IntArray out = runShortCircuit("orNested", TestConditionals::shortCircuitOrNested, in[0], in[1]);
        for (int i = 0; i < out.getSize(); i++) {
            int a = in[0].get(i);
            int b = in[1].get(i);
            assertEquals("a=" + a + " b=" + b, (a <= 32 || b >= 16) ? a * (a - 1) / 2 : -1000, out.get(i));
        }
    }

    @Test
    public void testShortCircuitAndElseNested() throws TornadoExecutionPlanException {
        IntArray[] in = shortCircuitOperands();
        IntArray out = runShortCircuit("andNested", TestConditionals::shortCircuitAndNested, in[0], in[1]);
        for (int i = 0; i < out.getSize(); i++) {
            assertEquals("a=" + in[0].get(i) + " b=" + in[1].get(i), shortCircuitAndReference(in[0].get(i), in[1].get(i)), out.get(i));
        }
    }

    @Test
    public void testShortCircuitTripleOrElseNested() throws TornadoExecutionPlanException {
        IntArray[] in = shortCircuitOperands();
        IntArray out = runShortCircuit("or3Nested", TestConditionals::shortCircuitTripleOrNested, in[0], in[1]);
        for (int i = 0; i < out.getSize(); i++) {
            assertEquals("a=" + in[0].get(i) + " b=" + in[1].get(i), shortCircuitTripleOrReference(in[0].get(i), in[1].get(i)), out.get(i));
        }
    }
}
