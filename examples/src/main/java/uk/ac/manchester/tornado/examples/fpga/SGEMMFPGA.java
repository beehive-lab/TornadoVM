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
 *
 */
package uk.ac.manchester.tornado.examples.fpga;

import java.util.Random;

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;

public class SGEMMFPGA {

    public static void sgemm(final int A[], final int B[], final int C[], final int[] dims) {

        for (@Parallel int i = 0; i < dims[0]; i++) {
            for (@Parallel int j = 0; j < dims[0]; j++) {
                int sum = 0;
                for (int k = 0; k < dims[0]; k++) {
                    sum += A[(i * dims[0]) + k] * B[(k * dims[0]) + j];
                }
                C[(i * dims[0]) + j] = sum;
            }
        }

    }

    public static void main(String[] args) {
        int size = Integer.parseInt(args[0]);

        int m = size;
        int n = size;

        int[] a,b,c,result,dims;

        a = new int[m * n];
        b = new int[m * n];
        c = new int[m * n];
        result = new int[m * n];
        dims = new int[1];

        dims[0] = size;

        final Random random = new Random();

        for (int i = 0; i < m; i++) {
            a[i * (m + 1)] = 45;
        }

        for (int i = 0; i < m * n; i++) {
            b[i] = 1;
        }

        TaskSchedule t0 = new TaskSchedule("s0").task("t0", SGEMMFPGA::sgemm, a, b, c, dims).streamOut(c);

        t0.warmup();

        for (int y = 0; y < 10; y++) {

            t0.execute();

            t0.syncObject(c);

            System.out.println("Checking result");
            boolean wrongResult = false;
            long start = System.nanoTime();

            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    int sum = 0;
                    for (int k = 0; k < size; k++) {
                        sum += a[(i * size) + k] * b[(k * size) + j];
                    }
                    result[(i * size) + j] = sum;

                    if (result[(i * size) + j] != c[(i * size) + j]) {
                        wrongResult = true;
                        break;
                    }
                }
            }
            long end = System.nanoTime();
            System.out.println("Sequential execution time of iteration  is: " + (end - start) + " ns");

            if (!wrongResult) {
                System.out.println("Test success");
            } else {
                System.out.println("Result is wrong");
            }
        }
    }
}
