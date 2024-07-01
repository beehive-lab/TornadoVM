/*
 * Copyright (c) 2013-2020, 2024, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.unittests.batches;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Assert;
import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.tools.Exceptions.UnsupportedConfigurationException;

/**
 * How to test?
 *
 * <p>
 * <code>
 * tornado-test -V --fast uk.ac.manchester.tornado.unittests.batches.TestBatches
 * </code>
 * </p>
 */
public class TestBatches extends TornadoTestBase {

    @Override
    public void before() {
        super.before();
        System.setProperty("tornado.reuse.device.buffers", "False");
    }

    public static void compute(FloatArray array) {
        for (@Parallel int i = 0; i < array.getSize(); i++) {
            // This assignment is on purpose for testing the whole array after merging all
            // batches.
            array.set(i, array.get(i));
        }
    }

    public static void compute(FloatArray arrayA, FloatArray arrayB) {
        for (@Parallel int i = 0; i < arrayA.getSize(); i++) {
            arrayB.set(i, arrayA.get(i) + 100);
        }
    }

    public static void compute(FloatArray arrayA, FloatArray arrayB, FloatArray arrayC) {
        for (@Parallel int i = 0; i < arrayA.getSize(); i++) {
            arrayC.set(i, arrayA.get(i) + arrayB.get(i));
        }
    }

    public static void compute(IntArray arrayA, IntArray arrayB, IntArray arrayC) {
        for (@Parallel int i = 0; i < arrayA.getSize(); i++) {
            arrayC.set(i, arrayA.get(i) + arrayB.get(i));
        }
    }

    public static void compute(LongArray arrayA, LongArray arrayB, LongArray arrayC) {
        for (@Parallel int i = 0; i < arrayA.getSize(); i++) {
            arrayC.set(i, arrayA.get(i) + arrayB.get(i));
        }
    }

    public static void compute(DoubleArray arrayA, DoubleArray arrayB, DoubleArray arrayC) {
        for (@Parallel int i = 0; i < arrayA.getSize(); i++) {
            arrayC.set(i, arrayA.get(i) + arrayB.get(i));
        }
    }

    public static void compute(ShortArray arrayA, ShortArray arrayB, ShortArray arrayC) {
        for (@Parallel int i = 0; i < arrayA.getSize(); i++) {
            arrayC.set(i, (short) (arrayA.get(i) + arrayB.get(i)));
        }
    }

