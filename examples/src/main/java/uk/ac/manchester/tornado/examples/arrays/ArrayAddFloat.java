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
import uk.ac.manchester.tornado.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;

public class ArrayAddFloat {

    public static void main(final String[] args) {
        final int numElements = (args.length == 1) ? Integer.parseInt(args[0])
                : 8192;
        final int iterations = 1;
        final float[] a = new float[numElements];
        final float[] b = new float[numElements];
        final float[] c = new float[numElements];

        Arrays.fill(a, 3);
        Arrays.fill(b, 2);
        Arrays.fill(c, 0);

        //@formatter:off
        final TaskSchedule schedule = new TaskSchedule("s0")
                .task("t0", SimpleMath::vectorAdd, a, b, c)
                .streamOut(c);
        //@formatter:on

        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            schedule.execute();
        }
        long stop = System.nanoTime();

        double elapsed = (stop - start) * 1e-9;
        double megaBytes = (((double) numElements * 4)) * 3 * iterations;
        double bw = megaBytes / elapsed;

        System.out.printf("Overall  : time = %f seconds, bw = %s\n", elapsed,
                RuntimeUtilities.formatBytesPerSecond(bw));

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
        }
    }
}
