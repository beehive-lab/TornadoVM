/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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

import org.junit.Test;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DDouble;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

/**
 * How to test?
 *
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.branching.TestLoopConditions
 * </code>
 *
 * [DISCLAIMER] These tests are legal in TornadoVM because in the if-conditions all threads are operating on the same data.
 */
public class TestLoopConditions extends TornadoTestBase {
    // CHECKSTYLE:OFF

    /**
     * This test is valid for TornadoVM because in the if-condition all threads are examining the same data.
     */
    public static void conditionBeforeSingleLoopReturn(IntArray a, IntArray b, IntArray c) {
        if (a.get(0) > b.get(0)) { // all threads examine the same values
            return;
        }
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    /**
     * This test is valid for TornadoVM because in the if-condition all threads are examining the same data.
     */
    public static void conditionBeforeSingleLoopComputation(IntArray a, IntArray b, IntArray c) {
        if (a.get(0) > b.get(0)) {
            b.set(0, a.get(0));
        }
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    public static void conditionInSingleLoopReturn(IntArray a, IntArray b, IntArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            if (a.get(0) > b.get(0)) {
                return;
            }
            c.set(i, a.get(i) + b.get(i));
        }
    }

    public static void conditionInSingleLoopComputation(IntArray a, IntArray b, IntArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            if (a.get(i) > b.get(i)) {
                b.set(i, a.get(i));
            }
            c.set(i, a.get(i) + b.get(i));
        }
    }

    /**
     * This test is valid for TornadoVM because in the if-condition all threads are examining the same data.
     */
    public static void conditionBeforeOuterForLoopReturn(Matrix2DDouble matrixA, Matrix2DDouble matrixB, Matrix2DDouble result) {
        if (matrixA.get(0, 0) != matrixB.get(1, 1)) {
            return;
        }
        for (@Parallel int i = 0; i < result.getNumRows(); i++) {
            for (@Parallel int j = 0; j < result.getNumColumns(); j++) {
                result.set(i, j, matrixA.get(i, j) + matrixB.get(i, j));
            }
        }
    }

    /**
     * This test is valid for TornadoVM because in the if-condition all threads are examining the same data.
     */
    public static void conditionBeforeOuterForLoopComputation(Matrix2DDouble matrixA, Matrix2DDouble matrixB, Matrix2DDouble result) {
        if (matrixA.get(0, 0) != matrixB.get(1, 1)) {
            result.set(0, 0, matrixA.get(0, 0) + matrixB.get(0, 0));
        }
        for (@Parallel int i = 0; i < result.getNumRows(); i++) {
            for (@Parallel int j = 0; j < result.getNumColumns(); j++) {
                result.set(i, j, matrixA.get(i, j) + matrixB.get(i, j));
            }
        }
    }

    /**
     * This test is valid for TornadoVM because in the if-condition all threads are examining the same data.
     */
    public static void conditionBeforeInnerForLoopReturn(Matrix2DDouble matrixA, Matrix2DDouble matrixB, Matrix2DDouble result) {
        for (@Parallel int i = 0; i < result.getNumRows(); i++) {
            if (matrixA.get(0, 0) != matrixB.get(1, 1)) {
                return;
            }
            for (@Parallel int j = 0; j < result.getNumColumns(); j++) {
                result.set(i, j, matrixA.get(i, j) + matrixB.get(i, j));
            }
        }
    }

    /**
     * This test is valid for TornadoVM because in the if-condition all threads are examining the same data.
     */
    public static void conditionBeforeInnerForLoopComputation(Matrix2DDouble matrixA, Matrix2DDouble matrixB, Matrix2DDouble result) {
        for (@Parallel int i = 0; i < result.getNumRows(); i++) {
            if (matrixA.get(0, 0) != matrixB.get(1, 1)) {
                result.set(0, 0, matrixA.get(0, 0) + matrixB.get(0, 0));
            }
            for (@Parallel int j = 0; j < result.getNumColumns(); j++) {
                result.set(i, j, matrixA.get(i, j) + matrixB.get(i, j));
            }
        }
    }

