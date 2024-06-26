/*
 * Copyright (c) 2020, 2022, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.unittests.common.TornadoNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.numpromotion.Types
 * </code>
 */
public class Types extends TornadoTestBase {

    private static void b2b(ByteArray input, ByteArray output) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            output.set(i, input.get(i));
        }
    }

    private static void b2s(ByteArray input, ShortArray output) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            output.set(i, input.get(i));
        }
    }

    private static void b2i(ByteArray input, IntArray output) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            output.set(i, input.get(i));
        }
    }

    private static void b2l(ByteArray input, LongArray output) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            output.set(i, input.get(i));
        }
    }

    private static void i2l(IntArray input, LongArray output) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            output.set(i, input.get(i));
        }
    }

    private static void s2i(ShortArray input, IntArray output) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            output.set(i, input.get(i));
        }
    }

    private static void f2d(FloatArray input, DoubleArray output) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            output.set(i, input.get(i));
        }
    }

    private static void i2d(IntArray input, DoubleArray output) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            output.set(i, input.get(i));
        }
    }

    @Test
    public void testByteToByte() throws TornadoExecutionPlanException {
        int size = 512;
        ByteArray input = new ByteArray(size);
        ByteArray output = new ByteArray(size);
        ByteArray seq = new ByteArray(size);

        Random r = new Random(System.nanoTime());
        IntStream.range(0, input.getSize()).forEach(x -> input.set(x, (byte) r.nextInt(127)));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", Types::b2b, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        b2b(input, seq);
        for (int i = 0; i < seq.getSize(); i++) {
            assertEquals(seq.get(i), output.get(i));
        }
    }

    @Test
    public void testByteToByte2() throws TornadoExecutionPlanException {
        int size = 512;
        ByteArray input = new ByteArray(size);
        ByteArray output = new ByteArray(size);
        ByteArray seq = new ByteArray(size);

        Random r = new Random(System.nanoTime());
        IntStream.range(0, input.getSize()).forEach(x -> input.set(x, (byte) r.nextInt(127)));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", Types::b2b, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        b2b(input, seq);
        for (int i = 0; i < seq.getSize(); i++) {
            assertEquals(seq.get(i), output.get(i));
        }
    }

    @TornadoNotSupported
    public void testByteToInt() throws TornadoExecutionPlanException {
        int size = 512;
        ByteArray input = new ByteArray(size);
        IntArray output = new IntArray(size);
        IntArray seq = new IntArray(size);

        Random r = new Random(System.nanoTime());
        IntStream.range(0, input.getSize()).forEach(x -> input.set(x, (byte) r.nextInt(127)));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", Types::b2i, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        b2i(input, seq);
        for (int i = 0; i < seq.getSize(); i++) {
            assertEquals(seq.get(i), output.get(i));
        }
    }

    @Test
    public void testByteToShort() throws TornadoExecutionPlanException {
        int size = 512;
        ByteArray input = new ByteArray(size);
        ShortArray output = new ShortArray(size);
        ShortArray seq = new ShortArray(size);

        Random r = new Random(System.nanoTime());
        IntStream.range(0, input.getSize()).forEach(x -> input.set(x, (byte) r.nextInt(127)));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", Types::b2s, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        b2s(input, seq);
        for (int i = 0; i < seq.getSize(); i++) {
            assertEquals(seq.get(i), output.get(i));
        }
    }

    @Test
    public void testByteToLong() throws TornadoExecutionPlanException {
        int size = 512;
        ByteArray input = new ByteArray(size);
        LongArray output = new LongArray(size);
        LongArray seq = new LongArray(size);

        Random r = new Random(System.nanoTime());
        IntStream.range(0, input.getSize()).forEach(x -> input.set(x, (byte) r.nextInt(127)));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", Types::b2l, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        b2l(input, seq);
        for (int i = 0; i < seq.getSize(); i++) {
            assertEquals(seq.get(i), output.get(i));
        }
    }

    @Test
    public void testIntToLong() throws TornadoExecutionPlanException {
        int size = 512;
        IntArray input = new IntArray(size);
        LongArray output = new LongArray(size);
        LongArray seq = new LongArray(size);

        Random r = new Random(System.nanoTime());
        IntStream.range(0, input.getSize()).forEach(x -> input.set(x, r.nextInt()));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", Types::i2l, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        i2l(input, seq);
        for (int i = 0; i < seq.getSize(); i++) {
            assertEquals(seq.get(i), output.get(i));
        }
    }

    @TornadoNotSupported
    public void testShortToInt() throws TornadoExecutionPlanException {
        int size = 512;
        ShortArray input = new ShortArray(size);
        IntArray output = new IntArray(size);
        IntArray seq = new IntArray(size);

        Random r = new Random(System.nanoTime());
        IntStream.range(0, input.getSize()).forEach(x -> input.set(x, (short) r.nextInt(256)));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", Types::s2i, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
        s2i(input, seq);
        for (int i = 0; i < seq.getSize(); i++) {
            assertEquals(seq.get(i), output.get(i));
        }
    }

    @Test
    public void testFloatToDouble() throws TornadoExecutionPlanException {
        int size = 512;
        FloatArray input = new FloatArray(size);
        DoubleArray output = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        Random r = new Random(System.nanoTime());
        IntStream.range(0, input.getSize()).forEach(x -> input.set(x, r.nextFloat()));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", Types::f2d, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        f2d(input, seq);
        for (int i = 0; i < seq.getSize(); i++) {
            assertEquals(seq.get(i), output.get(i), 0.001f);
        }
    }

    @Test
    public void testIntToDouble() throws TornadoExecutionPlanException {
        int size = 512;
        IntArray input = new IntArray(size);
        DoubleArray output = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        Random r = new Random(System.nanoTime());
        IntStream.range(0, input.getSize()).forEach(x -> input.set(x, r.nextInt()));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", Types::i2d, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        i2d(input, seq);
        for (int i = 0; i < seq.getSize(); i++) {
            assertEquals(seq.get(i), output.get(i), 0.001f);
        }
    }
}
