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
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.collections.VectorDouble;
import uk.ac.manchester.tornado.api.types.collections.VectorDouble16;
import uk.ac.manchester.tornado.api.types.collections.VectorDouble2;
import uk.ac.manchester.tornado.api.types.collections.VectorDouble3;
import uk.ac.manchester.tornado.api.types.collections.VectorDouble4;
import uk.ac.manchester.tornado.api.types.collections.VectorDouble8;
import uk.ac.manchester.tornado.api.types.vectors.Double16;
import uk.ac.manchester.tornado.api.types.vectors.Double2;
import uk.ac.manchester.tornado.api.types.vectors.Double3;
import uk.ac.manchester.tornado.api.types.vectors.Double4;
import uk.ac.manchester.tornado.api.types.vectors.Double8;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.vectortypes.TestDoubles
 * </code>
 */
public class TestDoubles extends TornadoTestBase {

    public static final double DELTA = 0.001;

    private static void addDouble2(Double2 a, Double2 b, VectorDouble results) {
        Double2 d2 = Double2.add(a, b);
        double r = d2.getX() + d2.getY();
        results.set(0, r);
    }

    private static void addDouble3(Double3 a, Double3 b, VectorDouble results) {
        Double3 d3 = Double3.add(a, b);
        double r = d3.getX() + d3.getY() + d3.getZ();
        results.set(0, r);
    }

    private static void addDouble4(Double4 a, Double4 b, VectorDouble results) {
        Double4 d4 = Double4.add(a, b);
        double r = d4.getX() + d4.getY() + d4.getZ() + d4.getW();
        results.set(0, r);
    }

    private static void addDouble8(Double8 a, Double8 b, VectorDouble results) {
        Double8 d8 = Double8.add(a, b);
        double r = d8.getS0() + d8.getS1() + d8.getS2() + d8.getS3() + d8.getS4() + d8.getS5() + d8.getS6() + d8.getS7();
        results.set(0, r);
    }

