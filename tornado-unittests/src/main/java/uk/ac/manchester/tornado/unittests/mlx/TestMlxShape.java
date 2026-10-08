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
package uk.ac.manchester.tornado.unittests.mlx;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.LibraryTask2;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.mlx.Mlx;

/**
 * Unit tests for the MLX shape and layout library tasks: reshaping, squeezing and expanding,
 * permutations, broadcasts, strided views, type conversion and reinterpretation, concatenate, stack,
 * split, repeat, tile, roll and pad. Each test checks the task against a sequential Java reference.
 * Skipped unless the default device is on the Metal backend and mlx.metallib is available.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestMlxShape
 * </code>
 */
public class TestMlxShape extends MlxTestBase {

    private static final int ROWS = 5;
    private static final int COLS = 7;

    /** 0.5 * i - 7 for i in [0, n): distinct values, so a misplaced element shows. */
    private static float[] ramp(int n) {
        float[] v = new float[n];
        for (int i = 0; i < n; i++) {
            v[i] = i * 0.5f - 7f;
        }
        return v;
    }

    /** Runs {@code mlx(x, out)} and checks out element for element against {@code expected}. */
    private static void check(float[] xv, float[] expected, LibraryTask2<FloatArray, FloatArray> mlx) throws TornadoExecutionPlanException {
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(expected.length);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("shape", mlx, x, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < expected.length; i++) {
            assertEquals("element " + i, expected[i], output.get(i), 0f);
        }
    }

    /** x[d0, d1, d2] with output axis k taken from input axis p[k]. */
    private static float[] permuteJava(float[] x, int[] d, int[] p) {
        int[] e = { d[p[0]], d[p[1]], d[p[2]] };
        float[] out = new float[x.length];
        for (int t = 0; t < x.length; t++) {
            int[] o = { t / (e[1] * e[2]), (t / e[2]) % e[1], t % e[2] };
            int[] in = new int[3];
            for (int k = 0; k < 3; k++) {
                in[p[k]] = o[k];
            }
            out[t] = x[(in[0] * d[1] + in[1]) * d[2] + in[2]];
        }
        return out;
    }

    // ---------------------------------------------------------------- layout-preserving

    @Test
    public void testReshape() throws TornadoExecutionPlanException {
        float[] xv = ramp(120);
        check(xv, xv, (x, out) -> Mlx.reshape(x, out, 12, 10));
    }

    @Test
    public void testFlatten() throws TornadoExecutionPlanException {
        float[] xv = ramp(120);
        check(xv, xv, (x, out) -> Mlx.flatten(x, out, 4, 6, 5));
    }

    @Test
    public void testUnflatten() throws TornadoExecutionPlanException {
        float[] xv = ramp(120);
        check(xv, xv, (x, out) -> Mlx.unflatten(x, out, 8, 15));
    }

    @Test
    public void testSqueeze() throws TornadoExecutionPlanException {
        float[] xv = ramp(120);
        check(xv, xv, (x, out) -> Mlx.squeeze(x, out, 24, 5));
    }

    @Test
    public void testSqueezeAxis() throws TornadoExecutionPlanException {
        float[] xv = ramp(120);
        check(xv, xv, (x, out) -> Mlx.squeezeAxis(x, out, 24, 5));
    }

    @Test
    public void testSqueezeAxes() throws TornadoExecutionPlanException {
        float[] xv = ramp(120);
        check(xv, xv, (x, out) -> Mlx.squeezeAxes(x, out, 24, 5));
    }

    @Test
    public void testExpandDims() throws TornadoExecutionPlanException {
        float[] xv = ramp(120);
        check(xv, xv, (x, out) -> Mlx.expandDims(x, out, 24, 5));
    }

    @Test
    public void testExpandDimsAxes() throws TornadoExecutionPlanException {
        float[] xv = ramp(120);
        check(xv, xv, (x, out) -> Mlx.expandDimsAxes(x, out, 24, 5));
    }

    @Test
    public void testAtleast1d() throws TornadoExecutionPlanException {
        float[] xv = ramp(120);
        check(xv, xv, Mlx::atleast1d);
    }

    @Test
    public void testAtleast2d() throws TornadoExecutionPlanException {
        float[] xv = ramp(120);
        check(xv, xv, Mlx::atleast2d);
    }

    @Test
    public void testAtleast3d() throws TornadoExecutionPlanException {
        float[] xv = ramp(120);
        check(xv, xv, Mlx::atleast3d);
    }

    @Test
    public void testContiguous() throws TornadoExecutionPlanException {
        float[] xv = ramp(120);
        check(xv, xv, Mlx::contiguous);
    }

