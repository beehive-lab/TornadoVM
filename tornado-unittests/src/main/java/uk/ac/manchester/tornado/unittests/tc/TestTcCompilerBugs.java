/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.tc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Regression tests for TC compiler bugs found by the PR #1228 validation (TC-1 ... TC-10). Each test
 * compares the device result with the same method run on the JVM.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tc.TestTcCompilerBugs
 * </code>
 */
public class TestTcCompilerBugs extends TornadoTestBase {

    private static final int N = 64;

    private static void runOnDevice(String name, TaskGraph graph) throws TornadoExecutionPlanException {
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
    }

    private static void assertSame(IntArray expected, IntArray actual) {
        for (int i = 0; i < expected.getSize(); i++) {
            assertEquals(expected.get(i), actual.get(i), "index " + i);
        }
    }

    // ---------------------------------------------------------------- TC-1

    public static void privateArrayInLoop(IntArray out) {
        for (@Parallel int id = 0; id < out.getSize(); id++) {
            int s = 0;
            for (int i = 0; i < 8; i++) {
                int[] t = new int[2];
                t[0] += i + id;
                t[1] += 1;
                s += t[0] * 10 + t[1];
            }
            out.set(id, s);
        }
    }

    /** TC-1: {@code new int[2]} inside a loop starts from zero on every iteration. */
    @Test
    public void testPrivateArrayAllocatedInLoopIsZeroedEachIteration() throws TornadoExecutionPlanException {
        IntArray out = new IntArray(N);
        runOnDevice("tc1", new TaskGraph("s0") //
                .task("t0", TestTcCompilerBugs::privateArrayInLoop, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        IntArray expected = new IntArray(N);
        privateArrayInLoop(expected);
        assertSame(expected, out);
    }

    // ---------------------------------------------------------------- TC-2

    public static void unrollWrappingInduction(IntArray out) {
        for (@Parallel int id = 0; id < out.getSize(); id++) {
            int cnt = 0;
            for (int i = 0; i < 2000000000; i += 1500000000) {
                cnt++;
            }
            out.set(id, cnt + id);
        }
    }

    /** TC-2: a fully unrolled loop counts its trips with the induction variable's int wrap-around. */
    @Test
    public void testFullUnrollWrapsInductionVariable() throws TornadoExecutionPlanException {
        IntArray out = new IntArray(N);
        runOnDevice("tc2", new TaskGraph("s0") //
                .task("t0", TestTcCompilerBugs::unrollWrappingInduction, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        IntArray expected = new IntArray(N);
        unrollWrappingInduction(expected);
        assertSame(expected, out);
    }

    // ---------------------------------------------------------------- TC-3

    public static void floatRemainder(FloatArray a, FloatArray out) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            out.set(i, a.get(i) % 0.75f);
        }
    }

    public static void doubleRemainder(DoubleArray a, DoubleArray out) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            out.set(i, a.get(i) % 0.75);
        }
    }

    /** TC-3: float {@code %} compiles on OpenCL and Metal (no {@code fmodf} there) and matches Java. */
    @Test
    public void testFloatRemainder() throws TornadoExecutionPlanException {
        FloatArray a = new FloatArray(N);
        for (int i = 0; i < N; i++) {
            a.set(i, (i - N / 2) * 0.37f);
        }
        FloatArray out = new FloatArray(N);
        runOnDevice("tc3", new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestTcCompilerBugs::floatRemainder, a, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        FloatArray expected = new FloatArray(N);
        floatRemainder(a, expected);
        for (int i = 0; i < N; i++) {
            assertEquals(Float.floatToRawIntBits(expected.get(i)), Float.floatToRawIntBits(out.get(i)), "index " + i);
        }
    }

    /** TC-3: double {@code %} keeps working. */
    @Test
    public void testDoubleRemainder() throws TornadoExecutionPlanException {
        DoubleArray a = new DoubleArray(N);
        for (int i = 0; i < N; i++) {
            a.set(i, (i - N / 2) * 0.37);
        }
        DoubleArray out = new DoubleArray(N);
        runOnDevice("tc3d", new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestTcCompilerBugs::doubleRemainder, a, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        DoubleArray expected = new DoubleArray(N);
        doubleRemainder(a, expected);
        for (int i = 0; i < N; i++) {
            assertEquals(Double.doubleToRawLongBits(expected.get(i)), Double.doubleToRawLongBits(out.get(i)), "index " + i);
        }
    }
}
