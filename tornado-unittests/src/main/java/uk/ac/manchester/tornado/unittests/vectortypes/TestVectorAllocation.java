/*
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.unittests.vectortypes;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.types.Float2;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.Float4;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat3;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat4;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado-test -V uk.ac.manchester.tornado.unittests.vectortypes.TestVectorAllocation
 * </code>
 */
public class TestVectorAllocation extends TornadoTestBase {

    /**
     * Test to check the kernel can create a float2 type
     *
     * @param a
     * @param result
     */
    private static void testVectorAlloc(float[] a, float[] result) {
        for (@Parallel int i = 0; i < a.length; i++) {
            Float2 x = new Float2(1, 10);
            result[i] = a[i] + (x.getX() * x.getY());
        }
    }

    @Test
    public void testAllocation1() {

        int size = 8;

        float[] a = new float[size];
        float[] output = new float[size];

        for (int i = 0; i < size; i++) {
            a[i] = i;
        }

        new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestVectorAllocation::testVectorAlloc, a, output) //
                .transferToHost(output) //
                .execute();

        for (int i = 0; i < size; i++) {
            assertEquals(a[i] + (10), output[i], 0.001);
        }
    }

    /**
     * Test to check the kernel can create a float2 type
     *
     * @param a
     * @param result
     */
    private static void testVectorAlloc2(float[] a, VectorFloat4 result) {
        for (@Parallel int i = 0; i < a.length; i++) {
            Float4 x = new Float4(a.length, 10, a[i], a[i] * 10);
            result.set(i, x);
        }
    }

    @Test
    public void testAllocation2() {

        int size = 8;

        float[] a = new float[size];
        VectorFloat4 output = new VectorFloat4(size);

        for (int i = 0; i < size; i++) {
            a[i] = i;
        }

        new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestVectorAllocation::testVectorAlloc2, a, output) //
                .transferToHost(output) //
                .execute();

        for (int i = 0; i < size; i++) {
            Float4 sequential = new Float4(a.length, 10, a[i], a[i] * 10);
            assertEquals(sequential.getX(), output.get(i).getX(), 0.001);
            assertEquals(sequential.getY(), output.get(i).getY(), 0.001);
            assertEquals(sequential.getZ(), output.get(i).getZ(), 0.001);
            assertEquals(sequential.getW(), output.get(i).getW(), 0.001);
        }
    }

    /**
     * Test to check the kernel can create a float2 type
     *
     * @param a
     * @param result
     */
    private static void testVectorAlloc3(float[] a, VectorFloat3 result) {
        for (@Parallel int i = 0; i < a.length; i++) {
            Float3 x = new Float3(a.length, 10, a[i]);
            result.set(i, x);
        }
    }

    @Test
    public void testAllocation3() {

        int size = 8;

        float[] a = new float[size];
        VectorFloat3 output = new VectorFloat3(size);

        for (int i = 0; i < size; i++) {
            a[i] = i;
        }

        new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestVectorAllocation::testVectorAlloc3, a, output) //
                .transferToHost(output) //
                .execute();

        for (int i = 0; i < size; i++) {
            Float3 sequential = new Float3(a.length, 10, a[i]);
            assertEquals(sequential.getX(), output.get(i).getX(), 0.001);
            assertEquals(sequential.getY(), output.get(i).getY(), 0.001);
            assertEquals(sequential.getZ(), output.get(i).getZ(), 0.001);
        }
    }
}
