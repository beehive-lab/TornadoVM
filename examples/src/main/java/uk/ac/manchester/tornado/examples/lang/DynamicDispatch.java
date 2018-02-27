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
package uk.ac.manchester.tornado.examples.lang;

import java.util.Arrays;
import java.util.function.BiFunction;
import tornado.runtime.api.TaskSchedule;

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
