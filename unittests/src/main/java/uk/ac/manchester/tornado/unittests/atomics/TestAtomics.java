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

package uk.ac.manchester.tornado.unittests.atomics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Ignore;
import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.TornadoVM_Intrinsics;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.atomics.TornadoAtomicInteger;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.api.type.annotations.Atomic;
import uk.ac.manchester.tornado.unittests.common.PTXNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestAtomics extends TornadoTestBase {

    /**
     * This test is just an example. This approach is not supported in TornadoVM.
     * 
     * @param a
     *            input array
     * @param sum
     *            initial value to sum
     */
    private static void atomic01(@Atomic int[] a, int sum) {
        for (@Parallel int i = 0; i < a.length; i++) {
            sum += a[i];
        }
        a[0] = sum;
    }

    @Ignore
    public void testAtomic() {
        final int size = 10;

        int[] a = new int[size];
        int sum = 0;

        Arrays.fill(a, 1);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestAtomics::atomic01, a, sum)
                .streamOut(a)
                .execute();
        //@formatter:on
    }

    // Failed code that should end-up in a race condition
    public static void atomic02(int[] a) {
        final int SIZE = 100;
        for (@Parallel int i = 0; i < a.length; i++) {
            int j = i % SIZE;
            a[j] = a[j] + 1;
        }
    }

    @Ignore
    public void testAtomic02() {
        final int size = 1024;
        int[] a = new int[size];
        int[] b = new int[size];

        Arrays.fill(a, 1);
        Arrays.fill(b, 1);

        new TaskSchedule("s0") //
                .task("t0", TestAtomics::atomic02, a) //
                .streamOut(a) //
                .execute();

        atomic02(b);
        for (int i = 0; i < a.length; i++) {
            assertEquals(b[i], a[i]);
        }
    }

    /**
     * Approach using a compiler-instrinsic in TornadoVM.
     * 
     * @param a
     *            input array
     */
    public static void atomic03(int[] a) {
        final int SIZE = 100;
        for (@Parallel int i = 0; i < a.length; i++) {
            int j = i % SIZE;
            a[j] = TornadoVM_Intrinsics.atomic_add(a, j, 1);
        }
    }

    @TornadoNotSupported
    public void testAtomic03() {
        final int size = 1024;
        int[] a = new int[size];
        int[] b = new int[size];

        Arrays.fill(a, 1);
        Arrays.fill(b, 1);

        new TaskSchedule("s0") //
                .task("t0", TestAtomics::atomic03, a) //
                .streamOut(a) //
                .execute();

        atomic03(b);
        for (int i = 0; i < a.length; i++) {
            assertEquals(b[i], a[i]);
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
        TornadoAtomicInteger tai = new TornadoAtomicInteger(200);
        for (@Parallel int i = 0; i < input.length; i++) {
            input[i] = tai.incrementAndGet();
        }
    }

    @Test
    public void testAtomic04() {
        checkForPTX();

        final int size = 32;
        int[] a = new int[size];
        Arrays.fill(a, 1);

        TaskSchedule ts = new TaskSchedule("s0") //
                .task("t0", TestAtomics::atomic04, a) //
                .streamOut(a); //

        ts.execute();

        if (!ts.isFinished()) {
            assertTrue(false);
        }

        // On GPUs and FPGAs, threads within the same work-group run in parallel.
        // Increments will be performed atomically when using TornadoAtomicInteger.
        // However the order is not guaranteed. For this test, we need to check that
        // there are not repeated values in the output array.
        HashSet<Integer> set = new HashSet<>();

        boolean repeated = false;
        for (int j : a) {
            if (!set.contains(j)) {
                set.add(j);
            } else {
                repeated = true;
                break;
            }
        }
        assertTrue(!repeated);
    }

    /**
     * How to test?
     * 
     * <code>    
     * $ tornado-test.py -V -pk --debug -J"-Ddevice=0" uk.ac.manchester.tornado.unittests.atomics.TestAtomics#testAtomic05_precompiled
     * </code>
     */
    @Test
    public void testAtomic05_precompiled() {
        checkForPTX();

        final int size = 32;
        int[] a = new int[size];
        int[] b = new int[1];
        Arrays.fill(a, 0);

        String deviceToRun = System.getProperties().getProperty("device", "0");
        int deviceNumber = Integer.parseInt(deviceToRun);

        TornadoDevice defaultDevice = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(deviceNumber);
        String tornadoSDK = System.getenv("TORNADO_SDK");

        // @formatter:off
        new TaskSchedule("s0")
                .prebuiltTask("t0",
                        "add",
                        tornadoSDK + "/examples/generated/atomics.cl",
                        new Object[] { a, b },
                        new Access[] { Access.WRITE, Access.WRITE },
                        defaultDevice,
                        new int[] { 32 }, 
                        new int[]{155}     // Atomics - Initial Value
                        )
                .streamOut(a)
                .execute();
        // @formatter:on

        HashSet<Integer> set = new HashSet<>();

        boolean repeated = false;
        for (int j : a) {
            if (!set.contains(j)) {
                set.add(j);
            } else {
                repeated = true;
                break;
            }
        }
        assertTrue(!repeated);
    }

    public static void atomic06(int[] a, int[] b) {
        TornadoAtomicInteger taiA = new TornadoAtomicInteger(200);
        TornadoAtomicInteger taiB = new TornadoAtomicInteger(100);
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = taiA.incrementAndGet();
            b[i] = taiB.incrementAndGet();
        }
    }

    @Test
    public void testAtomic06() {
        checkForPTX();

        final int size = 2048;
        int[] a = new int[size];
        int[] b = new int[size];
        Arrays.fill(a, 1);
        Arrays.fill(b, 1);

        TaskSchedule ts = new TaskSchedule("s0") //
                .streamIn(a, b) //
                .task("t0", TestAtomics::atomic06, a, b) //
                .streamOut(a, b); //

        ts.execute();

        if (!ts.isFinished()) {
            assertTrue(false);
        }

        // On GPUs and FPGAs, threads within the same work-group run in parallel.
        // Increments will be performed atomically when using TornadoAtomicInteger.
        // However the order is not guaranteed. For this test, we need to check that
        // there are not repeated values in the output array.
        HashSet<Integer> set = new HashSet<>();

        boolean repeated = false;
        for (int j : a) {
            if (!set.contains(j)) {
                set.add(j);
            } else {
                repeated = true;
                break;
            }
        }

        set.clear();

        for (int j : b) {
            if (!set.contains(j)) {
                set.add(j);
            } else {
                repeated = true;
                break;
            }
        }
        assertTrue(!repeated);
    }

    public static void atomic07(int[] input) {
        AtomicInteger ai = new AtomicInteger(200);
        for (@Parallel int i = 0; i < input.length; i++) {
            input[i] = ai.incrementAndGet();
        }
    }

    @Test
    public void testAtomic07() {
        checkForPTX();

        final int size = 32;
        int[] a = new int[size];
        Arrays.fill(a, 1);

        TaskSchedule ts = new TaskSchedule("s0") //
                .task("t0", TestAtomics::atomic07, a) //
                .streamOut(a); //

        ts.execute();

        if (!ts.isFinished()) {
            assertTrue(false);
        }

        // On GPUs and FPGAs, threads within the same work-group run in parallel.
        // Increments will be performed atomically when using TornadoAtomicInteger.
        // However the order is not guaranteed. For this test, we need to check that
        // there are not repeated values in the output array.
        HashSet<Integer> set = new HashSet<>();

        boolean repeated = false;
        for (int j : a) {
            if (!set.contains(j)) {
                set.add(j);
            } else {
                repeated = true;
                break;
            }
        }
        assertTrue(!repeated);
    }

    public static void atomic08(int[] input) {
        AtomicInteger ai = new AtomicInteger(200);
        for (@Parallel int i = 0; i < input.length; i++) {
            input[i] = ai.decrementAndGet();
        }
    }

    @Test
    public void testAtomic08() {
        checkForPTX();

        final int size = 32;
        int[] a = new int[size];
        Arrays.fill(a, 1);

        TaskSchedule ts = new TaskSchedule("s0") //
                .task("t0", TestAtomics::atomic08, a) //
                .streamOut(a); //

        ts.execute();

        if (!ts.isFinished()) {
            assertTrue(false);
        }

        // On GPUs and FPGAs, threads within the same work-group run in parallel.
        // Increments will be performed atomically when using TornadoAtomicInteger.
        // However the order is not guaranteed. For this test, we need to check that
        // there are not repeated values in the output array.
        HashSet<Integer> set = new HashSet<>();

        boolean repeated = false;
        for (int j : a) {
            if (!set.contains(j)) {
                set.add(j);
            } else {
                repeated = true;
                break;
            }
        }
        assertTrue(!repeated);
    }

}
