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
package uk.ac.manchester.tornado.examples.arrays;

import static uk.ac.manchester.tornado.runtime.TornadoRuntime.getTornadoRuntime;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.Parallel;
import uk.ac.manchester.tornado.runtime.TornadoDriver;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;

public class MigratingArrayAccInt {

    public static void acc(int[] a, int value) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] += value;
        }
    }

    public static void main(String[] args) {

        final int numElements = 8;
        final int numKernels = 8;
        int[] a = new int[numElements];

        Arrays.fill(a, 0);

        //@formatter:off
        TaskSchedule s0 = new TaskSchedule("s0");
        for (int i = 0; i < numKernels; i++) {
            s0.task("t" + i, MigratingArrayAccInt::acc, a, 1);
        }
        s0.streamOut(a);
        //@formatter:on

        TornadoDriver driver = getTornadoRuntime().getDriver(0);
        s0.mapAllTo(driver.getDevice(0));
        s0.execute();

        System.out.println("a: " + Arrays.toString(a));
        System.out.println("migrating devices...");
        s0.mapAllTo(driver.getDevice(1));
        s0.execute();

        s0.dumpEvents();
        System.out.println("a: " + Arrays.toString(a));
    }

}
