/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.unittests.batches;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.tools.Exceptions.UnsupportedConfigurationException;

/**
 * How to test?
 *
 * <p>
 * <code>
 *     tornado-test -V --fast uk.ac.manchester.tornado.unittests.batches.TestBatches
 * </code>
 * </p>
 */
public class TestBatches extends TornadoTestBase {

    @Override
    public void before() {
        super.before();
        System.setProperty("tornado.reuse.device.buffers", "False");
    }

    public static void compute(float[] array) {
        for (@Parallel int i = 0; i < array.length; i++) {
            // This assignment is on purpose for testing the whole array after merging all
            // batches.
            array[i] = array[i];
        }
    }

    public static void compute(float[] arrayA, float[] arrayB) {
        for (@Parallel int i = 0; i < arrayA.length; i++) {
            arrayB[i] = arrayA[i] + 100;
        }
    }

    public static void compute(float[] arrayA, float[] arrayB, float[] arrayC) {
        for (@Parallel int i = 0; i < arrayA.length; i++) {
            arrayC[i] = arrayA[i] + arrayB[i];
        }
    }

    public static void compute(int[] arrayA, int[] arrayB, int[] arrayC) {
        for (@Parallel int i = 0; i < arrayA.length; i++) {
            arrayC[i] = arrayA[i] + arrayB[i];
        }
    }

    public static void compute(long[] arrayA, long[] arrayB, long[] arrayC) {
        for (@Parallel int i = 0; i < arrayA.length; i++) {
            arrayC[i] = arrayA[i] + arrayB[i];
        }
    }

    public static void compute(double[] arrayA, double[] arrayB, double[] arrayC) {
        for (@Parallel int i = 0; i < arrayA.length; i++) {
            arrayC[i] = arrayA[i] + arrayB[i];
        }
    }

    public static void compute(short[] arrayA, short[] arrayB, short[] arrayC) {
        for (@Parallel int i = 0; i < arrayA.length; i++) {
            arrayC[i] = (short) (arrayA[i] + arrayB[i]);
        }
    }

    @Test
    public void test100MB() {

        long maxAllocMemory = checkMaxHeapAllocation(100, MemSize.MB);

        // Fill 800MB of float array
        int size = 200000000;
        // or as much as we can
        if (size * 4 > maxAllocMemory) {
            size = (int) ((maxAllocMemory / 4 / 2) * 0.9);
        }
        float[] arrayA = new float[size];
        float[] arrayB = new float[size];

        IntStream.range(0, arrayA.length).sequential().forEach(idx -> arrayA[idx] = idx);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, arrayA) //
                .task("t0", TestBatches::compute, arrayA, arrayB) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withBatch("100MB") // Slots of 100 MB
                .execute();

        for (int i = 0; i < arrayB.length; i++) {
            assertEquals(arrayA[i] + 100, arrayB[i], 0.1f);
        }

