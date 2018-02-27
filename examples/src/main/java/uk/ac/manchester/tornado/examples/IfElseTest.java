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

import uk.ac.manchester.tornado.lang.Debug;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;

public class IfElseTest {

    public static void printHello(int[] a) {
        Debug.printf("hello: %d\n", a[0]);
        if (a[0] == 1) {
            Debug.printf("one\n");
        } else if (a[0] == 2) {
            Debug.printf("two\n");
        } else {
            Debug.printf("unknown?\n");
        }
    }

    public static void main(String[] args) {

        /*
         * Simple hello world example which runs on 8 threads
         */
        int[] a = new int[]{1};
        new TaskSchedule("s0")
                .task("t0", IfElseTest::printHello, a)
                .execute();

    }
}
