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
}
