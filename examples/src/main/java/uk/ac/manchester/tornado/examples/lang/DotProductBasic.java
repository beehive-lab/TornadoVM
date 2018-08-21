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
package uk.ac.manchester.tornado.examples.lang;

import uk.ac.manchester.tornado.api.TaskSchedule;

public class DotProductBasic {

    public static float[] mult3(int n, float[] a, float[] b) {
        final float[] c = new float[n];
        for (int i = 0; i < n; i++) {
            c[i] = a[i] * b[i];
        }
        return c;
    }

    public static float dot3(int n, float[] a, float[] b) {
        float[] c = mult3(n, a, b);
        float sum = 0;
        for (int i = 0; i < n; i++) {
            sum += c[i];
        }
        return sum;
    }

    public static final void main(String[] args) {
        float[] a = new float[]{1, 1, 1};
        float[] b = new float[]{2, 2, 2};

        TaskSchedule s0 = new TaskSchedule("s0")
                .task("t0", DotProductBasic::dot3, 3, a, b);

        s0.warmup();
        s0.schedule();

        System.out.printf("result = 0x%x\n", s0.getReturnValue("t0"));

    }

}