    private static void addDouble(DoubleArray a, DoubleArray b, DoubleArray result) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            result.set(i, a.get(i) + b.get(i));
        }
    }

    public static void dotProductFunctionMap(DoubleArray a, DoubleArray b, DoubleArray results) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            results.set(i, a.get(i) * b.get(i));
        }
    }

    public static void dotProductFunctionReduce(DoubleArray input, DoubleArray results) {
        double sum = 0.0f;
        for (int i = 0; i < input.getSize(); i++) {
            sum += input.get(i);
        }
        results.set(0, sum);
    }

    public static void addVectorDouble2(VectorDouble2 a, VectorDouble2 b, VectorDouble2 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Double2.add(a.get(i), b.get(i)));
        }
    }

    public static void addVectorDouble3(VectorDouble3 a, VectorDouble3 b, VectorDouble3 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Double3.add(a.get(i), b.get(i)));
        }
    }

    public static void addVectorDouble4(VectorDouble4 a, VectorDouble4 b, VectorDouble4 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Double4.add(a.get(i), b.get(i)));
        }
    }

    public static void addVectorDouble8(VectorDouble8 a, VectorDouble8 b, VectorDouble8 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Double8.add(a.get(i), b.get(i)));
        }
    }

    public static void addVectorDouble16(VectorDouble16 a, VectorDouble16 b, VectorDouble16 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Double16.add(a.get(i), b.get(i)));
        }
    }

    public static void testPrivateVectorDouble2(VectorDouble2 output) {
        VectorDouble2 vectorDouble2 = new VectorDouble2(output.getLength());

        for (int i = 0; i < vectorDouble2.getLength(); i++) {
            vectorDouble2.set(i, new Double2(i, i));
        }

        Double2 sum = new Double2(0, 0);

        for (int i = 0; i < vectorDouble2.getLength(); i++) {
            Double2 f = vectorDouble2.get(i);
            sum = Double2.add(f, sum);
        }

        output.set(0, sum);
    }

    public static void testPrivateVectorDouble4(VectorDouble4 output) {
        VectorDouble4 vectorDouble4 = new VectorDouble4(output.getLength());

        for (int i = 0; i < vectorDouble4.getLength(); i++) {
            vectorDouble4.set(i, new Double4(i, i, i, i));
        }

        Double4 sum = new Double4(0, 0, 0, 0);

        for (int i = 0; i < vectorDouble4.getLength(); i++) {
            Double4 f = vectorDouble4.get(i);
            sum = Double4.add(f, sum);
        }

        output.set(0, sum);
    }

    public static void testPrivateVectorDouble8(VectorDouble8 output) {
        VectorDouble8 vectorDouble8 = new VectorDouble8(output.getLength());

        for (int i = 0; i < vectorDouble8.getLength(); i++) {
            vectorDouble8.set(i, new Double8(i, i, i, i, i, i, i, i));
        }

        Double8 sum = new Double8(0, 0, 0, 0, 0, 0, 0, 0);

        for (int i = 0; i < vectorDouble8.getLength(); i++) {
            Double8 f = vectorDouble8.get(i);
            sum = Double8.add(f, sum);
        }

        output.set(0, sum);
    }

    private static void vectorComputation01(VectorDouble2 value, VectorDouble2 output) {
        for (@Parallel int i = 0; i < output.getLength(); i++) {
            Double2 double2 = new Double2();
            double2.setX(value.get(i).getX() + value.get(i).getY());
            double2.setY(value.get(i).getX() * 2);
            output.set(i, double2);
        }
    }

    private static void vectorComputation02(VectorDouble3 value, VectorDouble3 output) {
        for (@Parallel int i = 0; i < output.getLength(); i++) {
            Double3 double3 = new Double3();
            double3.setX(value.get(i).getX() + value.get(i).getY());
            double3.setY(value.get(i).getZ() * 2);
            output.set(i, double3);
        }
    }

    private static Double3 vectorComputation03(final Double4 value) {
        Double3 output = new Double3();
        output.setX(value.getX() + value.getY());
        output.setY(value.getY());
        output.setZ(value.getW());
        return output;
    }

    private static void vectorComputation03(VectorDouble4 value, VectorDouble3 output) {
        for (@Parallel int i = 0; i < output.getLength(); i++) {
            output.set(i, vectorComputation03(value.get(i)));
        }
    }

    private static void vectorComputation04(VectorDouble8 value, VectorDouble2 output) {
        for (@Parallel int i = 0; i < output.getLength(); i++) {
            Double2 double2 = new Double2();
            double2.setX(value.get(i).getS0() + value.get(i).getS1());
            double2.setY(value.get(i).getS1());
            output.set(i, double2);
        }
    }

    @Test
    public void testDoubleAdd2() throws TornadoExecutionPlanException {
        int size = 1;
        Double2 a = new Double2(1., 2.);
        Double2 b = new Double2(3., 2.);
        VectorDouble output = new VectorDouble(size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestDoubles::addDouble2, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(8.0, output.get(i), DELTA);
        }
    }

    @Test
    public void testDoubleAdd3() throws TornadoExecutionPlanException {
        int size = 1;
        Double3 a = new Double3(1., 2., 3.);
        Double3 b = new Double3(3., 2., 1.);
        VectorDouble output = new VectorDouble(size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestDoubles::addDouble3, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(12.0, output.get(i), DELTA);
        }
    }

    @Test
    public void testDoubleAdd4() throws TornadoExecutionPlanException {
        int size = 1;
        Double4 a = new Double4(1., 2., 3., 4.);
        Double4 b = new Double4(4., 3., 2., 1.);
        VectorDouble output = new VectorDouble(size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestDoubles::addDouble4, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(20.0, output.get(i), DELTA);
        }
    }

    @Test
    public void testDoubleAdd8() throws TornadoExecutionPlanException {
        int size = 1;
        Double8 a = new Double8(1., 2., 3., 4., 5., 6., 7., 8.);
        Double8 b = new Double8(8., 7., 6., 5., 4., 3., 2., 1.);
        VectorDouble output = new VectorDouble(size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestDoubles::addDouble8, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(72., output.get(i), DELTA);
        }
    }

    @Test
    public void testDoubleAdd() throws TornadoExecutionPlanException {
        int size = 8;

        DoubleArray a = new DoubleArray(size);
        DoubleArray b = new DoubleArray(size);
        DoubleArray output = new DoubleArray(size);

        for (int i = 0; i < size; i++) {
            a.set(i, i);
            b.set(i, i);
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestDoubles::addDouble, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(i + i, output.get(i), DELTA);
        }
    }

    @Test
    public void testDotProductDouble() throws TornadoExecutionPlanException {

        int size = 8;

        DoubleArray a = new DoubleArray(size);
        DoubleArray b = new DoubleArray(size);
        DoubleArray outputMap = new DoubleArray(size);
        DoubleArray outputReduce = new DoubleArray(1);

        DoubleArray seqMap = new DoubleArray(size);
        DoubleArray seqReduce = new DoubleArray(1);

        Random r = new Random();
        for (int i = 0; i < size; i++) {
            a.set(i, r.nextDouble());
            b.set(i, r.nextDouble());
        }

        dotProductFunctionMap(a, b, seqMap);
        dotProductFunctionReduce(seqMap, seqReduce);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b, outputMap) //
                .task("t0-MAP", TestDoubles::dotProductFunctionMap, a, b, outputMap) //
                .task("t1-REDUCE", TestDoubles::dotProductFunctionReduce, outputMap, outputReduce) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputReduce);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertEquals(seqReduce.get(0), outputReduce.get(0), DELTA);
    }

    @Test
    public void testVectorDouble2() throws TornadoExecutionPlanException {
        int size = 256;

        VectorDouble2 a = new VectorDouble2(size);
        VectorDouble2 b = new VectorDouble2(size);
        VectorDouble2 output = new VectorDouble2(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Double2(i, i));
            b.set(i, new Double2(size - i, size - i));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestDoubles::addVectorDouble2, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Double2 sequential = new Double2(i + (size - i), i + (size - i));
            assertEquals(sequential.getX(), output.get(i).getX(), DELTA);
            assertEquals(sequential.getY(), output.get(i).getY(), DELTA);
        }
    }

    @Test
    public void testVectorDouble3() throws TornadoExecutionPlanException {
        int size = 64;

        VectorDouble3 a = new VectorDouble3(size);
        VectorDouble3 b = new VectorDouble3(size);
        VectorDouble3 output = new VectorDouble3(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Double3(i, i, i));
            b.set(i, new Double3(size - i, size - i, size - i));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestDoubles::addVectorDouble3, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Double3 sequential = new Double3(i + (size - i), i + (size - i), i + (size - i));
            assertEquals(sequential.getX(), output.get(i).getX(), DELTA);
            assertEquals(sequential.getY(), output.get(i).getY(), DELTA);
            assertEquals(sequential.getZ(), output.get(i).getZ(), DELTA);
        }
    }

    @Test
    public void testVectorDouble4() throws TornadoExecutionPlanException {
        int size = 64;

        VectorDouble4 a = new VectorDouble4(size);
        VectorDouble4 b = new VectorDouble4(size);
        VectorDouble4 output = new VectorDouble4(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Double4(i, i, i, i));
            b.set(i, new Double4(size - i, size - i, size - i, size - i));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestDoubles::addVectorDouble4, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Double4 sequential = new Double4(i + (size - i), i + (size - i), i + (size - i), i + (size - i));
            assertEquals(sequential.getX(), output.get(i).getX(), DELTA);
            assertEquals(sequential.getY(), output.get(i).getY(), DELTA);
            assertEquals(sequential.getZ(), output.get(i).getZ(), DELTA);
            assertEquals(sequential.getW(), output.get(i).getW(), DELTA);
        }
    }

    @Test
    public void testVectorDouble8() throws TornadoExecutionPlanException {
        int size = 64;

        VectorDouble8 a = new VectorDouble8(size);
        VectorDouble8 b = new VectorDouble8(size);
        VectorDouble8 output = new VectorDouble8(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Double8(i, i, i, i, i, i, i, i));
            b.set(i, new Double8(size - i, size - i, size - i, size - i, size - i, size - i, size - i, size - i));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestDoubles::addVectorDouble8, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Double8 sequential = new Double8(i + (size - i), i + (size - i), i + (size - i), i + (size - i), i + (size - i), i + (size - i), i + (size - i), i + (size - i));

            for (int j = 0; j < 8; j++) {
                assertEquals(sequential.get(j), output.get(i).get(j), DELTA);
            }
        }
    }

    @Test
    public void testVectorDouble16() throws TornadoExecutionPlanException {
        int size = 64;

        VectorDouble16 a = new VectorDouble16(size);
        VectorDouble16 b = new VectorDouble16(size);
        VectorDouble16 output = new VectorDouble16(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Double16(i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i));
            b.set(i, new Double16(size - i, size - i, size - i, size - i, size - i, size - i, size - i, size - i, size - i, size - i, size - i, size - i, size - i, size - i, size - i, size - i));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestDoubles::addVectorDouble16, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            Double16 sequential = new Double16(i + (size - i), i + (size - i), i + (size - i), i + (size - i), i + (size - i), i + (size - i), i + (size - i), i + (size - i), i + (size - i),
                    i + (size - i), i + (size - i), i + (size - i), i + (size - i), i + (size - i), i + (size - i), i + (size - i));

            for (int j = 0; j < 16; j++) {
                assertEquals(sequential.get(j), output.get(i).get(j), DELTA);
            }
        }
    }

    @Test
    public void privateVectorDouble2() throws TornadoExecutionPlanException {
        int size = 16;
        VectorDouble2 sequentialOutput = new VectorDouble2(size);
        VectorDouble2 tornadoOutput = new VectorDouble2(size);

        TaskGraph taskGraph = new TaskGraph("s0");
        taskGraph.task("t0", TestDoubles::testPrivateVectorDouble2, tornadoOutput);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoOutput);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testPrivateVectorDouble2(sequentialOutput);

        for (int i = 0; i < size; i++) {
            assertEquals(sequentialOutput.get(i).getX(), tornadoOutput.get(i).getX(), DELTA);
            assertEquals(sequentialOutput.get(i).getY(), tornadoOutput.get(i).getY(), DELTA);
        }
    }

    @Test
    public void privateVectorDouble4() throws TornadoExecutionPlanException {
        int size = 16;
        VectorDouble4 sequentialOutput = new VectorDouble4(size);
        VectorDouble4 tornadoOutput = new VectorDouble4(size);

        TaskGraph taskGraph = new TaskGraph("s0");
        taskGraph.task("t0", TestDoubles::testPrivateVectorDouble4, tornadoOutput);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoOutput);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testPrivateVectorDouble4(sequentialOutput);

        for (int i = 0; i < size; i++) {
            assertEquals(sequentialOutput.get(i).getX(), tornadoOutput.get(i).getX(), DELTA);
            assertEquals(sequentialOutput.get(i).getY(), tornadoOutput.get(i).getY(), DELTA);
            assertEquals(sequentialOutput.get(i).getZ(), tornadoOutput.get(i).getZ(), DELTA);
            assertEquals(sequentialOutput.get(i).getW(), tornadoOutput.get(i).getW(), DELTA);
        }
    }

    @Test
    public void privateVectorDouble8() throws TornadoExecutionPlanException {
        int size = 16;
        VectorDouble8 sequentialOutput = new VectorDouble8(16);
        VectorDouble8 tornadoOutput = new VectorDouble8(16);

        TaskGraph taskGraph = new TaskGraph("s0");
        taskGraph.task("t0", TestDoubles::testPrivateVectorDouble8, tornadoOutput);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoOutput);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testPrivateVectorDouble8(sequentialOutput);

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
    public void testInternalSetMethod01() throws TornadoExecutionPlanException {
        final int size = 16;
        VectorDouble2 tornadoInput = new VectorDouble2(size);
        VectorDouble2 sequentialInput = new VectorDouble2(size);
        VectorDouble2 tornadoOutput = new VectorDouble2(size);
        VectorDouble2 sequentialOutput = new VectorDouble2(size);

        for (int i = 0; i < size; i++) {
            double value = Math.random();
            Double2 double2 = new Double2(value, value);
            tornadoInput.set(i, double2);
            sequentialInput.set(i, double2);
        }

        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tornadoInput) //
                .task("t0", TestDoubles::vectorComputation01, tornadoInput, tornadoOutput) //
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
        VectorDouble3 tornadoInput = new VectorDouble3(size);
        VectorDouble3 sequentialInput = new VectorDouble3(size);
        VectorDouble3 tornadoOutput = new VectorDouble3(size);
        VectorDouble3 sequentialOutput = new VectorDouble3(size);

        for (int i = 0; i < size; i++) {
            double value = Math.random();
            Double3 double3 = new Double3(value, value, value);
            tornadoInput.set(i, double3);
            sequentialInput.set(i, double3);
        }

        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tornadoInput) //
                .task("t0", TestDoubles::vectorComputation02, tornadoInput, tornadoOutput) //
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
        VectorDouble4 tornadoInput = new VectorDouble4(size);
        VectorDouble4 sequentialInput = new VectorDouble4(size);
        VectorDouble3 tornadoOutput = new VectorDouble3(size);
        VectorDouble3 sequentialOutput = new VectorDouble3(size);

        for (int i = 0; i < size; i++) {
            double value = Math.random();
            Double4 double4 = new Double4(value, value, value, value);
            tornadoInput.set(i, double4);
            sequentialInput.set(i, double4);
        }

        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tornadoInput) //
                .task("t0", TestDoubles::vectorComputation03, tornadoInput, tornadoOutput) //
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
        VectorDouble8 tornadoInput = new VectorDouble8(size);
        VectorDouble8 sequentialInput = new VectorDouble8(size);
        VectorDouble2 tornadoOutput = new VectorDouble2(size);
        VectorDouble2 sequentialOutput = new VectorDouble2(size);

        for (int i = 0; i < size; i++) {
            double value = Math.random();
            Double8 double8 = new Double8(value, value, value, value, value, value, value, value);
            tornadoInput.set(i, double8);
            sequentialInput.set(i, double8);
        }

        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tornadoInput) //
                .task("t0", TestDoubles::vectorComputation04, tornadoInput, tornadoOutput) //
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

}
