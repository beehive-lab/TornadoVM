/*
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat3;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat4;
import uk.ac.manchester.tornado.api.types.vectors.Float2;
import uk.ac.manchester.tornado.api.types.vectors.Float3;
import uk.ac.manchester.tornado.api.types.vectors.Float4;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run.
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.vectortypes.TestVectorAllocation
 * </code>
 */
public class TestVectorAllocation extends TornadoTestBase {

    /**
     * Test to check the kernel can create a float2 type.
     *
     * @param a
     * @param result
     */
    private static void testVectorAlloc(FloatArray a, FloatArray result) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            Float2 x = new Float2(1, 10);
            result.set(i, a.get(i) + (x.getX() * x.getY()));
        }
    }

    /**
     * Test to check the kernel can create a float2 type.
     *
     * @param a
     * @param result
     */
    private static void testVectorAlloc2(FloatArray a, VectorFloat4 result) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            Float4 x = new Float4(a.getSize(), 10, a.get(i), a.get(i) * 10);
            result.set(i, x);
        }
    }

    /**
     * Test to check the kernel can create a float2 type.
     *
     * @param a
     * @param result
     */
    private static void testVectorAlloc3(FloatArray a, VectorFloat3 result) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            Float3 x = new Float3(a.getSize(), 10, a.get(i));
            result.set(i, x);
        }
    }

    @Test
    public void testAllocation1() throws TornadoExecutionPlanException {

        int size = 8;

        FloatArray a = new FloatArray(size);
        FloatArray output = new FloatArray(size);

        for (int i = 0; i < size; i++) {
            a.set(i, i);
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestVectorAllocation::testVectorAlloc, a, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(a.get(i) + (10), output.get(i), 0.001);
        }
    }

    @Test
    public void testAllocation2() throws TornadoExecutionPlanException {

        int size = 8;

        FloatArray a = new FloatArray(size);
        VectorFloat4 output = new VectorFloat4(size);

        for (int i = 0; i < size; i++) {
            a.set(i, i);
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestVectorAllocation::testVectorAlloc2, a, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Float4 sequential = new Float4(a.getSize(), 10, a.get(i), a.get(i) * 10);
            assertEquals(sequential.getX(), output.get(i).getX(), 0.001);
            assertEquals(sequential.getY(), output.get(i).getY(), 0.001);
            assertEquals(sequential.getZ(), output.get(i).getZ(), 0.001);
            assertEquals(sequential.getW(), output.get(i).getW(), 0.001);
        }
    }

    @Test
    public void testAllocation3() throws TornadoExecutionPlanException {

        int size = 8;

        FloatArray a = new FloatArray(size);
        VectorFloat3 output = new VectorFloat3(size);

        for (int i = 0; i < size; i++) {
            a.set(i, i);
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestVectorAllocation::testVectorAlloc3, a, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Float3 sequential = new Float3(a.getSize(), 10, a.get(i));
            assertEquals(sequential.getX(), output.get(i).getX(), 0.001);
            assertEquals(sequential.getY(), output.get(i).getY(), 0.001);
            assertEquals(sequential.getZ(), output.get(i).getZ(), 0.001);
        }
    }
}
