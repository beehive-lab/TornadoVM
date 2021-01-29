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
package uk.ac.manchester.tornado.unittests.api;

import org.junit.Test;
import uk.ac.manchester.tornado.api.GridTask;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.TornadoVMContext;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;

import java.util.Arrays;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

public class TestCombinedTaskSchedule {

    public static void vectorAddV1(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void vectorAddV2(TornadoVMContext context, int[] a, int[] b, int[] c) {
        c[context.threadIdx] = a[context.threadIdx] + b[context.threadIdx];
    }

    public static void vectorMulV1(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] * b[i];
        }
    }

    public static void vectorMulV2(TornadoVMContext context, int[] a, int[] b, int[] c) {
        c[context.threadIdx] = a[context.threadIdx] * b[context.threadIdx];
    }

    public static void vectorSubV1(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] - b[i];
        }
    }

    public static void vectorSubV2(TornadoVMContext context, int[] a, int[] b, int[] c) {
        c[context.threadIdx] = a[context.threadIdx] - b[context.threadIdx];
    }

    @Test
    public void combinedApiV1() {
        final int size = 16;
        int[] a = new int[size];
        int[] b = new int[size];
        int[] cTornado = new int[size];
        int[] cJava = new int[size];

        IntStream.range(0, a.length).sequential().forEach(i -> a[i] = i);
        IntStream.range(0, b.length).sequential().forEach(i -> b[i] = i);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridTask gridTask = new GridTask();
        gridTask.set("api_v1.t0", worker);
        TornadoVMContext context = new TornadoVMContext(worker);

        TaskSchedule s0 = new TaskSchedule("api_v1").streamIn(a, b).task("t0", TestCombinedTaskSchedule::vectorAddV1, a, b, cTornado)
                .task("t1", TestCombinedTaskSchedule::vectorMulV1, cTornado, b, cTornado).task("t2", TestCombinedTaskSchedule::vectorSubV1, cTornado, b, cTornado).streamOut(cTornado);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(size, 1, 1);
        s0.execute(gridTask);

        vectorAddV1(a, b, cJava);
        vectorMulV1(cJava, b, cJava);
        vectorSubV1(cJava, b, cJava);

        for (int i = 0; i < size; i++) {
            assertEquals(cJava[i], cTornado[i]);
        }
    }

    @Test
    public void combinedApiV2() {
        final int size = 16;
        int[] a = new int[size];
        int[] b = new int[size];
        int[] cTornado = new int[size];
        int[] cJava = new int[size];

        IntStream.range(0, a.length).sequential().forEach(i -> a[i] = i);
        IntStream.range(0, b.length).sequential().forEach(i -> b[i] = i);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridTask gridTask = new GridTask();
        gridTask.set("api_v2.t0", worker);
        gridTask.set("api_v2.t1", worker);
        gridTask.set("api_v2.t2", worker);
        TornadoVMContext context = new TornadoVMContext(worker);

        TaskSchedule s0 = new TaskSchedule("api_v2").streamIn(a, b).task("t0", TestCombinedTaskSchedule::vectorAddV2, context, a, b, cTornado)
                .task("t1", TestCombinedTaskSchedule::vectorMulV2, context, cTornado, b, cTornado).task("t2", TestCombinedTaskSchedule::vectorSubV2, context, cTornado, b, cTornado)
                .streamOut(cTornado);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(size, 1, 1);
        s0.execute(gridTask);

        vectorAddV1(a, b, cJava);
        vectorMulV1(cJava, b, cJava);
        vectorSubV1(cJava, b, cJava);

        for (int i = 0; i < size; i++) {
            assertEquals(cJava[i], cTornado[i]);
        }
    }

    @Test
    public void combinedApiV1V2() {
        final int size = 16;
        int[] a = new int[size];
        int[] b = new int[size];
        int[] cTornado = new int[size];
        int[] cJava = new int[size];

        IntStream.range(0, a.length).sequential().forEach(i -> a[i] = i);
        IntStream.range(0, b.length).sequential().forEach(i -> b[i] = i);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridTask gridTask = new GridTask();
        gridTask.set("api_v1_v2.t1", worker);
        gridTask.set("api_v1_v2.t2", worker);
        TornadoVMContext context = new TornadoVMContext(worker);

        TaskSchedule s0 = new TaskSchedule("api_v1_v2").streamIn(a, b).task("t0", TestCombinedTaskSchedule::vectorAddV1, a, b, cTornado)
                .task("t1", TestCombinedTaskSchedule::vectorMulV2, context, cTornado, b, cTornado).task("t2", TestCombinedTaskSchedule::vectorSubV2, context, cTornado, b, cTornado)
                .streamOut(cTornado);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(size, 1, 1);
        s0.execute(gridTask);

        vectorAddV1(a, b, cJava);
        vectorMulV1(cJava, b, cJava);
        vectorSubV1(cJava, b, cJava);

        for (int i = 0; i < size; i++) {
            assertEquals(cJava[i], cTornado[i]);
        }
    }

    @Test
    public void combinedApiV2V1() {
        final int size = 16;
        int[] a = new int[size];
        int[] b = new int[size];
        int[] cTornado = new int[size];
        int[] cJava = new int[size];

        IntStream.range(0, a.length).sequential().forEach(i -> a[i] = i);
        IntStream.range(0, b.length).sequential().forEach(i -> b[i] = i);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridTask gridTask = new GridTask();
        gridTask.set("api_v1_v2.t0", worker);
        gridTask.set("api_v1_v2.t1", worker);
        TornadoVMContext context = new TornadoVMContext(worker);

        TaskSchedule s0 = new TaskSchedule("api_v1_v2").streamIn(a, b).task("t0", TestCombinedTaskSchedule::vectorAddV2, context, a, b, cTornado)
                .task("t1", TestCombinedTaskSchedule::vectorMulV2, context, cTornado, b, cTornado).task("t2", TestCombinedTaskSchedule::vectorSubV1, cTornado, b, cTornado).streamOut(cTornado);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(size, 1, 1);
        s0.execute(gridTask);

        vectorAddV1(a, b, cJava);
        vectorMulV1(cJava, b, cJava);
        vectorSubV1(cJava, b, cJava);

        for (int i = 0; i < size; i++) {
            assertEquals(cJava[i], cTornado[i]);
        }
    }
}
