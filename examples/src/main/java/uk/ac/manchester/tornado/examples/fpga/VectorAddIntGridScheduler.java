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

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;

/**
 * Simple test to run on the FPGA to check for correctness.
 *
 */
public class VectorAddIntGridScheduler {

    private static void vectorAdd(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void main(String[] args) {
        int size = 8192;
        if (args.length > 0) {
            size = Integer.parseInt(args[0]);
        }

        int[] a = new int[size];
        int[] b = new int[size];
        int[] c = new int[size];
        int[] result = new int[size];

        Arrays.fill(a, 10);
        Arrays.fill(b, 20);

        WorkerGrid workerGrid = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);
        workerGrid.setGlobalWork(size, 1, 1);
        workerGrid.setLocalWork(size / 2, 1, 1);

        //@formatter:off
        TaskSchedule graph = new TaskSchedule("s0")
                .task("t0", VectorAddIntGridScheduler::vectorAdd, a, b, c)
                .streamOut(c);
        //@formatter:on

        for (int idx = 0; idx < 10; idx++) {
            graph.execute(gridScheduler);
            vectorAdd(a, b, result);

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
