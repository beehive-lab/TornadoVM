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

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestLinearAlgebra extends TornadoTestBase {

    @Test
    public void vectorAdd() {

        final int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(b, 100);
        Arrays.fill(c, 200);
        int[] expectedResult = new int[numElements];
        Arrays.fill(expectedResult, 300);

        new TaskGraph("s0") //
                .task("t0", TestKernels::vectorAddCompute, a, b, c) //
                .streamOut(a) //
                .execute(); //

        assertArrayEquals(expectedResult, a);
    }

    @Test
    public void vectorMul() {

        final int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(b, 100);
        Arrays.fill(c, 5);

        int[] expectedResult = new int[numElements];
        Arrays.fill(expectedResult, 500);

        new TaskGraph("s0") //
                .task("t0", TestKernels::vectorMul, a, b, c) //
                .streamOut(a) //
                .execute(); //

        assertArrayEquals(expectedResult, a);
    }

    @Test
    public void vectorSub() {

        final int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(b, 100);
        Arrays.fill(c, 75);

        int[] expectedResult = new int[numElements];
        Arrays.fill(expectedResult, 25);

        new TaskGraph("s0") //
                .task("t0", TestKernels::vectorSub, a, b, c) //
                .streamOut(a) //
                .execute(); //

        assertArrayEquals(expectedResult, a);
    }

    @Test
    public void vectorDiv() {

        final int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(b, 512);
        Arrays.fill(c, 2);
        int[] expectedResult = new int[numElements];
        Arrays.fill(expectedResult, 256);

        new TaskGraph("s0") //
                .task("t0", TestKernels::vectorDiv, a, b, c) //
                .streamOut(a) //
                .execute(); //

        assertArrayEquals(expectedResult, a);
    }

    @Test
    public void square() {

        final int numElements = 32;
        int[] a = new int[numElements];
        int[] b = new int[numElements];

        int[] expectedResult = new int[numElements];

        for (int i = 0; i < a.length; i++) {
            b[i] = i;
            expectedResult[i] = i * i;
        }

        new TaskGraph("s0") //
                .task("t0", TestKernels::vectorSquare, a, b) //
                .streamOut(a) //
                .execute(); //

        assertArrayEquals(expectedResult, a);
    }

    @Test
    public void saxpy() {

        final int numElements = 512;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        int[] expectedResult = new int[numElements];

        for (int i = 0; i < a.length; i++) {
            b[i] = i;
            c[i] = i;
            expectedResult[i] = 2 * i + i;
        }

        new TaskGraph("s0") //
                .task("t0", TestKernels::saxpy, a, b, c, 2) //
                .streamOut(a) //
                .execute(); //

        assertArrayEquals(expectedResult, a);
    }

}
