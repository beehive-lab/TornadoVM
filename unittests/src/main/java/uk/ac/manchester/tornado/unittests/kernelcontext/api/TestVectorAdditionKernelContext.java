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
package uk.ac.manchester.tornado.unittests.kernelcontext.api;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridTask;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * The unit-tests in this class check that the {@link KernelContext} parameter
 * can be passed in any sequence.
 */
public class TestVectorAdditionKernelContext extends TornadoTestBase {
    public static void vectorAddJava(int[] a, int[] b, int[] c) {
        for (int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void vectorAdd(KernelContext context, int[] a, int[] b, int[] c) {
        c[context.globalIdx] = a[context.globalIdx] + b[context.globalIdx];
    }

    @Test
    public void vectorAddKernelContext01() {
        final int size = 16;
        int[] a = new int[size];
        int[] b = new int[size];
        int[] cJava = new int[size];
        int[] cTornado = new int[size];

        Arrays.fill(a, 10);
        Arrays.fill(b, 20);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridTask gridTask = new GridTask("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(a, b) //
                .task("t0", TestVectorAdditionKernelContext::vectorAdd, context, a, b, cTornado) //
                .streamOut(cTornado);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWorkToNull();
        s0.execute(gridTask);

        vectorAddJava(a, b, cJava);

        for (int i = 0; i < size; i++) {
            assertEquals(cJava[i], cTornado[i]);
        }
    }

    public static void vectorAdd(int[] a, KernelContext context, int[] b, int[] c) {
        c[context.globalIdx] = a[context.globalIdx] + b[context.globalIdx];
    }

    @Test
    public void vectorAddKernelContext02() {
        final int size = 16;
        int[] a = new int[size];
        int[] b = new int[size];
        int[] cJava = new int[size];
        int[] cTornado = new int[size];

        Arrays.fill(a, 10);
        Arrays.fill(b, 20);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridTask gridTask = new GridTask();
        gridTask.setWorkerGrid("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(a, b) //
                .task("t0", TestVectorAdditionKernelContext::vectorAdd, a, context, b, cTornado) //
                .streamOut(cTornado);
        s0.execute(gridTask);

        vectorAddJava(a, b, cJava);

        for (int i = 0; i < size; i++) {
            assertEquals(cJava[i], cTornado[i]);
        }
    }

    public static void vectorAdd(int[] a, int[] b, KernelContext context, int[] c) {
        c[context.globalIdx] = a[context.globalIdx] + b[context.globalIdx];
    }

    @Test
    public void vectorAddKernelContext03() {
        final int size = 16;
        int[] a = new int[size];
        int[] b = new int[size];
        int[] cJava = new int[size];
        int[] cTornado = new int[size];

        Arrays.fill(a, 10);
        Arrays.fill(b, 20);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridTask gridTask = new GridTask();
        gridTask.setWorkerGrid("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(a, b) //
                .task("t0", TestVectorAdditionKernelContext::vectorAdd, a, b, context, cTornado) //
                .streamOut(cTornado);
        s0.execute(gridTask);

        vectorAddJava(a, b, cJava);

        for (int i = 0; i < size; i++) {
            assertEquals(cJava[i], cTornado[i]);
        }
    }

    public static void vectorAdd(int[] a, int[] b, int[] c, KernelContext context) {
        c[context.globalIdx] = a[context.globalIdx] + b[context.globalIdx];
    }

    @Test
    public void vectorAddKernelContext04() {
        final int size = 16;
        int[] a = new int[size];
        int[] b = new int[size];
        int[] cJava = new int[size];
        int[] cTornado = new int[size];

        Arrays.fill(a, 10);
        Arrays.fill(b, 20);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridTask gridTask = new GridTask();
        gridTask.setWorkerGrid("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(a, b) //
                .task("t0", TestVectorAdditionKernelContext::vectorAdd, a, b, cTornado, context) //
                .streamOut(cTornado);
        s0.execute(gridTask);

        vectorAddJava(a, b, cJava);

        for (int i = 0; i < size; i++) {
            assertEquals(cJava[i], cTornado[i]);
        }
    }
}