    /**
     * This test is valid for TornadoVM because in the if-condition all threads are examining the same data.
     */
    public static void conditionInInnerForLoopReturn(Matrix2DDouble matrixA, Matrix2DDouble matrixB, Matrix2DDouble result) {
        for (@Parallel int i = 0; i < result.getNumRows(); i++) {
            for (@Parallel int j = 0; j < result.getNumColumns(); j++) {
                if (matrixA.get(0, 0) != matrixB.get(1, 1)) {
                    return;
                }
                result.set(i, j, matrixA.get(i, j) + matrixB.get(i, j));
            }
        }
    }

    /**
     * This test is valid for TornadoVM because in the if-condition all threads are examining the same data.
     */
    public static void conditionInInnerForLoopComputation(Matrix2DDouble matrixA, Matrix2DDouble matrixB, Matrix2DDouble result) {
        for (@Parallel int i = 0; i < result.getNumRows(); i++) {
            for (@Parallel int j = 0; j < result.getNumColumns(); j++) {
                if (matrixA.get(0, 0) != matrixB.get(1, 1)) {
                    result.set(0, 0, matrixA.get(0, 0) + matrixB.get(0, 0));
                }
                result.set(i, j, matrixA.get(i, j) + matrixB.get(i, j));
            }
        }
    }

    /**
     * This test is valid for TornadoVM because in the if-condition all threads are examining the same data.
     */
    public static void conditionAfterInnerForLoopComputation(Matrix2DDouble matrixA, Matrix2DDouble matrixB, Matrix2DDouble result) {
        for (@Parallel int i = 0; i < result.getNumRows(); i++) {
            for (@Parallel int j = 0; j < result.getNumColumns(); j++) {
                result.set(i, j, matrixA.get(i, j) + matrixB.get(i, j));
            }
            if (matrixA.get(0, 0) != matrixB.get(1, 1)) {
                result.set(0, 0, matrixA.get(0, 0) + matrixB.get(0, 0));
            }
        }
    }

    /**
     * This test is valid for TornadoVM because in the if-condition all threads are examining the same data.
     */
    public static void conditionAfterOuterForLoopComputation(Matrix2DDouble matrixA, Matrix2DDouble matrixB, Matrix2DDouble result) {
        for (@Parallel int i = 0; i < result.getNumRows(); i++) {
            for (@Parallel int j = 0; j < result.getNumColumns(); j++) {
                result.set(i, j, matrixA.get(i, j) + matrixB.get(i, j));
            }
        }
        if (matrixA.get(0, 0) != matrixB.get(1, 1)) {
            result.set(0, 0, matrixA.get(0, 0) + matrixB.get(0, 0));
        }
    }

