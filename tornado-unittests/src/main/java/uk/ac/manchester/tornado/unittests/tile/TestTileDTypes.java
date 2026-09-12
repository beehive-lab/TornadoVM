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
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.tile.DType;
import uk.ac.manchester.tornado.api.tile.PartitionView;
import uk.ac.manchester.tornado.api.tile.Tile;
import uk.ac.manchester.tornado.api.tile.TileContext;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FP8Array;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.Int8Array;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * The element types beyond fp16 and fp32: 8-bit integers, doubles, and 8-bit floats.
 *
 * <p>
 * {@code DType} has named these since the first commit and {@code mma} has validated them
 * against the CUDA Tile accumulator tables, but until now only {@code FloatArray},
 * {@code HalfFloatArray}, {@code BFloat16Array} and {@code IntArray} could be viewed - so a
 * quantised GEMM had no way to be fed. These tests cover the three buffers that closes.
 * </p>
 *
 * <p>
 * The integer path is the valuable one and the easiest to trust: an int8 matmul accumulating
 * into int32 is exact, so the assertions are equalities rather than tolerances, and a wrong
 * operand layout or a saturating accumulate cannot hide inside a rounding budget. On this
 * device it lowers to {@code IMMA.16832.S8.S8.SAT}.
 * </p>
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileDTypes
 * </pre>
 */
public class TestTileDTypes extends TornadoTestBase {

    private static final int TILE = 32;

    private static final int ROW_WIDTH = 64;

    @Before
    public void tileMustBeAvailable() {
        TileSupport.requireTileSupport();
    }

    // -------------------------------------------------------------------------------------
    // int8 -> int32
    // -------------------------------------------------------------------------------------

    /**
     * Quantised matmul: int8 operands, int32 accumulator. The same kernel shape as the fp16
     * GEMM, with the element types doing all the talking.
     */
    public static void int8Matmul(TileContext tc, Int8Array a, Int8Array b, IntArray c, int m, int n, int k) {
        PartitionView aView = tc.partition(tc.view(a, m, k), TILE, TILE);
        PartitionView bView = tc.partition(tc.view(b, k, n), TILE, TILE);
        PartitionView cView = tc.partition(tc.view(c, m, n), TILE, TILE);

        int rowBlock = tc.bidX();
        int columnBlock = tc.bidY();
        Tile acc = tc.zeros(DType.S32, TILE, TILE);
        for (int step = 0; step < k / TILE; step++) {
            acc = tc.mma(aView.load(rowBlock, step), bView.load(step, columnBlock), acc);
        }
        cView.store(acc, rowBlock, columnBlock);
    }

    /**
     * The same matmul with a dequantising epilogue, which is what an inference kernel actually
     * wants: accumulate in int32, convert to float, apply the scale. Exercises a cast out of an
     * integer tile.
     */
    public static void int8MatmulDequantised(TileContext tc, Int8Array a, Int8Array b, FloatArray c, int m, int n, int k, float scale) {
        PartitionView aView = tc.partition(tc.view(a, m, k), TILE, TILE);
        PartitionView bView = tc.partition(tc.view(b, k, n), TILE, TILE);
        PartitionView cView = tc.partition(tc.view(c, m, n), TILE, TILE);

        int rowBlock = tc.bidX();
        int columnBlock = tc.bidY();
        Tile acc = tc.zeros(DType.S32, TILE, TILE);
        for (int step = 0; step < k / TILE; step++) {
            acc = tc.mma(aView.load(rowBlock, step), bView.load(step, columnBlock), acc);
        }
        cView.store(tc.scale(tc.cast(acc, DType.F32), scale), rowBlock, columnBlock);
    }

    /** Elementwise int8 work with an int32 result, to cover the integer path without mma. */
    public static void int8Absolute(TileContext tc, Int8Array in, IntArray out, int rows) {
        PartitionView iv = tc.partition(tc.view(in, rows, ROW_WIDTH), 1, ROW_WIDTH);
        PartitionView ov = tc.partition(tc.view(out, rows, ROW_WIDTH), 1, ROW_WIDTH);
        int row = tc.bidX();
        ov.store(tc.cast(tc.abs(iv.load(row, 0)), DType.S32), row, 0);
    }

    // -------------------------------------------------------------------------------------
    // double
    // -------------------------------------------------------------------------------------

    /**
     * Double-precision row work: a sum of square roots.
     *
     * <p>
     * fp64 has no tensor-core path on this device - an fp64 {@code mma} compiles but lowers to
     * scalar {@code DADD}/{@code DMUL} rather than {@code DMMA} - so what matters here is that
     * the element type round-trips through a view, a reduction and a store at full precision.
     * The tolerance is accordingly tight enough that an fp32 intermediate would fail it.
     * </p>
     */
    public static void doubleRowSum(TileContext tc, DoubleArray in, DoubleArray out, int rows) {
        PartitionView iv = tc.partition(tc.view(in, rows, ROW_WIDTH), 1, ROW_WIDTH);
        PartitionView ov = tc.partition(tc.view(out, rows, 1), 1, 1);
        int row = tc.bidX();
        ov.store(tc.sum(tc.sqrt(iv.load(row, 0)), 1), row, 0);
    }

