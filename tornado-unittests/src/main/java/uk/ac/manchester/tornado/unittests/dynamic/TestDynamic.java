/*
 * Copyright (c) 2013-2018, 2022, APT Group, School of Computer Science,
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
package uk.ac.manchester.tornado.unittests.dynamic;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.DRMode;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.Policy;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to run?
 * <p>
 * <code>
 *     tornado-test -V uk.ac.manchester.tornado.unittests.dynamic.TestDynamic
 * </code>
 * </p>
 */
public class TestDynamic extends TornadoTestBase {

    public static void compute(int[] a, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            b[i] = a[i] * 2;
        }
    }

    public static void compute2(int[] a, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            b[i] = a[i] * 10;
        }
    }

    public static void saxpy(float alpha, float[] x, float[] y) {
        for (@Parallel int i = 0; i < y.length; i++) {
            y[i] = alpha * x[i];
        }
    }

    @Test
    public void testDynamicWithProfiler() {
        int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];

        Arrays.fill(a, 10);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestDynamic::compute, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // Run first time to obtain the best performance device
        executionPlan.withDynamicReconfiguration(Policy.PERFORMANCE, DRMode.SERIAL) //
                .execute();

        // Run a few iterations to get the device.
        for (int i = 0; i < 10; i++) {
            executionPlan.execute();
        }

        for (int i = 0; i < b.length; i++) {
            assertEquals(a[i] * 2, b[i]);
        }
    }

    @Test
    public void testDynamicWithProfilerE2E() {
        int numElements = 16000;
        int[] a = new int[numElements];
        int[] b = new int[numElements];

        Arrays.fill(a, 10);

        TaskGraph taskGraph = new TaskGraph("ss0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("tt0", TestDynamic::compute, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // Run first time to obtain the best performance device
        executionPlan.withDynamicReconfiguration(Policy.END_2_END, DRMode.PARALLEL) //
                .execute();

        // Run a few iterations to get the device.
        for (int i = 0; i < 10; i++) {
            executionPlan.execute();
        }

        for (int i = 0; i < b.length; i++) {
            assertEquals(a[i] * 2, b[i]);
        }
    }

    @Test
    public void testDynamicWithProfiler2() {
        int numElements = 4194304;
        float[] a = new float[numElements];
        float[] b = new float[numElements];

        Arrays.fill(a, 10);
        Arrays.fill(b, 0);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestDynamic::saxpy, 2.0f, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // Run first time to obtain the best performance device
        executionPlan.withDynamicReconfiguration(Policy.PERFORMANCE, DRMode.SERIAL) //
                .execute();

        for (int i = 0; i < b.length; i++) {
            assertEquals(a[i] * 2.0f, b[i], 0.01f);
        }
    }

    @Test
    public void testDynamicWithProfiler3() {
        int numElements = 4096;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] seq = new int[numElements];

        Arrays.fill(a, 10);

        compute2(a, seq);

        TaskGraph taskGraph = new TaskGraph("ts") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("task", TestDynamic::compute2, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // Run first time to obtain the best performance device
        executionPlan.withDynamicReconfiguration(Policy.PERFORMANCE, DRMode.SERIAL) //
                .execute();

        // Run a few iterations to get the device.
        for (int i = 0; i < 10; i++) {
            executionPlan.execute();
        }

        for (int i = 0; i < b.length; i++) {
            assertEquals(seq[i], b[i]);
        }
    }

    @Test
    public void testDynamicWithProfiler4() {
        int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] seq = new int[numElements];

        Arrays.fill(a, 10);

        compute(a, seq);
        compute2(seq, seq);

        TaskGraph taskGraph = new TaskGraph("pp") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestDynamic::compute, a, b) //
                .task("t1", TestDynamic::compute2, b, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // Run first time to obtain the best performance device
        executionPlan.withDynamicReconfiguration(Policy.PERFORMANCE, DRMode.SERIAL) //
                .execute();

        // Run a few iterations to get the device.
        for (int i = 0; i < 10; i++) {
            executionPlan.execute();
        }

        for (int i = 0; i < b.length; i++) {
            assertEquals(seq[i], b[i]);
        }
    }

    @Test
    public void testDynamicWinner() {
        int numElements = 16000;
        int[] a = new int[numElements];
        int[] b = new int[numElements];

        Arrays.fill(a, 10);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestDynamic::compute, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // Run first time to obtain the best performance device
        executionPlan.withDynamicReconfiguration(Policy.LATENCY, DRMode.PARALLEL) //
                .execute();
        // Run a few iterations to get the device.
        for (int i = 0; i < 10; i++) {
            executionPlan.execute();
        }

        for (int i = 0; i < b.length; i++) {
            assertEquals(a[i] * 2, b[i]);
        }
    }
}
