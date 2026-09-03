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
package uk.ac.manchester.tornado.unittests.logic;

import static org.junit.Assert.assertEquals;

import java.util.stream.IntStream;

import org.junit.Ignore;
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
 * <p>
 * How to test?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.logic.TestLogic
 * </code>
 */
public class TestLogic extends TornadoTestBase {
    // CHECKSTYLE:OFF

    public static void logic01(IntArray data, IntArray output) {
        for (@Parallel int i = 0; i < data.getSize(); i++) {
            output.set(i, data.get(i) & data.get(i) - 1);
        }
    }

    public static void logic02(IntArray data, IntArray output) {
        for (@Parallel int i = 0; i < data.getSize(); i++) {
            output.set(i, data.get(i) | data.get(i) - 1);
        }
    }

    public static void logic03(IntArray data, IntArray output) {
        for (@Parallel int i = 0; i < data.getSize(); i++) {
            output.set(i, data.get(i) ^ data.get(i) - 1);
        }
    }

    public static void logic04(IntArray data, IntArray output) {
        for (@Parallel int i = 0; i < data.getSize(); i++) {
            int value = data.get(i);
            if ((value & (value - 1)) != 0) {

                int condition = (value & (value - 1));
                while (condition != 0) {
                    value &= value - 1;
                    condition = (value & (value - 1));
                }
            }
            output.set(i, value);
        }
    }

