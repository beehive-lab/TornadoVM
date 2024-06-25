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
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat16;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat2;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat3;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat4;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat8;
import uk.ac.manchester.tornado.api.types.collections.VectorInt16;
import uk.ac.manchester.tornado.api.types.collections.VectorInt2;
import uk.ac.manchester.tornado.api.types.collections.VectorInt4;
import uk.ac.manchester.tornado.api.types.collections.VectorInt8;
import uk.ac.manchester.tornado.api.types.vectors.Float16;
import uk.ac.manchester.tornado.api.types.vectors.Float2;
import uk.ac.manchester.tornado.api.types.vectors.Float3;
import uk.ac.manchester.tornado.api.types.vectors.Float4;
import uk.ac.manchester.tornado.api.types.vectors.Float8;
import uk.ac.manchester.tornado.api.types.vectors.Int16;
import uk.ac.manchester.tornado.api.types.vectors.Int2;
import uk.ac.manchester.tornado.api.types.vectors.Int4;
import uk.ac.manchester.tornado.api.types.vectors.Int8;
import uk.ac.manchester.tornado.unittests.common.TornadoNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run.
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.vectortypes.TestFloats
 * </code>
 */
public class TestFloats extends TornadoTestBase {

    public static final double DELTA = 0.001;

    private static void dotMethodFloat2(Float2 a, Float2 b, VectorFloat result) {
        float dot = Float2.dot(a, b);
        result.set(0, dot);
    }

    private static void dotMethodFloat3(Float3 a, Float3 b, VectorFloat result) {
        float dot = Float3.dot(a, b);
        result.set(0, dot);
    }

    private static void dotMethodFloat4(Float4 a, Float4 b, VectorFloat result) {
        float dot = Float4.dot(a, b);
        result.set(0, dot);
    }

    private static void dotMethodFloat8(Float8 a, Float8 b, VectorFloat result) {
        float dot = Float8.dot(a, b);
        result.set(0, dot);
    }

    private static void dotMethodFloat16(Float16 a, Float16 b, VectorFloat result) {
        float dot = Float16.dot(a, b);
        result.set(0, dot);
    }

    private static void testFloat3Add(Float3 a, Float3 b, VectorFloat3 results) {
        results.set(0, Float3.add(a, b));
    }

    /**
     * Test using the {@link Float} Java Wrapper class.
     *
     * @param a
     * @param b
     * @param result
     */
    private static void addFloat(Float[] a, Float[] b, Float[] result) {
        for (@Parallel int i = 0; i < a.length; i++) {
            result[i] = a[i] + b[i];
        }
    }

    /**
     * Test using the {@link Float2} Tornado wrapper class.
     *
     * @param a
     * @param b
     * @param result
     */
    private static void addFloat2(Float2[] a, Float2[] b, Float2[] result) {
        for (@Parallel int i = 0; i < a.length; i++) {
            result[i] = Float2.add(a[i], b[i]);
        }
    }

