/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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

import java.util.Random;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.collections.VectorInt;
import uk.ac.manchester.tornado.api.types.collections.VectorInt16;
import uk.ac.manchester.tornado.api.types.collections.VectorInt2;
import uk.ac.manchester.tornado.api.types.collections.VectorInt3;
import uk.ac.manchester.tornado.api.types.collections.VectorInt4;
import uk.ac.manchester.tornado.api.types.collections.VectorInt8;
import uk.ac.manchester.tornado.api.types.vectors.Int16;
import uk.ac.manchester.tornado.api.types.vectors.Int2;
import uk.ac.manchester.tornado.api.types.vectors.Int3;
import uk.ac.manchester.tornado.api.types.vectors.Int4;
import uk.ac.manchester.tornado.api.types.vectors.Int8;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.vectortypes.TestInts
 * </code>
 */
public class TestInts extends TornadoTestBase {

    private static void addInt2(Int2 a, Int2 b, VectorInt results) {
        Int2 i2 = Int2.add(a, b);
        int r = i2.getX() + i2.getY();
        results.set(0, r);
    }

    private static void addInt3(Int3 a, Int3 b, VectorInt results) {
        Int3 i3 = Int3.add(a, b);
        int r = i3.getX() + i3.getY() + i3.getZ();
        results.set(0, r);
    }

    private static void addInt8(Int8 a, Int8 b, VectorInt results) {
        Int8 i8 = Int8.add(a, b);
        int r = i8.getS0() + i8.getS1() + i8.getS2() + i8.getS3() + i8.getS4() + i8.getS5() + i8.getS6() + i8.getS7();
        results.set(0, r);
    }

    private static void addInt4(Int4 a, Int4 b, VectorInt results) {
        Int4 i4 = Int4.add(a, b);
        int r = i4.getX() + i4.getY() + i4.getZ() + i4.getW();
        results.set(0, r);
    }

