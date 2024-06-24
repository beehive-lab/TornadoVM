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
package uk.ac.manchester.tornado.unittests.loops;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Ignore;
import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat;
import uk.ac.manchester.tornado.unittests.common.TornadoNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to test?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.loops.TestLoops
 * </code>
 */
public class TestLoops extends TornadoTestBase {

    public static void forConstant01(IntArray a, final int n) {
        for (@Parallel int i = 0; i < n; i++) {
            a.set(i, 10);
        }
    }

    public static void forConstant02(IntArray a, final int n) {
        for (@Parallel int i = 0; i <= n; i++) {
            a.set(i, 10);
        }
    }

    public static void forConstant03(IntArray a, int n) {
        for (@Parallel int i = 0; i < n; i++) {
            a.set(i, 10);
        }
    }

    public static void forConstant04(Matrix2DFloat m, int n) {
        for (@Parallel int i = 0; i <= n; i++) {
            for (@Parallel int j = 0; j <= n; j++) {
                m.set(i, j, 10);
            }
        }
    }

    public static void forConstant05(Matrix2DFloat m, int n) {
        for (@Parallel int i = 0; i < n; i++) {
            for (@Parallel int j = 0; j < n; j++) {
                m.set(i, j, 10);
            }
        }
    }

    public static void forConstant06(Matrix2DFloat m2, int n, int m) {
        for (@Parallel int i = 0; i <= n; i++) {
            for (@Parallel int j = 0; j <= m; j++) {
                m2.set(i, j, 10);
            }
        }
    }

