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
package uk.ac.manchester.tornado.unittests.tasks;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

/**
 * Testing Tornado with multiple tasks in the same device. The
 * {@link TaskSchedule} contains more than one task.
 *
 */
public class TestMultipleTasksSingleDevice {

    public static void task0Initialization(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = 10;
        }
    }

    public static void task1Multiplication(int[] a, int alpha) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = a[i] * alpha;
        }
    }

    public static void task2Saxpy(int[] a, int[] b, int[] c, int alpha) {
        for (@Parallel int i = 0; i < a.length; i++) {
            c[i] = alpha * a[i] + b[i];
        }
    }

    public static void task3Copy(int[] a, int[] b, int alpha) {
        for (@Parallel int i = 0; i < a.length; i++) {
            b[i] = a[i];
        }
    }

    @Test
    public void testTwoTasks() {
        final int numElements = 1024;
        int[] a = new int[numElements];
        int[] b = new int[numElements];

        //@formatter:off
        new TaskSchedule("s0")
		    .streamIn(a, b)
		    .task("t0", TestMultipleTasksSingleDevice::task0Initialization, a)
		    .task("t1", TestMultipleTasksSingleDevice::task1Multiplication, a, 12)
		    .streamOut(a)
		    .execute();
        //@formatter:on

        for (int i = 0; i < a.length; i++) {
            assertEquals(120, a[i]);
        }
    }

    @Test
    public void testThreeTasks() {
        final int numElements = 1024;
        int[] a = new int[numElements];
        int[] b = new int[numElements];

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(a, b)
            .task("t0", TestMultipleTasksSingleDevice::task0Initialization, a)
            .task("t1", TestMultipleTasksSingleDevice::task1Multiplication, a, 12)
            .task("t3", TestMultipleTasksSingleDevice::task2Saxpy, a, a, b, 12)
            .streamOut(b)
            .execute();
        //@formatter:on

        int val = (12 * 120) + 120;
        for (int i = 0; i < a.length; i++) {
            assertEquals(val, b[i]);
        }
    }

    @Test
    public void testFourTasks() {
        final int numElements = 1024;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(a, b)
            .task("t0", TestMultipleTasksSingleDevice::task0Initialization, a)
            .task("t1", TestMultipleTasksSingleDevice::task1Multiplication, a, 12)
            .task("t2", TestMultipleTasksSingleDevice::task0Initialization, b)
            .task("t3", TestMultipleTasksSingleDevice::task2Saxpy, a, b, c, 12)
            .streamOut(c)
            .execute();
        //@formatter:on

        int val = (12 * 120) + 10;
        for (int i = 0; i < a.length; i++) {
            assertEquals(val, c[i]);
        }
    }

    @Test
    public void testFiveTasks() {
        final int numElements = 1024;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(a, b)
            .task("t0", TestMultipleTasksSingleDevice::task0Initialization, a)
            .task("t1", TestMultipleTasksSingleDevice::task1Multiplication, a, 12)
            .task("t2", TestMultipleTasksSingleDevice::task0Initialization, b)
            .task("t3", TestMultipleTasksSingleDevice::task2Saxpy, a, b, b, 12)
            .task("t4", TestMultipleTasksSingleDevice::task2Saxpy, b, a, c, 12)
            .streamOut(c)
            .execute();
        //@formatter:on

        int val = (12 * 120) + 10;
        val = (12 * val) + (120);
        for (int i = 0; i < a.length; i++) {
            assertEquals(val, c[i]);
        }
    }

}
