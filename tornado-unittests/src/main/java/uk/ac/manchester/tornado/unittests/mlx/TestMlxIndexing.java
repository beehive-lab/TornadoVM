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

import java.util.function.DoubleBinaryOperator;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.LibraryTask9;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.mlx.MlxIndexing;

/**
 * Unit tests for the MLX indexing library tasks: strided 2D slices and slice updates (set, add,
 * multiply). An update writes the modified copy to {@code out} and leaves {@code x} unchanged. Each
 * test checks the task against a sequential Java reference. Skipped unless the default device is on
 * the Metal backend and mlx.metallib is available.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestMlxIndexing
 * </code>
 */
public class TestMlxIndexing extends MlxTestBase {

    private static final int ROWS = 40;
    private static final int COLS = 24;
    private static final int R0 = 5;
    private static final int C0 = 4;
    private static final int UPDATE_ROWS = 9;
    private static final int UPDATE_COLS = 10;

    /** x[rows, cols] with the update[ur, uc] block at (r0, c0) combined by {@code op}. */
    private static float[] sliceUpdateJava(float[] x, float[] update, DoubleBinaryOperator op) {
        float[] out = x.clone();
        for (int r = 0; r < UPDATE_ROWS; r++) {
            for (int c = 0; c < UPDATE_COLS; c++) {
                int p = (R0 + r) * COLS + C0 + c;
                out[p] = (float) op.applyAsDouble(out[p], update[r * UPDATE_COLS + c]);
            }
        }
        return out;
    }

    private static void sliceUpdate(LibraryTask9<FloatArray, FloatArray, FloatArray, Integer, Integer, Integer, Integer, Integer, Integer> mlx, DoubleBinaryOperator op)
            throws TornadoExecutionPlanException {
        float[] xv = values(ROWS * COLS, 1, 2, 1);
        float[] uv = values(UPDATE_ROWS * UPDATE_COLS, 0.5f, 2.5f, 2);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray update = FloatArray.fromArray(uv);
        FloatArray output = new FloatArray(xv.length);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, update) //
                .libraryTask("update", mlx, x, update, output, ROWS, COLS, R0, C0, UPDATE_ROWS, UPDATE_COLS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output, x);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        float[] expected = sliceUpdateJava(xv, uv, op);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("element " + i, expected[i], output.get(i), 1e-6f * Math.abs(expected[i]));
            assertEquals("x must not change, element " + i, xv[i], x.get(i), 0f);
        }
    }

    @Test
    public void testSlice() throws TornadoExecutionPlanException {
        // x[3:37:2, 1:23:3]
        final int r0 = 3;
        final int r1 = 37;
        final int rowStep = 2;
        final int c0 = 1;
        final int c1 = 23;
        final int colStep = 3;
        final int outRows = (r1 - r0 + rowStep - 1) / rowStep;
        final int outCols = (c1 - c0 + colStep - 1) / colStep;
        float[] xv = values(ROWS * COLS, -10, 10, 3);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(outRows * outCols);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("slice", MlxIndexing::slice, x, output, ROWS, COLS, r0, r1, rowStep, c0, c1, colStep) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int r = 0; r < outRows; r++) {
            for (int c = 0; c < outCols; c++) {
                assertEquals(r + "," + c, xv[(r0 + r * rowStep) * COLS + c0 + c * colStep], output.get(r * outCols + c), 0f);
            }
        }
    }

    @Test
    public void testSliceUpdate() throws TornadoExecutionPlanException {
        sliceUpdate(MlxIndexing::sliceUpdate, (old, u) -> u);
    }

    @Test
    public void testSliceUpdateAdd() throws TornadoExecutionPlanException {
        sliceUpdate(MlxIndexing::sliceUpdateAdd, Double::sum);
    }

    @Test
    public void testSliceUpdateProd() throws TornadoExecutionPlanException {
        sliceUpdate(MlxIndexing::sliceUpdateProd, (old, u) -> old * u);
    }

    // ---------------------------------------------------------------- other types

    @Test
    public void testSliceInt() throws TornadoExecutionPlanException {
        int[] xv = new int[ROWS * COLS];
        for (int i = 0; i < xv.length; i++) {
            xv[i] = i;
        }
        IntArray x = IntArray.fromArray(xv);
        // x[0:40:4, 2:24:5]
        final int outRows = 10;
        final int outCols = 5;
        IntArray output = new IntArray(outRows * outCols);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("slice", MlxIndexing::slice, x, output, ROWS, COLS, 0, ROWS, 4, 2, COLS, 5) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int r = 0; r < outRows; r++) {
            for (int c = 0; c < outCols; c++) {
                assertEquals(r + "," + c, (r * 4) * COLS + 2 + c * 5, output.get(r * outCols + c));
            }
        }
    }

    @Test
    public void testSliceUpdateAddHalf() throws TornadoExecutionPlanException {
        float[] xv = values(ROWS * COLS, 1, 2, 4);
        float[] uv = values(UPDATE_ROWS * UPDATE_COLS, 0.5f, 2.5f, 5);
        HalfFloatArray x = half(xv);
        HalfFloatArray update = half(uv);
        HalfFloatArray output = new HalfFloatArray(xv.length);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, update) //
                .libraryTask("update", MlxIndexing::sliceUpdateAdd, x, update, output, ROWS, COLS, R0, C0, UPDATE_ROWS, UPDATE_COLS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        float[] expected = sliceUpdateJava(widen(x), widen(update), Double::sum);
        double[] e = new double[expected.length];
        for (int i = 0; i < e.length; i++) {
            e[i] = expected[i];
        }
        assertAllClose("sliceUpdateAdd float16", e, output, 1e-3, 1e-3);
    }
}