    /** Doubles through mma: correct, though without tensor cores on this architecture. */
    public static void doubleMatmul(TileContext tc, DoubleArray a, DoubleArray b, DoubleArray c, int m, int n, int k) {
        PartitionView aView = tc.partition(tc.view(a, m, k), 16, 16);
        PartitionView bView = tc.partition(tc.view(b, k, n), 16, 16);
        PartitionView cView = tc.partition(tc.view(c, m, n), 16, 16);

        int rowBlock = tc.bidX();
        int columnBlock = tc.bidY();
        Tile acc = tc.zeros(DType.F64, 16, 16);
        for (int step = 0; step < k / 16; step++) {
            acc = tc.mma(aView.load(rowBlock, step), bView.load(step, columnBlock), acc);
        }
        cView.store(acc, rowBlock, columnBlock);
    }

    // -------------------------------------------------------------------------------------
    // fp8: gated, not supported below Hopper
    // -------------------------------------------------------------------------------------

    /** An fp8 kernel, used to check that the gate refuses it with an explanation. */
    public static void fp8Scale(TileContext tc, FP8Array in, FloatArray out, int rows) {
        PartitionView iv = tc.partition(tc.view(in, DType.FP8_E4M3, rows, ROW_WIDTH), 1, ROW_WIDTH);
        PartitionView ov = tc.partition(tc.view(out, rows, ROW_WIDTH), 1, ROW_WIDTH);
        int row = tc.bidX();
        ov.store(tc.scale(tc.cast(iv.load(row, 0), DType.F32), 2.0), row, 0);
    }

    // -------------------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------------------

    private static Int8Array randomInt8(int elements, long seed) {
        Int8Array array = new Int8Array(elements);
        Random random = new Random(seed);
        for (int i = 0; i < elements; i++) {
            // Kept well inside int8 so the reference and the kernel agree exactly; the point is
            // the operand layout and the accumulate, not saturation behaviour.
            array.set(i, (byte) (random.nextInt(41) - 20));
        }
        return array;
    }

    private void checkInt8Matmul(int m, int n, int k) throws TornadoExecutionPlanException {
        Int8Array a = randomInt8(m * k, 901);
        Int8Array b = randomInt8(k * n, 907);
        IntArray c = new IntArray(m * n);

        WorkerGrid2D worker = new WorkerGrid2D(m / TILE, n / TILE);
        GridScheduler grid = new GridScheduler("imma.k", worker);
        TaskGraph graph = new TaskGraph("imma") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileDTypes::int8Matmul, new TileContext(), a, b, c, m, n, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        for (int row = 0; row < m; row++) {
            for (int column = 0; column < n; column++) {
                int expected = 0;
                for (int inner = 0; inner < k; inner++) {
                    expected += a.get(row * k + inner) * b.get(inner * n + column);
                }
                // Integer arithmetic: exact, no tolerance.
                assertEquals("(" + row + "," + column + ")", expected, c.get(row * n + column));
            }
        }
    }

    @Test
    public void testInt8MatmulSquare() throws TornadoExecutionPlanException {
        checkInt8Matmul(64, 64, 64);
    }

    @Test
    public void testInt8MatmulSingleTile() throws TornadoExecutionPlanException {
        checkInt8Matmul(TILE, TILE, TILE);
    }

    @Test
    public void testInt8MatmulRectangular() throws TornadoExecutionPlanException {
        checkInt8Matmul(96, 64, 128);
    }

