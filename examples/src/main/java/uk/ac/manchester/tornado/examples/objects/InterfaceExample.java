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
package uk.ac.manchester.tornado.examples.objects;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;

public class InterfaceExample {

    interface BinaryOp {

        public int apply(int a, int b);
    }

    static class AddOp implements BinaryOp {

        @Override
        public int apply(int a, int b) {
            return a + b;
        }
    }

    static class SubOp implements BinaryOp {

        @Override
        public int apply(int a, int b) {
            return a + b;
        }
    }

    public static void run(BinaryOp[] ops, int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < ops.length; i++) {
            c[i] = ops[i].apply(a[i], b[i]);
        }
    }

    public static void main(String[] args) {

        BinaryOp[] ops = new BinaryOp[8];
        for (int i = 0; i < 8; i++) {
            ops[i] = (i % 2 == 0) ? new AddOp() : new SubOp();
        }

        int[] a = new int[]{1, 2, 3, 4, 5, 6, 7, 8};
        int[] b = new int[]{1, 2, 3, 4, 5, 6, 7, 8};
        int[] c = new int[8];

        TaskSchedule s0 = new TaskSchedule("s0")
                .task("t0", InterfaceExample::run, ops, a, b, c)
                .streamOut(c);
        s0.warmup();
        s0.execute();

        System.out.println("c = " + Arrays.toString(c));

    }

}
