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

package uk.ac.manchester.tornado.unittests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.exceptions.Debug;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestHello extends TornadoTestBase {

    private static void printHello(final int n) {
        for (@Parallel int i = 0; i < n; i++) {
            Debug.printf("hello\n");
        }
    }

    public static void add(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public void compute(int[] a, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            b[i] = a[i] * 2;
        }
    }

    @Test
    public void testHello() {
        TaskSchedule task = new TaskSchedule("s0").task("t0", TestHello::printHello, 8);
        assertNotNull(task);

        try {
            task.execute();
            assertTrue("Task was executed.", true);
        } catch (Exception e) {
            assertTrue("Task was not executed.", false);
        }
    }

    @Test
    public void testVectorAddition() {
        int numElements = 16;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(a, 1);
        Arrays.fill(b, 2);

        // @formatter:off
		new TaskSchedule("s0")
		    .task("t0", TestHello::add, a, b, c)
		    .streamOut(c)
		    .execute();
		// @formatter:on

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i], 0.001);
        }
    }

    @Test
    public void testSimpleCompute() {
        int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];

        Arrays.fill(a, 10);

        TestHello t = new TestHello();

        //@formatter:off
        new TaskSchedule("s0")
            .task("t0", t::compute, a, b)
            .streamOut(b)
            .execute();
        //@formatter:on

        for (int i = 0; i < b.length; i++) {
            assertEquals(a[i] * 2, b[i]);
        }
    }

}
