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
 *      tornado-test -V uk.ac.manchester.tornado.unittests.foundation.TestLong
 * </code>
 */
public class TestLong extends TornadoTestBase {

    @Test
    public void testLongsCopy() {
        final int numElements = 256;
        long[] a = new long[numElements];

        long[] expected = new long[numElements];
        for (int i = 0; i < numElements; i++) {
            expected[i] = 50;
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestKernels::testLongsCopy, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < numElements; i++) {
            assertEquals(expected[i], a[i]);
        }
    }

    @Test
    public void testLongsAdd() {
        final int numElements = 256;
        long[] a = new long[numElements];
        long[] b = new long[numElements];
        long[] c = new long[numElements];

        Arrays.fill(b, Integer.MAX_VALUE);
        Arrays.fill(c, 1);

        long[] expected = new long[numElements];
        for (int i = 0; i < numElements; i++) {
            expected[i] = b[i] + c[i];
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, b, c) //
                .task("t0", TestKernels::vectorSumLongCompute, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < numElements; i++) {
            assertEquals(expected[i], a[i]);
        }
    }

}
