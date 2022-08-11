/*
 * Copyright (c) 2021, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.foundation;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * tornado-test.py -V uk.ac.manchester.tornado.unittests.foundation.TestIntegers
 */
public class TestIntegers extends TornadoTestBase {

    @Test
    public void test01() {
        final int numElements = 256;
        int[] a = new int[numElements];

        new TaskGraph("s0") //
                .task("t0", TestKernels::copyTestZero, a) //
                .streamOut(a) //
                .execute(); //

        assertEquals(50, a[0]);
    }

    @Test
    public void test02() {
        final int numElements = 512;
        int[] a = new int[numElements];

        int[] expectedResult = new int[numElements];
        Arrays.fill(expectedResult, 50);

        new TaskGraph("s1") //
                .streamIn(a) //
                .task("t1", TestKernels::copyTest, a) //
                .streamOut(a) //
                .execute(); //

        assertArrayEquals(expectedResult, a);
    }

    @Test
    public void test03() {
        final int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];

        Arrays.fill(b, 100);

        new TaskGraph("s0") //
                .task("t0", TestKernels::copyTest2, a, b) //
                .streamOut(a) //
                .execute(); //

        assertArrayEquals(b, a);
    }

    @Test
    public void test04() {
        final int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];

        Arrays.fill(b, 100);
        int[] expectedResult = new int[numElements];
        Arrays.fill(expectedResult, 150);

        new TaskGraph("s0") //
                .task("t0", TestKernels::compute, a, b) //
                .streamOut(a) //
                .execute(); //

        assertArrayEquals(expectedResult, a);
    }

    @Test
    public void test05() {
        final int numElements = 8192 * 16;
        int[] a = new int[numElements];
        int[] b = new int[numElements];

        Arrays.fill(a, 0);
        Arrays.fill(b, 0);
        int[] expectedResultA = new int[numElements];
        int[] expectedResultB = new int[numElements];
        Arrays.fill(expectedResultA, 100);
        Arrays.fill(expectedResultB, 500);

        new TaskGraph("s0") //
                .task("t0", TestKernels::init, a, b) //
                .streamOut(a, b) //
                .execute(); //

        assertArrayEquals(expectedResultA, a);
        assertArrayEquals(expectedResultB, b);
    }

}
