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
package uk.ac.manchester.tornado.unittests.vectortypes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.vectors.Float2;
import uk.ac.manchester.tornado.api.types.vectors.Float3;
import uk.ac.manchester.tornado.api.types.vectors.Float4;
import uk.ac.manchester.tornado.api.types.vectors.Float8;
import uk.ac.manchester.tornado.api.types.vectors.Int3;
import uk.ac.manchester.tornado.api.types.vectors.Int4;
import uk.ac.manchester.tornado.api.types.vectors.Int8;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Parts of a vector as a new vector: {@code asFloat2()}, {@code asFloat3()}, {@code getLow()} and
 * {@code getHigh()} (KFusion's depth2vertex uses {@code Float4.asFloat3()}).
 *
 * <p>How to run:</p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.vectortypes.TestVectorSlices
 * </code>
 */
public class TestVectorSlices extends TornadoTestBase {

    private static void floats(FloatArray in, FloatArray out) {
        for (@Parallel int i = 0; i < in.getSize() / 8; i++) {
            int b = 8 * i;
            Float8 v = new Float8(in.get(b), in.get(b + 1), in.get(b + 2), in.get(b + 3), in.get(b + 4), in.get(b + 5), in.get(b + 6), in.get(b + 7));
            Float4 high = v.getHigh();
            Float4 low = v.getLow();
            Float3 xyz = high.asFloat3();
            Float2 xy = low.asFloat2();
            Float2 zw = low.getHigh();
            out.set(b, xyz.getX() + xy.getY());
            out.set(b + 1, xyz.getY() * 2);
            out.set(b + 2, xyz.getZ() - zw.getX());
            out.set(b + 3, zw.getY() + xy.getX());
            out.set(b + 4, Float3.dot(xyz, new Float3(1, 2, 3)));
            out.set(b + 5, xyz.asFloat2().getY());
            out.set(b + 6, low.getLow().getX());
            out.set(b + 7, high.getHigh().getY());
        }
    }

    private static void ints(IntArray in, IntArray out) {
        for (@Parallel int i = 0; i < in.getSize() / 8; i++) {
            int b = 8 * i;
            Int8 v = new Int8(in.get(b), in.get(b + 1), in.get(b + 2), in.get(b + 3), in.get(b + 4), in.get(b + 5), in.get(b + 6), in.get(b + 7));
            Int4 high = v.getHigh();
            Int3 xyz = v.getLow().asInt3();
            out.set(b, xyz.getX());
            out.set(b + 1, xyz.getY());
            out.set(b + 2, xyz.getZ());
            out.set(b + 3, high.asInt2().getX());
            out.set(b + 4, high.asInt2().getY());
            out.set(b + 5, high.asInt3().getZ());
            out.set(b + 6, high.getW());
            out.set(b + 7, xyz.asInt2().getX() + xyz.asInt2().getY());
        }
    }

    @Test
    public void testFloatSlices() throws TornadoExecutionPlanException {
        final int size = 8 * 256;
        FloatArray in = new FloatArray(size);
        FloatArray out = new FloatArray(size);
        FloatArray expected = new FloatArray(size);
        for (int i = 0; i < size; i++) {
            in.set(i, i * 0.5f - 300);
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                .task("t0", TestVectorSlices::floats, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
        floats(in, expected);
        for (int i = 0; i < size; i++) {
            assertEquals(expected.get(i), out.get(i), 1e-3f, "element " + i);
        }
    }

    @Test
    public void testIntSlices() throws TornadoExecutionPlanException {
        final int size = 8 * 256;
        IntArray in = new IntArray(size);
        IntArray out = new IntArray(size);
        IntArray expected = new IntArray(size);
        for (int i = 0; i < size; i++) {
            in.set(i, i * 7 - 5000);
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                .task("t0", TestVectorSlices::ints, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
        ints(in, expected);
        for (int i = 0; i < size; i++) {
            assertEquals(expected.get(i), out.get(i), "element " + i);
        }
    }
}
