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

package uk.ac.manchester.tornado.unittests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.Debug;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestHello extends TornadoTestBase {

    private static void printHello(final int n) {
        for (@Parallel int i = 0; i < n; i++) {
            Debug.printf("hello\n");
        }
    }

    public static void add(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public void compute(int[] a, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            b[i] = a[i] * 2;
        }
    }

    public static void compute(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = a[i] * 2;
        }
    }

    @Test
    public void testHello() {
        assertNotBackend(TornadoVMBackendType.SPIRV);

        TaskSchedule task = new TaskSchedule("s0").task("t0", TestHello::printHello, 8);
        assertNotNull(task);

        try {
            task.execute();
            assertTrue("Task was executed.", true);
        } catch (Exception e) {
            assertTrue("Task was not executed.", false);
        }
    }

    @Test
    public void testVectorAddition() {
        int numElements = 16;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(a, 1);
        Arrays.fill(b, 2);

        // @formatter:off
		new TaskSchedule("s0")
		    .task("t0", TestHello::add, a, b, c)
		    .streamOut(c)
		    .execute();
		// @formatter:on

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i], 0.001);
        }
    }

    @Test
    public void testSimpleCompute() {
        int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];

        Arrays.fill(a, 10);

        TestHello t = new TestHello();

        //@formatter:off
        new TaskSchedule("s0")
            .task("t0", t::compute, a, b)
            .streamOut(b)
            .execute();
        //@formatter:on

        for (int i = 0; i < b.length; i++) {
            assertEquals(a[i] * 2, b[i]);
        }
    }

    @Test
    public void testSimpleCompute2() {
        int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];

        Arrays.fill(a, 10);

        TestHello t = new TestHello();

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(a)
            .task("t0", t::compute, a, b)
            .streamOut(b)
            .execute();
        //@formatter:on

        for (int i = 0; i < b.length; i++) {
            assertEquals(a[i] * 2, b[i]);
        }
    }

    @Test
    public void testSimpleInOut() {
        int numElements = 256;
        int[] a = new int[numElements];

        Arrays.fill(a, 10);

        //@formatter:off
        new TaskSchedule("s0")
            .task("t0", TestHello::compute, a)
            .streamOut(a)
            .execute();
        //@formatter:on

        for (int i = 0; i < a.length; i++) {
            assertEquals(20, a[i]);
        }
    }

}
