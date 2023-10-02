/*
 * Copyright (c) 2021, 2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.foundation;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado-test -V -f uk.ac.manchester.tornado.unittests.foundation.TestIf
 * </code>
 */
public class TestIf extends TornadoTestBase {

    @Test
    public void test01() {
        final int numElements = 256;
        int[] a = new int[numElements];
        int[] expectedResult = new int[numElements];

        Arrays.fill(a, 0);
        Arrays.fill(expectedResult, 50);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestKernels::testIfInt, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        assertEquals(50, a[0]);
    }

    @Test
    public void test02() {
        final int numElements = 256;
        int[] a = new int[numElements];
        int[] expectedResult = new int[numElements];

        Arrays.fill(a, 0);
        Arrays.fill(expectedResult, 50);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestKernels::testIfInt2, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        assertArrayEquals(expectedResult, a);
    }

    @Test
    public void test03() {
        final int numElements = 256;
        int[] a = new int[numElements];
        int[] expectedResult = new int[numElements];

        Arrays.fill(a, -1);
        Arrays.fill(expectedResult, 100);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestKernels::testIfInt3, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        assertArrayEquals(expectedResult, a);
    }

    @Test
    public void test04() {
        final int numElements = 256;
        int[] a = new int[numElements];
        int[] expectedResult = new int[numElements];

        Arrays.fill(a, -1);
        Arrays.fill(expectedResult, 100);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestKernels::testIfInt4, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        assertArrayEquals(expectedResult, a);
    }

    @Test
    public void test05() {
        final int numElements = 256;
        int[] a = new int[numElements];
        int[] expectedResult = new int[numElements];

        Arrays.fill(a, 0);
        Arrays.fill(expectedResult, 50);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestKernels::testIfInt5, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        assertArrayEquals(expectedResult, a);
    }

    @Test
    public void test06() {
        final int numElements = 256;
        int[] a = new int[numElements];
        int[] expectedResult = new int[numElements];

        Arrays.fill(a, 0);
        Arrays.fill(expectedResult, 100);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestKernels::testIfInt6, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        assertArrayEquals(expectedResult, a);
    }

}
