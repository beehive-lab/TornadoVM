/*
 * Copyright (c) 2021, APT Group, School of Computer Science,
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

package uk.ac.manchester.tornado.examples.kernelcontext.compute;

import uk.ac.manchester.tornado.api.GridTask;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid2D;

public class HilbertMatrix {

    private static final boolean CHECK_RESULT = true;
    private static int NROWS = 1024;
    private static int NCOLS = 1024;

    public static void hilberComputation(KernelContext context, float[] output, int rows, int cols) {
        int i = context.globalIdx;
        int j = context.globalIdy;

        output[j * rows + i] = (float) 1 / ((i + 1) + (j + 1) - 1);
    }

    public static void hilberComputationJava(float[] output, int rows, int cols) {
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                output[i * rows + j] = (float) 1 / ((i + 1) + (j + 1) - 1);
            }
        }
    }

    public static void main(String[] args) {
        float[] output = new float[NROWS * NCOLS];

        WorkerGrid workerGrid = new WorkerGrid2D(NROWS, NCOLS);
        GridTask gridTask = new GridTask("s0.t0", workerGrid);
        KernelContext context = new KernelContext();
        // [Optional] Set the global work group
        workerGrid.setGlobalWork(NROWS, NCOLS, 1);
        // [Optional] Set the local work group
        workerGrid.setLocalWork(32, 32, 1);
        // @formatter:off
        TaskSchedule s0 = new TaskSchedule("s0")
                .task("t0", HilbertMatrix::hilberComputation, context,  output, NROWS, NCOLS)
                .streamOut(output);
        // @formatter:on

        s0.execute(gridTask);

        if (CHECK_RESULT) {
            float[] seq = new float[NROWS * NCOLS];
            hilberComputationJava(seq, NROWS, NCOLS);
            for (int i = 0; i < NROWS; i++) {
                for (int j = 0; j < NCOLS; j++) {
                    if (Math.abs(output[i * NROWS + j] - seq[i * NROWS + j]) > 0.01f) {
                        System.out.println("Result is not correct: <" + i + "," + j + ">  -> " + output[i * NROWS + j] + " vs " + seq[i * NROWS + j]);
                        break;
                    }
                }
            }
        }

    }
}
