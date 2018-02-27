/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.examples;

import java.util.Arrays;
import tornado.common.exceptions.TornadoRuntimeException;
import tornado.runtime.api.TaskSchedule;

public class BoundsCheck {

    /*
     * The following code generates an index out-of-bounds exception
     */
    public static void add(final int[] a, final int[] b, final int[] c) {
        for (int i = 0; i < a.length + 1; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void main(final String[] args) throws TornadoRuntimeException {

        final int numElements = 16;

        final int[] a = new int[numElements];
        final int[] b = new int[numElements];
        final int[] c = new int[numElements];

        Arrays.fill(a, 3);
        Arrays.fill(b, 2);
        Arrays.fill(c, 0);

        /*
         * First step is to create a reference to the method invocation This
         * involves finding the methods called and the arguments used in each
         * call.
         */
        TaskSchedule graph = new TaskSchedule("s0")
                .task("t0", BoundsCheck::add, a, b, c)
                .streamOut(c);

        /*
         * Calculate a (3) + b (2) = c (5)
         */
        graph.execute();

        /*
         * Check to make sure result is correct
         */
        for (final int value : c) {
            if (value != 5) {
                System.out.println("Invalid result");
            }
        }
    }
}
