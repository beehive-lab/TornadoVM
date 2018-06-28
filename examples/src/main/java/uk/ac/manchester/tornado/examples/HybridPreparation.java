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
 * Authors: Juan Fumero
 *
 */
package uk.ac.manchester.tornado.examples;

import java.util.Random;

import uk.ac.manchester.tornado.api.Parallel;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;

public class HybridPreparation {

    public static void saxpy(int alpha, int[] x, int[] y) {
        for (@Parallel int i = 0; i < y.length; i++) {
            y[i] = alpha * x[i];
        }
    }

    public static void vectorAddition(int[] x, int[] y, int[] z) {
        for (@Parallel int i = 0; i < y.length; i++) {
            z[i] = x[i] * y[i];
        }
    }

    public static void main(String[] args) {
        int numElements = 65536;
        int alpha = 2;

        int[] x = new int[numElements];
        int[] y = new int[numElements];
        int[] z = new int[numElements];

        Random r = new Random();

        for (int i = 0; i < numElements; i++) {
            x[i] = r.nextInt();
            y[i] = r.nextInt();
        }

        // @formatter:off
        TaskSchedule s0 = new TaskSchedule("s0")
                .task("t0", HybridPreparation::saxpy, alpha, x, y)
                .task("t1", HybridPreparation::vectorAddition, x, y, z)
                .streamOut(z);
        // @formatter:on

        s0.execute();

    }

}
