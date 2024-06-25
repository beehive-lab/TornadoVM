/*
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.logic;

import static org.junit.Assert.assertEquals;

import java.util.stream.IntStream;

import org.junit.Ignore;
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
 * <p>
 * How to test?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.logic.TestLogic
 * </code>
 */
public class TestLogic extends TornadoTestBase {
    // CHECKSTYLE:OFF

    public static void logic01(IntArray data, IntArray output) {
        for (@Parallel int i = 0; i < data.getSize(); i++) {
            output.set(i, data.get(i) & data.get(i) - 1);
        }
    }

    public static void logic02(IntArray data, IntArray output) {
        for (@Parallel int i = 0; i < data.getSize(); i++) {
            output.set(i, data.get(i) | data.get(i) - 1);
        }
    }

    public static void logic03(IntArray data, IntArray output) {
        for (@Parallel int i = 0; i < data.getSize(); i++) {
            output.set(i, data.get(i) ^ data.get(i) - 1);
        }
    }

    public static void logic04(IntArray data, IntArray output) {
        for (@Parallel int i = 0; i < data.getSize(); i++) {
            int value = data.get(i);
            if ((value & (value - 1)) != 0) {

                int condition = (value & (value - 1));
                while (condition != 0) {
                    value &= value - 1;
                    condition = (value & (value - 1));
                }
            }
            output.set(i, value);
        }
    }

    @Test
    public void testLogic01() throws TornadoExecutionPlanException {
        final int N = 1024;
        IntArray data = new IntArray(N);
        IntArray output = new IntArray(N);
        IntArray sequential = new IntArray(N);

        IntStream.range(0, data.getSize()).sequential().forEach(i -> data.set(i, i));

        TaskGraph taskGraph = new TaskGraph("taskGraph") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestLogic::logic01, data, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
        logic01(data, sequential);

        for (int i = 0; i < data.getSize(); i++) {
            assertEquals(sequential.get(i), output.get(i));
        }

    }

    @Test
    public void testLogic02() throws TornadoExecutionPlanException {
        final int N = 1024;
        IntArray data = new IntArray(N);
        IntArray output = new IntArray(N);
        IntArray sequential = new IntArray(N);

        IntStream.range(0, data.getSize()).sequential().forEach(i -> data.set(i, i));

        TaskGraph taskGraph = new TaskGraph("taskGraph") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestLogic::logic02, data, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        logic02(data, sequential);

        for (int i = 0; i < data.getSize(); i++) {
            assertEquals(sequential.get(i), output.get(i));
        }
    }

    @Test
    public void testLogic03() throws TornadoExecutionPlanException {
        final int N = 1024;
        IntArray data = new IntArray(N);
        IntArray output = new IntArray(N);
        IntArray sequential = new IntArray(N);

        IntStream.range(0, data.getSize()).sequential().forEach(i -> data.set(i, i));

        TaskGraph taskGraph = new TaskGraph("taskGraph") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestLogic::logic03, data, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        logic03(data, sequential);

        for (int i = 0; i < data.getSize(); i++) {
            assertEquals(sequential.get(i), output.get(i));
        }
    }

    @Ignore
    public void testLogic04() throws TornadoExecutionPlanException {
        final int N = 1024;
        IntArray data = new IntArray(N);
        IntArray output = new IntArray(N);
        IntArray sequential = new IntArray(N);

        IntStream.range(0, data.getSize()).sequential().forEach(i -> data.set(i, i));

        TaskGraph taskGraph = new TaskGraph("taskGraph") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestLogic::logic04, data, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        logic04(data, sequential);

        for (int i = 0; i < data.getSize(); i++) {
            assertEquals(sequential.get(i), output.get(i));
        }
    }
    // CHECKSTYLE:ON
}
