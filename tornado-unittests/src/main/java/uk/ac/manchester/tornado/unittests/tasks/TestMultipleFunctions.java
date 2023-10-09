/*
 * Copyright (c) 2021, 2022, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.api.collections.types.Float4;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Tests TornadoVM compilation under different scenarios, when not performing
 * inlining in the method passed to the task.
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado-test -V uk.ac.manchester.tornado.unittests.tasks.TestMultipleFunctions
 * </code>
 */
public class TestMultipleFunctions extends TornadoTestBase {

    private static int operation(int a, int b) {
        return a + b;
    }

    public static void vectorAddInteger(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = operation(a[i], b[i]);
        }
    }

    private static int operation2(int a, int b) {
        return a + operation(a, b);
    }

    public static void vectorAddInteger2(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = operation2(a[i], b[i]);
        }
    }

    private static int operation3(int a, int b) {
        return a + operation2(a, b);
    }

    public static void vectorAddInteger3(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = operation3(a[i], b[i]);
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

    public static void vectorAddInteger4(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = foo(a[i]) + bar(b[i]);
        }
    }

    public static void vectorAddFloats(float[] a, float[] b, float[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = foo(a[i]) + bar(b[i]);
        }
    }

    /**
     * Test to check we can generate vector types for the method signature and
     * non-main kernel functions.
     */
    public static void vectorTypes(Float4 a, Float4 b, Float4 c) {
        c.set(Float4.add(foo(a), bar(b)));
    }

    public static void callee2(int[] calleeReadWrite, int[] calleeRead) {
        for (int i = 0; i < calleeReadWrite.length; i++) {
            calleeReadWrite[i] = calleeReadWrite[i] - calleeRead[i];
        }
    }

    private static void functionA(int[] arr) {
        functionB(arr);
        functionC(arr);
    }

    private static void functionB(int[] arr) {
        functionD(arr);
    }

    private static void functionC(int[] arr) {
        functionD(arr);
    }

    private static void functionD(int[] arr) {
        arr[0] = -1;
    }

    @Test
    public void test01() {
        final int numElements = 4096;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Random r = new Random();
        IntStream.range(0, numElements).sequential().forEach(i -> {
            a[i] = r.nextInt();
            b[i] = r.nextInt();
        });

        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b).task("t0", TestMultipleFunctions::vectorAddInteger, a, b, c)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.length; i++) {
            assertEquals((a[i] + b[i]), c[i]);
        }
    }

    @Test
    public void test02() {

        final int numElements = 4096;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Random r = new Random();
        IntStream.range(0, numElements).sequential().forEach(i -> {
            a[i] = r.nextInt();
            b[i] = r.nextInt();
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestMultipleFunctions::vectorAddInteger2, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.length; i++) {
            assertEquals((a[i] + (a[i] + b[i])), c[i]);
        }
    }

    @Test
    public void test03() {

        final int numElements = 4096;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Random r = new Random();
        IntStream.range(0, numElements).sequential().forEach(i -> {
            a[i] = r.nextInt();
            b[i] = r.nextInt();
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestMultipleFunctions::vectorAddInteger3, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.length; i++) {
            assertEquals((a[i] + (a[i] + (a[i] + b[i]))), c[i]);
        }
    }

    @Test
    public void test04() {

        final int numElements = 4096;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Random r = new Random();
        IntStream.range(0, numElements).sequential().forEach(i -> {
            a[i] = r.nextInt();
            b[i] = r.nextInt();
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestMultipleFunctions::vectorAddInteger4, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.length; i++) {
            assertEquals((a[i] + a[i]) + (b[i] * b[i]), c[i]);
        }
    }

    @Test
    public void test05() {
        final int numElements = 8192 * 4;
        float[] a = new float[numElements];
        float[] b = new float[numElements];
        float[] c = new float[numElements];
        float[] checker = new float[numElements];

        Random r = new Random();
        IntStream.range(0, numElements).sequential().forEach(i -> {
            a[i] = r.nextInt(10);
            b[i] = r.nextInt(10);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestMultipleFunctions::vectorAddFloats, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        vectorAddFloats(a, b, checker);

        for (int i = 0; i < c.length; i++) {
            assertEquals(checker[i], c[i], 0.01f);
        }
    }

    /**
     * Test to check we can generate vector types for the method signature and
     * non-main kernel functions.
     */
    @Test
    public void testVector01() {

        Float4 a = new Float4(1, 2, 3, 4);
        Float4 b = new Float4(4, 3, 2, 1);
        Float4 c = new Float4();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestMultipleFunctions::vectorTypes, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        Float4 result = Float4.add(foo(a), bar(b));

        assertEquals(result.getX(), c.getX());
        assertEquals(result.getY(), c.getY());
        assertEquals(result.getW(), c.getW());
        assertEquals(result.getZ(), c.getZ());
    }

    /**
     * Tests {@link uk.ac.manchester.tornado.api.common.Access} pattern when calling
     * a method and writing to one of the parameters in the callee.
     */
    public void caller1(int[] calleeRead, int ignoreParam1, int[] callerReadCalleeWrite, int ignoreParam2, int[] callerRead, int[] callerWrite) {
        for (int i = 0; i < callerRead.length; i++) {
            callerWrite[i] = callerRead[i] + callerReadCalleeWrite[i] + 10;
        }
        callee1(calleeRead, callerReadCalleeWrite);
    }

    public void callee1(int[] calleeRead, int[] calleeWrite) {
        for (int i = 0; i < calleeRead.length; i++) {
            calleeWrite[i] = calleeRead[i] + 1;
        }
    }

    public void caller2(int[] calleeReadWrite, int[] calleeRead) {
        callee2(calleeReadWrite, calleeRead);
    }

    public void caller3(int[] callerReadWrite, int[] callee1Read, int[] callee1Write, int[] callee2ReadWrite, int[] callee2Read) {
        for (int i = 0; i < callerReadWrite.length; i++) {
            callerReadWrite[i] = callerReadWrite[i] + 20;
        }
        callee1(callee1Read, callee1Write);
        callee2(callee2ReadWrite, callee2Read);
    }

    /**
     * Tests {@link uk.ac.manchester.tornado.api.common.Access} pattern when calling
     * a method and writing to one of the parameters in the callee.
     */
    @Test
    public void testSingleTask() {
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
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        Assert.assertArrayEquals(testArrays.calleeReadSeq, testArrays.calleeReadTor);
        Assert.assertArrayEquals(testArrays.callerReadCalleeWriteSeq, testArrays.callerReadCalleeWriteTor);
        Assert.assertArrayEquals(testArrays.callerReadSeq, testArrays.callerReadTor);
        Assert.assertArrayEquals(testArrays.callerWriteSeq, testArrays.callerWriteTor);
    }

    /**
     * Tests {@link uk.ac.manchester.tornado.api.common.Access} pattern when calling
     * two methods from different tasks, passing the same parameter to both tasks,
     * and writing in only one callee.
     */
    @Test
    public void testMultipleTasks() {
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
                .task("t1", testTaskAccesses::caller2, testArrays.callerReadTor, testArrays.calleeReadTor)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, testArrays.callerReadCalleeWriteTor, testArrays.callerWriteTor, testArrays.callerReadTor);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        Assert.assertArrayEquals(testArrays.calleeReadSeq, testArrays.calleeReadTor);
        Assert.assertArrayEquals(testArrays.callerReadCalleeWriteSeq, testArrays.callerReadCalleeWriteTor);
        Assert.assertArrayEquals(testArrays.callerReadSeq, testArrays.callerReadTor);
        Assert.assertArrayEquals(testArrays.callerWriteSeq, testArrays.callerWriteTor);
    }

    /**
     * Tests {@link uk.ac.manchester.tornado.api.common.Access} pattern when calling
     * three methods from different tasks. Performs a combination of
     * {@link #testMultipleTasks} and {@link #testSingleTask}.
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

        Assert.assertArrayEquals(arrays.calleeReadSeq, arrays.calleeReadTor);
        Assert.assertArrayEquals(arrays.callerReadCalleeWriteSeq, arrays.callerReadCalleeWriteTor);
        Assert.assertArrayEquals(arrays.callerReadSeq, arrays.callerReadTor);
        Assert.assertArrayEquals(arrays.callerWriteSeq, arrays.callerWriteTor);
        Assert.assertArrayEquals(arrays.callerReadWriteSeq, arrays.callerReadWriteTor);
        Assert.assertArrayEquals(arrays.callee1WriteSeq, arrays.callee1WriteTor);
        Assert.assertArrayEquals(arrays.callee2ReadSeq, arrays.callee2ReadTor);
    }

    //@formatter:off
    /** Tests if methods/functions invoked from different places in the call graph do not get compiled twice.
     *    A → B → D
     *      ↘ C ↗
     * If compiled twice, it will generate a runtime exception when launching the kernel.
     */
    //@formatter:on
    @Test
    public void testNoDoubleCompilation() {
        int[] arr = new int[] { 0 };
        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestMultipleFunctions::functionA, arr)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arr);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        Assert.assertEquals(-1, arr[0]);
    }

    //@formatter:off

    private static class TestArrays {
        final int N1 = 1024;

        int[] calleeReadTor;
        int[] callerReadCalleeWriteTor;
        int[] callerReadTor;
        int[] callerWriteTor;
        int[] callerReadWriteTor;
        int[] callee1WriteTor;
        int[] callee2ReadTor;

        int[] calleeReadSeq;
        int[] callerReadCalleeWriteSeq;
        int[] callerReadSeq;
        int[] callerWriteSeq;
        int[] callerReadWriteSeq;
        int[] callee1WriteSeq;
        int[] callee2ReadSeq;

        int ignoreParam1 = 10;
        int ignoreParam2 = -500;

        TestArrays() {
            calleeReadTor = new int[N1];
            callerReadCalleeWriteTor = new int[N1];
            callerReadTor = new int[N1];
            callerWriteTor = new int[N1];
            callerReadWriteTor = new int[N1];
            callee1WriteTor = new int[N1];
            callee2ReadTor = new int[N1];

            Random random = new Random();
            for (int i = 0; i < N1; i++) {
                calleeReadTor[i] = random.nextInt();
                callerReadCalleeWriteTor[i] = random.nextInt();
                callerReadTor[i] = random.nextInt();
                callerWriteTor[i] = random.nextInt();
                callerReadWriteTor[i] = random.nextInt();
                callee1WriteTor[i] = random.nextInt();
                callee2ReadTor[i] = random.nextInt();
            }

            calleeReadSeq = calleeReadTor.clone();
            callerReadCalleeWriteSeq = callerReadCalleeWriteTor.clone();
            callerReadSeq = callerReadTor.clone();
            callerWriteSeq = callerWriteTor.clone();
            callerReadWriteSeq = callerReadWriteTor.clone();
            callee1WriteSeq = callee1WriteTor.clone();
            callee2ReadSeq = callee2ReadTor.clone();
        }
    }

}
