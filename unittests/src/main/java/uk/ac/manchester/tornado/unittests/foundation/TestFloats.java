package uk.ac.manchester.tornado.unittests.foundation;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

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
public class TestFloats extends TornadoTestBase {

    @Test
    public void testFloatsCopy() {
        final int numElements = 256;
        float[] a = new float[numElements];

        new TaskGraph("s0") //
                .task("t0", TestKernels::testFloatCopy, a) //
                .streamOut(a) //
                .execute(); //

        assertEquals(a[0], 50.0f, 0.01f);
    }

    @Test
    public void testVectorFloatAdd() {

        final int numElements = 256;
        float[] a = new float[numElements];
        float[] b = new float[numElements];
        float[] c = new float[numElements];

        Arrays.fill(b, 100);
        Arrays.fill(c, 200);
        float[] expected = new float[numElements];
        for (int i = 0; i < numElements; i++) {
            expected[i] = b[i] + c[i];
        }

        new TaskGraph("s0") //
                .task("t0", TestKernels::vectorAddFloatCompute, a, b, c) //
                .streamOut(a) //
                .execute(); //

        for (int i = 0; i < numElements; i++) {
            assertEquals(expected[i], a[i], 0.01f);
        }
    }

    @Test
    public void testVectorFloatSub() {

        final int numElements = 256;
        float[] a = new float[numElements];
        float[] b = new float[numElements];
        float[] c = new float[numElements];

        Arrays.fill(b, 200);
        Arrays.fill(c, 100);
        float[] expected = new float[numElements];
        for (int i = 0; i < numElements; i++) {
            expected[i] = b[i] - c[i];
        }

        new TaskGraph("s0") //
                .task("t0", TestKernels::vectorSubFloatCompute, a, b, c) //
                .streamOut(a) //
                .execute(); //

        for (int i = 0; i < numElements; i++) {
            assertEquals(expected[i], a[i], 0.01f);
        }

    }

    @Test
    public void testVectorFloatMul() {

        final int numElements = 256;
        float[] a = new float[numElements];
        float[] b = new float[numElements];
        float[] c = new float[numElements];

        Arrays.fill(b, 100.0f);
        Arrays.fill(c, 5.0f);

        float[] expected = new float[numElements];
        for (int i = 0; i < numElements; i++) {
            expected[i] = b[i] * c[i];
        }

        new TaskGraph("s0") //
                .task("t0", TestKernels::vectorMulFloatCompute, a, b, c) //
                .streamOut(a) //
                .execute(); //

        for (int i = 0; i < numElements; i++) {
            assertEquals(expected[i], a[i], 0.01f);
        }
    }

    @Test
    public void testVectorFloatDiv() {

        final int numElements = 256;
        float[] a = new float[numElements];
        float[] b = new float[numElements];
        float[] c = new float[numElements];

        Arrays.fill(b, 100.0f);
        Arrays.fill(c, 5.0f);

        float[] expected = new float[numElements];
        for (int i = 0; i < numElements; i++) {
            expected[i] = b[i] / c[i];
        }

        new TaskGraph("s0") //
                .task("t0", TestKernels::vectorDivFloatCompute, a, b, c) //
                .streamOut(a) //
                .execute(); //

        for (int i = 0; i < numElements; i++) {
            assertEquals(expected[i], a[i], 0.01f);
        }
    }
}
