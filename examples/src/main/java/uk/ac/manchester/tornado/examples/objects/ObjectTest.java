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
        final TaskSchedule schedule = new TaskSchedule("s0").task("t0", ObjectTest::add, a, b, c).streamOut(c);

        /*
         * Calculate a (3) + b (2) = c (5)
         */
        schedule.execute();

        /*
         * Check to make sure result is correct
         */
        if (c.getValue() != 3) {
            System.out.printf("Invalid result: c = %d (expected 3)\n", c.getValue());
        }

    }
}
