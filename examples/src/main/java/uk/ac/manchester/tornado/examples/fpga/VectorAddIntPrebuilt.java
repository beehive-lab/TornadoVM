/*
 * Copyright (c) 2021, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.examples.fpga;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

public class VectorAddIntPrebuilt {

    private static void vectorAdd(int[] a, int[] b, int[] c, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void main(String[] args) {
        int size = 4096;

        if (args.length == 1) {
            size = Integer.parseInt(args[0]);
        }

        int[] a = new int[size];
        int[] b = new int[size];
        int[] c = new int[size];
        int[] result = new int[size];

        Arrays.fill(a, 10);
        Arrays.fill(b, 20);

        String tornadoSDK = System.getenv("TORNADO_SDK");

        TornadoDevice defaultDevice = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(1);
        String filePath = tornadoSDK + "/examples/generated/";
        filePath += "fpga_vadd.cl";

        // @formatter:off
        TaskGraph schedule = new TaskGraph("s0")
                .prebuiltTask("t0",
                        "vectorAdd",
                        filePath,
                        new Object[] { a, b, c, size },
                        new Access[] { Access.READ, Access.READ, Access.WRITE, Access.READ },
                        defaultDevice,
                        new int[] { size })
                .streamOut(c);
        // @formatter:on

        long startSequential = 0;
        long endSequential = 0;
        long startParallel = 0;
        long endParallel = 0;

        boolean wrongResult;
        for (int idx = 0; idx < 1; idx++) {
            // Parallel
            startParallel = System.nanoTime();
            schedule.execute();
            endParallel = System.nanoTime();

            // Sequential
            startSequential = System.nanoTime();
            vectorAdd(a, b, result, size);
            endSequential = System.nanoTime();

            long timeParallel = (endParallel - startParallel);
            long timeSequential = (endSequential - startSequential);

            // Check Result
            wrongResult = false;
            for (int i = 0; i < c.length; i++) {
                if (c[i] != 30) {
                    wrongResult = true;
                    break;
                }
            }
            if (wrongResult) {
                System.out.println("Result is wrong");
            } else {
                System.out.println("Result is correct.");
                System.out.println("Total TornadoVM  time: " + timeParallel + " (ns)");
                System.out.println("Total Sequential time: " + timeSequential + " (ns)");
                System.out.println("Speedup in peak performance: " + (timeSequential / timeParallel) + "x");
            }
        }

        System.out.println(schedule.getProfileLog());
    }
}
