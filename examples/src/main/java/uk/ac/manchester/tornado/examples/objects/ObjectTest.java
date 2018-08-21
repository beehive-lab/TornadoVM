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

import uk.ac.manchester.tornado.api.TaskSchedule;

public class ObjectTest {

    public static class Foo {

        int value;

        public Foo(int v) {
            value = v;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int v) {
            value = v;
        }
    }

    public static void add(Foo a, Foo b, Foo c) {
        c.setValue(a.getValue() + b.getValue());
    }

    public static void main(final String[] args) {

        Foo a = new Foo(1);
        Foo b = new Foo(2);
        Foo c = new Foo(0);

        /*
         * Next we insert the task into a task graph and specify that we want
         * the value of c updated on completion.
         */
        final TaskSchedule schedule = new TaskSchedule("s0")
                .task("t0", ObjectTest::add, a, b, c)
                .streamOut(c);

        /*
         * Calculate a (3) + b (2) = c (5)
         */
        schedule.execute();


        /*
         * Check to make sure result is correct
         */
        if (c.getValue() != 3) {
            System.out.printf("Invalid result: c = %d (expected 3)\n",
                    c.getValue());
        }

    }
}
