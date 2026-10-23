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
package uk.ac.manchester.tornado.unittests.numpromotion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Bit-manipulation methods of {@code Integer} and {@code Math}. {@code numberOfLeadingZeros} and
 * {@code bitCount} map to device built-ins; {@code numberOfTrailingZeros}, {@code highestOneBit},
 * {@code lowestOneBit} and {@code reverse} have none, and the compiler inlines the JDK's own
 * bit-arithmetic code for them. {@code Integer.divideUnsigned}/{@code Long.divideUnsigned} are not
 * covered here.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.numpromotion.TestBitManipulation
 * </code>
 */
public class TestBitManipulation extends TornadoTestBase {

    public static void floorDivMod(IntArray a, IntArray b, IntArray floorDivResult, IntArray floorModResult) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            floorDivResult.set(i, Math.floorDiv(a.get(i), b.get(i)));
            floorModResult.set(i, Math.floorMod(a.get(i), b.get(i)));
        }
    }

    public static void bitCounting(IntArray a, IntArray leadingZeros, IntArray bitCount) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            int value = a.get(i);
            leadingZeros.set(i, Integer.numberOfLeadingZeros(value));
            bitCount.set(i, Integer.bitCount(value));
        }
    }

    public static void bitEdges(IntArray a, IntArray trailingZeros, IntArray highestBit, IntArray lowestBit) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            int value = a.get(i);
            trailingZeros.set(i, Integer.numberOfTrailingZeros(value));
            highestBit.set(i, Integer.highestOneBit(value));
            lowestBit.set(i, Integer.lowestOneBit(value));
        }
    }

    public static void unsignedInts(IntArray a, IntArray b, IntArray out) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            int x = a.get(i);
            int y = b.get(i);
            out.set(4 * i, Integer.compareUnsigned(x, y));
            out.set(4 * i + 1, Integer.divideUnsigned(x, y));
            out.set(4 * i + 2, Integer.remainderUnsigned(x, y));
            out.set(4 * i + 3, Integer.rotateLeft(x, y));
        }
    }

    public static void unsignedLongs(LongArray a, LongArray b, LongArray out) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            long x = a.get(i);
            long y = b.get(i);
            out.set(4 * i, Long.compareUnsigned(x, y));
            out.set(4 * i + 1, Long.divideUnsigned(x, y));
            out.set(4 * i + 2, Long.remainderUnsigned(x, y));
            out.set(4 * i + 3, Long.reverse(x));
        }
    }

    public static void division(IntArray a, IntArray b, LongArray c, LongArray d, IntArray out, LongArray longOut) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            out.set(3 * i, a.get(i) / b.get(i));
            out.set(3 * i + 1, a.get(i) % b.get(i));
            out.set(3 * i + 2, Math.floorDiv(a.get(i), b.get(i)));
            longOut.set(2 * i, c.get(i) / d.get(i));
            longOut.set(2 * i + 1, c.get(i) % d.get(i));
        }
    }

    public static void reverseBits(IntArray a, IntArray out) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            out.set(i, Integer.reverse(a.get(i)));
        }
    }

    @Test
    public void testFloorDivFloorMod() throws TornadoExecutionPlanException {
        final int size = 128;
        IntArray a = new IntArray(size);
        IntArray b = new IntArray(size);
        Random r = new Random(5);
        IntStream.range(0, size).forEach(i -> {
            a.set(i, r.nextInt(2001) - 1000); // includes negatives, where floorDiv/floorMod differ from / and %
            int divisor = r.nextInt(200) - 100;
            b.set(i, divisor == 0 ? 1 : divisor);
        });
        IntArray floorDivResult = new IntArray(size);
        IntArray floorModResult = new IntArray(size);
        IntArray expectedDiv = new IntArray(size);
        IntArray expectedMod = new IntArray(size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestBitManipulation::floorDivMod, a, b, floorDivResult, floorModResult) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, floorDivResult, floorModResult);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        floorDivMod(a, b, expectedDiv, expectedMod);
        for (int i = 0; i < size; i++) {
            assertEquals(expectedDiv.get(i), floorDivResult.get(i));
            assertEquals(expectedMod.get(i), floorModResult.get(i));
        }
    }

    @Test
    public void testBitCountingIntrinsics() throws TornadoExecutionPlanException {
        final int size = 128;
        IntArray a = new IntArray(size);
        Random r = new Random(6);
        IntStream.range(0, size).forEach(i -> a.set(i, r.nextInt()));

        IntArray leadingZeros = new IntArray(size);
        IntArray bitCount = new IntArray(size);
        IntArray expectedLeadingZeros = new IntArray(size);
        IntArray expectedBitCount = new IntArray(size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestBitManipulation::bitCounting, a, leadingZeros, bitCount) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, leadingZeros, bitCount);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        bitCounting(a, expectedLeadingZeros, expectedBitCount);
        for (int i = 0; i < size; i++) {
            assertEquals(expectedLeadingZeros.get(i), leadingZeros.get(i));
            assertEquals(expectedBitCount.get(i), bitCount.get(i));
        }
    }

    @Test
    public void testBitEdgeIntrinsicsWithoutPluginRegistration() throws TornadoExecutionPlanException {
        final int size = 128;
        IntArray a = new IntArray(size);
        Random r = new Random(7);
        IntStream.range(0, size).forEach(i -> a.set(i, r.nextInt()));

        IntArray trailingZeros = new IntArray(size);
        IntArray highestBit = new IntArray(size);
        IntArray lowestBit = new IntArray(size);
        IntArray expectedTrailingZeros = new IntArray(size);
        IntArray expectedHighestBit = new IntArray(size);
        IntArray expectedLowestBit = new IntArray(size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestBitManipulation::bitEdges, a, trailingZeros, highestBit, lowestBit) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, trailingZeros, highestBit, lowestBit);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        bitEdges(a, expectedTrailingZeros, expectedHighestBit, expectedLowestBit);
        for (int i = 0; i < size; i++) {
            assertEquals(expectedTrailingZeros.get(i), trailingZeros.get(i));
            assertEquals(expectedHighestBit.get(i), highestBit.get(i));
            assertEquals(expectedLowestBit.get(i), lowestBit.get(i));
        }
    }


    /** Integer.reverse has no device intrinsic: the compiler inlines the JDK's own bit-twiddling code. */
    @Test
    public void testReverse() throws TornadoExecutionPlanException {
        final int size = 128;
        IntArray a = new IntArray(size);
        Random r = new Random(8);
        IntStream.range(0, size).forEach(i -> a.set(i, r.nextInt()));
        a.set(0, 0);
        a.set(1, -1);
        a.set(2, Integer.MIN_VALUE);
        a.set(3, 1);
        IntArray out = new IntArray(size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestBitManipulation::reverseBits, a, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(Integer.reverse(a.get(i)), out.get(i));
        }
    }

    /**
     * The JDK compares unsigned values as x + MIN_VALUE against y + MIN_VALUE. Those additions
     * overflow, which C treats as undefined: compiled naively, the device compiler drops them.
     */
    @Test
    public void testUnsignedInts() throws TornadoExecutionPlanException {
        final int size = 1024;
        IntArray a = new IntArray(size);
        IntArray b = new IntArray(size);
        Random r = new Random(9);
        int[] edges = { 0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE };
        for (int i = 0; i < size; i++) {
            a.set(i, i < 25 ? edges[i % 5] : r.nextInt());
            int y = i < 25 ? edges[i / 5] : r.nextInt();
            b.set(i, y == 0 ? 3 : y);
        }
        IntArray out = new IntArray(4 * size);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestBitManipulation::unsignedInts, a, b, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
        IntArray expected = new IntArray(4 * size);
        unsignedInts(a, b, expected);
        for (int i = 0; i < 4 * size; i++) {
            assertEquals(expected.get(i), out.get(i), "element " + i);
        }
    }

    @Test
    public void testUnsignedLongs() throws TornadoExecutionPlanException {
        final int size = 1024;
        LongArray a = new LongArray(size);
        LongArray b = new LongArray(size);
        Random r = new Random(10);
        long[] edges = { 0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE };
        for (int i = 0; i < size; i++) {
            a.set(i, i < 25 ? edges[i % 5] : r.nextLong());
            long y = i < 25 ? edges[i / 5] : r.nextLong();
            b.set(i, y == 0 ? 3 : y);
        }
        LongArray out = new LongArray(4 * size);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestBitManipulation::unsignedLongs, a, b, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
        LongArray expected = new LongArray(4 * size);
        unsignedLongs(a, b, expected);
        for (int i = 0; i < 4 * size; i++) {
            assertEquals(expected.get(i), out.get(i), "element " + i);
        }
    }

    /** MIN_VALUE / -1 is MIN_VALUE and MIN_VALUE % -1 is 0 in Java; C leaves both undefined. */
    @Test
    public void testDivisionByMinusOne() throws TornadoExecutionPlanException {
        final int size = 256;
        IntArray a = new IntArray(size);
        IntArray b = new IntArray(size);
        LongArray c = new LongArray(size);
        LongArray d = new LongArray(size);
        Random r = new Random(11);
        for (int i = 0; i < size; i++) {
            boolean edge = i % 4 == 0;
            a.set(i, edge ? Integer.MIN_VALUE : r.nextInt());
            b.set(i, edge || i % 4 == 1 ? -1 : r.nextInt(2001) - 1000 | 1);
            c.set(i, edge ? Long.MIN_VALUE : r.nextLong());
            d.set(i, edge || i % 4 == 1 ? -1 : r.nextInt(2001) - 1000 | 1);
        }
        IntArray out = new IntArray(3 * size);
        LongArray longOut = new LongArray(2 * size);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b, c, d) //
                .task("t0", TestBitManipulation::division, a, b, c, d, out, longOut) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out, longOut);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
        IntArray expected = new IntArray(3 * size);
        LongArray expectedLong = new LongArray(2 * size);
        division(a, b, c, d, expected, expectedLong);
        for (int i = 0; i < 3 * size; i++) {
            assertEquals(expected.get(i), out.get(i), "int element " + i);
        }
        for (int i = 0; i < 2 * size; i++) {
            assertEquals(expectedLong.get(i), longOut.get(i), "long element " + i);
        }
    }
}