    public static void forLoopOneD(IntArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, 10);
        }
    }

    public static void steppedLoop(IntArray a, int size) {
        for (@Parallel int i = 0; i < size; i += 2) {
            a.set(i, 200);
        }
    }

    public static void steppedLoop2(IntArray a, int size) {
        for (@Parallel int i = 0; i < size; i += 2) {
            a.set(i, 200);
            a.set(i + 1, 200);
        }
    }

    public static void steppedLoop3(IntArray a, int size) {
        for (@Parallel int i = 0; i < size; i += 3) {
            a.set(i, 200);
            a.set(i + 1, 200);
            a.set(i + 2, 200);
        }
    }

    public static void steppedLoop4(IntArray a, int size) {
        for (@Parallel int i = 0; i < size; i += 4) {
            a.set(i, 200);
        }
    }

    public static void steppedLoop5(IntArray a, int size) {
        for (@Parallel int i = 0; i < size; i += 3) {
            a.set(i, 200);
        }
    }

    public static void steppedLoop7(IntArray a, int size) {
        for (@Parallel int i = 0; i < size; i += 7) {
            a.set(i, 200);
        }
    }

    public static void steppedLoop10(IntArray a, int size) {
        for (@Parallel int i = 0; i < size; i += 10) {
            a.set(i, 200);
        }
    }

    public static void conditionalInLoop(IntArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            if (i == 4) {
                a.set(i, 4);
            } else {
                a.set(i, 10);
            }
        }
    }

    public static void conditionalInLoop2(IntArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            if (i != 4) {
                a.set(i, 10);
            }
        }
    }

    public static void conditionalIfElseLoop(IntArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            if (i == 4) {
                a.set(i, 4);
            } else if (i == 5) {
                a.set(i, 5);
            } else {
                a.set(i, 10);
            }
        }
    }

    public static void twoDLoop(int[][] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[i].length; j++) {
                a[i][j] = 10;
            }
        }
    }

    public static void nestedForLoopOneDArray(IntArray a, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                a.set(i * size + j, 10);
            }
        }
    }

    public static void nestedForLoopTwoDArray(int[][] a, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                a[i][j] = 10;
            }
        }
    }

    // TODO: Thi is a bad test, compiler strips down all control flow and codegen
    /*-
     * __kernel void controlFlowBreak(__global long *_kernel_context, __constant uchar *_constant_region, __local uchar *_local_region, __global int *_atomics, __global uchar *a)
     * {
     *   ulong ul_1, ul_0;
     *
     *   // BLOCK 0
     *   ul_0  =  (ulong) a;
     *   ul_1  =  ul_0 + 32L;
     *   *((__global int *) ul_1)  =  4;
     *   return;
     * }  //  kernel
     */
    public static void controlFlowBreak(IntArray a) {
        for (int i = 0; i < a.getSize(); i++) {
            if (i == 4) {
                a.set(i, 4);
                break;
            }
        }
    }

    // This test actually prevents optimizing the associated control flow
    public static void controlFlowBreak2(IntArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            if (a.get(i) == 2) {
                a.set(i, 10);
                break;
            }
        }
    }

    public static void controlFlowContinue(IntArray a) {
        for (int i = 0; i < a.getSize(); i++) {
            if (i == 4) {
                continue;
            }
            a.set(i, 150);
        }
    }

    public static void nested2ParallelLoops(IntArray a, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                a.set(i * size + j, 10);
            }
        }
    }

    public static void whileLoop(IntArray a, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            int y = 0;
            while (y < size) {
                a.set(i * size + y, 10);
                y++;
            }
        }
    }

    public static void dowWhileLoop(IntArray a, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            int y = 1;
            do {
                a.set(i * size + y, 10);
                y++;
            } while (y < size);
        }
    }

    public static void forEach(int[] a, int[] c, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            int idx = 0;
            for (int j : a) {
                c[idx] = j + 1;
                idx++;
            }
        }
    }

    public static void reverseLoop(IntArray a) {
        for (@Parallel int i = a.getSize() - 1; i >= 0; i--) {
            a.set(i, 10);
        }
    }

    private static void testSingleThreadLoopCond(IntArray in, IntArray out) {
        int otherCompVal = in.get(0);
        int i = 0;
        for (; i < in.getSize() / 4 - 1; i++) {
            int someNumber = getNumber(in, i, i % 4, 4);
            in.set(i, someNumber + i);
        }

        if (i == otherCompVal) {
            int someNumber = getNumber(in, i, i % 4, 4) + 1000;
            out.set(i, someNumber);
        }
    }

    private static int getNumber(IntArray in, int base, int offset, int multiplier) {
        // Perform some address computation
        return in.get(base * multiplier + offset);
    }

    private static void testMultipleThreadLoopCond(IntArray in, IntArray out) {
        int otherCompVal = in.get(0);

        @Parallel int i = 0;
        for (; i < in.getSize() / 4 - 1; i++) {
            int someNumber = getNumber(in, i, i % 4, 4);
            in.set(i, someNumber + i);
        }

        if (i == otherCompVal) {
            int someNumber = getNumber(in, i, i % 4, 4) + 1000;
            out.set(i, someNumber);
        }
    }

    @Test
    public void testForConstant01() throws TornadoExecutionPlanException {
        final int size = 256;
        IntArray a = new IntArray(size);
        a.init(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestLoops::forConstant01, a, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            assertEquals(10, a.get(i));
        }
    }

    @Test
    public void testForConstant02() throws TornadoExecutionPlanException {
        final int size = 256;
        IntArray a = new IntArray(size);
        a.init(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestLoops::forConstant02, a, (size - 1)) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            assertEquals(10, a.get(i));
        }
    }

    @Test
    public void testForConstant03() throws TornadoExecutionPlanException {
        int size = 256;
        IntArray a = new IntArray(size);
        a.init(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestLoops::forConstant03, a, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            assertEquals(10, a.get(i));
        }
    }

    @Test
    public void testForConstant04() throws TornadoExecutionPlanException {
        int size = 255;
        Matrix2DFloat m = new Matrix2DFloat(size, size);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestLoops::forConstant04, m, (size - 1)) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, m);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < m.getNumRows(); i++) {
            for (int j = 0; j < m.getNumColumns(); j++) {
                assertEquals(10.0f, m.get(i, j), 0.001f);
            }
        }
    }

    @Test
    public void testForConstant05() throws TornadoExecutionPlanException {
        int size = 256;
        Matrix2DFloat m = new Matrix2DFloat(size, size);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestLoops::forConstant05, m, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, m);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < m.getNumRows(); i++) {
            for (int j = 0; j < m.getNumColumns(); j++) {
                assertEquals(10.0f, m.get(i, j), 0.001f);
            }
        }
    }

    @Test
    public void testForConstant06() throws TornadoExecutionPlanException {
        int m = 256;
        int n = 64;
        Matrix2DFloat m2 = new Matrix2DFloat(m, n);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestLoops::forConstant06, m2, (m - 1), (n - 1)) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, m2);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < m2.getNumRows(); i++) {
            for (int j = 0; j < m2.getNumColumns(); j++) {
                assertEquals(10.0f, m2.get(i, j), 0.001f);
            }
        }
    }

    @Test
    public void testForLoopOneD() throws TornadoExecutionPlanException {
        final int size = 10;

        IntArray a = new IntArray(size);
        a.init(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestLoops::forLoopOneD, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            assertEquals(10, a.get(i));
        }
    }

    @Test
    public void testStepLoop() throws TornadoExecutionPlanException {
        final int size = 16;

        IntArray a = new IntArray(size);
        a.init(75);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestLoops::steppedLoop, a, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i += 2) {
            assertEquals(200, a.get(i));
            assertEquals(75, a.get(i + 1));
        }
    }

    @Test
    public void testStepLoop2() throws TornadoExecutionPlanException {
        final int size = 512;

        IntArray a = new IntArray(size);
        a.init(75);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestLoops::steppedLoop2, a, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(200, a.get(i));
        }
    }

    @Test
    public void testStepLoop3() throws TornadoExecutionPlanException {
        final int size = 512;

        IntArray a = new IntArray(size);
        a.init(75);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestLoops::steppedLoop3, a, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(200, a.get(i));
        }
    }

    @Test
    public void testStepLoop4() throws TornadoExecutionPlanException {
        final int size = 512;

        IntArray a = new IntArray(size);
        a.init(75);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestLoops::steppedLoop4, a, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i += 4) {
            assertEquals(200, a.get(i));
            for (int j = (i + 1); j < (i + 4) && j < size; j++) {
                assertEquals(75, a.get(j));
            }
        }
    }

    @Test
    public void testStepLoop5() throws TornadoExecutionPlanException {
        final int size = 512;

        IntArray a = new IntArray(size);
        a.init(75);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestLoops::steppedLoop5, a, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i += 3) {
            assertEquals(200, a.get(i));
            for (int j = (i + 1); j < (i + 3) && j < size; j++) {
                assertEquals(75, a.get(j));
            }
        }
    }

    @Test
    public void testStepLoop7() throws TornadoExecutionPlanException {
        final int size = 512;

        IntArray a = new IntArray(size);
        a.init(75);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestLoops::steppedLoop7, a, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i += 7) {
            assertEquals(200, a.get(i));
            for (int j = (i + 1); j < (i + 7) && j < size; j++) {
                assertEquals(75, a.get(j));
            }
        }
    }

    @Test
    public void testStepLoop10() throws TornadoExecutionPlanException {
        final int size = 2048;

        IntArray a = new IntArray(size);
        a.init(75);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestLoops::steppedLoop10, a, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i += 10) {
            assertEquals(200, a.get(i));
            for (int j = (i + 1); j < (i + 10) && j < size; j++) {
                assertEquals(75, a.get(j));
            }
        }
    }

    @Test
    public void testIfInsideForLoop() throws TornadoExecutionPlanException {
        final int size = 10;

        IntArray a = new IntArray(size);
        a.init(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestLoops::conditionalInLoop, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            if (i == 4) {
                assertEquals(4, a.get(i));
            } else {
                assertEquals(10, a.get(i));
            }
        }
    }

    @Test
    public void testIfInsideForLoop2() throws TornadoExecutionPlanException {
        final int size = 10;

        IntArray a = new IntArray(size);
        a.init(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestLoops::conditionalInLoop2, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
        for (int i = 0; i < a.getSize(); i++) {
            if (i == 4) {
                assertEquals(1, a.get(i));
            } else {
                assertEquals(10, a.get(i));
            }
        }
    }

    @Test
    public void testIfElseElseInLoop() throws TornadoExecutionPlanException {
        final int size = 10;
        IntArray a = new IntArray(size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestLoops::conditionalIfElseLoop, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            if (i == 4) {
                assertEquals(4, a.get(i));
            } else if (i == 5) {
                assertEquals(5, a.get(i));
            } else {
                assertEquals(10, a.get(i));
            }
        }
    }

    /**
     * TornadoVM does not support 2D primite arrays.
     * 
     * @throws TornadoExecutionPlanException
     */
    @Ignore
    public void testTwoDLoopTwoDArray() throws TornadoExecutionPlanException {
        final int size = 10;

        int[][] a = new int[size][size];

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestLoops::twoDLoop, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int[] ints : a) {
            for (int anInt : ints) {
                assertEquals(10, anInt);
            }
        }
    }

    @Test
    public void testNestedForLoopOneDArray() throws TornadoExecutionPlanException {
        final int size = 10;

        IntArray a = new IntArray(size * size);
        a.init(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestLoops::nestedForLoopOneDArray, a, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                assertEquals(10, a.get(i * size + j));
            }
        }
    }

    @Ignore
    public void testNestedForLoopTwoDArray() throws TornadoExecutionPlanException {
        final int size = 10;

        int[][] a = new int[size][size];

        for (int i = 0; i < size; i++) {
            Arrays.fill(a[i], 1);
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestLoops::nestedForLoopTwoDArray, a, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int[] ints : a) {
            for (int anInt : ints) {
                assertEquals(10, anInt);
            }
        }
    }

    /*
     * This test is failing, the reason is that the runtime does not copy in the
     * variable a, just copy out
     *
     */
    @Test
    public void testLoopControlFlowBreak() throws TornadoExecutionPlanException {
        final int size = 10;

        IntArray a = new IntArray(size);
        a.init(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestLoops::controlFlowBreak, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            if (i == 4) {
                assertEquals(4, a.get(i));
            } else {
                assertEquals(1, a.get(i));
            }
        }
    }

    @Test
    public void testLoopControlFlowBreak2() throws TornadoExecutionPlanException {
        final int size = 10;

        IntArray a = new IntArray(size);
        a.init(1000);

        a.set(2, 2);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestLoops::controlFlowBreak2, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            if (i == 2) {
                assertEquals(10, a.get(i));
            } else {
                assertEquals(1000, a.get(i));
            }
        }
    }

    @Test
    public void testLoopControlFlowContinue() throws TornadoExecutionPlanException {
        final int size = 10;
        IntArray foo = new IntArray(size);
        foo.init(50);

        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.EVERY_EXECUTION, foo) //
                .task("t0", TestLoops::controlFlowContinue, foo) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, foo);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < foo.getSize(); i++) {
            if (i == 4) {
                assertEquals(50, foo.get(i));
            } else {
                assertEquals(150, foo.get(i));
            }
        }
    }

    @Test
    public void testNestedForLoopOneDArray2() throws TornadoExecutionPlanException {
        final int size = 10;

        IntArray a = new IntArray(size * size);
        a.init(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestLoops::nested2ParallelLoops, a, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                assertEquals(10, a.get(i * size + j));
            }
        }
    }

    @Test
    public void testInnerWhileLoop() throws TornadoExecutionPlanException {
        final int size = 100;

        IntArray a = new IntArray(size * size);
        a.init(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestLoops::whileLoop, a, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            int y = 0;
            while (y < size) {
                assertEquals(10, a.get(i * size + y));
                y++;
            }
        }
    }

    @Ignore
    public void testInnerDoWhileLoop() throws TornadoExecutionPlanException {
        final int size = 100;

        IntArray a = new IntArray(size * size);
        a.init(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestLoops::dowWhileLoop, a, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            int y = 0;
            while (y < size) {
                assertEquals(10, a.get(i * size + y));
                y++;
            }
        }
    }

    @Test
    public void testInnerForEach() throws TornadoExecutionPlanException {
        final int size = 10;

        int[] a = new int[size];
        int[] c = new int[size];
        Arrays.fill(a, 1);
        Arrays.fill(c, 0);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestLoops::forEach, a, c, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(2, c[i]);
        }
    }

    @TornadoNotSupported
    public void testReverseOrderLoops() throws TornadoExecutionPlanException {
        final int size = 10;

        IntArray a = new IntArray(size);
        a.init(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestLoops::reverseLoop, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int j = 0; j < size; j++) {
            assertEquals(10, a.get(j));
        }
    }

    /**
     * Make sure that at the last iteration of the loop, the address computed in
     * {@link #getNumber} has been updated with the latest value of the induction
     * variable, in order to be used in the if condition.
     */
    @Test
    public void testSingleThreadLoopCondition() throws TornadoExecutionPlanException {

        int size = 1024;
        IntArray inTor = new IntArray(size);
        IntArray outTor = new IntArray(size);
        IntArray inSeq = new IntArray(size);
        IntArray outSeq = new IntArray(size);
        for (int i = 0; i < size; i++) {
            inTor.set(i, i);
            outTor.set(i, i);
            inSeq.set(i, i);
            outSeq.set(i, i);
        }

        inTor.set(0, size / 4 - 1);
        inSeq.set(0, size / 4 - 1);
        // inTor[0] = inSeq[0] = size / 4 - 1;

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, inTor) //
                .task("t0", TestLoops::testSingleThreadLoopCond, inTor, outTor) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, inTor, outTor);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testSingleThreadLoopCond(inSeq, outSeq);

        for (int i = 0; i < size; i++) {
            assertEquals(outSeq.get(i), outTor.get(i));
        }
    }

    @Test
    public void testMultipleThreadLoopCondition() throws TornadoExecutionPlanException {
        // Same test as testSingleThreadLoopCondition, but in parallel.
        int size = 1024;

        IntArray inTor = new IntArray(size);
        IntArray outTor = new IntArray(size);
        IntArray inSeq = new IntArray(size);
        IntArray outSeq = new IntArray(size);
        for (int i = 0; i < size; i++) {
            inTor.set(i, i);
            outTor.set(i, i);
            inSeq.set(i, i);
            outSeq.set(i, i);
        }

        inTor.set(0, size / 4 - 1);
        inSeq.set(0, size / 4 - 1);
        // inTor[0] = inSeq[0] = size / 4 - 1;

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, inTor) //
                .task("t0", TestLoops::testMultipleThreadLoopCond, inTor, outTor) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, inTor, outTor); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
        testMultipleThreadLoopCond(inSeq, outSeq);

        for (int i = 0; i < size; i++) {
            assertEquals(outSeq.get(i), outTor.get(i));
        }
        //Assert.assertArrayEquals(outSeq, outTor);
    }
}
