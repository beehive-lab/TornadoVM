/*
 * Copyright (c) 2021-2022 APT Group, Department of Computer Science,
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

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * The unit-tests in this class check that the {@link KernelContext} parameter
 * can be passed in any sequence.
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.api.TestVectorAdditionKernelContext
 * </code>
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

    public static void vectorAdd(int[] a, KernelContext context, int[] b, int[] c) {
        c[context.globalIdx] = a[context.globalIdx] + b[context.globalIdx];
    }

    public static void vectorAdd(int[] a, int[] b, KernelContext context, int[] c) {
        c[context.globalIdx] = a[context.globalIdx] + b[context.globalIdx];
    }

    public static void vectorAdd(int[] a, int[] b, int[] c, KernelContext context) {
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
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestVectorAdditionKernelContext::vectorAdd, context, a, b, cTornado) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cTornado);

        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWorkToNull();

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        executionPlan.withGridScheduler(gridScheduler) //
                .execute();

        vectorAddJava(a, b, cJava);

        for (int i = 0; i < size; i++) {
            assertEquals(cJava[i], cTornado[i]);
        }
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
        GridScheduler gridScheduler = new GridScheduler();
        gridScheduler.setWorkerGrid("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestVectorAdditionKernelContext::vectorAdd, a, context, b, cTornado) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cTornado);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withGridScheduler(gridScheduler) //
                .execute();

        vectorAddJava(a, b, cJava);

        for (int i = 0; i < size; i++) {
            assertEquals(cJava[i], cTornado[i]);
        }
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
        GridScheduler gridScheduler = new GridScheduler();
        gridScheduler.setWorkerGrid("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestVectorAdditionKernelContext::vectorAdd, a, b, context, cTornado) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cTornado);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withGridScheduler(gridScheduler) //
                .execute();

        vectorAddJava(a, b, cJava);

        for (int i = 0; i < size; i++) {
            assertEquals(cJava[i], cTornado[i]);
        }
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
        GridScheduler gridScheduler = new GridScheduler();
        gridScheduler.setWorkerGrid("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestVectorAdditionKernelContext::vectorAdd, a, b, cTornado, context) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cTornado);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withGridScheduler(gridScheduler) //
                .execute();

        vectorAddJava(a, b, cJava);

        for (int i = 0; i < size; i++) {
            assertEquals(cJava[i], cTornado[i]);
        }
    }

    @Test
    public void vectorAddKernelContext05() {
        final int size = 16;
        int[] a = new int[size];
        int[] b = new int[size];
        int[] cJava = new int[size];
        int[] cTornado = new int[size];

        Arrays.fill(a, 10);
        Arrays.fill(b, 20);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestVectorAdditionKernelContext::vectorAdd, context, a, b, cTornado) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cTornado);

        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWorkToNull();

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withWarmUp() //
                .withGridScheduler(gridScheduler) //
                .execute();

        vectorAddJava(a, b, cJava);

        for (int i = 0; i < size; i++) {
            assertEquals(cJava[i], cTornado[i]);
        }
    }
}