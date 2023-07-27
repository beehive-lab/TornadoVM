/*
 * Copyright (c) 2013-2022, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.unittests.atomics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.TornadoVM_Intrinsics;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.unittests.common.TornadoNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to test?
 *
 * <code>
 *     tornado-test -V --fast uk.ac.manchester.tornado.unittests.atomics.TestAtomics
 * </code>
 */
public class TestAtomics extends TornadoTestBase {

    /**
     * Approach using a compiler-intrinsic in TornadoVM.
     *
     * @param a
     *            Input array. It stores the addition with an atomic variable.
     */
    public static void atomic03(int[] a) {
        final int SIZE = 100;
        for (@Parallel int i = 0; i < a.length; i++) {
            int j = i % SIZE;
            a[j] = TornadoVM_Intrinsics.atomic_add(a, j, 1);
        }
    }

    /**
     * Approach using an API for Atomics. This provides atomics using the Java
     * semantics (block a single elements). Note that, in OpenCL, this single
     * elements has to be present in the device's global memory.
     *
     * @param input
     *            input array
     */
    public static void atomic04(int[] input) {
        AtomicInteger tai = new AtomicInteger(200);
        for (@Parallel int i = 0; i < input.length; i++) {
            input[i] = tai.incrementAndGet();
        }
    }

    public static void atomic04Get(int[] input) {
        AtomicInteger tai = new AtomicInteger(200);
        for (@Parallel int i = 0; i < input.length; i++) {
            input[i] = tai.incrementAndGet();
            int a = tai.get();
            if (a == 201) {
                input[i] = 0;
            }
        }
    }

