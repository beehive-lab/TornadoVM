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

import java.util.Random;

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
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * The scalar math, predicate and bitwise operations added to close the remaining parity gaps
 * against CUDA Tile's builtin set.
 *
 * <p>
 * Each of these is a single registration over machinery that already existed, so what the tests
 * are really checking is that each one lowers to the right {@code ct::} spelling and computes
 * what its name says. Two are not plain function calls and are worth singling out: predicate
 * negation is {@code !mask} and bitwise complement is {@code ~tile}, prefix operators rather
 * than named functions, and the statement emits them as such.
 * </p>
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileMathOps
 * </pre>
 */
public class TestTileMathOps extends TornadoTestBase {

    private static final int WIDTH = 64;

    @Before
    public void tileMustBeAvailable() {
        TileSupport.requireTileSupport();
    }

    /** The unary additions: ceil, tan, sinh, cosh. */
    public static void unaryMath(TileContext tc, FloatArray in, FloatArray ceil, FloatArray tan, FloatArray sinh, FloatArray cosh, int rows) {
        PartitionView iv = tc.partition(tc.view(in, rows, WIDTH), 1, WIDTH);
        PartitionView cv = tc.partition(tc.view(ceil, rows, WIDTH), 1, WIDTH);
        PartitionView tv = tc.partition(tc.view(tan, rows, WIDTH), 1, WIDTH);
        PartitionView sv = tc.partition(tc.view(sinh, rows, WIDTH), 1, WIDTH);
        PartitionView hv = tc.partition(tc.view(cosh, rows, WIDTH), 1, WIDTH);

        int row = tc.bidX();
        Tile x = iv.load(row, 0);
        cv.store(tc.ceil(x), row, 0);
        tv.store(tc.tan(x), row, 0);
        sv.store(tc.sinh(x), row, 0);
        hv.store(tc.cosh(x), row, 0);
    }

    /** The binary additions: atan2, pow. */
    public static void binaryMath(TileContext tc, FloatArray a, FloatArray b, FloatArray atan2, FloatArray power, int rows) {
        PartitionView av = tc.partition(tc.view(a, rows, WIDTH), 1, WIDTH);
        PartitionView bv = tc.partition(tc.view(b, rows, WIDTH), 1, WIDTH);
        PartitionView atanView = tc.partition(tc.view(atan2, rows, WIDTH), 1, WIDTH);
        PartitionView powerView = tc.partition(tc.view(power, rows, WIDTH), 1, WIDTH);

        int row = tc.bidX();
        Tile x = av.load(row, 0);
        Tile y = bv.load(row, 0);
        atanView.store(tc.atan2(x, y), row, 0);
        powerView.store(tc.pow(x, y), row, 0);
    }

    /**
     * The predicate additions: {@code isNaN}, {@code isInfinite}, {@code logicalNot}, and the
     * reductions {@code allOf} / {@code anyOf}. Each result is encoded as 1 or 0 by selecting
     * between two tiles, since a predicate tile has no storage form.
     */
    public static void predicates(TileContext tc, FloatArray in, FloatArray notPositive, FloatArray special, IntArray anyPositive, IntArray allPositive, int rows) {
        PartitionView iv = tc.partition(tc.view(in, rows, WIDTH), 1, WIDTH);
        PartitionView nv = tc.partition(tc.view(notPositive, rows, WIDTH), 1, WIDTH);
        PartitionView sv = tc.partition(tc.view(special, rows, WIDTH), 1, WIDTH);
        PartitionView anyView = tc.partition(tc.view(anyPositive, rows, 1), 1, 1);
        PartitionView allView = tc.partition(tc.view(allPositive, rows, 1), 1, 1);

        int row = tc.bidX();
        Tile x = iv.load(row, 0);
        Tile ones = tc.ones(DType.F32, 1, WIDTH);
        Tile zeros = tc.zeros(DType.F32, 1, WIDTH);
        Tile oneInt = tc.ones(DType.S32, 1, 1);
        Tile zeroInt = tc.zeros(DType.S32, 1, 1);

        Tile positive = tc.greaterThan(x, 0.0);
        nv.store(tc.select(tc.logicalNot(positive), ones, zeros), row, 0);
        // Neither NaN nor infinite for ordinary inputs, so this stores zero everywhere.
        sv.store(tc.select(tc.logicalOr(tc.isNaN(x), tc.isInfinite(x)), ones, zeros), row, 0);
        anyView.store(tc.select(tc.anyOf(positive, 1), oneInt, zeroInt), row, 0);
        allView.store(tc.select(tc.allOf(positive, 1), oneInt, zeroInt), row, 0);
    }

