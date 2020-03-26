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

package uk.ac.manchester.tornado.examples.objects;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

public class InterfaceExample {

    interface BinaryOp {

        public int apply(int a, int b);
    }

    static class AddOp implements BinaryOp {

        @Override
        public int apply(int a, int b) {
            return a + b;
        }
    }

    static class SubOp implements BinaryOp {

        @Override
        public int apply(int a, int b) {
            return a + b;
        }
    }

    public static void run(BinaryOp[] ops, int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < ops.length; i++) {
            c[i] = ops[i].apply(a[i], b[i]);
        }
    }

    public static void main(String[] args) {

        BinaryOp[] ops = new BinaryOp[8];
        for (int i = 0; i < 8; i++) {
            ops[i] = (i % 2 == 0) ? new AddOp() : new SubOp();
        }

        int[] a = new int[] { 1, 2, 3, 4, 5, 6, 7, 8 };
        int[] b = new int[] { 1, 2, 3, 4, 5, 6, 7, 8 };
        int[] c = new int[8];

        TaskSchedule s0 = new TaskSchedule("s0").task("t0", InterfaceExample::run, ops, a, b, c).streamOut(c);
        s0.warmup();
        s0.execute();

        System.out.println("c = " + Arrays.toString(c));

    }

}
