/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package uk.ac.manchester.tornado.examples.lang;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskSchedule;

public class CountedLoopEx2 {

    public static void add(int[] a, int[] b, int[] c) {
        for (int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void main(String[] args) {

        final int[] a = new int[8];
        final int[] b = new int[8];
        final int[] c = new int[8];
        Arrays.fill(a, 1);
        Arrays.fill(b, 2);

        TaskSchedule s0 = new TaskSchedule("s0").task("t0", CountedLoopEx2::add, a, b, c).streamOut(c);

        s0.warmup();
        s0.execute();

        System.out.printf("c: %s\n", Arrays.toString(c));

    }

}
