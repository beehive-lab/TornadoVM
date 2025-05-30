/*
 * Copyright (c) 2013-2018, 2022, 2025, APT Group, School of Computer Science,
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
package uk.ac.manchester.tornado.unittests.dynamic;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.DRMode;
import uk.ac.manchester.tornado.api.Policy;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to run?
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.dynamic.TestDynamic
 * </code>
 * </p>
 */
public class TestDynamic extends TornadoTestBase {

    public static void compute(IntArray a, IntArray b) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            b.set(i, a.get(i) * 2);
        }
    }

    public static void compute2(IntArray a, IntArray b) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            b.set(i, a.get(i) * 10);
        }
    }

    @Test
    public void test01() throws TornadoExecutionPlanException {
        int numElements = 256;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);

        a.init(10);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestDynamic::compute, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b); //

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {

            // Run first time to obtain the best performance device
            executionPlan.withDynamicReconfiguration(Policy.PERFORMANCE, DRMode.SERIAL) //
                    .execute();

            // Run a few iterations to get the device.
            for (int i = 0; i < 10; i++) {
                executionPlan.execute();
            }

        }

        for (int i = 0; i < b.getSize(); i++) {
            assertEquals(a.get(i) * 2, b.get(i));
        }
    }

    @Test
    public void test02() throws TornadoExecutionPlanException {
        int numElements = 256;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);

        a.init(10);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestDynamic::compute, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b); //

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {

            // Run first time to obtain the best performance device
            executionPlan.withDynamicReconfiguration(Policy.PERFORMANCE, DRMode.PARALLEL) //
                    .execute();

            // Run a few iterations to get the device.
            for (int i = 0; i < 10; i++) {
                executionPlan.execute();
            }

        }

        for (int i = 0; i < b.getSize(); i++) {
            assertEquals(a.get(i) * 2, b.get(i));
        }
    }

    @Test
    public void test03() throws TornadoExecutionPlanException {
        int numElements = 16000;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);

        a.init(10);

        TaskGraph taskGraph = new TaskGraph("ss0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("tt0", TestDynamic::compute, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b); //

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {

            // Run first time to obtain the best performance device
            executionPlan.withDynamicReconfiguration(Policy.END_2_END, DRMode.SERIAL) //
                    .execute();

            // Run a few iterations to get the device.
            for (int i = 0; i < 10; i++) {
                executionPlan.execute();
            }
        }

        for (int i = 0; i < b.getSize(); i++) {
            assertEquals(a.get(i) * 2, b.get(i));
        }
    }

    @Test
    public void test04() throws TornadoExecutionPlanException {
        int numElements = 16000;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);

        a.init(10);

        TaskGraph taskGraph = new TaskGraph("ss0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("tt0", TestDynamic::compute, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b); //

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {

            // Run first time to obtain the best performance device
            executionPlan.withDynamicReconfiguration(Policy.END_2_END, DRMode.PARALLEL) //
                    .execute();

            // Run a few iterations to get the device.
            for (int i = 0; i < 10; i++) {
                executionPlan.execute();
            }
        }

        for (int i = 0; i < b.getSize(); i++) {
            assertEquals(a.get(i) * 2, b.get(i));
        }
    }

    @Test
    public void test05() throws TornadoExecutionPlanException {
        int numElements = 256;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray seq = new IntArray(numElements);

        a.init(10);

        compute(a, seq);
        compute2(seq, seq);

        TaskGraph taskGraph = new TaskGraph("pp") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestDynamic::compute, a, b) //
                .task("t1", TestDynamic::compute2, b, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {

            // Run first time to obtain the best performance device
            executionPlan.withDynamicReconfiguration(Policy.PERFORMANCE, DRMode.SERIAL) //
                    .execute();

            // Run a few iterations to get the device.
            for (int i = 0; i < 10; i++) {
                executionPlan.execute();
            }
        }

        for (int i = 0; i < b.getSize(); i++) {
            assertEquals(seq.get(i), b.get(i));
        }
    }

    @Test
    public void test06() throws TornadoExecutionPlanException {
        int numElements = 256;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray seq = new IntArray(numElements);

        a.init(10);

        compute(a, seq);
        compute2(seq, seq);

        TaskGraph taskGraph = new TaskGraph("pp") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestDynamic::compute, a, b) //
                .task("t1", TestDynamic::compute2, b, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {

            // Run first time to obtain the best performance device
            executionPlan.withDynamicReconfiguration(Policy.PERFORMANCE, DRMode.PARALLEL) //
                    .execute();

            // Run a few iterations to get the device.
            for (int i = 0; i < 10; i++) {
                executionPlan.execute();
            }
        }

        for (int i = 0; i < b.getSize(); i++) {
            assertEquals(seq.get(i), b.get(i));
        }
    }

    @Test
    public void test07() throws TornadoExecutionPlanException {
        int numElements = 16000;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);

        a.init(10);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestDynamic::compute, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {

            // Run first time to obtain the best performance device
            executionPlan.withDynamicReconfiguration(Policy.LATENCY, DRMode.SERIAL) //
                    .execute();
            // Run a few iterations to get the device.
            for (int i = 0; i < 10; i++) {
                executionPlan.execute();
            }
        }

        for (int i = 0; i < b.getSize(); i++) {
            assertEquals(a.get(i) * 2, b.get(i));
        }
    }

    @Test
    public void test08() throws TornadoExecutionPlanException {
        int numElements = 16000;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);

        a.init(10);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestDynamic::compute, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {

            // Run first time to obtain the best performance device
            executionPlan.withDynamicReconfiguration(Policy.LATENCY, DRMode.PARALLEL) //
                    .execute();
            // Run a few iterations to get the device.
            for (int i = 0; i < 10; i++) {
                executionPlan.execute();
            }
        }

        for (int i = 0; i < b.getSize(); i++) {
            assertEquals(a.get(i) * 2, b.get(i));
        }
    }
}
