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
package uk.ac.manchester.tornado.unittests.tile;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.tile.DType;
import uk.ac.manchester.tornado.api.tile.PartitionView;
import uk.ac.manchester.tornado.api.tile.Tile;
import uk.ac.manchester.tornado.api.tile.TileContext;
import uk.ac.manchester.tornado.api.types.FP8;
import uk.ac.manchester.tornado.api.types.arrays.FP8Array;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;

/** FP8 arithmetic must retain its format across operations, stores and loop exits. */
public class TestTileFP8Arithmetic extends TornadoTestBase {

    private static final int SIZE = 32;
    private static final int OPERATIONS = 10;

    @Before
    public void requireTileSupport() {
        TileSupport.requireTileSupport();
    }

    public static void arithmeticE4M3(TileContext tc, FP8Array a, FP8Array b, FP8Array out,
            FloatArray reduced, FloatArray chained, IntArray iterations) {
        PartitionView av = tc.partition(tc.view(a, DType.FP8_E4M3, SIZE), SIZE);
        PartitionView bv = tc.partition(tc.view(b, DType.FP8_E4M3, SIZE), SIZE);
        PartitionView ov = tc.partition(tc.view(out, DType.FP8_E4M3, SIZE * OPERATIONS), SIZE);
        PartitionView rv = tc.partition(tc.view(reduced, 3), 1);
        PartitionView cv = tc.partition(tc.view(chained, SIZE), SIZE);
        Tile x = av.load(0);
        Tile y = bv.load(0);
        ov.store(tc.add(x, y), 0);
        ov.store(tc.sub(x, y), 1);
        ov.store(tc.mul(x, y), 2);
        ov.store(tc.div(x, y), 3);
        ov.store(tc.maximum(x, y), 4);
        ov.store(tc.minimum(x, y), 5);
        ov.store(tc.exp(x), 6);
        ov.store(tc.scale(x, 1.25), 7);
        ov.store(tc.fma(x, y, y), 8);
        ov.store(tc.prefixSum(x, 0), 9);
        rv.store(tc.cast(tc.sum(x, 0), DType.F32), 0);
        rv.store(tc.cast(tc.max(x, 0), DType.F32), 1);
        rv.store(tc.cast(tc.min(x, 0), DType.F32), 2);
        Tile acc = x;
        for (int i = 0; i < iterations.get(0); i++) {
            acc = tc.add(acc, y);
        }
        cv.store(tc.cast(acc, DType.F32), 0);
    }

    public static void arithmeticE5M2(TileContext tc, FP8Array a, FP8Array b, FP8Array out,
            FloatArray reduced, FloatArray chained, IntArray iterations) {
        PartitionView av = tc.partition(tc.view(a, DType.FP8_E5M2, SIZE), SIZE);
        PartitionView bv = tc.partition(tc.view(b, DType.FP8_E5M2, SIZE), SIZE);
        PartitionView ov = tc.partition(tc.view(out, DType.FP8_E5M2, SIZE * OPERATIONS), SIZE);
        PartitionView rv = tc.partition(tc.view(reduced, 3), 1);
        PartitionView cv = tc.partition(tc.view(chained, SIZE), SIZE);
        Tile x = av.load(0);
        Tile y = bv.load(0);
        ov.store(tc.add(x, y), 0);
        ov.store(tc.sub(x, y), 1);
        ov.store(tc.mul(x, y), 2);
        ov.store(tc.div(x, y), 3);
        ov.store(tc.maximum(x, y), 4);
        ov.store(tc.minimum(x, y), 5);
        ov.store(tc.exp(x), 6);
        ov.store(tc.scale(x, 1.25), 7);
        ov.store(tc.fma(x, y, y), 8);
        ov.store(tc.prefixSum(x, 0), 9);
        rv.store(tc.cast(tc.sum(x, 0), DType.F32), 0);
        rv.store(tc.cast(tc.max(x, 0), DType.F32), 1);
        rv.store(tc.cast(tc.min(x, 0), DType.F32), 2);
        Tile acc = x;
        for (int i = 0; i < iterations.get(0); i++) {
            acc = tc.add(acc, y);
        }
        cv.store(tc.cast(acc, DType.F32), 0);
    }

    private static float decode(byte bits, boolean e5m2) {
        return e5m2 ? FP8.e5m2ToFloat(bits) : FP8.e4m3ToFloat(bits);
    }

