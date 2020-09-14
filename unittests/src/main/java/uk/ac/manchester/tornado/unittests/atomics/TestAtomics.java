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

import java.util.Arrays;

import org.junit.Ignore;
import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.TornadoVM_Intrinsics;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.atomics.TornadoAtomicInteger;
import uk.ac.manchester.tornado.api.type.annotations.Atomic;
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

        atomic02(b);
        for (int i = 0; i < a.length; i++) {
            assertEquals(b[i], a[i]);
        }
    }

    public static void atomic04(int[] a) {
        TornadoAtomicInteger tai = new TornadoAtomicInteger(0);
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = tai.incrementAndGet();
        }
    }

    @Test
    public void testAtomic04() {
        final int size = 1024;
        int[] a = new int[size];
        int[] b = new int[size];

        Arrays.fill(a, 1);
        Arrays.fill(b, 1);

        new TaskSchedule("s0") //
                .task("t0", TestAtomics::atomic04, a) //
                .streamOut(a) //
                .execute();

        atomic02(b);
        for (int i = 0; i < a.length; i++) {
            assertEquals(b[i], a[i]);
        }
    }

}