    public static Matrix2DDouble matrix2DDoubleInit(int X, int Y) {
        double[][] a = new double[X][Y];
        Random r = new Random();
        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                a[i][j] = r.nextDouble();
            }
        }
        return new Matrix2DDouble(a);
    }

    @Test
    public void testConditionBeforeSingleLoopReturn() throws TornadoExecutionPlanException {
        final int numberOfElements = 2048;
        IntArray a = new IntArray(numberOfElements);
        IntArray b = new IntArray(numberOfElements);
        IntArray c = new IntArray(numberOfElements);
        IntArray serial = new IntArray(numberOfElements);

        Random r = new Random();
        IntStream.range(0, numberOfElements).sequential().forEach(i -> {
            a.set(i, r.nextInt());
            b.set(i, r.nextInt());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestLoopConditions::conditionBeforeSingleLoopReturn, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        conditionBeforeSingleLoopReturn(a, b, serial);

        for (int i = 0; i < numberOfElements; i++) {
            assertEquals(serial.get(i), c.get(i));
        }
    }

    @Test
    public void testConditionBeforeSingleLoopComputation() throws TornadoExecutionPlanException {
        final int numberOfElements = 2048;
        IntArray a = new IntArray(numberOfElements);
        IntArray b = new IntArray(numberOfElements);
        IntArray c = new IntArray(numberOfElements);
        IntArray serial = new IntArray(numberOfElements);

        Random r = new Random();
        IntStream.range(0, numberOfElements).sequential().forEach(i -> {
            a.set(i, r.nextInt());
            b.set(i, r.nextInt());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestLoopConditions::conditionBeforeSingleLoopComputation, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        conditionBeforeSingleLoopComputation(a, b, serial);

        for (int i = 0; i < numberOfElements; i++) {
            assertEquals(serial.get(i), c.get(i));
        }
    }

    @Test
    public void testConditionInSingleLoopReturn() throws TornadoExecutionPlanException {
        final int numberOfElements = 2048;
        IntArray a = new IntArray(numberOfElements);
        IntArray b = new IntArray(numberOfElements);
        IntArray c = new IntArray(numberOfElements);
        IntArray serial = new IntArray(numberOfElements);

        Random r = new Random();
        IntStream.range(0, numberOfElements).sequential().forEach(i -> {
            a.set(i, r.nextInt());
            b.set(i, r.nextInt());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestLoopConditions::conditionInSingleLoopReturn, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        conditionInSingleLoopReturn(a, b, serial);

        for (int i = 0; i < numberOfElements; i++) {
            assertEquals(serial.get(i), c.get(i));
        }
    }

    @Test
    public void testConditionInSingleLoopComputation() throws TornadoExecutionPlanException {
        final int numberOfElements = 2048;
        IntArray a = new IntArray(numberOfElements);
        IntArray b = new IntArray(numberOfElements);
        IntArray c = new IntArray(numberOfElements);
        IntArray serial = new IntArray(numberOfElements);

        Random r = new Random();
        IntStream.range(0, numberOfElements).sequential().forEach(i -> {
            a.set(i, r.nextInt());
            b.set(i, r.nextInt());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestLoopConditions::conditionInSingleLoopComputation, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        conditionInSingleLoopComputation(a, b, serial);

        for (int i = 0; i < numberOfElements; i++) {
            assertEquals(serial.get(i), c.get(i));
        }
    }

    @Test
    public void testConditionBeforeOuterForLoopReturn() throws TornadoExecutionPlanException {
        int X = 128;
        int Y = 128;

        Matrix2DDouble matrixA = matrix2DDoubleInit(X, Y);
        Matrix2DDouble matrixB = matrix2DDoubleInit(X, Y);
        Matrix2DDouble matrixC = new Matrix2DDouble(X, Y);
        Matrix2DDouble matrixSerial = new Matrix2DDouble(X, Y);

        TaskGraph taskGraph = new TaskGraph("t0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB)
                .task("s0", TestLoopConditions::conditionBeforeOuterForLoopReturn, matrixA, matrixB, matrixC)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        conditionBeforeOuterForLoopReturn(matrixA, matrixB, matrixSerial);

        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                assertEquals(matrixSerial.get(i, j), matrixC.get(i, j), 0.01);
            }
        }
    }

    @Test
    public void testConditionBeforeOuterForLoopComputation() throws TornadoExecutionPlanException {
        int X = 128;
        int Y = 128;

        Matrix2DDouble matrixA = matrix2DDoubleInit(X, Y);
        Matrix2DDouble matrixB = matrix2DDoubleInit(X, Y);
        Matrix2DDouble matrixC = new Matrix2DDouble(X, Y);
        Matrix2DDouble matrixSerial = new Matrix2DDouble(X, Y);

        TaskGraph taskGraph = new TaskGraph("t0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB)
                .task("s0", TestLoopConditions::conditionBeforeOuterForLoopComputation, matrixA, matrixB, matrixC)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        conditionBeforeOuterForLoopComputation(matrixA, matrixB, matrixSerial);

        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                assertEquals(matrixSerial.get(i, j), matrixC.get(i, j), 0.01);
            }
        }
    }

    @Test
    public void testConditionBeforeInnerForLoopReturn() throws TornadoExecutionPlanException {
        int X = 128;
        int Y = 128;

        Matrix2DDouble matrixA = matrix2DDoubleInit(X, Y);
        Matrix2DDouble matrixB = matrix2DDoubleInit(X, Y);
        Matrix2DDouble matrixC = new Matrix2DDouble(X, Y);
        Matrix2DDouble matrixSerial = new Matrix2DDouble(X, Y);

        TaskGraph taskGraph = new TaskGraph("t0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB)
                .task("s0", TestLoopConditions::conditionBeforeInnerForLoopReturn, matrixA, matrixB, matrixC)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        conditionBeforeInnerForLoopReturn(matrixA, matrixB, matrixSerial);

        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                assertEquals(matrixSerial.get(i, j), matrixC.get(i, j), 0.01);
            }
        }
    }

    @Test
    public void testConditionBeforeInnerForLoopComputation() throws TornadoExecutionPlanException {
        int X = 128;
        int Y = 128;

        Matrix2DDouble matrixA = matrix2DDoubleInit(X, Y);
        Matrix2DDouble matrixB = matrix2DDoubleInit(X, Y);
        Matrix2DDouble matrixC = new Matrix2DDouble(X, Y);
        Matrix2DDouble matrixSerial = new Matrix2DDouble(X, Y);

        TaskGraph taskGraph = new TaskGraph("t0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB)
                .task("s0", TestLoopConditions::conditionBeforeInnerForLoopComputation, matrixA, matrixB, matrixC)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        conditionBeforeInnerForLoopComputation(matrixA, matrixB, matrixSerial);

        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                assertEquals(matrixSerial.get(i, j), matrixC.get(i, j), 0.01);
            }
        }
    }

    @Test
    public void testConditionInInnerForLoopReturn() throws TornadoExecutionPlanException {
        int X = 128;
        int Y = 128;

        Matrix2DDouble matrixA = matrix2DDoubleInit(X, Y);
        Matrix2DDouble matrixB = matrix2DDoubleInit(X, Y);
        Matrix2DDouble matrixC = new Matrix2DDouble(X, Y);
        Matrix2DDouble matrixSerial = new Matrix2DDouble(X, Y);

        TaskGraph taskGraph = new TaskGraph("t0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB)
                .task("s0", TestLoopConditions::conditionInInnerForLoopReturn, matrixA, matrixB, matrixC)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        conditionInInnerForLoopReturn(matrixA, matrixB, matrixSerial);

        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                assertEquals(matrixSerial.get(i, j), matrixC.get(i, j), 0.01);
            }
        }
    }

    @Test
    public void testConditionInInnerForLoopComputation() throws TornadoExecutionPlanException {
        int X = 128;
        int Y = 128;

        Matrix2DDouble matrixA = matrix2DDoubleInit(X, Y);
        Matrix2DDouble matrixB = matrix2DDoubleInit(X, Y);
        Matrix2DDouble matrixC = new Matrix2DDouble(X, Y);
        Matrix2DDouble matrixSerial = new Matrix2DDouble(X, Y);

        TaskGraph taskGraph = new TaskGraph("t0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB)
                .task("s0", TestLoopConditions::conditionInInnerForLoopComputation, matrixA, matrixB, matrixC)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        conditionInInnerForLoopComputation(matrixA, matrixB, matrixSerial);

        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                assertEquals(matrixSerial.get(i, j), matrixC.get(i, j), 0.01);
            }
        }
    }

    @Test
    public void testConditionAfterInnerForLoopComputation() throws TornadoExecutionPlanException {
        int X = 128;
        int Y = 128;

        Matrix2DDouble matrixA = matrix2DDoubleInit(X, Y);
        Matrix2DDouble matrixB = matrix2DDoubleInit(X, Y);
        Matrix2DDouble matrixC = new Matrix2DDouble(X, Y);
        Matrix2DDouble matrixSerial = new Matrix2DDouble(X, Y);

        TaskGraph taskGraph = new TaskGraph("t0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB)
                .task("s0", TestLoopConditions::conditionAfterInnerForLoopComputation, matrixA, matrixB, matrixC)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        conditionAfterInnerForLoopComputation(matrixA, matrixB, matrixSerial);

        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                assertEquals(matrixSerial.get(i, j), matrixC.get(i, j), 0.01);
            }
        }
    }

    @Test
    public void testConditionAfterOuterForLoopComputation() throws TornadoExecutionPlanException {
        int X = 128;
        int Y = 128;

        Matrix2DDouble matrixA = matrix2DDoubleInit(X, Y);
        Matrix2DDouble matrixB = matrix2DDoubleInit(X, Y);
        Matrix2DDouble matrixC = new Matrix2DDouble(X, Y);
        Matrix2DDouble matrixSerial = new Matrix2DDouble(X, Y);

        TaskGraph taskGraph = new TaskGraph("t0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB)
                .task("s0", TestLoopConditions::conditionAfterOuterForLoopComputation, matrixA, matrixB, matrixC)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        conditionAfterOuterForLoopComputation(matrixA, matrixB, matrixSerial);

        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                assertEquals(matrixSerial.get(i, j), matrixC.get(i, j), 0.01);
            }
        }
    }
    // CHECKSTYLE:ON

}
