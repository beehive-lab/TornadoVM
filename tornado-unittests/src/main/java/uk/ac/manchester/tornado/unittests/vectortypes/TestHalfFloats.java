/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.collections.VectorHalf;
import uk.ac.manchester.tornado.api.types.collections.VectorHalf16;
import uk.ac.manchester.tornado.api.types.collections.VectorHalf2;
import uk.ac.manchester.tornado.api.types.collections.VectorHalf3;
import uk.ac.manchester.tornado.api.types.collections.VectorHalf4;
import uk.ac.manchester.tornado.api.types.collections.VectorHalf8;
import uk.ac.manchester.tornado.api.types.vectors.Half16;
import uk.ac.manchester.tornado.api.types.vectors.Half2;
import uk.ac.manchester.tornado.api.types.vectors.Half3;
import uk.ac.manchester.tornado.api.types.vectors.Half4;
import uk.ac.manchester.tornado.api.types.vectors.Half8;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run.
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.vectortypes.TestHalfFloats
 * </code>
 */
public class TestHalfFloats extends TornadoTestBase {

    public static final double DELTA = 0.001;

    public static void testVectorDot(VectorHalf a, VectorHalf b, VectorHalf c) {
        HalfFloat h = VectorHalf.dot(a, b);
        c.set(0, h);
    }

    private static void dotMethodHalf2(Half2 a, Half2 b, VectorHalf result) {
        HalfFloat dot = Half2.dot(a, b);
        result.set(0, dot);
    }

    private static void dotMethodHalf3(Half3 a, Half3 b, VectorHalf result) {
        HalfFloat dot = Half3.dot(a, b);
        result.set(0, dot);
    }

    private static void dotMethodHalf4(Half4 a, Half4 b, VectorHalf result) {
        HalfFloat dot = Half4.dot(a, b);
        result.set(0, dot);
    }

    private static void dotMethodHalf8(Half8 a, Half8 b, VectorHalf result) {
        HalfFloat dot = Half8.dot(a, b);
        result.set(0, dot);
    }

    private static void dotMethodHalf16(Half16 a, Half16 b, VectorHalf result) {
        HalfFloat dot = Half16.dot(a, b);
        result.set(0, dot);
    }

    private static void testFloat3Add(Half3 a, Half3 b, VectorHalf3 results) {
        results.set(0, Half3.add(a, b));
    }

