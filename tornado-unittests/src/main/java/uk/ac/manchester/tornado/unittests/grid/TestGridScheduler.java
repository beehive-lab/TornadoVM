/*
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.grid;

import static org.junit.Assert.assertEquals;

import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V --debug uk.ac.manchester.tornado.unittests.grid.TestGridScheduler
 * </code>
 *
 */
public class TestGridScheduler {

    public static float computeSequential(FloatArray a, FloatArray b, FloatArray c) {
        float acc = 0;
        vectorAddFloat(a, b, c);

        for (int i = 0; i < c.getSize(); i++) {
            // for (float v : c) {
            acc += c.get(i);
        }
        return acc;
    }

    public static void vectorAddFloat(FloatArray a, FloatArray b, FloatArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    public static void reduceAdd(FloatArray array, final int size) {
        float acc = array.get(0);
        for (int i = 1; i < size; i++) {
            acc += array.get(i);
        }
        array.set(0, acc);
    }

    @Test
    public void testMultipleTasksWithinTaskGraph() throws TornadoExecutionPlanException {
        final int size = 1024;
        FloatArray a = new FloatArray(size);
        FloatArray b = new FloatArray(size);
        FloatArray sequentialC = new FloatArray(size);
        FloatArray tornadoC = new FloatArray(size);
        IntStream.range(0, a.getSize()).sequential().forEach(i -> a.set(i, i));
        IntStream.range(0, b.getSize()).sequential().forEach(i -> b.set(i, 2));
        float sequential = computeSequential(a, b, sequentialC);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, size) //
                .task("t0", TestGridScheduler::vectorAddFloat, a, b, tornadoC) //
                .task("t1", TestGridScheduler::reduceAdd, tornadoC, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoC);

        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(1, 1, 1);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        // Final SUM
        float finalSum = tornadoC.get(0);
        assertEquals(sequential, finalSum, 0);
    }

    @Test
    public void testMultiTaskGraphs() throws TornadoExecutionPlanException {
        final int size = 1024;

        FloatArray a = new FloatArray(size);
        FloatArray b = new FloatArray(size);
        FloatArray output = new FloatArray(size);
        IntStream.range(0, a.getSize()).sequential().forEach(i -> a.set(i, i));
        IntStream.range(0, b.getSize()).sequential().forEach(i -> b.set(i, 2));

        WorkerGrid worker = new WorkerGrid1D(size);
        worker.setLocalWork(1, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);

        TaskGraph tg1 = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, size) //
                .task("t0", TestGridScheduler::vectorAddFloat, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        TaskGraph tg2 = new TaskGraph("s1") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, size) //
                .task("t1", TestGridScheduler::vectorAddFloat, a, b, output) //
                .task("t2", TestGridScheduler::vectorAddFloat, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg1.snapshot(), tg2.snapshot())) {
            executionPlan.withGraph(0).withGridScheduler(gridScheduler).execute();

            executionPlan.withGraph(1).execute();
        }
    }

    @Test
    public void testMultipleTasksSeparateTaskGraphs() throws TornadoExecutionPlanException {
        final int size = 1024;
        FloatArray a = new FloatArray(size);
        FloatArray b = new FloatArray(size);
        FloatArray sequentialC = new FloatArray(size);
        FloatArray tornadoC = new FloatArray(size);
        IntStream.range(0, a.getSize()).sequential().forEach(i -> a.set(i, i));
        IntStream.range(0, b.getSize()).sequential().forEach(i -> b.set(i, 2));
        float sequential = computeSequential(a, b, sequentialC);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);

        TaskGraph s0 = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, size) //
                .task("t0", TestGridScheduler::vectorAddFloat, a, b, tornadoC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoC);

        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(1, 1, 1);

        ImmutableTaskGraph immutableTaskGraph = s0.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .execute();
        }

        TaskGraph s1 = new TaskGraph("s1") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tornadoC, size) //
                .task("t0", TestGridScheduler::reduceAdd, tornadoC, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoC);

        ImmutableTaskGraph immutableTaskGraph1 = s1.snapshot();
        try (TornadoExecutionPlan executionPlan1 = new TornadoExecutionPlan(immutableTaskGraph1)) {
            executionPlan1.execute();
        }

        // Final SUM
        float finalSum = tornadoC.get(0);
        assertEquals(sequential, finalSum, 0);
    }
}