    private static byte encode(float value, boolean e5m2) {
        return e5m2 ? FP8.e5m2FromFloat(value) : FP8.e4m3FromFloat(value);
    }

    /** Independent nearest-even oracle: enumerate the finite FP8 values and break ties by bit parity. */
    private static float round(float value, boolean e5m2) {
        float nearest = 0.0f;
        double distance = Double.POSITIVE_INFINITY;
        for (int bits = 0; bits < 256; bits++) {
            float candidate = decode((byte) bits, e5m2);
            if (!Float.isFinite(candidate)) {
                continue;
            }
            double error = Math.abs((double) value - candidate);
            if (error < distance || (error == distance && (bits & 1) == 0)) {
                nearest = candidate;
                distance = error;
            }
        }
        return nearest;
    }

    private void checkArithmetic(boolean e5m2) throws TornadoExecutionPlanException {
        FP8Array a = new FP8Array(SIZE);
        FP8Array b = new FP8Array(SIZE);
        FP8Array out = new FP8Array(SIZE * OPERATIONS);
        FloatArray reduced = new FloatArray(3);
        FloatArray chained = new FloatArray(SIZE);
        IntArray iterations = new IntArray(1);
        // Exactly representable inputs include negative values, subnormals and rounding ties.
        float tiny = e5m2 ? 0x1.0p-16f : 0x1.0p-9f;
        float[] values = { 1.0f, -1.0f, 1.5f, -0.5f, tiny, -tiny, 0.0f, 2.0f };
        for (int i = 0; i < SIZE; i++) {
            a.set(i, encode(values[i % values.length], e5m2));
            b.set(i, encode(i % 2 == 0 ? 0.125f : 0.75f, e5m2));
        }
        TaskGraph graph = new TaskGraph("fp8")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, iterations);
        if (e5m2) {
            graph.task("k", TestTileFP8Arithmetic::arithmeticE5M2, new TileContext(), a, b, out, reduced, chained, iterations);
        } else {
            graph.task("k", TestTileFP8Arithmetic::arithmeticE4M3, new TileContext(), a, b, out, reduced, chained, iterations);
        }
        graph.transferToHost(DataTransferMode.EVERY_EXECUTION, out, reduced, chained);
        WorkerGrid1D worker = new WorkerGrid1D(1);
        worker.setLocalWork(1, 1, 1);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(new GridScheduler("fp8.k", worker));
            for (int count : new int[] { 0, 1, 3 }) {
                iterations.set(0, count);
                try {
                    plan.execute();
                } catch (RuntimeException e) {
                    String message = String.valueOf(e.getMessage()) + String.valueOf(e.getCause());
                    if (message.contains("FP8") && message.contains("9.0")) {
                        throw new TornadoVMCUDANotSupported("FP8 tiles require compute capability 9.0: " + message);
                    }
                    throw e;
                }
                float sum = 0.0f;
                float max = Float.NEGATIVE_INFINITY;
                float min = Float.POSITIVE_INFINITY;
                for (int i = 0; i < SIZE; i++) {
                    float x = decode(a.get(i), e5m2);
                    float y = decode(b.get(i), e5m2);
                    sum += x;
                    max = Math.max(max, x);
                    min = Math.min(min, x);
                    float[] expected = { x + y, x - y, x * y, x / y, Math.max(x, y), Math.min(x, y),
                            (float) Math.exp(x), x * round(1.25f, e5m2), Math.fma(x, y, y), sum };
                    for (int op = 0; op < OPERATIONS; op++) {
                        assertEquals("operation " + op + " element " + i,
                                round(expected[op], e5m2), decode(out.get(op * SIZE + i), e5m2), 0.0f);
                    }
                    float acc = x;
                    for (int k = 0; k < count; k++) {
                        acc = round(acc + y, e5m2);
                    }
                    assertEquals("loop count " + count + " element " + i, acc, chained.get(i), 0.0f);
                }
                assertEquals("sum", round(sum, e5m2), reduced.get(0), 0.0f);
                assertEquals("max", max, reduced.get(1), 0.0f);
                assertEquals("min", min, reduced.get(2), 0.0f);
            }
        }
    }

    @Test
    public void testE4M3Arithmetic() throws TornadoExecutionPlanException {
        checkArithmetic(false);
    }

    @Test
    public void testE5M2Arithmetic() throws TornadoExecutionPlanException {
        checkArithmetic(true);
    }
}
