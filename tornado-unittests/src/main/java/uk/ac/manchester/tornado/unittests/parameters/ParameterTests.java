/*
 * Copyright (c) 2023 APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.parameters;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import static org.junit.Assert.assertEquals;

/**
 * How to test?
 *
 * <p>
 * <code>
 *     tornado-test -V --fast uk.ac.manchester.tornado.unittests.parameters.ParameterTests
 * </code>
 * </p>
 */
public class ParameterTests extends TornadoTestBase {

    /**
     * This method is only for testing the Task-Graph and ExecutionPlan logic when
     * having scalar values as parameters. Note that this method does not return any
     * value is stored in a local variable. Thus, it does not scale the method
     * scope.
     */
    private static void testWithOnlyScalarValues(int x, int y, int z) {
        z = x + y;
    }

    /**
     * This method is only for testing the Task-Graph and ExecutionPlan logic when
     * having scalar values as parameters. Note that this method does not return any
     * value is stored in a local variable. Thus, it does not scale the method
     * scope.
     */
    private static void testWithOnlyScalarValues2(int z) {
        z = 0;
    }

    private static void testWithScalarValues03(LongArray x, long y, IntArray z) {
        for (@Parallel int i = 0; i < x.getSize(); i++) {
            long tmp = x.get(i) + y;
            z.set(i, (int) tmp);
        }
    }

    /**
     * This test throws a {@link TornadoRuntimeException} because scalar values are
     * used as output parameters. This type of code is not legal in TornadoVM.
     */
    @Test(expected = TornadoRuntimeException.class)
    public void testScalarParameters01() {
        int x = 10;
        int y = 20;
        int z = 0;

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, x, y) //
                .task("t0", ParameterTests::testWithOnlyScalarValues, x, y, z) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, z);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();
    }

    /**
     * This test throws a {@link TornadoRuntimeException} because scalar values are
     * used as output parameters. This type of code is not legal in TornadoVM.
     */
    @Test(expected = TornadoRuntimeException.class)
    public void testScalarParameters02() {
        int z = 0;

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", ParameterTests::testWithOnlyScalarValues2, z) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, z);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();
    }

    @Test
    public void testScalarParameters03() {
        final int size = 16;
        LongArray x = new LongArray(size);
        long y = 10L;
        IntArray z = new IntArray(size);

        for (int i = 0; i < size; i++) {
            x.set(i, i);
            z.set(i, -1);
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, x) //
                .task("t0", ParameterTests::testWithScalarValues03, x, y, z) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, z);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < z.getSize(); i++) {
            assertEquals(y + x.get(i), z.get(i));
        }
    }
}