    @Test
    public void testInt8MatmulDequantised() throws TornadoExecutionPlanException {
        final int size = 64;
        final float scale = 0.003f;
        Int8Array a = randomInt8(size * size, 911);
        Int8Array b = randomInt8(size * size, 919);
        FloatArray c = new FloatArray(size * size);

        WorkerGrid2D worker = new WorkerGrid2D(size / TILE, size / TILE);
        GridScheduler grid = new GridScheduler("deq.k", worker);
        TaskGraph graph = new TaskGraph("deq") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileDTypes::int8MatmulDequantised, new TileContext(), a, b, c, size, size, size, scale) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int accumulated = 0;
                for (int inner = 0; inner < size; inner++) {
                    accumulated += a.get(row * size + inner) * b.get(inner * size + column);
                }
                assertEquals("(" + row + "," + column + ")", accumulated * scale, c.get(row * size + column), 1e-3f);
            }
        }
    }

    @Test
    public void testInt8Absolute() throws TornadoExecutionPlanException {
        final int rows = 32;
        Int8Array in = randomInt8(rows * ROW_WIDTH, 929);
        IntArray out = new IntArray(rows * ROW_WIDTH);

        WorkerGrid1D worker = new WorkerGrid1D(rows);
        GridScheduler grid = new GridScheduler("abs.k", worker);
        TaskGraph graph = new TaskGraph("abs") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileDTypes::int8Absolute, new TileContext(), in, out, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        for (int i = 0; i < rows * ROW_WIDTH; i++) {
            assertEquals("element " + i, Math.abs(in.get(i)), out.get(i));
        }
    }

    @Test
    public void testDoubleRowSum() throws TornadoExecutionPlanException {
        final int rows = 48;
        DoubleArray in = new DoubleArray(rows * ROW_WIDTH);
        DoubleArray out = new DoubleArray(rows);
        Random random = new Random(937);
        for (int i = 0; i < rows * ROW_WIDTH; i++) {
            in.set(i, 1.0 + random.nextDouble());
        }

        WorkerGrid1D worker = new WorkerGrid1D(rows);
        GridScheduler grid = new GridScheduler("dsum.k", worker);
        TaskGraph graph = new TaskGraph("dsum") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileDTypes::doubleRowSum, new TileContext(), in, out, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        for (int row = 0; row < rows; row++) {
            double expected = 0.0;
            for (int i = 0; i < ROW_WIDTH; i++) {
                expected += Math.sqrt(in.get(row * ROW_WIDTH + i));
            }
            // Tight enough that an fp32 intermediate would not pass: 64 terms near 1.2 in fp32
            // carry roughly 1e-5 of error, three orders of magnitude above this bound.
            assertEquals("row " + row, expected, out.get(row), 1e-9);
        }
    }

    @Test
    public void testDoubleMatmul() throws TornadoExecutionPlanException {
        final int size = 32;
        DoubleArray a = new DoubleArray(size * size);
        DoubleArray b = new DoubleArray(size * size);
        DoubleArray c = new DoubleArray(size * size);
        Random random = new Random(941);
        for (int i = 0; i < size * size; i++) {
            a.set(i, random.nextDouble() - 0.5);
            b.set(i, random.nextDouble() - 0.5);
        }

        WorkerGrid2D worker = new WorkerGrid2D(size / 16, size / 16);
        GridScheduler grid = new GridScheduler("dmm.k", worker);
        TaskGraph graph = new TaskGraph("dmm") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileDTypes::doubleMatmul, new TileContext(), a, b, c, size, size, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                double expected = 0.0;
                for (int inner = 0; inner < size; inner++) {
                    expected += a.get(row * size + inner) * b.get(inner * size + column);
                }
                assertEquals("(" + row + "," + column + ")", expected, c.get(row * size + column), 1e-12);
            }
        }
    }

    /**
     * fp8 is refused below compute capability 9.0, and the message has to say so.
     *
     * <p>
     * Without the gate this fails deep inside nvcc with "Incompatibility with architecture
     * 'sm_89': unsupported type 'f8E4M3FN'", which does not tell a Java author what to do.
     * Verified on sm_89: the refusal reads "The tile operation 'partition view' uses FP8_E4M3,
     * and CUDA Tile supports fp8 tiles only from compute capability 9.0 (Hopper) onwards;
     * device reports 8.9."
     * </p>
     *
     * <p>
     * Two outcomes are accepted because two are correct: on Hopper and newer the kernel
     * compiles and the values must be right, and below it the compilation is refused. What the
     * test insists on is that a refusal explains itself.
     * </p>
     *
     * <p>
     * This test depends on {@code -Dtornado.recover.bailout=False}, which
     * {@code tornado-test} sets. With bailout enabled the refusal is swallowed and the task
     * silently runs {@link TileContext}'s JVM fallback, which computes the right answer on the
     * host - so the test would pass without the gate ever being exercised. That is worth
     * knowing before moving this test to another harness.
     * </p>
     */
    @Test
    public void testFp8RefusedBelowHopper() {
        final int rows = 32;
        FP8Array in = new FP8Array(rows * ROW_WIDTH);
        FloatArray out = new FloatArray(rows * ROW_WIDTH);
        for (int i = 0; i < rows * ROW_WIDTH; i++) {
            in.setE4M3(i, (i % 9) * 0.25f);
        }

        WorkerGrid1D worker = new WorkerGrid1D(rows);
        GridScheduler grid = new GridScheduler("fp8.k", worker);
        TaskGraph graph = new TaskGraph("fp8") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileDTypes::fp8Scale, new TileContext(), in, out, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
            // Hopper and newer: it ran, so the values must be right.
            for (int i = 0; i < rows * ROW_WIDTH; i++) {
                assertEquals("element " + i, in.getE4M3(i) * 2.0f, out.get(i), 1e-3f);
            }
        } catch (Exception e) {
            String message = String.valueOf(e.getMessage()) + String.valueOf(e.getCause());
            assertTrue("an fp8 refusal must name fp8 and the capability it needs, got: " + message, //
                    message.contains("FP8") && message.contains("9.0"));
            assertTrue("the refusal should name the device's own capability, got: " + message, message.contains("device reports"));
        }
    }
}
