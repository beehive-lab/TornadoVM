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

package uk.ac.manchester.tornado.unittests.compiler;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Graal turns a long enough chain of {@code x == constant} tests into an {@code IntegerSwitchNode},
 * and then chooses between three lowerings by key density. The backends emit source code, where a
 * jump table has no meaning, so all three have to produce the same {@code switch} statement.
 *
 * <p>
 * These tests cover the dense case, which selects the range-table lowering. When that lowering
 * emits nothing, the case labels - which come from the structured control-flow pass reading the
 * HIR - are left without an enclosing switch, the kernel fails to build, and execution silently
 * falls back to the host.
 * </p>
 *
 * <p>
 * How to run?
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.compiler.TestSwitchLowering
 * </code>
 * </p>
 */
public class TestSwitchLowering extends TornadoTestBase {

    private static final int NUM_ELEMENTS = 256;

    /** Sixteen dense keys written as an if-else chain, which Graal converts to a switch. */
    public static void chain16(FloatArray out, FloatArray in, IntArray selector, int n) {
        int op = selector.get(0);
        for (@Parallel int i = 0; i < n; i++) {
            float a = in.get(i);
            float r;
            if (op == 0) {
                r = a + 0.0f;
            } else if (op == 1) {
                r = a + 1.0f;
            } else if (op == 2) {
                r = a + 2.0f;
            } else if (op == 3) {
                r = a + 3.0f;
            } else if (op == 4) {
                r = a + 4.0f;
            } else if (op == 5) {
                r = a + 5.0f;
            } else if (op == 6) {
                r = a + 6.0f;
            } else if (op == 7) {
                r = a + 7.0f;
            } else if (op == 8) {
                r = a + 8.0f;
            } else if (op == 9) {
                r = a + 9.0f;
            } else if (op == 10) {
                r = a + 10.0f;
            } else if (op == 11) {
                r = a + 11.0f;
            } else if (op == 12) {
                r = a + 12.0f;
            } else if (op == 13) {
                r = a + 13.0f;
            } else if (op == 14) {
                r = a + 14.0f;
            } else if (op == 15) {
                r = a + 15.0f;
            } else {
                r = -1.0f;
            }
            out.set(i, r);
        }
    }

    /** The same shape written as a switch statement. */
    public static void switch16(FloatArray out, FloatArray in, IntArray selector, int n) {
        int op = selector.get(0);
        for (@Parallel int i = 0; i < n; i++) {
            float a = in.get(i);
            float r;
            switch (op) {
                case 0 -> r = a * 1.0f;
                case 1 -> r = a * 2.0f;
                case 2 -> r = a * 3.0f;
                case 3 -> r = a * 4.0f;
                case 4 -> r = a * 5.0f;
                case 5 -> r = a * 6.0f;
                case 6 -> r = a * 7.0f;
                case 7 -> r = a * 8.0f;
                case 8 -> r = a * 9.0f;
                case 9 -> r = a * 10.0f;
                case 10 -> r = a * 11.0f;
                case 11 -> r = a * 12.0f;
                case 12 -> r = a * 13.0f;
                case 13 -> r = a * 14.0f;
                case 14 -> r = a * 15.0f;
                case 15 -> r = a * 16.0f;
                default -> r = -1.0f;
            }
            out.set(i, r);
        }
    }

    @Test
    public void testDenseIfElseChain() throws TornadoExecutionPlanException {
        FloatArray in = new FloatArray(NUM_ELEMENTS);
        FloatArray out = new FloatArray(NUM_ELEMENTS);
        IntArray selector = new IntArray(1);
        in.init(10.0f);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in, selector) //
                .task("t0", TestSwitchLowering::chain16, out, in, selector, NUM_ELEMENTS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            for (int op = 0; op < 16; op++) {
                selector.set(0, op);
                out.init(Float.NaN);
                executionPlan.execute();
                for (int i = 0; i < NUM_ELEMENTS; i++) {
                    assertEquals("op " + op + ", element " + i, 10.0f + op, out.get(i), 0.0f);
                }
            }
        }
    }

    @Test
    public void testDenseSwitchStatement() throws TornadoExecutionPlanException {
        FloatArray in = new FloatArray(NUM_ELEMENTS);
        FloatArray out = new FloatArray(NUM_ELEMENTS);
        IntArray selector = new IntArray(1);
        in.init(2.0f);

        TaskGraph taskGraph = new TaskGraph("s1") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in, selector) //
                .task("t0", TestSwitchLowering::switch16, out, in, selector, NUM_ELEMENTS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            for (int op = 0; op < 16; op++) {
                selector.set(0, op);
                out.init(Float.NaN);
                executionPlan.execute();
                for (int i = 0; i < NUM_ELEMENTS; i++) {
                    assertEquals("op " + op + ", element " + i, 2.0f * (op + 1), out.get(i), 0.0f);
                }
            }
        }
    }

    /** The default arm must still be reachable when the keys are dense. */
    @Test
    public void testDefaultArm() throws TornadoExecutionPlanException {
        FloatArray in = new FloatArray(NUM_ELEMENTS);
        FloatArray out = new FloatArray(NUM_ELEMENTS);
        IntArray selector = new IntArray(1);
        in.init(10.0f);

        TaskGraph taskGraph = new TaskGraph("s2") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in, selector) //
                .task("t0", TestSwitchLowering::chain16, out, in, selector, NUM_ELEMENTS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            selector.set(0, 99);
            executionPlan.execute();
            for (int i = 0; i < NUM_ELEMENTS; i++) {
                assertEquals("element " + i, -1.0f, out.get(i), 0.0f);
            }
        }
    }
}
