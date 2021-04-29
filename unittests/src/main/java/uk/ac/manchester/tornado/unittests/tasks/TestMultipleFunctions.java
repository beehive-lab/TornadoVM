/*
 * Copyright (c) 2013-2021, APT Group, Department of Computer Science,
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

import org.junit.Assert;
import org.junit.Test;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import java.util.Random;

/**
 * Tests TornadoVM compilation under different scenarios, when not performing inlining in the method passed to the task.
 */
public class TestMultipleFunctions extends TornadoTestBase {

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

        public TestArrays() {
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

    public static void callee2(int[] calleeReadWrite, int[] calleeRead) {
        for (int i = 0; i < calleeReadWrite.length; i++) {
            calleeReadWrite[i] = calleeReadWrite[i] - calleeRead[i];
        }
    }

    public void caller3(int[] callerReadWrite, int[] callee1Read, int[] callee1Write, int[] callee2ReadWrite, int[] callee2Read) {
        for (int i = 0; i < callerReadWrite.length; i++) {
            callerReadWrite[i] = callerReadWrite[i] + 20;
        }
        callee1(callee1Read, callee1Write);
        callee2(callee2ReadWrite, callee2Read);
    }

    /**
     * Tests {@link uk.ac.manchester.tornado.api.common.Access} pattern when calling a method and writing to one
     * of the parameters in the callee.
     */
    @Test
    public void testSingleTask() {
        TestArrays testArrays = new TestArrays();

        caller1(testArrays.calleeReadSeq, testArrays.ignoreParam1, testArrays.callerReadCalleeWriteSeq, testArrays.ignoreParam2, testArrays.callerReadSeq, testArrays.callerWriteSeq);

        TestMultipleFunctions testTaskAccesses = new TestMultipleFunctions();

        TaskSchedule ts = new TaskSchedule("s0")
                .task("t0", testTaskAccesses::caller1, testArrays.calleeReadTor, testArrays.ignoreParam1, testArrays.callerReadCalleeWriteTor, testArrays.ignoreParam2, testArrays.callerReadTor, testArrays.callerWriteTor)
                .streamOut(testArrays.callerReadCalleeWriteTor, testArrays.callerWriteTor);
        ts.execute();

        Assert.assertArrayEquals(testArrays.calleeReadSeq, testArrays.calleeReadTor);
        Assert.assertArrayEquals(testArrays.callerReadCalleeWriteSeq, testArrays.callerReadCalleeWriteTor);
        Assert.assertArrayEquals(testArrays.callerReadSeq, testArrays.callerReadTor);
        Assert.assertArrayEquals(testArrays.callerWriteSeq, testArrays.callerWriteTor);
    }

    /**
     * Tests {@link uk.ac.manchester.tornado.api.common.Access} pattern when calling two methods from different tasks,
     * passing the same parameter to both tasks, and writing in only one callee.
     */
    @Test
    public void testMultipleTasks() {
        TestArrays testArrays = new TestArrays();

        caller1(testArrays.calleeReadSeq, testArrays.ignoreParam1, testArrays.callerReadCalleeWriteSeq, testArrays.ignoreParam2, testArrays.callerReadSeq, testArrays.callerWriteSeq);
        caller2(testArrays.callerReadSeq, testArrays.calleeReadSeq);

        TestMultipleFunctions testTaskAccesses = new TestMultipleFunctions();

        TaskSchedule ts = new TaskSchedule("s0")
                .task("t0", testTaskAccesses::caller1, testArrays.calleeReadTor, testArrays.ignoreParam1, testArrays.callerReadCalleeWriteTor, testArrays.ignoreParam2, testArrays.callerReadTor, testArrays.callerWriteTor)
                .task("t1", testTaskAccesses::caller2, testArrays.callerReadTor, testArrays.calleeReadTor)
                .streamOut(testArrays.callerReadCalleeWriteTor, testArrays.callerWriteTor, testArrays.callerReadTor);
        ts.execute();

        Assert.assertArrayEquals(testArrays.calleeReadSeq, testArrays.calleeReadTor);
        Assert.assertArrayEquals(testArrays.callerReadCalleeWriteSeq, testArrays.callerReadCalleeWriteTor);
        Assert.assertArrayEquals(testArrays.callerReadSeq, testArrays.callerReadTor);
        Assert.assertArrayEquals(testArrays.callerWriteSeq, testArrays.callerWriteTor);
    }

    /**
     * Tests {@link uk.ac.manchester.tornado.api.common.Access} pattern when calling three methods from different tasks.
     * Performs a combination of {@link #testMultipleTasks} and {@link #testSingleTask}.
     */
    @Test
    public void testMultipleTasksMultipleCallees() {
        TestArrays arrays = new TestArrays();

        caller1(arrays.calleeReadSeq, arrays.ignoreParam1, arrays.callerReadCalleeWriteSeq, arrays.ignoreParam2, arrays.callerReadSeq, arrays.callerWriteSeq);
        caller2(arrays.callerReadSeq, arrays.calleeReadSeq);
        caller3(arrays.callerReadWriteSeq, arrays.calleeReadSeq, arrays.callee1WriteSeq, arrays.callerReadCalleeWriteSeq, arrays.callee2ReadSeq);

        TestMultipleFunctions testTaskAccesses = new TestMultipleFunctions();

        TaskSchedule ts = new TaskSchedule("s0")
                .task("t0", testTaskAccesses::caller1, arrays.calleeReadTor, arrays.ignoreParam1, arrays.callerReadCalleeWriteTor, arrays.ignoreParam2, arrays.callerReadTor, arrays.callerWriteTor)
                .task("t1", testTaskAccesses::caller2, arrays.callerReadTor, arrays.calleeReadTor)
                .task("t2", testTaskAccesses::caller3, arrays.callerReadWriteTor, arrays.calleeReadTor, arrays.callee1WriteTor, arrays.callerReadCalleeWriteTor, arrays.callee2ReadTor)
                .streamOut(arrays.callerReadCalleeWriteTor, arrays.callerWriteTor, arrays.callerReadTor, arrays.callerReadWriteTor, arrays.callee1WriteTor, arrays.callerReadCalleeWriteTor);
        ts.execute();

        Assert.assertArrayEquals(arrays.calleeReadSeq, arrays.calleeReadTor);
        Assert.assertArrayEquals(arrays.callerReadCalleeWriteSeq, arrays.callerReadCalleeWriteTor);
        Assert.assertArrayEquals(arrays.callerReadSeq, arrays.callerReadTor);
        Assert.assertArrayEquals(arrays.callerWriteSeq, arrays.callerWriteTor);
        Assert.assertArrayEquals(arrays.callerReadWriteSeq, arrays.callerReadWriteTor);
        Assert.assertArrayEquals(arrays.callee1WriteSeq, arrays.callee1WriteTor);
        Assert.assertArrayEquals(arrays.callee2ReadSeq, arrays.callee2ReadTor);
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

    /**
     * Tests if methods/functions invoked from different places in the call graph do not get compiled twice.
     * A → B → D
     *   ↘ C ↗
     * If compiled twice, it will generate a runtime exception when launching the kernel.
     */
    @Test
    public void testNoDoubleCompilation() {
        int[] arr = new int[] { 0 };

        TaskSchedule ts = new TaskSchedule("s0")
                .task("t0", TestMultipleFunctions::functionA, arr)
                .streamOut(arr);

        ts.execute();

        Assert.assertEquals(-1, arr[0]);
    }

}
