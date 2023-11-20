/*
 * Copyright (c) 2013-2020, 2022 APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.unittests.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.CharArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.unittests.arrays.TestArrays;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to run?
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestAPI
 * </code>
 * </p>
 */
public class TestAPI extends TornadoTestBase {
    // CHECKSTYLE:OFF

    @Test
    public void testSegmentsByte() {
        ByteArray dataA = ByteArray.fromElements((byte) 0, (byte) 1, (byte) 2, (byte) 3);
        ByteArray dataB = ByteArray.fromArray(new byte[] { 0, 1, 2, 3 });

        for (int i = 0; i < dataA.getSize(); i++) {
            assertEquals(dataA.get(i), dataB.get(i));
        }
        byte[] fArray = dataA.toHeapArray();
        for (int i = 0; i < dataA.getSize(); i++) {
            assertEquals(fArray[i], dataA.get(i));
        }

        byte[] fArrayB = dataB.toHeapArray();
        for (int i = 0; i < dataA.getSize(); i++) {
            assertEquals(fArrayB[i], dataB.get(i));
        }
    }

    @Test
    public void testSegmentsChar() {
        CharArray dataA = CharArray.fromElements((char) 0, (char) 1, (char) 2, (char) 3);
        CharArray dataB = CharArray.fromArray(new char[] { 0, 1, 2, 3 });

        for (int i = 0; i < dataA.getSize(); i++) {
            assertEquals(dataA.get(i), dataB.get(i));
        }
        char[] fArray = dataA.toHeapArray();
        for (int i = 0; i < dataA.getSize(); i++) {
            assertEquals(fArray[i], dataA.get(i));
        }

        char[] fArrayB = dataB.toHeapArray();
        for (int i = 0; i < dataA.getSize(); i++) {
            assertEquals(fArrayB[i], dataB.get(i));
        }
    }

    @Test
    public void testSegmentsShort() {
        ShortArray dataA = ShortArray.fromElements((short) 0, (short) 1, (short) 2, (short) 3);
        ShortArray dataB = ShortArray.fromArray(new short[] { 0, 1, 2, 3 });

        for (int i = 0; i < dataA.getSize(); i++) {
            assertEquals(dataA.get(i), dataB.get(i));
        }
        short[] fArray = dataA.toHeapArray();
        for (int i = 0; i < dataA.getSize(); i++) {
            assertEquals(fArray[i], dataA.get(i));
        }

        short[] fArrayB = dataB.toHeapArray();
        for (int i = 0; i < dataA.getSize(); i++) {
            assertEquals(fArrayB[i], dataB.get(i));
        }
    }

    @Test
    public void testSegmentsIntegers() {
        IntArray dataA = IntArray.fromElements(0, 1, 2, 3);
        IntArray dataB = IntArray.fromArray(new int[] { 0, 1, 2, 3 });

        for (int i = 0; i < dataA.getSize(); i++) {
            assertEquals(dataA.get(i), dataB.get(i));
        }
        int[] fArray = dataA.toHeapArray();
        for (int i = 0; i < dataA.getSize(); i++) {
            assertEquals(fArray[i], dataA.get(i));
        }

        int[] fArrayB = dataB.toHeapArray();
        for (int i = 0; i < dataA.getSize(); i++) {
            assertEquals(fArrayB[i], dataB.get(i));
        }
    }

    @Test
    public void testSegmentsLong() {
        LongArray dataA = LongArray.fromElements(0, 1, 2, 3);
        LongArray dataB = LongArray.fromArray(new long[] { 0, 1, 2, 3 });

        for (int i = 0; i < dataA.getSize(); i++) {
            assertEquals(dataA.get(i), dataB.get(i));
        }
        long[] fArray = dataA.toHeapArray();
        for (int i = 0; i < dataA.getSize(); i++) {
            assertEquals(fArray[i], dataA.get(i));
        }

        long[] fArrayB = dataB.toHeapArray();
        for (int i = 0; i < dataA.getSize(); i++) {
            assertEquals(fArrayB[i], dataB.get(i));
        }
    }

