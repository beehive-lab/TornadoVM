/*
 * Copyright (c) 2013-2020, 2022, 2024, APT Group, Department of Computer Science,
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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.DoubleBuffer;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.DataRange;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.CharArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
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

    /**
     * Perform the copy out under demand. It performs a copy from the device to the host of the entire array via the execution result.
     */
    @Test
    public void testSegmentsHalfFloats() {
        HalfFloatArray dataA = HalfFloatArray.fromElements(new HalfFloat(0), new HalfFloat(1), new HalfFloat(2), new HalfFloat(3));
        HalfFloatArray dataB = HalfFloatArray.fromArray(new HalfFloat[] { new HalfFloat(0), new HalfFloat(1), new HalfFloat(2), new HalfFloat(3) });

        for (int i = 0; i < dataA.getSize(); i++) {
            assertEquals(dataA.get(i).getFloat32(), dataB.get(i).getFloat32(), 0.01f);
        }
        HalfFloat[] fArray = dataA.toHeapArray();
        for (int i = 0; i < dataA.getSize(); i++) {
            assertEquals(fArray[i].getFloat32(), dataA.get(i).getFloat32(), 0.01f);
        }

        HalfFloat[] fArrayB = dataB.toHeapArray();
        for (int i = 0; i < dataA.getSize(); i++) {
            assertEquals(fArrayB[i].getFloat32(), dataB.get(i).getFloat32(), 0.01f);
        }
    }

    @Test
    public void testLazyCopyOut() throws TornadoExecutionPlanException {
        final int N = 1024;
        int size = 20;
        IntArray data = new IntArray(N);

        data.init(size);

        TaskGraph taskGraph = new TaskGraph("s0");
        assertNotNull(taskGraph);

        taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestArrays::addAccumulator, data, 1) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {

            TornadoExecutionResult executionResult = executionPlan.execute();

            // Force data transfers from D->H after the execution of a task-graph
            executionResult.transferToHost(data);
        }

        for (int i = 0; i < N; i++) {
            assertEquals(21, data.get(i));
        }
    }

    /**
     * Perform the copy out under demand. It performs a copy from the device to the host of the entire array via the execution result.
     * In this test, input and output use the same array.
     */
    @Test
    public void testLazyCopyOut2() throws TornadoExecutionPlanException {
        final int N = 128;
        int size = 20;

        IntArray data = new IntArray(N);

        IntStream.range(0, N).parallel().forEach(idx -> data.set(idx, size));

        TaskGraph taskGraph = new TaskGraph("s0");
        assertNotNull(taskGraph);

        taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, data);
        taskGraph.task("t0", TestArrays::addAccumulator, data, 1);
        taskGraph.transferToHost(DataTransferMode.UNDER_DEMAND, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {

            TornadoExecutionResult executionResult = executionPlan.execute();

            executionResult.transferToHost(data);
        }

        for (int i = 0; i < N; i++) {
            assertEquals(21, data.get(i));
        }
    }

    /**
     * Perform the copy out under demand. It performs a copy of a subset of the output array.
     */
    @Test
    public void testLazyPartialCopyOut() throws TornadoExecutionPlanException {
        final int N = 1024;
        int size = 20;
        IntArray data = new IntArray(N);

        IntStream.range(0, N).parallel().forEach(idx -> data.set(idx, size));

        TaskGraph taskGraph = new TaskGraph("s0");
        assertNotNull(taskGraph);

        taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestArrays::addAccumulator, data, 1) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            TornadoExecutionResult executionResult = executionPlan.execute();

            DataRange dataRange = new DataRange(data);

            executionResult.transferToHost(dataRange.withSize(N / 2));

            executionResult.transferToHost(dataRange.withOffset(N / 2).withSize(N / 2));
        }

        for (int i = 0; i < N; i++) {
            assertEquals(21, data.get(i));
        }
    }

    @Test
    public void testWarmUp() throws TornadoExecutionPlanException {
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
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withWarmUp() //
                    .execute();
        }

        for (int i = 0; i < N; i++) {
            assertEquals(21, data.get(i));
        }
    }

    @Test
    public void testBuildWithSegmentsFloat() {

        final int n = 10;
        // Allocate 10 elements
        MemorySegment m = Arena.ofAuto().allocate(ValueLayout.JAVA_FLOAT.byteSize() * n);

        // Set 10 elements
        for (int i = 0; i < n; i++) {
            m.setAtIndex(ValueLayout.JAVA_FLOAT, i, 10 + i);
        }

        // Factory method to build a float array from a segment
        FloatArray floatArray = FloatArray.fromSegment(m);

        for (int i = 0; i < n; i++) {
            assertEquals(10 + i, floatArray.get(i), 0.001f);
        }
    }

    @Test
    public void testBuildWithSegmentsDouble() {

        final int n = 10;
        // Allocate 10 elements
        MemorySegment m = Arena.ofAuto().allocate(ValueLayout.JAVA_DOUBLE.byteSize() * n);

        // Set 10 elements
        for (int i = 0; i < n; i++) {
            m.setAtIndex(ValueLayout.JAVA_DOUBLE, i, 10 + i);
        }

        // Factory method to build a double array from a segment
        DoubleArray doubleArray = DoubleArray.fromSegment(m);

        for (int i = 0; i < n; i++) {
            assertEquals(10 + i, doubleArray.get(i), 0.001);
        }
    }

    @Test
    public void testBuildWithSegmentsInt() {

        final int n = 10;
        // Allocate 10 elements
        MemorySegment m = Arena.ofAuto().allocate(ValueLayout.JAVA_INT.byteSize() * n);

        // Set 10 elements
        for (int i = 0; i < n; i++) {
            m.setAtIndex(ValueLayout.JAVA_INT, i, 10 + i);
        }

        // Factory method to build an integer array from a segment
        IntArray intArray = IntArray.fromSegment(m);

        for (int i = 0; i < n; i++) {
            assertEquals(10 + i, intArray.get(i));
        }
    }

    @Test
    public void testBuildWithSegmentsLong() {

        final int n = 10;
        // Allocate 10 elements
        MemorySegment m = Arena.ofAuto().allocate(ValueLayout.JAVA_LONG.byteSize() * n);

        // Set 10 elements
        for (int i = 0; i < n; i++) {
            m.setAtIndex(ValueLayout.JAVA_LONG, i, 10 + i);
        }

        // Factory method to build a long array from a segment
        LongArray longArray = LongArray.fromSegment(m);

        for (int i = 0; i < n; i++) {
            assertEquals(10 + i, longArray.get(i));
        }
    }

    @Test
    public void testBuildWithSegmentsShort() {

        final int n = 10;
        // Allocate 10 elements
        MemorySegment m = Arena.ofAuto().allocate(ValueLayout.JAVA_SHORT.byteSize() * n);

        // Set 10 elements
        for (int i = 0; i < n; i++) {
            m.setAtIndex(ValueLayout.JAVA_SHORT, i, (short) (10 + i));
        }

        // Factory method to build a short array from a segment
        ShortArray shortArray = ShortArray.fromSegment(m);

        for (int i = 0; i < n; i++) {
            assertEquals((short) 10 + i, shortArray.get(i));
        }
    }

    @Test
    public void testBuildWithSegmentsByte() {

        final int n = 10;
        // Allocate 10 elements
        MemorySegment m = Arena.ofAuto().allocate(ValueLayout.JAVA_BYTE.byteSize() * n);

        // Set 10 elements
        for (int i = 0; i < n; i++) {
            m.setAtIndex(ValueLayout.JAVA_BYTE, i, (byte) (10 + i));
        }

        ByteArray array = ByteArray.fromSegment(m);

        for (int i = 0; i < n; i++) {
            assertEquals((byte) 10 + i, array.get(i));
        }
    }

    @Test
    public void testBuildWithSegmentsChar() {

        final int n = 10;
        // Allocate 10 elements
        MemorySegment m = Arena.ofAuto().allocate(ValueLayout.JAVA_CHAR.byteSize() * n);

        // Set 10 elements
        for (int i = 0; i < n; i++) {
            m.setAtIndex(ValueLayout.JAVA_CHAR, i, (char) (10 + i));
        }

        CharArray charArray = CharArray.fromSegment(m);

        for (int i = 0; i < n; i++) {
            assertEquals((char) 10 + i, charArray.get(i));
        }
    }

    @Test
    public void testBuildWithSegmentsHalfFloat() {

        final int n = 10;
        // Allocate 10 elements
        MemorySegment m = Arena.ofAuto().allocate(ValueLayout.JAVA_SHORT.byteSize() * n);

        // Set 10 elements
        for (int i = 0; i < n; i++) {
            m.setAtIndex(ValueLayout.JAVA_SHORT, i, Float.floatToFloat16(10 + i));
        }

        // Factory method to build a float array from a segment
        HalfFloatArray halfFloatArray = HalfFloatArray.fromSegment(m);

        for (int i = 0; i < n; i++) {
            assertEquals(10 + i, halfFloatArray.get(i).getFloat32(), 0.001f);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildWithSegmentsWrongSize() {
        final int num_elements = 10;
        final int additional_bytes = 1;
        final long byteSize = Integer.BYTES * num_elements + additional_bytes;

        MemorySegment m = Arena.ofAuto().allocate(byteSize);
        IntArray intArray = IntArray.fromSegment(m);
    }

    public static void simpleAddition(DoubleArray a, DoubleArray b) {
        for (@Parallel int i = 0; i < b.getSize(); i++) {
            b.set(i, a.get(i) + 1);
        }
    }

    @Test
    public void testArrayFromMemorySegment() throws TornadoExecutionPlanException {
        final int numberOfElements = 256;

        double[] someArray = new double[numberOfElements];
        IntStream.range(0, numberOfElements).sequential().forEach(i -> {
            someArray[i] = i;
        });

        MemorySegment a = MemorySegment.ofArray(someArray);
        DoubleArray dataA = DoubleArray.fromSegment(a);

        DoubleArray dataB = new DoubleArray(numberOfElements);

        DoubleArray dataBSeq = new DoubleArray(numberOfElements);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, dataA) //
                .task("t0", TestAPI::simpleAddition, dataA, dataB) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, dataB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        simpleAddition(dataA, dataBSeq);

        for (int i = 0; i < dataB.getSize(); i++) {
            assertEquals(dataBSeq.get(i), dataB.get(i), 0.01f);
        }
    }

    @Test
    public void testArrayFromBuffer() throws TornadoExecutionPlanException {
        final int numberOfElements = 256;

        double[] someArray = new double[numberOfElements];
        IntStream.range(0, numberOfElements).sequential().forEach(i -> {
            someArray[i] = i;
        });

        DoubleBuffer buffer = DoubleBuffer.allocate(someArray.length);
        buffer.put(someArray);
        buffer.flip();
        DoubleArray dataA = DoubleArray.fromDoubleBuffer(buffer);

        DoubleArray dataB = new DoubleArray(numberOfElements);

        DoubleArray dataBSeq = new DoubleArray(numberOfElements);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, dataA) //
                .task("t0", TestAPI::simpleAddition, dataA, dataB) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, dataB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        simpleAddition(dataA, dataBSeq);

        for (int i = 0; i < dataB.getSize(); i++) {
            assertEquals(dataBSeq.get(i), dataB.get(i), 0.01f);
        }
    }

    // CHECKSTYLE:ON
}