    private static void addIntVectors(IntArray a, IntArray b, IntArray result) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            result.set(i, a.get(i) + b.get(i));
        }
    }

    public static void dotProductFunctionMap(IntArray a, IntArray b, IntArray results) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            results.set(i, a.get(i) + b.get(i));

        }
    }

    public static void dotProductFunctionReduce(IntArray input, IntArray results) {
        int sum = 0;
        for (int i = 0; i < input.getSize(); i++) {
            sum += input.get(i);
        }
        results.set(0, sum);
    }

    public static void addVectorInt2(VectorInt2 a, VectorInt2 b, VectorInt2 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Int2.add(a.get(i), b.get(i)));
        }
    }

    public static void subVectorInt2(VectorInt2 a, VectorInt2 b, VectorInt2 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Int2.sub(a.get(i), b.get(i)));
        }
    }

    public static void divVectorInt2(VectorInt2 a, VectorInt2 b, VectorInt2 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Int2.div(a.get(i), b.get(i)));
        }
    }

    public static void addVectorInt3(VectorInt3 a, VectorInt3 b, VectorInt3 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Int3.add(a.get(i), b.get(i)));
        }
    }

    public static void subVectorInt3(VectorInt3 a, VectorInt3 b, VectorInt3 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Int3.sub(a.get(i), b.get(i)));
        }
    }

    public static void divVectorInt3(VectorInt3 a, VectorInt3 b, VectorInt3 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Int3.div(a.get(i), b.get(i)));
        }
    }

    public static void addVectorInt4(VectorInt4 a, VectorInt4 b, VectorInt4 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Int4.add(a.get(i), b.get(i)));
        }
    }

    public static void subVectorInt4(VectorInt4 a, VectorInt4 b, VectorInt4 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Int4.sub(a.get(i), b.get(i)));
        }
    }

    public static void divVectorInt4(VectorInt4 a, VectorInt4 b, VectorInt4 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Int4.div(a.get(i), b.get(i)));
        }
    }

    public static void addVectorInt8(VectorInt8 a, VectorInt8 b, VectorInt8 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Int8.add(a.get(i), b.get(i)));
        }
    }

    public static void addVectorInt16(VectorInt16 a, VectorInt16 b, VectorInt16 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Int16.add(a.get(i), b.get(i)));
        }
    }

    public static void subVectorInt8(VectorInt8 a, VectorInt8 b, VectorInt8 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Int8.sub(a.get(i), b.get(i)));
        }
    }

    public static void divVectorInt8(VectorInt8 a, VectorInt8 b, VectorInt8 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Int8.div(a.get(i), b.get(i)));
        }
    }

    public static void testPrivateVectorInt2(VectorInt2 output) {
        VectorInt2 vectorInt2 = new VectorInt2(output.getLength());

        for (int i = 0; i < vectorInt2.getLength(); i++) {
            vectorInt2.set(i, new Int2(i, i));
        }

        Int2 sum = new Int2(0, 0);

        for (int i = 0; i < vectorInt2.getLength(); i++) {
            Int2 f = vectorInt2.get(i);
            sum = Int2.add(f, sum);
        }

        output.set(0, sum);
    }

    public static void testPrivateVectorInt4(VectorInt4 output) {
        VectorInt4 vectorInt4 = new VectorInt4(output.getLength());

        for (int i = 0; i < vectorInt4.getLength(); i++) {
            vectorInt4.set(i, new Int4(i, i, i, i));
        }

        Int4 sum = new Int4(0, 0, 0, 0);

        for (int i = 0; i < vectorInt4.getLength(); i++) {
            Int4 f = vectorInt4.get(i);
            sum = Int4.add(f, sum);
        }

        output.set(0, sum);
    }

    public static void testPrivateVectorInt8(VectorInt8 output) {
        VectorInt8 vectorInt8 = new VectorInt8(output.getLength());

        for (int i = 0; i < vectorInt8.getLength(); i++) {
            vectorInt8.set(i, new Int8(i, i, i, i, i, i, i, i));
        }

        Int8 sum = new Int8(0, 0, 0, 0, 0, 0, 0, 0);

        for (int i = 0; i < vectorInt8.getLength(); i++) {
            Int8 f = vectorInt8.get(i);
            sum = Int8.add(f, sum);
        }

        output.set(0, sum);
    }

    private static void vectorComputation01(VectorInt2 value, VectorInt2 output) {
        for (@Parallel int i = 0; i < output.getLength(); i++) {
            Int2 int2 = new Int2();
            int2.setX(value.get(i).getX() + value.get(i).getY());
            int2.setY(value.get(i).getX() * 2);
            output.set(i, int2);
        }
    }

    private static void vectorComputation02(VectorInt3 value, VectorInt3 output) {
        for (@Parallel int i = 0; i < output.getLength(); i++) {
            Int3 int3 = new Int3();
            int3.setX(value.get(i).getX() + value.get(i).getY());
            int3.setY(value.get(i).getZ() * 2);
            output.set(i, int3);
        }
    }

    private static Int3 vectorComputation03(final Int4 value) {
        Int3 output = new Int3();
        output.setX(value.getX() + value.getY());
        output.setY(value.getY());
        output.setZ(value.getW());
        return output;
    }

    private static void vectorComputation03(VectorInt4 value, VectorInt3 output) {
        for (@Parallel int i = 0; i < output.getLength(); i++) {
            output.set(i, vectorComputation03(value.get(i)));
        }
    }

    private static void vectorComputation04(VectorInt8 value, VectorInt2 output) {
        for (@Parallel int i = 0; i < output.getLength(); i++) {
            Int2 int2 = new Int2();
            int2.setX(value.get(i).getS0() + value.get(i).getS1());
            int2.setY(value.get(i).getS1());
            output.set(i, int2);
        }
    }

    @Test
    public void testAddInt2() throws TornadoExecutionPlanException {
        int size = 1;
        Int2 a = new Int2(1, 2);
        Int2 b = new Int2(3, 2);
        VectorInt output = new VectorInt(size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestInts::addInt2, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(8, output.get(i));
        }
    }

    @Test
    public void testAddInt3() throws TornadoExecutionPlanException {
        int size = 1;
        Int3 a = new Int3(1, 2, 3);
        Int3 b = new Int3(3, 2, 1);
        VectorInt output = new VectorInt(size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestInts::addInt3, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(12, output.get(i));
        }
    }

    @Test
    public void testAddInt8() throws TornadoExecutionPlanException {
        int size = 1;
        Int8 a = new Int8(1, 2, 3, 4, 1, 2, 3, 4);
        Int8 b = new Int8(4, 3, 2, 1, 1, 2, 3, 4);
        VectorInt output = new VectorInt(size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestInts::addInt8, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(40, output.get(i));
        }
    }

    @Test
    public void testAddInt4() throws TornadoExecutionPlanException {
        int size = 1;
        Int4 a = new Int4(1, 2, 3, 4);
        Int4 b = new Int4(4, 3, 2, 1);
        VectorInt output = new VectorInt(size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestInts::addInt4, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(20, output.get(i));
        }
    }

    @Test
    public void testAddInt() throws TornadoExecutionPlanException {

        int size = 8;

        IntArray a = new IntArray(size);
        IntArray b = new IntArray(size);
        IntArray output = new IntArray(size);

        for (int i = 0; i < size; i++) {
            a.set(i, i);
            b.set(i, i);
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestInts::addIntVectors, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(i + i, output.get(i));
        }
    }

    @Test
    public void testDotProductDouble() throws TornadoExecutionPlanException {

        int size = 8;

        IntArray a = new IntArray(size);
        IntArray b = new IntArray(size);
        IntArray outputMap = new IntArray(size);
        IntArray outputReduce = new IntArray(1);

        IntArray seqMap = new IntArray(size);
        IntArray seqReduce = new IntArray(1);

        Random r = new Random();
        for (int i = 0; i < size; i++) {
            a.set(i, r.nextInt(1000));
            b.set(i, r.nextInt(1000));
        }

        // Sequential computation
        dotProductFunctionMap(a, b, seqMap);
        dotProductFunctionReduce(seqMap, seqReduce);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b, outputMap) //
                .task("t0-MAP", TestInts::dotProductFunctionMap, a, b, outputMap) //
                .task("t1-REDUCE", TestInts::dotProductFunctionReduce, outputMap, outputReduce) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputReduce);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertEquals(seqReduce.get(0), outputReduce.get(0));
    }

    @Test
    public void testVectorAddInt2() throws TornadoExecutionPlanException {
        int size = 16;

        VectorInt2 a = new VectorInt2(size);
        VectorInt2 b = new VectorInt2(size);
        VectorInt2 output = new VectorInt2(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Int2(i, i));
            b.set(i, new Int2(size - i, size - i));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestInts::addVectorInt2, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Int2 sequential = new Int2(i + (size - i), i + (size - i));
            assertEquals(sequential.getX(), output.get(i).getX());
            assertEquals(sequential.getY(), output.get(i).getY());
        }
    }

    @Test
    public void testVectorSubInt2() throws TornadoExecutionPlanException {
        int size = 16;

        VectorInt2 a = new VectorInt2(size);
        VectorInt2 b = new VectorInt2(size);
        VectorInt2 output = new VectorInt2(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Int2(i, i));
            b.set(i, new Int2(size - i, size - i));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestInts::subVectorInt2, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Int2 sequential = new Int2(i - (size - i), i - (size - i));
            assertEquals(sequential.getX(), output.get(i).getX());
            assertEquals(sequential.getY(), output.get(i).getY());
        }
    }

    @Test
    public void testVectorDivInt2() throws TornadoExecutionPlanException {
        int size = 16;

        VectorInt2 a = new VectorInt2(size);
        VectorInt2 b = new VectorInt2(size);
        VectorInt2 output = new VectorInt2(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Int2(i, i));
            b.set(i, new Int2(size - i, size - i));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestInts::divVectorInt2, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Int2 sequential = new Int2(i / (size - i), i / (size - i));
            assertEquals(sequential.getX(), output.get(i).getX());
            assertEquals(sequential.getY(), output.get(i).getY());
        }
    }

    @Test
    public void testVectorAddInt3() throws TornadoExecutionPlanException {
        int size = 8;

        VectorInt3 a = new VectorInt3(size);
        VectorInt3 b = new VectorInt3(size);
        VectorInt3 output = new VectorInt3(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Int3(i, i, i));
            b.set(i, new Int3(size - i, size - i, size - i));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestInts::addVectorInt3, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Int3 sequential = new Int3(i + (size - i), i + (size - i), i + (size - i));
            assertEquals(sequential.getX(), output.get(i).getX());
            assertEquals(sequential.getY(), output.get(i).getY());
            assertEquals(sequential.getZ(), output.get(i).getZ());
        }
    }

    @Test
    public void testVectorSubInt3() throws TornadoExecutionPlanException {
        int size = 8;

        VectorInt3 a = new VectorInt3(size);
        VectorInt3 b = new VectorInt3(size);
        VectorInt3 output = new VectorInt3(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Int3(i, i, i));
            b.set(i, new Int3(size - i, size - i, size - i));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestInts::subVectorInt3, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Int3 sequential = new Int3(i - (size - i), i - (size - i), i - (size - i));
            assertEquals(sequential.getX(), output.get(i).getX());
            assertEquals(sequential.getY(), output.get(i).getY());
            assertEquals(sequential.getZ(), output.get(i).getZ());
        }
    }

    @Test
    public void testVectorDivInt3() throws TornadoExecutionPlanException {
        int size = 8;

        VectorInt3 a = new VectorInt3(size);
        VectorInt3 b = new VectorInt3(size);
        VectorInt3 output = new VectorInt3(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Int3(i, i, i));
            b.set(i, new Int3(size - i, size - i, size - i));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestInts::divVectorInt3, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Int3 sequential = new Int3(i / (size - i), i / (size - i), i / (size - i));
            assertEquals(sequential.getX(), output.get(i).getX());
            assertEquals(sequential.getY(), output.get(i).getY());
            assertEquals(sequential.getZ(), output.get(i).getZ());
        }
    }

    @Test
    public void testVectorAddInt4() throws TornadoExecutionPlanException {
        int size = 8;

        VectorInt4 a = new VectorInt4(size);
        VectorInt4 b = new VectorInt4(size);
        VectorInt4 output = new VectorInt4(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Int4(i, i, i, i));
            b.set(i, new Int4(size - i, size - i, size - i, size));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestInts::addVectorInt4, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Int4 sequential = new Int4(i + (size - i), i + (size - i), i + (size - i), i + size);
            assertEquals(sequential.getX(), output.get(i).getX());
            assertEquals(sequential.getY(), output.get(i).getY());
            assertEquals(sequential.getZ(), output.get(i).getZ());
            assertEquals(sequential.getW(), output.get(i).getW());
        }
    }

    @Test
    public void testVectorSubInt4() throws TornadoExecutionPlanException {
        int size = 8;

        VectorInt4 a = new VectorInt4(size);
        VectorInt4 b = new VectorInt4(size);
        VectorInt4 output = new VectorInt4(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Int4(i, i, i, i));
            b.set(i, new Int4(size - i, size - i, size - i, size));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestInts::subVectorInt4, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Int4 sequential = new Int4(i - (size - i), i - (size - i), i - (size - i), i - size);
            assertEquals(sequential.getX(), output.get(i).getX());
            assertEquals(sequential.getY(), output.get(i).getY());
            assertEquals(sequential.getZ(), output.get(i).getZ());
            assertEquals(sequential.getW(), output.get(i).getW());
        }
    }

    @Test
    public void testVectorDivInt4() throws TornadoExecutionPlanException {
        int size = 8;

        VectorInt4 a = new VectorInt4(size);
        VectorInt4 b = new VectorInt4(size);
        VectorInt4 output = new VectorInt4(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Int4(i, i, i, i));
            b.set(i, new Int4(size - i, size - i, size - i, size));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestInts::divVectorInt4, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Int4 sequential = new Int4(i / (size - i), i / (size - i), i / (size - i), i / size);
            assertEquals(sequential.getX(), output.get(i).getX());
            assertEquals(sequential.getY(), output.get(i).getY());
            assertEquals(sequential.getZ(), output.get(i).getZ());
            assertEquals(sequential.getW(), output.get(i).getW());
        }
    }

    @Test
    public void testVectorAddInt8() throws TornadoExecutionPlanException {
        int size = 256;

        VectorInt8 a = new VectorInt8(size);
        VectorInt8 b = new VectorInt8(size);
        VectorInt8 output = new VectorInt8(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Int8(i, i, i, i, i, i, i, i));
            b.set(i, new Int8(size - i, size - i, size - i, size, size - i, size - i, size - i, size));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestInts::addVectorInt8, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Int8 sequential = new Int8(i + (size - i), i + (size - i), i + (size - i), i + size, i + (size - i), i + (size - i), i + (size - i), i + size);
            assertEquals(sequential.getS0(), output.get(i).getS0());
            assertEquals(sequential.getS1(), output.get(i).getS1());
            assertEquals(sequential.getS2(), output.get(i).getS2());
            assertEquals(sequential.getS3(), output.get(i).getS3());
            assertEquals(sequential.getS4(), output.get(i).getS4());
            assertEquals(sequential.getS5(), output.get(i).getS5());
            assertEquals(sequential.getS6(), output.get(i).getS6());
            assertEquals(sequential.getS7(), output.get(i).getS7());
        }
    }

    @Test
    public void testVectorAddInt16() throws TornadoExecutionPlanException {
        int size = 256;

        VectorInt16 a = new VectorInt16(size);
        VectorInt16 b = new VectorInt16(size);
        VectorInt16 output = new VectorInt16(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Int16(i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i));
            b.set(i, new Int16(size - i, size - i, size - i, size, size - i, size - i, size - i, size, size - i, size - i, size - i, size, size - i, size - i, size - i, size));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestInts::addVectorInt16, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Int16 sequential = new Int16(i + (size - i), i + (size - i), i + (size - i), i + size, i + (size - i), i + (size - i), i + (size - i), i + size, i + (size - i), i + (size - i),
                    i + (size - i), i + size, i + (size - i), i + (size - i), i + (size - i), i + size);

            assertEquals(sequential.getS0(), output.get(i).getS0());
            assertEquals(sequential.getS1(), output.get(i).getS1());
            assertEquals(sequential.getS2(), output.get(i).getS2());
            assertEquals(sequential.getS3(), output.get(i).getS3());
            assertEquals(sequential.getS4(), output.get(i).getS4());
            assertEquals(sequential.getS5(), output.get(i).getS5());
            assertEquals(sequential.getS6(), output.get(i).getS6());
            assertEquals(sequential.getS7(), output.get(i).getS7());
            assertEquals(sequential.getS8(), output.get(i).getS8());
            assertEquals(sequential.getS9(), output.get(i).getS9());
            assertEquals(sequential.getS10(), output.get(i).getS10());
            assertEquals(sequential.getS11(), output.get(i).getS11());
            assertEquals(sequential.getS12(), output.get(i).getS12());
            assertEquals(sequential.getS13(), output.get(i).getS13());
            assertEquals(sequential.getS14(), output.get(i).getS14());
            assertEquals(sequential.getS15(), output.get(i).getS15());
        }
    }

    @Test
    public void testVectorSubInt8() throws TornadoExecutionPlanException {
        int size = 256;

        VectorInt8 a = new VectorInt8(size);
        VectorInt8 b = new VectorInt8(size);
        VectorInt8 output = new VectorInt8(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Int8(i, i, i, i, i, i, i, i));
            b.set(i, new Int8(size - i, size - i, size - i, size, size - i, size - i, size - i, size));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestInts::subVectorInt8, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Int8 sequential = new Int8(i - (size - i), i - (size - i), i - (size - i), i - size, i - (size - i), i - (size - i), i - (size - i), i - size);
            assertEquals(sequential.getS0(), output.get(i).getS0());
            assertEquals(sequential.getS1(), output.get(i).getS1());
            assertEquals(sequential.getS2(), output.get(i).getS2());
            assertEquals(sequential.getS3(), output.get(i).getS3());
            assertEquals(sequential.getS4(), output.get(i).getS4());
            assertEquals(sequential.getS5(), output.get(i).getS5());
            assertEquals(sequential.getS6(), output.get(i).getS6());
            assertEquals(sequential.getS7(), output.get(i).getS7());
        }
    }

    @Test
    public void testVectorDivInt8() throws TornadoExecutionPlanException {
        int size = 256;

        VectorInt8 a = new VectorInt8(size);
        VectorInt8 b = new VectorInt8(size);
        VectorInt8 output = new VectorInt8(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Int8(i, i, i, i, i, i, i, i));
            b.set(i, new Int8(size - i, size - i, size - i, size, size - i, size - i, size - i, size));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestInts::divVectorInt8, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Int8 sequential = new Int8(i / (size - i), i / (size - i), i / (size - i), i / size, i / (size - i), i / (size - i), i / (size - i), i / size);
            assertEquals(sequential.getS0(), output.get(i).getS0());
            assertEquals(sequential.getS1(), output.get(i).getS1());
            assertEquals(sequential.getS2(), output.get(i).getS2());
            assertEquals(sequential.getS3(), output.get(i).getS3());
            assertEquals(sequential.getS4(), output.get(i).getS4());
            assertEquals(sequential.getS5(), output.get(i).getS5());
            assertEquals(sequential.getS6(), output.get(i).getS6());
            assertEquals(sequential.getS7(), output.get(i).getS7());
        }
    }

    @Test
    public void privateVectorInt2() throws TornadoExecutionPlanException {
        int size = 16;
        VectorInt2 sequentialOutput = new VectorInt2(size);
        VectorInt2 tornadoOutput = new VectorInt2(size);

        TaskGraph taskGraph = new TaskGraph("s0");
        taskGraph.task("t0", TestInts::testPrivateVectorInt2, tornadoOutput);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoOutput);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testPrivateVectorInt2(sequentialOutput);

        for (int i = 0; i < size; i++) {
            assertEquals(sequentialOutput.get(i).getX(), tornadoOutput.get(i).getX());
            assertEquals(sequentialOutput.get(i).getY(), tornadoOutput.get(i).getY());
        }
    }

    @Test
    public void privateVectorInt4() throws TornadoExecutionPlanException {
        int size = 16;
        VectorInt4 sequentialOutput = new VectorInt4(size);
        VectorInt4 tornadoOutput = new VectorInt4(size);

        TaskGraph taskGraph = new TaskGraph("s0");
        taskGraph.task("t0", TestInts::testPrivateVectorInt4, tornadoOutput);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoOutput);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testPrivateVectorInt4(sequentialOutput);

        for (int i = 0; i < size; i++) {
            assertEquals(sequentialOutput.get(i).getX(), tornadoOutput.get(i).getX());
            assertEquals(sequentialOutput.get(i).getY(), tornadoOutput.get(i).getY());
            assertEquals(sequentialOutput.get(i).getZ(), tornadoOutput.get(i).getZ());
            assertEquals(sequentialOutput.get(i).getW(), tornadoOutput.get(i).getW());
        }
    }

    @Test
    public void privateVectorInt8() throws TornadoExecutionPlanException {
        int size = 16;
        VectorInt8 sequentialOutput = new VectorInt8(16);
        VectorInt8 tornadoOutput = new VectorInt8(16);

        TaskGraph taskGraph = new TaskGraph("s0");
        taskGraph.task("t0", TestInts::testPrivateVectorInt8, tornadoOutput);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoOutput);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testPrivateVectorInt8(sequentialOutput);

        for (int i = 0; i < size; i++) {
            assertEquals(sequentialOutput.get(i).getS0(), tornadoOutput.get(i).getS0());
            assertEquals(sequentialOutput.get(i).getS1(), tornadoOutput.get(i).getS1());
            assertEquals(sequentialOutput.get(i).getS2(), tornadoOutput.get(i).getS2());
            assertEquals(sequentialOutput.get(i).getS3(), tornadoOutput.get(i).getS3());
            assertEquals(sequentialOutput.get(i).getS4(), tornadoOutput.get(i).getS4());
            assertEquals(sequentialOutput.get(i).getS5(), tornadoOutput.get(i).getS5());
            assertEquals(sequentialOutput.get(i).getS6(), tornadoOutput.get(i).getS6());
            assertEquals(sequentialOutput.get(i).getS7(), tornadoOutput.get(i).getS7());
        }
    }

    @Test
    public void testInternalSetMethod01() throws TornadoExecutionPlanException {
        final int size = 16;
        VectorInt2 tornadoInput = new VectorInt2(size);
        VectorInt2 sequentialInput = new VectorInt2(size);
        VectorInt2 tornadoOutput = new VectorInt2(size);
        VectorInt2 sequentialOutput = new VectorInt2(size);

        Random r = new Random();
        for (int i = 0; i < size; i++) {
            int value = r.nextInt();
            Int2 int2 = new Int2(value, value);
            tornadoInput.set(i, int2);
            sequentialInput.set(i, int2);
        }

        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tornadoInput) //
                .task("t0", TestInts::vectorComputation01, tornadoInput, tornadoOutput) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoOutput);

        ImmutableTaskGraph immutableTaskGraph = graph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        vectorComputation01(sequentialInput, sequentialOutput);

        for (int i = 0; i < size; i++) {
            assertEquals(sequentialOutput.get(i).getX(), tornadoOutput.get(i).getX());
            assertEquals(sequentialOutput.get(i).getY(), tornadoOutput.get(i).getY());
        }
    }

    @Test
    public void testInternalSetMethod02() throws TornadoExecutionPlanException {
        final int size = 16;
        VectorInt3 tornadoInput = new VectorInt3(size);
        VectorInt3 sequentialInput = new VectorInt3(size);
        VectorInt3 tornadoOutput = new VectorInt3(size);
        VectorInt3 sequentialOutput = new VectorInt3(size);

        Random r = new Random();
        for (int i = 0; i < size; i++) {
            int value = r.nextInt();
            Int3 int3 = new Int3(value, value, value);
            tornadoInput.set(i, int3);
            sequentialInput.set(i, int3);
        }

        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tornadoInput) //
                .task("t0", TestInts::vectorComputation02, tornadoInput, tornadoOutput) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoOutput);

        ImmutableTaskGraph immutableTaskGraph = graph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        vectorComputation02(sequentialInput, sequentialOutput);

        for (int i = 0; i < size; i++) {
            assertEquals(sequentialOutput.get(i).getX(), tornadoOutput.get(i).getX());
            assertEquals(sequentialOutput.get(i).getY(), tornadoOutput.get(i).getY());
            assertEquals(sequentialOutput.get(i).getZ(), tornadoOutput.get(i).getZ());
        }
    }

    @Test
    public void testInternalSetMethod03() throws TornadoExecutionPlanException {
        final int size = 16;
        VectorInt4 tornadoInput = new VectorInt4(size);
        VectorInt4 sequentialInput = new VectorInt4(size);
        VectorInt3 tornadoOutput = new VectorInt3(size);
        VectorInt3 sequentialOutput = new VectorInt3(size);

        Random r = new Random();
        for (int i = 0; i < size; i++) {
            int value = r.nextInt();
            Int4 int4 = new Int4(value, value, value, value);
            tornadoInput.set(i, int4);
            sequentialInput.set(i, int4);
        }

        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tornadoInput) //
                .task("t0", TestInts::vectorComputation03, tornadoInput, tornadoOutput) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoOutput);

        ImmutableTaskGraph immutableTaskGraph = graph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        vectorComputation03(sequentialInput, sequentialOutput);

        for (int i = 0; i < size; i++) {
            assertEquals(sequentialOutput.get(i).getX(), tornadoOutput.get(i).getX());
            assertEquals(sequentialOutput.get(i).getY(), tornadoOutput.get(i).getY());
            assertEquals(sequentialOutput.get(i).getZ(), tornadoOutput.get(i).getZ());
        }
    }

    @Test
    public void testInternalSetMethod04() throws TornadoExecutionPlanException {
        final int size = 16;
        VectorInt8 tornadoInput = new VectorInt8(size);
        VectorInt8 sequentialInput = new VectorInt8(size);
        VectorInt2 tornadoOutput = new VectorInt2(size);
        VectorInt2 sequentialOutput = new VectorInt2(size);

        Random r = new Random();
        for (int i = 0; i < size; i++) {
            int value = r.nextInt();
            Int8 int8 = new Int8(value, value, value, value, value, value, value, value);
            tornadoInput.set(i, int8);
            sequentialInput.set(i, int8);
        }

        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tornadoInput) //
                .task("t0", TestInts::vectorComputation04, tornadoInput, tornadoOutput) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoOutput);

        ImmutableTaskGraph immutableTaskGraph = graph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        vectorComputation04(sequentialInput, sequentialOutput);

        for (int i = 0; i < size; i++) {
            assertEquals(sequentialOutput.get(i).getX(), tornadoOutput.get(i).getX());
            assertEquals(sequentialOutput.get(i).getY(), tornadoOutput.get(i).getY());
        }
    }

}
