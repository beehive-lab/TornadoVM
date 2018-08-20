/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Authors: Juan Fumero, Michalis Papadimitriou
 *
 */

package uk.ac.manchester.tornado.unittests.arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestArrays extends TornadoTestBase {

    public static void addAccumulator(int[] a, int value) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] += value;
        }
    }

    public static void vectorAddDouble(double[] a, double[] b, double[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void vectorAddFloat(float[] a, float[] b, float[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void vectorAddInteger(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void vectorAddLong(long[] a, long[] b, long[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void vectorAddShort(short[] a, short[] b, short[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = (short) (a[i] + b[i]);
        }
    }

    public static void vectorChars(char[] a, char[] b, char[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = 'f';
        }
    }

    public static void addChars(char[] a, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] += b[i];
        }
    }

    public static void initializeSequential(int[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = 1;
        }
    }

    public static void initializeToOneParallel(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = 1;
        }
    }

    @Test
    public void testWarmUp() {

        final int N = 128;
        int numKernels = 8;

        int[] data = new int[N];

        IntStream.range(0, N).parallel().forEach(idx -> {
            data[idx] = idx;
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        for (int i = 0; i < numKernels; i++) {
            s0.task("t" + i, TestArrays::addAccumulator, data, 1);
        }

        s0.streamOut(data).warmup();

        s0.execute();

        for (int i = 0; i < N; i++) {
            assertEquals(i + numKernels, data[i]);
        }
    }

    @Test
    public void testInitNotParallel() {
        final int N = 128;
        int[] data = new int[N];

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.task("t0", TestArrays::initializeSequential, data);
        s0.streamOut(data).warmup();
        s0.execute();

        for (int i = 0; i < N; i++) {
            assertEquals(1, data[i], 0.0001);
        }
    }

    @Test
    public void testInitParallel() {
        final int N = 128;
        int[] data = new int[N];

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.task("t0", TestArrays::initializeToOneParallel, data);
        s0.streamOut(data).warmup();
        s0.execute();

        for (int i = 0; i < N; i++) {
            assertEquals(1, data[i], 0.0001);
        }
    }

    @Test
    public void testAdd() {

        final int N = 128;
        int numKernels = 8;

        int[] data = new int[N];

        IntStream.range(0, N).parallel().forEach(idx -> {
            data[idx] = idx;
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        for (int i = 0; i < numKernels; i++) {
            s0.task("t" + i, TestArrays::addAccumulator, data, 1);
        }

        s0.streamOut(data).execute();

        for (int i = 0; i < N; i++) {
            assertEquals(i + numKernels, data[i], 0.0001);
        }
    }

    @Test
    public void testVectorAdditionDouble() {
        final int numElements = 4096;
        double[] a = new double[numElements];
        double[] b = new double[numElements];
        double[] c = new double[numElements];

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a[i] = (float) Math.random();
            b[i] = (float) Math.random();
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(a, b)
            .task("t0", TestArrays::vectorAddDouble, a, b, c)
            .streamOut(c)
            .execute();
        //@formatter:on

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i], 0.01);
        }
    }

    @Test
    public void testVectorAdditionFloat() {
        final int numElements = 4096;
        float[] a = new float[numElements];
        float[] b = new float[numElements];
        float[] c = new float[numElements];

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a[i] = (float) Math.random();
            b[i] = (float) Math.random();
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(a, b)
            .task("t0", TestArrays::vectorAddFloat, a, b, c)
            .streamOut(c)
            .execute();
        //@formatter:on

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i], 0.01f);
        }
    }

    @Test
    public void testVectorAdditionInteger() {
        final int numElements = 4096;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Random r = new Random();
        IntStream.range(0, numElements).sequential().forEach(i -> {
            a[i] = r.nextInt();
            b[i] = r.nextInt();
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(a, b)
            .task("t0", TestArrays::vectorAddInteger, a, b, c)
            .streamOut(c)
            .execute();
        //@formatter:on

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i]);
        }
    }

    @Test
    public void testVectorAdditionLong() {
        final int numElements = 4096;
        long[] a = new long[numElements];
        long[] b = new long[numElements];
        long[] c = new long[numElements];

        IntStream.range(0, numElements).parallel().forEach(i -> {
            a[i] = i;
            b[i] = i;
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(a, b)
            .task("t0", TestArrays::vectorAddLong, a, b, c)
            .streamOut(c)
            .execute();
        //@formatter:on

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i]);
        }
    }

    @Test
    public void testVectorAdditionShort() {
        final int numElements = 4096;
        short[] a = new short[numElements];
        short[] b = new short[numElements];
        short[] c = new short[numElements];

        IntStream.range(0, numElements).parallel().forEach(idx -> {
            a[idx] = 20;
            b[idx] = 34;
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(a, b)
            .task("t0", TestArrays::vectorAddShort, a, b, c)
            .streamOut(c)
            .execute();
        //@formatter:on

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i]);
        }
    }

    @Test
    public void testVectorChars() {
        final int numElements = 4096;
        char[] a = new char[numElements];
        char[] b = new char[numElements];
        char[] c = new char[numElements];

        IntStream.range(0, numElements).parallel().forEach(idx -> {
            a[idx] = 'a';
            b[idx] = '0';
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(a, b)
            .task("t0", TestArrays::vectorChars, a, b, c)
            .streamOut(c)
            .execute();
        //@formatter:on

        for (int i = 0; i < c.length; i++) {
            assertEquals('f', c[i]);
        }
    }

    @Test
    public void testVectorCharsMessage() {
        char[] a = new char[] { 'h', 'e', 'l', 'l', 'o', ' ', '\0', '\0', '\0', '\0', '\0', '\0' };
        int[] b = new int[] { 15, 10, 6, 0, -11, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(a, b)
            .task("t0", TestArrays::addChars, a, b)
            .streamOut(a)
            .execute();
        //@formatter:on

        assertEquals('w', a[0]);
        assertEquals('o', a[1]);
        assertEquals('r', a[2]);
        assertEquals('l', a[3]);
        assertEquals('d', a[4]);
        assertEquals('!', a[5]);

    }

}
