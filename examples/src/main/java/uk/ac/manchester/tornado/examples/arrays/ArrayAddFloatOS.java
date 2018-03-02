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
package uk.ac.manchester.tornado.examples.arrays;

import java.util.Arrays;

import uk.ac.manchester.tornado.collections.math.SimpleMath;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;

public class ArrayAddFloatOS {

    public static void main(final String[] args) {
        final boolean useTornado = Boolean.parseBoolean(System.getProperty("useTornado", "False"));
        final boolean warmup = Boolean.parseBoolean(System.getProperty("warmup", "False"));
        final int numElements = (args.length == 1) ? Integer.parseInt(args[0])
                : 8192;
        final int iterations = Integer.parseInt(System.getProperty("iterations", "1"));
        final float[] a = new float[numElements];
        final float[] b = new float[numElements];
        final float[] c = new float[numElements];

        Arrays.fill(a, 3);
        Arrays.fill(b, 2);
        Arrays.fill(c, 0);

        //@formatter:off
        TaskSchedule s0 = new TaskSchedule("s0")
                .task("t0", SimpleMath::vectorAdd, a, b, c)
                .streamOut(c);
        //@formatter:on

        if (useTornado && warmup) {
            s0.warmup();
        }

        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {

            if (useTornado) {
                s0.execute();
            } else {
                SimpleMath.vectorAdd(a, b, c);
            }
        }
        long stop = System.nanoTime();

        /*
         * Check to make sure result is correct
         */
        int errors = 0;
        for (final float value : c) {
            if (value != 5f) {
                errors++;
            }
        }

        if (errors > 0) {
            System.out.printf("Invalid result: %d errors\n", errors);
            return;
        }

        final double elapsed = (stop - start) * 1e-9;
        final long bytesTransfered = ((numElements * 4)) * 3 * iterations;

        System.out.printf("result: %f, %d\n", elapsed, bytesTransfered);

        if (useTornado) {
            s0.dumpEvents();
        }
    }
}