    @Test
    public void testSegmentsFloats() {
        FloatArray dataA = FloatArray.fromElements(0, 1, 2, 3);
        FloatArray dataB = FloatArray.fromArray(new float[] { 0, 1, 2, 3 });

        for (int i = 0; i < dataA.getSize(); i++) {
            assertEquals(dataA.get(i), dataB.get(i), 0.01f);
        }
        float[] fArray = dataA.toHeapArray();
        for (int i = 0; i < dataA.getSize(); i++) {
            assertEquals(fArray[i], dataA.get(i), 0.01f);
        }

        float[] fArrayB = dataB.toHeapArray();
        for (int i = 0; i < dataA.getSize(); i++) {
            assertEquals(fArrayB[i], dataB.get(i), 0.01f);
        }
    }

    @Test
    public void testSegmentsDouble() {
        DoubleArray dataA = DoubleArray.fromElements(0, 1, 2, 3);
        DoubleArray dataB = DoubleArray.fromArray(new double[] { 0, 1, 2, 3 });

        for (int i = 0; i < dataA.getSize(); i++) {
            assertEquals(dataA.get(i), dataB.get(i), 0.001);
        }
        double[] fArray = dataA.toHeapArray();
        for (int i = 0; i < dataA.getSize(); i++) {
            assertEquals(fArray[i], dataA.get(i), 0.001);
        }

        double[] fArrayB = dataB.toHeapArray();
        for (int i = 0; i < dataA.getSize(); i++) {
            assertEquals(fArrayB[i], dataB.get(i), 0.001);
        }
    }

    @Test
    public void testLazyCopyOut() {
        final int N = 1024;
        int size = 20;
        IntArray data = new IntArray(N);

        IntStream.range(0, N).parallel().forEach(idx -> data.set(idx, size));

        TaskGraph taskGraph = new TaskGraph("s0");
        assertNotNull(taskGraph);

        taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestArrays::addAccumulator, data, 1) //
                .transferToHost(DataTransferMode.USER_DEFINED, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        TornadoExecutionResult executionResult = executionPlan.execute();

        // Force data transfers from D->H after the execution of a task-graph
        executionResult.transferToHost(data);

        // Mark all device memory buffers as free, thus the TornadoVM runtime can reuse
        // device buffers for other execution plans.
        executionPlan.freeDeviceMemory();

        for (int i = 0; i < N; i++) {
            assertEquals(21, data.get(i));
        }
    }

    @Test
    public void testLazyCopyOut2() {
        final int N = 128;
        int size = 20;

        IntArray data = new IntArray(N);

        IntStream.range(0, N).parallel().forEach(idx -> data.set(idx, size));

        TaskGraph taskGraph = new TaskGraph("s0");
        assertNotNull(taskGraph);

        taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, data);
        taskGraph.task("t0", TestArrays::addAccumulator, data, 1);
        taskGraph.transferToHost(DataTransferMode.USER_DEFINED, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlanPlan = new TornadoExecutionPlan(immutableTaskGraph);

        TornadoExecutionResult executionResult = executionPlanPlan.execute();

        executionResult.transferToHost(data);

        executionPlanPlan.freeDeviceMemory();

        for (int i = 0; i < N; i++) {
            assertEquals(21, data.get(i));
        }
    }

    @Test
    public void testWarmUp() {
        final int N = 128;
        int size = 20;

        IntArray data = new IntArray(N);

        IntStream.range(0, N).parallel().forEach(idx -> data.set(idx, size));

        TaskGraph taskGraph = new TaskGraph("s0");
        assertNotNull(taskGraph);

        taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestArrays::addAccumulator, data, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withWarmUp() //
                .execute();

        executionPlan.freeDeviceMemory();

        for (int i = 0; i < N; i++) {
            assertEquals(21, data.get(i));
        }
    }
    // CHECKSTYLE:ON
}