    /** The bitwise additions, elementwise and as reductions. */
    public static void bitwise(TileContext tc, IntArray in, IntArray and, IntArray or, IntArray xor, IntArray not, IntArray reduced, int rows) {
        PartitionView iv = tc.partition(tc.view(in, rows, WIDTH), 1, WIDTH);
        PartitionView andView = tc.partition(tc.view(and, rows, WIDTH), 1, WIDTH);
        PartitionView orView = tc.partition(tc.view(or, rows, WIDTH), 1, WIDTH);
        PartitionView xorView = tc.partition(tc.view(xor, rows, WIDTH), 1, WIDTH);
        PartitionView notView = tc.partition(tc.view(not, rows, WIDTH), 1, WIDTH);
        PartitionView reducedView = tc.partition(tc.view(reduced, rows, 1), 1, 1);

        int row = tc.bidX();
        Tile x = iv.load(row, 0);
        Tile mask = tc.full(DType.S32, 0x0F0F, 1, WIDTH);
        andView.store(tc.bitwiseAnd(x, mask), row, 0);
        orView.store(tc.bitwiseOr(x, mask), row, 0);
        xorView.store(tc.bitwiseXor(x, mask), row, 0);
        notView.store(tc.bitwiseNot(x), row, 0);
        reducedView.store(tc.reduceBitXor(x, 1), row, 0);
    }

    // -------------------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------------------

