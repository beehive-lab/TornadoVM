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

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.util.stream.IntStream;

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
 * Bit-manipulation intrinsics ({@code Integer}/{@code Math}) had zero coverage anywhere in the
 * suite prior to this file. {@code numberOfLeadingZeros}/{@code bitCount} ARE registered as
 * {@code InvocationPlugin}s on both CUDA and OpenCL; {@code numberOfTrailingZeros},
 * {@code highestOneBit}, and {@code lowestOneBit} have NO plugin registration either, but still
 * compile and run correctly -- their portable JDK bytecode fallback implementations (plain bit
 * arithmetic, no native-intrinsic dependency) get inlined and lowered fine without a dedicated
 * plugin. {@code Integer.reverse} is the one exception that genuinely fails to compile; see
 * {@code fails.BitOpsUnsupportedFail}. {@code Integer.divideUnsigned}/{@code Long.divideUnsigned}
 * were deliberately dropped from this file rather than tested: neither is registered as a plugin
 * either, but instead of bailing out cleanly they silently fall through to plain SIGNED division
 * and produce wrong numeric results with no exception -- a compiler correctness bug, not a
 * documentable restriction, so there's no clean assertion to write against it.
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

}