    /**
     * Test using Tornado {@link VectorFloat3} data type.
     *
     * @param a
     * @param b
     * @param results
     */
    public static void addVectorFloat2(VectorFloat2 a, VectorFloat2 b, VectorFloat2 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Float2.add(a.get(i), b.get(i)));
        }
    }

    /**
     * Test using Tornado {@link VectorFloat3} data type.
     *
     * @param a
     * @param b
     * @param results
     */
    public static void addVectorFloat3(VectorFloat3 a, VectorFloat3 b, VectorFloat3 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Float3.add(a.get(i), b.get(i)));
        }
    }

    /**
     * Test using Tornado {@link VectorFloat4} data type.
     *
     * @param a
     * @param b
     * @param results
     */
    public static void addVectorFloat4(VectorFloat4 a, VectorFloat4 b, VectorFloat4 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Float4.add(a.get(i), b.get(i)));
        }
    }

    public static void addVectorFloat8(VectorFloat8 a, VectorFloat8 b, VectorFloat8 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Float8.add(a.get(i), b.get(i)));
        }
    }

    public static void addVectorFloat16(VectorFloat16 a, VectorFloat16 b, VectorFloat16 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Float16.add(a.get(i), b.get(i)));
        }
    }

    public static void testVectorFloat8Storage(VectorFloat8 a, VectorFloat8 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, a.get(i));
        }
    }

    public static void dotProductFunctionMap(FloatArray a, FloatArray b, FloatArray results) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            results.set(i, a.get(i) * b.get(i));
        }
    }

    public static void dotProductFunctionReduce(FloatArray input, FloatArray results) {
        float sum = 0.0f;
        for (int i = 0; i < input.getSize(); i++) {
            sum += input.get(i);
        }
        results.set(0, sum);
    }

    private static void vectorPhiTest(VectorFloat3 input, VectorFloat3 output) {
        Float3 sum = new Float3();
        for (int i = 0; i < input.getLength(); i++) {
            sum = Float3.add(sum, input.get(i));
        }
        output.set(0, sum);
    }

    public static void testPrivateVectorFloat2(VectorFloat2 output) {
        VectorFloat2 vectorFloat2 = new VectorFloat2(output.getLength());

        for (int i = 0; i < vectorFloat2.getLength(); i++) {
            vectorFloat2.set(i, new Float2(i, i));
        }

        Float2 sum = new Float2(0, 0);

        for (int i = 0; i < vectorFloat2.getLength(); i++) {
            Float2 f = vectorFloat2.get(i);
            sum = Float2.add(f, sum);
        }

        output.set(0, sum);
    }

    public static void testPrivateVectorFloat4(VectorFloat4 output) {
        VectorFloat4 vectorFloat4 = new VectorFloat4(output.getLength());

        for (int i = 0; i < vectorFloat4.getLength(); i++) {
            vectorFloat4.set(i, new Float4(i, i, i, i));
        }

        Float4 sum = new Float4(0, 0, 0, 0);

        for (int i = 0; i < vectorFloat4.getLength(); i++) {
            Float4 f = vectorFloat4.get(i);
            sum = Float4.add(f, sum);
        }

        output.set(0, sum);
    }

    public static void testPrivateVectorFloat8(VectorFloat8 output) {
        VectorFloat8 vectorFloat8 = new VectorFloat8(output.getLength());

        for (int i = 0; i < vectorFloat8.getLength(); i++) {
            vectorFloat8.set(i, new Float8(i, i, i, i, i, i, i, i));
        }

        Float8 sum = new Float8(0, 0, 0, 0, 0, 0, 0, 0);

        for (int i = 0; i < vectorFloat8.getLength(); i++) {
            Float8 f = vectorFloat8.get(i);
            sum = Float8.add(f, sum);
        }

        output.set(0, sum);
    }

    public static void vectorFloatUnary(VectorFloat4 vector) {
        for (int i = 0; i < vector.getLength(); i++) {
            Float4 f4 = vector.get(i);
            float a = -f4.getX();
            vector.set(i, new Float4(a, f4.getY(), f4.getZ(), f4.getW()));
        }
    }

    private static void vectorComputation01(VectorFloat2 value, VectorFloat2 output) {
        for (@Parallel int i = 0; i < output.getLength(); i++) {
            Float2 float2 = new Float2();
            float2.setX(value.get(i).getX() + value.get(i).getY());
            float2.setY(value.get(i).getX() * 2);
            output.set(i, float2);
        }
    }

    private static void vectorComputation02(VectorFloat3 value, VectorFloat3 output) {
        for (@Parallel int i = 0; i < output.getLength(); i++) {
            Float3 float3 = new Float3();
            float3.setX(value.get(i).getX() + value.get(i).getY());
            float3.setY(value.get(i).getZ() * 2);
            output.set(i, float3);
        }
    }

    private static Float3 vectorComputation03(final Float4 value) {
        Float3 output = new Float3();
        output.setX(value.getX() + value.getY());
        output.setY(value.getY());
        output.setZ(value.getW());
        return output;
    }

    private static void vectorComputation03(VectorFloat4 value, VectorFloat3 output) {
        for (@Parallel int i = 0; i < output.getLength(); i++) {
            output.set(i, vectorComputation03(value.get(i)));
        }
    }

    private static void vectorComputation04(VectorFloat8 value, VectorFloat2 output) {
        for (@Parallel int i = 0; i < output.getLength(); i++) {
            Float2 float2 = new Float2();
            float2.setX(value.get(i).getS0() + value.get(i).getS1());
            float2.setY(value.get(i).getS1());
            output.set(i, float2);
        }
    }

    private static void vectorComputationMixedTypes(VectorFloat2 a, VectorInt2 b, VectorFloat2 output) {
        for (@Parallel int i = 0; i < output.getLength(); i++) {
            Float2 result = Float2.mult(a.get(i), b.get(i));
            output.set(i, result);
        }
    }

    private static void vectorComputationMixedTypes(VectorFloat4 a, VectorInt4 b, VectorFloat4 output) {
        for (@Parallel int i = 0; i < output.getLength(); i++) {
            Float4 result = Float4.mult(a.get(i), b.get(i));
            output.set(i, result);
        }
    }

    private static void vectorComputationMixedTypes(VectorFloat8 a, VectorInt8 b, VectorFloat8 output) {
        for (@Parallel int i = 0; i < output.getLength(); i++) {
            Float8 result = Float8.mult(a.get(i), b.get(i));
            output.set(i, result);
        }
    }

    private static void vectorComputationMixedTypes(VectorFloat16 a, VectorInt16 b, VectorFloat16 output) {
        for (@Parallel int i = 0; i < output.getLength(); i++) {
            Float16 result = Float16.mult(a.get(i), b.get(i));
            output.set(i, result);
        }
    }

    private static void vectorComputationMixedTypes(VectorFloat4 a, VectorFloat4 b, VectorFloat4 output) {
        for (@Parallel int i = 0; i < output.getLength(); i++) {
            Float4 result = Float4.mult(a.get(i), b.get(i));
            output.set(i, result);
        }
    }

    @Test
    public void testSimpleDotProductFloat2() throws TornadoExecutionPlanException {
        Float2 a = new Float2(1f, 2f);
        Float2 b = new Float2(3f, 2f);
        VectorFloat output = new VectorFloat(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestFloats::dotMethodFloat2, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertEquals(7, output.get(0), DELTA);
    }

    @Test
    public void testSimpleDotProductFloat3() throws TornadoExecutionPlanException {
        Float3 a = new Float3(1f, 2f, 3f);
        Float3 b = new Float3(3f, 2f, 1f);
        VectorFloat output = new VectorFloat(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestFloats::dotMethodFloat3, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertEquals(10, output.get(0), DELTA);
    }

    @Test
    public void testSimpleDotProductFloat4() throws TornadoExecutionPlanException {
        Float4 a = new Float4(1f, 2f, 3f, 4f);
        Float4 b = new Float4(4f, 3f, 2f, 1f);
        VectorFloat output = new VectorFloat(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestFloats::dotMethodFloat4, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertEquals(20, output.get(0), DELTA);
    }

    @Test
    public void testSimpleDotProductFloat8() throws TornadoExecutionPlanException {
        Float8 a = new Float8(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f);
        Float8 b = new Float8(8f, 7f, 6f, 5f, 4f, 3f, 2f, 1f);
        VectorFloat output = new VectorFloat(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestFloats::dotMethodFloat8, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertEquals(120, output.get(0), DELTA);
    }

    @Test
    public void testSimpleDotProductFloat16() throws TornadoExecutionPlanException {
        Float16 a = new Float16(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        Float16 b = new Float16(8f, 7f, 6f, 5f, 4f, 3f, 2f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        VectorFloat output = new VectorFloat(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestFloats::dotMethodFloat16, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertEquals(120, output.get(0), DELTA);
    }

    @Test
    public void testSimpleVectorAddition() throws TornadoExecutionPlanException {
        int size = 1;
        Float3 a = new Float3(1f, 2f, 3f);
        Float3 b = new Float3(3f, 2f, 1f);
        VectorFloat3 output = new VectorFloat3(size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestFloats::testFloat3Add, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(4, output.get(i).getX(), DELTA);
            assertEquals(4, output.get(i).getY(), DELTA);
            assertEquals(4, output.get(i).getZ(), DELTA);
        }
    }

    @TornadoNotSupported
    public void testFloat1() throws TornadoExecutionPlanException {
        int size = 8;

        Float[] a = new Float[size];
        Float[] b = new Float[size];
        Float[] output = new Float[size];

        for (int i = 0; i < size; i++) {
            a[i] = (float) i;
            b[i] = (float) i;
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestFloats::addFloat, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(i + i, output[i], DELTA);
        }
    }

    @TornadoNotSupported
    public void testFloat2() throws TornadoExecutionPlanException {
        int size = 8;

        Float2[] a = new Float2[size];
        Float2[] b = new Float2[size];
        Float2[] output = new Float2[size];

        for (int i = 0; i < size; i++) {
            a[i] = new Float2(i, i);
            b[i] = new Float2(i, i);
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestFloats::addFloat2, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Float2 sequential = new Float2(i + i, i + i);
            assertEquals(sequential.getX(), output[i].getX(), DELTA);
            assertEquals(sequential.getY(), output[i].getY(), DELTA);
        }
    }

    @Test
    public void testVectorFloat2() throws TornadoExecutionPlanException {
        int size = 16;

        VectorFloat2 a = new VectorFloat2(size);
        VectorFloat2 b = new VectorFloat2(size);
        VectorFloat2 output = new VectorFloat2(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Float2(i, i));
            b.set(i, new Float2(size - i, size - i));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestFloats::addVectorFloat2, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Float2 sequential = new Float2(i + (size - i), i + (size - i));
            assertEquals(sequential.getX(), output.get(i).getX(), DELTA);
            assertEquals(sequential.getY(), output.get(i).getY(), DELTA);
        }
    }

    @Test
    public void testVectorFloat3() throws TornadoExecutionPlanException {
        int size = 8;

        VectorFloat3 a = new VectorFloat3(size);
        VectorFloat3 b = new VectorFloat3(size);
        VectorFloat3 output = new VectorFloat3(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Float3(i, i, i));
            b.set(i, new Float3(size - i, size - i, size - i));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestFloats::addVectorFloat3, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Float3 sequential = new Float3(i + (size - i), i + (size - i), i + (size - i));
            assertEquals(sequential.getX(), output.get(i).getX(), DELTA);
            assertEquals(sequential.getY(), output.get(i).getY(), DELTA);
            assertEquals(sequential.getZ(), output.get(i).getZ(), DELTA);
        }
    }

    @Test
    public void testVectorFloat3toString() throws TornadoExecutionPlanException {
        int size = 2;

        VectorFloat3 a = new VectorFloat3(size);
        VectorFloat3 b = new VectorFloat3(size);
        VectorFloat3 output = new VectorFloat3(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Float3(i, i, i));
            b.set(i, new Float3((float) size - i, (float) size - i, (float) size - i));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestFloats::addVectorFloat3, a, b, output)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
    }

    @Test
    public void testVectorFloat4() throws TornadoExecutionPlanException {

        int size = 8;

        VectorFloat4 a = new VectorFloat4(size);
        VectorFloat4 b = new VectorFloat4(size);
        VectorFloat4 output = new VectorFloat4(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Float4(i, i, i, i));
            b.set(i, new Float4(size - i, size - i, size - i, size));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestFloats::addVectorFloat4, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Float4 sequential = new Float4(i + (size - i), i + (size - i), i + (size - i), i + size);
            assertEquals(sequential.getX(), output.get(i).getX(), DELTA);
            assertEquals(sequential.getY(), output.get(i).getY(), DELTA);
            assertEquals(sequential.getZ(), output.get(i).getZ(), DELTA);
            assertEquals(sequential.getW(), output.get(i).getW(), DELTA);
        }
    }

    @Test
    public void testVectorFloat16() throws TornadoExecutionPlanException {
        int size = 16;

        VectorFloat16 a = new VectorFloat16(size);
        VectorFloat16 b = new VectorFloat16(size);
        VectorFloat16 output = new VectorFloat16(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Float16(i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i));
            b.set(i, new Float16(size - i, size - i, size - i, size, size - i, size - i, size - i, size, size - i, size - i, size - i, size, size - i, size - i, size - i, size));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestFloats::addVectorFloat16, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Float16 sequential = new Float16(i + (size - i), i + (size - i), i + (size - i), i + size, i + (size - i), i + (size - i), i + (size - i), i + size, i + (size - i), i + (size - i),
                    i + (size - i), i + size, i + (size - i), i + (size - i), i + (size - i), i + size);
            assertEquals(sequential.getS0(), output.get(i).getS0(), DELTA);
            assertEquals(sequential.getS1(), output.get(i).getS1(), DELTA);
            assertEquals(sequential.getS2(), output.get(i).getS2(), DELTA);
            assertEquals(sequential.getS3(), output.get(i).getS3(), DELTA);
            assertEquals(sequential.getS4(), output.get(i).getS4(), DELTA);
            assertEquals(sequential.getS5(), output.get(i).getS5(), DELTA);
            assertEquals(sequential.getS6(), output.get(i).getS6(), DELTA);
            assertEquals(sequential.getS7(), output.get(i).getS7(), DELTA);
            assertEquals(sequential.getS8(), output.get(i).getS8(), DELTA);
            assertEquals(sequential.getS9(), output.get(i).getS9(), DELTA);
            assertEquals(sequential.getS10(), output.get(i).getS10(), DELTA);
            assertEquals(sequential.getS11(), output.get(i).getS11(), DELTA);
            assertEquals(sequential.getS12(), output.get(i).getS12(), DELTA);
            assertEquals(sequential.getS13(), output.get(i).getS13(), DELTA);
            assertEquals(sequential.getS14(), output.get(i).getS14(), DELTA);
            assertEquals(sequential.getS15(), output.get(i).getS15(), DELTA);
        }
    }

    @Test
    public void testVectorFloat8() throws TornadoExecutionPlanException {
        int size = 8;

        VectorFloat8 a = new VectorFloat8(size);
        VectorFloat8 b = new VectorFloat8(size);
        VectorFloat8 output = new VectorFloat8(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Float8(i, i, i, i, i, i, i, i));
            b.set(i, new Float8(size - i, size - i, size - i, size, size - i, size - i, size - i, size));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestFloats::addVectorFloat8, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Float8 sequential = new Float8(i + (size - i), i + (size - i), i + (size - i), i + size, i + (size - i), i + (size - i), i + (size - i), i + size);
            assertEquals(sequential.getS0(), output.get(i).getS0(), DELTA);
            assertEquals(sequential.getS1(), output.get(i).getS1(), DELTA);
            assertEquals(sequential.getS2(), output.get(i).getS2(), DELTA);
            assertEquals(sequential.getS3(), output.get(i).getS3(), DELTA);
            assertEquals(sequential.getS4(), output.get(i).getS4(), DELTA);
            assertEquals(sequential.getS5(), output.get(i).getS5(), DELTA);
            assertEquals(sequential.getS6(), output.get(i).getS6(), DELTA);
            assertEquals(sequential.getS7(), output.get(i).getS7(), DELTA);
        }
    }

    @Test
    public void testVectorFloat8_Storage() throws TornadoExecutionPlanException {
        int size = 8;

        VectorFloat8 a = new VectorFloat8(size);
        VectorFloat8 output = new VectorFloat8(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Float8(i, i, i, i, i, i, i, i));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestFloats::testVectorFloat8Storage, a, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Float8 sequential = new Float8(i, i, i, i, i, i, i, i);
            assertEquals(sequential.getS0(), output.get(i).getS0(), DELTA);
            assertEquals(sequential.getS1(), output.get(i).getS1(), DELTA);
            assertEquals(sequential.getS2(), output.get(i).getS2(), DELTA);
            assertEquals(sequential.getS3(), output.get(i).getS3(), DELTA);
            assertEquals(sequential.getS4(), output.get(i).getS4(), DELTA);
            assertEquals(sequential.getS5(), output.get(i).getS5(), DELTA);
            assertEquals(sequential.getS6(), output.get(i).getS6(), DELTA);
            assertEquals(sequential.getS7(), output.get(i).getS7(), DELTA);
        }
    }

    @Test
    public void testDotProduct() throws TornadoExecutionPlanException {

        int size = 8;

        FloatArray a = new FloatArray(size);
        FloatArray b = new FloatArray(size);
        FloatArray outputMap = new FloatArray(size);
        FloatArray outputReduce = new FloatArray(1);

        FloatArray seqMap = new FloatArray(size);
        FloatArray seqReduce = new FloatArray(1);

        Random r = new Random();
        for (int i = 0; i < size; i++) {
            a.set(i, r.nextFloat());
            b.set(i, r.nextFloat());
        }

        // Sequential computation
        dotProductFunctionMap(a, b, seqMap);
        dotProductFunctionReduce(seqMap, seqReduce);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b, outputMap) //
                .task("t0-MAP", TestFloats::dotProductFunctionMap, a, b, outputMap) //
                .task("t1-REDUCE", TestFloats::dotProductFunctionReduce, outputMap, outputReduce) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputReduce);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertEquals(seqReduce.get(0), outputReduce.get(0), DELTA);
    }

    @Test
    public void vectorPhiTest() throws TornadoExecutionPlanException {

        final VectorFloat3 input = new VectorFloat3(8);
        final VectorFloat3 output = new VectorFloat3(1);

        input.fill(1f);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestFloats::vectorPhiTest, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertEquals(8.0f, output.get(0).getS0(), DELTA);
        assertEquals(8.0f, output.get(0).getS1(), DELTA);
        assertEquals(8.0f, output.get(0).getS2(), DELTA);

    }

    @Test
    public void privateVectorFloat2() throws TornadoExecutionPlanException {
        int size = 16;
        VectorFloat2 sequentialOutput = new VectorFloat2(size);
        VectorFloat2 tornadoOutput = new VectorFloat2(size);

        TaskGraph taskGraph = new TaskGraph("s0");
        taskGraph.task("t0", TestFloats::testPrivateVectorFloat2, tornadoOutput);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoOutput);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testPrivateVectorFloat2(sequentialOutput);

        for (int i = 0; i < size; i++) {
            assertEquals(sequentialOutput.get(i).getX(), tornadoOutput.get(i).getX(), DELTA);
            assertEquals(sequentialOutput.get(i).getY(), tornadoOutput.get(i).getY(), DELTA);
        }
    }

    @Test
    public void privateVectorFloat4() throws TornadoExecutionPlanException {
        int size = 16;
        VectorFloat4 sequentialOutput = new VectorFloat4(size);
        VectorFloat4 tornadoOutput = new VectorFloat4(size);

        TaskGraph taskGraph = new TaskGraph("s0");
        taskGraph.task("t0", TestFloats::testPrivateVectorFloat4, tornadoOutput);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoOutput);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testPrivateVectorFloat4(sequentialOutput);

        for (int i = 0; i < size; i++) {
            assertEquals(sequentialOutput.get(i).getX(), tornadoOutput.get(i).getX(), DELTA);
            assertEquals(sequentialOutput.get(i).getY(), tornadoOutput.get(i).getY(), DELTA);
            assertEquals(sequentialOutput.get(i).getZ(), tornadoOutput.get(i).getZ(), DELTA);
            assertEquals(sequentialOutput.get(i).getW(), tornadoOutput.get(i).getW(), DELTA);
        }
    }

    @Test
    public void privateVectorFloat8() throws TornadoExecutionPlanException {
        int size = 16;
        VectorFloat8 sequentialOutput = new VectorFloat8(16);
        VectorFloat8 tornadoOutput = new VectorFloat8(16);

        TaskGraph taskGraph = new TaskGraph("s0");
        taskGraph.task("t0", TestFloats::testPrivateVectorFloat8, tornadoOutput);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoOutput);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testPrivateVectorFloat8(sequentialOutput);

        for (int i = 0; i < size; i++) {
            assertEquals(sequentialOutput.get(i).getS0(), tornadoOutput.get(i).getS0(), DELTA);
            assertEquals(sequentialOutput.get(i).getS1(), tornadoOutput.get(i).getS1(), DELTA);
            assertEquals(sequentialOutput.get(i).getS2(), tornadoOutput.get(i).getS2(), DELTA);
            assertEquals(sequentialOutput.get(i).getS3(), tornadoOutput.get(i).getS3(), DELTA);
            assertEquals(sequentialOutput.get(i).getS4(), tornadoOutput.get(i).getS4(), DELTA);
            assertEquals(sequentialOutput.get(i).getS5(), tornadoOutput.get(i).getS5(), DELTA);
            assertEquals(sequentialOutput.get(i).getS6(), tornadoOutput.get(i).getS6(), DELTA);
            assertEquals(sequentialOutput.get(i).getS7(), tornadoOutput.get(i).getS7(), DELTA);
        }
    }

    @Test
    public void testVectorFloat4_Unary() throws TornadoExecutionPlanException {
        int size = 256;
        VectorFloat4 sequentialOutput = new VectorFloat4(size);
        VectorFloat4 output = new VectorFloat4(size);

        TaskGraph taskGraph = new TaskGraph("s0");
        taskGraph.task("t0", TestFloats::vectorFloatUnary, output);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        vectorFloatUnary(sequentialOutput);

        for (int i = 0; i < size; i++) {
            assertEquals(sequentialOutput.get(i).getX(), output.get(i).getX(), DELTA);
            assertEquals(sequentialOutput.get(i).getY(), output.get(i).getY(), DELTA);
            assertEquals(sequentialOutput.get(i).getZ(), output.get(i).getZ(), DELTA);
            assertEquals(sequentialOutput.get(i).getW(), output.get(i).getW(), DELTA);
        }
    }

    @Test
    public void testInternalSetMethod01() throws TornadoExecutionPlanException {
        final int size = 16;
        VectorFloat2 tornadoInput = new VectorFloat2(size);
        VectorFloat2 sequentialInput = new VectorFloat2(size);
        VectorFloat2 tornadoOutput = new VectorFloat2(size);
        VectorFloat2 sequentialOutput = new VectorFloat2(size);

        for (int i = 0; i < size; i++) {
            float value = (float) Math.random();
            Float2 float2 = new Float2(value, value);
            tornadoInput.set(i, float2);
            sequentialInput.set(i, float2);
        }

        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tornadoInput) //
                .task("t0", TestFloats::vectorComputation01, tornadoInput, tornadoOutput) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoOutput);

        ImmutableTaskGraph immutableTaskGraph = graph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        vectorComputation01(sequentialInput, sequentialOutput);

        for (int i = 0; i < size; i++) {
            assertEquals(sequentialOutput.get(i).getX(), tornadoOutput.get(i).getX(), DELTA);
            assertEquals(sequentialOutput.get(i).getY(), tornadoOutput.get(i).getY(), DELTA);
        }
    }

    @Test
    public void testInternalSetMethod02() throws TornadoExecutionPlanException {
        final int size = 16;
        VectorFloat3 tornadoInput = new VectorFloat3(size);
        VectorFloat3 sequentialInput = new VectorFloat3(size);
        VectorFloat3 tornadoOutput = new VectorFloat3(size);
        VectorFloat3 sequentialOutput = new VectorFloat3(size);

        for (int i = 0; i < size; i++) {
            float value = (float) Math.random();
            Float3 float3 = new Float3(value, value, value);
            tornadoInput.set(i, float3);
            sequentialInput.set(i, float3);
        }

        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tornadoInput) //
                .task("t0", TestFloats::vectorComputation02, tornadoInput, tornadoOutput) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoOutput);

        ImmutableTaskGraph immutableTaskGraph = graph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        vectorComputation02(sequentialInput, sequentialOutput);

        for (int i = 0; i < size; i++) {
            assertEquals(sequentialOutput.get(i).getX(), tornadoOutput.get(i).getX(), DELTA);
            assertEquals(sequentialOutput.get(i).getY(), tornadoOutput.get(i).getY(), DELTA);
            assertEquals(sequentialOutput.get(i).getZ(), tornadoOutput.get(i).getZ(), DELTA);
        }
    }

    @Test
    public void testInternalSetMethod03() throws TornadoExecutionPlanException {
        final int size = 16;
        VectorFloat4 tornadoInput = new VectorFloat4(size);
        VectorFloat4 sequentialInput = new VectorFloat4(size);
        VectorFloat3 tornadoOutput = new VectorFloat3(size);
        VectorFloat3 sequentialOutput = new VectorFloat3(size);

        for (int i = 0; i < size; i++) {
            float value = (float) Math.random();
            Float4 float4 = new Float4(value, value, value, value);
            tornadoInput.set(i, float4);
            sequentialInput.set(i, float4);
        }

        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tornadoInput) //
                .task("t0", TestFloats::vectorComputation03, tornadoInput, tornadoOutput) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoOutput);

        ImmutableTaskGraph immutableTaskGraph = graph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        vectorComputation03(sequentialInput, sequentialOutput);

        for (int i = 0; i < size; i++) {
            assertEquals(sequentialOutput.get(i).getX(), tornadoOutput.get(i).getX(), DELTA);
            assertEquals(sequentialOutput.get(i).getY(), tornadoOutput.get(i).getY(), DELTA);
            assertEquals(sequentialOutput.get(i).getZ(), tornadoOutput.get(i).getZ(), DELTA);
        }
    }

    @Test
    public void testInternalSetMethod04() throws TornadoExecutionPlanException {
        final int size = 16;
        VectorFloat8 tornadoInput = new VectorFloat8(size);
        VectorFloat8 sequentialInput = new VectorFloat8(size);
        VectorFloat2 tornadoOutput = new VectorFloat2(size);
        VectorFloat2 sequentialOutput = new VectorFloat2(size);

        for (int i = 0; i < size; i++) {
            float value = (float) Math.random();
            Float8 float8 = new Float8(value, value, value, value, value, value, value, value);
            tornadoInput.set(i, float8);
            sequentialInput.set(i, float8);
        }

        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tornadoInput) //
                .task("t0", TestFloats::vectorComputation04, tornadoInput, tornadoOutput) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoOutput);

        ImmutableTaskGraph immutableTaskGraph = graph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        vectorComputation04(sequentialInput, sequentialOutput);

        for (int i = 0; i < size; i++) {
            assertEquals(sequentialOutput.get(i).getX(), tornadoOutput.get(i).getX(), DELTA);
            assertEquals(sequentialOutput.get(i).getY(), tornadoOutput.get(i).getY(), DELTA);
        }
    }

    @Test(timeout = 1000) //timeout of 1sec
    public void testAllocationIssue() {
        int size = 8192 * 4096;

        VectorFloat4 buffer = new VectorFloat4(size);
        for (int x = 0; x < size; x++) {
            buffer.set(x, new Float4(x, x, x, x));
        }
    }

    @Test
    public void testVectorFloat4Float4() throws TornadoExecutionPlanException {
        int size = 8192;
        VectorFloat4 a = new VectorFloat4(size);
        VectorFloat4 b = new VectorFloat4(size);
        VectorFloat4 output = new VectorFloat4(size);
        VectorFloat4 seq = new VectorFloat4(size);

        for (int x = 0; x < b.getLength(); x++) {
            a.set(x, new Float4(x, x, x, x));
            b.set(x, new Float4(0, 1, 2, 3));
        }

        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestFloats::vectorComputationMixedTypes, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = graph.snapshot();

        vectorComputationMixedTypes(a, b, seq);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
            for (int i = 0; i < size; i++) {
                assertEquals(seq.get(i).getX(), output.get(i).getX(), DELTA);
                assertEquals(seq.get(i).getY(), output.get(i).getY(), DELTA);
                assertEquals(seq.get(i).getZ(), output.get(i).getZ(), DELTA);
                assertEquals(seq.get(i).getW(), output.get(i).getW(), DELTA);
            }
        }
    }

    @Test
    public void testVectorFloat2Int2() throws TornadoExecutionPlanException {
        int size = 8192;
        VectorFloat2 a = new VectorFloat2(size);
        VectorInt2 b = new VectorInt2(size);
        VectorFloat2 output = new VectorFloat2(size);
        VectorFloat2 seq = new VectorFloat2(size);

        Random r = new Random();
        for (int i = 0; i < b.getLength(); i++) {
            a.set(i, new Float2(r.nextFloat(), r.nextFloat()));
            b.set(i, new Int2(r.nextInt(), r.nextInt()));
        }

        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestFloats::vectorComputationMixedTypes, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = graph.snapshot();

        vectorComputationMixedTypes(a, b, seq);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
            for (int i = 0; i < size; i++) {
                assertEquals(seq.get(i).getX(), output.get(i).getX(), DELTA);
                assertEquals(seq.get(i).getY(), output.get(i).getY(), DELTA);
            }
        }
    }

    @Test
    public void testVectorFloat4Int4() throws TornadoExecutionPlanException {
        int size = 8192;
        VectorFloat4 a = new VectorFloat4(size);
        VectorInt4 b = new VectorInt4(size);
        VectorFloat4 output = new VectorFloat4(size);
        VectorFloat4 seq = new VectorFloat4(size);

        Random r = new Random();
        for (int i = 0; i < b.getLength(); i++) {
            a.set(i, new Float4(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()));
            b.set(i, new Int4(r.nextInt(), r.nextInt(), r.nextInt(), r.nextInt()));
        }

        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestFloats::vectorComputationMixedTypes, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = graph.snapshot();

        vectorComputationMixedTypes(a, b, seq);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
            for (int i = 0; i < size; i++) {
                assertEquals(seq.get(i).getX(), output.get(i).getX(), DELTA);
                assertEquals(seq.get(i).getY(), output.get(i).getY(), DELTA);
                assertEquals(seq.get(i).getZ(), output.get(i).getZ(), DELTA);
                assertEquals(seq.get(i).getW(), output.get(i).getW(), DELTA);
            }
        }
    }

    @Test
    public void testVectorFloat8Int8() throws TornadoExecutionPlanException {
        int size = 8192;
        VectorFloat8 a = new VectorFloat8(size);
        VectorInt8 b = new VectorInt8(size);
        VectorFloat8 output = new VectorFloat8(size);
        VectorFloat8 seq = new VectorFloat8(size);

        Random r = new Random();
        IntStream.range(0, b.getLength()).forEach(i -> {
            Float8 vectorFloat = new Float8();
            IntStream.range(0, vectorFloat.size()).forEach(j -> vectorFloat.set(j, r.nextFloat()));
            a.set(i, vectorFloat);
            Int8 vectorInt = new Int8();
            IntStream.range(0, vectorInt.size()).forEach(j -> vectorInt.set(j, r.nextInt()));
            b.set(i, vectorInt);
        });

        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestFloats::vectorComputationMixedTypes, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = graph.snapshot();

        vectorComputationMixedTypes(a, b, seq);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
            for (int i = 0; i < size; i++) {
                for (int k = 0; k < 8; k++) {
                    assertEquals(seq.get(i).get(k), output.get(i).get(k), DELTA);
                }
            }
        }
    }

    @Test
    public void testVectorFloat16Int16() throws TornadoExecutionPlanException {
        int size = 8192;
        VectorFloat16 a = new VectorFloat16(size);
        VectorInt16 b = new VectorInt16(size);
        VectorFloat16 output = new VectorFloat16(size);
        VectorFloat16 seq = new VectorFloat16(size);

        Random r = new Random();
        IntStream.range(0, b.getLength()).forEach(i -> {

            Float16 vectorFloat = new Float16();
            IntStream.range(0, vectorFloat.size()).forEach(j -> vectorFloat.set(j, r.nextFloat()));
            a.set(i, vectorFloat);

            Int16 vectorInt = new Int16();
            IntStream.range(0, vectorInt.size()).forEach(j -> vectorInt.set(j, r.nextInt()));
            b.set(i, vectorInt);
        });

        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestFloats::vectorComputationMixedTypes, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = graph.snapshot();

        vectorComputationMixedTypes(a, b, seq);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
            for (int i = 0; i < size; i++) {
                for (int k = 0; k < 16; k++) {
                    assertEquals(seq.get(i).get(k), output.get(i).get(k), DELTA);
                }
            }
        }
    }

}
