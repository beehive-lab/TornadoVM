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
package uk.ac.manchester.tornado.unittests.arrays;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Regression tests for writing a {@code new HalfFloat(...)} straight into a {@link HalfFloatArray}.
 * When the constructor argument is a computed expression (rather than a local variable or a plain
 * array read) the {@code NewHalfFloatInstance} carrier is no longer the store's control-flow
 * predecessor, and the HalfFloat replacement phase used to leave it in the graph, failing code
 * generation with {@code node is not LIRLowerable: NewHalfFloatInstance}.
 *
 * <p>How to run: {@code tornado-test -V uk.ac.manchester.tornado.unittests.arrays.TestHalfFloatInlineWrite}</p>
 */
public class TestHalfFloatInlineWrite extends TornadoTestBase {

    // Local variable, computed input.
    public static void localComputed(FloatArray a, HalfFloatArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            float v = a.get(i) * 2.0f;
            HalfFloat h = new HalfFloat(v);
            out.set(i, h);
        }
    }

    // Inline new HalfFloat directly in set(), direct read input.
    public static void inlineDirect(FloatArray a, HalfFloatArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, new HalfFloat(a.get(i)));
        }
    }

    // Inline new HalfFloat in set(), computed input.
    public static void inlineComputed(FloatArray a, HalfFloatArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, new HalfFloat(a.get(i) * 2.0f));
        }
    }

    private static FloatArray input(int n) {
        FloatArray a = new FloatArray(n);
        for (int i = 0; i < n; i++) {
            a.set(i, i * 0.5f - 8.0f);
        }
        return a;
    }

    private static void check(FloatArray a, HalfFloatArray out, boolean doubled) {
        for (int i = 0; i < a.getSize(); i++) {
            float expected = doubled ? a.get(i) * 2.0f : a.get(i);
            assertEquals(expected, out.get(i).getFloat32(), 1e-2f * Math.max(1.0f, Math.abs(expected)));
        }
    }

    @Test
    public void testLocalComputed() throws TornadoExecutionPlanException {
        FloatArray a = input(64);
        HalfFloatArray out = new HalfFloatArray(64);
        TaskGraph tg = new TaskGraph("lc").transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestHalfFloatInlineWrite::localComputed, a, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.execute();
        }
        check(a, out, true);
    }

    @Test
    public void testInlineDirect() throws TornadoExecutionPlanException {
        FloatArray a = input(64);
        HalfFloatArray out = new HalfFloatArray(64);
        TaskGraph tg = new TaskGraph("id").transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestHalfFloatInlineWrite::inlineDirect, a, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.execute();
        }
        check(a, out, false);
    }

    @Test
    public void testInlineComputed() throws TornadoExecutionPlanException {
        FloatArray a = input(64);
        HalfFloatArray out = new HalfFloatArray(64);
        TaskGraph tg = new TaskGraph("ic").transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestHalfFloatInlineWrite::inlineComputed, a, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.execute();
        }
        check(a, out, true);
    }
}
