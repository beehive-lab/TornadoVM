/* 
 * Copyright 2012 James Clarkson.
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
 */
package tornado.examples;

import tornado.lang.Debug;
import tornado.runtime.api.TaskSchedule;

public class IfTest {

    public static void printHello(int[] a) {
        Debug.printf("hello: %d\n", a[0]);
        if (a[0] > 1) {
            Debug.printf("hello\n");
        }
    }

    public static void main(String[] args) {

        /*
         * Simple hello world example which runs on 8 threads
         */
        int[] a = new int[]{8};
        new TaskSchedule("s0")
                .task("t0", IfTest::printHello, a)
                .execute();

    }
}
