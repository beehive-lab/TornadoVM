/*
 * Copyright (c) 2013-2022, 2025, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.unittests.atomics;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.AccessorParameters;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.TornadoVMIntrinsics;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.unittests.common.TornadoNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to test?
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.atomics.TestAtomics
 * </code>
 * </p>
 */
public class TestAtomics extends TornadoTestBase {

    /**
     * Approach using a compiler-intrinsic in TornadoVM.
     *
     * @param a
     *     Input array. It stores the addition with an atomic variable.
     */
    public static void atomic03(IntArray a) {
        final int size = 100;
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            int j = i % size;
            a.set(j, TornadoVMIntrinsics.atomic_add(a, j, 1));
        }
    }

    /**
     * Approach using an API for Atomics. This provides atomics using the Java semantics (block a single elements). Note that, in OpenCL, this single elements has to be present in the device's global
     * memory.
     *
     * @param input
     *     input array
     */
    public static void atomic04(IntArray input) {
        AtomicInteger tai = new AtomicInteger(200);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            input.set(i, tai.incrementAndGet());
        }
    }

    public static void atomic04Get(IntArray input) {
        AtomicInteger tai = new AtomicInteger(200);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            input.set(i, tai.incrementAndGet());
            int a = tai.get();
            if (a == 201) {
                input.set(i, 0);
            }
        }
    }

    public static void atomic06(IntArray a, IntArray b) {
        AtomicInteger taiA = new AtomicInteger(200);
        AtomicInteger taiB = new AtomicInteger(100);
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, taiA.incrementAndGet());
            b.set(i, taiB.incrementAndGet());
        }
    }

    public static void atomic07(IntArray input) {
        AtomicInteger ai = new AtomicInteger(200);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            input.set(i, ai.incrementAndGet());
        }
    }

    public static void atomic08(IntArray input) {
        AtomicInteger ai = new AtomicInteger(200);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            input.set(i, ai.decrementAndGet());
        }
    }

    public static int callAtomic(IntArray input, int i, AtomicInteger ai) {
        return input.get(i) + ai.incrementAndGet();
    }

    public static void atomic09(IntArray input, AtomicInteger ai) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            input.set(i, callAtomic(input, i, ai));
        }
    }

    public static void atomic10(IntArray input, AtomicInteger ai, AtomicInteger bi) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            input.set(i, input.get(i) + ai.incrementAndGet() + bi.incrementAndGet());
        }
    }

    public static void atomic13DecrementAndGet(IntArray input, AtomicInteger ai) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            input.set(i, input.get(i) + ai.decrementAndGet());
        }
    }

    public static void atomic13GetAndDecrement(IntArray input, AtomicInteger ai) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            input.set(i, input.get(i) + ai.getAndDecrement());
        }
    }

    public static void atomic14(IntArray input, AtomicInteger ai, AtomicInteger bi) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            input.set(i, input.get(i) + ai.incrementAndGet());
            input.set(i, input.get(i) + bi.decrementAndGet());
        }
    }

    /**
     * This example combines an atomic created inside the compute kernel with an atomic passed as an argument.
     *
     * @param input
     *     Input array
     * @param ai
     *     Atomic Integer stored in Global Memory (atomic-region)
     */
    public static void atomic15(IntArray input, AtomicInteger ai) {
        AtomicInteger bi = new AtomicInteger(500);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            input.set(i, input.get(i) + ai.incrementAndGet());
            input.set(i, input.get(i) + bi.incrementAndGet());
        }
    }

    public static void atomic16(IntArray input, AtomicInteger ai) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            input.set(i, input.get(i) + ai.incrementAndGet());
        }
    }

    public static void atomic17GetAndIncrement(IntArray input, AtomicInteger ai) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            input.set(i, ai.getAndIncrement());
        }
    }

    public static void atomic17GetAndIncrement(KernelContext context, IntArray input, AtomicInteger ai) {
        int i = context.globalIdx;
        if (i < input.getSize()) {
            input.set(i, ai.getAndIncrement());
        }
    }

    public static void atomic17IncrementAndGet(IntArray input, AtomicInteger ai) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            input.set(i, ai.incrementAndGet());
        }
    }

    public static void atomic17IncrementAndGet(KernelContext context, IntArray input, AtomicInteger ai) {
        int i = context.globalIdx;
        if (i < input.getSize()) {
            input.set(i, ai.incrementAndGet());
        }
    }

    public static void atomic18(KernelContext context, IntArray data) {
        for (@Parallel int i = 0; i < data.getSize(); i++) {
            context.atomicAdd(data, i, 1);
        }
    }

    public static void atomic18(KernelContext context, int[] data, int[] incrementingValues) {
        for (@Parallel int i = 0; i < data.length; i++) {
            context.atomicAdd(data, i, incrementingValues[i]);
        }
    }

    public static void atomic18(KernelContext context, LongArray data) {
        for (@Parallel int i = 0; i < data.getSize(); i++) {
            context.atomicAdd(data, i, 1);
        }
    }

    public static void atomic18(KernelContext context, FloatArray data) {
        for (@Parallel int i = 0; i < data.getSize(); i++) {
            context.atomicAdd(data, i, 1);
        }
    }

    public static void atomic18(KernelContext context, DoubleArray data) {
        for (@Parallel int i = 0; i < data.getSize(); i++) {
            context.atomicAdd(data, i, 1);
        }
    }

    public static void atomic18Kernel(KernelContext context, IntArray input) {
        int tid = context.globalIdx;

        if (tid < input.getSize()) {
            context.atomicAdd(input, tid, 1);
        }
    }

    public static void atomic19(KernelContext context, IntArray input, IntArray output) {
        for (@Parallel int tid = 0; tid < input.getSize(); tid++) {
            int index = input.get(tid);
            context.atomicAdd(output, index, 1);
        }
    }

    public static void atomic19Kernel(KernelContext context, IntArray input, IntArray output) {
        int tid = context.globalIdx;

        if (tid < input.getSize()) {
            int index = input.get(tid);
            context.atomicAdd(output, index, 1);
        }
    }

    @TornadoNotSupported
    public void testAtomic03() throws TornadoExecutionPlanException {
        final int size = 1024;
        IntArray a = new IntArray(size);
        IntArray b = new IntArray(size);

        a.init(1);
        b.init(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestAtomics::atomic03, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        atomic03(b);
        for (int i = 0; i < a.getSize(); i++) {
            assertEquals(b.get(i), a.get(i));
        }
    }

    @Test
    public void testAtomic04() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        IntArray a = new IntArray(size);
        a.init(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestAtomics::atomic04, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            TornadoExecutionResult executionResult = executionPlan.execute();

            if (!executionResult.isReady()) {
                fail();
            }
        }

        // On GPUs and FPGAs, threads within the same work-group run in parallel.
        // Increments will be performed atomically when using TornadoAtomicInteger.
        // However, the order is not guaranteed. For this test, we need to check that
        // there are not repeated values in the output array.
        boolean repeated = isValueRepeated(a);
        assertFalse(repeated);
    }

    @Test
    public void testAtomic04Get() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        IntArray a = new IntArray(size);
        a.init(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestAtomics::atomic04Get, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            TornadoExecutionResult executionResult = executionPlan.execute();

            if (!executionResult.isReady()) {
                fail();
            }
        }

        // On GPUs and FPGAs, threads within the same work-group run in parallel.
        // Increments will be performed atomically when using TornadoAtomicInteger.
        // However, the order is not guaranteed. For this test, we need to check that
        // there are not repeated values in the output array.
        boolean repeated = isValueRepeated(a);
        assertFalse(repeated);
    }

    /**
     * How to test?
     *
     * <p>
     * <code>
     * $ tornado-test -V -pk --debug -J"-Ddevice=0" uk.ac.manchester.tornado.unittests.atomics.TestAtomics#testAtomic05_precompiled
     * </code>
     * </p>
     */
    @Test
    public void testAtomic05_precompiled() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        IntArray a = new IntArray(size);
        IntArray b = new IntArray(1);
        a.init(0);

        String deviceToRun = System.getProperties().getProperty("device", "0");
        int deviceNumber = Integer.parseInt(deviceToRun);

        TornadoDevice defaultDevice = TornadoRuntimeProvider.getTornadoRuntime().getBackend(0).getDevice(deviceNumber);
        String tornadoSDK = System.getenv("TORNADO_SDK");

        AccessorParameters accessorParameters = new AccessorParameters(2);
        accessorParameters.set(0, a, Access.WRITE_ONLY);
        accessorParameters.set(1, b, Access.WRITE_ONLY);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .prebuiltTask("t0", //
                        "add", //
                        tornadoSDK + "/examples/generated/atomics.cl", //
                        accessorParameters, //
                        new int[] { 155 }   // Array for AtomicsInteger - Initial int value
                ).transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        WorkerGrid workerGrid = new WorkerGrid1D(32);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .withDevice(defaultDevice) //
                    .execute();
        }

        boolean repeated = isValueRepeated(a);
        assertFalse(repeated);
    }

    @Test
    public void testAtomic06() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 2048;
        IntArray a = new IntArray(size);
        IntArray b = new IntArray(size);

        a.init(1);
        b.init(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestAtomics::atomic06, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            TornadoExecutionResult executionResult = executionPlan.execute();
            if (!executionResult.isReady()) {
                fail();
            }
        }

        boolean repeated = isValueRepeated(a);
        repeated &= isValueRepeated(a);

        assertFalse(repeated);
    }

    @Test
    public void testAtomic07() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        IntArray a = new IntArray(size);
        a.init(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestAtomics::atomic07, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            TornadoExecutionResult executionResult = executionPlan.execute();

            if (!executionResult.isReady()) {
                fail();
            }
        }
        boolean repeated = isValueRepeated(a);
        assertFalse(repeated);
    }

    @Test
    public void testAtomic08_decrementAndGet() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        IntArray a = new IntArray(size);
        a.init(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestAtomics::atomic08, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            TornadoExecutionResult executionResult = executionPlan.execute();
            if (!executionResult.isReady()) {
                fail();
            }
        }
        boolean repeated = isValueRepeated(a);
        assertFalse(repeated);
    }

    private boolean isValueRepeated(IntArray array) {
        HashSet<Integer> set = new HashSet<>();
        boolean repeated = false;
        for (int i = 0; i < array.getSize(); i++) {
            if (!set.contains(array.get(i))) {
                set.add(array.get(i));
            } else {
                repeated = true;
                break;
            }
        }
        return repeated;
    }

    @Test
    public void testAtomic09() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        IntArray a = new IntArray(size);
        a.init(1);

        final int initialValue = 311;

        AtomicInteger ai = new AtomicInteger(initialValue);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, ai) //
                .task("t0", TestAtomics::atomic09, a, ai) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, ai);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        boolean repeated = isValueRepeated(a);

        int lastValue = ai.get();
        assertFalse(repeated);
        assertEquals(initialValue + size, lastValue);
    }

    @Test
    public void testAtomic10() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        IntArray a = new IntArray(size);
        a.init(1);

        final int initialValue = 311;

        AtomicInteger ai = new AtomicInteger(initialValue);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestAtomics::atomic09, a, ai) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, ai);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        boolean repeated = isValueRepeated(a);

        int lastValue = ai.get();
        assertFalse(repeated);
        assertEquals(initialValue + size, lastValue);
    }

    @Test
    public void testAtomic11() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        IntArray a = new IntArray(size);
        a.init(1);

        final int initialValue = 311;

        AtomicInteger ai = new AtomicInteger(initialValue);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestAtomics::atomic09, a, ai) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, ai, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        boolean repeated = isValueRepeated(a);

        int lastValue = ai.get();
        assertFalse(repeated);
        assertEquals(initialValue + size, lastValue);
    }

    @Test
    public void testAtomic12() throws TornadoExecutionPlanException {
        // Calling multiple atomics
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        IntArray a = new IntArray(size);
        a.init(1);

        final int initialValueA = 311;
        final int initialValueB = 500;

        AtomicInteger ai = new AtomicInteger(initialValueA);
        AtomicInteger bi = new AtomicInteger(initialValueB);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestAtomics::atomic10, a, ai, bi) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, ai, a, bi);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        boolean repeated = isValueRepeated(a);

        int lastValue = ai.get();
        assertFalse(repeated);
        assertEquals(initialValueA + size, lastValue);

        lastValue = bi.get();
        assertEquals(initialValueB + size, lastValue);

    }

    @Test
    public void testAtomic13_decrementAndGet() throws TornadoExecutionPlanException {
        // Calling multiple atomics
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        Random random = new Random();
        final int size = 32;
        IntArray a = new IntArray(size);
        IntArray dataSequential = new IntArray(size);
        for (int i = 0; i < size; i++) {
            int intRandomValue = random.nextInt();
            a.set(i, intRandomValue);
            dataSequential.set(i, intRandomValue);
        }

        final int initialValueA = 311;
        AtomicInteger ai = new AtomicInteger(initialValueA);
        AtomicInteger jai = new AtomicInteger(initialValueA);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestAtomics::atomic13DecrementAndGet, a, ai) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, ai, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        atomic13DecrementAndGet(dataSequential, jai);

        boolean repeated = isValueRepeated(a);
        int lastValue = ai.get();
        assertFalse(repeated);
        assertEquals(initialValueA - size, lastValue);
        assertArrayEquals(dataSequential.toHeapArray(), a.toHeapArray());
    }

    @Test
    public void testAtomic13_getAndDecrement() throws TornadoExecutionPlanException {
        // Calling multiple atomics
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        Random random = new Random();
        final int size = 32;
        IntArray a = new IntArray(size);
        IntArray dataSequential = new IntArray(size);
        for (int i = 0; i < size; i++) {
            int intRandomValue = random.nextInt();
            a.set(i, intRandomValue);
            dataSequential.set(i, intRandomValue);
        }

        final int initialValueA = 311;
        AtomicInteger ai = new AtomicInteger(initialValueA);
        AtomicInteger jai = new AtomicInteger(initialValueA);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestAtomics::atomic13GetAndDecrement, a, ai) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, ai, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        atomic13GetAndDecrement(dataSequential, jai);

        boolean repeated = isValueRepeated(a);
        int lastValue = ai.get();
        assertFalse(repeated);
        assertEquals(initialValueA - size, lastValue);
        assertArrayEquals(dataSequential.toHeapArray(), a.toHeapArray());
    }

    @Test
    public void testAtomic14() throws TornadoExecutionPlanException {
        // Calling multiple atomics
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        IntArray a = new IntArray(size);
        a.init(1);

        final int initialValueA = 311;
        final int initialValueB = 50;
        AtomicInteger ai = new AtomicInteger(initialValueA);
        AtomicInteger bi = new AtomicInteger(initialValueB);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestAtomics::atomic14, a, ai, bi) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, ai, a, bi);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        int lastValue = ai.get();
        assertEquals(initialValueA + size, lastValue);

        lastValue = bi.get();
        assertEquals(initialValueB - size, lastValue);
    }

    @Test
    public void testAtomic15() throws TornadoExecutionPlanException {
        // Calling multiple atomics
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        IntArray a = new IntArray(size);
        a.init(1);

        final int initialValueA = 311;
        AtomicInteger ai = new AtomicInteger(initialValueA);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestAtomics::atomic15, a, ai) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, ai, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        int lastValue = ai.get();
        assertEquals(initialValueA + size, lastValue);

        boolean repeated = isValueRepeated(a);
        assertFalse(repeated);
    }

    @Test
    public void testAtomic16() throws TornadoExecutionPlanException {
        // Calling multiple atomics
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        IntArray a = new IntArray(size);
        a.init(1);

        final int initialValueA = 311;
        AtomicInteger ai = new AtomicInteger(initialValueA);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, ai) //
                .task("t0", TestAtomics::atomic16, a, ai) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, ai, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        final int iterations = 50;
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            IntStream.range(0, iterations).forEach(_ -> executionPlan.execute());
        }

        int lastValue = ai.get();
        assertEquals(initialValueA + (iterations * size), lastValue);
    }

    @Test
    public void testAtomic17_getAndIncrement_kernel_api() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        Random random = new Random();
        final int size = 32;
        IntArray dataTornadoVM = new IntArray(size);
        IntArray dataSequential = new IntArray(size);
        for (int i = 0; i < size; i++) {
            int intRandomValue = random.nextInt();
            dataTornadoVM.set(i, intRandomValue);
            dataSequential.set(i, intRandomValue);
        }

        KernelContext context = new KernelContext();
        AtomicInteger ai = new AtomicInteger(200);
        AtomicInteger jai = new AtomicInteger(200);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, dataTornadoVM, ai) //
                .task("t0", TestAtomics::atomic17GetAndIncrement, context, dataTornadoVM, ai) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, dataTornadoVM, ai); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        WorkerGrid workerGrid = new WorkerGrid1D(32);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        atomic17GetAndIncrement(dataSequential, jai);

        assertEquals(jai.get(), ai.get());
        assertArrayEquals(dataSequential.toHeapArray(), dataTornadoVM.toHeapArray());
    }

    @Test
    public void testAtomic17_getAndIncrement_parallel_api() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        Random random = new Random();
        final int size = 32;
        IntArray dataTornadoVM = new IntArray(size);
        IntArray dataSequential = new IntArray(size);
        for (int i = 0; i < size; i++) {
            int intRandomValue = random.nextInt();
            dataTornadoVM.set(i, intRandomValue);
            dataSequential.set(i, intRandomValue);
        }

        AtomicInteger ai = new AtomicInteger(200);
        AtomicInteger jai = new AtomicInteger(200);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, dataTornadoVM, ai) //
                .task("t0", TestAtomics::atomic17GetAndIncrement, dataTornadoVM, ai) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, dataTornadoVM, ai); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        atomic17GetAndIncrement(dataSequential, jai);

        assertEquals(jai.get(), ai.get());
        assertArrayEquals(dataSequential.toHeapArray(), dataTornadoVM.toHeapArray());
    }

    @Test
    public void testAtomic17_incrementAndGet_kernel_api() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        Random random = new Random();
        final int size = 32;
        IntArray dataTornadoVM = new IntArray(size);
        IntArray dataSequential = new IntArray(size);
        for (int i = 0; i < size; i++) {
            int intRandomValue = random.nextInt();
            dataTornadoVM.set(i, intRandomValue);
            dataSequential.set(i, intRandomValue);
        }

        KernelContext context = new KernelContext();
        AtomicInteger ai = new AtomicInteger(200);
        AtomicInteger jai = new AtomicInteger(200);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, dataTornadoVM, ai) //
                .task("t0", TestAtomics::atomic17IncrementAndGet, context, dataTornadoVM, ai) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, dataTornadoVM, ai); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        WorkerGrid workerGrid = new WorkerGrid1D(32);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        atomic17IncrementAndGet(dataSequential, jai);

        assertEquals(jai.get(), ai.get());
        assertArrayEquals(dataSequential.toHeapArray(), dataTornadoVM.toHeapArray());
    }

    @Test
    public void testAtomic17_incrementAndGet_parallel_api() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        Random random = new Random();
        final int size = 32;
        IntArray dataTornadoVM = new IntArray(size);
        IntArray dataSequential = new IntArray(size);
        for (int i = 0; i < size; i++) {
            int intRandomValue = random.nextInt();
            dataTornadoVM.set(i, intRandomValue);
            dataSequential.set(i, intRandomValue);
        }

        AtomicInteger ai = new AtomicInteger(200);
        AtomicInteger jai = new AtomicInteger(200);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, dataTornadoVM, ai) //
                .task("t0", TestAtomics::atomic17IncrementAndGet, dataTornadoVM, ai) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, dataTornadoVM, ai); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        atomic17IncrementAndGet(dataSequential, jai);

        assertEquals(jai.get(), ai.get());
        assertArrayEquals(dataSequential.toHeapArray(), dataTornadoVM.toHeapArray());
    }

    @Test
    public void testAtomic18_parallel_api_IntegerArray() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        IntArray dataTornadoVM = new IntArray(size);
        IntArray dataJava = new IntArray(size);
        for (int i = 0; i < size; i++) {
            dataTornadoVM.set(i, i);
            dataJava.set(i, i);
        }

        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, dataTornadoVM) //
                .task("t0", TestAtomics::atomic18, context, dataTornadoVM) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, dataTornadoVM); //

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withDefaultScheduler().execute();
        }

        atomic18(context, dataJava);

        assertArrayEquals(dataJava.toHeapArray(), dataTornadoVM.toHeapArray());
    }

    @Test
    public void testAtomic18_parallel_api_int_array() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        int[] dataTornadoVM = new int[size];
        int[] incrementingValues = new int[size];
        int[] dataJava = new int[size];
        for (int i = 0; i < size; i++) {
            dataTornadoVM[i] = i;
            dataJava[i] = i;
            incrementingValues[i] = 2*i;
        }

        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, dataTornadoVM, incrementingValues) //
                .task("t0", TestAtomics::atomic18, context, dataTornadoVM, incrementingValues) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, dataTornadoVM); //

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withDefaultScheduler().execute();
        }

        atomic18(context, dataJava, incrementingValues);

        assertArrayEquals(dataJava, dataTornadoVM);
    }

    @Test
    public void testAtomic18_parallel_api_LongArray() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        LongArray dataTornadoVM = new LongArray(size);
        LongArray dataJava = new LongArray(size);
        for (int i = 0; i < size; i++) {
            dataTornadoVM.set(i, i);
            dataJava.set(i, i);
        }

        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, dataTornadoVM) //
                .task("t0", TestAtomics::atomic18, context, dataTornadoVM) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, dataTornadoVM); //

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withDefaultScheduler().execute();
        }

        atomic18(context, dataJava);

        assertArrayEquals(dataJava.toHeapArray(), dataTornadoVM.toHeapArray());
    }

    @Test
    public void testAtomic18_parallel_api_FloatArray() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        FloatArray dataTornadoVM = new FloatArray(size);
        for (int i = 0; i < size; i++) {
            dataTornadoVM.set(i, i);
        }

        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, dataTornadoVM) //
                .task("t0", TestAtomics::atomic18, context, dataTornadoVM) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, dataTornadoVM); //

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withDefaultScheduler().execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(i + 1, dataTornadoVM.get(i), 0.1f);
        }
    }

    @Test
    public void testAtomic18_parallel_api_DoubleArray() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        DoubleArray dataTornadoVM = new DoubleArray(size);
        for (int i = 0; i < size; i++) {
            dataTornadoVM.set(i, i);
        }

        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, dataTornadoVM) //
                .task("t0", TestAtomics::atomic18, context, dataTornadoVM) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, dataTornadoVM); //

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withDefaultScheduler().execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(i + 1, dataTornadoVM.get(i), 0.1f);
        }
    }

    @Test
    public void testAtomic18_kernel_api() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        IntArray dataTornadoVM = new IntArray(size);
        IntArray dataJava = new IntArray(size);
        for (int i = 0; i < size; i++) {
            dataTornadoVM.set(i, i);
            dataJava.set(i, i);
        }

        KernelContext context = new KernelContext();
        WorkerGrid workerGrid = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, dataTornadoVM) //
                .task("t0", TestAtomics::atomic18Kernel, context, dataTornadoVM) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, dataTornadoVM); //

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        atomic18(context, dataJava);

        assertArrayEquals(dataJava.toHeapArray(), dataTornadoVM.toHeapArray());
    }

    @Test
    public void testAtomic19_parallel_api() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        final int indexForHistogram = 16;
        IntArray dataTornadoVM = new IntArray(size);
        IntArray output = new IntArray(size);
        IntArray outputJava = new IntArray(size);
        dataTornadoVM.init(indexForHistogram);
        output.init(0);

        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, dataTornadoVM) //
                .task("t0", TestAtomics::atomic19, context, dataTornadoVM, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output); //

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withDefaultScheduler().execute();
        }

        atomic19(context, dataTornadoVM, outputJava);
        assertArrayEquals(outputJava.toHeapArray(), output.toHeapArray());
    }

    @Test
    public void testAtomic19_kernel_api() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        final int indexForHistogram = 16;
        IntArray dataTornadoVM = new IntArray(size);
        IntArray output = new IntArray(size);
        IntArray outputJava = new IntArray(size);
        dataTornadoVM.init(indexForHistogram);
        output.init(0);

        KernelContext context = new KernelContext();
        WorkerGrid workerGrid = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, dataTornadoVM) //
                .task("t0", TestAtomics::atomic19Kernel, context, dataTornadoVM, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output); //

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        atomic19(context, dataTornadoVM, outputJava);
        assertArrayEquals(outputJava.toHeapArray(), output.toHeapArray());
    }
}
