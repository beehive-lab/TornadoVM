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

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Existing multi-graph chaining tests ({@code TestSharedBuffers}, {@code TestExecutor},
 * {@code TestChainOfGridSchedulers}) always use the SAME array element type across every graph in
 * one {@link TornadoExecutionPlan}. {@code consumeFromDevice} itself can't bridge a type change --
 * it re-uses the identical Java array object/type on-device, so the type system rules that out.
 * The realistic place a type change happens between chained graphs is the host-side glue in
 * between two independently-executed graphs of the SAME plan: graph 1's {@code IntArray} output
 * is read back to host, explicitly widened to a {@code LongArray}, and fed into graph 2. This
 * confirms the plan/graph-index machinery ({@code withGraph(0)}, {@code withGraph(1)}) tolerates
 * heterogeneous array types across its graphs, which nothing else exercises.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestChainedGraphsTypeChange
 * </code>
 */
public class TestChainedGraphsTypeChange extends TornadoTestBase {

    public static void squareInts(IntArray in, IntArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i) * in.get(i));
        }
    }

    public static void addOneToLongs(LongArray in, LongArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i) + 1L);
        }
    }

    @Test
    public void testIntGraphFeedsLongGraph() throws TornadoExecutionPlanException {
        final int size = 256;
        IntArray input = new IntArray(size);
        for (int i = 0; i < size; i++) {
            input.set(i, i);
        }
        IntArray squared = new IntArray(size);
        LongArray widened = new LongArray(size);
        LongArray result = new LongArray(size);

        TaskGraph tg1 = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestChainedGraphsTypeChange::squareInts, input, squared) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, squared);

        TaskGraph tg2 = new TaskGraph("s1") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, widened) //
                .task("t1", TestChainedGraphsTypeChange::addOneToLongs, widened, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg1.snapshot(), tg2.snapshot())) {
            executionPlan.withGraph(0).execute();

            // Host-side type change bridging the two graphs: int -> long widening.
            for (int i = 0; i < size; i++) {
                widened.set(i, (long) squared.get(i));
            }

            executionPlan.withGraph(1).execute();
        }

        for (int i = 0; i < size; i++) {
            long expected = ((long) (i * i)) + 1L;
            assertEquals(expected, result.get(i));
        }
    }

}
