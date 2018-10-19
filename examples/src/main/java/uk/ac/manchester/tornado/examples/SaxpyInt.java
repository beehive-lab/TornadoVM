/*
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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

package uk.ac.manchester.tornado.examples;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

public class SaxpyInt {

    // public static void saxpy(int alpha, int[] x, int[] y) {
    // for (@Parallel int i = 0; i < y.length; i++) {
    // y[i] va= alpha * x[i];
    //
    // }
    // }
    public static void saxpy(int alpha, int[] x, int[] y, int[] size) {
        // for (@Parallel int i = 0; i < y.length; i++) {
        for (@Parallel int i = 0; i < size[0]; i++) {
            for (@Parallel int ii = 0; ii < size[0]; ii++) {
                y[ii] = alpha * x[ii];
            }
        }
    }

    public static void main(String[] args) {

        int alpha = 2;
        int[] size = new int[1];
        String filename;

        filename = null;

        size[0] = Integer.parseInt(args[0]);
        int numElements = size[0];

        int[] x = new int[numElements];
        int[] y = new int[numElements];

        for (int i = 0; i < numElements; i++) {
            x[i] = 1;
        }

        if (args.length == 2) {
            filename = args[1];
            TornadoDevice defaultDevice = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
            new TaskSchedule("s0").prebuiltTask("t0", "saxpy", filename, new Object[] { alpha, x, y, size }, new Access[] { Access.READ, Access.READ, Access.WRITE, Access.READ }, defaultDevice,
                    new int[] { Integer.parseInt(args[0]) }).streamOut(y).execute();

        } else {
            TaskSchedule s0 = new TaskSchedule("s0").streamIn(x).task("t0", SaxpyInt::saxpy, alpha, x, y, size).streamOut(y);
            // s0.warmup();
            s0.execute();
        }

        for (int i = 0; i < y.length; i++) {
            System.out.println(y[i] + "\n");
        }

    }
}
