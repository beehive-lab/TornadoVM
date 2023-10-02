/*
 * Copyright (c) 2013-2022, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package uk.ac.manchester.tornado.unittests.branching;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to test?
 *
 * <code>
 *     tornado-test -V --fast uk.ac.manchester.tornado.unittests.branching.TestConditionals
 * </code>
 */
public class TestConditionals extends TornadoTestBase {

    public static void ifStatement(int[] a) {
        if (a[0] > 1) {
            a[0] = 10;
        }
    }

    public static void ifElseStatement(int[] a) {
        if (a[0] == 1) {
            a[0] = 5;
        } else {
            a[0] = 10;
        }
    }

    public static void nestedIfElseStatement(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            if (a[i] > 100) {
                if (a[i] > 200) {
                    a[i] = 5;
                } else {
                    a[i] = 10;
                }
                a[i] += 20;
            } else {
                a[i] = 2;
            }
        }
    }

    public static void switchStatement(int[] a) {
        int value = a[0];
        switch (value) {
            case 10:
                a[0] = 5;
                break;
            case 20:
                a[0] = 10;
                break;
            default:
                a[0] = 20;
        }
    }

    public static void switchStatement2(int[] a) {
        int value = a[0];
        switch (value) {
            case 10:
                a[0] = 5;
                break;
            case 20:
                a[0] = 10;
                break;
        }
    }

    public static void switchStatement3(int[] a) {
        for (int i = 0; i < a.length; i++) {
            int value = a[i];
            switch (value) {
                case 10:
                    a[i] = 5;
                    break;
                case 20:
                    a[i] = 10;
                    break;
            }
        }
    }

    public static void switchStatement4(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            int value = a[i];
            switch (value) {
                case 10:
                    a[i] = 5;
                    break;
                case 20:
                    a[i] = 10;
                    break;
            }
        }
    }

    public static void switchStatement5(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            int value = a[i];
            switch (value) {
                case 12:
                    a[i] = 5;
                    break;
                case 22:
                    a[i] = 10;
                    break;
                case 42:
                    a[i] = 30;
                    break;
            }
            a[i] *= 2;
        }
    }

    public static void switchStatement6(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            int value = a[i];
            switch (value) {
                case 12:
                case 22:
                    a[i] = 10;
                    break;
                case 42:
                    a[i] = 30;
                    break;
            }
        }
    }

    public static void ternaryCondition(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = (a[i] == 20) ? 10 : 5;
        }
    }

    public static void ternaryComplexCondition(int[] a, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            for (int x = 0; x < a.length; x++) {
                if (i == a.length) {
                    a[x] = (a[x] == 20) ? a[x] + b[x] : 5;
                }
            }
        }
    }

    public static void ternaryComplexCondition2(int[] a, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = (a[i] == 20) ? a[i] + b[i] : 5;
        }
    }

    public static void integerTestMove(int[] output, int dimensionSize) {
        for (@Parallel int i = 0; i < dimensionSize; i++) {
            for (@Parallel int j = 0; j < dimensionSize; j++) {
                if ((i % 2 == 0) & (j % 2 == 0)) {
                    output[i + j * dimensionSize] = 10;
                } else {
                    output[i + j * dimensionSize] = -1;
                }
            }
        }
    }

    @Test
    public void testIfStatement() {
        final int size = 10;
        int[] a = new int[size];
        Arrays.fill(a, 5);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::ifStatement, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        assertEquals(10, a[0]);
    }

    @Test
    public void testIfElseStatement() {
        final int size = 10;
        int[] a = new int[size];
        Arrays.fill(a, 5);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::ifElseStatement, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        assertEquals(10, a[0]);
    }

    @Test
    public void testNestedIfElseStatement() {
        final int size = 128;
        int[] a = new int[size];
        int[] serial = new int[size];

        IntStream.range(0, size).forEach(i -> {
            a[i] = 50;
            serial[i] = a[i];
        });

        nestedIfElseStatement(serial);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::nestedIfElseStatement, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        assertArrayEquals(serial, a);
    }

    @Test
    public void testSwitch() {

        final int size = 10;
        int[] a = new int[size];

        Arrays.fill(a, 20);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::switchStatement, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        assertEquals(10, a[0]);
    }

    @Test
    public void testSwitchDefault() {

        final int size = 10;
        int[] a = new int[size];

        Arrays.fill(a, 23);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::switchStatement, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        assertEquals(20, a[0]);
    }

    @Test
    public void testSwitch2() {

        final int size = 10;
        int[] a = new int[size];

        Arrays.fill(a, 20);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::switchStatement2, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        assertEquals(10, a[0]);
    }

    @Test
    public void testSwitch3() {

        final int size = 10;
        int[] a = new int[size];

        Arrays.fill(a, 20);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::switchStatement3, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int value : a) {
            assertEquals(10, value);
        }
    }

    @Test
    public void testSwitch4() {

        final int size = 10;
        int[] a = new int[size];

        Arrays.fill(a, 20);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::switchStatement4, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int value : a) {
            assertEquals(10, value);
        }
    }

    @Test
    public void testSwitch5() {
        final int size = 10;
        int[] a = new int[size];

        Arrays.fill(a, 12);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::switchStatement5, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int value : a) {
            assertEquals(10, value);
        }
    }

    @Test
    public void testTernaryCondition() {

        final int size = 10;
        int[] a = new int[size];

        Arrays.fill(a, 20);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::ternaryCondition, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int value : a) {
            assertEquals(10, value);
        }
    }

    @Test
    public void testComplexTernaryCondition() {

        final int size = 8192;
        int[] a = new int[size];
        int[] b = new int[size];

        Arrays.fill(a, 20);
        Arrays.fill(b, 30);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::ternaryComplexCondition, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int value : a) {
            assertEquals(20, value);
        }
    }

    @Test
    public void testComplexTernaryCondition2() {
        final int size = 8192;
        int[] a = new int[size];
        int[] b = new int[size];

        Arrays.fill(a, 20);
        Arrays.fill(b, 30);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::ternaryComplexCondition2, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int value : a) {
            assertEquals(50, value);
        }
    }

    @Test
    public void testSwitch6() {
        final int size = 8192;
        int[] a = new int[size];

        Arrays.fill(a, 42);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::switchStatement6, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int value : a) {
            assertEquals(30, value);
        }
    }

    @Test
    public void testSwitch7() {
        final int size = 8192;
        int[] a = new int[size];

        Arrays.fill(a, 12);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::switchStatement6, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int value : a) {
            assertEquals(10, value);
        }
    }

    @Test
    public void testSwitch8() {
        final int size = 8192;
        int[] a = new int[size];

        Arrays.fill(a, 22);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConditionals::switchStatement6, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int value : a) {
            assertEquals(10, value);
        }
    }

    @Test
    public void testIntegerTestMove() {
        final int size = 1024;
        int[] output = new int[size * size];
        int[] sequential = new int[size * size];

        IntStream.range(0, sequential.length).sequential().forEach(i -> sequential[i] = i);
        IntStream.range(0, output.length).sequential().forEach(i -> output[i] = i);

        TaskGraph taskGraph = new TaskGraph("s0");

        taskGraph.task("t0", TestConditionals::integerTestMove, output, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        integerTestMove(sequential, size);

        for (int i = 0; i < size * size; i++) {
            assertEquals(sequential[i], output[i]);
        }
    }
}
