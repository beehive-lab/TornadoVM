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

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Assert;
import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.numpromotion.Inlining
 * </code>
 */
public class Inlining extends TornadoTestBase {

    public static void bitwiseOr(ByteArray result, ByteArray input, ByteArray elements) {
        result.set(0, (byte) (result.get(0) | input.get(1)));
    }

    public static int b2i(byte v) {
        return (v < 0) ? (255 + v) : v;
    }

    public static void b2i(ByteArray v, IntArray result) {
        result.set(0, (v.get(0) < 0) ? (255 + v.get(0)) : v.get(0));
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

    public static void rgbToGreyKernel(ByteArray rgbBytes, IntArray greyInts) {
        for (@Parallel int i = 0; i < greyInts.getSize(); i++) {
            byte r = rgbBytes.get(i * 3);
            byte g = rgbBytes.get(i * 3 + 1);
            byte b = rgbBytes.get(i * 3 + 2);
            greyInts.set(i, grey(r, g, b));
        }
    }

    public static void rgbToGreyKernelInt(IntArray rgbBytes, IntArray greyInts) {
        for (@Parallel int i = 0; i < greyInts.getSize(); i++) {
            int r = rgbBytes.get(i * 3);
            int g = rgbBytes.get(i * 3 + 1);
            int b = rgbBytes.get(i * 3 + 2);
            greyInts.set(i, grey(r, g, b));
        }
    }

    public static void rgbToGreyKernelSmall(ByteArray rgbBytes, IntArray greyInts) {
        for (@Parallel int i = 0; i < greyInts.getSize(); i++) {
            byte r = rgbBytes.get(i);
            greyInts.set(i, grey(r));
        }
    }

    @Test
    public void test0() throws TornadoExecutionPlanException {

        ByteArray elements = new ByteArray(1);
        elements.init((byte) 4);
        ByteArray result = new ByteArray(4);
        ByteArray input = ByteArray.fromElements((byte) 127, (byte) 127, (byte) 127, (byte) 127, (byte) 1, (byte) 1, (byte) 1, (byte) 1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, result, input, elements) //
                .task("t0", Inlining::bitwiseOr, result, input, elements) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

    }

    @TornadoNotSupported
    public void rgbToGreyKernel() throws TornadoExecutionPlanException {

        final int size = 256;
        ByteArray rgbBytes = new ByteArray(size * 3);
        IntArray greyInts = new IntArray(size);
        IntArray seq = new IntArray(size);

        Random r = new Random();
        IntStream.range(0, rgbBytes.getSize()).forEach(i -> {
            rgbBytes.set(i, (byte) r.nextInt());
        });

        TaskGraph taskGraph = new TaskGraph("foo");
        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, rgbBytes) //
                .task("grey", Inlining::rgbToGreyKernel, rgbBytes, greyInts)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, greyInts);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        rgbToGreyKernel(rgbBytes, seq);

        for (int i = 0; i < seq.getSize(); i++) {
            Assert.assertEquals(seq.get(i), greyInts.get(i));
        }

    }

    @Test
    public void rgbToGreyKernelInt() throws TornadoExecutionPlanException {
        final int size = 256;
        IntArray rgbBytes = new IntArray(size * 3);
        IntArray greyInts = new IntArray(size);
        IntArray seq = new IntArray(size);
        IntStream.range(0, rgbBytes.getSize()).forEach(i -> {
            rgbBytes.set(i, 1);
        });

        TaskGraph taskGraph = new TaskGraph("foo");
        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, rgbBytes) //
                .task("grey", Inlining::rgbToGreyKernelInt, rgbBytes, greyInts)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, greyInts);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        rgbToGreyKernelInt(rgbBytes, seq);

        for (int i = 0; i < seq.getSize(); i++) {
            Assert.assertEquals(seq.get(i), greyInts.get(i));
        }

    }

    @TornadoNotSupported
    public void rgbToGreyKernelSmall() throws TornadoExecutionPlanException {
        final int size = 256;
        ByteArray rgbBytes = new ByteArray(size);
        IntArray greyInts = new IntArray(size);
        IntArray seq = new IntArray(size);
        Random r = new Random();
        IntStream.range(0, rgbBytes.getSize()).forEach(i -> {
            rgbBytes.set(i, (byte) -10);
        });

        TaskGraph taskGraph = new TaskGraph("s0");
        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, rgbBytes) //
                .task("t0", Inlining::rgbToGreyKernelSmall, rgbBytes, greyInts)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, greyInts);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        rgbToGreyKernelSmall(rgbBytes, seq);

        for (int i = 0; i < seq.getSize(); i++) {
            Assert.assertEquals(seq.get(i), greyInts.get(i));
        }
    }

    @TornadoNotSupported
    public void b2i() throws TornadoExecutionPlanException {
        ByteArray rgbBytes = new ByteArray(1);
        IntArray greyInts = new IntArray(1);
        IntArray seq = new IntArray(1);
        IntStream.range(0, rgbBytes.getSize()).forEach(i -> {
            rgbBytes.set(i, (byte) -10);
        });

        TaskGraph taskGraph = new TaskGraph("s0");
        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, rgbBytes) //
                .task("t0", Inlining::b2i, rgbBytes, greyInts)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, greyInts);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        b2i(rgbBytes, seq);

        for (int i = 0; i < seq.getSize(); i++) {
            Assert.assertEquals(seq.get(i), greyInts.get(i));
        }
    }
}
