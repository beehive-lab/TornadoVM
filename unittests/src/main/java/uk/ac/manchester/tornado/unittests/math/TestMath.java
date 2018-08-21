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

package uk.ac.manchester.tornado.unittests.math;

import static org.junit.Assert.assertArrayEquals;

import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestMath extends TornadoTestBase {

    public static void testCos(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = Math.cos(a[i]);
        }
    }

    @Test
    public void testMathCos() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestMath::testCos, data).streamOut(data).execute();

        testCos(seq);

        assertArrayEquals(data, seq, 0.01);

    }

    public static void testLog(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = Math.log(a[i]);
        }
    }

    @Test
    public void testMathLog() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestMath::testLog, data).streamOut(data).execute();

        testLog(seq);

        assertArrayEquals(data, seq, 0.01);

    }

    public static void testSqrt(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = Math.sqrt(a[i]);
        }
    }

    @Test
    public void testMathSqrt() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestMath::testSqrt, data).streamOut(data).execute();

        testSqrt(seq);

        assertArrayEquals(data, seq, 0.01);

    }

    public static void testExp(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = Math.exp(a[i]);
        }
    }

    @Test
    public void testMathExp() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestMath::testExp, data).streamOut(data).execute();

        testExp(seq);

        assertArrayEquals(data, seq, 0.01);

    }

    public static void testExpFloat(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = (float) Math.exp(a[i]);
        }
    }

    @Test
    public void testMathExpFloat() {
        final int size = 128;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestMath::testExpFloat, data).streamOut(data).execute();

        testExpFloat(seq);

        assertArrayEquals(data, seq, 0.01f);

    }
}
