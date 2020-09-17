/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.api;

import org.junit.Test;
import uk.ac.manchester.tornado.api.GridTask;
import uk.ac.manchester.tornado.api.TornadoVMContext;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.WorkerGrid2D;

import uk.ac.manchester.tornado.api.TaskSchedule;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class TestMatrixMultiplicationTornadoVMContextApi {

    public static void matrixMultiplicationJava(float[] a, float[] b, float[] c, int size) {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                float sum = 0;
                for (int k = 0; k < size; k++) {
                    sum += a[i * size + k] * b[k * size + j];
                }
                c[i * size + j] = sum;
            }
        }
    }

    public static void matrixMultiplication1D(float[] a, float[] b, float[] c, int size, TornadoVMContext context) {
        int idx = context.threadIdx;

        for (int jdx = 0; jdx < size; jdx++) {
            float sum = 0;
            for (int k = 0; k < size; k++) {
                sum += a[(idx * size) + k] * b[(k * size) + jdx];
            }
            c[(idx * size) + jdx] = sum;
        }
    }

    @Test
    public void mxm1DTornadoVMContextApi() {
        final int size = 16;
        float[] a = new float[size * size];
        float[] b = new float[size * size];
        float[] cJava = new float[size * size];
        float[] cTornado = new float[size * size];

        Arrays.fill(a, 2);
        Arrays.fill(b, 4);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridTask gridTask = new GridTask();
        gridTask.set("s0.t0", worker);
        TornadoVMContext context = new TornadoVMContext(worker);

        TaskSchedule s0 = new TaskSchedule("s0").streamIn(a, b).task("t0", TestMatrixMultiplicationTornadoVMContextApi::matrixMultiplication1D, a, b, cTornado, size, context).streamOut(cTornado);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(1, 1, 1);
        s0.execute(gridTask);

        matrixMultiplicationJava(a, b, cJava, size);

        for (int i = 0; i < size * size; i++) {
            assertEquals(cJava[i], cTornado[i], 0);
        }
    }

    public static void matrixMultiplication2D(float[] a, float[] b, float[] c, int size, TornadoVMContext context) {
        int idx = context.threadIdx;
        int jdx = context.threadIdy;
        float sum = 0;

        for (int k = 0; k < size; k++) {
            sum += a[(idx * size) + k] * b[(k * size) + jdx];
        }
        c[(idx * size) + jdx] = sum;
    }

    @Test
    public void mxm2DTornadoVMContextApi() {
        final int size = 16;
        float[] a = new float[size * size];
        float[] b = new float[size * size];
        float[] cJava = new float[size * size];
        float[] cTornado = new float[size * size];

        Arrays.fill(a, 2);
        Arrays.fill(b, 4);

        WorkerGrid worker = new WorkerGrid2D(size, size);
        GridTask gridTask = new GridTask();
        gridTask.set("s0.t0", worker);
        TornadoVMContext context = new TornadoVMContext(worker);

        TaskSchedule s0 = new TaskSchedule("s0").streamIn(a, b).task("t0", TestMatrixMultiplicationTornadoVMContextApi::matrixMultiplication2D, a, b, cTornado, size, context).streamOut(cTornado);
        // Change the Grid
        worker.setGlobalWork(size, size, 1);
        worker.setLocalWork(1, 1, 1);
        s0.execute(gridTask);

        matrixMultiplicationJava(a, b, cJava, size);

        for (int i = 0; i < size * size; i++) {
            assertEquals(cJava[i], cTornado[i], 0);
        }
    }
}