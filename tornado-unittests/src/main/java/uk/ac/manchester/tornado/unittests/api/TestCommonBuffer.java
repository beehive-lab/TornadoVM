/*
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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

import org.junit.Test;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.TestHello;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import static org.junit.Assert.assertEquals;

public class TestCommonBuffer extends TornadoTestBase {

    @Test
    public void testSingleReadWriteSharedObject() throws TornadoExecutionPlanException {
        int numElements = 16;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        a.init(10);
        b.init(20);

        TaskGraph tg1 = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHello::add, a, b, c) //
                .persistOnDevice(DataTransferMode.UNDER_DEMAND, c);

        TaskGraph tg2 = new TaskGraph("s1") //
                .consumeFromDevice(tg1.getTaskGraphName(), c) //
                .task("t1", TestHello::add, c, c, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);


        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg1.snapshot(), tg2.snapshot())) {

            executionPlan.withGraph(0).execute();

            executionPlan.withGraph(1).execute();

            for (int i = 0; i < a.getSize(); i++) {
                assertEquals(60, c.get(i)); // Expected: (10 + 20) + (30 + 30) = 60
            }

        }
    }

    @Test
    public void testMixInputConsumeAndCopy() throws TornadoExecutionPlanException {
        int numElements = 16;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);
        IntArray d = new IntArray(numElements);

        a.init(10);
        b.init(20);
        d.init(50);

        TaskGraph tg1 = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHello::add, a, b, c) //
                .persistOnDevice(c);


        TaskGraph tg2 = new TaskGraph("s1") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, d) //
                .consumeFromDevice(tg1.getTaskGraphName(), c) //
                .task("t1", TestHello::add, c, d, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);


        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg1.snapshot(), tg2.snapshot())) {
            executionPlan.withGraph(0).execute();
            executionPlan.withGraph(1).execute();

            for (int i = 0; i < a.getSize(); i++) {
                assertEquals(80, c.get(i)); // Expected: (10 + 20) + 50 = 80
            }
        }
    }

    @Test
    public void testMultipleSharedObjects() throws TornadoExecutionPlanException {
        int numElements = 16;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);
        IntArray d = new IntArray(numElements);

        a.init(10);
        b.init(20);

        TaskGraph tg1 = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHello::add, a, b, c) //
                .persistOnDevice(a,b);


        TaskGraph tg2 = new TaskGraph("s1") //
                .consumeFromDevice(tg1.getTaskGraphName(), a, b) //
                .task("t1", TestHello::add, a, b, d) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, d);


        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg1.snapshot(), tg2.snapshot())) {
            executionPlan.withGraph(0).execute();
            executionPlan.withGraph(1).execute();

            for (int i = 0; i < a.getSize(); i++) {
                assertEquals(30, d.get(i)); // Expected: 10 + 20 = 30
            }
        }
    }
}
