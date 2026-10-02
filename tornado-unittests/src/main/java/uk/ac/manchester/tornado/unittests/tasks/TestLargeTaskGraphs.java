/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.tasks;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Task graphs with hundreds of tasks. Each task contributes several nodes to the internal
 * TornadoGraph (the task and a dependent read per written array, plus the allocations and copies of
 * its arrays), so these graphs grow past its initial capacity of 1024 nodes.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tasks.TestLargeTaskGraphs
 * </code>
 */
public class TestLargeTaskGraphs extends TornadoTestBase {

    private static final int N = 256;

    public static void increment(IntArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, a.get(i) + 1);
        }
    }

    public static void incrementAll(IntArray a, IntArray b, IntArray c) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, a.get(i) + 1);
            b.set(i, b.get(i) + 2);
            c.set(i, c.get(i) + 3);
        }
    }

    /** 320 chained tasks on the same array. */
    @Test
    public void testManyChainedTasks() throws TornadoExecutionPlanException {
        final int tasks = 320;
        IntArray a = new IntArray(N);
        TaskGraph graph = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, a);
        for (int t = 0; t < tasks; t++) {
            graph.task("t" + t, TestLargeTaskGraphs::increment, a);
        }
        graph.transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
        for (int i = 0; i < N; i++) {
            assertEquals(tasks, a.get(i));
        }
    }

    /** 320 chained tasks writing three arrays each: well over 1024 graph nodes. */
    @Test
    public void testManyChainedTasksWithSeveralArrays() throws TornadoExecutionPlanException {
        final int tasks = 320;
        IntArray a = new IntArray(N);
        IntArray b = new IntArray(N);
        IntArray c = new IntArray(N);
        TaskGraph graph = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b, c);
        for (int t = 0; t < tasks; t++) {
            graph.task("t" + t, TestLargeTaskGraphs::incrementAll, a, b, c);
        }
        graph.transferToHost(DataTransferMode.EVERY_EXECUTION, a, b, c);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
            // A second execution reuses the compiled graph.
            plan.execute();
        }
        for (int i = 0; i < N; i++) {
            assertEquals(2 * tasks, a.get(i));
            assertEquals(4 * tasks, b.get(i));
            assertEquals(6 * tasks, c.get(i));
        }
    }
}
