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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.examples;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.common.exceptions.TornadoRuntimeException;

public class BoundsCheck {

    /*
     * The following code generates an index out-of-bounds exception
     */
    public static void add(final int[] a, final int[] b, final int[] c) {
        for (int i = 0; i < a.length + 1; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void main(final String[] args) throws TornadoRuntimeException {

        final int numElements = 16;

        final int[] a = new int[numElements];
        final int[] b = new int[numElements];
        final int[] c = new int[numElements];

        Arrays.fill(a, 3);
        Arrays.fill(b, 2);
        Arrays.fill(c, 0);

        /*
         * First step is to create a reference to the method invocation This
         * involves finding the methods called and the arguments used in each
         * call.
         */
        TaskSchedule graph = new TaskSchedule("s0").task("t0", BoundsCheck::add, a, b, c).streamOut(c);

        /*
         * Calculate a (3) + b (2) = c (5)
         */
        graph.execute();

        /*
         * Check to make sure result is correct
         */
        for (final int value : c) {
            if (value != 5) {
                System.out.println("Invalid result");
            }
        }
    }
}