        executionPlan.freeDeviceMemory();
    }

    @Test
    public void test300MB() {

        long maxAllocMemory = checkMaxHeapAllocation(300, MemSize.MB);

        // Fill 1.0GB
        int size = 250_000_000;
        // Or as much as we can
        if (size * 4 > maxAllocMemory) {
            size = (int) ((maxAllocMemory / 4 / 2) * 0.9);
        }
        float[] arrayA = new float[size];
        float[] arrayB = new float[size];

        Random r = new Random();
        IntStream.range(0, arrayA.length).sequential().forEach(idx -> arrayA[idx] = r.nextFloat());

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, arrayA) //
                .task("t0", TestBatches::compute, arrayA, arrayB) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withBatch("300MB") // Slots of 300 MB
                .execute();

        for (int i = 0; i < arrayB.length; i++) {
            assertEquals(arrayA[i] + 100, arrayB[i], 1.0f);
        }

        executionPlan.freeDeviceMemory();
    }

    @Test
    public void test512MB() {

        long maxAllocMemory = checkMaxHeapAllocation(512, MemSize.MB);

        // Fill 800MB
        int size = 200000000;
        // or as much as we can
        if (size * 4 > maxAllocMemory) {
            size = (int) ((maxAllocMemory / 4) * 0.9);
        }
        float[] arrayA = new float[size];

        IntStream.range(0, arrayA.length).sequential().forEach(idx -> arrayA[idx] = idx);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, arrayA) //
                .task("t0", TestBatches::compute, arrayA) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayA);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withBatch("512MB") // Slots of 512 MB
                .execute();

        for (int i = 0; i < arrayA.length; i++) {
            assertEquals(i, arrayA[i], 0.1f);
        }

        executionPlan.freeDeviceMemory();
    }

    @Test
    public void test50MB() {

        long maxAllocMemory = checkMaxHeapAllocation(50, MemSize.MB);

        // Fill 80MB of input Array
        int size = 20000000;
        // or as much as we can
        if (size * 4 > maxAllocMemory) {
            size = (int) ((maxAllocMemory / 4 / 3) * 0.9);
        }
        float[] arrayA = new float[size];
        float[] arrayB = new float[size];
        float[] arrayC = new float[size];

        IntStream.range(0, arrayA.length).sequential().forEach(idx -> {
            arrayA[idx] = idx;
            arrayB[idx] = idx;
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, arrayA, arrayB) //
                .task("t0", TestBatches::compute, arrayA, arrayB, arrayC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withBatch("50MB") // Slots of 50 MB
                .execute();

        for (int i = 0; i < arrayA.length; i++) {
            assertEquals(arrayA[i] + arrayB[i], arrayC[i], 0.1f);
        }

        executionPlan.freeDeviceMemory();
    }

    @Test
    public void test50MBInteger() {

        long maxAllocMemory = checkMaxHeapAllocation(50, MemSize.MB);

        // Fill 80MB of input Array
        int size = 20000000;
        // or as much as we can
        if (size * 4 > maxAllocMemory) {
            size = (int) ((maxAllocMemory / 4 / 3) * 0.9);
        }
        int[] arrayA = new int[size];
        int[] arrayB = new int[size];
        int[] arrayC = new int[size];

        IntStream.range(0, arrayA.length).sequential().forEach(idx -> {
            arrayA[idx] = idx;
            arrayB[idx] = idx;
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, arrayA, arrayB) //
                .task("t0", TestBatches::compute, arrayA, arrayB, arrayC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withBatch("50MB") // Slots of 50 MB
                .execute();

        for (int i = 0; i < arrayA.length; i++) {
            assertEquals(arrayA[i] + arrayB[i], arrayC[i]);
        }
        executionPlan.freeDeviceMemory();
    }

    @Test
    public void test50MBShort() {

        long maxAllocMemory = checkMaxHeapAllocation(50, MemSize.MB);

        // Fill 160MB of input Array
        int size = 80000000;
        // or as much as we can
        if (size * 2 > maxAllocMemory) {
            size = (int) ((maxAllocMemory / 2 / 3) * 0.9);
        }
        short[] arrayA = new short[size];
        short[] arrayB = new short[size];
        short[] arrayC = new short[size];

        Random r = new Random();
        IntStream.range(0, arrayA.length).sequential().forEach(idx -> {
            arrayA[idx] = (short) r.nextInt(Short.MAX_VALUE / 2);
            arrayB[idx] = (short) r.nextInt(Short.MAX_VALUE / 2);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, arrayA, arrayB) //
                .task("t0", TestBatches::compute, arrayA, arrayB, arrayC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withBatch("50MB") // Slots of 50 MB
                .execute();

        for (int i = 0; i < arrayA.length; i++) {
            assertEquals(arrayA[i] + arrayB[i], arrayC[i]);
        }
        executionPlan.freeDeviceMemory();
    }

    @Test
    public void test50MBDouble() {

        long maxAllocMemory = checkMaxHeapAllocation(50, MemSize.MB);

        int size = 20000000;
        // or as much as we can
        if (size * 8 > maxAllocMemory) {
            size = (int) ((maxAllocMemory / 8 / 3) * 0.9);
        }
        double[] arrayA = new double[size];
        double[] arrayB = new double[size];
        double[] arrayC = new double[size];

        IntStream.range(0, arrayA.length).sequential().forEach(idx -> {
            arrayA[idx] = idx;
            arrayB[idx] = idx;
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, arrayA, arrayB) //
                .task("t0", TestBatches::compute, arrayA, arrayB, arrayC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withBatch("50MB") // Slots of 50 MB
                .execute();

        for (int i = 0; i < arrayA.length; i++) {
            assertEquals(arrayA[i] + arrayB[i], arrayC[i], 0.01);
        }
        executionPlan.freeDeviceMemory();
    }

    @Test
    public void test50MBLong() {

        long maxAllocMemory = checkMaxHeapAllocation(50, MemSize.MB);

        // Fill 160MB of input Array
        int size = 20000000;
        // or as much as we can
        if (size * 8 > maxAllocMemory) {
            size = (int) ((maxAllocMemory / 8 / 3) * 0.9);
        }
        long[] arrayA = new long[size];
        long[] arrayB = new long[size];
        long[] arrayC = new long[size];

        IntStream.range(0, arrayA.length).sequential().forEach(idx -> {
            arrayA[idx] = idx;
            arrayB[idx] = idx;
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, arrayA, arrayB) //
                .task("t0", TestBatches::compute, arrayA, arrayB, arrayC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withBatch("50MB") // Slots of 50 MB
                .execute();

        for (int i = 0; i < arrayA.length; i++) {
            assertEquals(arrayA[i] + arrayB[i], arrayC[i]);
        }
        executionPlan.freeDeviceMemory();
    }

    private long checkMaxHeapAllocation(int size, MemSize memSize) throws UnsupportedConfigurationException {
        long maxAllocMemory = getTornadoRuntime().getDefaultDevice().getDeviceContext().getMemoryManager().getHeapSize();

        long memThreshold = 0;

        switch (memSize) {
            case GB:
                memThreshold = (long) size * 1024 * 1024 * 1024;
                break;
            case MB:
                memThreshold = (long) size * 1024 * 1024;
                break;
            case TB:
                memThreshold = (long) size * 1024 * 1024 * 1024 * 1024;
                break;

        }

        // check if there is enough memory for at least one chunk
        if (maxAllocMemory < memThreshold) {
            throw new UnsupportedConfigurationException("Not enough memory to run the test");
        }

        return maxAllocMemory;
    }

    private enum MemSize {
        MB, GB, TB
    }

}
