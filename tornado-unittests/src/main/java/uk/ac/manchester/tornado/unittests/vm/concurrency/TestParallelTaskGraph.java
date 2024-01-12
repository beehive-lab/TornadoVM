/*
 * Copyright (c) 2023, 2024 APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.vm.concurrency;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.tools.Exceptions.UnsupportedConfigurationException;

/**
 *
 * <p>
 * How to test?:
 * </p>
 *
 * <p>
 * <code>
 * tornado-test -V --printBytecode --threadInfo uk.ac.manchester.tornado.unittests.vm.concurrency.TestParallelTaskGraph#testTwoBackendsSerial
 * </code>
 * </p>
 */
public class TestParallelTaskGraph extends TornadoTestBase {

    final int SIZE = 1024;

    public static void init(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = i;
        }
    }

    public static void multiply(float[] a, float alpha) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = (a[i] * i) + alpha;
        }
    }

    @Test
    public void testTwoDevicesSerial() {

        float[] a = new float[SIZE];
        float[] b = new float[SIZE];
        float[] refB = new float[SIZE];
        float[] refA = new float[SIZE];
        float alpha = 0.12f;

        Random r = new Random(31);
        IntStream.range(0, SIZE).forEach(i -> {
            a[i] = r.nextFloat();
            b[i] = r.nextFloat();
            refA[i] = a[i];
            refB[i] = b[i];
        });

        TaskGraph taskGraph = new TaskGraph("graph") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("task0", TestParallelTaskGraph::init, a) //
                .task("task1", TestParallelTaskGraph::multiply, b, alpha) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // Assume that the first drivers finds, at least two devices
        int deviceCount = TornadoRuntime.getTornadoRuntime().getDriver(0).getDeviceCount();
        if (deviceCount < 2) {
            throw new UnsupportedConfigurationException("Test requires at least two devices");
        }

        TornadoDevice device0 = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(0);
        TornadoDevice device1 = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(1);

        // Extension for multi-device: This will run one task after the other
        // (sequentially)
        executionPlan.withDevice("graph.task0", device0) //
                .withDevice("graph.task1", device1);

        executionPlan.execute();

        for (int i = 0; i < a.length; i++) {
            assertEquals(i, a[i], DELTA);
            assertEquals((refB[i] * i) + alpha, b[i], DELTA);
        }

    }

    @Test
    public void testTwoDevicesSerial2() {

        float[] a = new float[SIZE];
        float[] b = new float[SIZE];
        float[] refB = new float[SIZE];
        float[] refA = new float[SIZE];
        float alpha = 0.12f;

        Random r = new Random(31);
        IntStream.range(0, SIZE).forEach(i -> {
            a[i] = r.nextFloat();
            b[i] = r.nextFloat();
            refA[i] = a[i];
            refB[i] = b[i];
        });

        TaskGraph taskGraph = new TaskGraph("graph") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("task0", TestParallelTaskGraph::init, a) //
                .task("task1", TestParallelTaskGraph::multiply, b, alpha) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(1);

        executionPlan.execute();

        executionPlan.withDevice(device).execute();

        for (int i = 0; i < a.length; i++) {
            assertEquals(i, a[i], DELTA);
            assertEquals((refB[i] * i) + alpha, b[i], DELTA);
        }
    }

    @Test
    public void testTwoDevicesSerial3() {

        float[] a = new float[SIZE];
        float[] b = new float[SIZE];
        float[] refB = new float[SIZE];
        float[] refA = new float[SIZE];
        float alpha = 0.12f;

        Random r = new Random(31);
        IntStream.range(0, SIZE).forEach(i -> {
            a[i] = r.nextFloat();
            b[i] = r.nextFloat();
            refA[i] = a[i];
            refB[i] = b[i];
        });

        TaskGraph taskGraph = new TaskGraph("graph") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("task0", TestParallelTaskGraph::init, a) //
                .task("task1", TestParallelTaskGraph::multiply, b, alpha) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // Assume that the first drivers finds, at least two devices
        int deviceCount = TornadoRuntime.getTornadoRuntime().getDriver(0).getDeviceCount();
        if (deviceCount < 3) {
            throw new UnsupportedConfigurationException("Test requires at least three devices");
        }

        TornadoDevice device0 = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(0);
        TornadoDevice device1 = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(1);
        TornadoDevice device2 = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(2);

        // Extension for multi-device: This will run one task after the other
        // (sequentially)
        executionPlan.withDevice("graph.task0", device0) //
                .withDevice("graph.task1", device1);

        executionPlan.execute();

        executionPlan.withDevice("graph.task0", device2) //
                .withDevice("graph.task1", device2);

        multiply(refB, alpha);
        multiply(refB, alpha);

        for (int i = 0; i < a.length; i++) {
            assertEquals(i, a[i], DELTA);
            assertEquals(refB[i], b[i], DELTA_05);
        }

    }

    @Test
    public void testTwoDevicesSerial4() {

        float[] a = new float[SIZE];
        float[] b = new float[SIZE];
        float[] refB = new float[SIZE];
        float[] refA = new float[SIZE];
        float alpha = 0.12f;

        Random r = new Random(31);
        IntStream.range(0, SIZE).forEach(i -> {
            a[i] = r.nextFloat();
            b[i] = r.nextFloat();
            refA[i] = a[i];
            refB[i] = b[i];
        });

        TaskGraph taskGraph = new TaskGraph("graph") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("task0", TestParallelTaskGraph::init, a) //
                .task("task1", TestParallelTaskGraph::multiply, b, alpha) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // Assume that the first drivers finds, at least two devices
        int deviceCount = TornadoRuntime.getTornadoRuntime().getDriver(0).getDeviceCount();
        if (deviceCount < 3) {
            throw new UnsupportedConfigurationException("Test requires at least three devices");
        }

        TornadoDevice device0 = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(0);
        TornadoDevice device1 = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(1);

        // Extension for multi-device: This will run one task after the other
        // (sequentially)
        executionPlan.withDevice("graph.task0", device0) //
                .withDevice("graph.task1", device1);

        executionPlan.execute();

        executionPlan.withDevice("graph.task0", device1) //
                .withDevice("graph.task1", device0);

        multiply(refB, alpha);
        multiply(refB, alpha);

        for (int i = 0; i < a.length; i++) {
            assertEquals(i, a[i], DELTA);
            assertEquals(refB[i], b[i], DELTA_05);
        }

    }

    @Test
    public void testTwoDevicesConcurrent() {

        final int SIZE = 1024;
        float[] a = new float[SIZE];
        float[] b = new float[SIZE];
        float[] refB = new float[SIZE];
        float[] refA = new float[SIZE];
        float alpha = 0.12f;

        Random r = new Random(31);
        IntStream.range(0, SIZE).forEach(i -> {
            a[i] = r.nextFloat();
            b[i] = r.nextFloat();
            refA[i] = a[i];
            refB[i] = b[i];
        });

        TaskGraph taskGraph = new TaskGraph("graph") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("task0", TestParallelTaskGraph::init, a) //
                .task("task1", TestParallelTaskGraph::multiply, b, alpha) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // Assume that the first drivers finds, at least two devices
        int deviceCount = TornadoRuntime.getTornadoRuntime().getDriver(0).getDeviceCount();
        if (deviceCount < 2) {
            throw new UnsupportedConfigurationException("Test requires at least two devices");
        }

        TornadoDevice device0 = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(0);
        TornadoDevice device1 = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(1);

        // Extension for multi-device: This will run one task after the other in
        // parallel
        executionPlan.withConcurrentDevices() //
                .withDevice("graph.task0", device0) //
                .withDevice("graph.task1", device1);

        executionPlan.execute();

        for (int i = 0; i < a.length; i++) {
            assertEquals(i, a[i], DELTA);
            assertEquals((refB[i] * i) + alpha, b[i], DELTA);
        }
    }

    @Test
    public void testTwoDevicesConcurrentOnAndOff() {

        float[] a = new float[SIZE];
        float[] b = new float[SIZE];
        float[] refB = new float[SIZE];
        float[] refA = new float[SIZE];
        float alpha = 0.12f;

        Random r = new Random(31);
        IntStream.range(0, SIZE).forEach(i -> {
            a[i] = r.nextFloat();
            b[i] = r.nextFloat();
            refA[i] = a[i];
            refB[i] = b[i];
        });

        TaskGraph taskGraph = new TaskGraph("graph") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("task0", TestParallelTaskGraph::init, a) //
                .task("task1", TestParallelTaskGraph::multiply, b, alpha) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // Assume that the first drivers finds, at least two devices
        int deviceCount = TornadoRuntime.getTornadoRuntime().getDriver(0).getDeviceCount();
        if (deviceCount < 2) {
            throw new UnsupportedConfigurationException("Test requires at least two devices");
        }

        TornadoDevice device0 = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(0);
        TornadoDevice device1 = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(1);

        // Extension for multi-device: This will run one task after the other in
        // parallel
        executionPlan.withConcurrentDevices() //
                .withDevice("graph.task0", device0) //
                .withDevice("graph.task1", device1);

        // Blocking call
        executionPlan.execute();

        // Disable concurrent devices
        executionPlan.withoutConcurrentDevices() //
                .execute();

        multiply(refB, alpha);
        multiply(refB, alpha);

        for (int i = 0; i < a.length; i++) {
            assertEquals(i, a[i], DELTA);
            assertEquals(refB[i], b[i], DELTA_05);
        }
    }
}
