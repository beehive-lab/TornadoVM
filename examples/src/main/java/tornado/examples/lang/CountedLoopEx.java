/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
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
package tornado.examples.lang;

import java.util.Arrays;
import tornado.runtime.api.TaskSchedule;

public class CountedLoopEx {

    public static void one(int[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = 1;
        }
    }

    public static void main(String[] args) {

        final int[] a = new int[8];

        TaskSchedule s0 = new TaskSchedule("s0")
                .task("t0", CountedLoopEx::one, a)
                .streamOut(a);

        s0.warmup();
        s0.execute();

        System.out.printf("a: %s\n", Arrays.toString(a));

    }

}