    private static void run(String taskId, TaskGraph graph, int blocks) throws TornadoExecutionPlanException {
        GridScheduler grid = new GridScheduler(taskId, new WorkerGrid1D(blocks));
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }
    }

    @Test
    public void testUnaryMath() throws TornadoExecutionPlanException {
        final int rows = 32;
        FloatArray in = new FloatArray(rows * WIDTH);
        Random random = new Random(1601);
        for (int i = 0; i < rows * WIDTH; i++) {
            // Kept inside (-1, 1) so tan stays far from its poles and cosh stays small.
            in.set(i, 1.6f * random.nextFloat() - 0.8f);
        }
        FloatArray ceil = new FloatArray(rows * WIDTH);
        FloatArray tan = new FloatArray(rows * WIDTH);
        FloatArray sinh = new FloatArray(rows * WIDTH);
        FloatArray cosh = new FloatArray(rows * WIDTH);

        run("u.k", new TaskGraph("u") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileMathOps::unaryMath, new TileContext(), in, ceil, tan, sinh, cosh, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, ceil, tan, sinh, cosh), rows);

        for (int i = 0; i < rows * WIDTH; i++) {
            double x = in.get(i);
            assertEquals("ceil " + i, Math.ceil(x), ceil.get(i), 1e-6);
            assertEquals("tan " + i, Math.tan(x), tan.get(i), 1e-4);
            assertEquals("sinh " + i, Math.sinh(x), sinh.get(i), 1e-4);
            assertEquals("cosh " + i, Math.cosh(x), cosh.get(i), 1e-4);
        }
    }

    @Test
    public void testBinaryMath() throws TornadoExecutionPlanException {
        final int rows = 32;
        FloatArray a = new FloatArray(rows * WIDTH);
        FloatArray b = new FloatArray(rows * WIDTH);
        Random random = new Random(1607);
        for (int i = 0; i < rows * WIDTH; i++) {
            // Positive bases only: pow of a negative base with a fractional exponent is NaN.
            a.set(i, 0.25f + random.nextFloat());
            b.set(i, 0.25f + random.nextFloat());
        }
        FloatArray atan2 = new FloatArray(rows * WIDTH);
        FloatArray power = new FloatArray(rows * WIDTH);

        run("b.k", new TaskGraph("b") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileMathOps::binaryMath, new TileContext(), a, b, atan2, power, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, atan2, power), rows);

        for (int i = 0; i < rows * WIDTH; i++) {
            assertEquals("atan2 " + i, Math.atan2(a.get(i), b.get(i)), atan2.get(i), 1e-5);
            assertEquals("pow " + i, Math.pow(a.get(i), b.get(i)), power.get(i), 1e-4);
        }
    }

    @Test
    public void testPredicates() throws TornadoExecutionPlanException {
        final int rows = 32;
        FloatArray in = new FloatArray(rows * WIDTH);
        Random random = new Random(1609);
        for (int i = 0; i < rows * WIDTH; i++) {
            in.set(i, 2.0f * random.nextFloat() - 1.0f);
        }
        // Make one row entirely positive and one entirely negative, so allOf and anyOf each
        // have a row that exercises both answers.
        for (int i = 0; i < WIDTH; i++) {
            in.set(i, 1.0f + i);
            in.set(WIDTH + i, -1.0f - i);
        }
        FloatArray notPositive = new FloatArray(rows * WIDTH);
        FloatArray special = new FloatArray(rows * WIDTH);
        IntArray anyPositive = new IntArray(rows);
        IntArray allPositive = new IntArray(rows);

        run("p.k", new TaskGraph("p") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileMathOps::predicates, new TileContext(), in, notPositive, special, anyPositive, allPositive, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, notPositive, special, anyPositive, allPositive), rows);

        for (int row = 0; row < rows; row++) {
            boolean any = false;
            boolean all = true;
            for (int i = 0; i < WIDTH; i++) {
                float value = in.get(row * WIDTH + i);
                any |= value > 0.0f;
                all &= value > 0.0f;
                assertEquals("not " + row + "," + i, value > 0.0f ? 0.0f : 1.0f, notPositive.get(row * WIDTH + i), 1e-6);
                assertEquals("special " + row + "," + i, 0.0f, special.get(row * WIDTH + i), 1e-6);
            }
            assertEquals("anyOf row " + row, any ? 1 : 0, anyPositive.get(row));
            assertEquals("allOf row " + row, all ? 1 : 0, allPositive.get(row));
        }
    }

    @Test
    public void testBitwise() throws TornadoExecutionPlanException {
        final int rows = 32;
        final int mask = 0x0F0F;
        IntArray in = new IntArray(rows * WIDTH);
        Random random = new Random(1613);
        for (int i = 0; i < rows * WIDTH; i++) {
            in.set(i, random.nextInt());
        }
        IntArray and = new IntArray(rows * WIDTH);
        IntArray or = new IntArray(rows * WIDTH);
        IntArray xor = new IntArray(rows * WIDTH);
        IntArray not = new IntArray(rows * WIDTH);
        IntArray reduced = new IntArray(rows);

        run("bit.k", new TaskGraph("bit") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileMathOps::bitwise, new TileContext(), in, and, or, xor, not, reduced, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, and, or, xor, not, reduced), rows);

        for (int row = 0; row < rows; row++) {
            int expectedReduction = 0;
            for (int i = 0; i < WIDTH; i++) {
                int value = in.get(row * WIDTH + i);
                expectedReduction ^= value;
                assertEquals("and " + i, value & mask, and.get(row * WIDTH + i));
                assertEquals("or " + i, value | mask, or.get(row * WIDTH + i));
                assertEquals("xor " + i, value ^ mask, xor.get(row * WIDTH + i));
                assertEquals("not " + i, ~value, not.get(row * WIDTH + i));
            }
            assertEquals("reduceBitXor row " + row, expectedReduction, reduced.get(row));
        }
    }
}
