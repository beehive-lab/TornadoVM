/*
 * Copyright (c) 2020, 2022, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.unittests.common.TornadoNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado-test -V uk.ac.manchester.tornado.unittests.numpromotion.Types
 * </code>
 */
public class Types extends TornadoTestBase {

    private static void b2b(byte[] input, byte[] output) {
        for (@Parallel int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
    }

    private static void b2s(byte[] input, short[] output) {
        for (@Parallel int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
    }

    private static void b2i(byte[] input, int[] output) {
        for (@Parallel int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
    }

    private static void b2l(byte[] input, long[] output) {
        for (@Parallel int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
    }

    private static void i2l(int[] input, long[] output) {
        for (@Parallel int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
    }

    private static void s2i(short[] input, int[] output) {
        for (@Parallel int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
    }

    private static void f2d(float[] input, double[] output) {
        for (@Parallel int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
    }

    private static void i2d(int[] input, double[] output) {
        for (@Parallel int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
    }

    @Test
    public void testByteToByte() {
        int size = 512;
        byte[] input = new byte[size];
        byte[] output = new byte[size];
        byte[] seq = new byte[size];

        Random r = new Random(System.nanoTime());
        IntStream.range(0, input.length).forEach(x -> input[x] = (byte) r.nextInt(127));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", Types::b2b, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        b2b(input, seq);
        for (int i = 0; i < seq.length; i++) {
            assertEquals(seq[i], output[i]);
        }
    }

    @Test
    public void testByteToByte2() {
        int size = 512;
        byte[] input = new byte[size];
        byte[] output = new byte[size];
        byte[] seq = new byte[size];

        Random r = new Random(System.nanoTime());
        IntStream.range(0, input.length).forEach(x -> input[x] = (byte) r.nextInt(127));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", Types::b2b, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        b2b(input, seq);
        for (int i = 0; i < seq.length; i++) {
            assertEquals(seq[i], output[i]);
        }
    }

    @TornadoNotSupported
    public void testByteToInt() {
        int size = 512;
        byte[] input = new byte[size];
        int[] output = new int[size];
        int[] seq = new int[size];

        Random r = new Random(System.nanoTime());
        IntStream.range(0, input.length).forEach(x -> input[x] = (byte) r.nextInt(127));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", Types::b2i, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        b2i(input, seq);
        for (int i = 0; i < seq.length; i++) {
            assertEquals(seq[i], output[i]);
        }
    }

    @Test
    public void testByteToShort() {
        int size = 512;
        byte[] input = new byte[size];
        short[] output = new short[size];
        short[] seq = new short[size];

        Random r = new Random(System.nanoTime());
        IntStream.range(0, input.length).forEach(x -> input[x] = (byte) r.nextInt(127));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", Types::b2s, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        b2s(input, seq);
        for (int i = 0; i < seq.length; i++) {
            assertEquals(seq[i], output[i]);
        }
    }

    @Test
    public void testByteToLong() {
        int size = 512;
        byte[] input = new byte[size];
        long[] output = new long[size];
        long[] seq = new long[size];

        Random r = new Random(System.nanoTime());
        IntStream.range(0, input.length).forEach(x -> input[x] = (byte) r.nextInt(127));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", Types::b2l, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        b2l(input, seq);
        for (int i = 0; i < seq.length; i++) {
            assertEquals(seq[i], output[i]);
        }
    }

    @Test
    public void testIntToLong() {
        int size = 512;
        int[] input = new int[size];
        long[] output = new long[size];
        long[] seq = new long[size];

        Random r = new Random(System.nanoTime());
        IntStream.range(0, input.length).forEach(x -> input[x] = r.nextInt());

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", Types::i2l, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        i2l(input, seq);
        for (int i = 0; i < seq.length; i++) {
            assertEquals(seq[i], output[i]);
        }
    }

    @TornadoNotSupported
    public void testShortToInt() {
        int size = 512;
        short[] input = new short[size];
        int[] output = new int[size];
        int[] seq = new int[size];

        Random r = new Random(System.nanoTime());
        IntStream.range(0, input.length).forEach(x -> input[x] = (short) r.nextInt(256));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", Types::s2i, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        s2i(input, seq);
        for (int i = 0; i < seq.length; i++) {
            assertEquals(seq[i], output[i]);
        }
    }

    @Test
    public void testFloatToDouble() {
        int size = 512;
        float[] input = new float[size];
        double[] output = new double[size];
        double[] seq = new double[size];

        Random r = new Random(System.nanoTime());
        IntStream.range(0, input.length).forEach(x -> input[x] = r.nextFloat());

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", Types::f2d, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        f2d(input, seq);
        for (int i = 0; i < seq.length; i++) {
            assertEquals(seq[i], output[i], 0.001f);
        }
    }

    @Test
    public void testIntToDouble() {
        int size = 512;
        int[] input = new int[size];
        double[] output = new double[size];
        double[] seq = new double[size];

        Random r = new Random(System.nanoTime());
        IntStream.range(0, input.length).forEach(x -> input[x] = r.nextInt());

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", Types::i2d, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        i2d(input, seq);
        for (int i = 0; i < seq.length; i++) {
            assertEquals(seq[i], output[i], 0.001f);
        }
    }
}