    public static void atomic06(int[] a, int[] b) {
        AtomicInteger taiA = new AtomicInteger(200);
        AtomicInteger taiB = new AtomicInteger(100);
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = taiA.incrementAndGet();
            b[i] = taiB.incrementAndGet();
        }
    }

    public static void atomic07(int[] input) {
        AtomicInteger ai = new AtomicInteger(200);
        for (@Parallel int i = 0; i < input.length; i++) {
            input[i] = ai.incrementAndGet();
        }
    }

    public static void atomic08(int[] input) {
        AtomicInteger ai = new AtomicInteger(200);
        for (@Parallel int i = 0; i < input.length; i++) {
            input[i] = ai.decrementAndGet();
        }
    }

    public static int callAtomic(int[] input, int i, AtomicInteger ai) {
        return input[i] + ai.incrementAndGet();
    }

    public static void atomic09(int[] input, AtomicInteger ai) {
        for (@Parallel int i = 0; i < input.length; i++) {
            input[i] = callAtomic(input, i, ai);
        }
    }

    public static void atomic10(int[] input, AtomicInteger ai, AtomicInteger bi) {
        for (@Parallel int i = 0; i < input.length; i++) {
            input[i] = input[i] + ai.incrementAndGet() + bi.incrementAndGet();
        }
    }

    public static void atomic13(int[] input, AtomicInteger ai) {
        for (@Parallel int i = 0; i < input.length; i++) {
            input[i] = input[i] + ai.decrementAndGet();
        }
    }

    public static void atomic14(int[] input, AtomicInteger ai, AtomicInteger bi) {
        for (@Parallel int i = 0; i < input.length; i++) {
            input[i] = input[i] + ai.incrementAndGet();
            input[i] = input[i] + bi.decrementAndGet();
        }
    }

    /**
     * This example combines an atomic created inside the compute kernel with an
     * atomic passed as an argument.
     *
     * @param input
     *            Input array
     * @param ai
     *            Atomic Integer stored in Global Memory (atomic-region)
     */
    public static void atomic15(int[] input, AtomicInteger ai) {
        AtomicInteger bi = new AtomicInteger(500);
        for (@Parallel int i = 0; i < input.length; i++) {
            input[i] = input[i] + ai.incrementAndGet();
            input[i] = input[i] + bi.incrementAndGet();
        }
    }

    public static void atomic16(int[] input, AtomicInteger ai) {
        for (@Parallel int i = 0; i < input.length; i++) {
            input[i] = input[i] + ai.incrementAndGet();
        }
    }

    @TornadoNotSupported
    public void testAtomic03() {
        final int size = 1024;
        int[] a = new int[size];
        int[] b = new int[size];

        Arrays.fill(a, 1);
        Arrays.fill(b, 1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestAtomics::atomic03, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        atomic03(b);
        for (int i = 0; i < a.length; i++) {
            assertEquals(b[i], a[i]);
        }
    }

    @Test
    public void testAtomic04() {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        int[] a = new int[size];
        Arrays.fill(a, 1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestAtomics::atomic04, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        TornadoExecutionResult executionResult = executionPlan.execute();

        if (!executionResult.isReady()) {
            assertTrue(false);
        }

        // On GPUs and FPGAs, threads within the same work-group run in parallel.
        // Increments will be performed atomically when using TornadoAtomicInteger.
        // However, the order is not guaranteed. For this test, we need to check that
        // there are not repeated values in the output array.
        boolean repeated = isValueRepeated(a);
        assertTrue(!repeated);
    }

    @Test
    public void testAtomic04Get() {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        int[] a = new int[size];
        Arrays.fill(a, 1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestAtomics::atomic04Get, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        TornadoExecutionResult executionResult = executionPlan.execute();

        if (!executionResult.isReady()) {
            assertTrue(false);
        }

        // On GPUs and FPGAs, threads within the same work-group run in parallel.
        // Increments will be performed atomically when using TornadoAtomicInteger.
        // However, the order is not guaranteed. For this test, we need to check that
        // there are not repeated values in the output array.
        boolean repeated = isValueRepeated(a);
        assertTrue(!repeated);
    }

    /**
     * How to test?
     *
     * <code>
     * $ tornado-test -V -pk --debug -J"-Ddevice=0" uk.ac.manchester.tornado.unittests.atomics.TestAtomics#testAtomic05_precompiled
     * </code>
     */
    @Test
    public void testAtomic05_precompiled() {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        int[] a = new int[size];
        int[] b = new int[1];
        Arrays.fill(a, 0);

        String deviceToRun = System.getProperties().getProperty("device", "0");
        int deviceNumber = Integer.parseInt(deviceToRun);

        TornadoDevice defaultDevice = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(deviceNumber);
        String tornadoSDK = System.getenv("TORNADO_SDK");

        TaskGraph taskGraph = new TaskGraph("s0") //
                .prebuiltTask("t0", //
                        "add", //
                        tornadoSDK + "/examples/generated/atomics.cl", //
                        new Object[] { a, b }, //
                        new Access[] { Access.WRITE_ONLY, Access.WRITE_ONLY }, //
                        defaultDevice, //
                        new int[] { 32 }, //
                        new int[] { 155 } // Atomics - Initial Value
                )//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        boolean repeated = isValueRepeated(a);
        assertTrue(!repeated);
    }

    @Test
    public void testAtomic06() {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 2048;
        int[] a = new int[size];
        int[] b = new int[size];
        Arrays.fill(a, 1);
        Arrays.fill(b, 1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestAtomics::atomic06, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        TornadoExecutionResult executionResult = executionPlan.execute();

        if (!executionResult.isReady()) {
            assertTrue(false);
        }

        boolean repeated = isValueRepeated(a);
        repeated &= isValueRepeated(a);

        assertTrue(!repeated);
    }

    @Test
    public void testAtomic07() {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        int[] a = new int[size];
        Arrays.fill(a, 1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestAtomics::atomic07, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        TornadoExecutionResult executionResult = executionPlan.execute();

        if (!executionResult.isReady()) {
            assertTrue(false);
        }
        boolean repeated = isValueRepeated(a);
        assertTrue(!repeated);
    }

    @Test
    public void testAtomic08() {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        int[] a = new int[size];
        Arrays.fill(a, 1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestAtomics::atomic08, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        TornadoExecutionResult executionResult = executionPlan.execute();

        if (!executionResult.isReady()) {
            assertTrue(false);
        }
        boolean repeated = isValueRepeated(a);
        assertTrue(!repeated);
    }

    private boolean isValueRepeated(int[] array) {
        HashSet<Integer> set = new HashSet<>();
        boolean repeated = false;
        for (int j : array) {
            if (!set.contains(j)) {
                set.add(j);
            } else {
                repeated = true;
                break;
            }
        }
        return repeated;
    }

    @Test
    public void testAtomic09() {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        int[] a = new int[size];
        Arrays.fill(a, 1);

        final int initialValue = 311;

        AtomicInteger ai = new AtomicInteger(initialValue);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, ai) //
                .task("t0", TestAtomics::atomic09, a, ai) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, ai);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        boolean repeated = isValueRepeated(a);

        int lastValue = ai.get();
        assertTrue(!repeated);
        assertEquals(initialValue + size, lastValue);
    }

    @Test
    public void testAtomic10() {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        int[] a = new int[size];
        Arrays.fill(a, 1);

        final int initialValue = 311;

        AtomicInteger ai = new AtomicInteger(initialValue);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestAtomics::atomic09, a, ai) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, ai);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        boolean repeated = isValueRepeated(a);

        int lastValue = ai.get();
        assertTrue(!repeated);
        assertEquals(initialValue + size, lastValue);
    }

    @Test
    public void testAtomic11() {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        int[] a = new int[size];
        Arrays.fill(a, 1);

        final int initialValue = 311;

        AtomicInteger ai = new AtomicInteger(initialValue);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestAtomics::atomic09, a, ai) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, ai, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        boolean repeated = isValueRepeated(a);

        int lastValue = ai.get();
        assertTrue(!repeated);
        assertEquals(initialValue + size, lastValue);
    }

    @Test
    public void testAtomic12() {
        // Calling multiple atomics
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        int[] a = new int[size];
        Arrays.fill(a, 1);

        final int initialValueA = 311;
        final int initialValueB = 500;

        AtomicInteger ai = new AtomicInteger(initialValueA);
        AtomicInteger bi = new AtomicInteger(initialValueB);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestAtomics::atomic10, a, ai, bi) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, ai, a, bi);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        boolean repeated = isValueRepeated(a);

        int lastValue = ai.get();
        assertTrue(!repeated);
        assertEquals(initialValueA + size, lastValue);

        lastValue = bi.get();
        assertEquals(initialValueB + size, lastValue);

    }

    @Test
    public void testAtomic13() {
        // Calling multiple atomics
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        int[] a = new int[size];
        Arrays.fill(a, 1);

        final int initialValueA = 311;
        AtomicInteger ai = new AtomicInteger(initialValueA);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestAtomics::atomic13, a, ai) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, ai, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        boolean repeated = isValueRepeated(a);

        int lastValue = ai.get();
        assertTrue(!repeated);
        assertEquals(initialValueA - size, lastValue);
    }

    @Test
    public void testAtomic14() {
        // Calling multiple atomics
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        int[] a = new int[size];
        Arrays.fill(a, 1);

        final int initialValueA = 311;
        final int initialValueB = 50;
        AtomicInteger ai = new AtomicInteger(initialValueA);
        AtomicInteger bi = new AtomicInteger(initialValueB);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestAtomics::atomic14, a, ai, bi) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, ai, a, bi);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        int lastValue = ai.get();
        assertEquals(initialValueA + size, lastValue);

        lastValue = bi.get();
        assertEquals(initialValueB - size, lastValue);
    }

    @Test
    public void testAtomic15() {
        // Calling multiple atomics
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        int[] a = new int[size];
        Arrays.fill(a, 1);

        final int initialValueA = 311;
        AtomicInteger ai = new AtomicInteger(initialValueA);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestAtomics::atomic15, a, ai) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, ai, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        int lastValue = ai.get();
        assertEquals(initialValueA + size, lastValue);

        boolean repeated = isValueRepeated(a);
        assertTrue(!repeated);
    }

    @Test
    public void testAtomic16() {
        // Calling multiple atomics
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 32;
        int[] a = new int[size];
        Arrays.fill(a, 1);

        final int initialValueA = 311;
        AtomicInteger ai = new AtomicInteger(initialValueA);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, ai) //
                .task("t0", TestAtomics::atomic16, a, ai) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, ai, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        final int iterations = 50;
        IntStream.range(0, iterations).forEach(i -> {
            executionPlan.execute();
        });

        int lastValue = ai.get();
        assertEquals(initialValueA + (iterations * size), lastValue);
    }

}
