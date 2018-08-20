/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Authors: Juan Fumero, Michalis Papadimitriou
 *
 */
package uk.ac.manchester.tornado.unittests.vectortypes;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.collections.types.Float2;
import uk.ac.manchester.tornado.collections.types.Float3;
import uk.ac.manchester.tornado.collections.types.Float4;
import uk.ac.manchester.tornado.collections.types.VectorFloat3;
import uk.ac.manchester.tornado.collections.types.VectorFloat4;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

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
            a[i] = (float) i;
        }

        //@formatter:off
        new TaskSchedule("s0")
            .task("t0", TestVectorAllocation::testVectorAlloc, a, output)
            .streamOut(output)
            .execute();
        //@formatter:on

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
            a[i] = (float) i;
        }

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestVectorAllocation::testVectorAlloc2, a, output)
                .streamOut(output)
                .execute();
        //@formatter:on

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
            a[i] = (float) i;
        }

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestVectorAllocation::testVectorAlloc3, a, output)
                .streamOut(output)
                .execute();
        //@formatter:on

        for (int i = 0; i < size; i++) {
            Float3 sequential = new Float3(a.length, 10, a[i]);
            assertEquals(sequential.getX(), output.get(i).getX(), 0.001);
            assertEquals(sequential.getY(), output.get(i).getY(), 0.001);
            assertEquals(sequential.getZ(), output.get(i).getZ(), 0.001);
        }
    }
}