    @Test
    public void testCopy() throws TornadoExecutionPlanException {
        float[] xv = ramp(120);
        check(xv, xv, Mlx::copy);
    }

    // ---------------------------------------------------------------- permutations

    @Test
    public void testTransposeAxes() throws TornadoExecutionPlanException {
        float[] xv = ramp(120);
        check(xv, permuteJava(xv, new int[] { 4, 5, 6 }, new int[] { 2, 0, 1 }), (x, out) -> Mlx.transposeAxes(x, out, 4, 5, 6, 2, 0, 1));
    }

    @Test
    public void testSwapaxes() throws TornadoExecutionPlanException {
        float[] xv = ramp(120);
        check(xv, permuteJava(xv, new int[] { 4, 5, 6 }, new int[] { 2, 1, 0 }), (x, out) -> Mlx.swapaxes(x, out, 4, 5, 6, 0, 2));
    }

    @Test
    public void testMoveaxis() throws TornadoExecutionPlanException {
        float[] xv = ramp(120);
        check(xv, permuteJava(xv, new int[] { 4, 5, 6 }, new int[] { 1, 2, 0 }), (x, out) -> Mlx.moveaxis(x, out, 4, 5, 6, 0, 2));
    }

    // ---------------------------------------------------------------- broadcasts and strided views

    @Test
    public void testBroadcastTo() throws TornadoExecutionPlanException {
        // A row of COLS broadcast to [ROWS, COLS].
        float[] row = ramp(COLS);
        float[] expected = new float[ROWS * COLS];
        for (int t = 0; t < expected.length; t++) {
            expected[t] = row[t % COLS];
        }
        check(row, expected, (x, out) -> Mlx.broadcastTo(x, out, ROWS, COLS));
    }

    @Test
    public void testAsStrided() throws TornadoExecutionPlanException {
        // A [5, 4] view with strides (7, 2) from offset 3.
        float[] xv = ramp(64);
        float[] expected = new float[5 * 4];
        for (int t = 0; t < expected.length; t++) {
            expected[t] = xv[3 + (t / 4) * 7 + (t % 4) * 2];
        }
        check(xv, expected, (x, out) -> Mlx.asStrided(x, out, 5, 4, 7, 2, 3));
    }

