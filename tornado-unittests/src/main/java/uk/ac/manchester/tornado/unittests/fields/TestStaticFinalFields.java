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
package uk.ac.manchester.tornado.unittests.fields;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Kernels reading {@code static final} fields whose values the class initialiser computes (javac
 * inlines only compile-time constants), as in {@code static final double TO_RADIANS = Math.PI / 180}.
 * The kernel sees them as constants.
 *
 * <p>How to run:</p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.fields.TestStaticFinalFields
 * </code>
 */
public class TestStaticFinalFields extends TornadoTestBase {

    static final class Angle {
        static final double TO_RADIANS = Math.PI / 180;
        static final float HALF = (float) Math.sqrt(0.25);
        static final int SCALE = Integer.parseInt("3");
        static final long BIG = Long.parseLong("10000000000");
        static final boolean NEGATE = Boolean.parseBoolean("true");
        static final char OFFSET = "A".charAt(0);
    }

    private static void doubles(DoubleArray degrees, DoubleArray radians) {
        for (@Parallel int i = 0; i < degrees.getSize(); i++) {
            radians.set(i, degrees.get(i) * Angle.TO_RADIANS);
        }
    }

    private static void mixed(IntArray in, FloatArray f, IntArray n, LongArray l) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            int x = in.get(i);
            f.set(i, x * Angle.HALF);
            n.set(i, (Angle.NEGATE ? -x : x) * Angle.SCALE + Angle.OFFSET);
            l.set(i, x + Angle.BIG);
        }
    }

    @Test
    public void testComputedDouble() throws TornadoExecutionPlanException {
        final int size = 360;
        DoubleArray degrees = new DoubleArray(size);
        DoubleArray radians = new DoubleArray(size);
        for (int i = 0; i < size; i++) {
            degrees.set(i, i);
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, degrees) //
                .task("t0", TestStaticFinalFields::doubles, degrees, radians) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, radians);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
        for (int i = 0; i < size; i++) {
            assertEquals(i * Angle.TO_RADIANS, radians.get(i), 1e-12);
        }
    }

    @Test
    public void testComputedOtherTypes() throws TornadoExecutionPlanException {
        final int size = 256;
        IntArray in = new IntArray(size);
        FloatArray f = new FloatArray(size);
        IntArray n = new IntArray(size);
        LongArray l = new LongArray(size);
        for (int i = 0; i < size; i++) {
            in.set(i, i - 100);
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                .task("t0", TestStaticFinalFields::mixed, in, f, n, l) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, f, n, l);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
        for (int i = 0; i < size; i++) {
            int x = i - 100;
            assertEquals(x * Angle.HALF, f.get(i), 0.0f);
            assertEquals(-x * Angle.SCALE + 'A', n.get(i));
            assertEquals(x + Angle.BIG, l.get(i));
        }
    }
}
