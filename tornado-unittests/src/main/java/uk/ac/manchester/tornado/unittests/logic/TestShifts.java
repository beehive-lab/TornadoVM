/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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

import java.util.Random;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Shift operators on negative values. Java's {@code >>>} shifts in zeros whatever the sign, while
 * {@code >>} on a signed type in C-like kernel languages (MSL, OpenCL C, CUDA C) is arithmetic, so
 * a backend must not lower {@code >>>} to a plain {@code >>} on a signed operand. Masking the
 * result does not always hide that: for {@code (x >>> 28) & 0xF} the compiler proves the shift is
 * already at most 15 and drops the mask.
 *
 * <p>
 * How to test?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.logic.TestShifts
 * </code>
 */
public class TestShifts extends TornadoTestBase {
    // CHECKSTYLE:OFF

    private static final int SIZE = 1024;

    // ---------------------------------------------------------------- kernels: int

    public static void ushrIntConstant(IntArray in, IntArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i) >>> 5);
        }
    }

    public static void ushrIntVariable(IntArray in, IntArray shifts, IntArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i) >>> shifts.get(i));
        }
    }

    public static void ushrIntTopNibble(IntArray in, IntArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, (in.get(i) >>> 28) & 0xF);
        }
    }

    /** The 4-bit dequantisation pattern that exposed the bug: all eight nibbles of each word. */
    public static void unpackNibbles(IntArray words, IntArray out) {
        for (@Parallel int i = 0; i < words.getSize(); i++) {
            int word = words.get(i);
            for (int k = 0; k < 8; k++) {
                out.set(i * 8 + k, (word >>> (4 * k)) & 0xF);
            }
        }
    }

    public static void shrIntVariable(IntArray in, IntArray shifts, IntArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i) >> shifts.get(i));
        }
    }

    // ---------------------------------------------------------------- kernels: long

    public static void ushrLongConstant(LongArray in, LongArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i) >>> 7);
        }
    }

    public static void ushrLongVariable(LongArray in, IntArray shifts, LongArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i) >>> shifts.get(i));
        }
    }

    public static void ushrLongTopNibble(LongArray in, LongArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, (in.get(i) >>> 60) & 0xFL);
        }
    }

    public static void shrLongVariable(LongArray in, IntArray shifts, LongArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i) >> shifts.get(i));
        }
    }

    // ---------------------------------------------------------------- inputs

    /** Mostly negative values (sign bit forced on for three in four), plus a few edge values. */
    private static IntArray negativeInts() {
        Random random = new Random(7);
        IntArray a = new IntArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            int v = random.nextInt();
            a.set(i, (i % 4 == 3) ? v : v | Integer.MIN_VALUE);
        }
        a.set(0, -1);
        a.set(1, Integer.MIN_VALUE);
        a.set(2, 0xF0000000);
        return a;
    }

    private static LongArray negativeLongs() {
        Random random = new Random(11);
        LongArray a = new LongArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            long v = random.nextLong();
            a.set(i, (i % 4 == 3) ? v : v | Long.MIN_VALUE);
        }
        a.set(0, -1L);
        a.set(1, Long.MIN_VALUE);
        a.set(2, 0xF000000000000000L);
        return a;
    }

    /** Shift amounts cycling through 0 .. width-1. */
    private static IntArray shiftAmounts(int width) {
        IntArray s = new IntArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            s.set(i, i % width);
        }
        return s;
    }

    private static void run(TaskGraph taskGraph) throws TornadoExecutionPlanException {
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
    }

    // ---------------------------------------------------------------- tests: int

    @Test
    public void testUnsignedShiftIntConstant() throws TornadoExecutionPlanException {
        IntArray in = negativeInts();
        IntArray out = new IntArray(SIZE);
        run(new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                .task("t0", TestShifts::ushrIntConstant, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < SIZE; i++) {
            assertEquals("i = " + i, in.get(i) >>> 5, out.get(i));
        }
    }

    @Test
    public void testUnsignedShiftIntVariable() throws TornadoExecutionPlanException {
        IntArray in = negativeInts();
        IntArray shifts = shiftAmounts(32);
        IntArray out = new IntArray(SIZE);
        run(new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in, shifts) //
                .task("t0", TestShifts::ushrIntVariable, in, shifts, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < SIZE; i++) {
            assertEquals("i = " + i, in.get(i) >>> shifts.get(i), out.get(i));
        }
    }

    @Test
    public void testUnsignedShiftIntTopNibble() throws TornadoExecutionPlanException {
        IntArray in = negativeInts();
        IntArray out = new IntArray(SIZE);
        run(new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                .task("t0", TestShifts::ushrIntTopNibble, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < SIZE; i++) {
            assertEquals("i = " + i, (in.get(i) >>> 28) & 0xF, out.get(i));
        }
    }

    @Test
    public void testUnpackNibbles() throws TornadoExecutionPlanException {
        IntArray words = negativeInts();
        IntArray out = new IntArray(SIZE * 8);
        run(new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, words) //
                .task("t0", TestShifts::unpackNibbles, words, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < SIZE; i++) {
            for (int k = 0; k < 8; k++) {
                assertEquals("word " + i + ", nibble " + k, (words.get(i) >>> (4 * k)) & 0xF, out.get(i * 8 + k));
            }
        }
    }

    @Test
    public void testSignedShiftIntVariable() throws TornadoExecutionPlanException {
        IntArray in = negativeInts();
        IntArray shifts = shiftAmounts(32);
        IntArray out = new IntArray(SIZE);
        run(new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in, shifts) //
                .task("t0", TestShifts::shrIntVariable, in, shifts, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < SIZE; i++) {
            assertEquals("i = " + i, in.get(i) >> shifts.get(i), out.get(i));
        }
    }

    // ---------------------------------------------------------------- tests: long

    @Test
    public void testUnsignedShiftLongConstant() throws TornadoExecutionPlanException {
        LongArray in = negativeLongs();
        LongArray out = new LongArray(SIZE);
        run(new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                .task("t0", TestShifts::ushrLongConstant, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < SIZE; i++) {
            assertEquals("i = " + i, in.get(i) >>> 7, out.get(i));
        }
    }

    @Test
    public void testUnsignedShiftLongVariable() throws TornadoExecutionPlanException {
        LongArray in = negativeLongs();
        IntArray shifts = shiftAmounts(64);
        LongArray out = new LongArray(SIZE);
        run(new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in, shifts) //
                .task("t0", TestShifts::ushrLongVariable, in, shifts, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < SIZE; i++) {
            assertEquals("i = " + i, in.get(i) >>> shifts.get(i), out.get(i));
        }
    }

    @Test
    public void testUnsignedShiftLongTopNibble() throws TornadoExecutionPlanException {
        LongArray in = negativeLongs();
        LongArray out = new LongArray(SIZE);
        run(new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                .task("t0", TestShifts::ushrLongTopNibble, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < SIZE; i++) {
            assertEquals("i = " + i, (in.get(i) >>> 60) & 0xFL, out.get(i));
        }
    }

    @Test
    public void testSignedShiftLongVariable() throws TornadoExecutionPlanException {
        LongArray in = negativeLongs();
        IntArray shifts = shiftAmounts(64);
        LongArray out = new LongArray(SIZE);
        run(new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in, shifts) //
                .task("t0", TestShifts::shrLongVariable, in, shifts, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < SIZE; i++) {
            assertEquals("i = " + i, in.get(i) >> shifts.get(i), out.get(i));
        }
    }
    // CHECKSTYLE:ON
}
