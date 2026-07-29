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
package uk.ac.manchester.tornado.unittests.tasks;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Tests for {@link TaskGraph#task} overloads with a high argument count (16 and 20
 * arguments). These exercise the enumerated {@code Task16}/{@code Task20} functional
 * interfaces and their corresponding runtime dispatch paths.
 *
 * <p>
 * How to test?
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tasks.TestManyArgumentTasks
 * </code>
 * </p>
 */
public class TestManyArgumentTasks extends TornadoTestBase {

    // Kernel with 16 parameters: 1 output array + 15 integer scalars.
    public static void add16(IntArray out, int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8, int a9, int a10, int a11, int a12, int a13, int a14, int a15) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, i + a1 + a2 + a3 + a4 + a5 + a6 + a7 + a8 + a9 + a10 + a11 + a12 + a13 + a14 + a15);
        }
    }

    // Kernel with 20 parameters: 1 output array + 19 integer scalars.
    public static void add20(IntArray out, int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8, int a9, int a10, int a11, int a12, int a13, int a14, int a15, int a16, int a17, int a18,
            int a19) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, i + a1 + a2 + a3 + a4 + a5 + a6 + a7 + a8 + a9 + a10 + a11 + a12 + a13 + a14 + a15 + a16 + a17 + a18 + a19);
        }
    }

    @Test
    public void testTask16() throws TornadoExecutionPlanException {
        final int size = 256;
        IntArray out = new IntArray(size);
        out.init(0);

        TaskGraph taskGraph = new TaskGraph("s16") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, out) //
                .task("t16", TestManyArgumentTasks::add16, out, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        // Sum of 1..15 == 120
        for (int i = 0; i < size; i++) {
            assertEquals(i + 120, out.get(i));
        }
    }

    @Test
    public void testTask20() throws TornadoExecutionPlanException {
        final int size = 256;
        IntArray out = new IntArray(size);
        out.init(0);

        TaskGraph taskGraph = new TaskGraph("s20") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, out) //
                .task("t20", TestManyArgumentTasks::add20, out, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        // Sum of 1..19 == 190
        for (int i = 0; i < size; i++) {
            assertEquals(i + 190, out.get(i));
        }
    }
}