    public static void addVectorHalf2(VectorHalf2 a, VectorHalf2 b, VectorHalf2 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Half2.add(a.get(i), b.get(i)));
        }
    }

    public static void addVectorHalf3(VectorHalf3 a, VectorHalf3 b, VectorHalf3 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Half3.add(a.get(i), b.get(i)));
        }
    }

    public static void addVectorHalf4(VectorHalf4 a, VectorHalf4 b, VectorHalf4 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Half4.add(a.get(i), b.get(i)));
        }
    }

    public static void addVectorHalf8(VectorHalf8 a, VectorHalf8 b, VectorHalf8 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Half8.add(a.get(i), b.get(i)));
        }
    }

    public static void addVectorHalf16(VectorHalf16 a, VectorHalf16 b, VectorHalf16 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Half16.add(a.get(i), b.get(i)));
        }
    }

    public static void testVectorHalf8Storage(VectorHalf8 a, VectorHalf8 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, a.get(i));
        }
    }

    public static void dotProductFunctionMap(HalfFloatArray a, HalfFloatArray b, HalfFloatArray results) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            results.set(i, HalfFloat.mult(a.get(i), b.get(i)));
        }
    }

    public static void dotProductFunctionReduce(HalfFloatArray input, HalfFloatArray results) {
        HalfFloat sum = new HalfFloat(0.0f);
        for (int i = 0; i < input.getSize(); i++) {
            sum = HalfFloat.add(sum, input.get(i));
        }
        results.set(0, sum);
    }

    private static void vectorPhiTest(VectorHalf3 input, VectorHalf3 output) {
        Half3 sum = new Half3();
        for (int i = 0; i < input.getLength(); i++) {
            sum = Half3.add(sum, input.get(i));
        }
        output.set(0, sum);
    }

    public static void testPrivateVectorHalf2(VectorHalf2 output) {
        VectorHalf2 vectorHalf2 = new VectorHalf2(output.getLength());

        for (int i = 0; i < vectorHalf2.getLength(); i++) {
            vectorHalf2.set(i, new Half2(new HalfFloat(i), new HalfFloat(i)));
        }

        Half2 sum = new Half2(new HalfFloat(0), new HalfFloat(0));

        for (int i = 0; i < vectorHalf2.getLength(); i++) {
            Half2 f = vectorHalf2.get(i);
            sum = Half2.add(f, sum);
        }

        output.set(0, sum);
    }

    public static void testPrivateVectorHalf4(VectorHalf4 output) {
        VectorHalf4 vectorHalf4 = new VectorHalf4(output.getLength());

        for (int i = 0; i < vectorHalf4.getLength(); i++) {
            vectorHalf4.set(i, new Half4(new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i)));
        }

        Half4 sum = new Half4(new HalfFloat(0), new HalfFloat(0), new HalfFloat(0), new HalfFloat(0));

        for (int i = 0; i < vectorHalf4.getLength(); i++) {
            Half4 f = vectorHalf4.get(i);
            sum = Half4.add(f, sum);
        }

        output.set(0, sum);
    }

    public static void testPrivateVectorHalf8(VectorHalf8 output) {
        VectorHalf8 vectorHalf8 = new VectorHalf8(output.getLength());

        for (int i = 0; i < vectorHalf8.getLength(); i++) {
            vectorHalf8.set(i, new Half8(new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i)));
        }

        Half8 sum = new Half8(new HalfFloat(0), new HalfFloat(0), new HalfFloat(0), new HalfFloat(0), new HalfFloat(0), new HalfFloat(0), new HalfFloat(0), new HalfFloat(0));

        for (int i = 0; i < vectorHalf8.getLength(); i++) {
            Half8 f = vectorHalf8.get(i);
            sum = Half8.add(f, sum);
        }

        output.set(0, sum);
    }

    public static void vectorHalfUnary(VectorHalf4 vector) {
        for (int i = 0; i < vector.getLength(); i++) {
            Half4 f4 = vector.get(i);
            HalfFloat a = f4.getX();
            vector.set(i, new Half4(a, f4.getY(), f4.getZ(), f4.getW()));
        }
    }

    private static void vectorComputation01(VectorHalf2 value, VectorHalf2 output) {
        for (@Parallel int i = 0; i < output.getLength(); i++) {
            Half2 half2 = new Half2();
            half2.setX(HalfFloat.add(value.get(i).getX(), value.get(i).getY()));
            half2.setY(HalfFloat.mult(value.get(i).getX(), new HalfFloat(2.0f)));
            output.set(i, half2);
        }
    }

    private static void vectorComputation02(VectorHalf3 value, VectorHalf3 output) {
        for (@Parallel int i = 0; i < output.getLength(); i++) {
            Half3 half3 = new Half3();
            half3.setX(HalfFloat.add(value.get(i).getX(), value.get(i).getY()));
            half3.setY(HalfFloat.mult(value.get(i).getZ(), new HalfFloat(2.0f)));
            output.set(i, half3);
        }
    }

    private static Half3 vectorComputation03(final Half4 value) {
        Half3 output = new Half3();
        output.setX(HalfFloat.add(value.getX(), value.getY()));
        output.setY(value.getY());
        output.setZ(value.getW());
        return output;
    }

    private static void vectorComputation03(VectorHalf4 value, VectorHalf3 output) {
        for (@Parallel int i = 0; i < output.getLength(); i++) {
            output.set(i, vectorComputation03(value.get(i)));
        }
    }

    private static void vectorComputation04(VectorHalf8 value, VectorHalf2 output) {
        for (@Parallel int i = 0; i < output.getLength(); i++) {
            Half2 half2 = new Half2();
            half2.setX(HalfFloat.add(value.get(i).getS0(), value.get(i).getS1()));
            half2.setY(value.get(i).getS1());
            output.set(i, half2);
        }
    }

    @Test
    public void testSimpleDotProductVectorHalf() throws TornadoExecutionPlanException {
        VectorHalf vectorHalfA = new VectorHalf(2);
        vectorHalfA.fill(new HalfFloat(2.0F));
        VectorHalf vectorHalfB = new VectorHalf(2);
        vectorHalfB.fill(new HalfFloat(4.0F));
        VectorHalf vectorHalfC = new VectorHalf(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, vectorHalfA, vectorHalfB).task("t0", TestHalfFloats::testVectorDot, vectorHalfA, vectorHalfB, vectorHalfC).transferToHost(
                        DataTransferMode.EVERY_EXECUTION, vectorHalfC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertEquals(16.0, vectorHalfC.get(0).getFloat32(), DELTA);
    }

    @Test
    public void testSimpleDotProductHalf2() throws TornadoExecutionPlanException {
        Half2 a = new Half2(new HalfFloat(1f), new HalfFloat(2f));
        Half2 b = new Half2(new HalfFloat(3f), new HalfFloat(2f));
        VectorHalf output = new VectorHalf(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHalfFloats::dotMethodHalf2, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertEquals(7, output.get(0).getFloat32(), DELTA);
    }

    @Test
    public void testSimpleDotProductHalf3() throws TornadoExecutionPlanException {
        Half3 a = new Half3(new HalfFloat(1f), new HalfFloat(2f), new HalfFloat(3f));
        Half3 b = new Half3(new HalfFloat(3f), new HalfFloat(2f), new HalfFloat(1f));
        VectorHalf output = new VectorHalf(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHalfFloats::dotMethodHalf3, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertEquals(10, output.get(0).getFloat32(), DELTA);
    }

    @Test
    public void testSimpleDotProductHalf4() throws TornadoExecutionPlanException {
        Half4 a = new Half4(new HalfFloat(1f), new HalfFloat(2f), new HalfFloat(3f), new HalfFloat(4f));
        Half4 b = new Half4(new HalfFloat(4f), new HalfFloat(3f), new HalfFloat(2f), new HalfFloat(1f));
        VectorHalf output = new VectorHalf(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHalfFloats::dotMethodHalf4, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertEquals(20, output.get(0).getFloat32(), DELTA);
    }

    @Test
    public void testSimpleDotProductHalf8() throws TornadoExecutionPlanException {
        Half8 a = new Half8(new HalfFloat(1f), new HalfFloat(2f), new HalfFloat(3f), new HalfFloat(4f), new HalfFloat(5f), new HalfFloat(6f), new HalfFloat(7f), new HalfFloat(8f));
        Half8 b = new Half8(new HalfFloat(8f), new HalfFloat(7f), new HalfFloat(6f), new HalfFloat(5f), new HalfFloat(4f), new HalfFloat(3f), new HalfFloat(2f), new HalfFloat(1f));
        VectorHalf output = new VectorHalf(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHalfFloats::dotMethodHalf8, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertEquals(120, output.get(0).getFloat32(), DELTA);
    }

    @Test
    public void testSimpleDotProductHalf16() throws TornadoExecutionPlanException {
        Half16 a = new Half16(new HalfFloat(1f), new HalfFloat(2f), new HalfFloat(3f), new HalfFloat(4f), new HalfFloat(5f), new HalfFloat(6f), new HalfFloat(7f), new HalfFloat(8f), new HalfFloat(0f),
                new HalfFloat(0f), new HalfFloat(0f), new HalfFloat(0f), new HalfFloat(0f), new HalfFloat(0f), new HalfFloat(0f), new HalfFloat(0f));
        Half16 b = new Half16(new HalfFloat(8f), new HalfFloat(7f), new HalfFloat(6f), new HalfFloat(5f), new HalfFloat(4f), new HalfFloat(3f), new HalfFloat(2f), new HalfFloat(1f), new HalfFloat(0f),
                new HalfFloat(0f), new HalfFloat(0f), new HalfFloat(0f), new HalfFloat(0f), new HalfFloat(0f), new HalfFloat(0f), new HalfFloat(0f));
        VectorHalf output = new VectorHalf(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHalfFloats::dotMethodHalf16, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertEquals(120, output.get(0).getFloat32(), DELTA);
    }

    @Test
    public void testSimpleVectorAddition() throws TornadoExecutionPlanException {
        int size = 1;
        Half3 a = new Half3(new HalfFloat(1f), new HalfFloat(2f), new HalfFloat(3f));
        Half3 b = new Half3(new HalfFloat(3f), new HalfFloat(2f), new HalfFloat(1f));
        VectorHalf3 output = new VectorHalf3(size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHalfFloats::testFloat3Add, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(4, output.get(i).getX().getFloat32(), DELTA);
            assertEquals(4, output.get(i).getY().getFloat32(), DELTA);
            assertEquals(4, output.get(i).getZ().getFloat32(), DELTA);
        }
    }

    @Test
    public void testVectorHalf2() throws TornadoExecutionPlanException {
        int size = 16;

        VectorHalf2 a = new VectorHalf2(size);
        VectorHalf2 b = new VectorHalf2(size);
        VectorHalf2 output = new VectorHalf2(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Half2(new HalfFloat(i), new HalfFloat(i)));
            b.set(i, new Half2(new HalfFloat(size - i), new HalfFloat(size - i)));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHalfFloats::addVectorHalf2, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Half2 sequential = new Half2(new HalfFloat(i + (size - i)), new HalfFloat(i + (size - i)));
            assertEquals(sequential.getX().getFloat32(), output.get(i).getX().getFloat32(), DELTA);
            assertEquals(sequential.getY().getFloat32(), output.get(i).getY().getFloat32(), DELTA);
        }
    }

    @Test
    public void testVectorHalf3() throws TornadoExecutionPlanException {
        int size = 8;

        VectorHalf3 a = new VectorHalf3(size);
        VectorHalf3 b = new VectorHalf3(size);
        VectorHalf3 output = new VectorHalf3(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Half3(new HalfFloat(i), new HalfFloat(i), new HalfFloat(i)));
            b.set(i, new Half3(new HalfFloat(size - i), new HalfFloat(size - i), new HalfFloat(size - i)));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHalfFloats::addVectorHalf3, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Half3 sequential = new Half3(new HalfFloat(i + (size - i)), new HalfFloat(i + (size - i)), new HalfFloat(i + (size - i)));
            assertEquals(sequential.getX().getFloat32(), output.get(i).getX().getFloat32(), DELTA);
            assertEquals(sequential.getY().getFloat32(), output.get(i).getY().getFloat32(), DELTA);
            assertEquals(sequential.getZ().getFloat32(), output.get(i).getZ().getFloat32(), DELTA);
        }
    }

    @Test
    public void testVectorFloat3toString() throws TornadoExecutionPlanException {
        int size = 2;

        VectorHalf3 a = new VectorHalf3(size);
        VectorHalf3 b = new VectorHalf3(size);
        VectorHalf3 output = new VectorHalf3(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Half3(new HalfFloat(i), new HalfFloat(i), new HalfFloat(i)));
            b.set(i, new Half3(new HalfFloat((float) size - i), new HalfFloat((float) size - i), new HalfFloat((float) size - i)));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHalfFloats::addVectorHalf3, a, b, output)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
    }

    @Test
    public void testVectorHalf4() throws TornadoExecutionPlanException {

        int size = 8;

        VectorHalf4 a = new VectorHalf4(size);
        VectorHalf4 b = new VectorHalf4(size);
        VectorHalf4 output = new VectorHalf4(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Half4(new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i)));
            b.set(i, new Half4(new HalfFloat((float) size - i), new HalfFloat((float) size - i), new HalfFloat((float) size - i), new HalfFloat((float) size)));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHalfFloats::addVectorHalf4, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Half4 sequential = new Half4(new HalfFloat(i + (size - i)), new HalfFloat(i + (size - i)), new HalfFloat(i + (size - i)), new HalfFloat(i + size));
            assertEquals(sequential.getX().getFloat32(), output.get(i).getX().getFloat32(), DELTA);
            assertEquals(sequential.getY().getFloat32(), output.get(i).getY().getFloat32(), DELTA);
            assertEquals(sequential.getZ().getFloat32(), output.get(i).getZ().getFloat32(), DELTA);
            assertEquals(sequential.getW().getFloat32(), output.get(i).getW().getFloat32(), DELTA);
        }
    }

    @Test
    public void testVectorHalf16() throws TornadoExecutionPlanException {
        int size = 16;

        VectorHalf16 a = new VectorHalf16(size);
        VectorHalf16 b = new VectorHalf16(size);
        VectorHalf16 output = new VectorHalf16(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Half16(new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i),
                    new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i)));
            b.set(i, new Half16(new HalfFloat((float) size - i), new HalfFloat((float) size - i), new HalfFloat((float) size - i), new HalfFloat((float) size), new HalfFloat((float) size - i),
                    new HalfFloat((float) size - i), new HalfFloat((float) size - i), new HalfFloat((float) size), new HalfFloat((float) size - i), new HalfFloat((float) size - i), new HalfFloat(
                            (float) size - i), new HalfFloat((float) size), new HalfFloat((float) size - i), new HalfFloat((float) size - i), new HalfFloat((float) size - i), new HalfFloat(
                                    (float) size)));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHalfFloats::addVectorHalf16, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Half16 sequential = new Half16(new HalfFloat(i + (size - i)), new HalfFloat(i + (size - i)), new HalfFloat(i + (size - i)), new HalfFloat(i + size), new HalfFloat(i + (size - i)),
                    new HalfFloat(i + (size - i)), new HalfFloat(i + (size - i)), new HalfFloat(i + size), new HalfFloat(i + (size - i)), new HalfFloat(i + (size - i)), new HalfFloat(i + (size - i)),
                    new HalfFloat(i + size), new HalfFloat(i + (size - i)), new HalfFloat(i + (size - i)), new HalfFloat(i + (size - i)), new HalfFloat(i + size));
            assertEquals(sequential.getS0().getFloat32(), output.get(i).getS0().getFloat32(), DELTA);
            assertEquals(sequential.getS1().getFloat32(), output.get(i).getS1().getFloat32(), DELTA);
            assertEquals(sequential.getS2().getFloat32(), output.get(i).getS2().getFloat32(), DELTA);
            assertEquals(sequential.getS3().getFloat32(), output.get(i).getS3().getFloat32(), DELTA);
            assertEquals(sequential.getS4().getFloat32(), output.get(i).getS4().getFloat32(), DELTA);
            assertEquals(sequential.getS5().getFloat32(), output.get(i).getS5().getFloat32(), DELTA);
            assertEquals(sequential.getS6().getFloat32(), output.get(i).getS6().getFloat32(), DELTA);
            assertEquals(sequential.getS7().getFloat32(), output.get(i).getS7().getFloat32(), DELTA);
            assertEquals(sequential.getS8().getFloat32(), output.get(i).getS8().getFloat32(), DELTA);
            assertEquals(sequential.getS9().getFloat32(), output.get(i).getS9().getFloat32(), DELTA);
            assertEquals(sequential.getS10().getFloat32(), output.get(i).getS10().getFloat32(), DELTA);
            assertEquals(sequential.getS11().getFloat32(), output.get(i).getS11().getFloat32(), DELTA);
            assertEquals(sequential.getS12().getFloat32(), output.get(i).getS12().getFloat32(), DELTA);
            assertEquals(sequential.getS13().getFloat32(), output.get(i).getS13().getFloat32(), DELTA);
            assertEquals(sequential.getS14().getFloat32(), output.get(i).getS14().getFloat32(), DELTA);
            assertEquals(sequential.getS15().getFloat32(), output.get(i).getS15().getFloat32(), DELTA);
        }
    }

    @Test
    public void testVectorHalf8() throws TornadoExecutionPlanException {
        int size = 8;

        VectorHalf8 a = new VectorHalf8(size);
        VectorHalf8 b = new VectorHalf8(size);
        VectorHalf8 output = new VectorHalf8(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Half8(new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i)));
            b.set(i, new Half8(new HalfFloat((float) size - i), new HalfFloat((float) size - i), new HalfFloat((float) size - i), new HalfFloat((float) size), new HalfFloat((float) size - i),
                    new HalfFloat((float) size - i), new HalfFloat((float) size - i), new HalfFloat((float) size)));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHalfFloats::addVectorHalf8, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Half8 sequential = new Half8(new HalfFloat(i + (size - i)), new HalfFloat(i + (size - i)), new HalfFloat(i + (size - i)), new HalfFloat(i + size), new HalfFloat(i + (size - i)),
                    new HalfFloat(i + (size - i)), new HalfFloat(i + (size - i)), new HalfFloat(i + size));
            assertEquals(sequential.getS0().getFloat32(), output.get(i).getS0().getFloat32(), DELTA);
            assertEquals(sequential.getS1().getFloat32(), output.get(i).getS1().getFloat32(), DELTA);
            assertEquals(sequential.getS2().getFloat32(), output.get(i).getS2().getFloat32(), DELTA);
            assertEquals(sequential.getS3().getFloat32(), output.get(i).getS3().getFloat32(), DELTA);
            assertEquals(sequential.getS4().getFloat32(), output.get(i).getS4().getFloat32(), DELTA);
            assertEquals(sequential.getS5().getFloat32(), output.get(i).getS5().getFloat32(), DELTA);
            assertEquals(sequential.getS6().getFloat32(), output.get(i).getS6().getFloat32(), DELTA);
            assertEquals(sequential.getS7().getFloat32(), output.get(i).getS7().getFloat32(), DELTA);
        }
    }

    @Test
    public void testVectorHalf8_Storage() throws TornadoExecutionPlanException {
        int size = 8;

        VectorHalf8 a = new VectorHalf8(size);
        VectorHalf8 output = new VectorHalf8(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Half8(new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i)));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestHalfFloats::testVectorHalf8Storage, a, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Half8 sequential = new Half8(new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i), new HalfFloat(i));
            assertEquals(sequential.getS0().getFloat32(), output.get(i).getS0().getFloat32(), DELTA);
            assertEquals(sequential.getS1().getFloat32(), output.get(i).getS1().getFloat32(), DELTA);
            assertEquals(sequential.getS2().getFloat32(), output.get(i).getS2().getFloat32(), DELTA);
            assertEquals(sequential.getS3().getFloat32(), output.get(i).getS3().getFloat32(), DELTA);
            assertEquals(sequential.getS4().getFloat32(), output.get(i).getS4().getFloat32(), DELTA);
            assertEquals(sequential.getS5().getFloat32(), output.get(i).getS5().getFloat32(), DELTA);
            assertEquals(sequential.getS6().getFloat32(), output.get(i).getS6().getFloat32(), DELTA);
            assertEquals(sequential.getS7().getFloat32(), output.get(i).getS7().getFloat32(), DELTA);
        }
    }

    @Test
    public void testDotProduct() throws TornadoExecutionPlanException {

        int size = 8;

        HalfFloatArray a = new HalfFloatArray(size);
        HalfFloatArray b = new HalfFloatArray(size);
        HalfFloatArray outputMap = new HalfFloatArray(size);
        HalfFloatArray outputReduce = new HalfFloatArray(1);

        HalfFloatArray seqMap = new HalfFloatArray(size);
        HalfFloatArray seqReduce = new HalfFloatArray(1);

        Random r = new Random();
        for (int i = 0; i < size; i++) {
            a.set(i, new HalfFloat(r.nextFloat()));
            b.set(i, new HalfFloat(r.nextFloat()));
        }

        // Sequential computation
        dotProductFunctionMap(a, b, seqMap);
        dotProductFunctionReduce(seqMap, seqReduce);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b, outputMap) //
                .task("t0-MAP", TestHalfFloats::dotProductFunctionMap, a, b, outputMap) //
                .task("t1-REDUCE", TestHalfFloats::dotProductFunctionReduce, outputMap, outputReduce) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputReduce);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertEquals(seqReduce.get(0).getFloat32(), outputReduce.get(0).getFloat32(), DELTA);
    }

    @Test
    public void vectorPhiTest() throws TornadoExecutionPlanException {

        final VectorHalf3 input = new VectorHalf3(8);
        final VectorHalf3 output = new VectorHalf3(1);

        input.fill(new HalfFloat(1f));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestHalfFloats::vectorPhiTest, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertEquals(8.0f, output.get(0).getS0().getFloat32(), DELTA);
        assertEquals(8.0f, output.get(0).getS1().getFloat32(), DELTA);
        assertEquals(8.0f, output.get(0).getS2().getFloat32(), DELTA);

    }

    @Test
    public void privateVectorHalf2() throws TornadoExecutionPlanException {
        int size = 16;
        VectorHalf2 sequentialOutput = new VectorHalf2(size);
        VectorHalf2 tornadoOutput = new VectorHalf2(size);

        TaskGraph taskGraph = new TaskGraph("s0");
        taskGraph.task("t0", TestHalfFloats::testPrivateVectorHalf2, tornadoOutput);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoOutput);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testPrivateVectorHalf2(sequentialOutput);

        for (int i = 0; i < size; i++) {
            assertEquals(sequentialOutput.get(i).getX().getFloat32(), tornadoOutput.get(i).getX().getFloat32(), DELTA);
            assertEquals(sequentialOutput.get(i).getY().getFloat32(), tornadoOutput.get(i).getY().getFloat32(), DELTA);
        }
    }

    @Test
    public void privateVectorHalf4() throws TornadoExecutionPlanException {
        int size = 16;
        VectorHalf4 sequentialOutput = new VectorHalf4(size);
        VectorHalf4 tornadoOutput = new VectorHalf4(size);

        TaskGraph taskGraph = new TaskGraph("s0");
        taskGraph.task("t0", TestHalfFloats::testPrivateVectorHalf4, tornadoOutput);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoOutput);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testPrivateVectorHalf4(sequentialOutput);

        for (int i = 0; i < size; i++) {
            assertEquals(sequentialOutput.get(i).getX().getFloat32(), tornadoOutput.get(i).getX().getFloat32(), DELTA);
            assertEquals(sequentialOutput.get(i).getY().getFloat32(), tornadoOutput.get(i).getY().getFloat32(), DELTA);
            assertEquals(sequentialOutput.get(i).getZ().getFloat32(), tornadoOutput.get(i).getZ().getFloat32(), DELTA);
            assertEquals(sequentialOutput.get(i).getW().getFloat32(), tornadoOutput.get(i).getW().getFloat32(), DELTA);
        }
    }

    @Test
    public void privateVectorHalf8() throws TornadoExecutionPlanException {
        int size = 16;
        VectorHalf8 sequentialOutput = new VectorHalf8(16);
        VectorHalf8 tornadoOutput = new VectorHalf8(16);

        TaskGraph taskGraph = new TaskGraph("s0");
        taskGraph.task("t0", TestHalfFloats::testPrivateVectorHalf8, tornadoOutput);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoOutput);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testPrivateVectorHalf8(sequentialOutput);

        for (int i = 0; i < size; i++) {
            assertEquals(sequentialOutput.get(i).getS0().getFloat32(), tornadoOutput.get(i).getS0().getFloat32(), DELTA);
            assertEquals(sequentialOutput.get(i).getS1().getFloat32(), tornadoOutput.get(i).getS1().getFloat32(), DELTA);
            assertEquals(sequentialOutput.get(i).getS2().getFloat32(), tornadoOutput.get(i).getS2().getFloat32(), DELTA);
            assertEquals(sequentialOutput.get(i).getS3().getFloat32(), tornadoOutput.get(i).getS3().getFloat32(), DELTA);
            assertEquals(sequentialOutput.get(i).getS4().getFloat32(), tornadoOutput.get(i).getS4().getFloat32(), DELTA);
            assertEquals(sequentialOutput.get(i).getS5().getFloat32(), tornadoOutput.get(i).getS5().getFloat32(), DELTA);
            assertEquals(sequentialOutput.get(i).getS6().getFloat32(), tornadoOutput.get(i).getS6().getFloat32(), DELTA);
            assertEquals(sequentialOutput.get(i).getS7().getFloat32(), tornadoOutput.get(i).getS7().getFloat32(), DELTA);
        }
    }

    @Test
    public void testVectorHalf4_Unary() throws TornadoExecutionPlanException {
        int size = 256;
        VectorHalf4 sequentialOutput = new VectorHalf4(size);
        VectorHalf4 output = new VectorHalf4(size);

        TaskGraph taskGraph = new TaskGraph("s0");
        taskGraph.task("t0", TestHalfFloats::vectorHalfUnary, output);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        vectorHalfUnary(sequentialOutput);

        for (int i = 0; i < size; i++) {
            assertEquals(sequentialOutput.get(i).getX().getFloat32(), output.get(i).getX().getFloat32(), DELTA);
            assertEquals(sequentialOutput.get(i).getY().getFloat32(), output.get(i).getY().getFloat32(), DELTA);
            assertEquals(sequentialOutput.get(i).getZ().getFloat32(), output.get(i).getZ().getFloat32(), DELTA);
            assertEquals(sequentialOutput.get(i).getW().getFloat32(), output.get(i).getW().getFloat32(), DELTA);
        }
    }

    @Test
    public void testInternalSetMethod01() throws TornadoExecutionPlanException {
        final int size = 16;
        VectorHalf2 tornadoInput = new VectorHalf2(size);
        VectorHalf2 sequentialInput = new VectorHalf2(size);
        VectorHalf2 tornadoOutput = new VectorHalf2(size);
        VectorHalf2 sequentialOutput = new VectorHalf2(size);

        for (int i = 0; i < size; i++) {
            float value = (float) Math.random();
            Half2 half2 = new Half2(new HalfFloat(value), new HalfFloat(value));
            tornadoInput.set(i, half2);
            sequentialInput.set(i, half2);
        }

        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tornadoInput) //
                .task("t0", TestHalfFloats::vectorComputation01, tornadoInput, tornadoOutput) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoOutput);

        ImmutableTaskGraph immutableTaskGraph = graph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        vectorComputation01(sequentialInput, sequentialOutput);

        for (int i = 0; i < size; i++) {
            assertEquals(sequentialOutput.get(i).getX().getFloat32(), tornadoOutput.get(i).getX().getFloat32(), DELTA);
            assertEquals(sequentialOutput.get(i).getY().getFloat32(), tornadoOutput.get(i).getY().getFloat32(), DELTA);
        }
    }

    @Test
    public void testInternalSetMethod02() throws TornadoExecutionPlanException {
        final int size = 16;
        VectorHalf3 tornadoInput = new VectorHalf3(size);
        VectorHalf3 sequentialInput = new VectorHalf3(size);
        VectorHalf3 tornadoOutput = new VectorHalf3(size);
        VectorHalf3 sequentialOutput = new VectorHalf3(size);

        for (int i = 0; i < size; i++) {
            float value = (float) Math.random();
            Half3 float3 = new Half3(new HalfFloat(value), new HalfFloat(value), new HalfFloat(value));
            tornadoInput.set(i, float3);
            sequentialInput.set(i, float3);
        }

        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tornadoInput) //
                .task("t0", TestHalfFloats::vectorComputation02, tornadoInput, tornadoOutput) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoOutput);

        ImmutableTaskGraph immutableTaskGraph = graph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        vectorComputation02(sequentialInput, sequentialOutput);

        for (int i = 0; i < size; i++) {
            assertEquals(sequentialOutput.get(i).getX().getFloat32(), tornadoOutput.get(i).getX().getFloat32(), DELTA);
            assertEquals(sequentialOutput.get(i).getY().getFloat32(), tornadoOutput.get(i).getY().getFloat32(), DELTA);
            assertEquals(sequentialOutput.get(i).getZ().getFloat32(), tornadoOutput.get(i).getZ().getFloat32(), DELTA);
        }
    }

    @Test
    public void testInternalSetMethod03() throws TornadoExecutionPlanException {
        final int size = 16;
        VectorHalf4 tornadoInput = new VectorHalf4(size);
        VectorHalf4 sequentialInput = new VectorHalf4(size);
        VectorHalf3 tornadoOutput = new VectorHalf3(size);
        VectorHalf3 sequentialOutput = new VectorHalf3(size);

        for (int i = 0; i < size; i++) {
            float value = (float) Math.random();
            Half4 half4 = new Half4(new HalfFloat(value), new HalfFloat(value), new HalfFloat(value), new HalfFloat(value));
            tornadoInput.set(i, half4);
            sequentialInput.set(i, half4);
        }

        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tornadoInput) //
                .task("t0", TestHalfFloats::vectorComputation03, tornadoInput, tornadoOutput) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoOutput);

        ImmutableTaskGraph immutableTaskGraph = graph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        vectorComputation03(sequentialInput, sequentialOutput);

        for (int i = 0; i < size; i++) {
            assertEquals(sequentialOutput.get(i).getX().getFloat32(), tornadoOutput.get(i).getX().getFloat32(), DELTA);
            assertEquals(sequentialOutput.get(i).getY().getFloat32(), tornadoOutput.get(i).getY().getFloat32(), DELTA);
            assertEquals(sequentialOutput.get(i).getZ().getFloat32(), tornadoOutput.get(i).getZ().getFloat32(), DELTA);
        }
    }

    @Test
    public void testInternalSetMethod04() throws TornadoExecutionPlanException {
        final int size = 16;
        VectorHalf8 tornadoInput = new VectorHalf8(size);
        VectorHalf8 sequentialInput = new VectorHalf8(size);
        VectorHalf2 tornadoOutput = new VectorHalf2(size);
        VectorHalf2 sequentialOutput = new VectorHalf2(size);

        for (int i = 0; i < size; i++) {
            float value = (float) Math.random();
            Half8 half8 = new Half8(new HalfFloat(value), new HalfFloat(value), new HalfFloat(value), new HalfFloat(value), new HalfFloat(value), new HalfFloat(value), new HalfFloat(value),
                    new HalfFloat(value));
            tornadoInput.set(i, half8);
            sequentialInput.set(i, half8);
        }

        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tornadoInput) //
                .task("t0", TestHalfFloats::vectorComputation04, tornadoInput, tornadoOutput) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoOutput);

        ImmutableTaskGraph immutableTaskGraph = graph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        vectorComputation04(sequentialInput, sequentialOutput);

        for (int i = 0; i < size; i++) {
            assertEquals(sequentialOutput.get(i).getX().getFloat32(), tornadoOutput.get(i).getX().getFloat32(), DELTA);
            assertEquals(sequentialOutput.get(i).getY().getFloat32(), tornadoOutput.get(i).getY().getFloat32(), DELTA);
        }
    }

    @Test(timeout = 1000) //timeout of 1sec
    public void testAllocationIssue() {
        int size = 8192 * 4096;

        VectorHalf4 buffer = new VectorHalf4(size);
        for (int x = 0; x < size; x++) {
            buffer.set(x, new Half4(new HalfFloat(x), new HalfFloat(x), new HalfFloat(x), new HalfFloat(x)));
        }
    }

}
