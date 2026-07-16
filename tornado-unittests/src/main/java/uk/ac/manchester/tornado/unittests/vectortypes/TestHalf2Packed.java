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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.vectors.Half2;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Tests for the packed half2 accessors ({@link HalfFloatArray#getHalf2(int)},
 * {@link HalfFloatArray#setHalf2(int, Half2)}) and the {@link Half2}
 * fma/conversion helpers.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.vectortypes.TestHalf2Packed
 * </code>
 */
public class TestHalf2Packed extends TornadoTestBase {

    public static final int NUM_ELEMENTS = 4096;

    private static final float DELTA = 0.01f;

    public static void copyPairs(HalfFloatArray input, HalfFloatArray output) {
        for (@Parallel int i = 0; i < input.getSize() / 2; i++) {
            output.setHalf2(i * 2, input.getHalf2(i * 2));
        }
    }

    public static void addPairs(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        for (@Parallel int i = 0; i < a.getSize() / 2; i++) {
            c.setHalf2(i * 2, Half2.add(a.getHalf2(i * 2), b.getHalf2(i * 2)));
        }
    }

    public static void multPairs(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        for (@Parallel int i = 0; i < a.getSize() / 2; i++) {
            c.setHalf2(i * 2, Half2.mult(a.getHalf2(i * 2), b.getHalf2(i * 2)));
        }
    }

    public static void fmaPairs(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c, HalfFloatArray d) {
        for (@Parallel int i = 0; i < a.getSize() / 2; i++) {
            d.setHalf2(i * 2, Half2.fma(a.getHalf2(i * 2), b.getHalf2(i * 2), c.getHalf2(i * 2)));
        }
    }

    /**
     * Attention-style pattern: packed FP16 loads, FP32 accumulation of the pairwise products.
     */
    public static void dotRowsFloatAccumulate(HalfFloatArray a, HalfFloatArray b, FloatArray result, int rowSize) {
        for (@Parallel int row = 0; row < result.getSize(); row++) {
            float acc = 0.0f;
            int base = row * rowSize;
            for (int d = 0; d < rowSize; d += 2) {
                Half2 pa = a.getHalf2(base + d);
                Half2 pb = b.getHalf2(base + d);
                acc += Half2.lowFloat(pa) * Half2.lowFloat(pb);
                acc += Half2.highFloat(pa) * Half2.highFloat(pb);
            }
            result.set(row, acc);
        }
    }

    /**
     * RoPE-style pattern: compute a rotated pair in FP32, pack and store with one 32-bit write.
     */
    public static void packRotatedPairs(FloatArray input, HalfFloatArray output) {
        for (@Parallel int i = 0; i < input.getSize() / 2; i++) {
            float x = input.get(i * 2);
            float y = input.get(i * 2 + 1);
            output.setHalf2(i * 2, Half2.fromFloats(x - y, x + y));
        }
    }

    /**
     * Local-memory tile pattern: stage packed pairs into a __half2 shared-memory
     * tile, then consume them with FP32 accumulation after a barrier.
     */
    public static void localTileDot(KernelContext context, HalfFloatArray a, HalfFloatArray b, FloatArray result) {
        Half2[] tile = context.allocateHalf2LocalArray(128);
        int localId = context.localIdx;
        int globalId = context.globalIdx;
        tile[localId] = a.getHalf2(globalId * 2);
        context.localBarrier();
        Half2 pa = tile[localId];
        Half2 pb = b.getHalf2(globalId * 2);
        result.set(globalId, Half2.lowFloat(pa) * Half2.lowFloat(pb) + Half2.highFloat(pa) * Half2.highFloat(pb));
    }

    private static HalfFloatArray createSequenceArray(int numElements, float scale) {
        HalfFloatArray array = new HalfFloatArray(numElements);
        for (int i = 0; i < numElements; i++) {
            array.set(i, new HalfFloat((i % 32) * scale));
        }
        return array;
    }

    @Test
    public void testPackedCopy() throws TornadoExecutionPlanException {
        HalfFloatArray input = createSequenceArray(NUM_ELEMENTS, 0.5f);
        HalfFloatArray output = new HalfFloatArray(NUM_ELEMENTS);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestHalf2Packed::copyPairs, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < NUM_ELEMENTS; i++) {
            assertEquals(input.get(i).getFloat32(), output.get(i).getFloat32(), 0.0f);
        }
    }

    @Test
    public void testPackedAdd() throws TornadoExecutionPlanException {
        HalfFloatArray a = createSequenceArray(NUM_ELEMENTS, 0.25f);
        HalfFloatArray b = createSequenceArray(NUM_ELEMENTS, 0.5f);
        HalfFloatArray c = new HalfFloatArray(NUM_ELEMENTS);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHalf2Packed::addPairs, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < NUM_ELEMENTS; i++) {
            assertEquals(a.get(i).getFloat32() + b.get(i).getFloat32(), c.get(i).getFloat32(), DELTA);
        }
    }

    @Test
    public void testPackedMult() throws TornadoExecutionPlanException {
        HalfFloatArray a = createSequenceArray(NUM_ELEMENTS, 0.25f);
        HalfFloatArray b = createSequenceArray(NUM_ELEMENTS, 0.5f);
        HalfFloatArray c = new HalfFloatArray(NUM_ELEMENTS);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHalf2Packed::multPairs, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < NUM_ELEMENTS; i++) {
            assertEquals(a.get(i).getFloat32() * b.get(i).getFloat32(), c.get(i).getFloat32(), DELTA * 16);
        }
    }

    @Test
    public void testPackedFma() throws TornadoExecutionPlanException {
        HalfFloatArray a = createSequenceArray(NUM_ELEMENTS, 0.25f);
        HalfFloatArray b = createSequenceArray(NUM_ELEMENTS, 0.5f);
        HalfFloatArray c = createSequenceArray(NUM_ELEMENTS, 0.125f);
        HalfFloatArray d = new HalfFloatArray(NUM_ELEMENTS);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b, c) //
                .task("t0", TestHalf2Packed::fmaPairs, a, b, c, d) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, d);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < NUM_ELEMENTS; i++) {
            float expected = a.get(i).getFloat32() * b.get(i).getFloat32() + c.get(i).getFloat32();
            assertEquals(expected, d.get(i).getFloat32(), DELTA * 16);
        }
    }

    @Test
    public void testDotRowsFloatAccumulate() throws TornadoExecutionPlanException {
        final int rowSize = 128;
        final int numRows = NUM_ELEMENTS / rowSize;
        HalfFloatArray a = createSequenceArray(NUM_ELEMENTS, 0.25f);
        HalfFloatArray b = createSequenceArray(NUM_ELEMENTS, 0.5f);
        FloatArray result = new FloatArray(numRows);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHalf2Packed::dotRowsFloatAccumulate, a, b, result, rowSize) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int row = 0; row < numRows; row++) {
            float expected = 0.0f;
            for (int d = 0; d < rowSize; d++) {
                expected += a.get(row * rowSize + d).getFloat32() * b.get(row * rowSize + d).getFloat32();
            }
            assertEquals(expected, result.get(row), Math.abs(expected) * 0.01f + DELTA);
        }
    }

    @Test
    public void testLocalTileDot() throws TornadoExecutionPlanException {
        final int numPairs = NUM_ELEMENTS / 2;
        HalfFloatArray a = createSequenceArray(NUM_ELEMENTS, 0.25f);
        HalfFloatArray b = createSequenceArray(NUM_ELEMENTS, 0.5f);
        FloatArray result = new FloatArray(numPairs);

        KernelContext context = new KernelContext();
        WorkerGrid workerGrid = new WorkerGrid1D(numPairs);
        workerGrid.setLocalWork(128, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHalf2Packed::localTileDot, context, a, b, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        for (int i = 0; i < numPairs; i++) {
            float expected = a.get(i * 2).getFloat32() * b.get(i * 2).getFloat32() + a.get(i * 2 + 1).getFloat32() * b.get(i * 2 + 1).getFloat32();
            assertEquals(expected, result.get(i), Math.abs(expected) * 0.01f + DELTA);
        }
    }

    @Test
    public void testPackRotatedPairs() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(NUM_ELEMENTS);
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            input.set(i, (i % 64) * 0.125f);
        }
        HalfFloatArray output = new HalfFloatArray(NUM_ELEMENTS);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestHalf2Packed::packRotatedPairs, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < NUM_ELEMENTS / 2; i++) {
            float x = input.get(i * 2);
            float y = input.get(i * 2 + 1);
            assertEquals(x - y, output.get(i * 2).getFloat32(), DELTA);
            assertEquals(x + y, output.get(i * 2 + 1).getFloat32(), DELTA);
        }
    }
}