    @Test
    public void testBroadcastArrays() throws TornadoExecutionPlanException {
        // A row [1, COLS] and a column [ROWS, 1], both broadcast to [ROWS, COLS].
        float[] rv = ramp(COLS);
        float[] cv = values(ROWS, -1, 1, 1);
        FloatArray row = FloatArray.fromArray(rv);
        FloatArray col = FloatArray.fromArray(cv);
        FloatArray outRow = new FloatArray(ROWS * COLS);
        FloatArray outCol = new FloatArray(ROWS * COLS);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, row, col) //
                .libraryTask("broadcast", Mlx::broadcastArrays, row, col, outRow, outCol, ROWS, COLS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outRow, outCol);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int t = 0; t < ROWS * COLS; t++) {
            assertEquals("row " + t, rv[t % COLS], outRow.get(t), 0f);
            assertEquals("column " + t, cv[t / COLS], outCol.get(t), 0f);
        }
    }

    // ---------------------------------------------------------------- types

    @Test
    public void testAstype() throws TornadoExecutionPlanException {
        final int n = 257;
        float[] xv = values(n, -100, 100, 2);
        FloatArray x = FloatArray.fromArray(xv);
        HalfFloatArray toHalf = new HalfFloatArray(n);
        IntArray toInt = new IntArray(n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("half", Mlx::astype, x, toHalf) //
                .libraryTask("int", Mlx::astype, x, toInt) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, toHalf, toInt);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < n; i++) {
            float h = new HalfFloat(xv[i]).getFloat32();
            assertEquals("float16 " + i, h, toHalf.get(i).getFloat32(), Math.abs(h) * 1e-3f);
            assertEquals("int32 " + i, (int) xv[i], toInt.get(i));
        }
    }

    @Test
    public void testView() throws TornadoExecutionPlanException {
        // Reinterpreting float32 as int32 and back keeps the bits.
        final int n = 257;
        float[] xv = values(n, -100, 100, 3);
        FloatArray x = FloatArray.fromArray(xv);
        IntArray bits = new IntArray(n);
        FloatArray back = new FloatArray(n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("bits", Mlx::view, x, bits) //
                .libraryTask("back", Mlx::view, bits, back) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, bits, back);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < n; i++) {
            assertEquals("bits " + i, Float.floatToRawIntBits(xv[i]), bits.get(i));
            assertEquals("back " + i, xv[i], back.get(i), 0f);
        }
    }

    @Test
    public void testNumberOfElements() throws TornadoExecutionPlanException {
        FloatArray x = FloatArray.fromArray(ramp(3 * 4 * 5));
        IntArray count = new IntArray(1);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("count", Mlx::numberOfElements, x, count, 3, 4, 5) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, count);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        // The number of elements over the last two axes of [3, 4, 5].
        assertEquals(20, count.get(0));
    }

    // ---------------------------------------------------------------- joins and splits

    @Test
    public void testConcatenate() throws TornadoExecutionPlanException {
        float[] av = ramp(30);
        float[] bv = values(18, 100, 200, 4);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        FloatArray output = new FloatArray(av.length + bv.length);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("concatenate", Mlx::concatenate, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < output.getSize(); i++) {
            assertEquals("element " + i, i < av.length ? av[i] : bv[i - av.length], output.get(i), 0f);
        }
    }

    @Test
    public void testConcatenateAxis() throws TornadoExecutionPlanException {
        // a[6, 5] and b[6, 3] joined along the columns.
        final int rows = 6;
        final int colsA = 5;
        final int colsB = 3;
        float[] av = ramp(rows * colsA);
        float[] bv = values(rows * colsB, 100, 200, 5);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        FloatArray output = new FloatArray(rows * (colsA + colsB));

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("concatenate", Mlx::concatenateAxis, a, b, output, rows, colsA, colsB) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int t = 0; t < output.getSize(); t++) {
            int r = t / (colsA + colsB);
            int c = t % (colsA + colsB);
            assertEquals("element " + t, c < colsA ? av[r * colsA + c] : bv[r * colsB + c - colsA], output.get(t), 0f);
        }
    }

    @Test
    public void testStack() throws TornadoExecutionPlanException {
        float[] av = ramp(18);
        float[] bv = values(18, 100, 200, 6);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        FloatArray output = new FloatArray(36);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("stack", Mlx::stack, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < 18; i++) {
            assertEquals("a " + i, av[i], output.get(i), 0f);
            assertEquals("b " + i, bv[i], output.get(18 + i), 0f);
        }
    }

    @Test
    public void testStackAxis() throws TornadoExecutionPlanException {
        // Stacking along the last axis interleaves a and b.
        float[] av = ramp(18);
        float[] bv = values(18, 100, 200, 7);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        FloatArray output = new FloatArray(36);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("stack", Mlx::stackAxis, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < 18; i++) {
            assertEquals("a " + i, av[i], output.get(2 * i), 0f);
            assertEquals("b " + i, bv[i], output.get(2 * i + 1), 0f);
        }
    }

    private static void split(int index, boolean halves) throws TornadoExecutionPlanException {
        final int rows = 6;
        final int cols = 8;
        float[] xv = ramp(rows * cols);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray first = new FloatArray(rows * index);
        FloatArray second = new FloatArray(rows * (cols - index));

        TaskGraph taskGraph = new TaskGraph("g").transferToDevice(DataTransferMode.EVERY_EXECUTION, x);
        if (halves) {
            taskGraph.libraryTask("split", Mlx::split, x, first, second, rows, cols);
        } else {
            taskGraph.libraryTask("split", Mlx::splitSections, x, first, second, rows, cols, index);
        }
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, first, second);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int t = 0; t < rows * cols; t++) {
            int r = t / cols;
            int c = t % cols;
            float got = c < index ? first.get(r * index + c) : second.get(r * (cols - index) + c - index);
            assertEquals("element " + t, xv[t], got, 0f);
        }
    }

    @Test
    public void testSplit() throws TornadoExecutionPlanException {
        split(4, true);
    }

    @Test
    public void testSplitSections() throws TornadoExecutionPlanException {
        split(3, false);
    }

    // ---------------------------------------------------------------- repeat, tile, roll and pad

    @Test
    public void testRepeat() throws TornadoExecutionPlanException {
        float[] xv = ramp(ROWS * COLS);
        float[] expected = new float[3 * xv.length];
        for (int t = 0; t < expected.length; t++) {
            expected[t] = xv[t / 3];
        }
        check(xv, expected, (x, out) -> Mlx.repeat(x, out, 3));
    }

    @Test
    public void testRepeatAxis() throws TornadoExecutionPlanException {
        // Each row of [ROWS, COLS] twice.
        float[] xv = ramp(ROWS * COLS);
        float[] expected = new float[2 * xv.length];
        for (int t = 0; t < expected.length; t++) {
            expected[t] = xv[(t / (2 * COLS)) * COLS + t % COLS];
        }
        check(xv, expected, (x, out) -> Mlx.repeatAxis(x, out, ROWS, COLS, 2));
    }

    @Test
    public void testTile() throws TornadoExecutionPlanException {
        // [ROWS, COLS] tiled 2 x 3.
        float[] xv = ramp(ROWS * COLS);
        float[] expected = new float[2 * ROWS * 3 * COLS];
        for (int t = 0; t < expected.length; t++) {
            expected[t] = xv[((t / (3 * COLS)) % ROWS) * COLS + (t % (3 * COLS)) % COLS];
        }
        check(xv, expected, (x, out) -> Mlx.tile(x, out, ROWS, COLS, 2, 3));
    }

    @Test
    public void testRoll() throws TornadoExecutionPlanException {
        float[] xv = ramp(ROWS * COLS);
        int n = xv.length;
        float[] expected = new float[n];
        for (int t = 0; t < n; t++) {
            expected[t] = xv[Math.floorMod(t - 4, n)];
        }
        check(xv, expected, (x, out) -> Mlx.roll(x, out, 4));
    }

    @Test
    public void testRollAxis() throws TornadoExecutionPlanException {
        // Each row rolled by -2.
        float[] xv = ramp(ROWS * COLS);
        float[] expected = new float[xv.length];
        for (int t = 0; t < xv.length; t++) {
            expected[t] = xv[(t / COLS) * COLS + Math.floorMod(t % COLS + 2, COLS)];
        }
        check(xv, expected, (x, out) -> Mlx.rollAxis(x, out, ROWS, COLS, -2));
    }

    @Test
    public void testRollAxes() throws TornadoExecutionPlanException {
        // Rolled by 1 along the rows and 3 along the columns.
        float[] xv = ramp(ROWS * COLS);
        float[] expected = new float[xv.length];
        for (int t = 0; t < xv.length; t++) {
            expected[t] = xv[Math.floorMod(t / COLS - 1, ROWS) * COLS + Math.floorMod(t % COLS - 3, COLS)];
        }
        check(xv, expected, (x, out) -> Mlx.rollAxes(x, out, ROWS, COLS, 1, 3));
    }

    @Test
    public void testPad() throws TornadoExecutionPlanException {
        // 1 row above, 2 below, 3 columns left, none right, filled with -9.
        float[] xv = ramp(ROWS * COLS);
        int outRows = ROWS + 3;
        int outCols = COLS + 3;
        float[] expected = new float[outRows * outCols];
        for (int t = 0; t < expected.length; t++) {
            int r = t / outCols - 1;
            int c = t % outCols - 3;
            expected[t] = r >= 0 && r < ROWS && c >= 0 && c < COLS ? xv[r * COLS + c] : -9f;
        }
        check(xv, expected, (x, out) -> Mlx.pad(x, out, ROWS, COLS, 1, 2, 3, 0, -9f));
    }

    @Test
    public void testPadSymmetric() throws TornadoExecutionPlanException {
        final int width = 2;
        float[] xv = ramp(ROWS * COLS);
        int outCols = COLS + 2 * width;
        float[] expected = new float[(ROWS + 2 * width) * outCols];
        for (int t = 0; t < expected.length; t++) {
            int r = t / outCols - width;
            int c = t % outCols - width;
            expected[t] = r >= 0 && r < ROWS && c >= 0 && c < COLS ? xv[r * COLS + c] : 0.5f;
        }
        check(xv, expected, (x, out) -> Mlx.padSymmetric(x, out, ROWS, COLS, width, 0.5f));
    }

    // ---------------------------------------------------------------- other types

    @Test
    public void testTransposeAxesInt() throws TornadoExecutionPlanException {
        final int rows = 4;
        final int cols = 6;
        int[] xv = new int[rows * cols];
        for (int i = 0; i < xv.length; i++) {
            xv[i] = i * 3 - 10;
        }
        IntArray x = IntArray.fromArray(xv);
        IntArray output = new IntArray(xv.length);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("transpose", Mlx::transposeAxes, x, output, 1, rows, cols, 0, 2, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < xv.length; i++) {
            assertEquals("element " + i, xv[(i % rows) * cols + i / rows], output.get(i));
        }
    }

    @Test
    public void testAstypeIntToFloat() throws TornadoExecutionPlanException {
        int[] xv = new int[100];
        for (int i = 0; i < xv.length; i++) {
            xv[i] = i * 3 - 150;
        }
        IntArray x = IntArray.fromArray(xv);
        FloatArray output = new FloatArray(xv.length);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("astype", Mlx::astype, x, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < xv.length; i++) {
            assertEquals("element " + i, xv[i], output.get(i), 0f);
        }
    }
}
