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
 * Authors: Juan Fumero, Michalis Papadimitriou
 *
 */

package uk.ac.manchester.tornado.unittests.atomics;

import java.util.Arrays;

import org.junit.Ignore;

import uk.ac.manchester.tornado.api.annotations.Atomic;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestAtomics extends TornadoTestBase {

    public static void atomic01(@Atomic int[] a, int sum) {
        for (@Parallel int i = 0; i < a.length; i++) {
            sum += a[i];
        }
        a[0] = sum;
    }

    @Ignore
    public void testAtomic() {
        final int size = 10;

        int[] a = new int[size];
        int sum = 0;

        Arrays.fill(a, 1);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestAtomics::atomic01, a, sum)
                .streamOut(a)
                .execute();
        //@formatter:on
    }

}
