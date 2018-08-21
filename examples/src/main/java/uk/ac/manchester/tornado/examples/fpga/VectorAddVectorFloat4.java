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
 * Authors: Michalis Papadimitriou 
 */

package uk.ac.manchester.tornado.examples.fpga;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.types.Float4;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat4;

public class VectorAddVectorFloat4 {

    public static void saxpy(VectorFloat4 x, VectorFloat4 y, VectorFloat4 b) {
        for (@Parallel int i = 0; i < x.getLength(); i++) {
            y.set(i, Float4.add(x.get(i), b.get(i)));
        }
    }

    public static void main(String[] args) {
        int numElements = Integer.parseInt(args[0]);

        numElements = numElements / 4;

        VectorFloat4 xx = new VectorFloat4(numElements);
        VectorFloat4 yy = new VectorFloat4(numElements);
        VectorFloat4 bb = new VectorFloat4(numElements);
        VectorFloat4 results = new VectorFloat4(numElements);

        xx.fill(450f);
        yy.fill(0);
        bb.fill(20);

        TaskSchedule s0 = new TaskSchedule("s0").task("t0", VectorAddVectorFloat4::saxpy, xx, yy, bb).streamOut(yy);

        for (int idx = 0; idx < 10; idx++) {
            s0.execute();

            long start = System.nanoTime();
            saxpy(xx, results, bb);
            long stop = System.nanoTime();

            long seqTime = stop - start;
            System.out.println("Sequential time: " + seqTime + "\n");

            System.out.println("Checking result");
            boolean wrongResult = false;

            for (int i = 0; i < yy.getLength(); i++) {

                if (Math.abs(yy.get(i).getW() - results.get(i).getW()) > 0.1) {
                    wrongResult = true;
                } else if (Math.abs(yy.get(i).getX() - results.get(i).getX()) > 0.1) {
                    wrongResult = true;
                }
                if (Math.abs(yy.get(i).getZ() - results.get(i).getZ()) > 0.1) {
                    wrongResult = true;
                }
                if (Math.abs(yy.get(i).getY() - results.get(i).getY()) > 0.1) {
                    wrongResult = true;
                }
            }

            if (!wrongResult) {
                System.out.println("Test success");
            } else {
                System.out.println("Result is wrong");
            }
        }
    }
}
