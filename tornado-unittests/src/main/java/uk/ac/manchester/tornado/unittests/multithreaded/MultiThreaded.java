/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.unittests.multithreaded;

import org.junit.Test;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Test to check the invocation of multiple execution plans from different Java threads.
 * The different execution plans are built using the same instance of the Task-Graph.
 *
 * How to run?
 *
 * <p>
 * <code>
 * $ tornado-test -V --fast uk.ac.manchester.tornado.unittests.multithreaded.MultiThreaded
 * </code>
 * </p>
 */
public class MultiThreaded extends TornadoTestBase {

    private static void computeForThread1(FloatArray input, FloatArray output, KernelContext context) {
        int idx = context.globalIdx;
        float value = input.get(idx) * 100 * TornadoMath.sqrt(input.get(idx));
        output.set(idx, value);
    }

    private static void computeForThread2(FloatArray input, FloatArray output) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            float value = input.get(i) * 100 * TornadoMath.sqrt(input.get(i));
            output.set(i, value);
        }
    }

    @Test
    public void test01() throws InterruptedException {

        Thread t0;
        Thread t1;

        KernelContext context = new KernelContext();
        final int size = 1024 * 1024 * 32;
        FloatArray input = new FloatArray(size);
        input.init(1.0f);
        FloatArray output = new FloatArray(size);

        TaskGraph taskGraph = new TaskGraph("check") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("compute01", MultiThreaded::computeForThread1, input, output, context) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        WorkerGrid workerGrid = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("check.compute01", workerGrid);

        t0 = new Thread(() -> {
            System.out.print("Running thread t0");
            ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
            TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
            executionPlan.withGridScheduler(gridScheduler).execute();
        });

        t1 = new Thread(() -> {
            System.out.print("Running thread t1");
            ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
            TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
            executionPlan.withGridScheduler(gridScheduler).execute();
        });

        t0.start();
        t1.start();

        t0.join();
        t1.join();
    }

    @Test
    public void test02() throws InterruptedException {

        Thread t0;
        Thread t1;

        final int size = 1024 * 1024 * 32;
        FloatArray input = new FloatArray(size);
        input.init(1.0f);
        FloatArray output = new FloatArray(size);

        TaskGraph taskGraph = new TaskGraph("check") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("compute01", MultiThreaded::computeForThread2, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        t0 = new Thread(() -> {
            ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
            TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
            executionPlan.execute();
        });

        t1 = new Thread(() -> {
            ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
            TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
            executionPlan.execute();
        });

        t0.start();
        t1.start();

        t0.join();
        t1.join();
    }
}
