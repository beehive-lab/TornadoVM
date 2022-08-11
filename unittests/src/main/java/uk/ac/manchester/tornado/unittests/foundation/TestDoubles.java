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

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestDoubles extends TornadoTestBase {

    @Test
    public void testDoublesCopy() {
        final int numElements = 256;
        double[] a = new double[numElements];

        new TaskGraph("s0") //
                .task("t0", TestKernels::testDoublesCopy, a) //
                .streamOut(a) //
                .execute(); //

        assertEquals(a[0], 50.0, 0.01);
    }

    @Test
    public void testDoublesAdd() {

        final int numElements = 256;
        double[] a = new double[numElements];
        double[] b = new double[numElements];
        double[] c = new double[numElements];

        Arrays.fill(b, 100);
        Arrays.fill(c, 200);

        double[] expected = new double[numElements];
        for (int i = 0; i < numElements; i++) {
            expected[i] = b[i] + c[i];
        }

        new TaskGraph("s0") //
                .task("t0", TestKernels::vectorAddDoubleCompute, a, b, c) //
                .streamOut(a) //
                .execute(); //

        for (int i = 0; i < numElements; i++) {
            assertEquals(expected[i], a[i], 0.01f);
        }
    }

    @Test
    public void testDoublesSub() {

        final int numElements = 256;
        double[] a = new double[numElements];
        double[] b = new double[numElements];
        double[] c = new double[numElements];

        Arrays.fill(b, 2.2);
        Arrays.fill(c, 3.5);

        double[] expected = new double[numElements];
        for (int i = 0; i < numElements; i++) {
            expected[i] = b[i] - c[i];
        }

        new TaskGraph("s0") //
                .task("t0", TestKernels::vectorSubDoubleCompute, a, b, c) //
                .streamOut(a) //
                .execute(); //

        for (int i = 0; i < numElements; i++) {
            assertEquals(expected[i], a[i], 0.01f);
        }
    }

    @Test
    public void testDoublesMul() {

        final int numElements = 256;
        double[] a = new double[numElements];
        double[] b = new double[numElements];
        double[] c = new double[numElements];

        Arrays.fill(b, 2.2);
        Arrays.fill(c, 3.5);
        double[] expected = new double[numElements];
        for (int i = 0; i < numElements; i++) {
            expected[i] = b[i] * c[i];
        }

        new TaskGraph("s0") //
                .task("t0", TestKernels::vectorMulDoubleCompute, a, b, c) //
                .streamOut(a) //
                .execute(); //

        for (int i = 0; i < numElements; i++) {
            assertEquals(expected[i], a[i], 0.01f);
        }
    }

    @Test
    public void testDoublesDiv() {

        final int numElements = 256;
        double[] a = new double[numElements];
        double[] b = new double[numElements];
        double[] c = new double[numElements];

        Arrays.fill(b, 10.2);
        Arrays.fill(c, 2.0);
        double[] expected = new double[numElements];
        for (int i = 0; i < numElements; i++) {
            expected[i] = b[i] / c[i];
        }

        new TaskGraph("s0") //
                .task("t0", TestKernels::vectorDivDoubleCompute, a, b, c) //
                .streamOut(a) //
                .execute(); //

        for (int i = 0; i < numElements; i++) {
            assertEquals(expected[i], a[i], 0.01f);
        }
    }

}
