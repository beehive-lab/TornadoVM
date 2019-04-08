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
package uk.ac.manchester.tornado.examples.memories;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

/**
 * Simple test to run on the FPGA to check for correctness.
 *
 */
public class VectorAddIntLocal {

    private static void vectorAdd(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void main(String[] args) {
        boolean pre = false;
        int size = 8192;
        String kernelLocation = null;

        if (args.length > 0) {
            size = Integer.parseInt(args[0]);
        } else if (args.length == 2) {
            pre = true;
            kernelLocation = args[1];
        }

        int[] a = new int[size];
        int[] b = new int[size];
        int[] c = new int[size];
        int[] result = new int[size];

        Arrays.fill(a, 10);
        Arrays.fill(b, 20);

        // TornadoDevice defaultDevice =
        // TornadoRuntime.getTornadoRuntime().getDefaultDevice();

        TornadoDevice newDev = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(1);

        TaskSchedule graph = new TaskSchedule("s0");

        if (pre) {
            graph.prebuiltTask("t0", "vectorAdd", kernelLocation, new Object[] { a, b, c }, new Access[] { Access.READ, Access.READ, Access.WRITE }, newDev, new int[] { size }).streamOut(c);
        } else {
            graph.task("t0", VectorAddIntLocal::vectorAdd, a, b, c).streamOut(c);
        }

        for (int idx = 0; idx < 10; idx++) {

            long startTornado = System.nanoTime();
            graph.execute();
            long stopTornado = System.nanoTime();

            long startSeq = System.nanoTime();
            vectorAdd(a, b, result);
            long stopSeq = System.nanoTime();

            double speedup = (double) (stopSeq - startSeq) / (double) (stopTornado - startTornado);

            System.out.println("Speedup: " + speedup);

            boolean wrongResult = false;
            for (int i = 0; i < c.length; i++) {
                if (c[i] != 30) {
                    wrongResult = true;
                    break;
                }
            }
            if (!wrongResult) {
                System.out.println("Test success");
            } else {
                System.out.println("Result is wrong");
            }
        }
    }
}
