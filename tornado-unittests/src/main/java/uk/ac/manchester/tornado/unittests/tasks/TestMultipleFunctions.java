/*
 * Copyright (c) 2021, 2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.tasks;

import static junit.framework.TestCase.assertEquals;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Assert;
import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.vectors.Float4;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Tests TornadoVM compilation under different scenarios, when not performing inlining in the method passed to the task.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tasks.TestMultipleFunctions
 * </code>
 */
public class TestMultipleFunctions extends TornadoTestBase {

    private static int operation(int a, int b) {
        return a + b;
    }

    public static void vectorAddInteger(IntArray a, IntArray b, IntArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, operation(a.get(i), b.get(i)));
        }
    }

    private static int operation2(int a, int b) {
        return a + operation(a, b);
    }

    public static void vectorAddInteger2(IntArray a, IntArray b, IntArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, operation2(a.get(i), b.get(i)));
        }
    }

    private static int operation3(int a, int b) {
        return a + operation2(a, b);
    }

    public static void vectorAddInteger3(IntArray a, IntArray b, IntArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, operation3(a.get(i), b.get(i)));
        }
    }

    private static int foo(int a) {
        return a + a;
    }

    private static int bar(int a) {
        return a * a;
    }

    private static float foo(float a) {
        return a + a;
    }

    private static float bar(float a) {
        return a * a;
    }

    private static Float4 foo(Float4 a) {
        return Float4.add(a, a);
    }

    private static Float4 bar(Float4 a) {
        return Float4.mult(a, a);
    }

    public static void vectorAddInteger4(IntArray a, IntArray b, IntArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, foo(a.get(i)) + bar(b.get(i)));
        }
    }

    public static void vectorAddFloats(FloatArray a, FloatArray b, FloatArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, foo(a.get(i)) + bar(b.get(i)));
        }
    }

    /**
     * Test to check we can generate vector types for the method signature and non-main kernel functions.
     */
    public static void vectorTypes(Float4 a, Float4 b, Float4 c) {
        c.set(Float4.add(foo(a), bar(b)));
    }

    public static void callee2(IntArray calleeReadWrite, IntArray calleeRead) {
        for (int i = 0; i < calleeReadWrite.getSize(); i++) {
            calleeReadWrite.set(i, calleeReadWrite.get(i) - calleeRead.get(i));
        }
    }

    private static void functionA(IntArray arr) {
        functionB(arr);
        functionC(arr);
    }

    private static void functionB(IntArray arr) {
        functionD(arr);
    }

    private static void functionC(IntArray arr) {
        functionD(arr);
    }

    private static void functionD(IntArray arr) {
        arr.set(0, -1);
    }

    @Test
    public void test01() throws TornadoExecutionPlanException {
        final int numElements = 4096;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        Random r = new Random();
        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, r.nextInt());
            b.set(i, r.nextInt());
        });

        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b).task("t0", TestMultipleFunctions::vectorAddInteger, a, b, c).transferToHost(
                DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals((a.get(i) + b.get(i)), c.get(i));
        }
    }

    @Test
    public void test02() throws TornadoExecutionPlanException {

        final int numElements = 4096;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        Random r = new Random();
        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, r.nextInt());
            b.set(i, r.nextInt());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestMultipleFunctions::vectorAddInteger2, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals((a.get(i) + (a.get(i) + b.get(i))), c.get(i));
        }
    }

    @Test
    public void test03() throws TornadoExecutionPlanException {

        final int numElements = 4096;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        Random r = new Random();
        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, r.nextInt());
            b.set(i, r.nextInt());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestMultipleFunctions::vectorAddInteger3, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals((a.get(i) + (a.get(i) + (a.get(i) + b.get(i)))), c.get(i));
        }
    }

    @Test
    public void test04() throws TornadoExecutionPlanException {

        final int numElements = 4096;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        Random r = new Random();
        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, r.nextInt());
            b.set(i, r.nextInt());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestMultipleFunctions::vectorAddInteger4, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals((a.get(i) + a.get(i)) + (b.get(i) * b.get(i)), c.get(i));
        }
    }

    @Test
    public void test05() throws TornadoExecutionPlanException {
        final int numElements = 8192 * 4;
        FloatArray a = new FloatArray(numElements);
        FloatArray b = new FloatArray(numElements);
        FloatArray c = new FloatArray(numElements);
        FloatArray checker = new FloatArray(numElements);

        Random r = new Random();
        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, r.nextInt(10));
            b.set(i, r.nextInt(10));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestMultipleFunctions::vectorAddFloats, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        vectorAddFloats(a, b, checker);

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(checker.get(i), c.get(i), 0.01f);
        }
    }

    /**
     * Test to check we can generate vector types for the method signature and non-main kernel functions.
     */
    @Test
    public void testVector01() throws TornadoExecutionPlanException {

        Float4 a = new Float4(1, 2, 3, 4);
        Float4 b = new Float4(4, 3, 2, 1);
        Float4 c = new Float4();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestMultipleFunctions::vectorTypes, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        Float4 result = Float4.add(foo(a), bar(b));

        assertEquals(result.getX(), c.getX());
        assertEquals(result.getY(), c.getY());
        assertEquals(result.getW(), c.getW());
        assertEquals(result.getZ(), c.getZ());
    }

    /**
     * Tests {@link uk.ac.manchester.tornado.api.common.Access} pattern when calling a method and writing to one of the parameters in the callee.
     */
    public void caller1(IntArray calleeRead, int ignoreParam1, IntArray callerReadCalleeWrite, int ignoreParam2, IntArray callerRead, IntArray callerWrite) {
        for (int i = 0; i < callerRead.getSize(); i++) {
            callerWrite.set(i, callerRead.get(i) + callerReadCalleeWrite.get(i) + 10);
        }
        callee1(calleeRead, callerReadCalleeWrite);
    }

    public void callee1(IntArray calleeRead, IntArray calleeWrite) {
        for (int i = 0; i < calleeRead.getSize(); i++) {
            calleeWrite.set(i, calleeRead.get(i) + 1);
        }
    }

    public void caller2(IntArray calleeReadWrite, IntArray calleeRead) {
        callee2(calleeReadWrite, calleeRead);
    }

    public void caller3(IntArray callerReadWrite, IntArray callee1Read, IntArray callee1Write, IntArray callee2ReadWrite, IntArray callee2Read) {
        for (int i = 0; i < callerReadWrite.getSize(); i++) {
            callerReadWrite.set(i, callerReadWrite.get(i) + 20);
        }
        callee1(callee1Read, callee1Write);
        callee2(callee2ReadWrite, callee2Read);
    }

    /**
     * Tests {@link uk.ac.manchester.tornado.api.common.Access} pattern when calling a method and writing to one of the parameters in the callee.
     */
    @Test
    public void testSingleTask() throws TornadoExecutionPlanException {
        TestArrays testArrays = new TestArrays();

        caller1(testArrays.calleeReadSeq, testArrays.ignoreParam1, testArrays.callerReadCalleeWriteSeq, testArrays.ignoreParam2, testArrays.callerReadSeq, testArrays.callerWriteSeq);

        TestMultipleFunctions testTaskAccesses = new TestMultipleFunctions();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, //
                        testArrays.calleeReadTor, //
                        testArrays.ignoreParam1, //
                        testArrays.callerReadTor) //
                .task("t0", testTaskAccesses::caller1, //
                        testArrays.calleeReadTor, //
                        testArrays.ignoreParam1, //
                        testArrays.callerReadCalleeWriteTor, //
                        testArrays.ignoreParam2, //
                        testArrays.callerReadTor, //
                        testArrays.callerWriteTor) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, testArrays.callerReadCalleeWriteTor, testArrays.callerWriteTor);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < testArrays.calleeReadTor.getSize(); i++) {
            Assert.assertEquals(testArrays.calleeReadSeq.get(i), testArrays.calleeReadTor.get(i));
        }
        for (int i = 0; i < testArrays.callerReadCalleeWriteSeq.getSize(); i++) {
            Assert.assertEquals(testArrays.callerReadCalleeWriteSeq.get(i), testArrays.callerReadCalleeWriteTor.get(i));
        }
        for (int i = 0; i < testArrays.callerReadSeq.getSize(); i++) {
            Assert.assertEquals(testArrays.callerReadSeq.get(i), testArrays.callerReadTor.get(i));
        }
        for (int i = 0; i < testArrays.callerWriteSeq.getSize(); i++) {
            Assert.assertEquals(testArrays.callerWriteSeq.get(i), testArrays.callerWriteSeq.get(i));
        }
    }

    /**
     * Tests {@link uk.ac.manchester.tornado.api.common.Access} pattern when calling two methods from different tasks, passing the same parameter to both tasks, and writing in only one callee.
     */
    @Test
    public void testMultipleTasks() throws TornadoExecutionPlanException {
        TestArrays testArrays = new TestArrays();

        caller1(testArrays.calleeReadSeq, testArrays.ignoreParam1, testArrays.callerReadCalleeWriteSeq, testArrays.ignoreParam2, testArrays.callerReadSeq, testArrays.callerWriteSeq);
        caller2(testArrays.callerReadSeq, testArrays.calleeReadSeq);

        TestMultipleFunctions testTaskAccesses = new TestMultipleFunctions();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, //
                        testArrays.calleeReadTor) //
                .task("t0", testTaskAccesses::caller1, //
                        testArrays.calleeReadTor, //
                        testArrays.ignoreParam1, //
                        testArrays.callerReadCalleeWriteTor, //
                        testArrays.ignoreParam2, //
                        testArrays.callerReadTor, //
                        testArrays.callerWriteTor) //
                .task("t1", testTaskAccesses::caller2, testArrays.callerReadTor, testArrays.calleeReadTor).transferToHost(DataTransferMode.EVERY_EXECUTION, testArrays.callerReadCalleeWriteTor,
                        testArrays.callerWriteTor, testArrays.callerReadTor);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < testArrays.calleeReadTor.getSize(); i++) {
            Assert.assertEquals(testArrays.calleeReadSeq.get(i), testArrays.calleeReadTor.get(i));
        }
        for (int i = 0; i < testArrays.callerReadCalleeWriteSeq.getSize(); i++) {
            Assert.assertEquals(testArrays.callerReadCalleeWriteSeq.get(i), testArrays.callerReadCalleeWriteTor.get(i));
        }
        for (int i = 0; i < testArrays.callerReadSeq.getSize(); i++) {
            Assert.assertEquals(testArrays.callerReadSeq.get(i), testArrays.callerReadTor.get(i));
        }
        for (int i = 0; i < testArrays.callerWriteSeq.getSize(); i++) {
            Assert.assertEquals(testArrays.callerWriteSeq.get(i), testArrays.callerWriteSeq.get(i));
        }
    }

    /**
     * Tests {@link uk.ac.manchester.tornado.api.common.Access} pattern when calling three methods from different tasks. Performs a combination of {@link #testMultipleTasks} and
     * {@link #testSingleTask}.
     */
    @Test
    public void testMultipleTasksMultipleCallees() {
        TestArrays arrays = new TestArrays();

        caller1(arrays.calleeReadSeq, arrays.ignoreParam1, arrays.callerReadCalleeWriteSeq, arrays.ignoreParam2, arrays.callerReadSeq, arrays.callerWriteSeq);
        caller2(arrays.callerReadSeq, arrays.calleeReadSeq);
        caller3(arrays.callerReadWriteSeq, arrays.calleeReadSeq, arrays.callee1WriteSeq, arrays.callerReadCalleeWriteSeq, arrays.callee2ReadSeq);

        TestMultipleFunctions testTaskAccesses = new TestMultipleFunctions();

        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, arrays.calleeReadTor, //
                arrays.callee2ReadTor)//
                .task("t0", testTaskAccesses::caller1, //
                        arrays.calleeReadTor, //
                        arrays.ignoreParam1, //
                        arrays.callerReadCalleeWriteTor, //
                        arrays.ignoreParam2, //
                        arrays.callerReadTor, //
                        arrays.callerWriteTor)//
                .task("t1", testTaskAccesses::caller2, arrays.callerReadTor, //
                        arrays.calleeReadTor) //
                .task("t2", testTaskAccesses::caller3, //
                        arrays.callerReadWriteTor, //
                        arrays.calleeReadTor, //
                        arrays.callee1WriteTor, //
                        arrays.callerReadCalleeWriteTor, //
                        arrays.callee2ReadTor)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrays.callerReadCalleeWriteTor, //
                        arrays.callerWriteTor, //
                        arrays.callerReadTor, //
                        arrays.callerReadWriteTor, //
                        arrays.callee1WriteTor, //
                        arrays.callerReadCalleeWriteTor); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < arrays.calleeReadTor.getSize(); i++) {
            Assert.assertEquals(arrays.calleeReadSeq.get(i), arrays.calleeReadTor.get(i));
        }
        for (int i = 0; i < arrays.callerReadCalleeWriteSeq.getSize(); i++) {
            Assert.assertEquals(arrays.callerReadCalleeWriteSeq.get(i), arrays.callerReadCalleeWriteTor.get(i));
        }
        for (int i = 0; i < arrays.callerReadSeq.getSize(); i++) {
            Assert.assertEquals(arrays.callerReadSeq.get(i), arrays.callerReadTor.get(i));
        }
        for (int i = 0; i < arrays.callerWriteSeq.getSize(); i++) {
            Assert.assertEquals(arrays.callerWriteSeq.get(i), arrays.callerWriteSeq.get(i));
        }
        for (int i = 0; i < arrays.callerReadWriteSeq.getSize(); i++) {
            Assert.assertEquals(arrays.callerReadWriteSeq.get(i), arrays.callerReadWriteTor.get(i));
        }
        for (int i = 0; i < arrays.callee1WriteSeq.getSize(); i++) {
            Assert.assertEquals(arrays.callee1WriteSeq.get(i), arrays.callee1WriteTor.get(i));
        }
        for (int i = 0; i < arrays.callee2ReadSeq.getSize(); i++) {
            Assert.assertEquals(arrays.callee2ReadSeq.get(i), arrays.callee2ReadTor.get(i));
        }
    }

    //@formatter:off
    // CHECKSTYLE:OFF
    /** Tests if methods/functions invoked from different places in the call graph do not get compiled twice.
     *    A → B → D
     *      ↘ C ↗
     * If compiled twice, it will generate a runtime exception when launching the kernel.
     */
    // CHECKSTYLE:ON
    //@formatter:on
    @Test
    public void testNoDoubleCompilation() throws TornadoExecutionPlanException {
        IntArray arr = new IntArray(1);
        arr.init(0);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestMultipleFunctions::functionA, arr)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arr);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        Assert.assertEquals(-1, arr.get(0));
    }

    //@formatter:off

    private static class TestArrays {
        final int N1 = 1024;

        IntArray calleeReadTor;
        IntArray callerReadCalleeWriteTor;
        IntArray callerReadTor;
        IntArray callerWriteTor;
        IntArray callerReadWriteTor;
        IntArray callee1WriteTor;
        IntArray callee2ReadTor;

        IntArray calleeReadSeq;
        IntArray callerReadCalleeWriteSeq;
        IntArray callerReadSeq;
        IntArray callerWriteSeq;
        IntArray callerReadWriteSeq;
        IntArray callee1WriteSeq;
        IntArray callee2ReadSeq;

        int ignoreParam1 = 10;
        int ignoreParam2 = -500;

        public TestArrays() {
            calleeReadTor = new IntArray(N1);
            callerReadCalleeWriteTor = new IntArray(N1);
            callerReadTor = new IntArray(N1);
            callerWriteTor = new IntArray(N1);
            callerReadWriteTor = new IntArray(N1);
            callee1WriteTor = new IntArray(N1);
            callee2ReadTor = new IntArray(N1);
            calleeReadSeq = new IntArray(N1);
            callerReadCalleeWriteSeq = new IntArray(N1);
            callerReadSeq = new IntArray(N1);
            callerWriteSeq = new IntArray(N1);
            callerReadWriteSeq = new IntArray(N1);
            callee1WriteSeq = new IntArray(N1);
            callee2ReadSeq = new IntArray(N1);

            Random random = new Random();
            for (int i = 0; i < N1; i++) {
                calleeReadTor.set(i, random.nextInt());
                calleeReadSeq.set(i, calleeReadTor.get(i));
                callerReadCalleeWriteTor.set(i, random.nextInt());
                callerReadCalleeWriteSeq.set(i, callerReadCalleeWriteTor.get(i));
                callerReadTor.set(i, random.nextInt());
                callerReadSeq.set(i, callerReadTor.get(i));
                callerWriteTor.set(i, random.nextInt());
                callerWriteSeq.set(i, callerWriteTor.get(i));
                callerReadWriteTor.set(i, random.nextInt());
                callerReadWriteSeq.set(i, callerReadWriteTor.get(i));
                callee1WriteTor.set(i, random.nextInt());
                callee1WriteSeq.set(i, callee1WriteTor.get(i));
                callee2ReadTor.set(i, random.nextInt());
                callee2ReadSeq.set(i, callee2ReadTor.get(i));
            }
        }
    }

}