    static void compute(IntArray in, IntArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i));
        }
    }

    static void compute(IntArray in, LongArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, in.get(i));
        }
    }

    static void compute(int[] in, int[] out) {
        for (@Parallel int i = 0; i < in.length; i++) {
            out[i] = in[i];
        }
    }

    static void compute(int[] in, long[] out) {
        for (@Parallel int i = 0; i < out.length; i++) {
            out[i] = in[i];
        }
    }

    static void compute(int[] in, IntArray out) {
        for (@Parallel int i = 0; i < in.length; i++) {
            out.set(i, in[i]);
        }
    }

    static void compute(IntArray in, int[] out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out[i] = in.get(i);
        }
    }

    static void compute(int[] in, float[] out) {
        for (@Parallel int i = 0; i < in.length; i++) {
            out[i] = in[i];
        }
    }

    static void compute(IntArray in, FloatArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i));
        }
    }

    static void compute(int[] in, FloatArray out) {
        for (@Parallel int i = 0; i < in.length; i++) {
            out.set(i, in[i]);
        }
    }

    static void compute(IntArray in, float[] out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out[i] = in.get(i);
        }
    }

    public static void compute(FloatArray data, float beta) {
        for (@Parallel int i = 0; i < data.getSize(); i++) {
            data.set(i, i * 20 + beta);
        }
    }

    @Test
    public void test100MBSmall() throws TornadoExecutionPlanException {

        long maxAllocMemory = checkMaxHeapAllocationOnDevice(100, MemoryUnit.MB);

        // Fill 120MB of float array
        int size = 30000000;
        // or as much as we can
        if (size * 4 > maxAllocMemory) {
            size = (int) ((maxAllocMemory / 4 / 2) * 0.9);
        }
        FloatArray arrayA = new FloatArray(size);
        FloatArray arrayB = new FloatArray(size);

        IntStream.range(0, arrayA.getSize()).sequential().forEach(idx -> arrayA.set(idx, 0));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, arrayA) //
                .task("t0", TestBatches::compute, arrayA, arrayB) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withBatch("60MB") // Slots of 100 MB
                    .execute();
        }

        for (int i = 0; i < arrayB.getSize(); i++) {
            assertEquals(arrayA.get(i) + 100, arrayB.get(i), 0.1f);
        }

    }

    @Test
    public void test100MBSmallLazy() throws TornadoExecutionPlanException {

        long maxAllocMemory = checkMaxHeapAllocationOnDevice(100, MemoryUnit.MB);

        // Fill 120MB of float array
        int size = 30000000;
        // or as much as we can
        if (size * 4 > maxAllocMemory) {
            size = (int) ((maxAllocMemory / 4 / 2) * 0.9);
        }
        FloatArray arrayA = new FloatArray(size);
        FloatArray arrayB = new FloatArray(size);

        IntStream.range(0, arrayA.getSize()).sequential().forEach(idx -> arrayA.set(idx, 0));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, arrayA) //
                .task("t0", TestBatches::compute, arrayA, arrayB) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, arrayB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            TornadoExecutionResult tornadoExecutionResult = executionPlan.withBatch("60MB") // Slots of 100 MB
                    .execute();

            tornadoExecutionResult.transferToHost(arrayB);
        }

        for (int i = 0; i < arrayB.getSize(); i++) {
            assertEquals(arrayA.get(i) + 100, arrayB.get(i), 0.1f);
        }

    }

    @Test
    public void test100MB() throws TornadoExecutionPlanException {

        long maxAllocMemory = checkMaxHeapAllocationOnDevice(100, MemoryUnit.MB);

        // Fill 800MB of float array
        int size = 200000000;
        // or as much as we can
        if (size * 4 > maxAllocMemory) {
            size = (int) ((maxAllocMemory / 4 / 2) * 0.9);
        }
        FloatArray arrayA = new FloatArray(size);
        FloatArray arrayB = new FloatArray(size);

        IntStream.range(0, arrayA.getSize()).sequential().forEach(idx -> arrayA.set(idx, 0));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, arrayA) //
                .task("t0", TestBatches::compute, arrayA, arrayB) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withBatch("100MB") // Slots of 100 MB
                    .execute();
        }

        for (int i = 0; i < arrayB.getSize(); i++) {
            assertEquals(arrayA.get(i) + 100, arrayB.get(i), 0.1f);
        }

    }

    @Test
    public void test100MBLazy() throws TornadoExecutionPlanException {

        long maxAllocMemory = checkMaxHeapAllocationOnDevice(100, MemoryUnit.MB);

        // Fill 800MB of float array
        int size = 200000000;
        // or as much as we can
        if (size * 4 > maxAllocMemory) {
            size = (int) ((maxAllocMemory / 4 / 2) * 0.9);
        }
        FloatArray arrayA = new FloatArray(size);
        FloatArray arrayB = new FloatArray(size);

        IntStream.range(0, arrayA.getSize()).sequential().forEach(idx -> arrayA.set(idx, 0));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, arrayA) //
                .task("t0", TestBatches::compute, arrayA, arrayB) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, arrayB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            TornadoExecutionResult tornadoExecutionResult = executionPlan.withBatch("100MB") // Slots of 100 MB
                    .execute();
            tornadoExecutionResult.transferToHost(arrayB);
        }

        for (int i = 0; i < arrayB.getSize(); i++) {
            assertEquals(arrayA.get(i) + 100, arrayB.get(i), 0.1f);
        }

    }

    @Test
    public void test300MB() throws TornadoExecutionPlanException {

        long maxAllocMemory = checkMaxHeapAllocationOnDevice(300, MemoryUnit.MB);

        // Fill 1.0GB
        int size = 250_000_000;
        // Or as much as we can
        if (size * 4 > maxAllocMemory) {
            size = (int) ((maxAllocMemory / 4 / 2) * 0.9);
        }
        FloatArray arrayA = new FloatArray(size);
        FloatArray arrayB = new FloatArray(size);

        Random r = new Random();
        IntStream.range(0, arrayA.getSize()).sequential().forEach(idx -> arrayA.set(idx, r.nextFloat()));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, arrayA) //
                .task("t0", TestBatches::compute, arrayA, arrayB) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withBatch("300MB") // Slots of 300 MB
                    .execute();
        }

        for (int i = 0; i < arrayB.getSize(); i++) {
            assertEquals(arrayA.get(i) + 100, arrayB.get(i), 1.0f);
        }
    }

    @Test
    public void test300MBLazy() throws TornadoExecutionPlanException {

        long maxAllocMemory = checkMaxHeapAllocationOnDevice(300, MemoryUnit.MB);

        // Fill 1.0GB
        int size = 250_000_000;
        // Or as much as we can
        if (size * 4 > maxAllocMemory) {
            size = (int) ((maxAllocMemory / 4 / 2) * 0.9);
        }
        FloatArray arrayA = new FloatArray(size);
        FloatArray arrayB = new FloatArray(size);

        Random r = new Random();
        IntStream.range(0, arrayA.getSize()).sequential().forEach(idx -> arrayA.set(idx, r.nextFloat()));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, arrayA) //
                .task("t0", TestBatches::compute, arrayA, arrayB) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, arrayB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            TornadoExecutionResult tornadoExecutionResult = executionPlan.withBatch("300MB") // Slots of 300 MB
                    .execute();
            tornadoExecutionResult.transferToHost(arrayB);
        }

        for (int i = 0; i < arrayB.getSize(); i++) {
            assertEquals(arrayA.get(i) + 100, arrayB.get(i), 1.0f);
        }

    }

    @Test
    public void test512MB() throws TornadoExecutionPlanException {

        long maxAllocMemory = checkMaxHeapAllocationOnDevice(512, MemoryUnit.MB);

        // Fill 800MB
        int size = 200000000;
        // or as much as we can
        if (size * 4 > maxAllocMemory) {
            size = (int) ((maxAllocMemory / 4) * 0.9);
        }
        FloatArray arrayA = new FloatArray(size);

        IntStream.range(0, arrayA.getSize()).sequential().forEach(idx -> arrayA.set(idx, idx));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, arrayA) //
                .task("t0", TestBatches::compute, arrayA) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayA);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withBatch("512MB") // Slots of 512 MB
                    .execute();
        }

        for (int i = 0; i < arrayA.getSize(); i++) {
            assertEquals(i, arrayA.get(i), 0.1f);
        }
    }

    @Test
    public void test512MBLazy() throws TornadoExecutionPlanException {

        long maxAllocMemory = checkMaxHeapAllocationOnDevice(512, MemoryUnit.MB);

        // Fill 800MB
        int size = 200000000;
        // or as much as we can
        if (size * 4 > maxAllocMemory) {
            size = (int) ((maxAllocMemory / 4) * 0.9);
        }
        FloatArray arrayA = new FloatArray(size);

        IntStream.range(0, arrayA.getSize()).sequential().forEach(idx -> arrayA.set(idx, idx));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, arrayA) //
                .task("t0", TestBatches::compute, arrayA) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, arrayA);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            TornadoExecutionResult tornadoExecutionResult = executionPlan.withBatch("512MB") // Slots of 512 MB
                    .execute();
            tornadoExecutionResult.transferToHost(arrayA);
        }

        for (int i = 0; i < arrayA.getSize(); i++) {
            assertEquals(i, arrayA.get(i), 0.1f);
        }
    }

    @Test
    public void test50MB() throws TornadoExecutionPlanException {

        long maxAllocMemory = checkMaxHeapAllocationOnDevice(50, MemoryUnit.MB);

        // Fill 80MB of input Array
        int size = 20000000;
        // or as much as we can
        if (size * 4 > maxAllocMemory) {
            size = (int) ((maxAllocMemory / 4 / 3) * 0.9);
        }
        FloatArray arrayA = new FloatArray(size);
        FloatArray arrayB = new FloatArray(size);
        FloatArray arrayC = new FloatArray(size);

        IntStream.range(0, arrayA.getSize()).sequential().forEach(idx -> {
            arrayA.set(idx, idx);
            arrayB.set(idx, idx);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, arrayA, arrayB) //
                .task("t0", TestBatches::compute, arrayA, arrayB, arrayC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withBatch("50MB") // Slots of 50 MB
                    .execute();
        }

        for (int i = 0; i < arrayA.getSize(); i++) {
            assertEquals(arrayA.get(i) + arrayB.get(i), arrayC.get(i), 0.1f);
        }

    }

    @Test
    public void test50MBInteger() throws TornadoExecutionPlanException {

        long maxAllocMemory = checkMaxHeapAllocationOnDevice(50, MemoryUnit.MB);

        // Fill 80MB of input Array
        int size = 20000000;
        // or as much as we can
        if (size * 4 > maxAllocMemory) {
            size = (int) ((maxAllocMemory / 4 / 3) * 0.9);
        }
        IntArray arrayA = new IntArray(size);
        IntArray arrayB = new IntArray(size);
        IntArray arrayC = new IntArray(size);

        IntStream.range(0, arrayA.getSize()).sequential().forEach(idx -> {
            arrayA.set(idx, idx);
            arrayB.set(idx, idx);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, arrayA, arrayB) //
                .task("t0", TestBatches::compute, arrayA, arrayB, arrayC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withBatch("50MB") // Slots of 50 MB
                    .execute();
        }

        for (int i = 0; i < arrayA.getSize(); i++) {
            assertEquals((arrayA.get(i) + arrayB.get(i)), arrayC.get(i));
        }
    }

    @Test
    public void test50MBShort() throws TornadoExecutionPlanException {

        long maxAllocMemory = checkMaxHeapAllocationOnDevice(50, MemoryUnit.MB);

        // Fill 160MB of input Array
        int size = 80000000;
        // or as much as we can
        if (size * 2 > maxAllocMemory) {
            size = (int) ((maxAllocMemory / 2 / 3) * 0.9);
        }
        ShortArray arrayA = new ShortArray(size);
        ShortArray arrayB = new ShortArray(size);
        ShortArray arrayC = new ShortArray(size);

        Random r = new Random();
        IntStream.range(0, arrayA.getSize()).sequential().forEach(idx -> {
            arrayA.set(idx, (short) r.nextInt(Short.MAX_VALUE / 2));
            arrayB.set(idx, (short) r.nextInt(Short.MAX_VALUE / 2));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, arrayA, arrayB) //
                .task("t0", TestBatches::compute, arrayA, arrayB, arrayC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withBatch("50MB") // Slots of 50 MB
                    .execute();
        }

        for (int i = 0; i < arrayA.getSize(); i++) {
            assertEquals(arrayA.get(i) + arrayB.get(i), arrayC.get(i));
        }
    }

    @Test
    public void test50MBDouble() throws TornadoExecutionPlanException {

        long maxAllocMemory = checkMaxHeapAllocationOnDevice(50, MemoryUnit.MB);

        int size = 20000000;
        // or as much as we can
        if (size * 8 > maxAllocMemory) {
            size = (int) ((maxAllocMemory / 8 / 3) * 0.9);
        }
        DoubleArray arrayA = new DoubleArray(size);
        DoubleArray arrayB = new DoubleArray(size);
        DoubleArray arrayC = new DoubleArray(size);

        IntStream.range(0, arrayA.getSize()).sequential().forEach(idx -> {
            arrayA.set(idx, idx);
            arrayB.set(idx, idx);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, arrayA, arrayB) //
                .task("t0", TestBatches::compute, arrayA, arrayB, arrayC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withBatch("50MB") // Slots of 50 MB
                    .execute();
        }

        for (int i = 0; i < arrayA.getSize(); i++) {
            assertEquals(arrayA.get(i) + arrayB.get(i), arrayC.get(i), 0.01);
        }
    }

    @Test
    public void test50MBLong() throws TornadoExecutionPlanException {

        long maxAllocMemory = checkMaxHeapAllocationOnDevice(50, MemoryUnit.MB);

        // Fill 160MB of input Array
        int size = 20000000;
        // or as much as we can
        if (size * 8 > maxAllocMemory) {
            size = (int) ((maxAllocMemory / 8 / 3) * 0.9);
        }
        LongArray arrayA = new LongArray(size);
        LongArray arrayB = new LongArray(size);
        LongArray arrayC = new LongArray(size);

        IntStream.range(0, arrayA.getSize()).sequential().forEach(idx -> {
            arrayA.set(idx, idx);
            arrayB.set(idx, idx);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, arrayA, arrayB) //
                .task("t0", TestBatches::compute, arrayA, arrayB, arrayC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withBatch("50MB") // Slots of 50 MB
                    .execute();
        }

        for (int i = 0; i < arrayA.getSize(); i++) {
            assertEquals(arrayA.get(i) + arrayB.get(i), arrayC.get(i));
        }
    }

    @Test
    public void testSameInputSizeAndTypeRestriction() throws TornadoExecutionPlanException {
        // total input size mismatch for IntArray
        checkMaxHeapAllocationOnDevice(5, MemoryUnit.MB);
        IntArray a0 = new IntArray(2 * 1_000_000);
        IntArray a1 = new IntArray(3 * 1_000_000);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a0) //
                .task("t0", TestBatches::compute, a0, a1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a1);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            Assert.assertThrows(TornadoBailoutRuntimeException.class, () -> executionPlan.withBatch("1MB").execute());
        }
    }

    @Test
    public void testSameInputSizeAndTypeRestrictionJavaArrays() throws TornadoExecutionPlanException {
        // total input size mismatch for int[]
        checkMaxHeapAllocationOnDevice(5, MemoryUnit.MB);
        int[] a0 = new int[2 * 1_000_000];
        int[] a1 = new int[3 * 1_000_000];

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a0) //
                .task("t0", TestBatches::compute, a0, a1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a1);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            Assert.assertThrows(TornadoBailoutRuntimeException.class, () -> executionPlan.withBatch("1MB").execute());
        }
    }

    @Test
    public void testSameInputTypeRestriction() throws TornadoExecutionPlanException {
        // IntArray is NOT compatible with LongArray even if the total input size is equal
        checkMaxHeapAllocationOnDevice(6, MemoryUnit.MB);
        IntArray a0 = new IntArray(4 * 1_000_000);
        LongArray a1 = new LongArray(2 * 1_000_000);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a0) //
                .task("t0", TestBatches::compute, a0, a1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a1);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            Assert.assertThrows(TornadoBailoutRuntimeException.class, () -> executionPlan.withBatch("1MB").execute());
        }
    }

    @Test
    public void testSameInputTypeRestrictionJavaArrays() throws TornadoExecutionPlanException {
        // int[] is NOT compatible with long[] even if the total input size is equal
        checkMaxHeapAllocationOnDevice(6, MemoryUnit.MB);
        int[] a0 = new int[4 * 1_000_000];
        long[] a1 = new long[2 * 1_000_000];

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a0) //
                .task("t0", TestBatches::compute, a0, a1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a1);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            Assert.assertThrows(TornadoBailoutRuntimeException.class, () -> executionPlan.withBatch("1MB").execute());
        }
    }

    @Test
    public void testSameInputSize() throws TornadoExecutionPlanException {
        // IntArray is compatible with FloatArray for the same # of elements
        checkMaxHeapAllocationOnDevice(4, MemoryUnit.MB);
        IntArray a0 = new IntArray(2 * 1_000_000);
        IntStream.range(0, a0.getSize()).forEach(i -> a0.set(i, i));
        FloatArray a1 = new FloatArray(2 * 1_000_000);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a0) //
                .task("t0", TestBatches::compute, a0, a1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a1);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withBatch("1MB").execute();
        }

        for (int i = 0; i < a1.getSize(); i++) {
            assertEquals(a0.get(i), a1.get(i), 1e-20);
        }
    }

    @Test
    public void testSameInputSizeJavaArrays() throws TornadoExecutionPlanException {
        // int[] is compatible with float[] for the same # of elements
        checkMaxHeapAllocationOnDevice(4, MemoryUnit.MB);
        int[] a0 = new int[2 * 1_000_000];
        IntStream.range(0, a0.length).forEach(i -> a0[i] = i);
        float[] a1 = new float[2 * 1_000_000];

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a0) //
                .task("t0", TestBatches::compute, a0, a1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a1);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withBatch("1MB").execute();
        }

        for (int i = 0; i < a1.length; i++) {
            assertEquals(a0[i], a1[i], 1e-20);
        }
    }

    @Test
    public void testSameInputSizeJavaToTornado() throws TornadoExecutionPlanException {
        // int[] is compatible with FloatArray for the same # of elements
        checkMaxHeapAllocationOnDevice(4, MemoryUnit.MB);
        int[] a0 = new int[2 * 1_000_000];
        IntStream.range(0, a0.length).forEach(i -> a0[i] = i);
        FloatArray a1 = new FloatArray(2 * 1_000_000);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a0) //
                .task("t0", TestBatches::compute, a0, a1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a1);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withBatch("1MB").execute();
        }

        for (int i = 0; i < a1.getSize(); i++) {
            assertEquals(a0[i], a1.get(i), 1e-20);
        }
    }

    @Test
    public void testSameInputSizeTornadoToJava() throws TornadoExecutionPlanException {
        // IntArray is compatible with float[] for the same # of elements
        checkMaxHeapAllocationOnDevice(4, MemoryUnit.MB);
        IntArray a0 = new IntArray(2 * 1_000_000);
        IntStream.range(0, a0.getSize()).forEach(i -> a0.set(i, i));
        float[] a1 = new float[2 * 1_000_000];

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a0) //
                .task("t0", TestBatches::compute, a0, a1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a1);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withBatch("1MB").execute();
        }

        for (int i = 0; i < a1.length; i++) {
            assertEquals(a0.get(i), a1[i], 1e-20);
        }
    }

    @Test
    public void testSameInputSizeAndTypeJavaToTornado() throws TornadoExecutionPlanException {
        // int[] is compatible with IntArray for the same # of elements
        checkMaxHeapAllocationOnDevice(4, MemoryUnit.MB);
        int[] a0 = new int[2 * 1_000_000];
        IntStream.range(0, a0.length).forEach(i -> a0[i] = i);
        IntArray a1 = new IntArray(2 * 1_000_000);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a0) //
                .task("t0", TestBatches::compute, a0, a1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a1);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withBatch("1MB").execute();
        }

        for (int i = 0; i < a1.getSize(); i++) {
            assertEquals(a0[i], a1.get(i));
        }
    }

    @Test
    public void testSameInputSizeAndTypeTornadoToJava() throws TornadoExecutionPlanException {
        // IntArray is compatible with int[] for the same # of elements
        checkMaxHeapAllocationOnDevice(4, MemoryUnit.MB);
        IntArray a0 = new IntArray(2 * 1_000_000);
        IntStream.range(0, a0.getSize()).forEach(i -> a0.set(i, i));
        int[] a1 = new int[2 * 1_000_000];

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a0) //
                .task("t0", TestBatches::compute, a0, a1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a1);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withBatch("1MB").execute();
        }

        for (int i = 0; i < a1.length; i++) {
            assertEquals(a0.get(i), a1[i]);
        }
    }

    public static void parallelInitialization(FloatArray data) {
        for (@Parallel int i = 0; i < data.getSize(); i++) {
            data.set(i, i);
        }
    }

    public static void compute2(FloatArray data) {
        for (@Parallel int i = 0; i < data.getSize(); i++) {
            float value = data.get(i);
            data.set(i, value * 2);
        }
    }

    @Test
    public void testBatchNotEven() throws TornadoExecutionPlanException {
        checkMaxHeapAllocationOnDevice(64, MemoryUnit.MB);

        // Allocate ~ 64MB
        FloatArray array = new FloatArray(1024 * 1024 * 16);
        FloatArray arraySeq = new FloatArray(1024 * 1024 * 16);
        for (int i = 0; i < arraySeq.getSize(); i++) {
            arraySeq.set(i, i);
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestBatches::parallelInitialization, array) //
                .task("t1", TestBatches::compute2, array) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, array);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withBatch("10MB") // Batches of 10MB
                    .execute();
        }

        for (int i = 0; i < array.getSize(); i++) {
            assertEquals(arraySeq.get(i) * 2, array.get(i), 0.01f);
        }
    }

    @Test
    public void testBatchNotEven2() throws TornadoExecutionPlanException {
        checkMaxHeapAllocationOnDevice(64, MemoryUnit.MB);

        // Allocate ~ 64MB
        FloatArray array = new FloatArray(1024 * 1024 * 16);
        FloatArray array2 = new FloatArray(1024 * 1024 * 16);
        array.init(1.0f);
        array2.init(1.0f);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, array) //
                .task("t1", TestBatches::compute2, array) //
                .task("t2", TestBatches::compute2, array) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, array);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withBatch("10MB") // Batches of 10MB
                    .execute();
        }

        for (int i = 0; i < array.getSize(); i++) {
            assertEquals(array2.get(i) * 4, array.get(i), 0.01f);
        }
    }

    @Test
    public void testBatchNotEven2Lazy() throws TornadoExecutionPlanException {
        checkMaxHeapAllocationOnDevice(64, MemoryUnit.MB);

        // Allocate ~ 64MB
        FloatArray array = new FloatArray(1024 * 1024 * 16);
        FloatArray array2 = new FloatArray(1024 * 1024 * 16);
        array.init(1.0f);
        array2.init(1.0f);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, array) //
                .task("t1", TestBatches::compute2, array) //
                .task("t2", TestBatches::compute2, array) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, array);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            TornadoExecutionResult tornadoExecutionResult = executionPlan.withBatch("10MB") // Batches of 10MB
                    .execute();
            tornadoExecutionResult.transferToHost(array);
        }

        for (int i = 0; i < array.getSize(); i++) {
            assertEquals(array2.get(i) * 4, array.get(i), 0.01f);
        }
    }

    @Test
    public void testBatchThreadIndex() throws TornadoExecutionPlanException {
        checkMaxHeapAllocationOnDevice(64, MemoryUnit.MB);

        // Allocate ~ 64MB
        FloatArray array = new FloatArray(1024 * 1024 * 16);
        FloatArray arraySeq = new FloatArray(1024 * 1024 * 16);

        float beta = 2.0f;
        for (int i = 0; i < arraySeq.getSize(); i++) {
            arraySeq.set(i, i * 20 + beta);
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestBatches::compute, array, beta) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, array);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withBatch("10MB") // Batches of 10MB
                    .execute();
        }

        for (int i = 0; i < array.getSize(); i++) {
            assertEquals(arraySeq.get(i), array.get(i), 0.01f);
        }
    }

    private long checkMaxHeapAllocationOnDevice(int size, MemoryUnit memoryUnit) throws UnsupportedConfigurationException {
        long maxAllocMemory = getTornadoRuntime().getDefaultDevice().getDeviceContext().getMemoryManager().getHeapSize();

        long memThreshold = switch (memoryUnit) {
            case GB -> (long) size * 1024 * 1024 * 1024;
            case MB -> (long) size * 1024 * 1024;
            case TB -> (long) size * 1024 * 1024 * 1024 * 1024;
        };
        // check if there is enough memory for at least one chunk
        if (maxAllocMemory < memThreshold) {
            throw new UnsupportedConfigurationException("Not enough memory to run the test");
        }
        return maxAllocMemory;
    }

    private enum MemoryUnit {
        MB, GB, TB
    }

}
