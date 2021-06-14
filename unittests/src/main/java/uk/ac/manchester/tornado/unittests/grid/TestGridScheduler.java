/*
 * Copyright (c) 2020-2021, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.grid;

import static org.junit.Assert.assertEquals;

import java.util.stream.IntStream;

import org.junit.Test;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;

public class TestGridScheduler {

    public static float computeSequential(float[] a, float[] b, float[] c) {
        float acc = 0;
        vectorAddFloat(a, b, c);

        for (float v : c) {
            acc += v;
        }
        return acc;
    }

    public static void vectorAddFloat(float[] a, float[] b, float[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void reduceAdd(float[] array, final int size) {
        float acc = array[0];
        for (int i = 1; i < size; i++) {
            acc += array[i];
        }
        array[0] = acc;
    }

    @Test
    public void testMultipleTasksWithinTaskSchedule() {
        final int size = 1024;
        float[] a = new float[size];
        float[] b = new float[size];
        float[] sequentialC = new float[size];
        float[] tornadoC = new float[size];
        IntStream.range(0, a.length).sequential().forEach(i -> a[i] = i);
        IntStream.range(0, b.length).sequential().forEach(i -> b[i] = 2);
        float sequential = computeSequential(a, b, sequentialC);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);

        TaskSchedule s0 = new TaskSchedule("s0").streamIn(a, b, size).task("t0", TestGridScheduler::vectorAddFloat, a, b, tornadoC).task("t1", TestGridScheduler::reduceAdd, tornadoC, size)
                .streamOut(tornadoC);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(1, 1, 1);
        s0.execute(gridScheduler);

        // Final SUM
        float finalSum = tornadoC[0];
        assertEquals(sequential, finalSum, 0);
    }

    @Test
    public void testMultipleTasksSeparateTaskSchedules() {
        final int size = 1024;
        float[] a = new float[size];
        float[] b = new float[size];
        float[] sequentialC = new float[size];
        float[] tornadoC = new float[size];
        IntStream.range(0, a.length).sequential().forEach(i -> a[i] = i);
        IntStream.range(0, b.length).sequential().forEach(i -> b[i] = 2);
        float sequential = computeSequential(a, b, sequentialC);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(a, b, size) //
                .task("t0", TestGridScheduler::vectorAddFloat, a, b, tornadoC) //
                .streamOut(tornadoC);

        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(1, 1, 1);
        s0.execute(gridScheduler);

        TaskSchedule s1 = new TaskSchedule("s1").streamIn(tornadoC, size).task("t0", TestGridScheduler::reduceAdd, tornadoC, size).streamOut(tornadoC);
        s1.execute();
        // Final SUM
        float finalSum = tornadoC[0];
        assertEquals(sequential, finalSum, 0);
    }
}
