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

import uk.ac.manchester.tornado.api.Parallel;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;

public class ReductionTest {

    public final int[] data;
    public int result;

    public void sum() {
        int sum = 0;
        for (@Parallel int i = 0; i < data.length; i++) {
            sum += data[i];
        }
        result = sum;
    }

    public ReductionTest(int[] data) {
        this.data = data;
    }

    public static void main(String[] args) {
        final int[] data = new int[614400];

        Arrays.fill(data, 1);

        ReductionTest rt = new ReductionTest(data);

        new TaskSchedule("s0")
                .task("t0", ReductionTest::sum, rt)
                .streamOut(rt)
                .execute();

        int sum = 0;
        for (int value : data) {
            sum += value;
        }

        System.out.printf("result: %d == %d\n", rt.result, sum);

    }

}
