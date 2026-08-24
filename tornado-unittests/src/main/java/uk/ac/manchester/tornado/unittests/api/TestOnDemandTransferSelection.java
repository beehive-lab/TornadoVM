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
package uk.ac.manchester.tornado.unittests.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.DataRange;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * On-demand transfers on a plan that is driven one task-graph at a time with
 * {@link TornadoExecutionPlan#withGraph(int)}.
 *
 * <p>A transfer names an object, not a graph. The graph that owns the object is not necessarily
 * the one selected to run next, so asking only the selected graph turns the transfer into a silent
 * no-op and the caller reads whatever the host array held before.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestOnDemandTransferSelection
 * </code>
 */
public class TestOnDemandTransferSelection extends TornadoTestBase {

    private static final int SIZE = 4096;

    public static void scale(FloatArray input, FloatArray output, float alpha) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            output.set(i, alpha * input.get(i));
        }
    }

    /** The requested object belongs to a graph that is not the selected one. */
    @Test
    public void testTransferToHostOfAnUnselectedGraph() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        input.init(2.0f);
        FloatArray outputOfFirst = new FloatArray(SIZE);
        FloatArray outputOfSecond = new FloatArray(SIZE);

        TaskGraph first = new TaskGraph("selectionFirst") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("a", TestOnDemandTransferSelection::scale, input, outputOfFirst, 3.0f) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, outputOfFirst);

        TaskGraph second = new TaskGraph("selectionSecond") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("b", TestOnDemandTransferSelection::scale, input, outputOfSecond, 5.0f) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, outputOfSecond);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(first.snapshot(), second.snapshot())) {
            plan.withGraph(0).execute();
            TornadoExecutionResult executionResult = plan.withGraph(1).execute();

            // outputOfFirst is owned by graph 0, which is no longer the selected graph.
            executionResult.transferToHost(outputOfFirst, outputOfSecond);
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(6.0f, outputOfFirst.get(i), 0.001f);
            assertEquals(10.0f, outputOfSecond.get(i), 0.001f);
        }
    }

    /** The same for a partial transfer through a {@link DataRange}. */
    @Test
    public void testPartialTransferToHostOfAnUnselectedGraph() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        input.init(2.0f);
        FloatArray outputOfFirst = new FloatArray(SIZE);
        FloatArray outputOfSecond = new FloatArray(SIZE);

        TaskGraph first = new TaskGraph("partialSelectionFirst") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("a", TestOnDemandTransferSelection::scale, input, outputOfFirst, 4.0f) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, outputOfFirst);

        TaskGraph second = new TaskGraph("partialSelectionSecond") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("b", TestOnDemandTransferSelection::scale, input, outputOfSecond, 6.0f) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, outputOfSecond);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(first.snapshot(), second.snapshot())) {
            plan.withGraph(0).execute();
            TornadoExecutionResult executionResult = plan.withGraph(1).execute();

            DataRange dataRange = new DataRange(outputOfFirst);
            executionResult.transferToHost(dataRange.withSize(SIZE / 2));
            executionResult.transferToHost(dataRange.withOffset(SIZE / 2).withSize(SIZE / 2));
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(8.0f, outputOfFirst.get(i), 0.001f);
        }
    }

    /** Selecting a graph and asking for its own object keeps working. */
    @Test
    public void testTransferToHostOfTheSelectedGraph() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        input.init(3.0f);
        FloatArray output = new FloatArray(SIZE);
        FloatArray unrelated = new FloatArray(SIZE);

        TaskGraph first = new TaskGraph("ownFirst") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("a", TestOnDemandTransferSelection::scale, input, output, 2.0f) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, output);

        TaskGraph second = new TaskGraph("ownSecond") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("b", TestOnDemandTransferSelection::scale, input, unrelated, 7.0f) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, unrelated);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(first.snapshot(), second.snapshot())) {
            TornadoExecutionResult executionResult = plan.withGraph(0).execute();
            executionResult.transferToHost(output);
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(6.0f, output.get(i), 0.001f);
        }
    }
}
