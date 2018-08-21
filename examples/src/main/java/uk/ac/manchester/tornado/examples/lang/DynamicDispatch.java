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

import java.util.Arrays;
import java.util.function.BiFunction;

import uk.ac.manchester.tornado.api.TaskSchedule;

public class DynamicDispatch {

    static class AddOp implements BiFunction<Integer, Integer, Integer> {

        @Override
        public Integer apply(Integer x, Integer y) {
            return x + y;
        }

    }

    static class SubOp implements BiFunction<Integer, Integer, Integer> {

        @Override
        public Integer apply(Integer x, Integer y) {
            return x - y;
        }

    }

    public static final void applyOp(BiFunction<Integer, Integer, Integer> op, int[] a, int[] b, int[] c) {
        for (int i = 0; i < c.length; i++) {
            c[i] = op.apply(a[i], b[i]);
        }
    }

    public static final void main(String[] args) {

        int[] a = new int[8];
        int[] b = new int[8];
        int[] c = new int[8];

        Arrays.fill(a, 1);
        Arrays.fill(b, 2);

        TaskSchedule s0 = new TaskSchedule("s0")
                .task("t0", DynamicDispatch::applyOp, new AddOp(), a, b, c)
                .streamOut(c);

        s0.warmup();
        s0.execute();

        System.out.printf("c = %s\n", Arrays.toString(c));

    }

}
