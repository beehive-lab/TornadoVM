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

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Assert;
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
 *     tornado-test -V uk.ac.manchester.tornado.unittests.numpromotion.Inlining
 * </code>
 */
public class Inlining extends TornadoTestBase {

    public static void bitwiseOr(byte[] result, byte[] input, byte[] elements) {
        result[0] |= input[1];
    }

    @Test
    public void test0() {

        byte[] elements = new byte[] { 4 };
        byte[] result = new byte[4];
        byte[] input = new byte[] { 127, 127, 127, 127, 1, 1, 1, 1 };

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, result, input, elements) //
                .task("t0", Inlining::bitwiseOr, result, input, elements) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

    }

    public static int b2i(byte v) {
        return (v < 0) ? (255 + v) : v;
    }

    public static void b2i(byte[] v, int[] result) {
        result[0] = (v[0] < 0) ? (255 + v[0]) : v[0];
    }

    public static int grey(byte r, byte g, byte b) {
        return ((29 * b2i(r) + 60 * b2i(g) + 11 * b2i(b)) / 100);
    }

    public static int grey(byte r) {
        return (b2i(r) / 100);
    }

    public static int grey(int r, int g, int b) {
        return (29 * b2i((byte) r) + 60 * b2i((byte) g) + 11 * b2i((byte) b)) / 100;
    }

    public static void rgbToGreyKernel(byte[] rgbBytes, int[] greyInts) {
        for (@Parallel int i = 0; i < greyInts.length; i++) {
            byte r = rgbBytes[i * 3];
            byte g = rgbBytes[i * 3 + 1];
            byte b = rgbBytes[i * 3 + 2];
            greyInts[i] = grey(r, g, b);
        }
    }

    public static void rgbToGreyKernelInt(int[] rgbBytes, int[] greyInts) {
        for (@Parallel int i = 0; i < greyInts.length; i++) {
            int r = rgbBytes[i * 3];
            int g = rgbBytes[i * 3 + 1];
            int b = rgbBytes[i * 3 + 2];
            greyInts[i] = grey(r, g, b);
        }
    }

    public static void rgbToGreyKernelSmall(byte[] rgbBytes, int[] greyInts) {
        for (@Parallel int i = 0; i < greyInts.length; i++) {
            byte r = rgbBytes[i];
            greyInts[i] = grey(r);
        }
    }

    @TornadoNotSupported
    public void rgbToGreyKernel() {

        final int size = 256;
        byte[] rgbBytes = new byte[size * 3];
        int[] greyInts = new int[size];
        int[] seq = new int[size];

        Random r = new Random();
        IntStream.range(0, rgbBytes.length).forEach(i -> {
            rgbBytes[i] = (byte) r.nextInt();
        });

        TaskGraph taskGraph = new TaskGraph("foo");
        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, rgbBytes) //
                .task("grey", Inlining::rgbToGreyKernel, rgbBytes, greyInts)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, greyInts);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        rgbToGreyKernel(rgbBytes, seq);

        for (int i = 0; i < seq.length; i++) {
            Assert.assertEquals(seq[i], greyInts[i]);
        }

    }

    @Test
    public void rgbToGreyKernelInt() {
        final int size = 256;
        int[] rgbBytes = new int[size * 3];
        int[] greyInts = new int[size];
        int[] seq = new int[size];
        IntStream.range(0, rgbBytes.length).forEach(i -> {
            rgbBytes[i] = 1;
        });

        TaskGraph taskGraph = new TaskGraph("foo");
        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, rgbBytes) //
                .task("grey", Inlining::rgbToGreyKernelInt, rgbBytes, greyInts)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, greyInts);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        rgbToGreyKernelInt(rgbBytes, seq);

        for (int i = 0; i < seq.length; i++) {
            Assert.assertEquals(seq[i], greyInts[i]);
        }

    }

    @TornadoNotSupported
    public void rgbToGreyKernelSmall() {
        final int size = 256;
        byte[] rgbBytes = new byte[size];
        int[] greyInts = new int[size];
        int[] seq = new int[size];
        Random r = new Random();
        IntStream.range(0, rgbBytes.length).forEach(i -> {
            rgbBytes[i] = (byte) -10;
        });

        TaskGraph taskGraph = new TaskGraph("s0");
        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, rgbBytes) //
                .task("t0", Inlining::rgbToGreyKernelSmall, rgbBytes, greyInts)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, greyInts);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        rgbToGreyKernelSmall(rgbBytes, seq);

        for (int i = 0; i < seq.length; i++) {
            Assert.assertEquals(seq[i], greyInts[i]);
        }
    }

    @TornadoNotSupported
    public void b2i() {
        byte[] rgbBytes = new byte[1];
        int[] greyInts = new int[1];
        int[] seq = new int[1];
        IntStream.range(0, rgbBytes.length).forEach(i -> {
            rgbBytes[i] = (byte) -10;
        });

        TaskGraph taskGraph = new TaskGraph("s0");
        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, rgbBytes) //
                .task("t0", Inlining::b2i, rgbBytes, greyInts)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, greyInts);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        b2i(rgbBytes, seq);

        for (int i = 0; i < seq.length; i++) {
            Assert.assertEquals(seq[i], greyInts[i]);
        }
    }
}