    // Short-circuit && must skip the divide when the guard is false -- if the compiler ever
    // evaluated both operands eagerly, index 0 (divisor 0) would trap/produce garbage.
    public static void logicShortCircuit(IntArray a, IntArray b, IntArray output) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            boolean safe = (b.get(i) != 0) && (a.get(i) / b.get(i) > 0);
            output.set(i, safe ? 1 : 0);
        }
    }

    public static void logicXorPattern(IntArray a, IntArray b, IntArray output) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            boolean result = (a.get(i) > 0) ^ (b.get(i) > 0);
            output.set(i, result ? 1 : 0);
        }
    }

    public static void logicNestedBoolean(IntArray a, IntArray b, IntArray output) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            boolean result = !(a.get(i) > 0 && b.get(i) > 0) || (a.get(i) == b.get(i));
            output.set(i, result ? 1 : 0);
        }
    }

    // A || (B && C) exhaustively over 16 neighbour counts x {DEAD, ALIVE}, the Game-of-Life
    // B3/S23 survival predicate as written in upstream Java source (OQ-17).
    public static void logicOrOfAnd(IntArray count, IntArray cell, IntArray output) {
        for (@Parallel int i = 0; i < count.getSize(); i++) {
            int c = count.get(i);
            int v = cell.get(i);
            boolean result = (c == 3) || ((c == 2) && (v == -1));
            output.set(i, result ? -1 : 0);
        }
    }

    // A || (B && C && D), i.e. A || ((B && C) && D): a three-way conjunction nested inside the
    // disjunction, so the inner AND-of-AND is itself a ShortCircuitOrNode nested two deep. OQ-17
    // follow-up.
    public static void logicOrOfAndAnd(IntArray count, IntArray cell, IntArray w, IntArray output) {
        for (@Parallel int i = 0; i < count.getSize(); i++) {
            int c = count.get(i);
            int v = cell.get(i);
            int ww = w.get(i);
            boolean result = (c == 3) || ((c == 2) && (v == -1) && (ww == 1));
            output.set(i, result ? -1 : 0);
        }
    }

    // A || (B && (C || D)): an OR nested inside the AND nested inside the outer OR - three
    // levels of ShortCircuitOrNode, mixing the two connectives. OQ-17 follow-up.
    public static void logicOrOfAndOr(IntArray count, IntArray cell, IntArray w, IntArray output) {
        for (@Parallel int i = 0; i < count.getSize(); i++) {
            int c = count.get(i);
            int v = cell.get(i);
            int ww = w.get(i);
            boolean result = (c == 3) || ((c == 2) && ((v == -1) || (ww == 1)));
            output.set(i, result ? -1 : 0);
        }
    }

    @Test
    public void testLogic01() throws TornadoExecutionPlanException {
        final int N = 1024;
        IntArray data = new IntArray(N);
        IntArray output = new IntArray(N);
        IntArray sequential = new IntArray(N);

        IntStream.range(0, data.getSize()).sequential().forEach(i -> data.set(i, i));

        TaskGraph taskGraph = new TaskGraph("taskGraph") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestLogic::logic01, data, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
        logic01(data, sequential);

        for (int i = 0; i < data.getSize(); i++) {
            assertEquals(sequential.get(i), output.get(i));
        }

    }

    @Test
    public void testLogic02() throws TornadoExecutionPlanException {
        final int N = 1024;
        IntArray data = new IntArray(N);
        IntArray output = new IntArray(N);
        IntArray sequential = new IntArray(N);

        IntStream.range(0, data.getSize()).sequential().forEach(i -> data.set(i, i));

        TaskGraph taskGraph = new TaskGraph("taskGraph") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestLogic::logic02, data, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        logic02(data, sequential);

        for (int i = 0; i < data.getSize(); i++) {
            assertEquals(sequential.get(i), output.get(i));
        }
    }

    @Test
    public void testLogic03() throws TornadoExecutionPlanException {
        final int N = 1024;
        IntArray data = new IntArray(N);
        IntArray output = new IntArray(N);
        IntArray sequential = new IntArray(N);

        IntStream.range(0, data.getSize()).sequential().forEach(i -> data.set(i, i));

        TaskGraph taskGraph = new TaskGraph("taskGraph") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestLogic::logic03, data, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        logic03(data, sequential);

        for (int i = 0; i < data.getSize(); i++) {
            assertEquals(sequential.get(i), output.get(i));
        }
    }

    @Ignore
    public void testLogic04() throws TornadoExecutionPlanException {
        final int N = 1024;
        IntArray data = new IntArray(N);
        IntArray output = new IntArray(N);
        IntArray sequential = new IntArray(N);

        IntStream.range(0, data.getSize()).sequential().forEach(i -> data.set(i, i));

        TaskGraph taskGraph = new TaskGraph("taskGraph") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestLogic::logic04, data, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        logic04(data, sequential);

        for (int i = 0; i < data.getSize(); i++) {
            assertEquals(sequential.get(i), output.get(i));
        }
    }

    @Test
    public void testLogicShortCircuitSideEffect() throws TornadoExecutionPlanException {
        final int N = 1024;
        IntArray a = new IntArray(N);
        IntArray b = new IntArray(N);
        IntArray output = new IntArray(N);
        IntArray sequential = new IntArray(N);

        IntStream.range(0, N).sequential().forEach(i -> {
            a.set(i, i);
            b.set(i, (i % 5 == 0) ? 0 : (i % 3) - 1); // every 5th element has divisor 0
        });

        TaskGraph taskGraph = new TaskGraph("taskGraph") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestLogic::logicShortCircuit, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
        logicShortCircuit(a, b, sequential);

        for (int i = 0; i < N; i++) {
            assertEquals(sequential.get(i), output.get(i));
        }
    }

    @Test
    public void testLogicXorPattern() throws TornadoExecutionPlanException {
        final int N = 1024;
        IntArray a = new IntArray(N);
        IntArray b = new IntArray(N);
        IntArray output = new IntArray(N);
        IntArray sequential = new IntArray(N);

        IntStream.range(0, N).sequential().forEach(i -> {
            a.set(i, i - 512);
            b.set(i, 512 - i);
        });

        TaskGraph taskGraph = new TaskGraph("taskGraph") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestLogic::logicXorPattern, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
        logicXorPattern(a, b, sequential);

        for (int i = 0; i < N; i++) {
            assertEquals(sequential.get(i), output.get(i));
        }
    }

    @Test
    public void testLogicNestedBoolean() throws TornadoExecutionPlanException {
        final int N = 1024;
        IntArray a = new IntArray(N);
        IntArray b = new IntArray(N);
        IntArray output = new IntArray(N);
        IntArray sequential = new IntArray(N);

        IntStream.range(0, N).sequential().forEach(i -> {
            a.set(i, i - 512);
            b.set(i, (i % 7 == 0) ? a.get(i) : i - 400);
        });

        TaskGraph taskGraph = new TaskGraph("taskGraph") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestLogic::logicNestedBoolean, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
        logicNestedBoolean(a, b, sequential);

        for (int i = 0; i < N; i++) {
            assertEquals(sequential.get(i), output.get(i));
        }
    }

    @Test
    public void testLogicOrOfAnd() throws TornadoExecutionPlanException {
        final int N = 256;
        IntArray count = new IntArray(N);
        IntArray cell = new IntArray(N);
        IntArray output = new IntArray(N);
        IntArray sequential = new IntArray(N);

        for (int i = 0; i < N; i++) {
            count.set(i, (i >> 1) & 0xf); // 0..15 neighbour counts
            cell.set(i, (i & 1) != 0 ? -1 : 0); // the only two cell encodings life produces
        }

        TaskGraph taskGraph = new TaskGraph("taskGraph") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, count, cell) //
                .task("t0", TestLogic::logicOrOfAnd, count, cell, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
        logicOrOfAnd(count, cell, sequential);

        for (int i = 0; i < N; i++) {
            assertEquals("index " + i + ": count=" + count.get(i) + " cell=" + cell.get(i), sequential.get(i), output.get(i));
        }
    }

    @Test
    public void testLogicOrOfAndAnd() throws TornadoExecutionPlanException {
        final int N = 64;
        IntArray count = new IntArray(N);
        IntArray cell = new IntArray(N);
        IntArray w = new IntArray(N);
        IntArray output = new IntArray(N);
        IntArray sequential = new IntArray(N);

        for (int i = 0; i < N; i++) {
            count.set(i, (i >> 2) & 0xf); // 0..15 neighbour counts
            cell.set(i, ((i >> 1) & 1) != 0 ? -1 : 0);
            w.set(i, i & 1);
        }

        TaskGraph taskGraph = new TaskGraph("taskGraph") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, count, cell, w) //
                .task("t0", TestLogic::logicOrOfAndAnd, count, cell, w, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
        logicOrOfAndAnd(count, cell, w, sequential);

        for (int i = 0; i < N; i++) {
            assertEquals("index " + i + ": count=" + count.get(i) + " cell=" + cell.get(i) + " w=" + w.get(i), sequential.get(i), output.get(i));
        }
    }

    @Test
    public void testLogicOrOfAndOr() throws TornadoExecutionPlanException {
        final int N = 64;
        IntArray count = new IntArray(N);
        IntArray cell = new IntArray(N);
        IntArray w = new IntArray(N);
        IntArray output = new IntArray(N);
        IntArray sequential = new IntArray(N);

        for (int i = 0; i < N; i++) {
            count.set(i, (i >> 2) & 0xf); // 0..15 neighbour counts
            cell.set(i, ((i >> 1) & 1) != 0 ? -1 : 0);
            w.set(i, i & 1);
        }

        TaskGraph taskGraph = new TaskGraph("taskGraph") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, count, cell, w) //
                .task("t0", TestLogic::logicOrOfAndOr, count, cell, w, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
        logicOrOfAndOr(count, cell, w, sequential);

        for (int i = 0; i < N; i++) {
            assertEquals("index " + i + ": count=" + count.get(i) + " cell=" + cell.get(i) + " w=" + w.get(i), sequential.get(i), output.get(i));
        }
    }
    // CHECKSTYLE:ON
}
