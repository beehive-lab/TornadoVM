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

import uk.ac.manchester.tornado.api.*;
import uk.ac.manchester.tornado.api.annotations.*;

public class ASaxpy {

    public static void saxpy(float alpha, float[] x, float[] y, float[] b) {
        for (@Parallel int i = 0; i < y.length; i++) {
            y[i] = alpha * x[i] + b[i];
        }
    }

    public static void main(String[] args) {
        int numElements = Integer.parseInt(args[0]);

        float alpha = 2f;

        float[] x = new float[numElements];
        float[] y = new float[numElements];
        float[] b = new float[numElements];
        float[] result = new float[numElements];

        for (int i = 0; i < numElements; i++) {
            x[i] = 450;
            y[i] = 0;
            b[i] = 20;
        }

        TaskSchedule s0 = new TaskSchedule("s0").task("t0", ASaxpy::saxpy, alpha, x, y, b).streamOut(y);

        for (int idx = 0; idx < 10; idx++) {
            s0.execute();
            saxpy(alpha, x, result, b);
            System.out.println("Checking result");
            boolean wrongResult = false;
            for (int i = 0; i < y.length; i++) {
                if (Math.abs(y[i] - (alpha * x[i] + b[i])) > 0.01) {
                    wrongResult = true;
                    break;
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
